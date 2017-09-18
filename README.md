本代码由[sealtalk-android](https://github.com/sealtalk/sealtalk-android)修改。
# 对接步骤
## 添加module
添加faceunity module到工程中，在app dependencies里添加compile project(':faceunity')
## 修改代码
### 生成与销毁
在SingleCallActivity的
onCreate方法中添加（初始化并加载美颜道具、默认道具）
~~~
FUManager.getInstance(mContext).loadItems();
~~~
onDestroy方法中添加（销毁道具）
~~~
FUManager.getInstance(mContext).destroyItems();
~~~
### 渲染道具到原始数据上
在SingleCallActivity的onCreate方法里使用FUManager将道具渲染到原始数据上
~~~
RongCallClient.getInstance().registerVideoFrameListener(new IVideoFrameListener() {
    @Override
    public boolean onCaptureVideoFrame(AgoraVideoFrame agoraVideoFrame) {
        FUManager.renderItemsToYUVFrame(agoraVideoFrame.getyBuffer(), agoraVideoFrame.getuBuffer(), agoraVideoFrame.getvBuffer(), agoraVideoFrame.getyStride(), agoraVideoFrame.getuStride(), agoraVideoFrame.getvStride(), agoraVideoFrame.getWidth(), agoraVideoFrame.getHeight(), 270);
        return true;
    }
});
~~~
## 添加界面（可选）
### 修改rc_voip_activity_single_call
在rc_voip_activity_single_call(layout文件)的末尾修改（在界面底部显示默认的道具选择控件）
~~~
<LinearLayout
    android:layout_alignParentBottom="true"
    android:layout_width="match_parent"
    android:orientation="vertical"
    android:layout_height="wrap_content">

    <FrameLayout
        android:id="@+id/rc_voip_btn"
        android:layout_width="match_parent"
        android:layout_height="wrap_content">
    </FrameLayout>

    <com.faceunity.EffectView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"/>
</LinearLayout>
~~~
# 更新SDK
[Nama SDK发布地址](https://github.com/Faceunity/FULiveDemoDroid/releases),可查看Nama的所有版本和发布说明。
更新方法为下载Faceunity*.zip解压后替换faceunity模块中的相应文件。
# 定制需求
## 定制界面
修改faceunity中的界面代码
EffectView、EffectAndFilterRecycleViewAdapter和EffectAndFilterItemView或者自己编写。
## 定制道具
faceunity中FUManager ITEM_NAMES指定的是assets里对应的道具的文件名，故如需增删道具只需要在assets增删相应的道具文件并在ITEM_NAMES增删相应的文件名即可。
## 修改默认美颜参数
修改faceunity中FUManager中以下代码
~~~
faceunity.fuItemSetParam(facebeautyItem, "blur_level", 6);
faceunity.fuItemSetParam(facebeautyItem, "color_level", 0.2);
faceunity.fuItemSetParam(facebeautyItem, "red_level", 0.5);
faceunity.fuItemSetParam(facebeautyItem, "face_shape", 3);
faceunity.fuItemSetParam(facebeautyItem, "face_shape_level", 0.5);
faceunity.fuItemSetParam(facebeautyItem, "cheek_thinning", 1);
faceunity.fuItemSetParam(facebeautyItem, "eye_enlarging", 0.5);
~~~
参数含义与取值范围参考[这里](http://www.faceunity.com/technical/android-beauty.html)，如果使用界面，则需要同时修改界面中的初始值。
## 其他需求
nama库的使用参考[这里](http://www.faceunity.com/technical/android-api.html)。
# 2D 3D道具制作
除了使用制作好的道具外，还可以自行制作2D和3D道具，参考[这里](http://www.faceunity.com/technical/fueditor-intro.html)。