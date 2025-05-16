package org.videolan.vlc;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.media.AudioManager;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.litao.slider.NiftySlider;

import org.videolan.R;
import org.videolan.vlc.listener.MediaListenerEvent;
import org.videolan.vlc.util.CoreUtil;
import org.videolan.vlc.util.DensityUtil;
import org.videolan.vlc.util.EnumConfig;
import org.videolan.vlc.util.L;
import org.videolan.vlc.util.SpUtils;
import org.videolan.vlc.util.VLCInstance;

import java.io.File;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;

import androidx.constraintlayout.widget.ConstraintLayout;
import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import kotlin.Unit;
import kotlin.jvm.functions.Function3;

import static org.videolan.vlc.util.EnumConfig.PlayState.STATE_LOAD;
import static org.videolan.vlc.util.EnumConfig.PlayState.STATE_PLAY;
import static org.videolan.vlc.util.SpConfig.PREFIX_VIDEO_PLAY_POSITION;

public class MyVlcVideoView extends RelativeLayout implements SeekBar.OnSeekBarChangeListener, GestureDetector.OnGestureListener, View.OnClickListener {

    //控件，因为不是activity，无法正常使用ButterKnife
    private VlcVideoView mVideoView;
    private RelativeLayout mRootLayout;
    private LinearLayout mBufferingLayout;
    private ConstraintLayout mVideoTopLayout;
    private LinearLayout mVideoBottomLayout;
    private LinearLayout mVideoSpeedLayout;
    private ImageView mVideoBack;
    private SeekBar mVideoSeekBar;
    private TextView mVideoTimeText;
    private ImageView mVideoStateImg;
//    private TextView mVideoTitleText;
    private LinearLayout mVideoGestureLayout;
    private ImageView mVideoGestureImg;
    private TextView mVideoGestureText;
    private ImageView mVideoFullImg;
    private TextView mErrorText;
    private TextView mSpeedAdd;
    private TextView mSpeedDecrease;
    private TextView mSpeedText;
    private TextView mBufferingText;

    private NiftySlider seekbar3XLeft;
    private NiftySlider seekbar3XRight;
    private NiftySlider seekbar3Y;

    //播放器手势
    private GestureDetector mGestureDetector;
    private boolean mIsProgressChange = false;    //是否为手势改变进度
    private boolean mIsFirstScroll = false;// 每次触摸屏幕后，第一次scroll的标志
    private int GESTURE_FLAG = 0;// 1,调节进度，2，调节音量,3.调节亮度
    private static final int GESTURE_MODIFY_PROGRESS = 1;
    private static final int GESTURE_MODIFY_VOLUME = 2;
    private static final int GESTURE_MODIFY_BRIGHT = 3;
    private static final float STEP_PROGRESS = 2f;// 设定进度滑动时的步长，避免每次滑动都改变，导致改变过快
    private static final int CONTROLLER_HIDE_DELAY = 4;
    private static final int CONTROLLER_HIDE_IMDT = 0;

    private AudioManager mAudiomanager;
    private int mMaxVolume = -1;
    private int mCurrentVolume = -1;
    private float mCurrentBrightness = 0.5f;
    private float mMaxBrightness = 255.0f;
    private static final int MIN_BRIGHTNESS = 10;
    private long mCurDownPlayingTime = 0;
    private float mDownX = 0;
    private float mDownY = 0;

    //其他
    private Context mContext;
    private VideoPlayCallbackImpl mVideoPlayCallback;
    private String mCurPlayVideoAccount = "";
    private String mCurPlayVideoId = "";

    private boolean mIsCompleted = false;
    private Runnable mVideoReconnect;
    private boolean mIsVideoReconnecting = false;
    private int mReadFrameToutReqDelayMills = 1000;

    private float mCurSpeed = 1.00f;
    private BigDecimal mSpeedRate = new BigDecimal(Float.toString(0.05f));

    private int mCurrPageType;
    private boolean mIsStreaming = false;   //如果是网络流媒体，则需要判断wifi
    private boolean mIsPlayingBeforePause = false;  //暂停前播放状态，是否在播放
    private boolean mIsVideoBackFinish = true;
    private boolean mIsNoWifiPlay = false;  //无法是否继续播放
    private boolean mIsAllowGesture = true; //默认允许手势操作
    private MyOrientationDetector mMyOrientationDetector;
    //自定义方法订阅
    private Disposable mUpdateVideoTimeDis;
    private Disposable mControllerDis;

    private int seekbar_3x_left = 50;
    private int seekbar_3x_right = 50;
    private int seekbar_3y = 50;
//    private int mWidth;
//    private int mHeight;

    public MyVlcVideoView(Context context) {
        super(context);
        initView(context);
    }

    public MyVlcVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public MyVlcVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    protected void initView(Context context) {
        mContext = context;
        LayoutInflater.from(context).inflate(R.layout.vlc_videoview_layout, this);
        //初始化控件
        initControlView();

        mVideoTopLayout.getBackground().setAlpha(110);
        mVideoBottomLayout.getBackground().setAlpha(110);
        mVideoSpeedLayout.getBackground().setAlpha(110);
        mBufferingLayout.getBackground().setAlpha(110);
        mVideoGestureLayout.getBackground().setAlpha(110);
        mErrorText.getBackground().setAlpha(110);

        mVideoView.setPlayStateCallback(mPlayStateCallback);
        mVideoView.setMediaListenerEvent(mMediaListenerEvent);
        mRootLayout.setOnTouchListener(mOnTouchVideoListener);
        mRootLayout.setLongClickable(true);  //手势需要

        mVideoSeekBar.setOnSeekBarChangeListener(this);

        setPageType(EnumConfig.PageType.SHRINK);

        //视频播放器手势
        mGestureDetector = new GestureDetector(mContext, this);
        mGestureDetector.setIsLongpressEnabled(true);
        mAudiomanager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mMaxVolume = mAudiomanager.getStreamMaxVolume(AudioManager.STREAM_MUSIC); // 获取系统最大音量

        //开启手机旋转角度监听
        mMyOrientationDetector = new MyOrientationDetector(mContext);
        mMyOrientationDetector.enable();
    }

    private void initControlView() {
        mVideoView = findViewById(R.id.vlc_video_view);
        mRootLayout = findViewById(R.id.root_layout);
        mBufferingLayout = findViewById(R.id.buffering_layout);
        mVideoTopLayout = findViewById(R.id.video_top_layout);
        mVideoBottomLayout = findViewById(R.id.video_bottom_layout);
        mVideoSpeedLayout = findViewById(R.id.video_speed_layout);
        mVideoBack = findViewById(R.id.video_back);
        mVideoSeekBar = findViewById(R.id.video_seekbar);
        mVideoTimeText = findViewById(R.id.video_time_text);
        mVideoStateImg = findViewById(R.id.video_state_img);
//        mVideoTitleText = findViewById(R.id.video_title_text);
        mVideoGestureLayout = findViewById(R.id.video_gesture_layout);
        mVideoGestureImg = findViewById(R.id.video_gesture_img);
        mVideoGestureText = findViewById(R.id.video_gesture_text);
        mVideoFullImg = findViewById(R.id.video_full_img);
        mErrorText = findViewById(R.id.error_text);
        mSpeedAdd = findViewById(R.id.speed_add);
        mSpeedDecrease = findViewById(R.id.speed_decrease);
        mSpeedText = findViewById(R.id.speed_text);
        mBufferingText = findViewById(R.id.buffering_text);

        seekbar3XLeft = findViewById(R.id.seekbar_3_x_left);
        seekbar3XRight = findViewById(R.id.seekbar_3_x_right);
        seekbar3Y = findViewById(R.id.seekbar_3_y);

        mVideoBack.setOnClickListener(this);
        mVideoStateImg.setOnClickListener(this);
        mVideoFullImg.setOnClickListener(this);
        mSpeedAdd.setOnClickListener(this);
        mSpeedDecrease.setOnClickListener(this);
        mSpeedText.setOnClickListener(this);


//        mWidth = ScreenUtils.getScreenWidth();
//        mHeight = ScreenUtils.getScreenHeight();
        seekbar3XLeft.addOnIntValueChangeListener(new Function3<NiftySlider, Integer, Boolean, Unit>() {
            @Override
            public Unit invoke(NiftySlider niftySlider, Integer value, Boolean aBoolean) {
                seekbar_3x_left = value;
                refresh(seekbar_3x_left, seekbar_3x_right, seekbar_3y, mVideoView.getHeight(), mVideoView.getWidth());
                return null;
            }
        });
        seekbar3XRight.addOnIntValueChangeListener(new Function3<NiftySlider, Integer, Boolean, Unit>() {
            @Override
            public Unit invoke(NiftySlider niftySlider, Integer value, Boolean aBoolean) {
                seekbar_3x_right = value;
                refresh(seekbar_3x_left, seekbar_3x_right, seekbar_3y, mVideoView.getHeight(), mVideoView.getWidth());
                return null;
            }
        });
        seekbar3Y.addOnIntValueChangeListener(new Function3<NiftySlider, Integer, Boolean, Unit>() {
            @Override
            public Unit invoke(NiftySlider niftySlider, Integer value, Boolean aBoolean) {
                seekbar_3y = value;
                refresh(seekbar_3x_left, seekbar_3x_right, seekbar_3y, mVideoView.getHeight(), mVideoView.getWidth());
                return null;
            }
        });
    }

    private void refresh(int value_x_left,int value_x_right,int value_y,float bHeight,float bWidth) {
        Matrix matrix = new Matrix();
        float[] src = new float[]{0f, 0f, 0f, bHeight, bWidth, bHeight, bWidth, 0f};

        float left_top_Height = bHeight * value_y / 100 / 2;
        float left_bottom_Height = bHeight - bHeight * value_y / 100 / 2;
        float right_bottom_Height = bHeight - bHeight * value_y / 100 / 2;
        float right_top_Height = bHeight * value_y / 100 / 2;
        if (left_bottom_Height - left_top_Height <= 0) {
            return ;
        }

        float left_top_Width = 0 + bWidth * value_x_left / 100 / 2;
        float left_bottom_Width = 0f;
        float right_bottom_Width = bWidth;
        float right_top_Width = bWidth - bWidth * value_x_right / 100 / 2;
        if (right_top_Width - left_top_Width <= 0) {
            return ;
        }

        float[] dst = new float[]{
            left_top_Width, left_top_Height,
            left_bottom_Width, left_bottom_Height,
            right_bottom_Width, right_bottom_Height,
            right_top_Width, right_top_Height};
        matrix.setPolyToPoly(src, 0, dst, 0, 4);
        mVideoView.setTransform(matrix);//将矩阵添加到textureView
        mVideoView.postInvalidate();//重绘视图
    }

    /**
     * 进入开始播放
     *
     * @param url            视频播放url
     * @param title          视频播放标题，如果无标题则传空
     * @param userAccount    视频播放账户，用来保存视频播放位置的文件名；如果不保存视频播放位置，则userAccount和curPlayVideoId传空
     * @param curPlayVideoId 视频播放Id，用来保存视频播放位置的key值；如果不保存视频播放位置，则userAccount和curPlayVideoId传空
     */
    public void startPlayVideo(String url, String title, String userAccount, String curPlayVideoId) {
        if (!VLCInstance.testCompatibleCPU(mContext)) {
            mVideoPlayCallback.unSupportCPU();
            Toast.makeText(mContext, R.string.not_support_cpu, Toast.LENGTH_SHORT).show();
            return;
        }

        //切换视频前保存上一个视频最后播放位置
        saveVideoPlayPosition();

        mIsCompleted = false;

//        mVideoTitleText.setText(title);

        String scheme = Uri.parse(url).getScheme();
        mIsStreaming = (("http".equalsIgnoreCase(scheme) || "rtsp".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)));

        File file = new File(url);
        if (!mIsStreaming && !file.exists()) {
            mVideoPlayCallback.onVideoNotExist();
            return;
        }

        long seekTime = getVideoPlayPosition(userAccount, curPlayVideoId);
        mVideoView.startPlay(url, seekTime, mCurSpeed);
        updateVideoProgress();
    }

    /*视频播放 - Start*/
    private OnTouchListener mOnTouchVideoListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN && mBufferingLayout.getVisibility() != View.VISIBLE) {
                mDownX = motionEvent.getX();
                mDownY = motionEvent.getY();

                mIsFirstScroll = true;  // 设定是触摸屏幕后第一次scroll的标志
                mCurrentVolume = mAudiomanager.getStreamVolume(AudioManager.STREAM_MUSIC); // 获取当前音量值
                //第一次进入，获取的当前亮度为系统目前亮度（此时getWindow().getAttributes().screenBrightness = -1.0）
                //未退出再次在该界面调节时，获取当前已调节的亮度
                if (((Activity) mContext).getWindow().getAttributes().screenBrightness < 0) {
                    mCurrentBrightness = android.provider.Settings.System.getInt(mContext.getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, 255) / mMaxBrightness;  // 获取当前系统亮度值,获取失败则返回255
                } else {
                    mCurrentBrightness = ((Activity) mContext).getWindow().getAttributes().screenBrightness;
                }
            } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                float upX = motionEvent.getX();
                float upY = motionEvent.getY();

                GESTURE_FLAG = 0;// 手指离开屏幕后，重置调节音量或进度的标志
                mVideoGestureLayout.setVisibility(GONE);

                //通过down和up来判断手势是否移动，部分机型用MotionEvent.ACTION_MOVE判断会有误
                if (Math.abs(upX - mDownX) > 20 || Math.abs(upY - mDownY) > 20) {
                    resetHideController(CONTROLLER_HIDE_IMDT, true);   //手势移动，强制隐藏状态栏
                } else {  //非手势移动，才自动显示/隐藏状态栏
                    resetHideController(CONTROLLER_HIDE_IMDT, false);
                }
                if (mVideoView != null && mIsProgressChange) {
                    mVideoView.seekTo((int) mCurDownPlayingTime);
                }
                mIsProgressChange = false;
            }
            return mGestureDetector.onTouchEvent(motionEvent);
        }
    };

    private void showOrHideController(boolean isForceHide) {
        if (isForceHide) {
            mVideoTopLayout.setVisibility(GONE);
            mVideoBottomLayout.setVisibility(GONE);
            mVideoSpeedLayout.setVisibility(GONE);
            return;
        }

        if (mVideoBottomLayout.getVisibility() == View.VISIBLE
                || mVideoTopLayout.getVisibility() == View.VISIBLE) {
            //顶部隐藏动画
            if (mCurrPageType == EnumConfig.PageType.EXPAND) {
                mVideoTopLayout.clearAnimation();
                mVideoTopLayout.setVisibility(GONE);
                Animation animationTop = AnimationUtils.loadAnimation(mContext, R.anim.anim_exit_from_top);
                mVideoTopLayout.startAnimation(animationTop);
            } else {
                mVideoTopLayout.setVisibility(GONE);
                mVideoTopLayout.clearAnimation();
            }

            //底部隐藏动画
            mVideoBottomLayout.clearAnimation();
            mVideoBottomLayout.setVisibility(GONE);
            Animation animationBottom = AnimationUtils.loadAnimation(mContext, R.anim.anim_exit_from_bottom);
            mVideoBottomLayout.startAnimation(animationBottom);
            //倍速隐藏动画
            mVideoSpeedLayout.clearAnimation();
            mVideoSpeedLayout.setVisibility(GONE);
            Animation animationSpeed = AnimationUtils.loadAnimation(mContext, R.anim.anim_exit_from_left);
            mVideoSpeedLayout.startAnimation(animationSpeed);

            CoreUtil.disposeSubscribe(mControllerDis);
        } else {
            //顶部
            if (mCurrPageType == EnumConfig.PageType.EXPAND) {
                mVideoTopLayout.setVisibility(VISIBLE);
                mVideoTopLayout.clearAnimation();
                Animation animationTop = AnimationUtils.loadAnimation(mContext, R.anim.anim_enter_from_top);
                mVideoTopLayout.startAnimation(animationTop);
            } else {
                mVideoTopLayout.setVisibility(GONE);
                mVideoTopLayout.clearAnimation();
            }

            //底部
            mVideoBottomLayout.setVisibility(VISIBLE);
            mVideoBottomLayout.clearAnimation();
            Animation animationBottom = AnimationUtils.loadAnimation(mContext, R.anim.anim_enter_from_bottom);
            mVideoBottomLayout.startAnimation(animationBottom);
            //倍速
            mVideoSpeedLayout.setVisibility(VISIBLE);
            mVideoSpeedLayout.clearAnimation();
            Animation animationSpeed = AnimationUtils.loadAnimation(mContext, R.anim.anim_enter_from_left);
            mVideoSpeedLayout.startAnimation(animationSpeed);

            resetHideController(CONTROLLER_HIDE_DELAY, false);
        }
    }

    private void resetHideController(int delayTime, boolean isForceHide) {
        createControllerSub(delayTime, isForceHide);
    }

    private void updateVideoProgress() {
        CoreUtil.disposeSubscribe(mUpdateVideoTimeDis);
        Flowable flowable = Flowable.interval(0, 1, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) throws Exception {
                        if ((getPlayState() == STATE_LOAD || getPlayState() == STATE_PLAY) && mBufferingLayout.getVisibility() != View.VISIBLE
                                && mVideoBottomLayout.getVisibility() == VISIBLE) {   //播放非缓冲且显示才更新
                            long curTime = getCurPlayTime();
                            long totalTime = getTotalPlayTime();

                            if (totalTime == 0) { //防止出错
                                mVideoSeekBar.setProgress(0);
                            } else {
                                int curProgress = (int) (curTime * 100 / totalTime);
                                mVideoSeekBar.setProgress(CoreUtil.getPlayProgress(curProgress));
                            }
                            mVideoTimeText.setText(CoreUtil.getPlayTime(curTime, totalTime));
                        }
                    }
                });

        mUpdateVideoTimeDis = flowable.subscribe();
    }

    private void createControllerSub(int delayTime, final boolean isForceHide) {
        CoreUtil.disposeSubscribe(mControllerDis);
        Flowable flowable = Flowable.timer(delayTime, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) throws Exception {
                        showOrHideController(isForceHide);
                    }
                });

        mControllerDis = flowable.subscribe();
    }

    //旋转屏幕
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (null == mVideoView) return;

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {

            ((Activity) mContext).getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            ((Activity) mContext).getWindow().getDecorView().invalidate();
            mRootLayout.getLayoutParams().width = (int) DensityUtil.getWidthInPx(mContext);
            mRootLayout.getLayoutParams().height = (int) DensityUtil.getHeightInPx(mContext);

            mVideoPlayCallback.onOrientationLandscape(mRootLayout.getLayoutParams().width, mRootLayout.getLayoutParams().height);
        } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            WindowManager.LayoutParams attrs = ((Activity) mContext).getWindow().getAttributes();
            attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
            ((Activity) mContext).getWindow().setAttributes(attrs);
            ((Activity) mContext).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            mRootLayout.getLayoutParams().width = (int) DensityUtil.getWidthInPx(mContext);
            mRootLayout.getLayoutParams().height = CoreUtil.getVideoHeightPx(mContext);

            mVideoPlayCallback.onOrientationPortrait(mRootLayout.getLayoutParams().width, mRootLayout.getLayoutParams().height);
        }
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (!mIsAllowGesture) {
            return false;
        }
        float ex = e1.getX(), ey = e1.getY();
        int varX = (int) (e2.getX() - e1.getX());
        int varY = (int) (e2.getY() - e1.getY());
        if (mIsFirstScroll) {// 以触摸屏幕后第一次滑动为标准，避免在屏幕上操作切换混乱
            mVideoGestureLayout.setVisibility(VISIBLE);
            // 横向的距离变化大则调整进度，纵向的变化大则调整音量
            if (Math.abs(distanceX) >= Math.abs(distanceY)) {    //调节进度
                GESTURE_FLAG = GESTURE_MODIFY_PROGRESS;
            } else {
                if (ex < mRootLayout.getWidth() / 2) {     //左半边亮度
                    GESTURE_FLAG = GESTURE_MODIFY_BRIGHT;
                } else {    //右半边音量
                    GESTURE_FLAG = GESTURE_MODIFY_VOLUME;
                }
            }
            mCurDownPlayingTime = getCurPlayTime();
        }

        // 如果每次触摸屏幕后第一次scroll是调节进度，那之后的scroll事件都处理音量进度，直到离开屏幕执行下一次操作
        if (GESTURE_FLAG == GESTURE_MODIFY_PROGRESS) {
            mIsProgressChange = true;
            if (distanceX >= DensityUtil.dip2px(mContext, STEP_PROGRESS)) {// 快退，用步长控制改变速度，可微调
                mVideoGestureImg.setImageResource(R.drawable.seek_back);
                if (mCurDownPlayingTime > 3000) {// 避免为负
                    mCurDownPlayingTime -= 3000;// scroll方法执行一次快退3秒
                } else {
                    mCurDownPlayingTime = 0;
                }
            } else if (distanceX <= -DensityUtil.dip2px(mContext, STEP_PROGRESS)) {// 快进
                mVideoGestureImg.setImageResource(R.drawable.seek_forward);
                if (mCurDownPlayingTime < getTotalPlayTime()) {// 避免超过总时长
                    mCurDownPlayingTime += 3000;// scroll执行一次快进3秒
                } else {
                    mCurDownPlayingTime = getTotalPlayTime() - 1000;
                }
            }
            if (mCurDownPlayingTime < 0) {
                mCurDownPlayingTime = 0;
            }
            mVideoGestureText.setText(CoreUtil.getPlayTime(mCurDownPlayingTime, getTotalPlayTime()));
        } else if (GESTURE_FLAG == GESTURE_MODIFY_BRIGHT) {
            mVideoGestureImg.setImageResource(R.drawable.brightness);
            int slideHeight = mRootLayout.getHeight() / 2;
            int midLevelPx = slideHeight / 15;
            int slideDistance = -varY;
            int slideLevel = slideDistance / midLevelPx;

            if (mCurrentBrightness == -1 || mCurrentBrightness < 0) {
                mCurrentBrightness = 0;
            }
            WindowManager.LayoutParams lpa = ((Activity) mContext).getWindow().getAttributes();
            float midLevelBright = (mMaxBrightness - MIN_BRIGHTNESS) / 15.0f;
            float realBright = midLevelBright * slideLevel + mCurrentBrightness
                    * (mMaxBrightness - MIN_BRIGHTNESS) + MIN_BRIGHTNESS;

            if (realBright < MIN_BRIGHTNESS) {
                realBright = MIN_BRIGHTNESS;
            }
            if (realBright > mMaxBrightness) {
                realBright = mMaxBrightness;
            }

            lpa.screenBrightness = realBright / mMaxBrightness;
            ((Activity) mContext).getWindow().setAttributes(lpa);
            mVideoGestureText.setText((int) (lpa.screenBrightness * 100) + "%");

            //保存系统亮度，这样退出程序后，系统亮度也会改变，但无此必要
            //android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, (int) realBright);
        } else if (GESTURE_FLAG == GESTURE_MODIFY_VOLUME) {
            if (Math.abs(distanceY) > Math.abs(distanceX)) {// 纵向移动大于横向移动
                int slideHeight = mRootLayout.getHeight() / 2;
                int midLevelPx = slideHeight / 15;
                int slideDistance = -varY;
                int slideLevel = slideDistance / midLevelPx;
                int midLevelVolume = mMaxVolume / 15;
                int realVolume = midLevelVolume * slideLevel + mCurrentVolume;

                if (realVolume <= 0) {
                    realVolume = 0;
                    mVideoGestureImg.setImageResource(R.drawable.volume_slience);
                } else {
                    mVideoGestureImg.setImageResource(R.drawable.volume_not_slience);
                }
                if (realVolume > mMaxVolume) {
                    realVolume = mMaxVolume;
                }

                int percentage = (realVolume * 100) / mMaxVolume;
                mVideoGestureText.setText(percentage + "%");
                mAudiomanager.setStreamVolume(AudioManager.STREAM_MUSIC, realVolume, 0);
            }
        }

        mIsFirstScroll = false;// 第一次scroll执行完成，修改标志
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            onProgressTurn(EnumConfig.ProgressState.DOING, progress);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        onProgressTurn(EnumConfig.ProgressState.START, 0);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        onProgressTurn(EnumConfig.ProgressState.STOP, 0);
    }

    public void onPlayTurn() {
        if (mVideoView.isPlaying()) {
            mVideoView.pause();
        } else {
            if (CoreUtil.isNetworkAvailable(mContext) || !mIsStreaming) {
                mVideoView.start();
            } else {
                Toast.makeText(mContext, R.string.no_network, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void onProgressTurn(int state, int progress) {
        boolean isWifiConnected = CoreUtil.isWifiConnected(mContext);
        if (mIsStreaming && !isWifiConnected && !mIsNoWifiPlay) {
            return;
        }

        if (state == EnumConfig.ProgressState.START) {

        } else if (state == EnumConfig.ProgressState.STOP) {

        } else {
            int time = (int) (progress * getTotalPlayTime() / 100);
            mVideoView.seekTo(time);
            resetHideController(CONTROLLER_HIDE_DELAY, false);
        }
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.video_back) {
            if (mIsVideoBackFinish) {
                ((Activity) mContext).finish();
            } else {
                onVideoWindowTurn();
            }
        } else if (i == R.id.video_state_img) {
            onPlayTurn();
        } else if (i == R.id.video_full_img) {
            onVideoWindowTurn();
        } else if (i == R.id.speed_add) {
            setSpeed(true);
        } else if (i == R.id.speed_decrease) {
            setSpeed(false);
        } else if (i == R.id.speed_text) {
            //do nothing，避免点击时点击事件传递到下面隐藏状态栏
        }
    }

    //是否允许手势操作
    public void setGestureEnable(boolean isAllowGesture) {
        mIsAllowGesture = isAllowGesture;
    }

    private void setSpeed(boolean isAdd) {
        resetHideController(CONTROLLER_HIDE_DELAY, false);
        if ((!isAdd && mCurSpeed <= 0.25f) || (isAdd && mCurSpeed >= 4.0f)) {
            return;
        }

        DecimalFormat decimalFormat = new DecimalFormat("0.00");
        BigDecimal decimal = new BigDecimal(Float.toString(mCurSpeed));
        if (isAdd) {
            mCurSpeed = decimal.add(mSpeedRate).floatValue();
        } else {
            mCurSpeed = decimal.subtract(mSpeedRate).floatValue();
        }
        mVideoView.setPlaybackSpeedMedia(mCurSpeed);
        mSpeedText.setText(decimalFormat.format(mCurSpeed) + "x");
    }

    //对外调用接口
    public void setVideoPlayCallback(VideoPlayCallbackImpl videoPlayCallback) {
        mVideoPlayCallback = videoPlayCallback;
    }

    public String getCurPlayVideoId() {
        return mCurPlayVideoId;
    }

    public void start() {
        if (mVideoView != null && mIsPlayingBeforePause) {
            mVideoView.start();
        }
    }

    public void pause() {
        mIsPlayingBeforePause = mVideoView.isPlaying();
        if (mVideoView != null) {
            mVideoView.pause();
        }
    }

    public void stop() {
        saveVideoPlayPosition();
        mVideoView.onStop();
        CoreUtil.disposeSubscribe(mUpdateVideoTimeDis, mControllerDis);
    }

    //保存视频播放位置
    public void saveVideoPlayPosition() {
        if (!mIsCompleted && !mCurPlayVideoAccount.isEmpty() && !mCurPlayVideoId.isEmpty()) {
            SpUtils.put(mContext, PREFIX_VIDEO_PLAY_POSITION + mCurPlayVideoAccount, mCurPlayVideoId, String.valueOf(getCurPlayTime()));
        }
    }

    //获取上一次播放位置
    public long getVideoPlayPosition(String userAccount, String curPlayVideoId) {
        long seekTime = 0;
        try {
            if (!userAccount.isEmpty() && !curPlayVideoId.isEmpty()) {
                seekTime = Long.parseLong(SpUtils.get(mContext, PREFIX_VIDEO_PLAY_POSITION + userAccount, curPlayVideoId, "0").toString());
            }
            mCurPlayVideoAccount = userAccount;
            mCurPlayVideoId = curPlayVideoId;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return seekTime;
    }

    //移除保存的播放位置，避免再次播放后仍从上次保存的位置继续播放
    public void removeVideoPlayPosition() {
        try {
            if (!mCurPlayVideoAccount.isEmpty() && !mCurPlayVideoId.isEmpty()) {
                SpUtils.remove(mContext, PREFIX_VIDEO_PLAY_POSITION + mCurPlayVideoAccount, mCurPlayVideoId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setAutoRotateDisable() {
        mMyOrientationDetector.disable();
    }

    public void showFullImage() {
        mVideoFullImg.setVisibility(VISIBLE);
    }

    public void setPageType(int pageType) {
        mCurrPageType = pageType;
        if (pageType == EnumConfig.PageType.SHRINK) {
            mVideoFullImg.setImageResource(R.drawable.biz_video_expand);
        } else {
            mVideoFullImg.setImageResource(R.drawable.biz_video_shrink);
        }
    }

    public void setPageTypeByBoolean(boolean isShrink) {
        if (isShrink) {
            mCurrPageType = EnumConfig.PageType.SHRINK;
            mVideoFullImg.setImageResource(R.drawable.biz_video_expand);
        } else {
            mCurrPageType = EnumConfig.PageType.EXPAND;
            mVideoFullImg.setImageResource(R.drawable.biz_video_shrink);
        }
    }

    public void onVideoWindowTurn() {
        int orientation = ((Activity) mContext).getRequestedOrientation();
        if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                || orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
            ((Activity) mContext).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            setPageType(EnumConfig.PageType.SHRINK);
        } else {
            ((Activity) mContext).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setPageType(EnumConfig.PageType.EXPAND);
        }
    }

    public void setIsVideoBackFinish(boolean isVideoBackFinish) {
        mIsVideoBackFinish = isVideoBackFinish;
    }

    public void showVideoTopLayout(boolean isShow) {
        if (isShow) {
            mVideoTopLayout.setVisibility(VISIBLE);
        } else {
            mVideoTopLayout.setVisibility(GONE);
        }
    }

    public void setIsNoWifiPlay(boolean isNoWifiPlay) {
        mIsNoWifiPlay = isNoWifiPlay;
    }

    public int getPlayState() {
        return mVideoView.getPlayState();
    }

    VlcVideoView.PlayStateImpl mPlayStateCallback = new VlcVideoView.PlayStateImpl() {
        @Override
        public void setPlayState(int playState) {
            L.e("playState " + playState);
            switch (playState) {
                case STATE_LOAD:
                case STATE_PLAY:
                    mVideoStateImg.setImageResource(R.drawable.biz_video_pause);
                    break;
                default:
                    mVideoStateImg.setImageResource(R.drawable.biz_video_play);
                    break;
            }

            //设置屏幕常亮与否
            if ((playState == STATE_PLAY) && !CoreUtil.isKeepScreenOn(mContext)) {
                CoreUtil.keepScreenOnOff(mContext, true);
            } else if (!(playState == STATE_PLAY) && CoreUtil.isKeepScreenOn(mContext)) {
                CoreUtil.keepScreenOnOff(mContext, false);
            }
        }
    };

    MediaListenerEvent mMediaListenerEvent = new MediaListenerEvent() {
        @Override
        public void eventBuffing(int event, float buffing, boolean show) {
            if (mErrorText.getVisibility() != GONE) {
                mErrorText.setVisibility(GONE);
            }

            if (show) {
                if (mBufferingLayout.getVisibility() != VISIBLE) {
                    mBufferingLayout.setVisibility(VISIBLE);
                }
                mBufferingText.setText(mContext.getString(R.string.buffer) + " " + new DecimalFormat("0").format(buffing) + "%");
                setGestureEnable(false);    //缓冲时不允许手势操作
            } else {
                if (mBufferingLayout.getVisibility() != GONE) {
                    mBufferingLayout.setVisibility(GONE);
                }
                setGestureEnable(true);     //缓冲结束允许手势操作
            }
        }

        @Override
        public void eventPlayInit(boolean openClose) {

        }

        @Override
        public void eventStop(boolean isPlayError) {

        }

        @Override
        public void eventError(int error, boolean show) {
            L.d("Error happened, errorCode = " + error);
            if (mBufferingLayout != null) {
                mBufferingLayout.setVisibility(GONE);
            }

            if (!mIsCompleted && mVideoView != null) {
                if (mVideoView.getPlayState() == STATE_PLAY) {
                    mVideoView.pause();
                    mVideoView.removeCallbacks(mVideoReconnect);
                    mVideoReconnect = new Runnable() {
                        @Override
                        public void run() {
                            mVideoView.start();
                        }
                    };
                    if (!mIsVideoReconnecting) {
                        mIsVideoReconnecting = true;
                        mErrorText.setVisibility(VISIBLE);
                        mVideoView.postDelayed(mVideoReconnect, mReadFrameToutReqDelayMills);
                    } else {
                        mIsVideoReconnecting = false;
                        mErrorText.setVisibility(GONE);
                        mVideoPlayCallback.onError();
                        Toast.makeText(mContext, R.string.error_video_play, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }

        @Override
        public void eventPlay(boolean isPlaying) {
            mIsVideoReconnecting = false;
            mVideoPlayCallback.onEventPlay(isPlaying);
        }

        @Override
        public void eventComplete() {
            mIsCompleted = true;

            mBufferingLayout.setVisibility(GONE);
            CoreUtil.disposeSubscribe(mUpdateVideoTimeDis, mControllerDis);

            removeVideoPlayPosition();

            mVideoPlayCallback.onPlayComplete();
        }

        @Override
        public void eventTimeChanged(long playTime) {
        }
    };

    public long getCurPlayTime() {
        return mVideoView.getCurrentPosition();
    }

    public long getTotalPlayTime() {
        return mVideoView.getDuration();
    }

    public boolean isCurPlayCompleted() {
        return mIsCompleted;
    }

    //向外回调接口
    public interface VideoPlayCallbackImpl {
        void onPlayComplete();

        void onVideoNotExist();

        void onOrientationLandscape(int width, int height);

        void onOrientationPortrait(int width, int height);

        void onError();

        void unSupportCPU();

        void onEventPlay(boolean isPlaying);
    }

    public class MyOrientationDetector extends OrientationEventListener {
        public MyOrientationDetector(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            int currentOrientation = -1;
            if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                return;  //手机平放时，检测不到有效的角度
            }
            //只检测是否有四个角度的改变
            if (orientation > 350 || orientation < 10) {
                currentOrientation = Surface.ROTATION_0;
            } else if (orientation > 80 && orientation < 100) {
                currentOrientation = Surface.ROTATION_90;
            } else if (orientation > 170 && orientation < 190) {
                currentOrientation = Surface.ROTATION_180;
            } else if (orientation > 260 && orientation < 280) {
                currentOrientation = Surface.ROTATION_270;
            } else {
                return;
            }
            doLandScapeRotate(currentOrientation);
        }

        //水平方向旋转
        private void doLandScapeRotate(int orientation) {
            if (mCurrPageType != EnumConfig.PageType.EXPAND) {
                return;
            }
            int ori = ((Activity) mContext).getRequestedOrientation();
            switch (orientation) {
                case Surface.ROTATION_90:
                    if (ori != ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                        ((Activity) mContext).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                        setPageType(EnumConfig.PageType.EXPAND);
                    }
                    break;
                case Surface.ROTATION_270:
                    if (ori != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE && ori != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                        ((Activity) mContext).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        setPageType(EnumConfig.PageType.EXPAND);
                    }
                    break;
            }
        }
    }
}