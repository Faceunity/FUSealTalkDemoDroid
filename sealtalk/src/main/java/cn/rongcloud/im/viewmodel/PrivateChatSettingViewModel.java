package cn.rongcloud.im.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import cn.rongcloud.im.db.model.FriendDetailInfo;
import cn.rongcloud.im.db.model.FriendShipInfo;
import cn.rongcloud.im.db.model.UserInfo;
import cn.rongcloud.im.im.IMManager;
import cn.rongcloud.im.model.Resource;
import cn.rongcloud.im.model.ScreenCaptureResult;
import cn.rongcloud.im.task.FriendTask;
import cn.rongcloud.im.task.PrivacyTask;
import cn.rongcloud.im.task.UserTask;
import cn.rongcloud.im.utils.SingleSourceLiveData;
import io.rong.imlib.model.ConversationIdentifier;

/** 私聊详情视图模型 */
public class PrivateChatSettingViewModel extends AndroidViewModel {
    private final String TAG = "PrivateChatSettingViewModel";
    private UserTask userTask;
    private MediatorLiveData<Resource<FriendShipInfo>> friendShipInfoLiveData =
            new MediatorLiveData<>();

    private ConversationIdentifier conversationIdentifier;
    private SingleSourceLiveData<Resource<ScreenCaptureResult>> screenCaptureResult =
            new SingleSourceLiveData<>();
    private SingleSourceLiveData<Resource<Void>> setScreenCaptureResult =
            new SingleSourceLiveData<>();
    private FriendTask friendTask;
    private PrivacyTask privacyTask;

    public PrivateChatSettingViewModel(@NonNull Application application) {
        super(application);
        friendTask = new FriendTask(application);
    }

    public PrivateChatSettingViewModel(
            @NonNull Application application, ConversationIdentifier conversationIdentifier) {
        super(application);

        userTask = new UserTask(application);
        friendTask = new FriendTask(application);
        privacyTask = new PrivacyTask(application);
        this.conversationIdentifier = conversationIdentifier;
        getScreenCaptureStatus();
    }

    /** 获取是否开启截屏通知 */
    private void getScreenCaptureStatus() {
        screenCaptureResult.setSource(
                privacyTask.getScreenCapture(
                        conversationIdentifier.getTypeValue(),
                        conversationIdentifier.getTargetId()));
    }

    public LiveData<Resource<ScreenCaptureResult>> getScreenCaptureStatusResult() {
        return screenCaptureResult;
    }

    /**
     * 设置是否开启截屏通知
     *
     * @param status
     */
    public void setScreenCaptureStatus(int status) {
        setScreenCaptureResult.setSource(
                privacyTask.setScreenCapture(
                        conversationIdentifier.getTypeValue(),
                        conversationIdentifier.getTargetId(),
                        status));
    }

    public LiveData<Resource<Void>> getSetScreenCaptureResult() {
        return setScreenCaptureResult;
    }

    /** 请求好友信息. */
    public void requestFriendInfo() {
        // 支持加入信息是自己的话，支持查看自己， 则需要查询自己的信息
        if (IMManager.getInstance().getCurrentId().equals(conversationIdentifier.getTargetId())) {
            friendShipInfoLiveData.addSource(
                    userTask.getUserInfo(conversationIdentifier.getTargetId()),
                    new Observer<Resource<UserInfo>>() {
                        @Override
                        public void onChanged(Resource<UserInfo> resource) {
                            if (resource != null && resource.data != null) {
                                UserInfo data = resource.data;
                                FriendShipInfo info = new FriendShipInfo();
                                info.setDisplayName(data.getAlias());
                                info.setDisPlayNameSpelling(data.getAliasSpelling());
                                FriendDetailInfo friendDetailInfo = new FriendDetailInfo();
                                friendDetailInfo.setNickname(data.getName());
                                friendDetailInfo.setId(data.getId());
                                friendDetailInfo.setPhone(data.getPhoneNumber());
                                friendDetailInfo.setNameSpelling(data.getNameSpelling());
                                friendDetailInfo.setOrderSpelling(data.getOrderSpelling());
                                friendDetailInfo.setPortraitUri(data.getPortraitUri());
                                friendDetailInfo.setRegion(data.getRegion());
                                info.setUser(friendDetailInfo);
                                friendShipInfoLiveData.postValue(
                                        new Resource<>(resource.status, info, resource.code));
                            }
                        }
                    });
        } else {
            friendShipInfoLiveData.addSource(
                    friendTask.getFriendInfo(conversationIdentifier.getTargetId()),
                    new Observer<Resource<FriendShipInfo>>() {
                        @Override
                        public void onChanged(Resource<FriendShipInfo> friendShipInfoResource) {
                            friendShipInfoLiveData.postValue(friendShipInfoResource);
                        }
                    });
        }
    }

    /**
     * 获取好友信息
     *
     * @return
     */
    public LiveData<Resource<FriendShipInfo>> getFriendInfo() {
        return friendShipInfoLiveData;
    }

    public static class Factory implements ViewModelProvider.Factory {
        private Application application;
        private ConversationIdentifier conversationIdentifier;

        public Factory(Application application, ConversationIdentifier conversationIdentifier) {
            this.application = application;
            this.conversationIdentifier = conversationIdentifier;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            try {
                return modelClass
                        .getConstructor(Application.class, ConversationIdentifier.class)
                        .newInstance(application, conversationIdentifier);
            } catch (Exception e) {
                throw new RuntimeException("Cannot create an instance of " + modelClass, e);
            }
        }
    }
}
