package io.rong.callkit;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.faceunity.core.entity.FURenderOutputData;
import com.faceunity.core.enumeration.FUAIProcessorEnum;
import com.faceunity.core.enumeration.FUInputTextureEnum;
import com.faceunity.core.faceunity.FUAIKit;
import com.faceunity.core.faceunity.FURenderKit;
import com.faceunity.core.model.facebeauty.FaceBeautyBlurTypeEnum;
import com.faceunity.nama.FUConfig;
import com.faceunity.nama.FURenderer;
import com.faceunity.nama.data.FaceUnityDataFactory;
import com.faceunity.nama.listener.FURendererListener;
import com.faceunity.nama.ui.FaceUnityView;
import com.faceunity.nama.utils.FuDeviceUtils;

import cn.rongcloud.rtc.api.RCRTCEngine;
import cn.rongcloud.rtc.audioroute.RCAudioRouteType;
import cn.rongcloud.rtc.utils.FinLog;
import io.rong.callkit.profile.CSVUtils;
import io.rong.callkit.profile.Constant;
import io.rong.callkit.util.BluetoothUtil;
import io.rong.callkit.util.CallKitUtils;
import io.rong.callkit.util.DefaultPushConfig;
import io.rong.callkit.util.HeadsetInfo;
import io.rong.callkit.util.PreferenceUtil;
import io.rong.callkit.util.RingingMode;
import io.rong.callkit.util.RongCallPermissionUtil;
import io.rong.calllib.CallUserProfile;
import io.rong.calllib.CallVideoFrame;
import io.rong.calllib.IVideoFrameListener;
import io.rong.calllib.ReportUtil;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallCommon;
import io.rong.calllib.RongCallSession;
import io.rong.calllib.StartIncomingPreviewCallback;
import io.rong.calllib.message.CallSTerminateMessage;
import io.rong.common.RLog;
import io.rong.imkit.IMCenter;
import io.rong.imkit.userinfo.RongUserInfoManager;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.model.Conversation;
import io.rong.imlib.model.UserInfo;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SingleCallActivity extends BaseCallActivity implements Handler.Callback, SensorEventListener {
    private static final String TAG = "VoIPSingleActivity";
    private static final int LOSS_RATE_ALARM = 20;
    private LayoutInflater inflater;
    private RongCallSession callSession;
    private RelativeLayout mLPreviewContainer;
    private FrameLayout mSPreviewContainer;
    private FrameLayout mButtonContainer;
    private LinearLayout mUserInfoContainer;
    private TextView mConnectionStateTextView;
    private Boolean isInformationShow = false;
    private SurfaceView mLocalVideo = null;
    private boolean muted = false;
    private boolean handFree = false;
    private boolean startForCheckPermissions = false;
    private boolean isReceiveLost = false;
    private boolean isSendLost = false;
    private SoundPool mSoundPool = null;

    private int EVENT_FULL_SCREEN = 1;

    private String targetId = null;
    private RongCallCommon.CallMediaType mediaType;

    private FURenderer mFURenderer;
    private FaceUnityDataFactory mFaceUnityDataFactory;
    // 渲染是否暂停，当 APP 退到后台时
    private boolean mIsRenderPaused;
    // 切换相机时，跳过几十帧
    private int mSkippedFrames = CAMERA_SWITCH_SKIP_FRAME;
    // 相机方向，默认前置
    private int mCameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
    // 相机切换跳过 5 帧，如果还有问题，可以增加帧数
    private static final int CAMERA_SWITCH_SKIP_FRAME = 0;
    private SensorManager mSensorManager;
    private TextView mTvFps;
    private CSVUtils mCSVUtils;

    @Override
    public final boolean handleMessage(Message msg) {
        if (msg.what == EVENT_FULL_SCREEN) {
            hideVideoCallInformation();
            return true;
        }
        return false;
    }

    @Override
    @TargetApi(23)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rc_voip_activity_single_call);
        Log.i(
                "AudioPlugin",
                "savedInstanceState != null="
                        + (savedInstanceState != null)
                        + ",,,RongCallClient.getInstance() == null"
                        + (RongCallClient.getInstance() == null));
        if (savedInstanceState != null && RongCallClient.getInstance() == null) {
            // 音视频请求权限时，用户在设置页面取消权限，导致应用重启，退出当前activity.
            Log.i("AudioPlugin", "音视频请求权限时，用户在设置页面取消权限，导致应用重启，退出当前activity");
            finish();
            return;
        }
        Intent intent = getIntent();
        mLPreviewContainer = (RelativeLayout) findViewById(R.id.rc_voip_call_large_preview);
        mSPreviewContainer = (FrameLayout) findViewById(R.id.rc_voip_call_small_preview);
        mButtonContainer = (FrameLayout) findViewById(R.id.rc_voip_btn);
        mUserInfoContainer = (LinearLayout) findViewById(R.id.rc_voip_user_info);
        mConnectionStateTextView = findViewById(R.id.rc_tv_connection_state);

        startForCheckPermissions = intent.getBooleanExtra("checkPermissions", false);
        RongCallAction callAction = RongCallAction.valueOf(intent.getStringExtra("callAction"));

        String receivedCallId = "";
        if (callAction.equals(RongCallAction.ACTION_OUTGOING_CALL)) {
            if (intent.getAction().equals(RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEAUDIO)) {
                mediaType = RongCallCommon.CallMediaType.AUDIO;
            } else {
                mediaType = RongCallCommon.CallMediaType.VIDEO;
            }
        } else if (callAction.equals(RongCallAction.ACTION_INCOMING_CALL)) {
            callSession = intent.getParcelableExtra("callSession");
            mediaType = callSession.getMediaType();
            receivedCallId = callSession.getCallId();
        } else {
            callSession = RongCallClient.getInstance().getCallSession();
            if (callSession != null) {
                mediaType = callSession.getMediaType();
                receivedCallId = callSession.getCallId();
            }
        }
        if (!RongCallClient.getInstance().canCallContinued(receivedCallId)) {
            RLog.w(TAG, "Already received hangup message before, finish current activity");
            ReportUtil.libStatus(ReportUtil.TAG.ACTIVITYFINISH, "reason", "canCallContinued not");
            finish();
            return;
        }
        if (mediaType != null) {
            inflater = LayoutInflater.from(this);
            initView(mediaType, callAction);

            if (requestCallPermissions(mediaType, REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS)) {
                setupIntent();
            }
        } else {
            RLog.w(TAG, "remote already hangup, finish current activity");
            setShouldShowFloat(false);
            CallFloatBoxView.hideFloatBox();
            finish();
        }

        mTvFps = new TextView(this);
        ((FrameLayout)mConnectionStateTextView.getParent()).addView(mTvFps);
        FaceUnityView beautyControlView = findViewById(R.id.faceunity_control);
        String isOpenFU = PreferenceUtil.getString(this, PreferenceUtil.KEY_FACEUNITY_IS_ON);

        if ("true".equals(isOpenFU)) {
            mFURenderer = FURenderer.getInstance();
            mFURenderer.setInputTextureType(FUInputTextureEnum.FU_ADM_FLAG_COMMON_TEXTURE);
            mFURenderer.setMarkFPSEnable(true);
            mFaceUnityDataFactory = FaceUnityDataFactory.getInstance();
            beautyControlView.bindDataFactory(mFaceUnityDataFactory);
            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

        } else {
            beautyControlView.setVisibility(View.GONE);
        }
    }

    private FURendererListener mFURendererListener = new FURendererListener() {

        @Override
        public void onPrepare() {
            mFaceUnityDataFactory.bindCurrentRenderer();
            Log.e(TAG, "mFURendererListener: onPrepare: ");
        }

        @Override
        public void onTrackStatusChanged(FUAIProcessorEnum type, int status) {
            Log.e(TAG, "onTrackStatusChanged: 人脸数: " + status);
        }

        @Override
        public void onFpsChanged(double fps, double callTime) {
            final String FPS = String.format(Locale.getDefault(), "%.2f", fps);
            Log.d(TAG, "onFpsChanged FPS: " + FPS + ", callTime: " + String.format("%.2f", callTime));
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mTvFps != null) {
                        mTvFps.setText(FPS);
                    }
                }
            });
        }

        @Override
        public void onRelease() {
//            RongCallClient.getInstance().unregisterVideoFrameObserver();
        }
    };

    @Override
    protected void onNewIntent(Intent intent) {
        startForCheckPermissions = intent.getBooleanExtra("checkPermissions", false);
        RongCallAction callAction = RongCallAction.valueOf(intent.getStringExtra("callAction"));
        if (callAction == null) {
            return;
        }
        if (callAction.equals(RongCallAction.ACTION_OUTGOING_CALL)) {
            if (intent.getAction().equals(RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEAUDIO)) {
                mediaType = RongCallCommon.CallMediaType.AUDIO;
            } else {
                mediaType = RongCallCommon.CallMediaType.VIDEO;
            }
        } else if (callAction.equals(RongCallAction.ACTION_INCOMING_CALL)) {
            callSession = intent.getParcelableExtra("callSession");
            mediaType = callSession.getMediaType();
        } else {
            callSession = RongCallClient.getInstance().getCallSession();
            mediaType = callSession.getMediaType();
        }
        super.onNewIntent(intent);

        if (requestCallPermissions(mediaType, REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS)) {
            setupIntent();
        }
    }

    @TargetApi(23)
    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS:
                if (RongCallPermissionUtil.checkPermissionByType(this, mediaType)) {
                    if (startForCheckPermissions) {
                        startForCheckPermissions = false;
                        RongCallClient.getInstance().onPermissionGranted();
                    } else {
                        setupIntent();
                    }
                } else {
                    StringBuilder builder = new StringBuilder();
                    for (String str : permissions) {
                        if (str.equals("android.permission.CAMERA")) {
                            builder.append(getString(R.string.rc_android_permission_CAMERA));
                        } else if (str.equals("android.permission.RECORD_AUDIO")) {
                            builder.append(getString(R.string.rc_android_permission_RECORD_AUDIO));
                        } else if (str.equals("android.permission.BLUETOOTH_CONNECT")) {
                            builder.append(
                                    getString(R.string.rc_android_permission_BLUETOOTH_CONNECT));
                        }
                        builder.append(",");
                    }

                    String rets =
                            builder.length() > 0 ? builder.substring(0, builder.length() - 1) : "";
                    String msg =
                            String.format(
                                    "%s (%s)",
                                    getString(R.string.rc_permission_grant_needed), rets);
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    if (startForCheckPermissions) {
                        startForCheckPermissions = false;
                        RongCallClient.getInstance().onPermissionDenied();
                    } else {
                        Log.i("AudioPlugin", "--onRequestPermissionsResult--finish");
                        finish();
                    }
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS) {
            if (RongCallPermissionUtil.checkPermissionByType(this, mediaType)) {
                if (startForCheckPermissions) {
                    RongCallClient.getInstance().onPermissionGranted();
                } else {
                    setupIntent();
                }
            } else {
                if (startForCheckPermissions) {
                    RongCallClient.getInstance().onPermissionDenied();
                } else {
                    Log.i("AudioPlugin", "onActivityResult finish");
                    finish();
                }
            }

        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void setupIntent() {
        RongCallCommon.CallMediaType mediaType;
        Intent intent = getIntent();
        RongCallAction callAction = RongCallAction.valueOf(intent.getStringExtra("callAction"));
        //        if (callAction.equals(RongCallAction.ACTION_RESUME_CALL)) {
        //            return;
        //        }
        if (callAction.equals(RongCallAction.ACTION_INCOMING_CALL)) {
            callSession = intent.getParcelableExtra("callSession");
            mediaType = callSession.getMediaType();
            targetId = callSession.getInviterUserId();
            RongCallClient.getInstance()
                    .startIncomingPreview(
                            new StartIncomingPreviewCallback() {
                                @Override
                                public void onDone(boolean isFront, SurfaceView localVideo) {
                                    if (callSession
                                            .getMediaType()
                                            .equals(RongCallCommon.CallMediaType.VIDEO)) {
                                        mLPreviewContainer.setVisibility(View.VISIBLE);
                                        localVideo.setTag(callSession.getSelfUserId());
                                        mLPreviewContainer.addView(localVideo, mLargeLayoutParams);
                                    }
                                }

                                @Override
                                public void onError(int errorCode) {}
                            });
        } else if (callAction.equals(RongCallAction.ACTION_OUTGOING_CALL)) {
            if (intent.getAction().equals(RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEAUDIO)) {
                mediaType = RongCallCommon.CallMediaType.AUDIO;
            } else {
                mediaType = RongCallCommon.CallMediaType.VIDEO;
            }
            Conversation.ConversationType conversationType =
                    Conversation.ConversationType.valueOf(
                            intent.getStringExtra("conversationType").toUpperCase(Locale.US));
            targetId = intent.getStringExtra("targetId");
            RongCallCommon.RoomType roomType = RongCallCommon.RoomType.NORMAL;
            if (intent.hasExtra("roomType")) {
                roomType = RongCallCommon.RoomType.valueOf(intent.getIntExtra("roomType", 0));
            }
            List<String> userIds = new ArrayList<>();
            userIds.add(targetId);

            RongCallClient.setPushConfig(
                    DefaultPushConfig.getInviteConfig(
                            this, mediaType == RongCallCommon.CallMediaType.AUDIO, true, ""),
                    DefaultPushConfig.getHangupConfig(this, true, ""));

            if (isCrossCall(targetId)) {
                roomType = RongCallCommon.RoomType.CROSS;
            } else {
                roomType = RongCallCommon.RoomType.NORMAL;
            }
            FinLog.i(TAG, "call type: " + roomType.name() + " targetId" + targetId);

            if (roomType == RongCallCommon.RoomType.NORMAL) {
                RongCallClient.getInstance()
                        .startCall(conversationType, targetId, userIds, null, mediaType, null);
            } else {
                RongCallClient.getInstance()
                        .startCrossCall(conversationType, targetId, userIds, null, mediaType, null);
            }
        } else { // resume call
            callSession = RongCallClient.getInstance().getCallSession();
            mediaType = callSession.getMediaType();
        }

        if (mediaType.equals(RongCallCommon.CallMediaType.AUDIO)) {
            handFree = false;
        } else if (mediaType.equals(RongCallCommon.CallMediaType.VIDEO)) {
            handFree = true;
        }

        UserInfo userInfo = RongUserInfoManager.getInstance().getUserInfo(targetId);
        if (userInfo != null) {
            if (mediaType.equals(RongCallCommon.CallMediaType.AUDIO)
                    || callAction.equals(RongCallAction.ACTION_INCOMING_CALL)) {
                ImageView userPortrait =
                        (ImageView) mUserInfoContainer.findViewById(R.id.rc_voip_user_portrait);
                if (userPortrait != null && userInfo.getPortraitUri() != null) {
                    RongCallKit.getKitImageEngine()
                            .loadPortrait(
                                    getBaseContext(),
                                    userInfo.getPortraitUri(),
                                    R.drawable.rc_default_portrait,
                                    userPortrait);
                }
                TextView userName =
                        (TextView) mUserInfoContainer.findViewById(R.id.rc_voip_user_name);
                userName.setText(CallKitUtils.nickNameRestrict(userInfo.getName()));
            }
        }
        if (callAction.equals(RongCallAction.ACTION_INCOMING_CALL) && userInfo != null) {
            ImageView iv_icoming_backgroud =
                    (ImageView) mUserInfoContainer.findViewById(R.id.iv_icoming_backgroud);
            if (iv_icoming_backgroud != null) {
                iv_icoming_backgroud.setVisibility(View.VISIBLE);
                RongCallKit.getKitImageEngine()
                        .loadPortrait(
                                getBaseContext(),
                                userInfo.getPortraitUri(),
                                R.drawable.rc_default_portrait,
                                iv_icoming_backgroud);
            }
        }
        createPickupDetector();
    }

    private boolean isCrossCall(String targetId) {
        if (!TextUtils.isEmpty(targetId) && targetId.contains("_")) {
            String[] pairs = targetId.split("_");
            if (pairs.length == 2 && pairs[0].length() == 13) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "---single activity onResume---");
        if (pickupDetector != null && mediaType.equals(RongCallCommon.CallMediaType.AUDIO)) {
            pickupDetector.register(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "---single activity onPause---");
        if (pickupDetector != null) {
            pickupDetector.unRegister();
        }
    }

    private void callSurfaceDestroyed() {
        String isOpenFU = PreferenceUtil.getString(this, PreferenceUtil.KEY_FACEUNITY_IS_ON);
        if ("false".equals(isOpenFU)) {
            return;
        }

        if (!mIsRenderPaused) {
            mFURenderer.release();
            mIsRenderPaused = true;
            mIsFirstFrame = true;
            FaceUnityDataFactory.release();
            if (mCSVUtils != null) {
                mCSVUtils.close();
            }
        }
    }

    private void initView(RongCallCommon.CallMediaType mediaType, RongCallAction callAction) {
        RelativeLayout buttonLayout =
                (RelativeLayout)
                        inflater.inflate(
                                R.layout.rc_voip_call_bottom_connected_button_layout, null);
        RelativeLayout userInfoLayout = null;
        if (mediaType.equals(RongCallCommon.CallMediaType.AUDIO)) {
            userInfoLayout =
                    (RelativeLayout)
                            inflater.inflate(R.layout.rc_voip_audio_call_user_info_incoming, null);
            userInfoLayout.findViewById(R.id.iv_large_preview_Mask).setVisibility(View.VISIBLE);
        } else {
            // 单人视频 or 拨打 界面
            userInfoLayout =
                    (RelativeLayout) inflater.inflate(R.layout.rc_voip_audio_call_user_info, null);
            TextView callInfo =
                    (TextView) userInfoLayout.findViewById(R.id.rc_voip_call_remind_info);
            CallKitUtils.textViewShadowLayer(callInfo, SingleCallActivity.this);
        }

        if (callAction.equals(RongCallAction.ACTION_RESUME_CALL) && CallKitUtils.isDial) {
            try {
                ImageView button = buttonLayout.findViewById(R.id.rc_voip_call_mute_btn);
                button.setEnabled(false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (callAction.equals(RongCallAction.ACTION_OUTGOING_CALL)) {
            RelativeLayout layout = buttonLayout.findViewById(R.id.rc_voip_call_mute);
            layout.setVisibility(View.VISIBLE);
            ImageView button = buttonLayout.findViewById(R.id.rc_voip_call_mute_btn);
            button.setEnabled(true);
            buttonLayout.findViewById(R.id.rc_voip_handfree).setVisibility(View.VISIBLE);
        }

        if (mediaType.equals(RongCallCommon.CallMediaType.AUDIO)) {
            findViewById(R.id.rc_voip_call_information)
                    .setBackgroundColor(getResources().getColor(R.color.rc_voip_background_color));
            mLPreviewContainer.setVisibility(View.GONE);
            mSPreviewContainer.setVisibility(View.GONE);

            if (callAction.equals(RongCallAction.ACTION_INCOMING_CALL)) {
                buttonLayout =
                        (RelativeLayout)
                                inflater.inflate(
                                        R.layout.rc_voip_call_bottom_incoming_button_layout, null);
                ImageView iv_answerBtn =
                        (ImageView) buttonLayout.findViewById(R.id.rc_voip_call_answer_btn);
                iv_answerBtn.setBackground(
                        CallKitUtils.BackgroundDrawable(
                                R.drawable.rc_voip_audio_answer_selector_new,
                                SingleCallActivity.this));

                TextView callInfo =
                        (TextView) userInfoLayout.findViewById(R.id.rc_voip_call_remind_info);
                CallKitUtils.textViewShadowLayer(callInfo, SingleCallActivity.this);
                callInfo.setText(R.string.rc_voip_audio_call_inviting);
                onIncomingCallRinging(callSession);
            }
        } else if (mediaType.equals(RongCallCommon.CallMediaType.VIDEO)) {
            if (callAction.equals(RongCallAction.ACTION_INCOMING_CALL)) {
                findViewById(R.id.rc_voip_call_information)
                        .setBackgroundColor(getResources().getColor(android.R.color.transparent));
                buttonLayout =
                        (RelativeLayout)
                                inflater.inflate(
                                        R.layout.rc_voip_call_bottom_incoming_button_layout, null);
                ImageView iv_answerBtn =
                        (ImageView) buttonLayout.findViewById(R.id.rc_voip_call_answer_btn);
                iv_answerBtn.setBackground(
                        CallKitUtils.BackgroundDrawable(
                                R.drawable.rc_voip_vedio_answer_selector_new,
                                SingleCallActivity.this));

                TextView callInfo =
                        (TextView) userInfoLayout.findViewById(R.id.rc_voip_call_remind_info);
                CallKitUtils.textViewShadowLayer(callInfo, SingleCallActivity.this);
                callInfo.setText(R.string.rc_voip_video_call_inviting);
                onIncomingCallRinging(callSession);
            }
        }
        mButtonContainer.removeAllViews();
        mButtonContainer.addView(buttonLayout);
        mUserInfoContainer.removeAllViews();
        mUserInfoContainer.addView(userInfoLayout);
    }

    @Override
    public void onCallOutgoing(RongCallSession callSession, SurfaceView localVideo) {
        super.onCallOutgoing(callSession, localVideo);
        this.callSession = callSession;
        try {
            UserInfo InviterUserIdInfo = RongUserInfoManager.getInstance().getUserInfo(targetId);
            UserInfo SelfUserInfo =
                    RongUserInfoManager.getInstance().getUserInfo(callSession.getSelfUserId());
            if (callSession.getMediaType().equals(RongCallCommon.CallMediaType.VIDEO)) {
                mLPreviewContainer.setVisibility(View.VISIBLE);
                localVideo.setTag(callSession.getSelfUserId());
                mLPreviewContainer.addView(localVideo, mLargeLayoutParams);
                if (null != SelfUserInfo && null != SelfUserInfo.getName()) {
                    // 单人视频
                    TextView callkit_voip_user_name_signleVideo =
                            (TextView)
                                    mUserInfoContainer.findViewById(
                                            R.id.callkit_voip_user_name_signleVideo);
                    //                    topUserName = SelfUserInfo.getName();
                    callkit_voip_user_name_signleVideo.setText(
                            CallKitUtils.nickNameRestrict(SelfUserInfo.getName()));
                }
            } else if (callSession.getMediaType().equals(RongCallCommon.CallMediaType.AUDIO)) {
                if (null != InviterUserIdInfo && null != InviterUserIdInfo.getPortraitUri()) {
                    ImageView iv_icoming_backgroud =
                            mUserInfoContainer.findViewById(R.id.iv_icoming_backgroud);
                    RongCallKit.getKitImageEngine()
                            .loadPortrait(
                                    getBaseContext(),
                                    InviterUserIdInfo.getPortraitUri(),
                                    R.drawable.rc_default_portrait,
                                    iv_icoming_backgroud);
                    iv_icoming_backgroud.setVisibility(View.VISIBLE);
                    mUserInfoContainer
                            .findViewById(R.id.iv_large_preview_Mask)
                            .setVisibility(View.VISIBLE);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        callRinging(RingingMode.Outgoing);
    }

    @Override
    public void onCallConnected(RongCallSession callSession, SurfaceView localVideo) {
        super.onCallConnected(callSession, localVideo);
        addPreviewCallback();
        this.callSession = callSession;
        Log.d(TAG, "onCallConnected----mediaType=" + callSession.getMediaType().getValue());
        if (callSession.getMediaType().equals(RongCallCommon.CallMediaType.AUDIO)) {
            findViewById(R.id.rc_voip_call_minimize).setVisibility(View.VISIBLE);
            RelativeLayout btnLayout =
                    (RelativeLayout)
                            inflater.inflate(
                                    R.layout.rc_voip_call_bottom_connected_button_layout, null);
            ImageView button = btnLayout.findViewById(R.id.rc_voip_call_mute_btn);
            button.setEnabled(true);
            mButtonContainer.removeAllViews();
            mButtonContainer.addView(btnLayout);
            RCRTCEngine.getInstance().enableSpeaker(handFree);
        } else {
            mConnectionStateTextView.setVisibility(View.VISIBLE);
            mConnectionStateTextView.setText(R.string.rc_voip_connecting);
            // 二人视频通话接通后 mUserInfoContainer 中更换为无头像的布局
            mUserInfoContainer.removeAllViews();
            inflater.inflate(R.layout.rc_voip_video_call_user_info, mUserInfoContainer);
            UserInfo userInfo = RongUserInfoManager.getInstance().getUserInfo(targetId);
            if (userInfo != null) {
                TextView userName = mUserInfoContainer.findViewById(R.id.rc_voip_user_name);
                userName.setText(CallKitUtils.nickNameRestrict(userInfo.getName()));
                //                userName.setShadowLayer(16F, 0F, 2F,
                // getResources().getColor(R.color.rc_voip_reminder_shadow));//callkit_shadowcolor
                CallKitUtils.textViewShadowLayer(userName, SingleCallActivity.this);
            }
            mLocalVideo = localVideo;
            mLocalVideo.setTag(callSession.getSelfUserId());
            RCRTCEngine.getInstance().enableSpeaker(true);
        }
        TextView tv_rc_voip_call_remind_info =
                (TextView) mUserInfoContainer.findViewById(R.id.rc_voip_call_remind_info);
        CallKitUtils.textViewShadowLayer(tv_rc_voip_call_remind_info, SingleCallActivity.this);
        tv_rc_voip_call_remind_info.setVisibility(View.GONE);
        TextView remindInfo = null;
        if (callSession.getMediaType().equals(RongCallCommon.CallMediaType.AUDIO)) {
            remindInfo = mUserInfoContainer.findViewById(R.id.tv_setupTime);
        } else {
            remindInfo = mUserInfoContainer.findViewById(R.id.tv_setupTime_video);
        }
        if (remindInfo == null) {
            remindInfo = tv_rc_voip_call_remind_info;
        }
        setupTime(remindInfo);

        RongCallClient.getInstance().setEnableLocalAudio(!muted);
        View muteV = mButtonContainer.findViewById(R.id.rc_voip_call_mute);
        if (muteV != null) {
            muteV.setSelected(muted);
        }

        stopRing();
    }

    protected void resetHandFreeStatus(RCAudioRouteType type) {
        ImageView handFreeV = null;
        if (null != mButtonContainer) {
            handFreeV = mButtonContainer.findViewById(R.id.rc_voip_handfree_btn);
        }
        if (handFreeV != null) {
            if (type == RCAudioRouteType.HEADSET || type == RCAudioRouteType.HEADSET_BLUETOOTH) {
                // 耳机态下不在将扬声器设为关闭
                //                handFreeV.setSelected(false);
            } else {
                // 非耳机状态
                handFreeV.setSelected(type == RCAudioRouteType.SPEAKER_PHONE);
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "---single activity onDestroy---");
        stopRing();
        super.onDestroy();
    }

    private RongCallCommon.CallMediaType remoteMediaType;
    int userType;
    SurfaceView remoteVideo;
    String remoteUserId;
    /** 远端首帧是否到来, 音频帧跟视频帧其中一个到来就更改该标记, 从而更新连接状态 */
    boolean isFirstRemoteFrame = false;

    @Override
    public void onRemoteUserJoined(
            final String userId,
            RongCallCommon.CallMediaType mediaType,
            int userType,
            SurfaceView remoteVideo) {
        super.onRemoteUserJoined(userId, mediaType, userType, remoteVideo);
        Log.v(
                TAG,
                "onRemoteUserJoined userID="
                        + userId
                        + ",mediaType="
                        + mediaType.name()
                        + " , userType="
                        + (userType == 1 ? "Normal" : "Observer"));
        this.remoteMediaType = mediaType;
        this.userType = userType;
        this.remoteVideo = remoteVideo;
        this.remoteUserId = userId;
    }

    private volatile int width;
    private volatile int height;

    private boolean mIsFirstFrame = true;
    private volatile boolean mSkip;


    private void addPreviewCallback() {
        String isOpenFU = PreferenceUtil.getString(this, PreferenceUtil.KEY_FACEUNITY_IS_ON);
        if ("false".equals(isOpenFU)) {
            return;
        }

        RongCallClient.getInstance().registerVideoFrameListener(new IVideoFrameListener() {

            /**
             * 检查当前人脸数量
             */
            private void cheekFaceNum() {
                //根据有无人脸 + 设备性能 判断开启的磨皮类型
                float faceProcessorGetConfidenceScore = FUAIKit.getInstance().getFaceProcessorGetConfidenceScore(0);
                if (faceProcessorGetConfidenceScore >= 0.95) {
                    //高端手机并且检测到人脸开启均匀磨皮，人脸点位质
                    if (FURenderKit.getInstance().getFaceBeauty() != null && FURenderKit.getInstance().getFaceBeauty().getBlurType() != FaceBeautyBlurTypeEnum.EquallySkin) {
                        FURenderKit.getInstance().getFaceBeauty().setBlurType(FaceBeautyBlurTypeEnum.EquallySkin);
                        FURenderKit.getInstance().getFaceBeauty().setEnableBlurUseMask(true);
                    }
                } else {
                    if (FURenderKit.getInstance().getFaceBeauty() != null && FURenderKit.getInstance().getFaceBeauty().getBlurType() != FaceBeautyBlurTypeEnum.FineSkin) {
                        FURenderKit.getInstance().getFaceBeauty().setBlurType(FaceBeautyBlurTypeEnum.FineSkin);
                        FURenderKit.getInstance().getFaceBeauty().setEnableBlurUseMask(false);
                    }
                }
            }
            @Override
            public CallVideoFrame processVideoFrame(CallVideoFrame callVideoFrame) {
//                Log.e(TAG, "processVideoFrame: egl context " + EGL14.eglGetCurrentContext());
                int rotation = callVideoFrame.getRotation();
                width = callVideoFrame.getWidth();
                height = callVideoFrame.getHeight();
                if (mIsRenderPaused || mFURenderer == null) {
                    return callVideoFrame;
                }
                if (mIsFirstFrame) {
                    mIsFirstFrame = false;
                    initCsvUtil(SingleCallActivity.this);
                    mFURenderer.prepareRenderer(mFURendererListener);
                }

                if (FUConfig.DEVICE_LEVEL > FuDeviceUtils.DEVICE_LEVEL_MID) {
                    cheekFaceNum();
                }

                long start = System.nanoTime();
                mFURenderer.setInputOrientation(rotation);
                FURenderOutputData data = mFURenderer.onDrawFrameInputWithReturn(callVideoFrame.getData(), width, height);
                long time = System.nanoTime() - start;
                if (mCSVUtils != null) {
                    mCSVUtils.writeCsv(null, time);
                }
                if (mSkippedFrames > 0 || mSkip) {
                    --mSkippedFrames;
                    return callVideoFrame;
                }
                if (data != null && data.getImage() != null && data.getImage().getBuffer() != null) {
                    callVideoFrame.setData(data.getImage().getBuffer());
                }
                return callVideoFrame;
            }
        });
    }
    @Override
    public void onFirstRemoteAudioFrame(String userId) {
        super.onFirstRemoteAudioFrame(userId);
        Log.v(TAG, "onFirstRemoteAudioFrame ");
        if (!isFirstRemoteFrame) {
            changeToConnectedState(userId, remoteMediaType, userType, remoteVideo);
            isFirstRemoteFrame = true;
        }
    }

    @Override
    public void onRemoteUserPublishVideoStream(
            String userId, String streamId, String tag, SurfaceView surfaceView) {
        super.onRemoteUserPublishVideoStream(userId, streamId, tag, surfaceView);
        Log.v(TAG, "onRemoteUserPublishVideoStream userID=" + userId + ",streamId=" + streamId);
        this.remoteVideo = surfaceView;
        addRemoteVideoView(userId, remoteVideo);
    }

    private void changeToConnectedState(
            String userId,
            RongCallCommon.CallMediaType mediaType,
            int userType,
            SurfaceView remoteVideo) {
        mConnectionStateTextView.setVisibility(View.GONE);
        if (RongCallCommon.CallMediaType.VIDEO.equals(mediaType)) {
            if (remoteVideo != null) {
                addRemoteVideoView(userId, remoteVideo);
            }
            mSPreviewContainer.setVisibility(View.VISIBLE);
            mSPreviewContainer.removeAllViews();
            Log.d(TAG, "onRemoteUserJoined mLocalVideo != null=" + (mLocalVideo != null));
            if (mLocalVideo != null) {
                mLocalVideo.setZOrderMediaOverlay(true);
                mLocalVideo.setZOrderOnTop(true);
                mSPreviewContainer.addView(mLocalVideo);
            }
            /** 小窗口点击事件 * */
            mSPreviewContainer.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                SurfaceView fromView =
                                        (SurfaceView) mSPreviewContainer.getChildAt(0);
                                SurfaceView toView = (SurfaceView) mLPreviewContainer.getChildAt(0);
                                fromView.setVisibility(View.INVISIBLE);

                                mLPreviewContainer.removeAllViews();
                                mSPreviewContainer.removeAllViews();
                                fromView.setZOrderOnTop(false);
                                fromView.setZOrderMediaOverlay(false);
                                mLPreviewContainer.addView(fromView, mLargeLayoutParams);
                                toView.setZOrderOnTop(true);
                                toView.setZOrderMediaOverlay(true);
                                mSPreviewContainer.addView(toView);
                                mSPreviewContainer.postDelayed(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                fromView.setVisibility(View.VISIBLE);
                                            }
                                        },
                                        30);
                                if (null != fromView.getTag()
                                        && !TextUtils.isEmpty(fromView.getTag().toString())) {
                                    UserInfo userInfo =
                                            RongUserInfoManager.getInstance()
                                                    .getUserInfo(fromView.getTag().toString());
                                    TextView userName =
                                            (TextView)
                                                    mUserInfoContainer.findViewById(
                                                            R.id.rc_voip_user_name);
                                    //                                    topUserName =
                                    // userInfo.getName();
                                    userName.setText(
                                            CallKitUtils.nickNameRestrict(userInfo.getName()));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
            mButtonContainer.setVisibility(View.GONE);
            mUserInfoContainer.setVisibility(View.GONE);
        }
    }

    /**
     * 当前的布局中是否包含了 RemoteVideoView
     *
     * @param remoteVideo
     * @return
     */
    protected boolean hasRemoteVideoView(SurfaceView remoteVideo) {
        int count = mLPreviewContainer.getChildCount();
        if (count == 0) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            View view = mLPreviewContainer.getChildAt(i);
            if (view == remoteVideo) {
                return true;
            }
        }
        return false;
    }

    private void addRemoteVideoView(String userId, SurfaceView remoteVideo) {
        if (hasRemoteVideoView(remoteVideo)) {
            return;
        }
        findViewById(R.id.rc_voip_call_information)
                .setBackgroundColor(getResources().getColor(android.R.color.transparent));
        mLPreviewContainer.setVisibility(View.VISIBLE);
        mLPreviewContainer.removeAllViews();
        remoteVideo.setTag(userId);

        Log.v(TAG, "onRemoteUserJoined mLPreviewContainer.addView(remoteVideo)");
        mLPreviewContainer.addView(remoteVideo, mLargeLayoutParams);
        mLPreviewContainer.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (isInformationShow) {
                            hideVideoCallInformation();
                        } else {
                            showVideoCallInformation();
                            handler.sendEmptyMessageDelayed(EVENT_FULL_SCREEN, 5 * 1000);
                        }
                    }
                });
    }

    /**
     * 当通话中的某一个参与者切换通话类型，例如由 audio 切换至 video，回调 onMediaTypeChanged。
     *
     * @param userId 切换者的 userId。
     * @param mediaType 切换者，切换后的媒体类型。
     * @param video 切换着，切换后的 camera 信息，如果由 video 切换至 audio，则为 null。
     */
    @Override
    public void onMediaTypeChanged(
            String userId, RongCallCommon.CallMediaType mediaType, SurfaceView video) {
        if (callSession.getSelfUserId().equals(userId)) {
            showShortToast(getString(R.string.rc_voip_switched_to_audio));
        } else {
            if (callSession.getMediaType() != RongCallCommon.CallMediaType.AUDIO) {
                RongCallClient.getInstance()
                        .changeCallMediaType(RongCallCommon.CallMediaType.AUDIO);
                callSession.setMediaType(RongCallCommon.CallMediaType.AUDIO);
                showShortToast(getString(R.string.rc_voip_remote_switched_to_audio));
            }
        }
        initAudioCallView();
        handler.removeMessages(EVENT_FULL_SCREEN);
        mButtonContainer.findViewById(R.id.rc_voip_call_mute).setSelected(muted);
    }

    @Override
    public void onNetworkReceiveLost(String userId, int lossRate) {
        //        RLog.d(TAG, "onNetworkReceiveLost : userId =" + userId + "   lossRate=" +
        // lossRate);
        isReceiveLost = lossRate > LOSS_RATE_ALARM;
        handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        refreshConnectionState();
                    }
                });
    }

    @Override
    public void onNetworkSendLost(int lossRate, int delay) {
        //        RLog.d(TAG, "onNetworkSendLost : rate =" + lossRate + "   delay=" + delay);
        isSendLost = lossRate > LOSS_RATE_ALARM;
        handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        refreshConnectionState();
                    }
                });
    }

    @Override
    public void onFirstRemoteVideoFrame(String userId, int height, int width) {
        Log.d(TAG, "onFirstRemoteVideoFrame for user::" + userId);
        if (userId.equals(remoteUserId)) {
            //            mConnectionStateTextView.setVisibility(View.GONE);
            if (!isFirstRemoteFrame) {
                changeToConnectedState(userId, remoteMediaType, userType, remoteVideo);
                isFirstRemoteFrame = true;
            }
        }
    }

    /** 视频转语音 * */
    private void initAudioCallView() {
        mLPreviewContainer.removeAllViews();
        mLPreviewContainer.setVisibility(View.GONE);
        mSPreviewContainer.removeAllViews();
        mSPreviewContainer.setVisibility(View.GONE);
        // 显示全屏底色
        findViewById(R.id.rc_voip_call_information)
                .setBackgroundColor(getResources().getColor(R.color.rc_voip_background_color));
        findViewById(R.id.rc_voip_audio_chat).setVisibility(View.GONE); // 隐藏语音聊天按钮

        View userInfoView = inflater.inflate(R.layout.rc_voip_audio_call_user_info_incoming, null);
        TextView tv_rc_voip_call_remind_info =
                (TextView) userInfoView.findViewById(R.id.rc_voip_call_remind_info);
        tv_rc_voip_call_remind_info.setVisibility(View.GONE);

        TextView timeView = (TextView) userInfoView.findViewById(R.id.tv_setupTime);
        setupTime(timeView);

        mUserInfoContainer.removeAllViews();
        mUserInfoContainer.addView(userInfoView);
        UserInfo userInfo = RongUserInfoManager.getInstance().getUserInfo(targetId);
        if (userInfo != null) {
            TextView userName = (TextView) mUserInfoContainer.findViewById(R.id.rc_voip_user_name);
            userName.setText(CallKitUtils.nickNameRestrict(userInfo.getName()));
            if (callSession.getMediaType().equals(RongCallCommon.CallMediaType.AUDIO)) {
                ImageView userPortrait =
                        (ImageView) mUserInfoContainer.findViewById(R.id.rc_voip_user_portrait);
                if (userPortrait != null) {
                    RongCallKit.getKitImageEngine()
                            .loadPortrait(
                                    getBaseContext(),
                                    userInfo.getPortraitUri(),
                                    R.drawable.rc_default_portrait,
                                    userPortrait);
                    //                    Glide.with(this)
                    //                            .load(userInfo.getPortraitUri())
                    //                            .placeholder(R.drawable.rc_default_portrait)
                    //                            .apply(RequestOptions.bitmapTransform(new
                    // CircleCrop()))
                    //                            .into(userPortrait);
                }
            } else { // 单人视频接听layout
                ImageView iv_large_preview = mUserInfoContainer.findViewById(R.id.iv_large_preview);
                iv_large_preview.setVisibility(View.VISIBLE);
                RongCallKit.getKitImageEngine()
                        .loadPortrait(
                                getBaseContext(),
                                userInfo.getPortraitUri(),
                                R.drawable.rc_default_portrait,
                                iv_large_preview);
            }
        }
        mUserInfoContainer.setVisibility(View.VISIBLE);
        mUserInfoContainer.findViewById(R.id.rc_voip_call_minimize).setVisibility(View.VISIBLE);

        View button = inflater.inflate(R.layout.rc_voip_call_bottom_connected_button_layout, null);
        mButtonContainer.removeAllViews();
        mButtonContainer.addView(button);
        mButtonContainer.setVisibility(View.VISIBLE);
        // 视频转音频时默认不开启免提
        handFree = false;
        RongCallClient.getInstance().setEnableSpeakerphone(false);
        View handFreeV = mButtonContainer.findViewById(R.id.rc_voip_handfree);
        handFreeV.setSelected(handFree);

        ImageView iv_large_preview_Mask =
                (ImageView) userInfoView.findViewById(R.id.iv_large_preview_Mask);
        iv_large_preview_Mask.setVisibility(View.VISIBLE);

        /** 视频切换成语音 全是语音界面的ui* */
        ImageView iv_large_preview = mUserInfoContainer.findViewById(R.id.iv_icoming_backgroud);

        if (null != userInfo
                && callSession.getMediaType().equals(RongCallCommon.CallMediaType.AUDIO)) {
            RongCallKit.getKitImageEngine()
                    .loadPortrait(
                            getBaseContext(),
                            userInfo.getPortraitUri(),
                            R.drawable.rc_default_portrait,
                            iv_large_preview);
            iv_large_preview.setVisibility(View.VISIBLE);
        }

        if (pickupDetector != null) {
            pickupDetector.register(this);
        }
    }

    public void onHangupBtnClick(View view) {
        //        unRegisterHeadsetplugReceiver();
        callSurfaceDestroyed();
        RongCallSession session = RongCallClient.getInstance().getCallSession();
        if (session == null || isFinishing) {
            finish();
            Log.e(
                    TAG,
                    "hangup call error:  callSession="
                            + (callSession == null)
                            + ",isFinishing="
                            + isFinishing);
            return;
        }
        RongCallClient.getInstance().hangUpCall(session.getCallId());
        stopRing();
    }

    public void onReceiveBtnClick(View view) {
        RongCallSession session = RongCallClient.getInstance().getCallSession();
        if (session == null || isFinishing) {
            Log.e(
                    TAG,
                    "hangup call error:  callSession="
                            + (callSession == null)
                            + ",isFinishing="
                            + isFinishing);
            finish();
            return;
        }
        RongCallClient.getInstance().acceptCall(session.getCallId());
    }

    public void hideVideoCallInformation() {
        isInformationShow = false;
        mUserInfoContainer.setVisibility(View.GONE);
        mButtonContainer.setVisibility(View.GONE);
        findViewById(R.id.rc_voip_audio_chat).setVisibility(View.GONE);
    }

    public void showVideoCallInformation() {
        isInformationShow = true;
        mUserInfoContainer.setVisibility(View.VISIBLE);

        mUserInfoContainer.findViewById(R.id.rc_voip_call_minimize).setVisibility(View.VISIBLE);
        mButtonContainer.setVisibility(View.VISIBLE);
        RelativeLayout btnLayout =
                (RelativeLayout)
                        inflater.inflate(
                                R.layout.rc_voip_call_bottom_connected_button_layout, null);
        btnLayout.findViewById(R.id.rc_voip_call_mute).setSelected(muted);
        btnLayout.findViewById(R.id.rc_voip_handfree).setVisibility(View.GONE);
        btnLayout.findViewById(R.id.rc_voip_camera).setVisibility(View.VISIBLE);
        mButtonContainer.removeAllViews();
        mButtonContainer.addView(btnLayout);
        View view = findViewById(R.id.rc_voip_audio_chat);
        view.setVisibility(View.VISIBLE);
        view.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (RongIMClient.getInstance().getCurrentConnectionStatus()
                                == RongIMClient.ConnectionStatusListener.ConnectionStatus
                                        .CONNECTED) {
                            RongCallClient.getInstance()
                                    .changeCallMediaType(RongCallCommon.CallMediaType.AUDIO);
                            callSession.setMediaType(RongCallCommon.CallMediaType.AUDIO);
                            initAudioCallView();
                        } else {
                            showShortToast(getString(R.string.rc_voip_im_connection_abnormal));
                        }
                    }
                });
    }

    public void onHandFreeButtonClick(View view) {
        CallKitUtils.speakerphoneState = !view.isSelected();
        RongCallClient.getInstance()
                .setEnableSpeakerphone(!view.isSelected()); // true:打开免提 false:关闭免提
        view.setSelected(!view.isSelected());
        handFree = view.isSelected();
    }

    public void onMuteButtonClick(View view) {
        RongCallClient.getInstance().setEnableLocalAudio(view.isSelected());
        view.setSelected(!view.isSelected());
        muted = view.isSelected();
    }

    @Override
    public void onCallDisconnected(
            RongCallSession callSession, RongCallCommon.CallDisconnectedReason reason) {
        super.onCallDisconnected(callSession, reason);

        String senderId;
        String extra = "";

        isFinishing = true;
        if (callSession == null) {
            RLog.e(TAG, "onCallDisconnected. callSession is null!");
            postRunnableDelay(
                    new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    });
            return;
        }
        senderId = callSession.getInviterUserId();
        long time = getTime(callSession.getActiveTime());
        if (time > 0) {
            if (time >= 3600) {
                extra = String.format("%d:%02d:%02d", time / 3600, (time % 3600) / 60, (time % 60));
            } else {
                extra = String.format("%02d:%02d", (time % 3600) / 60, (time % 60));
            }
        }
        cancelTime();

        if (!TextUtils.isEmpty(senderId)) {
            CallSTerminateMessage message = new CallSTerminateMessage();
            message.setReason(reason);
            message.setMediaType(callSession.getMediaType());
            message.setExtra(extra);
            long serverTime =
                    System.currentTimeMillis() - RongIMClient.getInstance().getDeltaTime();
            if (senderId.equals(callSession.getSelfUserId())) {
                message.setDirection("MO");
                IMCenter.getInstance()
                        .insertOutgoingMessage(
                                Conversation.ConversationType.PRIVATE,
                                callSession.getTargetId(),
                                io.rong.imlib.model.Message.SentStatus.SENT,
                                message,
                                serverTime,
                                null);
            } else {
                message.setDirection("MT");
                IMCenter.getInstance()
                        .insertIncomingMessage(
                                Conversation.ConversationType.PRIVATE,
                                callSession.getTargetId(),
                                senderId,
                                CallKitUtils.getReceivedStatus(reason),
                                message,
                                serverTime,
                                null);
            }
        }
        callSurfaceDestroyed();
        postRunnableDelay(
                new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
    }

    private Runnable mCheckConnectionStableTask =
            new Runnable() {
                @Override
                public void run() {
                    boolean isConnectionStable = !isSendLost && !isReceiveLost;
                    if (isConnectionStable) {
                        mConnectionStateTextView.setVisibility(View.GONE);
                    }
                }
            };

    private void refreshConnectionState() {
        if (isSendLost || isReceiveLost) {
            if (mConnectionStateTextView.getVisibility() == View.GONE) {
                mConnectionStateTextView.setText(R.string.rc_voip_unstable_call_connection);
                mConnectionStateTextView.setVisibility(View.VISIBLE);
                if (mSoundPool != null) {
                    mSoundPool.release();
                }
                mSoundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
                mSoundPool.load(this, R.raw.voip_network_error_sound, 0);
                mSoundPool.setOnLoadCompleteListener(
                        new SoundPool.OnLoadCompleteListener() {
                            @Override
                            public void onLoadComplete(
                                    SoundPool soundPool, int sampleId, int status) {
                                soundPool.play(sampleId, 1F, 1F, 0, 0, 1F);
                            }
                        });
            }
            mConnectionStateTextView.removeCallbacks(mCheckConnectionStableTask);
            mConnectionStateTextView.postDelayed(mCheckConnectionStableTask, 3000);
        }
    }

    @Override
    public void onRestoreFloatBox(Bundle bundle) {
        super.onRestoreFloatBox(bundle);
        Log.d(TAG, "---single activity onRestoreFloatBox---");
        if (bundle == null) return;
        muted = bundle.getBoolean("muted");
        handFree = bundle.getBoolean("handFree");
        //        topUserName=bundle.getString(EXTRA_BUNDLE_KEY_USER_TOP_NAME);

        setShouldShowFloat(true);
        callSession = RongCallClient.getInstance().getCallSession();
        if (callSession == null) {
            setShouldShowFloat(false);
            finish();
            return;
        }
        RongCallCommon.CallMediaType mediaType = callSession.getMediaType();
        RongCallAction callAction =
                RongCallAction.valueOf(getIntent().getStringExtra("callAction"));
        inflater = LayoutInflater.from(this);
        initView(mediaType, callAction);
        targetId = callSession.getTargetId();
        UserInfo userInfo = RongUserInfoManager.getInstance().getUserInfo(targetId);
        if (userInfo != null) {
            TextView userName = (TextView) mUserInfoContainer.findViewById(R.id.rc_voip_user_name);
            userName.setText(CallKitUtils.nickNameRestrict(userInfo.getName()));
            if (mediaType.equals(RongCallCommon.CallMediaType.AUDIO)) {
                ImageView userPortrait =
                        (ImageView) mUserInfoContainer.findViewById(R.id.rc_voip_user_portrait);
                if (userPortrait != null) {
                    RongCallKit.getKitImageEngine()
                            .loadPortrait(
                                    getBaseContext(),
                                    userInfo.getPortraitUri(),
                                    R.drawable.rc_default_portrait,
                                    userPortrait);
                }
            } else if (mediaType.equals(RongCallCommon.CallMediaType.VIDEO)) {
                if (null != callAction && callAction.equals(RongCallAction.ACTION_INCOMING_CALL)) {
                    ImageView iv_large_preview =
                            mUserInfoContainer.findViewById(R.id.iv_large_preview);
                    iv_large_preview.setVisibility(View.VISIBLE);
                    Uri imgUri = userInfo == null ? null : userInfo.getPortraitUri();
                    RongCallKit.getKitImageEngine()
                            .loadPortrait(
                                    getBaseContext(),
                                    imgUri,
                                    R.drawable.rc_default_portrait,
                                    iv_large_preview);
                }
            }
        }
        SurfaceView localVideo = null;
        SurfaceView remoteVideo = null;
        String remoteUserId = null;
        for (CallUserProfile profile : callSession.getParticipantProfileList()) {
            if (profile.getUserId().equals(RongIMClient.getInstance().getCurrentUserId())) {
                localVideo = profile.getVideoView();
            } else {
                remoteVideo = profile.getVideoView();
                remoteUserId = profile.getUserId();
            }
        }
        if (localVideo != null && localVideo.getParent() != null) {
            ((ViewGroup) localVideo.getParent()).removeView(localVideo);
        }
        onCallOutgoing(callSession, localVideo);
        if (!(boolean) bundle.get("isDial")) {
            onCallConnected(callSession, localVideo);
        }
        if (remoteVideo != null) {
            if (remoteVideo.getParent() != null) {
                ((ViewGroup) remoteVideo.getParent()).removeView(remoteVideo);
            }
            changeToConnectedState(remoteUserId, mediaType, 1, remoteVideo);
        }
    }

    @Override
    public String onSaveFloatBoxState(Bundle bundle) {
        super.onSaveFloatBoxState(bundle);
        callSession = RongCallClient.getInstance().getCallSession();
        if (callSession == null) {
            return null;
        }
        bundle.putBoolean("muted", muted);
        bundle.putBoolean("handFree", handFree);
        bundle.putInt("mediaType", callSession.getMediaType().getValue());
        //        bundle.putString(EXTRA_BUNDLE_KEY_USER_TOP_NAME, topUserName);
        return getIntent().getAction();
    }

    public void onMinimizeClick(View view) {
        super.onMinimizeClick(view);
    }

    public void onSwitchCameraClick(View view) {
        RongCallClient.getInstance().switchCamera();
    }

    @Override
    public void onBackPressed() {
        return;
        //        List<CallUserProfile> participantProfiles =
        // callSession.getParticipantProfileList();
        //        RongCallCommon.CallStatus callStatus = null;
        //        for (CallUserProfile item : participantProfiles) {
        //            if (item.getUserId().equals(callSession.getSelfUserId())) {
        //                callStatus = item.getCallStatus();
        //                break;
        //            }
        //        }
        //        if (callStatus != null && callStatus.equals(RongCallCommon.CallStatus.CONNECTED))
        // {
        //            super.onBackPressed();
        //        } else {
        //            RongCallClient.getInstance().hangUpCall(callSession.getCallId());
        //        }
    }

    @Override
    public void onUserUpdate(UserInfo info) {
        if (isFinishing()) {
            return;
        }
        if (targetId != null && targetId.equals(info.getUserId())) {
            TextView userName = (TextView) mUserInfoContainer.findViewById(R.id.rc_voip_user_name);
            if (info.getName() != null)
                userName.setText(CallKitUtils.nickNameRestrict(info.getName()));

            ImageView userPortrait =
                    (ImageView) mUserInfoContainer.findViewById(R.id.rc_voip_user_portrait);
            if (userPortrait != null
                    && info.getPortraitUri() != null
                    && userPortrait.getVisibility() == View.VISIBLE) {
                RongCallKit.getKitImageEngine()
                        .loadPortrait(
                                getBaseContext(),
                                info.getPortraitUri(),
                                R.drawable.rc_default_portrait,
                                userPortrait);
            }
        }
    }

    public void onHeadsetPlugUpdate(HeadsetInfo headsetInfo) {
        if (headsetInfo == null || !BluetoothUtil.isForground(SingleCallActivity.this)) {
            Log.v(TAG, "SingleCallActivity 不在前台！");
            return;
        }
        Log.v(
                TAG,
                "Insert="
                        + headsetInfo.isInsert()
                        + ",headsetInfo.getType="
                        + headsetInfo.getType().getValue());
        try {
            if (headsetInfo.isInsert()) {
                RongCallClient.getInstance().setEnableSpeakerphone(false);
                ImageView handFreeV = null;
                if (null != mButtonContainer) {
                    handFreeV = mButtonContainer.findViewById(R.id.rc_voip_handfree_btn);
                }
                if (handFreeV != null) {
                    handFreeV.setSelected(false);
                    handFreeV.setEnabled(false);
                    handFreeV.setClickable(false);
                }
                if (headsetInfo.getType() == HeadsetInfo.HeadsetType.BluetoothA2dp) {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    am.setMode(AudioManager.MODE_IN_COMMUNICATION);
                    am.startBluetoothSco();
                    am.setBluetoothScoOn(true);
                    am.setSpeakerphoneOn(false);
                }
            } else {
                if (headsetInfo.getType() == HeadsetInfo.HeadsetType.WiredHeadset
                        && BluetoothUtil.hasBluetoothA2dpConnected()) {
                    return;
                }
                RongCallClient.getInstance().setEnableSpeakerphone(true);
                ImageView handFreeV = mButtonContainer.findViewById(R.id.rc_voip_handfree_btn);
                if (handFreeV != null) {
                    handFreeV.setSelected(true);
                    handFreeV.setEnabled(true);
                    handFreeV.setClickable(true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "SingleCallActivity->onHeadsetPlugUpdate Error=" + e.getMessage());
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        if (Math.abs(x) > 3 || Math.abs(y) > 3) {
            if (Math.abs(x) > Math.abs(y)) {
                mFURenderer.setDeviceOrientation(x > 0 ? 0 : 180);
            } else {
                mFURenderer.setDeviceOrientation(y > 0 ? 90 : 270);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void initCsvUtil(Context context) {
        mCSVUtils = new CSVUtils(context);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
        String dateStrDir = format.format(new Date(System.currentTimeMillis()));
        dateStrDir = dateStrDir.replaceAll("-", "").replaceAll("_", "");
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault());
        String dateStrFile = df.format(new Date());
        String filePath = Constant.filePath + dateStrDir + File.separator + "excel-" + dateStrFile + ".csv";
        Log.d(TAG, "initLog: CSV file path:" + filePath);
        StringBuilder headerInfo = new StringBuilder();
        headerInfo.append("version：").append(FURenderer.getInstance().getVersion()).append(CSVUtils.COMMA)
                .append("机型：").append(android.os.Build.MANUFACTURER).append(android.os.Build.MODEL)
                .append("处理方式：Texture").append(CSVUtils.COMMA);
        mCSVUtils.initHeader(filePath, headerInfo);
    }
}
