# BTRedirect

An Android helper that keeps a Bluetooth headset's HFP/SCO microphone route alive for another app, verified with WO Mic.

## What it does

- Requests a Bluetooth communication device on Android 12 and above.
- Keeps a silent `USAGE_VOICE_COMMUNICATION` playback stream active so Android does not reclaim the SCO route.
- Does **not** record, store, or transmit microphone audio. The application does not request `RECORD_AUDIO`.
- Uses Material You dynamic colour when the device supports it.
- Supports Simplified Chinese and English, automatically selected from the device language.

## Use

1. Connect the headset and make sure its **Call audio** setting is enabled.
2. Open BTRedirect and choose **Enable Bluetooth mic routing**.
3. Wait for **Bluetooth SCO is ready**.
4. Keep BTRedirect running, then start WO Mic and connect it on the PC as usual.
5. When finished, stop WO Mic first, then choose **Stop and clear routing** in BTRedirect.

If the route does not become ready, reconnect the Bluetooth headset and try again. Media-only A2DP audio cannot provide the headset microphone.

## Build

Install JDK 17 and Android SDK Platform 34 or newer. Create `local.properties` locally with an `sdk.dir` entry, then build with Gradle:

```powershell
gradle :app:assembleDebug
```

The generated APK is under `app/build/outputs/apk/`.

## Verified setup

- Xiaomi 23049RAD8C (Android 15 / HyperOS 3.0.5.0)
- HUAWEI FreeBuds 6i
- WO Mic (`com.wo.voice2`)
