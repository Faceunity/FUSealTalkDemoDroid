package cn.rongcloud.im.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
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
import io.rong.imlib.model.Conversation;

/**
 * 私聊详情视图模型
 */
public class PrivateChatSettingViewModel extends AndroidViewModel {
    private final String TAG = "PrivateChatSettingViewModel";
    private UserTask userTask;
    private MediatorLiveData<Resource<FriendShipInfo>> friendShipInfoLiveData = new MediatorLiveData<>();

    private String targetId;
    private Conversation.ConversationType conversationType;
    private SingleSourceLiveData<Resource<Boolean>> isNotifyLiveData = new SingleSourceLiveData<>();
    private SingleSourceLiveData<Resource<Boolean>> isTopLiveData = new SingleSourceLiveData<>();
    private SingleSourceLiveData<Resource<Boolean>> cleanMessageResult = new SingleSourceLiveData<>();
    private SingleSourceLiveData<Resource<ScreenCaptureResult>> screenCaptureResult = new SingleSourceLiveData<>();
    private SingleSourceLiveData<Resource<Void>> setScreenCaptureResult = new SingleSourceLiveData<>();
    private FriendTask friendTask;
    private PrivacyTask privacyTask;
    private IMManager imManager;

    public PrivateChatSettingViewModel(@NonNull Application application) {
        super(application);
        friendTask = new FriendTask(application);
        imManager = IMManager.getInstance();
    }

    public PrivateChatSettingViewModel(@NonNull Application application, String targetId, Conversation.ConversationType conversationType) {
        super(application);

        userTask = new UserTask(application);
        friendTask = new FriendTask(application);
        privacyTask = new PrivacyTask(application);
        imManager = IMManager.getInstance();
        this.targetId = targetId;
        this.conversationType = conversationType;

        isNotifyLiveData.setSource(imManager.getConversationNotificationStatus(conversationType, targetId));
        isTopLiveData.setSource(imManager.getConversationIsOnTop(conversationType, targetId));
        getScreenCaptureStatus();
    }

    /**
     * 获取是否开启截屏通知
     */
    private void getScreenCaptureStatus() {
        screenCaptureResult.setSource(privacyTask.getScreenCapture(conversationType.getValue(), targetId));
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
        setScreenCaptureResult.setSource(privacyTask.setScreenCapture(conversationType.getValue(), targetId, status));
    }

    public LiveData<Resource<Void>> getSetScreenCaptureResult(){
        return  setScreenCaptureResult;
    }


    /**
     * 请求好友信息.
     */
    public void requestFriendInfo() {

        // 支持加入信息是自己的话，支持查看自己， 则需要查询自己的信息
        if (IMManager.getInstance().getCurrentId().equals(targetId)) {
            friendShipInfoLiveData.addSource(userTask.getUserInfo(targetId), new Observer<Resource<UserInfo>>() {
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
                        friendShipInfoLiveData.postValue(new Resource<>(resource.status, info, resource.code));
                    }
                }
            });
        } else {
            friendShipInfoLiveData.addSource(friendTask.getFriendInfo(targetId), new Observer<Resource<FriendShipInfo>>() {
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

    /**
     * 设置是否消息免打扰
     *
     * @param isNotify
     */
    public void setIsNotifyConversation(final boolean isNotify) {
        Resource<Boolean> value = isNotifyLiveData.getValue();
        if (value != null && value.data != null && value.data == isNotify) return;

        isNotifyLiveData.setSource(imManager.setConversationNotificationStatus(conversationType, targetId, isNotify));
    }

    /**
     * 设置会话置顶
     *
     * @param isTop
     */
    public void setConversationOnTop(boolean isTop) {
        Resource<Boolean> value = isTopLiveData.getValue();
        if (value != null && value.data != null && value.data == isTop) return;

        isTopLiveData.setSource(imManager.setConversationToTop(conversationType, targetId, isTop));
    }

    /**
     * 获取会话是否接受消息通知
     *
     * @return
     */
    public MutableLiveData<Resource<Boolean>> getIsNotify() {
        return isNotifyLiveData;
    }

    /**
     * 获取会话是否置顶
     *
     * @return
     */
    public MutableLiveData<Resource<Boolean>> getIsTop() {
        return isTopLiveData;
    }

    /**
     * 清除历史消息
     */
    public void cleanHistoryMessage() {
        cleanMessageResult.setSource(imManager.cleanHistoryMessage(conversationType, targetId));
    }

    /**
     * 获取清除历史消息结果
     *
     * @return
     */
    public LiveData<Resource<Boolean>> getCleanHistoryMessageResult() {
        return cleanMessageResult;
    }

    public static class Factory implements ViewModelProvider.Factory {
        private String targetId;
        private Conversation.ConversationType conversationType;
        private Application application;

        public Factory(Application application, String targetId, Conversation.ConversationType conversationType) {
            this.conversationType = conversationType;
            this.targetId = targetId;
            this.application = application;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            try {
                return modelClass.getConstructor(Application.class, String.class, Conversation.ConversationType.class).newInstance(application, targetId, conversationType);
            } catch (Exception e) {
                throw new RuntimeException("Cannot create an instance of " + modelClass, e);
            }
        }
    }

}
