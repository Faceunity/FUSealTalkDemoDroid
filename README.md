# FUSealTalkDemoDroid
FUQiniuShortVideoDemo 是集成了 Faceunity 面部跟踪和虚拟道具功能和 [sealtalk-android](https://github.com/sealtalk/sealtalk-android) 的 Demo 。
本文是 FaceUnity SDK 快速对接 sealtalk 的导读说明，关于 FaceUnity SDK 的更多详细说明，请参看 [FULiveDemo](https://github.com/Faceunity/FULiveDemoDroid/tree/dev).

## 快速集成方法
### 添加module
添加faceunity module到工程中，在app dependencies里添加compile project(':faceunity')
### 修改代码
#### 生成与销毁
在SingleCallActivity的
onCreate方法中添加（初始化并加载美颜道具、默认道具）
```
mFURenderer = new FURenderer.Builder(this).createEGLContext(true).inputImageOrientation(270).build();
mFURenderer.loadItems();
```
onDestroy方法中添加（销毁道具）
```
mFURenderer.destroyItems();
```
#### 渲染道具到原始数据上
在SingleCallActivity的onCreate方法里使用FURenderer将道具渲染到原始数据上
```
RongCallClient.getInstance().registerVideoFrameListener(new IVideoFrameListener() {
    @Override
    public boolean onCaptureVideoFrame(AgoraVideoFrame agoraVideoFrame) {
        mFURenderer.onDrawFrame(agoraVideoFrame.getyBuffer(), agoraVideoFrame.getuBuffer(), agoraVideoFrame.getvBuffer(), agoraVideoFrame.getyStride(), agoraVideoFrame.getuStride(), agoraVideoFrame.getvStride(), agoraVideoFrame.getWidth(), agoraVideoFrame.getHeight());
        return true;
    }
});
```
### 修改默认美颜参数
修改faceunity中faceunity中以下代码
```
private float mFaceBeautyALLBlurLevel = 1.0f;//精准磨皮
private float mFaceBeautyType = 0.0f;//美肤类型
private float mFaceBeautyBlurLevel = 0.7f;//磨皮
private float mFaceBeautyColorLevel = 0.5f;//美白
private float mFaceBeautyRedLevel = 0.5f;//红润
private float mBrightEyesLevel = 1000.7f;//亮眼
private float mBeautyTeethLevel = 1000.7f;//美牙

private float mFaceBeautyFaceShape = 4.0f;//脸型
private float mFaceBeautyEnlargeEye = 0.4f;//大眼
private float mFaceBeautyCheekThin = 0.4f;//瘦脸
private float mFaceBeautyEnlargeEye_old = 0.4f;//大眼
private float mFaceBeautyCheekThin_old = 0.4f;//瘦脸
private float mChinLevel = 0.3f;//下巴
private float mForeheadLevel = 0.3f;//额头
private float mThinNoseLevel = 0.5f;//瘦鼻
private float mMouthShape = 0.4f;//嘴形
```
参数含义与取值范围参考[这里](http://www.faceunity.com/technical/android-beauty.html)，如果使用界面，则需要同时修改界面中的初始值。