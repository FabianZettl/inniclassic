package com.themoon.y1.managers;

import android.content.Context;
import android.net.Uri;
import android.view.SurfaceView;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import java.io.File;

// 🚀 [신규 추가] 비디오 전용 재생 엔진 - AudioPlayerManager와 완전히 분리된 별도 ExoPlayer 인스턴스를 씁니다.
// 음악 재생 로직(FLAC 특수 엔진, 트랙 큐 등)과 얽히지 않도록 독립적으로 구성했습니다.
public class VideoPlayerManager {
    private static VideoPlayerManager instance;
    private SimpleExoPlayer exoPlayer;
    private Context appContext;

    public static VideoPlayerManager getInstance() {
        if (instance == null) instance = new VideoPlayerManager();
        return instance;
    }

    private VideoPlayerManager() {}

    private void ensurePlayer(Context context) {
        if (exoPlayer == null) {
            appContext = context.getApplicationContext();
            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(appContext);
            exoPlayer = new SimpleExoPlayer.Builder(appContext, renderersFactory).build();
        }
    }

    public void playVideo(Context context, File videoFile, SurfaceView surfaceView) {
        ensurePlayer(context);
        exoPlayer.setVideoSurfaceView(surfaceView);

        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(appContext,
                Util.getUserAgent(appContext, "InniClassic"));
        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();

        MediaItem mediaItem = MediaItem.fromUri(Uri.fromFile(videoFile));
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                .createMediaSource(mediaItem);

        exoPlayer.stop();
        exoPlayer.clearMediaItems();
        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
    }

    public void togglePlayPause() {
        if (exoPlayer == null) return;
        exoPlayer.setPlayWhenReady(!exoPlayer.getPlayWhenReady());
    }

    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.getPlayWhenReady();
    }

    public void seekRelative(long deltaMs) {
        if (exoPlayer == null) return;
        long target = exoPlayer.getCurrentPosition() + deltaMs;
        long duration = exoPlayer.getDuration();
        if (duration > 0) target = Math.max(0, Math.min(target, duration));
        else target = Math.max(0, target);
        exoPlayer.seekTo(target);
    }

    public long getCurrentPosition() {
        return exoPlayer != null ? exoPlayer.getCurrentPosition() : 0;
    }

    public long getDuration() {
        return exoPlayer != null ? Math.max(0, exoPlayer.getDuration()) : 0;
    }

    public void stopAndRelease() {
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        }
    }
}
