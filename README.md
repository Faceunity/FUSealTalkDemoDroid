sealtalk 是集成了 FaceUnity 美颜贴纸功能和 **融云音视频** 的 Demo。

SDK 版本为 **7.4.1.0**。关于 SDK 的详细说明，请参看 **[FULiveDemoDroid](https://github.com/Faceunity/FULiveDemoDroid/tree/master/doc)**。

--------

## 快速集成方法

### 一、添加 SDK

### 1. build.gradle配置

#### 1.1 allprojects配置
```java
allprojects {
    repositories {
        ...
        maven { url 'http://maven.faceunity.com/repository/maven-public/' }
        ...
  }
}
```

#### 1.2 dependencies导入依赖
```java
dependencies {
...
implementation 'com.faceunity:core:7.4.1.0' // 实现代码
implementation 'com.faceunity:model:7.4.1.0' // 道具以及AI bundle
...
}
```

##### 备注

集成参考文档：FULiveDemoDroid 工程 doc目录

### 2. 其他接入方式-底层库依赖

```java
dependencies {
...
implementation 'com.faceunity:nama:7.4.0' //底层库-标准版
implementation 'com.faceunity:nama-lite:7.4.0' //底层库-lite版
...
}
```

如需指定应用的 so 架构，请修改 app 模块 build.gradle：

```groovy
android {
    // ...
    defaultConfig {
        // ...
        ndk {
            abiFilters 'armeabi-v7a', 'arm64-v8a'
        }
    }
}
```

如需剔除不必要的 assets 文件，请修改 app 模块 build.gradle：

```groovy
android {
    // ...
    applicationVariants.all { variant ->
        variant.mergeAssetsProvider.configure {
            doLast {
                delete(fileTree(dir: outputDir, includes: ['model/ai_face_processor_lite.bundle',
                                                           'model/ai_hand_processor.bundle',
                                                           'graphics/controller.bundle',
                                                           'graphics/fuzzytoonfilter.bundle',
                                                           'graphics/fxaa.bundle',
                                                           'graphics/tongue.bundle']))
            }
        }
    }
}
```

### 二、使用 SDK

#### 1. 初始化

在 `FURenderer` 类 的  `setup` 方法是对 FaceUnity SDK 全局数据初始化的封装，可以在工作线程调用，仅需初始化一次即可。

在 SingleCallActivity  类中 按照是否需要加载美颜决定是否执行。

#### 2.创建

在 `FURenderer` 类 的  `prepareRenderer` 方法是对 FaceUnity SDK 使用前数据初始化的封装。

在 SingleCallActivity  注册监听 ， 在 processVideoFrame 方法第一次执行时，执行该方法。

```
        RongCallClient.getInstance().registerVideoFrameListener(new IVideoFrameListener() {
            @Override
            public CallVideoFrame processVideoFrame(CallVideoFrame callVideoFrame) {
                rotation = callVideoFrame.getRotation();
                width = callVideoFrame.getWidth();
                height = callVideoFrame.getHeight();
                if (mIsRenderPaused) {
                    return callVideoFrame;
                }
                if (mIsFirstFrame) {
                    mIsFirstFrame = false;
                    initCsvUtil(SingleCallActivity.this);
                    mFURenderer.prepareRenderer(mFURendererListener);
                }

                long start = System.nanoTime();
                mFURenderer.setInputOrientation(callVideoFrame.getRotation());
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
```

#### 3. 图像处理

在 `FURenderer` 类 的  `onDrawFrameXXX` 方法是对 FaceUnity SDK 图像处理的封装，该方法有许多重载方法适用于不同数据类型的需求。

在 processVideoFrame  方法中进行处理（代码如上图）

onDrawFrameSingleInput 是单输入，输入图像buffer数组或者纹理Id，输出纹理Id
onDrawFrameDualInput 双输入，输入图像buffer数组与纹理Id，输出纹理Id。性能上，双输入优于单输入

在onDrawFrameSingleInput 与onDrawFrameDualInput 方法内，在执行底层方法之前，都会执行prepareDrawFrame()方法(执行各个特效模块的任务，将美颜参数传给底层)。

#### 4. 销毁

在 `FURenderer` 类 的  `release` 方法是对 FaceUnity SDK 退出前数据销毁的封装。

#### 5. 切换相机

在 SingleCallActivity  执行 RongCallClient.getInstance().switchCamera 方法是会重新设置 FURenderer 中的参数

注册监听 ， 在 processVideoFrame 方法中进行处理

#### 6. 旋转手机

调用 `FURenderer` 类 的  `setDeviceOrientation` 方法，用于重新为 SDK 设置参数。

使用方法：SingleCallActivity  中可见

```java
1.implements SensorEventListener
2. onCreate()    
mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

3.
protected void destroyRecorder() {
    super.onDestroy();
    // 清理相关资源
    if (mSensorManager != null) {
        mSensorManager.unregisterListener(this);
    }
}
4. 
//实现接口
@Override
public void onSensorChanged(SensorEvent event) {
    //具体代码见 SingleCallActivity   类
}

```

**注意：** 上面一系列方法的使用，可以前往对应类查看，参考该代码示例接入即可。

### 三、接口介绍

- IFURenderer 是核心接口，提供了创建、销毁、渲染等接口。
- FaceUnityDataFactory 控制四个功能模块，用于功能模块的切换，初始化
- FaceBeautyDataFactory 是美颜业务工厂，用于调整美颜参数。
- PropDataFactory 是道具业务工厂，用于加载贴纸效果。
- MakeupDataFactory 是美妆业务工厂，用于加载美妆效果。
- BodyBeautyDataFactory 是美体业务工厂，用于调整美体参数。

**关于 FaceUnity SDK 的更多详细说明，请参看 [FULiveDemoDroid](https://github.com/Faceunity/FULiveDemoDroid/)**。