package cn.rongcloud.im;

import static io.rong.common.SystemUtils.getCurrentProcessName;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.multidex.MultiDexApplication;
import cn.rongcloud.im.common.ErrorCode;
import cn.rongcloud.im.contact.PhoneContactManager;
import cn.rongcloud.im.im.IMManager;
import cn.rongcloud.im.ui.activity.MainActivity;
import cn.rongcloud.im.ui.activity.SplashActivity;
import cn.rongcloud.im.utils.SearchUtils;
import cn.rongcloud.im.wx.WXManager;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.faceunity.nama.FUConfig;
import com.faceunity.nama.utils.FuDeviceUtils;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.tencent.bugly.crashreport.CrashReport;
import com.umeng.commonsdk.UMConfigure;
import io.rong.imkit.GlideKitImageEngine;
import io.rong.imkit.IMCenter;
import io.rong.imkit.RongIM;
import io.rong.imkit.config.RongConfigCenter;
import io.rong.imkit.utils.language.LangUtils;
import io.rong.imlib.model.Conversation;
import io.rong.imlib.model.Message;
import java.util.HashMap;

public class SealApp extends MultiDexApplication {
    private static SealApp appInstance;
    /** 应用是否在后台 */
    private boolean isAppInForeground;

    private String lastVisibleActivityName;
    private Intent nextOnForegroundIntent;
    private boolean isMainActivityIsCreated;

    public static SealApp getApplication() {
        return appInstance;
    }

    @Override
    protected void attachBaseContext(Context base) {
        Context context = LangUtils.getConfigurationContext(base);
        super.attachBaseContext(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        appInstance = this;
        HashMap<String, Integer> map = new HashMap<>();
        int id = io.rong.imkit.R.drawable.rc_file_icon_pdf;
        map.put("doc", R.drawable.doc_icon);
        map.put("mp3", R.drawable.mp3_icon);
        map.put("pdf", R.drawable.pdf_icon);
        map.put("rmvb", R.drawable.rmvb_icon);
        map.put("default", R.drawable.default_icon);
        map.put("error", 123);
        RongConfigCenter.conversationConfig().registerFileSuffixTypes(map);

        // 初始化 bugly BUG 统计
        // CrashReport.initCrashReport(getApplicationContext());
        // BlockCanary.install(this, new AppBlockCanaryContext()).start();
        CrashReport.initCrashReport(getApplicationContext(), "c0ac38ea5b", true);
        ErrorCode.init(this);
        ImageLoaderConfiguration imageLoaderConfiguration =
                ImageLoaderConfiguration.createDefault(this);
        ImageLoader.getInstance().init(imageLoaderConfiguration);
        /*
         * 以上部分在所有进程中会执行
         */
        if (!getApplicationInfo()
                .packageName
                .equals(getCurrentProcessName(getApplicationContext()))) {
            return;
        }

        // 检查是否争取配置了 SealTalk 参数
        checkSealConfig();

        /*
         * 以下部分仅在主进程中进行执行
         */
        // 初始化融云IM SDK，初始化 SDK 仅需要在主进程中初始化一次
        IMManager.getInstance().init(this);
        RongConfigCenter.featureConfig()
                .setKitImageEngine(
                        new GlideKitImageEngine() {
                            @Override
                            public void loadConversationListPortrait(
                                    @NonNull Context context,
                                    @NonNull String url,
                                    @NonNull ImageView imageView,
                                    Conversation conversation) {
                                @DrawableRes
                                int resourceId = io.rong.imkit.R.drawable.rc_default_portrait;
                                switch (conversation.getConversationType()) {
                                    case GROUP:
                                        resourceId =
                                                io.rong.imkit.R.drawable.rc_default_group_portrait;
                                        break;
                                    case CUSTOMER_SERVICE:
                                        resourceId =
                                                io.rong.imkit.R.drawable.rc_cs_default_portrait;
                                        break;
                                    case CHATROOM:
                                        resourceId =
                                                io.rong
                                                        .imkit
                                                        .R
                                                        .drawable
                                                        .rc_default_chatroom_portrait;
                                        break;
                                }
                                Glide.with(imageView)
                                        .load(url)
                                        .placeholder(resourceId)
                                        .error(resourceId)
                                        .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                                        .into(imageView);
                            }

                            @Override
                            public void loadConversationPortrait(
                                    @NonNull Context context,
                                    @NonNull String url,
                                    @NonNull ImageView imageView,
                                    Message message) {
                                @DrawableRes
                                int resourceId = io.rong.imkit.R.drawable.rc_default_portrait;
                                switch (message.getConversationType()) {
                                    case CUSTOMER_SERVICE:
                                        if (Message.MessageDirection.RECEIVE
                                                == message.getMessageDirection())
                                            resourceId =
                                                    io.rong.imkit.R.drawable.rc_cs_default_portrait;
                                        break;
                                }
                                Glide.with(imageView)
                                        .load(url)
                                        .placeholder(resourceId)
                                        .error(resourceId)
                                        .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                                        .into(imageView);
                            }
                        });
        RongIM.getInstance().setVoiceMessageType(IMCenter.VoiceMessageType.HighQuality);

        SearchUtils.init(this);

        //        Thread.setDefaultUncaughtExceptionHandler(new RongExceptionHandler(this));

        // 微信分享初始化
        WXManager.getInstance().init(this);

        PhoneContactManager.getInstance().init(this);

        // 监听 App 前后台变化
        observeAppInBackground();

        // UMeng初始化
        UMConfigure.preInit(this, BuildConfig.SEALTALK_UMENG_APPKEY, null);

        FUConfig.DEVICE_LEVEL = FuDeviceUtils.judgeDeviceLevelGPU();
    }

    /** 检查是否正确的配置 SealTalk 中的一些必要环境。 */
    private void checkSealConfig() {
        if (!BuildConfig.SEALTALK_SERVER.startsWith("http")) {
            Log.e(
                    "SealTalk 集成错误",
                    "\n"
                            + "您需要确认是否将 sealtalk 目录下 gradle.properties "
                            + "文件中的 SEALTALK_SERVER 参数修改为了您所部署的 SealTalk 服务器。\n"
                            + "同时，建议您阅读下 README.MD 中的关于【运行 SealTalk-Android】部分，以便您能正常运行。");
            throw new IllegalArgumentException("需要运行 SealTalk 您先要正确的指定您的 SealTalk 服务器。");
        }

        if (BuildConfig.SEALTALK_APP_KEY.contains("AppKey")) {
            Log.e(
                    "SealTalk 集成错误",
                    "\n"
                            + "您需要确认是否将 sealtalk 目录下 gradle.properties "
                            + "文件中的 SEALTALK_APP_KEY 参数修改为了您在融云所申请的 AppKey。\n"
                            + "同时，建议您阅读下 README.MD 中的关于【运行 SealTalk-Android】部分，以便您能正常运行。");
            throw new IllegalArgumentException("需要运行 SealTalk 您需要指定您所申请融云的 Appkey。");
        }
    }

    /** 监听应用是否转为后台 */
    private void observeAppInBackground() {
        registerActivityLifecycleCallbacks(
                new ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                        // 解决：切后台关闭应用某项权限,进程被杀,重启应用因SDK还未绑定IPC进程,
                        // 导致当前恢复页面调用SDK API接口返回IPC_DISCONNECT,页面白屏或黑屏现象
                        if (savedInstanceState != null) {
                            Intent intent = new Intent(activity, SplashActivity.class);
                            intent.setFlags(
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            | Intent.FLAG_ACTIVITY_NEW_TASK);
                            activity.startActivity(intent);
                            return;
                        }

                        if (activity instanceof MainActivity) {
                            isMainActivityIsCreated = true;
                        }
                    }

                    @Override
                    public void onActivityStarted(Activity activity) {}

                    @Override
                    public void onActivityResumed(Activity activity) {
                        // 当切换为前台时启动预设的优先显示界面
                        if (isMainActivityIsCreated
                                && !isAppInForeground
                                && nextOnForegroundIntent != null) {
                            activity.startActivity(nextOnForegroundIntent);
                            nextOnForegroundIntent = null;
                        }

                        lastVisibleActivityName = activity.getClass().getSimpleName();
                        isAppInForeground = true;
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {
                        String pauseActivityName = activity.getClass().getSimpleName();
                        /*
                         * 介于 Activity 生命周期在切换画面时现进行要跳转画面的 onResume，
                         * 再进行当前画面 onPause，所以当用户且到后台时肯定会为当前画面直接进行 onPause，
                         * 同过此来判断是否应用在前台
                         */
                        if (pauseActivityName.equals(lastVisibleActivityName)) {
                            isAppInForeground = false;
                        }
                    }

                    @Override
                    public void onActivityStopped(Activity activity) {}

                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

                    @Override
                    public void onActivityDestroyed(Activity activity) {
                        if (activity instanceof MainActivity) {
                            isMainActivityIsCreated = false;
                        }
                    }
                });
    }

    /**
     * 当前 App 是否在前台
     *
     * @return
     */
    public boolean isAppInForeground() {
        return isAppInForeground;
    }

    /**
     * 获取最后在前台的 Activity 名称
     *
     * @return
     */
    public String getLastVisibleActivityName() {
        return lastVisibleActivityName;
    }

    /**
     * 设置当 App 切换为前台时启动的 intent，该 intent 在启动后情况
     *
     * @param intent
     */
    public void setOnAppForegroundStartIntent(Intent intent) {
        nextOnForegroundIntent = intent;
    }

    /**
     * 获取最近设置的未触发的启动 intent
     *
     * @return
     */
    public Intent getLastOnAppForegroundStartIntent() {
        return nextOnForegroundIntent;
    }

    /**
     * 判断是否进入到了主界面
     *
     * @return
     */
    public boolean isMainActivityCreated() {
        return isMainActivityIsCreated;
    }
}
