# HyMedDoctorAndroid

HyMedDoctorAndroid 是一个面向医生端场景的原生 Android 应用项目，用于连接无界医院相关后端服务，提供患者查看、配药任务、输液任务、诊疗时间线、病历生成、语音录入等医生工作台功能。

本项目使用 Android Studio 开发，主要语言为 Kotlin。

## 项目特点

- 原生 Android 应用，不是 WebView 套壳。
- 使用 Kotlin 编写主要业务和界面逻辑。
- 支持医生登录，并连接现有 HyMed 后端接口。
- 支持患者列表与患者信息切换。
- 支持配药任务创建、查看和状态展示。
- 支持输液任务创建、查看和状态展示。
- 支持诊疗时间线查看。
- 支持病历生成入口。
- 支持原生 Android 麦克风录音。
- 支持音频上传至医生端后端进行转写。
- 支持 CT 助手入口，打开既有 Dify 页面。
- 已针对平板和手机进行基础响应式适配。
- 已对首页、卡片、按钮、底部导航等前端界面做过一轮优化，整体更宽松、更适合医疗工作台场景。

## 技术栈

- Android Studio
- Kotlin
- Gradle Kotlin DSL
- Android SDK 35
- minSdk 26
- targetSdk 35
- 原生 Android View UI

## 目录结构

```text
HyMedDoctorAndroid/
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle.properties
├─ gradlew
├─ gradlew.bat
├─ gradle/
└─ HyMedDoctorAndroid/
   ├─ app/
   │  ├─ build.gradle.kts
   │  └─ src/main/
   │     ├─ AndroidManifest.xml
   │     ├─ java/com/hymed/doctorapp/
   │     │  ├─ MainActivity.kt
   │     │  └─ LocaleHelper.kt
   │     └─ res/
   ├─ build.gradle.kts
   ├─ settings.gradle.kts
   └─ gradle.properties
```

当前仓库根目录做了 Gradle 桥接配置，因此可以直接用 Android Studio 打开仓库根目录。

## 运行方式

1. 克隆本仓库。
2. 使用 Android Studio 打开仓库根目录。
3. 等待 Gradle Sync 完成。
4. 在 Android Studio 中确认运行配置：

```text
Type: Android App
Module: app
Launch: Default Activity
```

5. 连接真机或启动模拟器。
6. 点击 Android Studio 顶部绿色 Run 按钮运行。

## 真机调试

真机运行前需要在手机或平板上开启：

```text
开发者选项
USB 调试
```

连接电脑后，如果设备弹出 USB 调试授权窗口，选择允许。然后在 Android Studio 顶部设备列表中选择对应设备，再点击 Run。

## 构建 APK

在 Android Studio 中可以通过菜单构建：

```text
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

也可以在命令行中执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

构建成功后的 debug APK 通常位于：

```text
HyMedDoctorAndroid/app/build/outputs/apk/debug/app-debug.apk
```

## 默认后端地址

```text
http://119.29.163.179:8082
```

应用登录页可以修改服务器地址。

## 后端接口

当前应用使用的主要接口包括：

```text
POST /api/administrator/auth/login
GET  /api/customer/patients
GET  /api/nurse/dispensing_tasks
GET  /api/nurse/infusion_tasks
GET  /api/customer/patients/{patient_id}/events
POST /api/doctor/dispensing_tasks
POST /api/doctor/infusion_tasks
POST /api/doctor/case-generator
POST /api/doctor/audio/transcriptions
```

## 本地配置说明

以下文件不会提交到仓库：

```text
local.properties
.gradle/
build/
.idea/
*.apk
```

这些文件属于本机配置或构建产物。重新拉取项目后，Android Studio 会自动生成本机所需的配置文件。

如果项目路径包含中文，Android Gradle Plugin 可能会提示路径检查问题。本项目已在 `gradle.properties` 中启用：

```properties
android.overridePathCheck=true
```

## 备注

- 当前项目主要界面由 `MainActivity.kt` 通过原生 Android View 动态生成。
- 项目没有使用 Jetpack Compose。
- 项目没有使用 Retrofit 或 OkHttp，网络请求基于平台 API 实现。
- 应用需要 `INTERNET` 和 `RECORD_AUDIO` 权限。
- 当前测试后端为 HTTP 地址，因此项目中包含明文网络访问配置；后续如果后端升级为 HTTPS，可移除相关明文网络配置。
