# HyMed Doctor Android

Native Android Studio project for the HyMed doctor app.

Project path:

```text
D:\HyMedDoctorAndroid
```

## What is included

- Native Android UI, not WebView.
- Login with the existing HyMed backend.
- Bottom navigation: Home, Features, Settings.
- Patient selector.
- Dispensing task creation and status list.
- Infusion task creation and status list.
- Treatment timeline view.
- Case generation screen.
- Native microphone recording with `MediaRecorder`.
- Audio transcription upload to the existing doctor backend.
- CT assistant entry opens the existing Dify page in the system browser.
- HTTP cleartext traffic is enabled for the current test server.

## Default server

```text
http://119.29.163.179:8082
```

You can change the server URL on the login screen.

## Backend APIs used

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

## How to run

1. Open Android Studio.
2. Choose `Open`.
3. Select:

```text
D:\HyMedDoctorAndroid
```

4. Wait for Gradle sync.
5. Connect a phone or start an emulator.
6. Click `Run`.

## Build APK

In Android Studio:

```text
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

Or after Android Studio creates/uses a Gradle wrapper:

```powershell
cd D:\HyMedDoctorAndroid
.\gradlew.bat assembleDebug
```

Debug APK path is usually:

```text
D:\HyMedDoctorAndroid\app\build\outputs\apk\debug\app-debug.apk
```

## Notes

- This project currently uses platform Android APIs only.
- No Retrofit, OkHttp, Compose, or XML layout files are required.
- The app requests `INTERNET` and `RECORD_AUDIO`.
- Browser HTTP microphone restrictions do not apply here because recording is native Android recording.
- If the hospital server later enables HTTPS, remove cleartext HTTP settings from the manifest and network security config.

