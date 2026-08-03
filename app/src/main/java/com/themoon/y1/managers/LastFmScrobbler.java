package com.themoon.y1.managers;

import android.content.Context;
import android.content.SharedPreferences;

import com.themoon.y1.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

// Last.fm scrobbling. Two independent records are kept for every eligible play:
// 1) a permanent local /storage/sdcard0/.scrobbler.log in the classic Audioscrobbler
//    1.1 format (same idea as Rockbox's scrobbler.log - a durable record you can
//    hand to any external uploader), and
// 2) a small pending-submission queue that is drained against the real Last.fm API
//    (auth.getMobileSession / track.updateNowPlaying / track.scrobble) whenever the
//    device is online, so scrobbles usually show up on last.fm without any extra tool.
public class LastFmScrobbler {

    // Register a free API account at https://www.last.fm/api/account/create and add
    // lastfm.api.key / lastfm.api.secret to your local.properties (see app/build.gradle) -
    // Last.fm requires every client application to identify itself with its own credentials.
    private static final String API_KEY = BuildConfig.LASTFM_API_KEY;
    private static final String API_SECRET = BuildConfig.LASTFM_API_SECRET;

    private static final String API_ROOT = "https://ws.audioscrobbler.com/2.0/";
    private static final File SCROBBLE_LOG_FILE = new File("/storage/sdcard0/.scrobbler.log");
    private static final int MIN_TRACK_LENGTH_SEC = 30;
    private static final int MAX_PENDING_QUEUE = 500;

    private static LastFmScrobbler instance;

    private final SharedPreferences prefs;
    private final OkHttpClient client = buildHttpClient();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    // The stock Android 4.2 (API 17) trust store on this device can't validate
    // ws.audioscrobbler.com's certificate chain ("Trust anchor not found").
    // Same workaround already used elsewhere in this app (see MainActivity's
    // podcast/album-art fetchers): route TLS through Conscrypt with an
    // all-trusting TrustManager instead of the broken platform one.
    private static OkHttpClient buildHttpClient() {
        try {
            final javax.net.ssl.X509TrustManager trustAll = new javax.net.ssl.X509TrustManager() {
                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
            };
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS", "Conscrypt");
            sslContext.init(null, new javax.net.ssl.TrustManager[]{trustAll}, new java.security.SecureRandom());

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), trustAll)
                    .hostnameVerifier(new javax.net.ssl.HostnameVerifier() {
                        @Override
                        public boolean verify(String hostname, javax.net.ssl.SSLSession session) { return true; }
                    })
                    .build();
        } catch (Exception e) {
            return new OkHttpClient(); // fall back to the default stack if Conscrypt isn't available yet
        }
    }

    // Metadata for the track currently armed for scrobbling, set by onTrackStart()
    // and consumed by evaluateAndMaybeSubmit() right before the next track loads.
    private String curArtist, curTitle, curAlbum, curFilePath;
    private long curDurationMs;
    private long curStartedAtUnixSec;
    private boolean curSubmitted = true;

    private LastFmScrobbler(Context context) {
        this.prefs = context.getSharedPreferences("Y1_LASTFM", Context.MODE_PRIVATE);
    }

    public static synchronized LastFmScrobbler getInstance(Context context) {
        if (instance == null) instance = new LastFmScrobbler(context.getApplicationContext());
        return instance;
    }

    // ================= settings / account =================

    public boolean isEnabled() {
        return prefs.getBoolean("enabled", false);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean("enabled", enabled).apply();
    }

    public boolean isLoggedIn() {
        return prefs.getString("session_key", null) != null;
    }

    public String getUsername() {
        return prefs.getString("username", "");
    }

    public void logout() {
        prefs.edit().remove("session_key").remove("username").apply();
    }

    public interface LoginCallback {
        void onResult(boolean success, String message);
    }

    public void login(final String username, final String password, final LoginCallback callback) {
        worker.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Map<String, String> params = new TreeMap<>();
                    params.put("method", "auth.getMobileSession");
                    params.put("api_key", API_KEY);
                    params.put("username", username);
                    params.put("password", password);
                    String sig = sign(params);

                    FormBody form = new FormBody.Builder()
                            .add("method", "auth.getMobileSession")
                            .add("api_key", API_KEY)
                            .add("username", username)
                            .add("password", password)
                            .add("api_sig", sig)
                            .add("format", "json")
                            .build();

                    Request request = new Request.Builder().url(API_ROOT).post(form).build();
                    try (Response response = client.newCall(request).execute()) {
                        String body = response.body() != null ? response.body().string() : "";
                        JSONObject json = new JSONObject(body);
                        if (response.isSuccessful() && json.has("session")) {
                            JSONObject session = json.getJSONObject("session");
                            prefs.edit()
                                    .putString("session_key", session.getString("key"))
                                    .putString("username", session.getString("name"))
                                    .apply();
                            setEnabled(true);
                            if (callback != null) callback.onResult(true, session.getString("name"));
                            flushPendingQueue();
                        } else {
                            String msg = json.has("message") ? json.getString("message") : "Login failed";
                            if (callback != null) callback.onResult(false, msg);
                        }
                    }
                } catch (Exception e) {
                    if (callback != null) callback.onResult(false, e.getMessage());
                }
            }
        });
    }

    // ================= playback lifecycle =================

    // Call once a track actually starts playing (duration must already be known).
    public void onTrackStart(String artist, String title, String album, long durationMs, String filePath) {
        curSubmitted = true; // nothing armed until validated below
        if (filePath == null || isExcludedPath(filePath)) return;
        if (title == null || title.trim().isEmpty()) return;

        curArtist = (artist == null || artist.trim().isEmpty()) ? "Unknown Artist" : artist.trim();
        curTitle = title.trim();
        curAlbum = (album != null) ? album.trim() : "";
        curFilePath = filePath;
        curDurationMs = durationMs;
        curStartedAtUnixSec = System.currentTimeMillis() / 1000L;
        curSubmitted = false;

        if (isEnabled() && isLoggedIn()) {
            updateNowPlaying(curArtist, curTitle, curAlbum, (int) (curDurationMs / 1000));
        }
    }

    // Re-arms the same track for another scrobble - used for repeat-single playback,
    // where the track loops without prepareMusicTrack()/onTrackStart() running again.
    public void rearmCurrentTrack() {
        if (curFilePath == null) return;
        curStartedAtUnixSec = System.currentTimeMillis() / 1000L;
        curSubmitted = false;
    }

    // Call right before switching away from the current track (skip, auto-advance,
    // or natural completion) with its current playback position.
    public void evaluateAndMaybeSubmit(long currentPositionMs) {
        // 🚀 [버그 수리] 예전엔 이 함수 전체가 !isEnabled()(=Settings의 "Scrobble to Last.fm" 토글)에 걸려
        // 있어서, 로그인은 물론 그 토글을 직접 켠 적도 없는 사용자는 로컬 .scrobbler.log조차 절대 생성되지
        // 않았습니다 - README에는 이게 계정 없이도 항상 남는 로컬 기록처럼 적혀 있는데 실제로는 그렇지
        // 않았던 것. 로컬 로그(계정/네트워크 불필요, SD카드 안에만 남는 순수 텍스트)는 이제 항상 남기고,
        // 실제 Last.fm API로 보내는 부분만 그 토글로 계속 제어합니다.
        if (curSubmitted || curFilePath == null) return;
        long durationSec = curDurationMs / 1000;
        if (durationSec < MIN_TRACK_LENGTH_SEC) {
            curSubmitted = true;
            return;
        }

        // Last.fm scrobble rule: track must be played past half its length, or 4 minutes.
        boolean halfPlayed = currentPositionMs >= curDurationMs / 2;
        boolean fourMinPlayed = currentPositionMs >= 4 * 60 * 1000L;
        if (!halfPlayed && !fourMinPlayed) return;

        curSubmitted = true;
        appendToScrobblerLog(curArtist, curAlbum, curTitle, durationSec, curStartedAtUnixSec);

        if (isEnabled()) {
            enqueuePendingScrobble(curArtist, curAlbum, curTitle, durationSec, curStartedAtUnixSec);
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    flushPendingQueue();
                }
            });
        }
    }

    private boolean isExcludedPath(String path) {
        // Audiobooks/podcasts aren't "tracks" in the Last.fm sense - skip them.
        return path.startsWith("/storage/sdcard0/Audiobooks") || path.contains("/Podcasts") || path.startsWith("/PODCAST_STREAM");
    }

    // ================= now playing =================

    private void updateNowPlaying(final String artist, final String title, final String album, final int durationSec) {
        final String sk = prefs.getString("session_key", null);
        if (sk == null) return;
        worker.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Map<String, String> params = new TreeMap<>();
                    params.put("method", "track.updateNowPlaying");
                    params.put("api_key", API_KEY);
                    params.put("sk", sk);
                    params.put("artist", artist);
                    params.put("track", title);
                    if (!album.isEmpty()) params.put("album", album);
                    if (durationSec > 0) params.put("duration", String.valueOf(durationSec));
                    String sig = sign(params);

                    FormBody.Builder form = new FormBody.Builder();
                    for (Map.Entry<String, String> e : params.entrySet()) form.add(e.getKey(), e.getValue());
                    form.add("api_sig", sig).add("format", "json");

                    Request request = new Request.Builder().url(API_ROOT).post(form.build()).build();
                    try (Response response = client.newCall(request).execute()) {
                        // best effort - a failed "now playing" ping doesn't affect the real scrobble
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    // ================= scrobbler.log (Rockbox-style local record) =================

    private synchronized void appendToScrobblerLog(String artist, String album, String title, long durationSec, long timestampSec) {
        try {
            boolean isNew = !SCROBBLE_LOG_FILE.exists();
            File parent = SCROBBLE_LOG_FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileWriter fw = new FileWriter(SCROBBLE_LOG_FILE, true);
            try {
                if (isNew) {
                    fw.write("#AUDIOSCROBBLER/1.1\n");
                    fw.write("#TZ/UNKNOWN\n");
                    fw.write("#CLIENT/JJY 1.0\n");
                }
                fw.write(tsv(artist, album, title, "", String.valueOf(durationSec), "L", String.valueOf(timestampSec), ""));
                fw.write("\n");
            } finally {
                fw.close();
            }
        } catch (Exception ignored) {
        }
    }

    private String tsv(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append('\t');
            sb.append(fields[i] == null ? "" : fields[i].replace("\t", " ").replace("\n", " "));
        }
        return sb.toString();
    }

    // ================= pending queue (API retries while offline) =================

    private synchronized void enqueuePendingScrobble(String artist, String album, String title, long durationSec, long timestampSec) {
        try {
            JSONArray queue = loadQueue();
            JSONObject entry = new JSONObject();
            entry.put("artist", artist);
            entry.put("album", album);
            entry.put("title", title);
            entry.put("duration", durationSec);
            entry.put("timestamp", timestampSec);
            queue.put(entry);
            while (queue.length() > MAX_PENDING_QUEUE) queue.remove(0);
            prefs.edit().putString("pending_queue", queue.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private JSONArray loadQueue() throws Exception {
        return new JSONArray(prefs.getString("pending_queue", "[]"));
    }

    private synchronized void flushPendingQueue() {
        String sk = prefs.getString("session_key", null);
        if (sk == null) return;
        try {
            JSONArray queue = loadQueue();
            while (queue.length() > 0) {
                JSONObject entry = queue.getJSONObject(0);
                boolean ok = submitScrobble(sk, entry.getString("artist"), entry.optString("album", ""),
                        entry.getString("title"), entry.getLong("duration"), entry.getLong("timestamp"));
                if (!ok) break; // stop on first failure (offline/rate limited) - keep order, retry later
                queue.remove(0);
            }
            prefs.edit().putString("pending_queue", queue.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private boolean submitScrobble(String sk, String artist, String album, String title, long durationSec, long timestampSec) {
        try {
            Map<String, String> params = new TreeMap<>();
            params.put("method", "track.scrobble");
            params.put("api_key", API_KEY);
            params.put("sk", sk);
            params.put("artist", artist);
            params.put("track", title);
            params.put("timestamp", String.valueOf(timestampSec));
            if (album != null && !album.isEmpty()) params.put("album", album);
            String sig = sign(params);

            FormBody.Builder form = new FormBody.Builder();
            for (Map.Entry<String, String> e : params.entrySet()) form.add(e.getKey(), e.getValue());
            form.add("api_sig", sig).add("format", "json");

            Request request = new Request.Builder().url(API_ROOT).post(form.build()).build();
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ================= Last.fm request signing =================

    private String sign(Map<String, String> sortedParams) throws Exception {
        // sortedParams must be a TreeMap so keys are already in the alphabetical
        // order the Last.fm signature algorithm requires.
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sortedParams.entrySet()) {
            sb.append(e.getKey()).append(e.getValue());
        }
        sb.append(API_SECRET);
        return md5(sb.toString());
    }

    private String md5(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(input.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
