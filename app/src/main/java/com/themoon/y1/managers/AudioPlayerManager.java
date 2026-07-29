package com.themoon.y1.managers;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.PowerManager;
import android.widget.Toast;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.themoon.y1.MainActivity;
import com.themoon.y1.R;
import com.themoon.y1.ThemeManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AudioPlayerManager {
    private static AudioPlayerManager instance;
    public SimpleExoPlayer exoPlayer;
    // 🚀 [신규 엔진] 10밴드 하드코어 소프트웨어 DSP를 전역으로 장전합니다!
    public Y1EqAudioProcessor customEqProcessor = new Y1EqAudioProcessor();

    // 🚀 [신규 엔진 2] 자연스러운 스피커 공간감을 구현할 크로스피드 DSP 장전!
    public Y1CrossfeedAudioProcessor crossfeedProcessor = new Y1CrossfeedAudioProcessor();
    public MediaPlayer legacyPlayer;
    public boolean isUsingLegacyPlayer = false;
    // 🚀 [FLAC 2단 엔진] 네이티브 legacyPlayer가 재생 도중 특정 FLAC 파일에서 에러를 뱉으면(일부
    // 리포트: "Legacy Player Error: 262" - 이 기기의 순정 FLAC 추출기가 못 읽는 파일), 곧바로 포기하지
    // 않고 그 파일의 경로만 여기에 기록해서 딱 한 번 ExoPlayer의 FLAC 익스텐션으로 재시도합니다.
    private final java.util.Set<String> flacLegacyFailedPaths = new java.util.HashSet<>();
    // 🚀 [Gapless 재생] 앨범(또는 15곡 이하 재생목록)일 때만 켜집니다 - ExoPlayer의 네이티브 큐 기능을 써서
    // 트랙 사이에 정지/재장전 없이 이어지도록 합니다. FLAC은 별도의 legacyPlayer 엔진을 쓰기 때문에
    // 이 큐에 섞일 수 없어 자동으로 제외됩니다.
    private boolean isGaplessQueueActive = false;
    private java.io.FileInputStream currentFileInputStream;

    private float currentSpeed = 1.0f;

    // Metadata for the track currently being prepared, handed to LastFmScrobbler
    // once its real duration is known (synchronously for the legacy/FLAC player,
    // or asynchronously at STATE_READY for ExoPlayer).
    private String pendingScrobbleArtist, pendingScrobbleTitle, pendingScrobbleAlbum, pendingScrobblePath;

    private AudioPlayerManager() {}



    public static synchronized AudioPlayerManager getInstance() {
        if (instance == null) instance = new AudioPlayerManager();
        return instance;
    }

    public void initPlayer(Context context) {
        if (exoPlayer == null) {
            com.google.android.exoplayer2.DefaultRenderersFactory renderersFactory = new com.google.android.exoplayer2.DefaultRenderersFactory(context.getApplicationContext()) {
                @Override
                protected void buildAudioRenderers(
                        Context context,
                        int extensionRendererMode,
                        com.google.android.exoplayer2.mediacodec.MediaCodecSelector mediaCodecSelector,
                        boolean enableDecoderFallback,
                        com.google.android.exoplayer2.audio.AudioSink audioSink,
                        android.os.Handler eventHandler,
                        com.google.android.exoplayer2.audio.AudioRendererEventListener eventListener,
                        java.util.ArrayList<com.google.android.exoplayer2.Renderer> out) {



                    // 🚀 1. [신규 엔진: 자바 리플렉션 해킹 - 24/32비트를 16비트로 분쇄하는 '진짜' 압축기 소환!]
                    java.util.List<com.google.android.exoplayer2.audio.AudioProcessor> procList = new java.util.ArrayList<>();
                    try {
                        java.lang.reflect.Constructor<?> ctor = Class.forName("com.google.android.exoplayer2.audio.ToInt16PcmAudioProcessor").getDeclaredConstructor();
                        ctor.setAccessible(true);
                        procList.add((com.google.android.exoplayer2.audio.AudioProcessor) ctor.newInstance());
                    } catch (Exception e) {}

                    // 🚀 2. [완벽 수리] 빈 깡통 버그를 원천 차단한 안전한 주파수 방어막!
                    com.google.android.exoplayer2.audio.AudioProcessor immortalSonic = new com.google.android.exoplayer2.audio.AudioProcessor() {
                        private final com.google.android.exoplayer2.audio.SonicAudioProcessor sonic = new com.google.android.exoplayer2.audio.SonicAudioProcessor();
                        private boolean isActive = false;

                        @Override
                        public com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat configure(com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat inputAudioFormat) throws com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException {
                            int safeSampleRate = inputAudioFormat.sampleRate;
                            // 96kHz, 192kHz 등 초고음질이 들어오면 스피커 보호를 위해 48kHz로 압축
                            if (safeSampleRate > 48000) { safeSampleRate = 48000; }
                            sonic.setOutputSampleRateHz(safeSampleRate);
                            com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat outputFormat = sonic.configure(inputAudioFormat);
                            isActive = sonic.isActive(); // 엔진이 알아서 활성화 여부를 결정하게 둡니다!
                            return outputFormat;
                        }

                        // 💡 0바이트를 뱉던 악성 코드를 삭제하고, 엔진의 순리대로 완벽히 위임합니다.
                        @Override public boolean isActive() { return isActive; }
                        @Override public void queueInput(java.nio.ByteBuffer inputBuffer) { sonic.queueInput(inputBuffer); }
                        @Override public void queueEndOfStream() { sonic.queueEndOfStream(); }
                        @Override public java.nio.ByteBuffer getOutput() { return sonic.getOutput(); }
                        @Override public boolean isEnded() { return sonic.isEnded(); }
                        @Override public void flush() { sonic.flush(); }
                        @Override public void reset() { sonic.reset(); }
                    };

                    // 🚀 3. [배관 조립] 1.강제 16비트 압축기 -> 2.수학 필터(EQ) -> 3.크로스피드 -> 4.주파수 방어막(Sonic) 순으로 직렬 연결!
                    procList.add(customEqProcessor);
                    procList.add(crossfeedProcessor);
                    procList.add(immortalSonic);

                    com.google.android.exoplayer2.audio.AudioProcessor[] processors = procList.toArray(new com.google.android.exoplayer2.audio.AudioProcessor[0]);
                    com.google.android.exoplayer2.audio.AudioCapabilities strict16BitCaps = new com.google.android.exoplayer2.audio.AudioCapabilities(new int[] { 2 }, 2);
                    com.google.android.exoplayer2.audio.AudioSink customSink = new com.google.android.exoplayer2.audio.DefaultAudioSink(strict16BitCaps, processors);

                    // 🚀 4. [MTK 사형 선고 - 타겟 정밀 조준 복구!]
                    com.google.android.exoplayer2.mediacodec.MediaCodecSelector customSelector = new com.google.android.exoplayer2.mediacodec.MediaCodecSelector() {
                        @Override
                        public java.util.List<com.google.android.exoplayer2.mediacodec.MediaCodecInfo> getDecoderInfos(String mimeType, boolean requiresSecureDecoder, boolean requiresTunnelingDecoder) throws com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException {
                            java.util.List<com.google.android.exoplayer2.mediacodec.MediaCodecInfo> decoders = mediaCodecSelector.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder);

                            // 💡 [핵심 수정] MP3, WAV 등은 건드리지 않고, 오직 애플 포맷(m4a/aac/alac)일 때만 MTK 디코더를 박살냅니다!
                            if (mimeType != null && (mimeType.contains("mp4a") || mimeType.contains("aac") || mimeType.contains("alac"))) {
                                java.util.List<com.google.android.exoplayer2.mediacodec.MediaCodecInfo> safeDecoders = new java.util.ArrayList<>();
                                for (com.google.android.exoplayer2.mediacodec.MediaCodecInfo info : decoders) {
                                    if (info.name != null && info.name.toLowerCase().contains("mtk")) {
                                        continue; // 🚨 애플 포맷을 훔쳐가려는 MTK 디코더만 정확히 사형!
                                    }
                                    safeDecoders.add(info);
                                }
                                return safeDecoders;
                            }

                            // 🟢 MP3 등 다른 모든 포맷은 시스템 순정 부품(MTK 포함)을 그대로 안전하게 통과시킵니다.
                            return decoders;
                        }
                    };

                    // 🚀 최종 조립 완료 및 부모에게 전달!
                    super.buildAudioRenderers(context, extensionRendererMode, customSelector, enableDecoderFallback, customSink, eventHandler, eventListener, out);
                }
            };

            // C++ 확장 부품 최우선 사용 명령
            renderersFactory.setExtensionRendererMode(com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);

            // 팩토리를 넣어서 조립 완료!
            exoPlayer = new com.google.android.exoplayer2.SimpleExoPlayer.Builder(context.getApplicationContext(), renderersFactory).build();

            // (이 아래의 리스너 코드들은 기존과 100% 동일하게 유지해 주세요!)
            // (이 아래 리스너 코드들은 기존과 100% 동일하게 유지해 주세요!)
            exoPlayer.addListener(new Player.EventListener() {
                @Override
                public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                    if (playbackState == Player.STATE_READY && !isUsingLegacyPlayer) {
                        if (MainActivity.instance != null) {
                            MainActivity.instance.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        // 🚀 [버그 완벽 수리] 곡이 바뀌어 오디오 잭이 갱신될 때마다 EQ 설정도 잊지 않고 다시 주입합니다!
                                        if (AudioEffectManager.getInstance() != null) {
                                            AudioEffectManager.getInstance().applyAudioEffects();
                                            AudioEffectManager.getInstance().applyEqProfile(); // 💡 이 한 줄이 추가되어야 소리가 바뀝니다!!
                                        }
                                        MainActivity.instance.setupVisualizer();

                                        int duration = getDuration();
                                        int s = (duration / 1000) % 60;
                                        int m = (duration / (1000 * 60)) % 60;
                                        MainActivity.instance.tvPlayerTimeTotal.setText(String.format(Locale.US, "%02d:%02d", m, s));
                                        com.themoon.y1.managers.LastFmScrobbler.getInstance(MainActivity.instance)
                                                .onTrackStart(pendingScrobbleArtist, pendingScrobbleTitle, pendingScrobbleAlbum, duration, pendingScrobblePath);
                                        // 🚀 [추가!] 곡 장전이 끝나서 정확한 duration이 나왔으므로, 이 시점에 비트레이트 캡슐을 다시 업데이트합니다!
                                        if (!MainActivity.instance.currentPlaylist.isEmpty()) {
                                            MainActivity.instance.updateAudioQualityInfo(MainActivity.instance.currentPlaylist.get(MainActivity.instance.currentIndex));
                                        }
                                    } catch (Exception e) {}
                                }
                            });
                        }
                    } else if (playbackState == Player.STATE_ENDED && !isUsingLegacyPlayer) {
                        handleTrackCompletion();
                    }
                    if (MainActivity.instance != null) {
                        MainActivity.instance.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (MainActivity.instance != null) MainActivity.instance.updatePlayerUI();
                            }
                        });
                    }
                }

                @Override
                public void onPlayerError(com.google.android.exoplayer2.ExoPlaybackException error) {
                    if (!isUsingLegacyPlayer) handleTrackError("Cannot play this file.");
                }

                // 🚀 [Gapless 재생] ExoPlayer가 큐 안에서 자연스럽게(또는 seekToNext/Previous로) 다음 곡으로
                // 넘어갈 때 호출됩니다 - 이때는 재장전 없이 메타데이터/화면만 갱신하면 됩니다.
                // PLAYLIST_CHANGED(=setMediaItems 최초 호출)는 playTrackList가 직접 처리하므로 제외합니다.
                @Override
                public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                    if (!isGaplessQueueActive || MainActivity.instance == null) return;
                    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                            && reason != Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) return;

                    final MainActivity main = MainActivity.instance;
                    int newIndex = exoPlayer.getCurrentWindowIndex();
                    if (newIndex < 0 || newIndex >= main.currentPlaylist.size()) return;

                    main.currentIndex = newIndex;
                    main.lastTrackChangeTime = System.currentTimeMillis();
                    main.runOnUiThread(() -> {
                        prepareMusicTrack(newIndex);
                        main.updatePlayerUI();
                    });
                }
            });
        }
    }

    public void setPlaybackSpeed(float speed) {
        this.currentSpeed = speed;
        if (exoPlayer != null && !isUsingLegacyPlayer) {
            exoPlayer.setPlaybackParameters(new PlaybackParameters(speed, 1.0f));
        } else if (isUsingLegacyPlayer) {
            if (MainActivity.instance != null) {
                MainActivity.instance.runOnUiThread(() ->
                        Toast.makeText(MainActivity.instance, "⚠️ Speed control is disabled for FLAC files on this device.", Toast.LENGTH_SHORT).show()
                );
            }
        }
    }
    // =======================================================
    // 🚀 [신규 엔진] 메인 화면의 셔플 버튼을 눌렀을 때 ExoPlayer에 실시간으로 명령을 하달합니다!
    // =======================================================
    public void setShuffleMode(boolean isShuffle) {
        if (exoPlayer != null && !isUsingLegacyPlayer) {
            exoPlayer.setShuffleModeEnabled(isShuffle);
        }
    }
    public float getCurrentSpeed() { return currentSpeed; }

    private void handleTrackCompletion() {
        MainActivity main = MainActivity.instance;
        if (main == null) return;
        main.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Track just played to completion - evaluate it for scrobbling before
                    // any repeat/next logic changes the player's position.
                    com.themoon.y1.managers.LastFmScrobbler.getInstance(main).evaluateAndMaybeSubmit(getCurrentPosition());

                    int repeatMode = main.prefs.getInt("repeat_mode", 0);
                    if (repeatMode == 1) {
                        if (isUsingLegacyPlayer && legacyPlayer != null) {
                            legacyPlayer.seekTo(0); legacyPlayer.start();
                        } else if (exoPlayer != null) {
                            exoPlayer.seekTo(0); exoPlayer.setPlayWhenReady(true);
                        }
                        // Same track loops again - re-arm it so the next full loop can scrobble too.
                        com.themoon.y1.managers.LastFmScrobbler.getInstance(main).rearmCurrentTrack();
                    } else if (repeatMode == 2) {
                        nextTrack();
                    } else {
                        if (main.currentIndex < main.currentPlaylist.size() - 1) {
                            nextTrack();
                        } else {
                            main.currentIndex = 0;
                            prepareMusicTrack(main.currentIndex);
                            main.isPausedByHand = true;
                            main.updatePlayerUI();
                        }
                    }
                } catch (Exception e) { nextTrack(); }
            }
        });
    }

    private void handleTrackError(String errorMsg) {
        MainActivity main = MainActivity.instance;
        if (main == null) return;
        main.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(main, "⚠️ " + errorMsg + " Skipping...", Toast.LENGTH_SHORT).show();
                nextTrack();
            }
        });
    }

    public void playTrackList(List<File> list, int index) {
        saveAudiobookBookmarkIfNeeded();

        final MainActivity main = MainActivity.instance;
        if (main == null) return;

        initPlayer(main);

        // 1. 외부에서 받은 리스트를 안전하게 복사
        List<File> newList = new java.util.ArrayList<>(list);
        if (newList.isEmpty()) return; // 방어막: 리스트가 텅 비었으면 함수를 중단!

        // 2. 사용자가 클릭한 원본(타겟) 노래를 미리 기억해 둡니다.
        // 만약 인덱스가 꼬여서 리스트 범위를 벗어났다면 안전하게 0번으로 고정!
        if (index < 0 || index >= newList.size()) index = 0;
        File targetSong = newList.get(index);

        // 3. 메인 화면의 재생 바구니(Playlist) 2개를 싹 비우고 새 곡들로 채웁니다.
        main.originalPlaylist.clear();
        main.originalPlaylist.addAll(newList);
        main.currentPlaylist.clear();
        main.currentPlaylist.addAll(newList);

        // 🚀 4. [절대 셔플 엔진 가동] 설정에서 셔플 모드가 켜져 있다면? 무조건 섞습니다!
        boolean isShuffle = main.prefs.getBoolean("shuffle", false);
        if (isShuffle) {
            java.util.Collections.shuffle(main.currentPlaylist); // currentPlaylist를 사정없이 섞음!

            // 섞인 바구니 안에서 방금 사용자가 누른 그 곡(targetSong)이 몇 번 자리로 밀려났는지 찾아냅니다.
            main.currentIndex = main.currentPlaylist.indexOf(targetSong);
            if (main.currentIndex == -1) main.currentIndex = 0; // 혹시나 못 찾으면 0번 재생
        } else {
            // 셔플이 꺼져있다면 원본 순서 그대로!
            main.currentIndex = index;
        }
// 🚀 [추가] ExoPlayer 엔진 자체에도 셔플 상태를 명확히 인지시킵니다!
        if (!isUsingLegacyPlayer && exoPlayer != null) {
            exoPlayer.setShuffleModeEnabled(isShuffle);
        }
        main.isPausedByHand = false; // 🚀 스위치를 미리 켜줍니다!

        // 🚀 [Gapless 재생] 앨범(또는 15곡 이하 목록)이고 셔플이 꺼져 있고 FLAC이 섞여있지 않으면
        // ExoPlayer의 네이티브 큐로 전체 목록을 한 번에 장전해서 트랙 사이에 끊김이 없게 합니다.
        // 큰 목록(전체 곡 셔플 재생 등)은 기존 방식(트랙별 개별 로드) 그대로 둡니다.
        isGaplessQueueActive = !isShuffle && shouldUseGaplessQueue(main.currentPlaylist);
        if (isGaplessQueueActive) {
            List<MediaItem> mediaItems = new ArrayList<>();
            for (File f : main.currentPlaylist) {
                mediaItems.add(MediaItem.fromUri(Uri.fromFile(f)));
            }
            if (legacyPlayer != null) { legacyPlayer.stop(); legacyPlayer.reset(); }
            exoPlayer.stop();
            exoPlayer.setMediaItems(mediaItems, main.currentIndex, 0);
            exoPlayer.setShuffleModeEnabled(false);
            exoPlayer.setPlaybackParameters(new PlaybackParameters(currentSpeed, 1.0f));
            exoPlayer.prepare();
            if (!main.isPausedByHand) exoPlayer.setPlayWhenReady(true);
        }

        prepareMusicTrack(main.currentIndex);
        main.updatePlayerUI(); // 🚀 타이머 즉시 시작!
    }

    // 🚀 [Gapless 재생 조건] 앨범(대부분 15곡 이하)이거나 명시적으로 15곡 이하인 재생목록일 때만 켭니다.
    // FLAC은 별도의 legacyPlayer 엔진을 쓰기 때문에 ExoPlayer 네이티브 큐에 섞일 수 없어 제외합니다.
    private boolean shouldUseGaplessQueue(List<File> list) {
        if (list.size() > 15) return false;
        for (File f : list) {
            if (f.getName().toLowerCase().endsWith(".flac")) return false;
        }
        return true;
    }
    public void playPodcastStream(String url, String title, String imageUrl, String channelName, int offsetMs) {
        final MainActivity main = MainActivity.instance;
        if (main == null) return;

        try {
            if (legacyPlayer != null) { legacyPlayer.stop(); legacyPlayer.reset(); }
            if (exoPlayer == null) initPlayer(main.getApplicationContext());
            else { exoPlayer.stop(); exoPlayer.clearMediaItems(); }

            isUsingLegacyPlayer = false;
            isGaplessQueueActive = false; // 🚀 팟캐스트는 항상 단일 트랙 모드로 재생합니다.

            // 🚀 [핵심 엔진] 스트리밍일 때도 '가짜(Dummy) 플레이리스트'를 만들어 시간을 기록하게 속입니다!
            String safeChannel = channelName.replaceAll("[\\\\/:*?\"<>|]", "_");
            String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_") + ".mp3";
            main.currentPlaylist.clear();
            main.currentPlaylist.add(new java.io.File("/PODCAST_STREAM/" + safeChannel, safeTitle));
            main.currentIndex = 0;

            com.google.android.exoplayer2.MediaItem mediaItem = com.google.android.exoplayer2.MediaItem.fromUri(android.net.Uri.parse(url));
            com.google.android.exoplayer2.upstream.DataSource.Factory dataSourceFactory = new com.google.android.exoplayer2.upstream.DefaultDataSourceFactory(main, com.google.android.exoplayer2.util.Util.getUserAgent(main, "Y1_Launcher"));
            com.google.android.exoplayer2.extractor.DefaultExtractorsFactory extractorsFactory = new com.google.android.exoplayer2.extractor.DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true);
            com.google.android.exoplayer2.source.MediaSource mediaSource = new com.google.android.exoplayer2.source.ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory).createMediaSource(mediaItem);

            exoPlayer.setMediaSource(mediaSource);
            exoPlayer.prepare();

            // 🚀 [이어서 재생] 팝업에서 넘겨받은 시간이 있다면 그곳으로 워프!
            if (offsetMs > 0) {
                exoPlayer.seekTo(offsetMs);
            }
            exoPlayer.setPlaybackParameters(new com.google.android.exoplayer2.PlaybackParameters(currentSpeed, 1.0f));

            main.runOnUiThread(() -> {
                main.isPausedByHand = false;
                main.tvPlayerTitle.setText(title);
                main.tvPlayerArtist.setText(channelName); // 가수에 팟캐스트 채널명 표시
                main.tvPlayerAlbum.setText("");
                main.ivAlbumArt.setImageResource(R.drawable.default_album);
                main.ivPlayerBgBlur.setImageResource(0);
                main.updatePlayerUI();
            });

            // (앨범 아트 백그라운드 다운로드 코드는 이전과 동일하게 유지!)
            if (imageUrl != null && !imageUrl.isEmpty()) {
                new Thread(() -> {
                    try {
                        java.net.URL imgUrl = new java.net.URL(imageUrl);
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) imgUrl.openConnection();
                        conn.setDoInput(true); conn.connect();
                        final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(conn.getInputStream());
                        if (bmp != null) {
                            main.runOnUiThread(() -> {
                                main.ivAlbumArt.setImageBitmap(bmp);
                                android.graphics.Bitmap blurredBg = main.applyGaussianBlur(bmp);
                                main.ivPlayerBgBlur.setImageBitmap(blurredBg);
                                main.currentAlbumColor = bmp.getPixel(bmp.getWidth()/2, (int)(bmp.getHeight()*0.8)) | 0xFF000000;
                                main.updateMainMenuBackground();
                            });
                        }
                    } catch (Exception e) {}
                }).start();
            }

            exoPlayer.setPlayWhenReady(true);

        } catch (Exception e) {}
    }
    public void playTrackListWithOffset(List<File> list, int index, int offsetMs) {
        playTrackList(list, index);
        if (offsetMs > 0) {
            try {
                seekRelative(offsetMs - getCurrentPosition());
                final int totalSec = offsetMs / 1000;
                final int min = totalSec / 60;
                final int sec = totalSec % 60;
                if (MainActivity.instance != null) {
                    MainActivity.instance.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                android.widget.Toast toast = android.widget.Toast.makeText(MainActivity.instance,
                                        "🎧 Resuming playback from " + min + "m " + sec + "s",
                                        android.widget.Toast.LENGTH_SHORT);
                                android.widget.LinearLayout toastLayout = (android.widget.LinearLayout) toast.getView();
                                android.widget.TextView toastTV = (android.widget.TextView) toastLayout.getChildAt(0);
                                toastTV.setTextSize(18f);
                                toast.show();
                            } catch (Exception e) {}
                        }
                    });
                }
            } catch (Exception e) {}
        }
    }

    public void setupFolderPlaylist(File clickedFile, File parentFolder) {
        MainActivity main = MainActivity.instance;
        if (main == null) return;

        List<File> folderAudio = new java.util.ArrayList<>();
        File[] files = parentFolder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && isAudioFile(f.getName())) folderAudio.add(f);
            }
            java.util.Collections.sort(folderAudio, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
        }
        int idx = folderAudio.indexOf(clickedFile);
        if (idx == -1) idx = 0;
        playTrackList(folderAudio, idx);
    }

    private boolean isAudioFile(String name) {
        name = name.toLowerCase();
        // 🚀 [.m4b 오디오북 공식 지원 도어 오픈!]
        return name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".ogg")
                || name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".ape") || name.endsWith(".wma")
                || name.endsWith(".opus") || name.endsWith(".m4b");
    }

    public void playOrPauseMusic() {
        if (isUsingLegacyPlayer && legacyPlayer != null) {
            if (legacyPlayer.isPlaying()) {
                saveAudiobookBookmarkIfNeeded();
                legacyPlayer.pause();
                if (MainActivity.instance != null) MainActivity.instance.isPausedByHand = true;
            } else {
                legacyPlayer.start();
                if (MainActivity.instance != null) MainActivity.instance.isPausedByHand = false;
            }
        } else if (exoPlayer != null) {
            if (exoPlayer.getPlayWhenReady()) {
                saveAudiobookBookmarkIfNeeded();
                exoPlayer.setPlayWhenReady(false);
                if (MainActivity.instance != null) MainActivity.instance.isPausedByHand = true;
            } else {
                exoPlayer.setPlayWhenReady(true);
                if (MainActivity.instance != null) MainActivity.instance.isPausedByHand = false;
            }
        }
        if (MainActivity.instance != null) MainActivity.instance.updatePlayerUI();
    }

    public void nextTrack() {
        saveAudiobookBookmarkIfNeeded();
        MainActivity main = MainActivity.instance;
        if (main == null || main.currentPlaylist.isEmpty()) return;
        main.lastTrackChangeTime = System.currentTimeMillis();

        // 🚀 [Gapless 재생] 큐 안에서 그대로 다음 곡으로 넘어갑니다 - onMediaItemTransition 리스너가
        // currentIndex 갱신과 화면 새로고침을 알아서 처리하므로 여기서 중복으로 하지 않습니다.
        if (isGaplessQueueActive && exoPlayer != null) {
            if (exoPlayer.hasNext()) {
                exoPlayer.next();
            } else {
                exoPlayer.seekTo(0, 0);
            }
            return;
        }

        main.currentIndex = (main.currentIndex + 1) % main.currentPlaylist.size();
        prepareMusicTrack(main.currentIndex);
        main.updatePlayerUI();
    }

    public void prevTrack() {
        saveAudiobookBookmarkIfNeeded();
        MainActivity main = MainActivity.instance;
        if (main == null || main.currentPlaylist.isEmpty()) return;
        main.lastTrackChangeTime = System.currentTimeMillis();

        if (isGaplessQueueActive && exoPlayer != null) {
            if (exoPlayer.hasPrevious()) {
                exoPlayer.previous();
            } else {
                exoPlayer.seekTo(main.currentPlaylist.size() - 1, 0);
            }
            return;
        }

        main.currentIndex--;
        if (main.currentIndex < 0) main.currentIndex = main.currentPlaylist.size() - 1;
        prepareMusicTrack(main.currentIndex);
        main.updatePlayerUI();
    }

    public void seekRelative(int offsetMs) {
        long currentPos = getCurrentPosition();
        long duration = getDuration();
        long targetPos = currentPos + offsetMs;
        if (targetPos < 0) targetPos = 0;
        if (targetPos > duration && duration > 0) targetPos = duration;

        if (isUsingLegacyPlayer && legacyPlayer != null) {
            legacyPlayer.seekTo((int) targetPos);
        } else if (exoPlayer != null) {
            exoPlayer.seekTo(targetPos);
        }
    }

    // 🚀 [순정 및 동기화 완벽 복원] 번쩍이는 딜레이를 아예 없앴습니다!
    public void prepareMusicTrack(int index) {
        final MainActivity main = MainActivity.instance;
        if (main == null || main.currentPlaylist.isEmpty()) return;

        // The track that's about to be replaced gets one last chance to scrobble,
        // using its position right before we tear down/reload the player.
        com.themoon.y1.managers.LastFmScrobbler.getInstance(main).evaluateAndMaybeSubmit(getCurrentPosition());

        final File track = main.currentPlaylist.get(index);
        main.lastAlbumArtBytes = null;
        main.currentAlbumColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000;

        if (!track.exists() || track.length() < 1024) {
            main.tvPlayerTitle.setText("Corrupted File");
            main.tvPlayerArtist.setText("Skipping...");
            main.tvPlayerAlbum.setText("");
            main.ivAlbumArt.setImageResource(R.drawable.default_album);

            main.consecutiveErrorCount++;

            if (main.consecutiveErrorCount >= main.currentPlaylist.size()) {
                Toast.makeText(main, "❌ All tracks failed. Playback stopped.", Toast.LENGTH_SHORT).show();
                main.isPausedByHand = true;
                main.updatePlayerUI();
                main.consecutiveErrorCount = 0;
            } else {
                Toast.makeText(main, "Corrupted file detected. Skipping...", Toast.LENGTH_SHORT).show();
                new Handler().postDelayed(() -> nextTrack(), 1500);
            }
            return;
        }

        // 🚀 스레드(Thread)를 걷어내고 메인에서 즉시 처리하여 깜빡임/딜레이 현상을 완벽 차단!
        main.tvPlayerTitle.setText(track.getName());
        main.tvPlayerArtist.setText("Loading...");
        main.tvPlayerAlbum.setText("");
        main.ivAlbumArt.setImageResource(R.drawable.default_album);
        main.ivPlayerBgBlur.setImageResource(0);
        main.playerProgress.setProgress(0);
        main.tvPlayerTimeCurrent.setText("00:00");
        main.tvPlayerTimeTotal.setText("00:00");

        String ext = track.getName().toLowerCase();
        boolean isOpus = ext.endsWith(".opus");
        boolean isFlac = ext.endsWith(".flac"); // 🚀 FLAC 판별기 신규 추가!
        // 🚀 [FLAC 재생 버그 수정] 아래 "FLAC: 데드락 구출용 특수 엔진" 분기가 실제로는 한 번도 켜지지
        // 않고 있었습니다 - isUsingLegacyPlayer가 항상 false로 고정되어 있어서 FLAC도 그냥 ExoPlayer로
        // 흘러갔고, ExoPlayer의 FLAC 익스텐션이 일부 기기에서 멈춰버려(0:00에서 진행 안 됨) 있었습니다.
        // 다만 이 파일이 이미 한 번 네이티브 엔진에서 에러를 낸 적이 있다면(flacLegacyFailedPaths), 이번엔
        // 처음부터 ExoPlayer의 FLAC 익스텐션으로 보냅니다.
        isUsingLegacyPlayer = isFlac && !flacLegacyFailedPaths.contains(track.getAbsolutePath());

        try {
            String t = null;
            String a = null;
            String album = null;
            main.lastAlbumArtBytes = null;

            // ==========================================
            // 🛡️ [1구역] 메타데이터 추출 (안전 구역 분리)
            // ==========================================
            if (isOpus) {
                Object[] opusTags = extractOpusMetadata(track);
                if (opusTags[0] != null) t = (String) opusTags[0];
                if (opusTags[1] != null) a = (String) opusTags[1];
                if (opusTags[2] != null) album = (String) opusTags[2];
                if (opusTags[5] != null) main.lastAlbumArtBytes = (byte[]) opusTags[5];
            } else if (isFlac) {
                Object[] flacTags = extractFlacMetadata(track);
                if (flacTags[0] != null) t = (String) flacTags[0];
                if (flacTags[1] != null) a = (String) flacTags[1];
                if (flacTags[2] != null) album = (String) flacTags[2];
                if (flacTags[5] != null) main.lastAlbumArtBytes = (byte[]) flacTags[5];

                // 🚀 [여기에 신규 장착!] FLAC 검사가 끝나고, 순정 부품으로 넘어가기 직전에 ALAC/M4A를 낚아챕니다!
            } else if (ext.endsWith(".m4a") || ext.endsWith(".alac")) {
                Object[] alacTags = extractAlacMetadata(track);
                if (alacTags[0] != null) t = (String) alacTags[0];
                if (alacTags[1] != null) a = (String) alacTags[1];
                if (alacTags[2] != null) album = (String) alacTags[2];
                if (alacTags[5] != null) main.lastAlbumArtBytes = (byte[]) alacTags[5];

                // 🎯 대망의 가사 장착!
                // MainActivity 쪽에 가사를 저장하는 변수(예: main.currentLyrics 등)에 값을 넣어줍니다.
                // 아티스트님의 앱 구조에 맞춰 변수명만 살짝 수정해 주세요!
                if (alacTags.length > 7 && alacTags[7] != null) {
                    // 예시: main.lyricsText = (String) alacTags[7];
                }

            } else if (ext.endsWith(".ogg")) {
                // 🚀 [신규 추가] OGG Vorbis 재생 시 메타데이터 + 앨범 아트 추출!
                Object[] oggTags = extractOggVorbisMetadata(track);
                if (oggTags[0] != null) t = (String) oggTags[0];
                if (oggTags[1] != null) a = (String) oggTags[1];
                if (oggTags[2] != null) album = (String) oggTags[2];
                if (oggTags[5] != null) main.lastAlbumArtBytes = (byte[]) oggTags[5];
            } else {
                // 🚀 MP3, WAV 등 버틸 수 있는 파일만 순정 부품 사용
                try {
                    android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
                    java.io.FileInputStream fisMmr = new java.io.FileInputStream(track);
                    mmr.setDataSource(fisMmr.getFD());
                    t = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE);
                    a = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST);
                    // 🚀 [오디오북 태그 지원] 가수 태그가 없으면 '저자(AUTHOR)' 태그를 한 번 더 긁어옵니다!
                    if (a == null || a.isEmpty()) a = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_AUTHOR);
                    album = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM);

                    main.lastAlbumArtBytes = mmr.getEmbeddedPicture();
                    fisMmr.close();
                    mmr.release();
                } catch (Throwable e) {}
            }

            // ==========================================
            // 🖼️ [2구역] 화면 UI 덮어쓰기 (무조건 실행됨!)
            // ==========================================
            // 🚀 [제목에 확장자 노출 버그 수정] 하드코딩된 확장자 목록(.ogg 등 누락 가능) 대신
            // 마지막 점(.) 기준으로 잘라서 어떤 확장자든 안전하게 제거합니다!
            String safeFileName = track.getName();
            int safeFileNameDot = safeFileName.lastIndexOf(".");
            if (safeFileNameDot > 0)
                safeFileName = safeFileName.substring(0, safeFileNameDot);
            File coverFile = new File("/storage/sdcard0/Y1_Covers", safeFileName + ".jpg");

            // 🚀 [팟캐스트 초고속 렌더링 지름길 장착!]
            boolean isPodcast = track.getAbsolutePath().contains("/Podcasts/");
            File podcastCoverFile = null;
            if (isPodcast) {
                // 무거운 MP3 태그 스캔을 완벽히 건너뛰고, 폴더 안의 cover.jpg를 1순위로 지정합니다!
                File podcastFolder = track.getParentFile();
                podcastCoverFile = new File(podcastFolder, "cover.jpg");
            }

            if (main.prefs.contains("meta_title_" + track.getAbsolutePath())) {
                t = main.prefs.getString("meta_title_" + track.getAbsolutePath(), t);
                a = main.prefs.getString("meta_artist_" + track.getAbsolutePath(), a);
            }

            // 🚀 [폴더 이름 강제 수혈 엔진] 태그가 텅 비었을 때, 폴더 이름을 가수/책 이름으로 띄워줍니다!
            if (a == null || a.trim().isEmpty() || a.equalsIgnoreCase("Unknown Artist") || a.equalsIgnoreCase("Unknown Author")) {
                try {
                    String parentName = track.getParentFile().getParentFile().getName();
                    String folderName = track.getParentFile().getName();

                    if (parentName != null && !parentName.equals("Music") && !parentName.equals("Audiobooks") && !parentName.equals("sdcard0") && !parentName.equals("Y1_Playlists")) {
                        a = parentName;
                    } else if (folderName != null && !folderName.equals("Music") && !folderName.equals("Audiobooks") && !folderName.equals("sdcard0") && !folderName.equals("Y1_Playlists")) {
                        a = folderName;
                    }
                } catch (Exception e) {}
            }

            if (t != null && !t.trim().isEmpty()) main.tvPlayerTitle.setText(t);
            else main.tvPlayerTitle.setText(safeFileName);

            if (a != null && !a.trim().isEmpty()) main.tvPlayerArtist.setText(a);
            else main.tvPlayerArtist.setText(track.getAbsolutePath().contains("/Audiobooks") || main.isAudiobookLibraryMode ? "Unknown Author" : "Unknown Artist");

            main.tvPlayerAlbum.setText(album != null && !album.trim().isEmpty() ? album.trim() : "");

            // 🚀 Last.fm 스크로블 후보 메타데이터 확정 (실제 duration이 나오는 시점에 onTrackStart 호출됨)
            pendingScrobbleArtist = a;
            pendingScrobbleTitle = (t != null && !t.trim().isEmpty()) ? t : safeFileName;
            pendingScrobbleAlbum = album;
            pendingScrobblePath = track.getAbsolutePath();


            // 🚀 동기식 렌더링으로 번쩍거림 없이 100% 매끄럽게 넘어갑니다.
            if (main.lastAlbumArtBytes != null && main.lastAlbumArtBytes.length > 0) {
                main.updateMainMenuBackground();
                main.refreshNowPlayingPreview();
                try {
                    android.graphics.BitmapFactory.Options optsCenter = new android.graphics.BitmapFactory.Options();
                    optsCenter.inSampleSize = 2;
                    android.graphics.Bitmap bmpCenter = android.graphics.BitmapFactory.decodeByteArray(main.lastAlbumArtBytes, 0, main.lastAlbumArtBytes.length, optsCenter);
                    main.ivAlbumArt.setImageBitmap(bmpCenter);

                    android.graphics.BitmapFactory.Options optsBg = new android.graphics.BitmapFactory.Options();
                    optsBg.inSampleSize = 4;
                    android.graphics.Bitmap sourceBg = android.graphics.BitmapFactory.decodeByteArray(main.lastAlbumArtBytes, 0, main.lastAlbumArtBytes.length, optsBg);
                    android.graphics.Bitmap blurredBg = main.applyGaussianBlur(sourceBg);
                    main.ivPlayerBgBlur.setImageBitmap(blurredBg);
                    if (sourceBg != blurredBg) sourceBg.recycle();

                    try {
                        int centerX = bmpCenter.getWidth() / 2;
                        int centerY = (int) (bmpCenter.getHeight() * 0.8);
                        main.currentAlbumColor = bmpCenter.getPixel(centerX, centerY) | 0xFF000000;
                    } catch (Exception e) {
                        main.currentAlbumColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000;
                    }
                } catch (Throwable e) {}
            }
            // 🚀 팟캐스트 전용 지름길: 폴더에 cover.jpg가 있으면 즉시 로드! (초고해상도 압축 방어막 장착)
            else if (isPodcast && podcastCoverFile != null && podcastCoverFile.exists()) {
                try {
                    // 1. 메모리에 올리기 전에 이미지의 '진짜 크기'만 몰래 알아옵니다.
                    android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    android.graphics.BitmapFactory.decodeFile(podcastCoverFile.getAbsolutePath(), opts);

                    // 2. 3000x3000 같은 괴물 해상도를 500x500 이하가 될 때까지 무자비하게 압축 비율(Scale)을 높입니다!
                    int scale = 1;
                    while (opts.outWidth / scale > 500 || opts.outHeight / scale > 500) {
                        scale *= 2;
                    }

                    // 3. 계산된 가벼운 압축 비율로 진짜 이미지를 메모리에 올립니다. (9MB -> 100KB 수준으로 다이어트!)
                    opts.inJustDecodeBounds = false;
                    opts.inSampleSize = scale;
                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(podcastCoverFile.getAbsolutePath(), opts);

                    main.ivAlbumArt.setImageBitmap(bmp);

                    // 4. 홀쭉해진 이미지로 배경 블러(RenderScript)를 0.05초 만에 초고속 처리!
                    android.graphics.Bitmap blurredBg = main.applyGaussianBlur(bmp);
                    main.ivPlayerBgBlur.setImageBitmap(blurredBg);

                    try {
                        int centerX = bmp.getWidth() / 2;
                        int centerY = (int) (bmp.getHeight() * 0.8);
                        main.currentAlbumColor = bmp.getPixel(centerX, centerY) | 0xFF000000;
                    } catch (Exception e) {
                        main.currentAlbumColor = com.themoon.y1.ThemeManager.getListButtonFocusedBg() | 0xFF000000;
                    }

                    main.updateMainMenuBackground();
                    main.refreshNowPlayingPreview();
                } catch (Exception e) {
                    main.applyCachedCoverArt(podcastCoverFile.getAbsolutePath()); // 에러 시 기존 방식 안전망
                }
            }
            // 일반 음악 전용: Y1_Covers 폴더에 다운로드된 이미지가 있으면 로드
            else if (coverFile.exists()) {
                main.applyCachedCoverArt(coverFile.getAbsolutePath());
            }
            else {
                // 🚀 [신규 장착!] (3순위) 파일 안에 사진은 없지만, 같은 폴더에 'cover.jpg'가 있을 때!
                File folderCover = main.findFolderCover(track.getParentFile());

                if (folderCover != null) {
                    main.applyCachedCoverArt(folderCover.getAbsolutePath()); // 폴더 이미지를 메인 화면에 예쁘게 적용!
                } else {
                    // (4순위) 다 없으면 최후의 수단으로 '기본 테마 이미지'를 띄우고 인터넷에 다운로드 명령을 내립니다!
                    main.ivAlbumArt.setImageResource(R.drawable.default_album);
                    main.currentAlbumColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000;
                    main.ivPlayerBgBlur.setImageResource(0);
                    main.updateMainMenuBackground();
                    main.refreshNowPlayingPreview();

                    // 🚀 [에러 해결] 제목(t)과 가수(a)가 'Unknown'이 아닌 정상적인 태그를 가지고 있는지 판별하는 센서 장착!
                    boolean hasValidTags = (t != null && !t.trim().isEmpty()) && (a != null && !a.trim().isEmpty() && !a.contains("Unknown"));

                    boolean isAutoFetchEnabled = main.prefs.getBoolean("auto_fetch", true);

                    // 💡 팟캐스트는 AutoFetch(인터넷 검색)를 돌리지 않고 건너뜁니다! (시간 딜레이 원천 차단)
                    if (!isPodcast && isAutoFetchEnabled) {
                        String searchQuery = hasValidTags ? (a + " " + t) : safeFileName.replace("-", " ").replace("_", " ");
                        main.fetchTrackInfoFromInternet(track, searchQuery, hasValidTags, t, a);
                    }
                }
            }
        } catch (Throwable t) {}

        // 🚀 (이 아래 try { if (isUsingLegacyPlayer) ... 엔진 가동 로직은 기존 코드 100% 동일하게 유지!)

        // 🚀 엔진 가동 구간
        try {
            if (isUsingLegacyPlayer) {
                // FLAC: 데드락 구출용 특수 엔진
                if (exoPlayer != null) { exoPlayer.stop(); exoPlayer.clearMediaItems(); }

                if (legacyPlayer == null) {
                    legacyPlayer = new MediaPlayer();
                    legacyPlayer.setWakeMode(main.getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
                    legacyPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                } else {
                    legacyPlayer.reset();
                }
                // 🚀 [매 트랙마다 재설정] 리스너를 최초 1회만 달면 클로저가 "그때 그 트랙"의 index/track을
                // 영원히 붙들고 있어서, 재사용되는 legacyPlayer 인스턴스로 다음 곡을 틀었을 때 에러가 나면
                // 엉뚱한(첫 곡의) index로 재시도하게 됩니다 - 매번 지금 로드 중인 트랙 값으로 새로 겁니다.
                legacyPlayer.setOnCompletionListener(mp -> handleTrackCompletion());
                legacyPlayer.setOnErrorListener((mp, what, extra) -> {
                    if (isFlac && flacLegacyFailedPaths.add(track.getAbsolutePath())) {
                        // 첫 실패: ExoPlayer의 FLAC 익스텐션으로 딱 한 번 재시도
                        prepareMusicTrack(index);
                    } else {
                        handleTrackError("Legacy Player Error: " + what + " (extra " + extra + ")");
                    }
                    return true;
                });

                if (currentFileInputStream != null) {
                    try { currentFileInputStream.close(); } catch (Exception e) {}
                }
                currentFileInputStream = new java.io.FileInputStream(track);
                legacyPlayer.setDataSource(currentFileInputStream.getFD());
                legacyPlayer.prepare(); // 💡 장전 완료!

                // 🚀 [핵심 로직 1] 쏘기 직전, 오디오북인지 검사하고 기억해둔 시간으로 강제 점프!
                int savedPos = main.prefs.getInt("book_pos_" + track.getAbsolutePath(), 0);
                if (savedPos > 0 && (main.isAudiobookLibraryMode || track.getAbsolutePath().contains("/Audiobooks"))) {
                    legacyPlayer.seekTo(savedPos);
                }

                if (!main.isPausedByHand) legacyPlayer.start();

                if (AudioEffectManager.getInstance() != null) AudioEffectManager.getInstance().applyAudioEffects();
                main.setupVisualizer();

                int duration = legacyPlayer.getDuration();
                int s = (duration / 1000) % 60;
                int m = (duration / (1000 * 60)) % 60;
                main.tvPlayerTimeTotal.setText(String.format(Locale.US, "%02d:%02d", m, s));
                com.themoon.y1.managers.LastFmScrobbler.getInstance(main)
                        .onTrackStart(pendingScrobbleArtist, pendingScrobbleTitle, pendingScrobbleAlbum, duration, pendingScrobblePath);

            } else if (isGaplessQueueActive) {
                // 🚀 [Gapless 재생] ExoPlayer가 이미 내부 큐에서 이 트랙으로 넘어가 있는 상태입니다 (직접
                // 넣어놓은 setMediaItems() 큐를 통해서든, onMediaItemTransition으로 자동으로든) - 여기서
                // 다시 stop/setMediaSource/prepare를 하면 오히려 끊김이 생기므로, 재장전 없이 그대로
                // 이어서 재생합니다. 위에서 이미 메타데이터/화면(제목, 아티스트, 커버 등)은 갱신되었습니다.
                exoPlayer.setPlaybackParameters(new PlaybackParameters(currentSpeed, 1.0f));
            } else {
                // MP3/WAV: 초고속 ExoPlayer
                if (legacyPlayer != null) { legacyPlayer.stop(); legacyPlayer.reset(); }

                if (exoPlayer == null) initPlayer(main.getApplicationContext());
                else exoPlayer.stop();

                com.google.android.exoplayer2.MediaItem mediaItem = com.google.android.exoplayer2.MediaItem.fromUri(Uri.fromFile(track));
                DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(main, Util.getUserAgent(main, "Y1_Launcher"));
                DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true);
                MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory).createMediaSource(mediaItem);

                exoPlayer.setMediaSource(mediaSource);
                exoPlayer.prepare(); // 💡 장전 완료!
// 🚀 [추가 확인사살] 곡을 장전할 때마다 현재 셔플 상태를 엑소 엔진에 강제 주입!
                boolean isShuffle = main.prefs.getBoolean("shuffle", false);
                exoPlayer.setShuffleModeEnabled(isShuffle);
                // 🚀 [핵심 로직 2] 쏘기 직전, 오디오북인지 검사하고 기억해둔 시간으로 강제 점프!
                int savedPos = main.prefs.getInt("book_pos_" + track.getAbsolutePath(), 0);
                if (savedPos > 0 && (main.isAudiobookLibraryMode || track.getAbsolutePath().contains("/Audiobooks"))) {
                    exoPlayer.seekTo(savedPos);
                }

                exoPlayer.setPlaybackParameters(new PlaybackParameters(currentSpeed, 1.0f));

                if (!main.isPausedByHand) exoPlayer.setPlayWhenReady(true);
            }

            main.consecutiveErrorCount = 0;
            // 🚀 iPod Classic 스타일: "01 / 14" 대신 "4 of 14" 형식으로 표시!
            main.tvPlayerTrackCount.setText((index + 1) + " of " + main.currentPlaylist.size());

        } catch (Throwable e) {
            main.consecutiveErrorCount++;
            String failReason = "Unknown Error";
            if (e instanceof OutOfMemoryError) failReason = "Album Art is too huge!";
            else if (e instanceof java.io.FileNotFoundException) failReason = "File not found";
            else if (e instanceof java.io.IOException) failReason = "Broken file";

            main.tvPlayerTitle.setText("Load Failed ❌");
            main.tvPlayerArtist.setText(failReason);
            main.tvPlayerAlbum.setText("");
            Toast.makeText(main, "🚨 " + failReason, Toast.LENGTH_SHORT).show();

            if (main.consecutiveErrorCount >= main.currentPlaylist.size()) {
                main.isPausedByHand = true;
                main.updatePlayerUI();
                main.consecutiveErrorCount = 0;
            } else {
                new Handler().postDelayed(() -> nextTrack(), 2000);
            }
        }
    }

    private void saveAudiobookBookmarkIfNeeded() {
        try {
            MainActivity main = MainActivity.instance;
            if (main != null && main.currentPlaylist != null && !main.currentPlaylist.isEmpty()) {
                if (main.currentIndex >= 0 && main.currentIndex < main.currentPlaylist.size()) {
                    String filePath = main.currentPlaylist.get(main.currentIndex).getAbsolutePath();

                    // 🚀 [수정] 오디오북 폴더, 팟캐스트 로컬 폴더, 팟캐스트 스트리밍 주소까지 모두 저장 허용!
                    if (filePath.startsWith("/storage/sdcard0/Audiobooks") || filePath.contains("/Podcasts") || filePath.startsWith("/PODCAST_STREAM") || main.isAudiobookLibraryMode) {
                        AudiobookManager.getInstance(main).saveBookmark(filePath, getCurrentPosition(), main.currentIndex);

                        main.prefs.edit()
                                .putInt("book_pos_" + filePath, getCurrentPosition())
                                .putInt("book_dur_" + filePath, getDuration())
                                .apply();
                    }
                }
            }
        } catch (Exception e) {}
    }

    public int getCurrentPosition() {
        if (isUsingLegacyPlayer && legacyPlayer != null) return legacyPlayer.getCurrentPosition();
        if (!isUsingLegacyPlayer && exoPlayer != null) {
            long pos = exoPlayer.getCurrentPosition();
            return pos < 0 ? 0 : (int) pos;
        }
        return 0;
    }

    public int getDuration() {
        if (isUsingLegacyPlayer && legacyPlayer != null) return legacyPlayer.getDuration();
        if (!isUsingLegacyPlayer && exoPlayer != null) {
            long duration = exoPlayer.getDuration();
            return duration < 0 ? 0 : (int) duration;
        }
        return 0;
    }

    public boolean isPlaying() {
        if (isUsingLegacyPlayer && legacyPlayer != null) return legacyPlayer.isPlaying();
        if (!isUsingLegacyPlayer && exoPlayer != null) return exoPlayer.getPlayWhenReady();
        return false;
    }

    public int getAudioSessionId() {
        if (isUsingLegacyPlayer && legacyPlayer != null) return legacyPlayer.getAudioSessionId();
        if (!isUsingLegacyPlayer && exoPlayer != null) return exoPlayer.getAudioSessionId();
        return 0;
    }

    public void releasePlayer() {
        if (exoPlayer != null) { exoPlayer.release(); exoPlayer = null; }
        if (legacyPlayer != null) { legacyPlayer.release(); legacyPlayer = null; }
        if (currentFileInputStream != null) { try { currentFileInputStream.close(); } catch (Exception e) {} }
    }

    // =======================================================
    // 🚀 [자체 제작 4.0] Ogg 껍데기 분쇄형 Opus 정밀 스캐너 (6종 메타데이터 싹쓸이)
    // =======================================================
    public Object[] extractOpusMetadata(File file) {
        return extractOpusMetadata(file, true);
    }

    // 🚀 [신규 추가] includeArt=false면 앨범 아트 디코딩을 건너뜁니다 - 라이브러리 대량 스캔 시
    // 수천 개 파일의 임베디드 아트를 매번 메모리에 올렸다 버리면서 생기는 힙 단편화/OOM을 방지!
    public Object[] extractOpusMetadata(File file, boolean includeArt) {
        // [0]제목, [1]가수, [2]앨범, [3]연도, [4]장르, [5]앨범아트(byte[]), [6]트랙번호, [7]가사(미사용), [8]앨범아티스트, [9]작곡가
        Object[] tags = new Object[]{null, null, null, null, null, null, null, null, null, null};
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] header = new byte[27];
            int totalRead = 0;

            // 🚀 1. 최대 1.5MB까지만 읽어서 Ogg 캡슐 껍데기를 싹 벗겨내고 순수 알맹이만 이어 붙입니다!
            while (totalRead < 1500000 && fis.read(header) == 27) {
                if (header[0] != 'O' || header[1] != 'g' || header[2] != 'g' || header[3] != 'S') break;

                int pageSegments = header[26] & 0xFF;
                byte[] segmentTable = new byte[pageSegments];
                fis.read(segmentTable);

                int pageSize = 0;
                for (int i = 0; i < pageSegments; i++) pageSize += (segmentTable[i] & 0xFF);

                byte[] pageData = new byte[pageSize];
                int read = fis.read(pageData);
                if (read > 0) bos.write(pageData, 0, read);

                totalRead += (27 + pageSegments + pageSize);
            }
            fis.close();

            // 🚀 2. 껍데기가 사라진 순수 텍스트 덩어리에서 명찰(OpusTags)을 찾습니다.
            byte[] buffer = bos.toByteArray();
            byte[] magic = "OpusTags".getBytes("UTF-8");
            int p = -1;
            for (int i = 0; i < buffer.length - magic.length; i++) {
                boolean match = true;
                for (int j = 0; j < magic.length; j++) {
                    if (buffer[i + j] != magic[j]) { match = false; break; }
                }
                if (match) { p = i; break; }
            }

            // 🚀 3. 태그 6종류 정밀 폭격 추출 가동!
            if (p != -1) {
                p += 8;
                int vendorLen = (buffer[p] & 0xFF) | ((buffer[p+1] & 0xFF) << 8) | ((buffer[p+2] & 0xFF) << 16) | ((buffer[p+3] & 0xFF) << 24);
                p += 4 + vendorLen;

                int commentsCount = (buffer[p] & 0xFF) | ((buffer[p+1] & 0xFF) << 8) | ((buffer[p+2] & 0xFF) << 16) | ((buffer[p+3] & 0xFF) << 24);
                p += 4;

                for (int i = 0; i < commentsCount && p < buffer.length - 4; i++) {
                    int commentLen = (buffer[p] & 0xFF) | ((buffer[p+1] & 0xFF) << 8) | ((buffer[p+2] & 0xFF) << 16) | ((buffer[p+3] & 0xFF) << 24);
                    p += 4;
                    if (commentLen <= 0 || p + commentLen > buffer.length) break;

                    String comment = new String(buffer, p, commentLen, "UTF-8");
                    p += commentLen;
                    String upper = comment.toUpperCase();

                    // 라이브러리 분류를 위한 5대 텍스트 수집!
                    // 라이브러리 분류를 위한 5대 텍스트 수집!
                    if (upper.startsWith("TITLE=")) tags[0] = comment.substring(6);
                    else if (upper.startsWith("ARTIST=") || upper.startsWith("AUTHOR=")) tags[1] = comment.substring(comment.indexOf("=") + 1); // 🚀 작가(AUTHOR) 태그 완벽 지원!
                    else if (upper.startsWith("ALBUM=")) tags[2] = comment.substring(6);
                    else if (upper.startsWith("DATE=") || upper.startsWith("YEAR=")) tags[3] = comment.substring(comment.indexOf("=") + 1);
                    else if (upper.startsWith("GENRE=")) tags[4] = comment.substring(6);
                    else if (upper.startsWith("TRACKNUMBER=") || upper.startsWith("TRACKNUM=")) tags[6] = comment.substring(comment.indexOf("=") + 1);
                    else if (upper.startsWith("ALBUMARTIST=") || upper.startsWith("ALBUM_ARTIST=")) tags[8] = comment.substring(comment.indexOf("=") + 1);
                    else if (upper.startsWith("COMPOSER=")) tags[9] = comment.substring(comment.indexOf("=") + 1);
                    else if (includeArt && upper.startsWith("METADATA_BLOCK_PICTURE=")) {
                        try {
                            // 🚀 공백, 줄바꿈 찌꺼기를 완벽히 지워 Base64 해독 성공률 100% 달성!
                            String base64Data = comment.substring(23).replaceAll("\\s", "");
                            byte[] flacPic = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);

                            int ptr = 4;
                            int mimeLen = ((flacPic[ptr] & 0xFF) << 24) | ((flacPic[ptr+1] & 0xFF) << 16) | ((flacPic[ptr+2] & 0xFF) << 8) | (flacPic[ptr+3] & 0xFF);
                            ptr += 4 + mimeLen;
                            int descLen = ((flacPic[ptr] & 0xFF) << 24) | ((flacPic[ptr+1] & 0xFF) << 16) | ((flacPic[ptr+2] & 0xFF) << 8) | (flacPic[ptr+3] & 0xFF);
                            ptr += 4 + descLen;
                            ptr += 16;
                            int picDataLen = ((flacPic[ptr] & 0xFF) << 24) | ((flacPic[ptr+1] & 0xFF) << 16) | ((flacPic[ptr+2] & 0xFF) << 8) | (flacPic[ptr+3] & 0xFF);
                            ptr += 4;

                            if (ptr + picDataLen <= flacPic.length) {
                                byte[] img = new byte[picDataLen];
                                System.arraycopy(flacPic, ptr, img, 0, picDataLen);
                                tags[5] = img; // 🎯 앨범 아트 최종 확보!
                            }
                        } catch (Exception e) {}
                    }
                }
            }
        } catch (Exception e) {}
        return tags;
    }
    // =======================================================
    // 🚀 [자체 제작 4.1] OGG Vorbis 스캐너 - Opus와 동일한 Ogg 컨테이너 + Vorbis Comment
    // 포맷을 쓰므로 파서 로직은 거의 동일합니다. 다른 점은 코멘트 헤더 매직바이트뿐!
    // (Opus="OpusTags", Vorbis=0x03 + "vorbis")
    // =======================================================
    public Object[] extractOggVorbisMetadata(File file) {
        return extractOggVorbisMetadata(file, true);
    }

    public Object[] extractOggVorbisMetadata(File file, boolean includeArt) {
        // [0]제목, [1]가수, [2]앨범, [3]연도, [4]장르, [5]앨범아트(byte[]), [6]트랙번호, [7]가사(미사용), [8]앨범아티스트, [9]작곡가
        Object[] tags = new Object[]{null, null, null, null, null, null, null, null, null, null};
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] header = new byte[27];
            int totalRead = 0;

            while (totalRead < 1500000 && fis.read(header) == 27) {
                if (header[0] != 'O' || header[1] != 'g' || header[2] != 'g' || header[3] != 'S') break;

                int pageSegments = header[26] & 0xFF;
                byte[] segmentTable = new byte[pageSegments];
                fis.read(segmentTable);

                int pageSize = 0;
                for (int i = 0; i < pageSegments; i++) pageSize += (segmentTable[i] & 0xFF);

                byte[] pageData = new byte[pageSize];
                int read = fis.read(pageData);
                if (read > 0) bos.write(pageData, 0, read);

                totalRead += (27 + pageSegments + pageSize);
            }
            fis.close();

            byte[] buffer = bos.toByteArray();

            // 🚨 [치명적 버그 수리] ".ogg" 확장자라고 해서 진짜 Vorbis라는 보장이 없습니다!
            // 일부 변환 도구는 실제로는 Opus로 인코딩하고서도 확장자만 .ogg로 저장합니다.
            // 첫 페이지 알맹이가 "OpusHead"로 시작하면 진짜 정체는 Opus이므로 그쪽 파서로 넘깁니다!
            byte[] opusMagic = "OpusHead".getBytes("UTF-8");
            if (buffer.length >= opusMagic.length) {
                boolean isActuallyOpus = true;
                for (int j = 0; j < opusMagic.length; j++) {
                    if (buffer[j] != opusMagic[j]) { isActuallyOpus = false; break; }
                }
                if (isActuallyOpus) {
                    return extractOpusMetadata(file, includeArt);
                }
            }

            // 🚀 Vorbis 코멘트 헤더 패킷의 매직: 패킷타입(0x03) + "vorbis" (식별 헤더는 0x01이라 안 겹칩니다!)
            byte[] magic = new byte[]{0x03, 'v', 'o', 'r', 'b', 'i', 's'};
            int p = -1;
            for (int i = 0; i < buffer.length - magic.length; i++) {
                boolean match = true;
                for (int j = 0; j < magic.length; j++) {
                    if (buffer[i + j] != magic[j]) { match = false; break; }
                }
                if (match) { p = i; break; }
            }

            if (p != -1) {
                p += magic.length;
                int vendorLen = (buffer[p] & 0xFF) | ((buffer[p+1] & 0xFF) << 8) | ((buffer[p+2] & 0xFF) << 16) | ((buffer[p+3] & 0xFF) << 24);
                p += 4 + vendorLen;

                int commentsCount = (buffer[p] & 0xFF) | ((buffer[p+1] & 0xFF) << 8) | ((buffer[p+2] & 0xFF) << 16) | ((buffer[p+3] & 0xFF) << 24);
                p += 4;

                for (int i = 0; i < commentsCount && p < buffer.length - 4; i++) {
                    int commentLen = (buffer[p] & 0xFF) | ((buffer[p+1] & 0xFF) << 8) | ((buffer[p+2] & 0xFF) << 16) | ((buffer[p+3] & 0xFF) << 24);
                    p += 4;
                    if (commentLen <= 0 || p + commentLen > buffer.length) break;

                    String comment = new String(buffer, p, commentLen, "UTF-8");
                    p += commentLen;
                    String upper = comment.toUpperCase();

                    if (upper.startsWith("TITLE=")) tags[0] = comment.substring(6);
                    else if (upper.startsWith("ARTIST=") || upper.startsWith("AUTHOR=")) tags[1] = comment.substring(comment.indexOf("=") + 1);
                    else if (upper.startsWith("ALBUM=")) tags[2] = comment.substring(6);
                    else if (upper.startsWith("DATE=") || upper.startsWith("YEAR=")) tags[3] = comment.substring(comment.indexOf("=") + 1);
                    else if (upper.startsWith("GENRE=")) tags[4] = comment.substring(6);
                    else if (upper.startsWith("TRACKNUMBER=") || upper.startsWith("TRACKNUM=")) tags[6] = comment.substring(comment.indexOf("=") + 1);
                    else if (upper.startsWith("ALBUMARTIST=") || upper.startsWith("ALBUM_ARTIST=")) tags[8] = comment.substring(comment.indexOf("=") + 1);
                    else if (upper.startsWith("COMPOSER=")) tags[9] = comment.substring(comment.indexOf("=") + 1);
                    else if (includeArt && upper.startsWith("METADATA_BLOCK_PICTURE=")) {
                        try {
                            String base64Data = comment.substring(23).replaceAll("\\s", "");
                            byte[] flacPic = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);

                            int ptr = 4;
                            int mimeLen = ((flacPic[ptr] & 0xFF) << 24) | ((flacPic[ptr+1] & 0xFF) << 16) | ((flacPic[ptr+2] & 0xFF) << 8) | (flacPic[ptr+3] & 0xFF);
                            ptr += 4 + mimeLen;
                            int descLen = ((flacPic[ptr] & 0xFF) << 24) | ((flacPic[ptr+1] & 0xFF) << 16) | ((flacPic[ptr+2] & 0xFF) << 8) | (flacPic[ptr+3] & 0xFF);
                            ptr += 4 + descLen;
                            ptr += 16;
                            int picDataLen = ((flacPic[ptr] & 0xFF) << 24) | ((flacPic[ptr+1] & 0xFF) << 16) | ((flacPic[ptr+2] & 0xFF) << 8) | (flacPic[ptr+3] & 0xFF);
                            ptr += 4;

                            if (ptr + picDataLen <= flacPic.length) {
                                byte[] img = new byte[picDataLen];
                                System.arraycopy(flacPic, ptr, img, 0, picDataLen);
                                tags[5] = img;
                            }
                        } catch (Exception e) {}
                    }
                    // 🚀 [레거시 지원] 구형 Vorbis 태거들은 METADATA_BLOCK_PICTURE 대신 COVERART(base64 원본 이미지)를 씁니다.
                    else if (includeArt && upper.startsWith("COVERART=")) {
                        try {
                            String base64Data = comment.substring(9).replaceAll("\\s", "");
                            tags[5] = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
                        } catch (Exception e) {}
                    }
                }
            }
        } catch (Exception e) {}
        return tags;
    }
    // =======================================================
    // 🚀 [자체 제작 6.0] FLAC 6기통 만능 채굴기 (앨범, 연도, 장르, 가사 완벽 지원)
    // =======================================================
    public Object[] extractFlacMetadata(File file) {
        return extractFlacMetadata(file, true);
    }

    // 🚀 [신규 추가] includeArt=false면 앨범 아트 디코딩을 건너뜁니다 - 대량 스캔 시 OOM 방지!
    public Object[] extractFlacMetadata(File file, boolean includeArt) {
        // 🚨 [치명적 버그 수리 완료] 바구니 크기를 6칸에서 8칸으로 늘려 앱 강제 종료(Crash)를 막습니다!
        // [0]제목, [1]가수, [2]앨범, [3]연도, [4]장르, [5]앨범아트, [6]트랙번호, [7]가사, [8]앨범아티스트, [9]작곡가
        Object[] tags = new Object[]{null, null, null, null, null, null, null, null, null, null};
        try {
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r");
            byte[] header = new byte[4];
            raf.readFully(header);

            if (header[0] != 'f' || header[1] != 'L' || header[2] != 'a' || header[3] != 'C') {
                raf.close(); return tags;
            }

            boolean isLast = false;
            while (!isLast) {
                int blockHeader = raf.readUnsignedByte();
                isLast = (blockHeader & 0x80) != 0;
                int blockType = blockHeader & 0x7F;
                int length = (raf.readUnsignedByte() << 16) | (raf.readUnsignedByte() << 8) | raf.readUnsignedByte();

                if (blockType == 4) { // 🚀 텍스트 정보 추출 (Vorbis Comment)
                    byte[] commentData = new byte[length];
                    raf.readFully(commentData);
                    try {
                        int p = 0;
                        int vendorLen = (commentData[p]&0xFF) | ((commentData[p+1]&0xFF)<<8) | ((commentData[p+2]&0xFF)<<16) | ((commentData[p+3]&0xFF)<<24);
                        p += 4 + vendorLen;
                        int listLen = (commentData[p]&0xFF) | ((commentData[p+1]&0xFF)<<8) | ((commentData[p+2]&0xFF)<<16) | ((commentData[p+3]&0xFF)<<24);
                        p += 4;
                        for (int i = 0; i < listLen && p < commentData.length - 4; i++) {
                            int strLen = (commentData[p]&0xFF) | ((commentData[p+1]&0xFF)<<8) | ((commentData[p+2]&0xFF)<<16) | ((commentData[p+3]&0xFF)<<24);
                            p += 4;
                            String comment = new String(commentData, p, strLen, "UTF-8");
                            p += strLen;
                            String upper = comment.toUpperCase();

                            // 🚀 대망의 텍스트 수집 (가사 포함!)
                            if (upper.startsWith("TITLE=")) tags[0] = comment.substring(6);
                            else if (upper.startsWith("ARTIST=") || upper.startsWith("AUTHOR=")) tags[1] = comment.substring(comment.indexOf("=") + 1);
                            else if (upper.startsWith("ALBUM=")) tags[2] = comment.substring(6);
                            else if (upper.startsWith("DATE=") || upper.startsWith("YEAR=")) tags[3] = comment.substring(comment.indexOf("=") + 1);
                            else if (upper.startsWith("GENRE=")) tags[4] = comment.substring(6);
                            else if (upper.startsWith("TRACKNUMBER=") || upper.startsWith("TRACKNUM=")) tags[6] = comment.substring(comment.indexOf("=") + 1);
                                // 🎯 [신규 장착] FLAC 내부에 LYRICS 라는 이름으로 박혀있는 가사 텍스트를 무자비하게 캐옵니다!
                            else if (upper.startsWith("LYRICS=")) tags[7] = comment.substring(7);
                            else if (upper.startsWith("ALBUMARTIST=") || upper.startsWith("ALBUM_ARTIST=")) tags[8] = comment.substring(comment.indexOf("=") + 1);
                            else if (upper.startsWith("COMPOSER=")) tags[9] = comment.substring(comment.indexOf("=") + 1);
                        }
                    } catch (Exception e) {}
                } else if (blockType == 6) { // 🚀 사진 추출
                    if (includeArt) {
                        int picType = raf.readInt();
                        int mimeLen = raf.readInt(); raf.skipBytes(mimeLen);
                        int descLen = raf.readInt(); raf.skipBytes(descLen);
                        raf.skipBytes(16);
                        int picDataLen = raf.readInt();
                        byte[] picData = new byte[picDataLen];
                        raf.readFully(picData);
                        tags[5] = picData; // 🎯 사진 데이터
                    } else {
                        raf.skipBytes(length); // 🚀 대량 스캔 중에는 무거운 사진 바이트를 아예 읽지 않고 건너뜁니다!
                    }
                } else {
                    raf.skipBytes(length);
                }
            }
            raf.close();
        } catch (Exception e) {}
        return tags;
    }
    // =======================================================
    // 🚀 [자체 제작 7.0] ALAC / M4A (Apple Lossless) 원자 단위 정밀 해독기!
    // 애플의 악랄한 moov -> udta -> meta -> ilst 트리 구조를 추적하여 태그와 앨범 아트를 싹쓸이합니다.
    // =======================================================
    public Object[] extractAlacMetadata(File file) {
        return extractAlacMetadata(file, true);
    }

    // 🚀 [신규 추가] includeArt=false면 앨범 아트 디코딩을 건너뜁니다 - 대량 스캔 시 OOM 방지!
    public Object[] extractAlacMetadata(File file, boolean includeArt) {
        // 💡 [수정] null을 10개로 맞춰야 앱이 터지지 않습니다! (8번째 자리: 앨범아티스트, 9번째: 작곡가)
        Object[] tags = new Object[]{null, null, null, null, null, null, null, null, null, null};
        try {
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r");
            long fileSize = raf.length();
            long pos = 0;

            // 🚀 MP4 원자(Atom) 구조 탐색 크롤러 가동!
            while (pos < fileSize) {
                raf.seek(pos);
                int size = raf.readInt();
                if (size <= 0) break; // 파일 끝이거나 구조가 깨졌으면 탈출

                byte[] typeBytes = new byte[4];
                raf.readFully(typeBytes);

                // 🚨 [치명적 버그 수정!] '©' 기호가 UTF-8에서 깨지는 현상을 막기 위해 ISO-8859-1 렌즈를 장착합니다!
                String type = new String(typeBytes, "ISO-8859-1");

                // 💡 1. 껍데기(컨테이너) 원자를 발견하면 크기(Size)를 건너뛰고 내부로 파고듭니다!
                if (type.equals("moov") || type.equals("udta") || type.equals("meta") || type.equals("ilst")) {
                    if (type.equals("meta")) pos += 4; // meta 원자는 4바이트의 버전/플래그가 숨어있으므로 회피
                    pos += 8; // Size(4) + Type(4) 만큼 전진해서 알맹이로 진입!
                    continue;
                }

                // 💡 조건문에 type.equals("©lyr"), type.equals("aART") 추가 완료!
                // 🚀 includeArt가 false면 covr(앨범 아트) 원자는 아예 무거운 읽기 없이 건너뜁니다!
                if (type.equals("©nam") || type.equals("©ART") || type.equals("©alb") ||
                        type.equals("©day") || type.equals("©gen") || (includeArt && type.equals("covr")) ||
                        type.equals("trkn") || type.equals("©lyr") || type.equals("aART") || type.equals("©wrt")) {

                    raf.seek(pos + 8);
                    int dataSize = raf.readInt();
                    byte[] dataTypeBytes = new byte[4];
                    raf.readFully(dataTypeBytes);

                    // 🚨 [여기서도 수정!] 이름표는 무조건 ISO-8859-1 렌즈로 읽습니다.
                    String dataType = new String(dataTypeBytes, "ISO-8859-1");

                    // 실제 값이 들어있는 'data' 구역인지 확인
                    if (dataType.equals("data")) {
                        raf.skipBytes(8); // 버전, 플래그, 빈 공간(Null padding) 8바이트 스킵
                        int actualDataSize = dataSize - 16;

                        // 데이터가 10MB 이하일 때만 읽어옵니다 (메모리 폭발 방지)
                        if (actualDataSize > 0 && actualDataSize < 10485760) {
                            byte[] data = new byte[actualDataSize];
                            raf.readFully(data);

                            // 🎯 알맹이(진짜 텍스트)는 UTF-8 인코딩이 맞으므로 그대로 파싱합니다!
                            if (type.equals("©nam")) tags[0] = new String(data, "UTF-8"); // 제목
                            else if (type.equals("©ART")) tags[1] = new String(data, "UTF-8"); // 가수
                            else if (type.equals("©alb")) tags[2] = new String(data, "UTF-8"); // 앨범
                            else if (type.equals("©day")) tags[3] = new String(data, "UTF-8"); // 연도
                            else if (type.equals("©gen")) tags[4] = new String(data, "UTF-8"); // 장르
                            else if (type.equals("covr")) tags[5] = data; // 앨범 아트
                            else if (type.equals("trkn") && actualDataSize >= 4) {
                                tags[6] = String.valueOf((int) data[3]); // 트랙 번호
                            }
                            // 🚀 [신규 장착] 대망의 가사 채굴! UTF-8 텍스트로 변환하여 8번째 바구니에 담습니다!
                            else if (type.equals("©lyr")) {
                                tags[7] = new String(data, "UTF-8");
                            }
                            // 🚀 [신규 장착] 앨범 아티스트(aART) 채굴!
                            else if (type.equals("aART")) {
                                tags[8] = new String(data, "UTF-8");
                            }
                            // 🚀 [iPod 스타일] 작곡가(©wrt) 채굴!
                            else if (type.equals("©wrt")) {
                                tags[9] = new String(data, "UTF-8");
                            }
                        }
                    }
                }
                pos += size; // 💡 현재 원자 탐색이 끝났으면 다음 원자로 훌쩍 건너뜁니다!
            }
            raf.close();
        } catch (Exception e) {}
        return tags;
    }
}