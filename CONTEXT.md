# Heyboard 教学助手 - 项目上下文

## 项目概览

面向教育场景的 Android 教学辅助工具，运行在横屏 16:9 平板上（主要目标设备：H3C MegaBook）。

- **包名**: `com.heyboard.teachingassistant`
- **当前版本**: 1.0.9 (versionCode=9)
- **编译环境**: AGP 9.1.0, compileSdk 36, minSdk 24, targetSdk 36, Java 11
- **GitHub**: https://github.com/occurjan/Heyboard

---

## 功能模块

| 模块 | Activity | Service / Receiver | 说明 |
|------|----------|-------------------|------|
| 主页 | MainActivity | - | 四宫格功能入口 + 设置/HOME 按钮，启动 HeyboardToolService |
| 随机点名 | RandomCallActivity | - | 设置学号范围，随机抽人 |
| 课堂计时器 | TimerActivity | - | 倒计时，支持暂停/继续/重置 |
| 聚光灯 | SpotlightActivity | SpotlightService | 悬浮遮罩 + 可拖动透明区域，实时调节大小和透明度 |
| 实时字幕 | SubtitleActivity | SubtitleService | Vosk 离线语音识别，悬浮窗显示字幕 |
| 设置 | SettingsActivity | - | 版本信息、语言切换（中/英） |
| 自动化 | AutomationActivity / ScenarioDetailActivity / ActionDetailActivity | HeyboardToolService, BootReceiver, FinishClassReceiver | RS232 串口自动化控制，场景管理，开机/一键下课触发 |

---

## 代码结构

```
app/src/main/
├── java/com/heyboard/teachingassistant/
│   ├── HeyboardApp.kt                # Application 类，persistent 进程启动时自动启动 HeyboardToolService
│   ├── MainActivity.kt              # 主页，四宫格入口，启动 HeyboardToolService
│   ├── RandomCallActivity.kt        # 随机点名
│   ├── TimerActivity.kt             # 计时器
│   ├── SpotlightActivity.kt         # 聚光灯设置页（SeekBar 调节）
│   ├── SpotlightService.kt          # 聚光灯悬浮窗服务（双窗口架构）
│   ├── SpotlightOverlayView.kt      # 聚光灯遮罩自定义 View
│   ├── SubtitleActivity.kt          # 字幕设置页（语言选择）
│   ├── SubtitleService.kt           # 字幕悬浮窗服务（Vosk 语音识别）
│   ├── VoskModelManager.kt          # Vosk 模型管理（从 assets 解压）
│   ├── SettingsActivity.kt          # 设置页（版本、语言）
│   └── automation/
│       ├── AutomationModels.kt      # 数据模型（SerialConfig, ScenarioAction, AutomationScenario）
│       ├── AutomationRepository.kt  # SharedPreferences 持久化（MODE_MULTI_PROCESS 跨进程同步）
│       ├── AutomationExecutor.kt    # 触发执行器（on_start / on_close 场景执行）
│       ├── SerialCommandExecutor.kt # USB 串口通信（CH340 等），含同步权限请求
│       ├── HeyboardToolService.kt   # 前台服务（:tool 独立进程）：开机执行 on_start、动态注册接收一键下课执行 on_close、维护 H3C 白名单
│       ├── BootReceiver.kt          # 开机广播接收器 → 启动 HeyboardToolService
│       ├── FinishClassReceiver.kt   # H3C 一键下课广播接收器（静态注册，Android 14 不生效，仅保留兼容）
│       ├── AutomationActivity.kt    # 场景列表页
│       ├── ScenarioDetailActivity.kt # 场景详情页
│       └── ActionDetailActivity.kt  # 动作配置页（串口参数 + 测试）
├── res/
│   ├── layout/                      # 9 个 Activity 布局 + 1 个悬浮窗布局 + 2 个 RecyclerView item
│   ├── drawable/                    # 图标、背景、渐变等
│   ├── values/strings.xml           # 中文字符串
│   ├── values-en/strings.xml        # 英文字符串
│   └── values/colors.xml            # 主题色（蓝色系）
└── assets/
    ├── model-zh-cn/                 # Vosk 中文语音模型 (~65MB)
    └── model-en-us/                 # Vosk 英文语音模型 (~68MB)
```

---

## 关键架构决策

### 1. Vosk 离线语音识别（替代 Google SpeechRecognizer）
- **原因**: 目标设备（H3C MegaBook）无 Google 框架，SpeechRecognizer 不可用
- **方案**: 集成 `com.alphacephei:vosk-android:0.3.47`，模型打包在 assets 中
- **流程**: 首次使用时 VoskModelManager 从 assets 解压到 filesDir，然后加载 Model
- **代价**: APK 体积 170MB（含中英文模型）
- **精度**: 安静环境 85-92%，商业方案 95%+

### 2. 聚光灯双窗口架构
- **遮罩窗口**: FLAG_NOT_TOUCHABLE，全屏覆盖，绘制半透明黑色 + 透明圆形
- **控制窗口**: 可触摸，圆形区域可拖动
- 通过 static instance 模式实现 Activity ↔ Service 实时通信（调节大小/透明度）

### 3. H3C 设备兼容性
- **HOME Intent 无效**: H3C 定制系统拦截了 `ACTION_MAIN + CATEGORY_HOME` Intent
- **解决方案**: 使用 `finishAffinity()` 关闭所有 Activity，直接露出桌面
- **navigation_mode=2**: 手势导航模式，三指手势回桌面

### 4. Service ↔ Activity 通信
- **聚光灯**: static `instance` 引用 + `updateRadius()`/`updateOpacity()` 方法
- **字幕**: BroadcastReceiver 监听 `ACTION_STATE_CHANGED`（Service onDestroy 时发送）
- 按钮状态通过 static `isRunning` 标志同步

### 5. 自动化 / RS232 串口控制
- **串口库**: `com.github.mik3y:usb-serial-for-android:3.8.1`（JitPack）
- **USB 权限**: 系统平台签名 (sharedUserId=android.uid.system) 自动授权，无需弹窗
- **数据帧格式**: `AA BB CC [主命令] [子命令] [数据] [校验和] DD EE FF`（10 字节）
- **串口参数默认值**: 9600 baud, 8 data bits, 1 stop bit, No parity, No flow control

#### 自动化触发机制（完整业务流程）

**系统启动时 (on_start):**
```
设备开机
  → Android 发送 BOOT_COMPLETED 广播
  → BootReceiver (静态注册) 接收广播
  → 启动 HeyboardToolService (独立 :tool 进程)
  → HeyboardApp.onCreate() 也会启动 Service (persistent 进程自动拉起)
  → HeyboardToolService.onStartCommand() 收到 ACTION_BOOT_COMPLETED
  → AutomationExecutor.executeOnStart()
  → 从 SharedPreferences 读取 trigger="on_start" 的场景
  → SerialCommandExecutor 发送串口指令 (最多重试3次，间隔5秒)
```

**系统关闭时 (on_close):**
```
用户点击关机
  → Android 发送 ACTION_SHUTDOWN 广播
  → HeyboardToolService 中动态注册的 ShutdownReceiver 接收广播
  → 读取 sys.shutdown.requested 系统属性区分关机/重启
    → "0" = 关机 → 执行 on_close 场景动作
    → "1" = 重启 → 跳过，不执行
  → AutomationExecutor.executeOnClose()
  → 从 SharedPreferences 读取 trigger="on_close" 的场景
  → SerialCommandExecutor 发送串口指令
```

#### 关键保障机制

| 机制 | 作用 |
|------|------|
| `android:sharedUserId="android.uid.system"` + 平台签名 | 系统级权限，USB 免授权弹窗 |
| `android:persistent="true"` | 进程常驻，开机自动拉起 |
| `android:process=":tool"` | Service 独立进程，与 Activity 生命周期解耦 |
| H3C `service.json` 白名单 | 防止一键下课/一键清理杀掉服务，防止 stopped=true |
| 动态注册 ShutdownReceiver | 监听 ACTION_SHUTDOWN 广播，通过 `sys.shutdown.requested` 区分关机（"0"）和重启（"1"），仅关机时执行 on_close |
| `MODE_MULTI_PROCESS` | Activity (主进程) 和 Service (:tool 进程) 间 SharedPreferences 数据同步 |
| 开机时间戳守卫 | on_start 每次开机只执行一次（5秒容差） |
| `AtomicBoolean` | 防止 on_close 重复执行 |
| 串口重试机制 | on_start 最多重试3次，间隔5秒，应对 USB 驱动初始化延迟 |

#### H3C 系统白名单配置

白名单文件位于 `/data/h3c/h3cconfig/{最高版本号}/`，由 `HeyboardToolService.ensureH3CWhitelist()` 在服务启动时自动维护：

- **`service.json`**: 添加 `com.heyboard.teachingassistant/.automation.HeyboardToolService`，防止 `forceStopPackage` 杀掉服务
- **`startApp.json`**: 不需要添加包名（该文件用于 launcher 拉起 Activity 前台界面，我们通过 persistent + BOOT_COMPLETED 实现纯后台自启动）

#### 关机/重启区分机制
- 关机和重启都会触发 `ACTION_SHUTDOWN` 广播，广播本身不区分
- 通过反射读取 `sys.shutdown.requested` 系统属性来区分：
  - `"0"` = 用户关机 → 执行 on_close 场景动作
  - `"1"` = 用户重启 → 跳过
  - 包含 `"reboot"` = 其他重启变体 → 跳过

#### H3C 一键下课
- 从 H3C Launcher APK (`/system/priv-app/h3clauncher/h3clauncher.apk`) 反编译发现的 Action：
  - `com.h3c.action.FINISH_CLASS` — 下课触发（在 forceStopPackage 之前发送）
  - `com.h3c.action.FINISH_CLASS_DONE` — 下课完成（在 forceStopPackage 之后发送）
- 广播从 `com.h3c.system.control.BaseControlCompat.notifyFinishClassActionDone()` 发出
- 新版 h3clauncher 已将我们的包名加入白名单，forceStopPackage 会跳过
- 一键下课不再触发 on_close 场景动作（v1.0.9 起改为仅响应系统关机广播）

#### RS232 接线方案
- **MegaBook(USB) → CH340 直通线 → 串口分配器 → 交叉线 → 交互平板 RS232 口**
- MegaBook 到分配器：**直通线**（分配器输入端为 DCE，DTE↔DCE 引脚互补）
- 分配器到交互平板：**交叉线**（分配器为信号复制器，输出端等同 DTE，平板也是 DTE，需交叉 TX/RX）
- 被控设备：CVTE mtk9679 交互智能平板（H3C），RS232 协议规范见桌面文档

### 6. 系统平台签名

- **签名文件**: `sign/platform.keystore`（从 H3C 系统团队提供的 `platform.pk8` + `platform.x509.pem` 转换）
- **密码**: `platform123`，别名: `platform`
- **作用**: 使 APP 以 system UID (1000) 运行，获得系统级权限
- **效果**: USB 设备权限自动授权（无弹窗）、可读写 `/data/h3c/` 系统配置文件、persistent 标记生效

### 7. 主题与 UI
- **主色调**: macOS Finder 蓝（#007AFF / #1565C0 / #0B3D91）
- **渐变背景**: #0B3D91 → #1565C0
- **MaterialButton**: 必须用 `backgroundTintList` 而非 `setBackgroundColor()`
- **悬浮窗**: 不能用 `?attr/selectableItemBackgroundBorderless`（无 AppCompat 主题），用 `@android:color/transparent`

---

## 踩坑记录

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 字幕服务启动即崩溃 | floating_subtitle.xml 用了 `?attr/selectableItemBackgroundBorderless`，Service 无 AppCompat 主题 | 改为 `@android:color/transparent` |
| MaterialButton 颜色不变 | `setBackgroundColor()` 被 Material 主题拦截 | 用 `backgroundTintList = ColorStateList.valueOf(color)` |
| 字幕按钮状态不同步 | Activity 无法感知 Service 被悬浮窗关闭 | BroadcastReceiver + `ACTION_STATE_CHANGED` |
| HOME 按钮在 H3C 无效 | 定制系统拦截 HOME Intent | `finishAffinity()` |
| `moveTaskToBack()` 后重开不回主页 | 恢复到上次页面 | `finishAffinity()` 关闭所有 Activity |
| 悬浮窗拖动时灵时不灵 | 拖动只绑定在 TextView 上 | 改为整个 floatingView 监听触摸 |
| Debug/Release APK 安装冲突 | 签名不一致 | 统一用 release 签名测试 |
| Android 14+ 隐式广播不可达 | 静态注册的 BroadcastReceiver 收不到隐式广播 | 改为在 Service 中动态注册 receiver |
| 重启时误触发 on_close | 关机和重启都发送 ACTION_SHUTDOWN 广播 | 读取 `sys.shutdown.requested` 属性区分：`"0"`=关机，`"1"`=重启 |
| USB 权限 PendingIntent 崩溃 | Android 14 禁止隐式 Intent + FLAG_MUTABLE | 改为显式 Intent（setPackage）+ FLAG_MUTABLE |
| Toast 在 H3C 设备不显示 | H3C 系统屏蔽 Toast 通知 | 改用 AlertDialog 显示结果 |
| 串口指令发出但设备无反应 | USB 转 DB9 直通线 TX/RX 未交叉 | DTE↔DTE 需使用交叉线（Null Modem） |
| USB 权限弹窗每次开机都出现 | 普通签名无法持久化 USB 权限 | 使用系统平台签名，USB 权限自动授权 |
| 开机后 UsbSerialProber 返回 0 个驱动 | 系统 UID 下 USB 驱动初始化有延迟 | 重试机制（3次，间隔5秒） |
| 一键下课杀掉 Service | forceStopPackage 杀掉整个包的所有进程 | H3C 白名单 (service.json) + 独立 :tool 进程 |
| 一键下课后 stopped=true 导致开机无法自启动 | forceStopPackage 设置 FLAG_STOPPED | H3C 白名单豁免 forceStopPackage |
| 开机时前台界面被自动打开 | USB_DEVICE_ATTACHED intent-filter 自动拉起 MainActivity | 移除该 intent-filter |
| Activity 修改配置后 Service 读不到新数据 | SharedPreferences 跨进程不同步 | 改为 MODE_MULTI_PROCESS |

---

## 版本规则

- 每次打包版本号 +0.0.1（十进制，如 1.0.9 → 1.0.10）
- versionCode 同步递增
- APK 文件名格式: `Heyboard_v{版本号}_{YYYYMMDD}.apk`
- 版本号体现在 build.gradle.kts 和设置页面

---

## 签名配置

- **Keystore**: `sign/platform.keystore`（系统平台签名）
- **Alias**: `platform`
- **密码**: `platform123`
- **来源**: H3C 系统团队提供的 `platform.pk8` + `platform.x509.pem` 转换而来

---

## 开发环境

- macOS, Android Studio
- 主控设备: H3C MegaBook (Android 14, SDK 34, 3840x2160, 无 Google 框架, IP: 10.214.75.17)
- 被控设备: H3C/CVTE mtk9679 交互智能平板 (IP: 10.214.75.35)
- 系统版本: `OPS2HEYBOARDD012P01` (H3C/caas/caas:14/UQ1A.231205.015)
- adb 无线连接: `adb connect <ip>:5555`
- 屏幕投射: `ADB=/Users/Admin/Library/Android/sdk/platform-tools/adb scrcpy`
- adb 路径: `/Users/Admin/Library/Android/sdk/platform-tools/adb`

### 硬件连接拓扑
```
MegaBook(USB) → CH340直通线 → 串口分配器(1入4出) → 交叉线×4 → 4台交互平板RS232口
```

---

## 版本历史

| 版本 | 日期 | 主要变更 |
|------|------|----------|
| 1.0.1 | 2026-03-16 | UI 重构，多语言支持 |
| 1.0.3 | 2026-03-16 | Vosk 离线语音识别，H3C 兼容性修复 |
| 1.0.4 | 2026-03-17 | 完成 RandomCall/Timer i18n |
| 1.0.5 | 2026-03-17 | 新增自动化功能（RS232 串口控制） |
| 1.0.6 | 2026-03-17 | 修复 USB 权限崩溃、Toast 不显示；自动化图标改为扳手 |
| 1.0.7 | 2026-03-18 | 自动化触发重构：HeyboardToolService 前台服务 + 开机自启动 + H3C 一键下课广播触发 |
| 1.0.8 | 2026-03-19 | 系统平台签名（USB 免授权）、persistent 进程常驻、:tool 独立进程、H3C 白名单集成、动态注册 FinishClassReceiver、跨进程 SharedPreferences 同步 |
| 1.0.9 | 2026-03-20 | "系统关闭时"触发改为监听 ACTION_SHUTDOWN 广播，通过 sys.shutdown.requested 区分关机/重启，仅关机时执行 on_close；移除 FinishClassReceiver |

---

## 待办 / 改进方向

- [ ] 语音识别精度提升（考虑科大讯飞 SDK 作为可选方案）
- [ ] 字幕历史记录 / 导出功能
- [ ] 聚光灯形状可选（圆形 / 矩形）
- [ ] 随机点名支持导入学生名单
- [ ] 计时器到时音效提醒
- [ ] ProGuard 混淆开启（当前 isMinifyEnabled = false）
- [ ] APK 瘦身（按 ABI 分包，减少模型体积）
- [ ] 适配更多无 Google 框架的国产设备
- [ ] 自动化：预设常用串口命令（开关机、切换输入源等）
- [ ] 自动化：串口指令执行延时设置（多条指令间隔）
