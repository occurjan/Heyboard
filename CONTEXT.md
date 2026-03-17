# Heyboard 教学助手 - 项目上下文

## 项目概览

面向教育场景的 Android 教学辅助工具，运行在横屏 16:9 平板上（主要目标设备：H3C MegaBook）。

- **包名**: `com.heyboard.teachingassistant`
- **当前版本**: 1.0.3 (versionCode=3)
- **编译环境**: AGP 9.1.0, compileSdk 36, minSdk 24, targetSdk 36, Java 11
- **GitHub**: https://github.com/occurjan/Heyboard

---

## 功能模块

| 模块 | Activity | Service | 说明 |
|------|----------|---------|------|
| 主页 | MainActivity | - | 四宫格功能入口 + 设置/HOME 按钮 |
| 随机点名 | RandomCallActivity | - | 设置学号范围，随机抽人 |
| 课堂计时器 | TimerActivity | - | 倒计时，支持暂停/继续/重置 |
| 聚光灯 | SpotlightActivity | SpotlightService | 悬浮遮罩 + 可拖动透明区域，实时调节大小和透明度 |
| 实时字幕 | SubtitleActivity | SubtitleService | Vosk 离线语音识别，悬浮窗显示字幕 |
| 设置 | SettingsActivity | - | 版本信息、语言切换（中/英） |

---

## 代码结构

```
app/src/main/
├── java/com/heyboard/teachingassistant/
│   ├── MainActivity.kt              # 主页，四宫格入口
│   ├── RandomCallActivity.kt        # 随机点名
│   ├── TimerActivity.kt             # 计时器
│   ├── SpotlightActivity.kt         # 聚光灯设置页（SeekBar 调节）
│   ├── SpotlightService.kt          # 聚光灯悬浮窗服务（双窗口架构）
│   ├── SpotlightOverlayView.kt      # 聚光灯遮罩自定义 View
│   ├── SubtitleActivity.kt          # 字幕设置页（语言选择）
│   ├── SubtitleService.kt           # 字幕悬浮窗服务（Vosk 语音识别）
│   ├── VoskModelManager.kt          # Vosk 模型管理（从 assets 解压）
│   └── SettingsActivity.kt          # 设置页（版本、语言）
├── res/
│   ├── layout/                      # 6 个 Activity 布局 + 1 个悬浮窗布局
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

### 5. 主题与 UI
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
- 目标设备: H3C MegaBook (Android 14, 3840x2160, 无 Google 框架)
- adb 无线连接: `adb connect <ip>:5555`
- 屏幕投射: `scrcpy -s <ip>:5555`

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
