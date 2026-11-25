# AndroidAI 人脸识别系统(OSAI)

基于 Android 的端侧实时人脸识别系统:前置摄像头采集画面,在设备本地完成 **SCRFD 人脸检测 → 五点对齐 → EdgeFace 特征识别 → MiniFASNet 活体检测** 的完整流水线,支持人脸注册、人脸库管理、身份匹配与单/双进程推理架构。

- 应用名称:人脸识别系统
- 包名:`com.example.osai`
- 完整工程:[`AndroidAI/AndroidAI/`](AndroidAI/AndroidAI/)(可直接用 Android Studio 打开构建)

## 仓库布局

```
.
├─ AndroidAI/AndroidAI/                 # ★ 完整可构建 Android 工程
│  ├─ app/                              # 应用模块(源码、AIDL、资源、assets/models 模型)
│  ├─ tools/export/                     # 模型导出与验证 Python 脚本
│  ├─ docs/                             # 实验记录与运行截图
│  ├─ _models_raw/                      # 原始权重与第三方仓库(不参与构建,可删除)
│  ├─ build.gradle.kts / settings.gradle.kts / gradlew
│  └─ README.md                         # 工程级详细说明(环境、构建、实测数据)
├─ 01_检测与界面_CameraX_SCRFD/          # 分工材料一:采集、检测与界面(设计文档 + 代码摘录)
├─ AndroidAI_分工提交/02_识别与注册_EdgeFace_人脸库/   # 分工材料二:对齐、识别与人脸库
├─ 03_活体与系统机制_MiniFASNet_双进程(1)/ # 分工材料三:活体、IPC、线程与信号量
└─ .gitattributes                       # Git LFS 追踪规则(*.zip / *.onnx / *.pt / *.apk)
```

## 克隆注意事项(重要)

1. **必须安装 Git LFS**:模型、APK、原始权重等大文件使用 LFS 存储,普通 `git clone` 只会得到指针文件。请安装 [git-lfs](https://git-lfs.github.com/) 后克隆,或克隆后执行:
   ```powershell
   git lfs install
   git lfs pull
   ```
2. **使用纯英文路径**:AGP 与 `aidl.exe` 不支持非 ASCII 路径,仓库克隆到含中文的目录会导致构建失败。
3. 本仓库包含完整工程的全部文件(含 `build/` 产物与 `_models_raw/` 原始权重),体积较大;如只需源码,可删除 `AndroidAI/AndroidAI/_models_raw/` 与各级 `build/` 目录,不影响构建。

## 环境要求

| 项 | 要求 |
| --- | --- |
| JDK | 17(工程 `gradle.properties` 中 `org.gradle.java.home` 指向本机 JDK,路径不同需修改或改用 `JAVA_HOME`) |
| Android SDK | platform-android-35、build-tools 35.0.0、platform-tools |
| Gradle | 8.7(Wrapper 自动下载,走腾讯镜像;Maven 依赖走阿里云镜像) |
| Android Studio | 最新稳定版(直接打开工程可自动处理上述配置) |
| 设备 | Android 8.0(minSdk 26)及以上真机,前置摄像头;参考测试机 iQOO Z9X(Android 14) |

主要依赖:CameraX 1.3.4、ONNX Runtime Android 1.19.2、AppCompat 1.7.0、Kotlin(JVM Target 17)。

## 快速启动

### 方式一:Android Studio(推荐)

1. 用 Android Studio 打开 `AndroidAI/AndroidAI/` 目录,等待 Gradle Sync 完成。
2. 连接真机(开启 USB 调试),点击 Run。
3. 首次启动授予相机权限,状态栏显示「已连接推理服务 :inference」即就绪。

### 方式二:命令行

```powershell
cd AndroidAI/AndroidAI
.\gradlew.bat assembleDebug          # 若 JDK 路径不符,先设置 $env:JAVA_HOME
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.example.osai/.MainActivity
```

三个 ONNX 模型已内置于 `app/src/main/assets/models/`(LFS 文件,确认已执行 `git lfs pull`),APK 约 85 MB。

## 功能特性

| 模块 | 说明 |
| --- | --- |
| 实时人脸检测 | SCRFD-500M,320×320 letterbox,置信度筛选 + NMS,检测框与 5 关键点叠加,自动修正前摄镜像;NNAPI 加速,失败回退 CPU |
| 人脸识别与注册 | 五点相似变换对齐到 ArcFace 112×112 模板,EdgeFace-xs(gamma=0.6)输出 512 维特征并 L2 归一化,余弦相似度匹配,阈值 0.45;注册 / 同名覆盖 / 列表 / 删除 |
| 活体检测 | MiniFASNetV2(80×80,0–255 BGR)三分类,最近 5 帧投票(满 3 票出结论),界面按真人(绿)/ 疑似攻击(红)/ 判定中(黄)着色 |
| 单 / 双进程架构 | 默认双进程:UI 进程经 AIDL/Binder 将 JPEG 帧发给 `:inference` 进程,死亡监听 + 1 秒自动重连;状态页可切单进程基线 |
| 线程与并发 | 推理线程池 1/2/4 在线切换(线程名 `infer-N`),Semaphore 限流(许可数 = 线程数,80 ms 获取失败即计超时),5 s 超时取消 |
| 系统状态页 | PID、线程名、信号量许可、提交 / 完成 / 超时计数、耗时 P50 / P95、注册人数;可一键杀死推理进程演示故障恢复 |

多张人脸时全部画框,识别与活体只处理**检测框面积最大**的一张。

## 架构

```
CameraX ImageAnalysis (camera-analyzer 线程)
        │  ImageProxy → Bitmap → 旋转 → JPEG(质量 90,约 30–60 KB)
        ▼
 Binder/AIDL IFaceInference.analyzeFrame(FaceFrame)
        ▼
 :inference 进程  InferenceCore
        │  Semaphore(N) → ExecutorService(N, infer-N)
        ▼
   InferenceEngine
     SCRFD(320, NNAPI) → 对齐(112) → EdgeFace → MiniFASNet(80)
        ▼
 FrameResult(Binder 回传) → UI 绘制框 / 关键点 / 标签
```

## 配置项说明

| 配置项 | 位置 | 默认值 / 说明 |
| --- | --- | --- |
| `single_process` | SharedPreferences `osai` | `false`(双进程);状态页可切换,切换后 Activity 自动重建 |
| `SCRFD_INPUT` | InferenceEngine | `320`,letterbox 输入边长 |
| 检测归一化 | InferenceEngine | `(pixel - 127.5) / 128`,RGB |
| `NMS_THRESHOLD` | InferenceEngine | `0.4`,按分数降序贪心抑制 |
| `STRIDES` / `NUM_ANCHORS` | InferenceEngine | `8 / 16 / 32`,每位置 2 anchor;9 个输出按最后一维分组(1=分数、4=框、10=关键点) |
| ORT 会话参数 | InferenceEngine | `intraOp=4`、`interOp=1`、`ALL_OPT`;SCRFD 优先 NNAPI,回退 CPU |
| `EDGEFACE_SIZE` | InferenceEngine | `112`,对齐输出,与 ArcFace 模板一致 |
| 识别归一化 | InferenceEngine | `pixel / 127.5 - 1`,输出 512 维并 L2 归一化 |
| 识别阈值 | InferenceCore | `0.45`,低于该值判为未知;**小样本经验值,未做 ROC 标定** |
| 活体预处理 | InferenceEngine | 框中心放大 2.7 倍(官方 `_get_new_box`),缩放 80×80,**0–255 BGR,不做 /255** |
| 活体判定 | InferenceCore | 最近 5 帧投票,满 3 票出结论 |
| 推理线程池 / 信号量 | InferenceCore | N = 1 / 2 / 4;`tryAcquire(80ms)`、`future.get(5000ms)` 超时取消并计数 |
| `JPEG_QUALITY` | MainActivity | `90`,规避 Binder 1 MB 事务上限 |
| 人脸库存储 | FaceStore | 应用私有目录 `filesDir/faces.json`,卸载即清除 |

## 实测性能(iQOO Z9X / Android 14,2 线程双进程)

| 指标 | 数值 |
| --- | --- |
| 检测(SCRFD) | 35–55 ms(NNAPI 时 17–29 ms) |
| 对齐 | 6–8 ms |
| 识别(EdgeFace) | 13–21 ms |
| 活体(MiniFASNet) | 8–15 ms |
| 端到端 | 平均 65–90 ms,P50 68 ms,P95 83 ms |
| 有效 FPS | 10–13(单进程约 65 ms,FPS 13.4) |

## 模型来源

| 文件 | 来源 | 大小 |
| --- | --- | --- |
| `scrfd.onnx` | InsightFace buffalo_s(HF `immich-app/buffalo_s`) | 2.5 MB |
| `edgeface.onnx` | `otroshi/edgeface` `edgeface_xs_gamma_06.pt` 导出 | 7.0 MB |
| `minifasnet.onnx` | `minivision-ai/Silent-Face-Anti-Spoofing` `2.7_80x80_MiniFASNetV2.pth` 导出 | 1.7 MB |

导出与验证脚本见 `AndroidAI/AndroidAI/tools/export/`。

## 常见问题(FAQ)

**Q:克隆后运行报模型加载失败 / 文件只有几 KB?**
LFS 文件未拉取。执行 `git lfs install && git lfs pull` 后重新构建。

**Q:构建报 AGP / aidl.exe 路径错误?**
工程路径含中文或非 ASCII 字符。把仓库移到纯英文路径再构建。

**Q:Windows 下 AIDL 编译报 `MalformedInputException`?**
AIDL 文件按 GBK 读取,中文 UTF-8 注释会报错,本工程 AIDL 注释一律使用英文,新增注释请遵守。

**Q:识别结果显示「未知」?**
与库中模板最高相似度低于 0.45。先注册本人人脸并保持正脸;阈值来自小样本实测,严格场景应重新标定。

**Q:注册失败「当前没有可用的人脸特征」?**
注册保存推理服务保留的最近一帧特征,需画面中已有稳定清晰的单张正脸再点击注册。

**Q:提示「推理进程意外退出」?**
双进程模式会自动在 1 秒后重连;反复出现可切单进程模式排查(此时状态页「杀死推理进程」不可用)。

**Q:为什么帧数据传 JPEG 而不是原始 RGBA?**
640×480 RGBA 约 1.2 MB,超过 Binder 单次 1 MB 限制;JPEG(质量 90)约 30–60 KB。

**Q:卸载 App 后人脸库还在吗?**
不在。`faces.json` 位于应用私有目录,卸载即删除;同名注册会覆盖旧模板。

**Q:`_models_raw/` 和 `build/` 可以删除吗?**
可以。原始权重仅用于复现模型导出,`build/` 为构建产物,均不影响工程构建与运行。

## 已知限制

- 活体对强逆光 / 过曝、屏幕翻拍样本判断不稳定,需按课程要求采集多轮样本统计。
- 识别阈值 0.45 仅在少量样本上验证,未做大规模标定。
- NNAPI 仅加速 SCRFD,EdgeFace / MiniFASNet 仍在 CPU。
- 单进程模式下状态页「杀死推理进程」不可用。

## 分工材料索引

各分工目录包含设计说明(`分工说明.md`)、算法代码摘录(`关键代码_*.md`)、模型与运行截图,完整实现以 `AndroidAI/AndroidAI/` 工程为准。

| 分工 | 内容 | 目录 |
| --- | --- | --- |
| 成员 A | 摄像头采集、SCRFD 检测、界面与结果展示 | `01_检测与界面_CameraX_SCRFD/` |
| 成员 B | 五点对齐、EdgeFace 特征、注册与人脸库 | `AndroidAI_分工提交/02_识别与注册_EdgeFace_人脸库/` |
| 成员 C | MiniFASNet 活体、双进程 IPC、线程与信号量、系统状态页 | `03_活体与系统机制_MiniFASNet_双进程(1)/` |
