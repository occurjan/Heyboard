# Heyboard 教学助手 - 项目上下文

## 项目概览

面向教育场景的 Android 教学辅助工具，运行在横屏 16:9 平板上（主要目标设备：H3C MegaBook）。

- **包名**: `com.heyboard.teachingassistant`
- **当前版本**: 1.0.6 (versionCode=6)
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
│       ├── AutomationRepository.kt  # SharedPreferences 持久化
│       ├── AutomationExecutor.kt    # 触发执行器（on_start / on_close 场景执行）
│       ├── SerialCommandExecutor.kt # USB 串口通信（CH340 等）
│       ├── HeyboardToolService.kt   # 前台服务：开机执行 on_start、接收一键下课执行 on_close
│       ├── BootReceiver.kt          # 开机广播接收器 → 启动 HeyboardToolService
│       ├── FinishClassReceiver.kt   # H3C 一键下课广播接收器 → 启动 HeyboardToolService
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
- **USB 权限**: 通过 `usb_device_filter.xml` 声明 CH340/CP2102/FTDI/PL2303，实现自动授权
- **数据帧格式**: `AA BB CC [主命令] [子命令] [数据] [校验和] DD EE FF`（10 字节）
- **串口参数默认值**: 9600 baud, 8 data bits, 1 stop bit, No parity, No flow control

#### 自动化触发机制（HeyboardToolService 架构）

```
┌─────────────────────────────────────────────────────────┐
│                   HeyboardToolService                    │
│              (前台服务, START_STICKY 保活)                 │
│                                                          │
│  ACTION_BOOT_COMPLETED → executeOnStart (仅开机后1次)     │
│  ACTION_FINISH_CLASS   → executeOnClose (每次一键下课)     │
└────────────────────────┬────────────────┬────────────────┘
                         │                │
              ┌──────────┘                └──────────┐
              │                                      │
    ┌─────────┴──────────┐              ┌────────────┴───────────┐
    │    BootReceiver     │              │  FinishClassReceiver   │
    │  (Manifest 静态注册) │              │  (Manifest 静态注册)    │
    │                     │              │                        │
    │ BOOT_COMPLETED      │              │ com.h3c.action.        │
    │ → startForeground   │              │ FINISH_CLASS_DONE      │
    │   Service           │              │ → startForeground      │
    └─────────────────────┘              │   Service              │
                                         └────────────────────────┘
```

| 触发条件 | 实际触发方式 | 接收组件 | 保障机制 |
|---------|------------|---------|---------|
| 系统开启时 (`on_start`) | 系统开机完成广播 `BOOT_COMPLETED` | BootReceiver → HeyboardToolService | boot-time guard 防止重复执行（同一次开机仅执行1次） |
| 系统关闭时 (`on_close`) | H3C 一键下课广播 `com.h3c.action.FINISH_CLASS_DONE` | FinishClassReceiver → HeyboardToolService | Manifest 静态注册，APP 关闭也能接收 |

- **boot-time guard**: `AutomationExecutor.executeOnStart()` 通过 `SystemClock.elapsedRealtime()` 计算开机时间戳，与上次记录比较（5秒容差），确保每次开机仅执行一次
- **H3C 一键下课广播**: 从 H3C Launcher APK (`/system/priv-app/h3clauncher/h3clauncher.apk`) 反编译发现的 Action 字符串：
  - `com.h3c.action.FINISH_CLASS` — 下课触发
  - `com.h3c.action.FINISH_CLASS_DONE` — 下课完成（当前使用此 Action）
  - 广播从 `com.h3c.system.control.BaseControlCompat.notifyFinishClassActionDone()` 发出，运行在 system_server 进程
- **MainActivity**: 仅负责启动 HeyboardToolService（不传 action，不触发任何自动化），确保用户手动打开 APP 时服务就绪

#### RS232 接线方案
- **MegaBook(USB) → CH340 直通线 → 串口分配器 → 交叉线 → 交互平板 RS232 口**
- MegaBook 到分配器：**直通线**（分配器输入端为 DCE，DTE↔DCE 引脚互补）
- 分配器到交互平板：**交叉线**（分配器为信号复制器，输出端等同 DTE，平板也是 DTE，需交叉 TX/RX）
- 原理：分配器将输入信号原样复制到输出端相同引脚，不做 TX/RX 转换，所以输出端行为等同 DTE
- 两台 DTE 设备直连必须用交叉线（Null Modem），TX/RX 互换
- CH340 USB 转 DB9 线出厂默认为直通线
- 被控设备：CVTE mtk9679 交互智能平板（H3C），RS232 协议规范见桌面文档

### 6. 主题与 UI
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
| Android 14+ 广播不可达 | 隐式广播限制 | `setPackage(packageName)` + `RECEIVER_NOT_EXPORTED` |
| USB 权限 PendingIntent 崩溃 | Android 14 禁止隐式 Intent + FLAG_MUTABLE | 改为显式 Intent（setPackage）+ FLAG_MUTABLE |
| Toast 在 H3C 设备不显示 | H3C 系统屏蔽 Toast 通知 | 改用 AlertDialog 显示结果 |
| 串口指令发出但设备无反应 | USB 转 DB9 直通线 TX/RX 未交叉 | DTE↔DTE 需使用交叉线（Null Modem） |

---

## 版本规则

- 每次打包版本号 +0.0.1（十进制，如 1.0.9 → 1.0.10）
- versionCode 同步递增
- APK 文件名格式: `Heyboard-v{版本号}-{YYYY-MM-DD}.apk`
- 版本号体现在 build.gradle.kts 和设置页面

---

## 签名配置

- Keystore: `../heyboard-release.jks`
- Alias: `heyboard`
- 签名密码: `heyboard123`

---

## 开发环境

- macOS, Android Studio
- 主控设备: H3C MegaBook (Android 14, 3840x2160, 无 Google 框架, IP: 10.214.75.17)
- 被控设备: H3C/CVTE mtk9679 交互智能平板 (IP: 10.214.75.35)
- adb 无线连接: `adb connect <ip>:5555`
- 屏幕投射: `scrcpy -s <ip>:5555`

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
- [ ] 验证 FinishClassReceiver 在 APP 被杀后能否正常接收广播（若不行需 H3C 系统源码配合）
