package com.themoon.y1.managers;

import android.content.Context;
import android.net.Uri;
import android.view.SurfaceView;

import com.themoon.y1.MainActivity;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IVLCVout;

import java.io.File;
import java.util.ArrayList;

// 🚀 [비디오 전용 엔진 - VLC로 교체] ExoPlayer는 이 기기(Android API 17)에서 프레임을 정확한 시간에
// 내보내는 API(releaseOutputBuffer with timestamp)가 API 21부터만 있어서, 그 이전 기기용 폴백
// 타이밍 경로가 사실상 거의 테스트되지 않은 채로 남아있었습니다 - 오디오/포지션 시계는 정상인데
// 화면만 디코더가 뽑아내는 대로 배속으로 흘러가 버리는 버그가 바로 이것 때문이었습니다.
// VLC는 자체 네이티브 AV 싱크 엔진을 쓰기 때문에 이 문제를 완전히 우회합니다.
// 음악 재생(AudioPlayerManager)은 계속 ExoPlayer를 그대로 씁니다 - 여긴 손대지 않습니다.
public class VideoPlayerManager {
    private static VideoPlayerManager instance;
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private SurfaceView attachedSurfaceView;
    private File currentVideoFile;

    // 🚀 [이어보기] 나중에 이 파일을 다시 열었을 때 몇 초부터 이어볼지 저장해둘 최소/최대 임계값 -
    // 너무 초반(3초 미만)이거나 이미 거의 다 봤으면(끝나기 5초 전 이내) 저장하지 않고, 대신 처음부터
    // 다시 봅니다(오디오북 북마크와 동일한 사고방식).
    private static final long RESUME_MIN_POS_MS = 3000;
    private static final long RESUME_END_MARGIN_MS = 5000;

    public static VideoPlayerManager getInstance() {
        if (instance == null) instance = new VideoPlayerManager();
        return instance;
    }

    private VideoPlayerManager() {}

    private void ensurePlayer(Context context) {
        if (libVLC == null) {
            ArrayList<String> options = new ArrayList<>();
            libVLC = new LibVLC(context.getApplicationContext(), options);
            mediaPlayer = new MediaPlayer(libVLC);
        }
    }

    public void playVideo(Context context, final File videoFile, final SurfaceView surfaceView) {
        ensurePlayer(context);

        // 🚀 [이어보기] 지금까지 보던 파일을 떠나기 직전에 그 위치를 저장합니다 (같은 파일을 다시 여는
        // 경우, 예: 재생목록 없이 그냥 같은 영상을 다시 눌렀을 때는 저장하지 않습니다).
        if (currentVideoFile != null && !currentVideoFile.equals(videoFile)) {
            saveResumePositionIfNeeded();
        }
        currentVideoFile = videoFile;

        // 🚀 뷰가 바뀌었으면(다른 영상 화면 재진입 등) 기존 연결을 정리하고 새로 붙입니다.
        if (attachedSurfaceView != surfaceView) {
            IVLCVout oldVout = mediaPlayer.getVLCVout();
            if (oldVout.areViewsAttached()) oldVout.detachViews();
            attachedSurfaceView = surfaceView;
        }

        final IVLCVout vlcVout = mediaPlayer.getVLCVout();
        if (!vlcVout.areViewsAttached()) {
            vlcVout.setVideoView(surfaceView);
            vlcVout.attachViews();
        }

        // 🚀 [화면 꽉 채우기 버그 수정] setWindowSize()를 안 불러주면 VLC가 실제 화면 크기를 몰라서
        // 원본 해상도 그대로(예: 320x240) 작게 그려버립니다 - SurfaceView가 아직 레이아웃을 마치지
        // 않았을 수 있으므로 post()로 실제 크기가 잡힌 뒤에 넘겨줍니다.
        // 🚀 [화면비 옵션] "화면 채우기(Stretch)"가 켜져 있으면 여기서 실제로 잡힌 뷰 크기를 그대로
        // 목표 비율로 강제해서(letterbox 없이) 화면을 완전히 채웁니다 - 원본이 아니면 비율이 눌리거나
        // 늘어날 수 있지만, 검은 여백 없이 꽉 채워 보고 싶다는 요청(Issue #7)에 대한 답입니다.
        surfaceView.post(new Runnable() {
            @Override
            public void run() {
                int w = surfaceView.getWidth();
                int h = surfaceView.getHeight();
                if (w > 0 && h > 0) {
                    vlcVout.setWindowSize(w, h);
                    boolean stretchFill = MainActivity.instance != null
                            && MainActivity.instance.prefs.getBoolean("video_stretch_fill", false);
                    mediaPlayer.setAspectRatio(stretchFill ? (w + ":" + h) : null);
                }
            }
        });

        mediaPlayer.stop();
        Media media = new Media(libVLC, Uri.fromFile(videoFile));
        mediaPlayer.setMedia(media);
        media.release();
        // 🚀 [화면비 옵션] Best Fit(기본값): 원본 비율 유지, 화면비 강제는 위 post() 콜백에서 처리합니다.
        mediaPlayer.setScale(0);
        mediaPlayer.play();

        // 🚀 [이어보기] 이 파일에 저장된 위치가 있으면 재생 시작과 동시에 그 지점으로 점프합니다.
        if (MainActivity.instance != null) {
            long savedPos = MainActivity.instance.prefs.getLong("video_pos_" + videoFile.getAbsolutePath(), 0);
            if (savedPos > 0) {
                mediaPlayer.setTime(savedPos);
            }
        }
    }

    public void togglePlayPause() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            saveResumePositionIfNeeded();
            mediaPlayer.pause();
        } else {
            mediaPlayer.play();
        }
    }

    // 🚀 [이어보기] 오디오북 북마크와 동일한 사고방식: 너무 초반이거나 이미 거의 다 봤으면 저장하지
    // 않고(=처음부터 다시 보기), 그 사이 구간이면 현재 위치를 기억해둡니다.
    private void saveResumePositionIfNeeded() {
        if (mediaPlayer == null || currentVideoFile == null || MainActivity.instance == null) return;
        long pos = mediaPlayer.getTime();
        long dur = mediaPlayer.getLength();
        String key = "video_pos_" + currentVideoFile.getAbsolutePath();
        if (pos < RESUME_MIN_POS_MS || (dur > 0 && pos > dur - RESUME_END_MARGIN_MS)) {
            MainActivity.instance.prefs.edit().remove(key).apply();
        } else {
            MainActivity.instance.prefs.edit().putLong(key, pos).apply();
        }
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public void seekRelative(long deltaMs) {
        if (mediaPlayer == null) return;
        long target = mediaPlayer.getTime() + deltaMs;
        long duration = mediaPlayer.getLength();
        if (duration > 0) target = Math.max(0, Math.min(target, duration));
        else target = Math.max(0, target);
        mediaPlayer.setTime(target);
    }

    public long getCurrentPosition() {
        return mediaPlayer != null ? mediaPlayer.getTime() : 0;
    }

    public long getDuration() {
        return mediaPlayer != null ? Math.max(0, mediaPlayer.getLength()) : 0;
    }

    public void stopAndRelease() {
        if (mediaPlayer != null) {
            saveResumePositionIfNeeded();
            mediaPlayer.stop();
            IVLCVout vlcVout = mediaPlayer.getVLCVout();
            if (vlcVout.areViewsAttached()) vlcVout.detachViews();
            attachedSurfaceView = null;
        }
        currentVideoFile = null;
    }
}
