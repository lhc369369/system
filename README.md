# AndroidAI 人脸识别系统(OSAI)

基于 Android 的端侧实时人脸识别系统:前置摄像头采集画面,在设备本地完成 **SCRFD 人脸检测 → 五点对齐 → EdgeFace 特征识别 → MiniFASNet 活体检测** 的完整流水线,支持人脸注册、人脸库管理、身份匹配与单/双进程推理架构。

- 应用名称:人脸识别系统
- 包名:`com.example.osai`
- 界面语言:中文(深色主题)

> **仓库性质说明**:本仓库是课程设计的**分工提交材料仓库**,收录三个分工模块的关键代码、AIDL 接口、`AndroidManifest.xml`、界面资源、ONNX 模型、运行截图与设计说明,**不是可直接 `./gradlew build` 的完整 Android 工程**:未包含 Gradle 构建配置,推理引擎 `InferenceEngine.kt` 等以代码摘录(`关键代码_*.md`)形式收录。将本仓库材料合入完整工程后即可构建运行,步骤见「安装与快速启动」。

## 功能特性

| 模块 | 说明 |
| --- | --- |
| 实时人脸检测 | SCRFD-500M,320×320 letterbox 预处理,置信度筛选 + NMS,预览上叠加检测框与 5 个关键点,自动修正前摄镜像 |
| 人脸识别与注册 | 五点相似变换对齐到 ArcFace 112×112 模板,EdgeFace-xs 输出 512 维特征并 L2 归一化,余弦相似度匹配,阈值 0.45,支持注册 / 同名覆盖 / 列表 / 删除 |
| 活体检测 | MiniFASNetV2 三分类输出真人 / 攻击概率,最近 5 帧投票(满 3 票才出结论)抑制单帧抖动,界面按「真人(绿)/ 疑似攻击(红)/ 判定中(黄)」着色提示 |
| 单 / 双进程架构 | 默认双进程:UI 进程经 AIDL/Binder 将 JPEG 帧发给独立 `:inference` 进程,支持死亡监听与 1 秒自动重连;可切换为 UI 进程内本地推理 |
| 线程与并发 | 推理线程池 1/2/4 可在线切换(线程名 `infer-1..N`),Semaphore 限流(许可数 = 线程数,80 ms 拿不到许可即计超时返回),5 s 超时自动取消并计数 |
| 性能监控 | 实时显示 FPS、人脸数、检测 / 识别 / 活体 / 总耗时、IPC 耗时、服务 pid;系统状态页展示 PID、线程名、许可数、提交 / 完成 / 超时计数与 P50 / P95 |

## 技术栈

- **语言 / UI**:Kotlin、AndroidX AppCompat、View 体系(自定义 `FaceOverlayView` 绘制)
- **相机**:CameraX(`Preview` + `ImageAnalysis`,`KEEP_ONLY_LATEST` 背压)
- **推理**:ONNX Runtime Android,SCRFD 优先 NNAPI 加速、失败自动回退 CPU
- **跨进程**:AIDL / Binder,`@Parcelize`(kotlinx-parcelize)传输帧与结果
- **持久化**:`org.json` 读写应用私有目录 `faces.json`

## 项目结构

```
.
├─ README.md
├─ 01_检测与界面_CameraX_SCRFD/01_检测与界面_CameraX_SCRFD/   # 分工一:采集、检测与界面
│  ├─ code/                    MainActivity.kt、FaceOverlayView.kt、FaceBox.kt、FaceFrame.kt
│  ├─ res/                     activity_main.xml、drawable/、values/(颜色、字符串、主题)
│  ├─ model/scrfd.onnx         SCRFD-500M 检测模型(输入动态,实际使用 320×320)
│  ├─ evidence/                01_detection_liveness.png(检测与活体运行截图)
│  ├─ 关键代码_检测.md          SCRFD 前后处理、NMS、图像转换代码摘录
│  └─ 分工说明.md               负责范围、实现要点与答辩问题
├─ AndroidAI_分工提交/02_识别与注册_EdgeFace_人脸库/           # 分工二:对齐、识别与人脸库
   ├─ code/                    FaceStore.kt(特征库)、FrameResult.kt(结果结构)
   ├─ model/edgeface.onnx      EdgeFace-xs(输入 1×3×112×112,输出 1×512)
   ├─ evidence/                03_dual_process_identify.png、06_registered.png
   ├─ 关键代码_识别.md          相似变换对齐、双线性采样、特征提取代码摘录
   └─ 分工说明.md               负责范围、实现要点与答辩问题
└─ 03_活体与系统机制_MiniFASNet_双进程(1)/03_活体与系统机制_MiniFASNet_双进程/   # 分工三:活体与系统机制
   ├─ code/                    FaceInferenceService.kt、InferenceCore.kt、FaceApi.kt、SystemStatusActivity.kt
   ├─ aidl/                    IFaceInference.aidl、FaceFrame.aidl、FaceBox.aidl、FrameResult.aidl
   ├─ res/activity_status.xml  系统状态页布局
   ├─ AndroidManifest.xml      权限、Activity 与 `:inference` 服务声明
   ├─ model/minifasnet.onnx    MiniFASNetV2(输入 1×3×80×80,输出 1×3)
   ├─ evidence/                02_status_page.png、04_single_process.png、05_threads4.png
   ├─ 关键代码_活体.md          MiniFASNet 预处理与调试裁剪代码摘录
   └─ 分工说明.md               负责范围、实现要点与答辩问题
```

## 环境要求

- Android Studio 最新稳定版,配套 JDK(推荐 17)与 Android SDK
- Android 真机(推荐):前置摄像头、ARM64;参考测试机型为骁龙 6 Gen 1 同级设备
- NNAPI 加速需设备支持 Android 8.0(API 26)及以上;不支持时自动回退 CPU,不影响功能
- 模拟器可运行但无真实摄像头体验,且推理性能不代表真机

## 安装与快速启动

### 方式一:合入完整工程运行

1. 用 Android Studio 打开完整工程(包名 `com.example.osai`),或新建工程后补齐被引用的推理服务、AIDL 与状态页代码。
2. 在 app 模块添加依赖:CameraX(core / camera2 / lifecycle / view)、ONNX Runtime Android、AppCompat,并启用 `kotlinx-parcelize`。
3. 放置模型:将 `scrfd.onnx`、`edgeface.onnx`、`minifasnet.onnx` 复制到 `app/src/main/assets/`(代码通过 `context.assets.open(...)` 加载)。
4. 合并源码与资源:
   - `code/*.kt` → `app/src/main/java/com/example/osai/`
   - `res/` 内容 → `app/src/main/res/` 对应目录
   - `aidl/*.aidl` → `app/src/main/aidl/com/example/osai/`(并在 build.gradle 中启用 AIDL 支持)
5. 配置 `AndroidManifest.xml`:可直接参考分工三目录中的清单文件,包含 `CAMERA` 权限、前置摄像头 `uses-feature`、`MainActivity` / `SystemStatusActivity` 声明,以及 `FaceInferenceService`(`android:process=":inference"`,双进程模式必需)。
6. 连接真机并运行,首次启动授予相机权限,状态栏显示「已连接推理服务 :inference」即就绪。

### 方式二:仅阅读本仓库材料

直接按目录阅读 `分工说明.md`(设计要点)与 `关键代码_*.md`(算法实现),配合 `evidence/` 截图理解运行效果。

## 使用指南

1. **预览与检测**:正对前置摄像头,画面出现绿色 / 黄色人脸框与 5 个关键点;顶部状态栏显示 FPS、人脸数与单帧总耗时。
2. **注册人脸**:保证画面中只有一张清晰正脸,点击「注册当前人脸」,输入姓名后确认;同名注册会覆盖旧模板。
3. **人脸库**:点击「人脸库」查看已注册姓名,点选条目可删除。
4. **身份识别**:默认开启。已注册人脸显示「姓名 相似度」,未注册但低于阈值显示「未知」,无模板时显示「未注册」。
5. **活体检测**:默认开启。人脸框上方标签显示「真人 x.xx」或「疑似攻击 x.xx」,框体颜色同步变化。
6. **系统状态**:点击「系统状态」进入状态页,查看推理服务运行信息。

多张人脸时全部画框,但识别与活体只处理**检测框面积最大**的一张。

## 配置项说明

| 配置项 | 位置 | 默认值 / 说明 |
| --- | --- | --- |
| `single_process` | SharedPreferences `osai` | `false`,即双进程;置 `true` 改为 UI 进程内推理,切换后 Activity 自动重建 |
| `SCRFD_INPUT` | InferenceEngine | `320`,letterbox 输入边长(兼顾精度与耗时) |
| 检测归一化 | InferenceEngine | `(pixel - 127.5) / 128`,RGB 通道顺序 |
| `CONF_THRESHOLD` | InferenceEngine | 检测置信度阈值,低于该值的候选框直接丢弃 |
| `NMS_THRESHOLD` | InferenceEngine | `0.4`,按分数降序贪心抑制 IoU > 0.4 的框 |
| `STRIDES` / `NUM_ANCHORS` | InferenceEngine | `8 / 16 / 32`,每个特征图位置 2 个 anchor;9 个输出按最后一维分组:1=分数、4=框、10=关键点 |
| ORT 会话参数 | InferenceEngine | `intraOpNumThreads=4`、`interOpNumThreads=1`、`ALL_OPT`;SCRFD 优先 NNAPI,失败回退 CPU |
| `EDGEFACE_SIZE` | InferenceEngine | `112`,对齐输出尺寸,与 ArcFace 模板一致 |
| 识别归一化 | InferenceEngine | `pixel / 127.5 - 1`(映射到 [-1, 1]),输出 512 维并 L2 归一化 |
| 识别阈值 | InferenceCore | `0.45`,余弦相似度低于该值判为未知;**小样本经验值,未做 ROC 标定** |
| 活体预处理 | InferenceEngine | 以检测框中心放大 2.7 倍并平移限幅(官方 `_get_new_box`),缩放到 80×80,**输入 0–255 BGR,不做 /255** |
| 活体判定 | InferenceCore | 最近 5 帧投票,真人 / 攻击任一方满 3 票才给出结论,否则保持「判定中」 |
| 推理线程池 | InferenceCore | `Executors.newFixedThreadPool(N)`,N = 1 / 2 / 4,状态页可切;线程名 `infer-1..N` |
| Semaphore 限流 | InferenceCore | 许可数 = 线程数,`tryAcquire(80ms)` 拿不到许可即计 `semTimeout` 并返回,避免 Binder 线程堆积;仅在推理进程内生效 |
| 推理超时 | InferenceCore | `future.get(5000ms)` 超时 `cancel(true)` 并计数,`finally` 中必定 `release()` |
| `JPEG_QUALITY` | MainActivity | `90`,帧压缩质量(单帧约 30–60 KB,规避 Binder 1 MB 上限) |
| 人脸库存储 | FaceStore | 应用私有目录 `filesDir/faces.json`,卸载即清除 |

## 性能设计要点

- **输入尺寸取 320 而非 640**:骁龙 6 Gen 1 上 640 输入单帧超过 100 ms,320 letterbox 后检测约 35–55 ms,自拍场景人脸占比足够大。
- **直接缓冲区构造张量**:堆内 `FloatBuffer` 会退化为逐元素拷贝(实测约 730 ms/帧),改用 `ByteBuffer.allocateDirect`。
- **帧率控制**:CameraX `KEEP_ONLY_LATEST` + 客户端 `frameBusy` 原子标志,推理未完成时直接丢帧并计数,避免排队积压。
- **进程隔离**:推理放在独立 `:inference` 进程,崩溃不影响 UI,断线 1 秒自动重连;状态页打开期间保持绑定。代价是每帧多一次序列化与往返,实测端到端比单进程多约 15–25 ms。
- **状态页演示**:提供「杀死推理进程」按钮演示崩溃恢复,可在线切换 1 / 2 / 4 推理线程观察吞吐与 P50 / P95 变化。

## 常见问题(FAQ)

**Q:克隆本仓库后能否直接用 Android Studio 构建?**
不能。本仓库为分工材料,缺少 Gradle 构建配置,推理引擎等实现以代码摘录形式提供,请按「安装与快速启动」合入完整工程。

**Q:检测框左右反了?**
前置摄像头预览是镜像的,叠加层绘制时需将 x 坐标翻转为 `imageWidth - x`(`FaceOverlayView.mirrored` 已处理)。

**Q:一直提示「未检测到人脸」?**
确认已授予相机权限、正对镜头且光线充足;状态栏会显示「未检测到人脸 · 请正对镜头」。

**Q:识别结果显示「未知」?**
表示与库中模板的最高相似度低于 0.45。先注册本人人脸,注册时保持稳定正脸;阈值来自小样本实测,严格场景应重新标定。

**Q:注册失败,提示「当前没有可用的人脸特征」?**
注册保存的是推理服务保留的最近一帧特征,需要画面中已有稳定、清晰的单张正脸后再点击注册。

**Q:活体判定结果会跳动吗?**
不会频繁跳动。活体采用最近 5 帧投票,任一方满 3 票才更新结论,其余时间显示「判定中」。

**Q:提示「推理进程意外退出」?**
双进程模式下服务崩溃会自动在 1 秒后重连;若反复出现,可在 SharedPreferences 中将 `single_process` 置为 `true`,切到单进程模式排查。

**Q:NNAPI 不可用会报错吗?**
不会。日志提示「NNAPI 不可用,回退 CPU」后自动使用 CPU 推理,仅耗时增加。

**Q:卸载 App 后人脸库还在吗?**
不在。`faces.json` 位于应用私有目录,卸载即删除;同名注册会覆盖旧模板。

**Q:为什么帧数据传 JPEG 而不是原始 RGBA?**
640×480 RGBA 约 1.2 MB,超过 Binder 单次 1 MB 事务限制;JPEG(质量 90)约 30–60 KB。

**Q:为什么注册前不先强制通过活体?**
当前识别与活体并行输出,注册时活体仅作提示;严格场景应先通过活体判定再落库。

## 分工与文档索引

| 分工 | 内容 | 文档 |
| --- | --- | --- |
| 成员 A | 摄像头采集、SCRFD 检测、界面与结果展示 | `01_检测与界面_CameraX_SCRFD/.../分工说明.md`、`关键代码_检测.md` |
| 成员 B | 五点对齐、EdgeFace 特征、注册与人脸库 | `AndroidAI_分工提交/02_识别与注册_EdgeFace_人脸库/分工说明.md`、`关键代码_识别.md` |
| 成员 C | MiniFASNet 活体、双进程 IPC、线程与信号量、系统状态页 | `03_活体与系统机制_MiniFASNet_双进程(1)/.../分工说明.md`、`关键代码_活体.md` |
