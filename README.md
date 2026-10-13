# BT Mic Route

一个只负责维持 Android 通话音频路由的小工具：让另一应用（本项目实际验证为 **WO Mic**）能从蓝牙耳机的 HFP/SCO 麦克风取音。

## 已验证组合

- 手机：Xiaomi 23049RAD8C（marble），Android 15 / HyperOS 3.0.5.0
- 蓝牙耳机：HUAWEI FreeBuds 6i
- 录音应用：WO Mic（`com.wo.voice2`）
- 结果：WO Mic 的实际输入设备为 `AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET`。

## 日常使用

1. 连接蓝牙耳机，并确保系统蓝牙设置中已启用其“通话音频”。
2. 打开 **BT Mic Route**，点击 **Request Bluetooth route**。
3. 等待界面同时显示：
   - `Requested by this app: true`
   - `Silent communication playback: true`
   - `Audio mode: MODE_IN_COMMUNICATION`
   - `Current communication device: ... BLUETOOTH_SCO ...`
4. 保持本应用运行，打开并启动 WO Mic；随后按原有方式在电脑端连接 WO Mic。
5. 用完后，先在 WO Mic 停止采集，再回到本应用点 **Clear my request**。

## 不能做的事

- 不要在 WO Mic 使用期间点清除、强行停止或杀掉本应用；它负责持续维持蓝牙通话音频路由。
- 仅连接 A2DP（媒体音频）不够；必须让 `BLUETOOTH_SCO` / 通话音频端点可用。
- 本工具不采集、保存或转发麦克风声音，也没有申请 `RECORD_AUDIO` 权限；它播放全零的通话用途 PCM，以防系统在没有活跃通信流时回收 SCO 路由。

## 构建

需要 JDK 17 和 Android SDK。项目使用 Android Gradle Plugin 8.10.1，`local.properties` 指向本机 SDK。

```powershell
& 'C:\Users\Lenovo\.gradle\wrapper\dists\gradle-8.12-all\ejduaidbjup3bmmkhw3rie4zb\gradle-8.12\bin\gradle.bat' --no-daemon :app:assembleDebug
```

生成的 APK 位于 `app\build\outputs\apk\debug\app-debug.apk`。

完整的设计依据、排障步骤及可移植性约束见上级目录的
[`执行方案_安卓蓝牙耳机麦克风到WO_Mic.md`](../执行方案_安卓蓝牙耳机麦克风到WO_Mic.md)。
