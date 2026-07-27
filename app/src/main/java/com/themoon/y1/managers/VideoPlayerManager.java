package com.themoon.y1.managers;

import android.content.Context;
import android.net.Uri;
import android.view.SurfaceView;

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

    public void playVideo(Context context, File videoFile, final SurfaceView surfaceView) {
        ensurePlayer(context);

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
        surfaceView.post(new Runnable() {
            @Override
            public void run() {
                int w = surfaceView.getWidth();
                int h = surfaceView.getHeight();
                if (w > 0 && h > 0) {
                    vlcVout.setWindowSize(w, h);
                }
            }
        });

        mediaPlayer.stop();
        Media media = new Media(libVLC, Uri.fromFile(videoFile));
        mediaPlayer.setMedia(media);
        media.release();
        // 🚀 화면을 꽉 채우도록 비율 유지 + 최적 크기(Best-fit) 스케일링 (기본값이지만 명시적으로 지정)
        mediaPlayer.setAspectRatio(null);
        mediaPlayer.setScale(0);
        mediaPlayer.play();
    }

    public void togglePlayPause() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) mediaPlayer.pause();
        else mediaPlayer.play();
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
            mediaPlayer.stop();
            IVLCVout vlcVout = mediaPlayer.getVLCVout();
            if (vlcVout.areViewsAttached()) vlcVout.detachViews();
            attachedSurfaceView = null;
        }
    }
}
