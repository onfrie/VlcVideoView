package org.videolan.vlc;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.view.TextureView;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.vlc.listener.MediaListenerEvent;
import org.videolan.vlc.listener.MediaPlayerControl;
import org.videolan.vlc.util.L;

public class VlcVideoView extends TextureView implements MediaPlayerControl, TextureView.SurfaceTextureListener {
    private VlcPlayer videoMediaLogic;
    private PlayStateImpl mPlayStateCallback;
    private final String tag = "VideoView";

    public VlcVideoView(Context context) {
        this(context, null);
    }

    public VlcVideoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VlcVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (isInEditMode()) {
            return;
        }
        init(context);
    }

    public void setMediaListenerEvent(MediaListenerEvent mediaListenerEvent) {
        videoMediaLogic.setMediaListenerEvent(mediaListenerEvent);
    }

    @Override
    public boolean canControl() {
        return videoMediaLogic.canControl();
    }

    /**
     * 关闭avtivity时 停止时用
     */
    public void onStop() {
        videoMediaLogic.onStop();
    }

    /**
     * 退出应用时回收
     */
    public void onDestory() {
        if (videoMediaLogic != null)
            videoMediaLogic.onDestory();
        L.i(tag, "onDestory");
    }

    private void init(Context context) {
        videoMediaLogic = new VlcPlayer(context);
        videoMediaLogic.setPlayerStateCallback(mPlayerStateCallback);
        setSurfaceTextureListener(this);
    }

    public void setMediaPlayer(LibVLC libVLC) {
        videoMediaLogic.setMediaPlayer(libVLC);
    }

    public void setMedia(Media media) {
        videoMediaLogic.setMedia(media);
    }

    @Override
    public boolean isPrepare() {
        return videoMediaLogic.isPrepare();
    }

    @Override
    public void startPlay(String path, long seekTime, float speed) {
        videoMediaLogic.startPlay(path, seekTime, speed);
    }

    public void saveState() {
        videoMediaLogic.saveState();
    }

    @Override
    public void start() {
        videoMediaLogic.start();
    }

    @Override
    public void pause() {
        videoMediaLogic.pause();
    }

    @Override
    public long getDuration() {
        return videoMediaLogic.getDuration();
    }

    @Override
    public long getCurrentPosition() {
        return videoMediaLogic.getCurrentPosition();
    }

    @Override
    public int getPlayState() {
        return videoMediaLogic.getPlayState();
    }

    @Override
    public void seekTo(long pos) {
        videoMediaLogic.seekTo(pos);
    }

    @Override
    public boolean isPlaying() {
        return videoMediaLogic.isPlaying();
    }

    @Override
    public void setMirror(boolean mirror) {
        this.mirror = mirror;
        if (mirror) {
            setScaleX(-1f);
        } else {
            setScaleX(1f);
        }
    }

    private boolean mirror = false;

    @Override
    public boolean getMirror() {
        return mirror;
    }


    @Override
    public int getBufferPercentage() {
        return videoMediaLogic.getBufferPercentage();
    }

    @Override
    public boolean setPlaybackSpeedMedia(float speed) {
        return videoMediaLogic.setPlaybackSpeedMedia(speed);
    }

    @Override
    public float getPlaybackSpeed() {
        return videoMediaLogic.getPlaybackSpeed();
    }


    @Override
    public void setLoop(boolean isLoop) {
        videoMediaLogic.setLoop(isLoop);
    }

    @Override
    public boolean isLoop() {
        return videoMediaLogic.isLoop();
    }


    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        L.i(tag, "onSurfaceTextureAvailable");
        videoMediaLogic.setSurface(width, height, surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        L.i(tag, "onSurfaceTextureSizeChanged");
        videoMediaLogic.setWindowSize(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        L.i(tag, "onSurfaceTextureDestroyed");
        videoMediaLogic.onSurfaceTextureDestroyed();
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    //根据播放状态 打开关闭旋转动画
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        L.i(tag, "onAttachedToWindow");
        if (isInEditMode()) {
            return;
        }
        setKeepScreenOn(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        L.i(tag, "onDetachedFromWindow");
        if (isInEditMode()) {
            return;
        }
        setKeepScreenOn(false);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }


    VlcPlayer.PlayerStateImpl mPlayerStateCallback = new VlcPlayer.PlayerStateImpl() {
        @Override
        public void setPlayerState(int playerState) {
            mPlayStateCallback.setPlayState(playerState);
        }
    };

    public void setPlayStateCallback(PlayStateImpl playStateCallback) {
        mPlayStateCallback = playStateCallback;
    }

    public interface PlayStateImpl {
        void setPlayState(int playState);
    }
}