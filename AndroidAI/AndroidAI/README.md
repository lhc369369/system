# 综合实验-AndroidAI

Android 平台轻量化 AI 模型部署与优化 —— 前置摄像头人脸检测、识别与活体检测。

> **路径要求**:工程必须放在纯英文路径下(当前 `E:\system\AndroidAI`)。
> 中文目录名会让 AGP / aidl.exe 直接报错,手册里的 `D:\system\综合实验-AndroidAI` 不能用了。

## 本机环境

| 项 | 位置 / 值 |
|---|---|
| Android Studio | `F:\Android Studio`(2026.2.2) |
| Android SDK | `C:\Users\21849\AppData\Local\Android\Sdk` |
| SDK 组件 | platform-tools、platforms;android-35、build-tools;35.0.0、cmdline-tools |
| JDK 17 | `D:\Dev\jdk-17`(工程 `gradle.properties` 用 `org.gradle.java.home` 指定) |
| Gradle | 8.7(Wrapper 分发走腾讯镜像) |
| Maven 镜像 | 阿里云(见 `settings.gradle.kts`) |

## 构建与安装

```powershell
cd E:\system\AndroidAI
$env:JAVA_HOME = "D:\Dev\jdk-17"
.\gradlew.bat assembleDebug

adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.example.osai/.MainActivity
```

APK 约 85 MB(含三个 ONNX 模型与 ONNX Runtime)。

## 功能

| 功能 | 说明 |
|---|---|
| 前摄预览 | CameraX,权限/旋转/生命周期处理,前摄镜像绘制 |
| 人脸检测 | SCRFD-500M(INSIGHTFACE buffalo_s),输出框 + 5 关键点 + 置信度 |
| 人脸对齐 | 5 点相似变换到 ArcFace 112×112 模板 |
| 人脸识别 | EdgeFace-xs(gamma=0.6),512 维特征,余弦相似度,阈值 0.45 |
| 活体检测 | MiniFASNetV2(80×80),3 类输出,5 帧投票稳定 |
| 注册 / 删除 | 人脸特征存应用私有目录 `faces.json`,同名覆盖 |
| 双进程 | 推理服务运行在 `:inference`,AIDL/Binder |
| 单进程基线 | 状态页一键切换,模型直接跑在 UI 进程 |
| 线程调度 | 推理线程池 1/2/4 可切换,线程名 `infer-N` |
| 同步 | Semaphore 限制在途推理数,统计许可/提交/完成/超时 |
| 系统状态页 | PID、线程名、信号量、耗时 P50/P95、注册人数 |
| 故障恢复 | 状态页可杀死推理进程,主进程自动重连 |
| 单脸策略 | 多张脸时只对最大一张做识别/活体,全部框都画 |

## 架构

```
CameraX ImageAnalysis (camera-analyzer 线程)
        │  ImageProxy → Bitmap → 旋转 → JPEG
        ▼
 Binder/AIDL IFaceInference.analyzeFrame(FaceFrame)
        │
        ▼
 :inference 进程  InferenceCore
        │  Semaphore(N) → ExecutorService(N, 线程名 infer-N)
        ▼
   InferenceEngine
     SCRFD(320, NNAPI) → 对齐(112) → EdgeFace → MiniFASNet(80)
        │
        ▼
 FrameResult(Binder 回传) → UI 绘制框/关键点/标签
```

## 模型来源

| 文件 | 来源 | 大小 | 说明 |
|---|---|---|---|
| `scrfd.onnx` | InsightFace buffalo_s → HF `immich-app/buffalo_s` `detection/model.onnx` | 2.5 MB | SCRFD-500M,输入动态(实际用 320) |
| `edgeface.onnx` | `otroshi/edgeface` `edgeface_xs_gamma_06.pt` 导出 | 7.0 MB | 输入 1×3×112×112,输出 512 维 |
| `minifasnet.onnx` | `minivision-ai/Silent-Face-Anti-Spoofing` `2.7_80x80_MiniFASNetV2.pth` 导出 | 1.7 MB | 输入 1×3×80×80,0–255 BGR |

导出脚本在 `tools/export/`:
`export_edgeface.py`、`export_minifasnet.py`、`inspect_models.py`、`validate_pipeline.py`。

原始权重放在 `_models_raw/`(不参与构建,可按需删除)。

## 实测数据(iQOO Z9X / V2353A / Android 14,2 线程双进程)

| 指标 | 数值 |
|---|---|
| 检测 | 35–55 ms |
| 对齐 | 6–8 ms |
| 识别(EdgeFace) | 13–21 ms |
| 活体(MiniFASNet) | 8–15 ms |
| IPC 往返 | 85–105 ms |
| 端到端平均 | 65–90 ms,P50 68 ms,P95 83 ms |
| 有效 FPS | 10–13 |
| 单进程端到端 | 约 65 ms(FPS 13.4) |

## 已解决的工程问题(报告可用)

1. **中文路径**:AGP 拒绝非 ASCII 工程路径,`aidl.exe` 也无法打开中文路径 → 工程放到 `E:\system\AndroidAI`。
2. **AIDL 中文注释**:Windows 下 AIDL 按 GBK 读取,UTF-8 中文注释报 `MalformedInputException` → AIDL 注释改英文。
3. **onnxruntime-android 堆内 FloatBuffer**:`OnnxTensor.createTensor(env, FloatBuffer.wrap(...))` 在设备上耗时 730 ms/帧,改为直接缓冲区后 1 ms。
4. **MiniFASNet 输入尺度**:官方 `src/data_io/functional.py` 的 `to_tensor` 去掉了 `/255`,模型要求 **0–255 BGR**;按 0–1 输入时恒判为攻击。
5. **OrtSession.Result 解包**:`result.first().value` 返回 `OnnxTensor`,需要再取 `.value` 才是数组;否则活体/特征恒为 null。
6. **NNAPI**:SCRFD 使用 `addNnapi()` 后单帧推理 17–29 ms;NNAPI 不可用时自动回退 CPU。
7. **日志限流**:高频 `Log.d` 会被 logd 丢弃,vivo 还会过滤 DEBUG 日志 → 关键日志改 `Log.i` 并限频到 1 条/秒。

## 已知限制

- 活体对强逆光/过曝、屏幕翻拍样本的判断不稳定,需按课程要求采集多轮样本统计。
- 识别阈值 0.45 仅在本人少量样本上验证,未做大规模标定。
- NNAPI 只是把 SCRFD 放到加速后端,EdgeFace/MiniFASNet 仍在 CPU。
- 单进程模式下没有独立推理进程,状态页的"杀死推理进程"不可用。

## 目录

```
app/src/main/java/com/example/osai/
  MainActivity.kt          摄像头、UI、绑定服务
  FaceOverlayView.kt       框/关键点/标签绘制
  FaceInferenceService.kt  :inference 服务(Binder 转发)
  InferenceCore.kt         线程池 + Semaphore + 流水线编排
  InferenceEngine.kt       三模型加载与前/后处理
  FaceStore.kt             本地特征库
  FaceApi.kt               单/双进程统一接口
  SystemStatusActivity.kt  系统状态页
tools/export/              模型导出与验证脚本
```
