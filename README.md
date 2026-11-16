# ATV ADB Commander 📺⚡

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Android%20TV%20%7C%20Google%20TV%20%7C%20Fire%20TV-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue.svg)](https://developer.android.com/jetpack/compose)
[![Min API](https://img.shields.io/badge/Min%20API-26%20(Android%208.0)-orange.svg)](https://android-arsenal.com/api?level=26)

**ATV ADB Commander** is a next-generation, rootless Android mobile application built with **Kotlin** and **Jetpack Compose (Material 3)**. It enables you to wirelessly manage, debloat, monitor, stream, and control **Android TV**, **Google TV**, and **Amazon Fire TV** devices over network ADB — completely standalone, with **zero computer or external server required**.

---

## 🌟 Key Features

### 1. ⚡ Standalone Pure-Kotlin Wireless ADB Engine
- **No PC / Server Required**: Native ADB TCP client implementation directly inside the app.
- **Secure Authentication**: Built-in 2048-bit RSA keypair generation, token signing (`A_AUTH`), and public key exchange.
- **TLS ADB Support**: Compatible with Android 11+ wireless debugging standards.
- **One-Tap Subnet Scanner**: Automatically discovers active TV devices on your local Wi-Fi network (port 5555).

### 2. 🖤 Titanium OLED Dark Interface & Gestures
- **Edge-to-Edge Titanium Styling**: High-contrast OLED dark background (`#08090A`) with subtle neon teal & cyan accents.
- **Fluid Swipe Gestures**: Smooth swipe navigation across all major sections using Compose `HorizontalPager` (`Overview` ⇄ `Apps` ⇄ `Remote` ⇄ `Files` ⇄ `Tweaks`).
- **Quick Action Pills**: One-tap access to **✦ Screensaver**, **🛍 Store**, **⟳ Reboot**, and **+ TV**.

### 3. 📊 Real-Time Telemetry & Hardware Monitor
- **Multi-Core Normalized CPU Meter**: Accurately accounts for all CPU cores to eliminate false 100% spikes.
- **Live Memory Utilization**: Displays active RAM consumption %, used vs. free memory.
- **Network I/O Throughput**: Shows live incoming and outgoing bandwidth (KB/s, MB/s).
- **Storage Breakdown**: Real-time internal storage capacity and free space meter.

### 4. 🛍️ Direct One-Tap TV Play Store Integration
- Direct launcher action (`com.google.android.finsky.tvmainactivity.TvMainActivity`) to open Google TV Play Store with a single tap from your phone.

### 5. 📦 TV App Manager & Rootless Debloater
- **Smart Filtering**: Categorize packages into **User Apps**, **System Apps**, **Disabled Apps**, and **All Apps**.
- **Rootless Debloating**: Disable pre-installed manufacturer bloatware safely via `pm disable-user --user 0`.
- **One-Tap Enable / Restore**: Re-enable any disabled package instantly.
- **Process Control & Uninstall**: Force-stop running apps or uninstall packages directly from your phone.

### 6. 📲 Zero-Download Sideloading (Phone ➔ TV)
- Scan apps already installed on your mobile phone (e.g. SmartTube, Kodi, VLC, Stremio, browsers).
- Directly extracts the APK from phone storage and streams it to your TV with automatic installation (`pm install -r -d -g`).
- Never hunt for sketchy third-party APK download links again.

### 7. 🎮 3-in-1 Smart Remote Controller
- **Directional D-Pad**: Classic Up, Down, Left, Right, and Center Select with tactile haptic feedback.
- **Virtual Trackpad / Air Mouse**: Smooth touch surface for swiping and clicking inside TV web browsers or mobile-ported apps.
- **Numpad & Channel Mode**: Full 12-key numeric pad for live TV channels, setup codes, and PINs.
- **Software Keyboard Input**: Send typed text, URLs, and complex passwords directly to TV input fields.
- **Media Controls**: Volume Up/Down/Mute, Play/Pause, Fast Forward, Rewind, and Home.

### 8. 📁 Two-Way Interactive TV File Explorer
- Browse the full TV storage hierarchy (`/sdcard/`, `Downloads`, `Movies`, `Pictures`).
- **Push Files**: Upload videos, subtitles, photos, or ROMs from phone to TV.
- **Pull Files**: Download recordings, logs, or backups from TV directly to your phone.
- **File Management**: Create directories (`mkdir -p`) and delete files (`rm -rf`) with confirmation dialogs.

### 9. 🛠️ Power & TV Tweaks Suite (with 3-Dots Menu)
- **Top-Right Overflow Menu (`⋮`)**: Quick access to power user actions:
  - **About & Contact**: Version details and developer contact modal.
  - **TV Developer Options**: Instant launch of hidden TV developer settings.
  - **Keep TV Screen Awake**: Toggle `svc power stayon true/false` to prevent unwanted standby.
  - **Flush TV Logcat Buffer**: One-tap `logcat -c` memory buffer purge.
  - **TV Screenshot Capture**: Instant high-resolution TV screengrab saved to phone.
  - **Reboot Modes**: Normal Reboot, Soft Reboot (`restart zygote`), and Recovery.
- **Display Resolution & Density**: Custom resolution scaling (1080p, 4K, 720p) and DPI adjustments.
- **Animation Scales**: Speed up TV UI animations (0.5x, 0.25x, or disabled).

---

## 🏛️ Project Architecture

```
atv-adb-commander/
├── app/
│   ├── src/main/java/com/atv/adbcommander/
│   │   ├── adb/                 # Pure Kotlin ADB protocol implementation
│   │   │   ├── AdbConnection.kt # Socket, handshake, auth & channel manager
│   │   │   ├── AdbCrypto.kt     # RSA 2048-bit keypair & token signing
│   │   │   ├── AdbManager.kt    # High-level API (apps, power, debloat, stream)
│   │   │   └── AdbSync.kt       # Binary SEND/RECV file sync engine
│   │   ├── data/                # Data models, discovery & telemetry parsers
│   │   │   ├── DeviceTelemetry.kt
│   │   │   └── SubnetScanner.kt
│   │   └── ui/                  # Jetpack Compose UI
│   │       ├── screens/
│   │       │   ├── OverviewScreen.kt # Hero telemetry, pills & quick tools
│   │       │   ├── AppsScreen.kt     # App manager, search & debloater
│   │       │   ├── RemoteScreen.kt   # D-Pad, Trackpad & Numpad remote
│   │       │   ├── FilesScreen.kt    # Two-way TV file explorer
│   │       │   └── TweaksScreen.kt   # Launchers, tweaks & 3-dots menu
│   │       ├── components/      # Reusable dialogs, cards & navbars
│   │       └── theme/           # Titanium OLED theme & typography
│   └── build.gradle.kts
├── tv-mouse-pointer/            # Companion TV Accessibility cursor service module
├── gradle/                      # Gradle wrapper
├── build.gradle.kts             # Root build script
├── settings.gradle.kts          # Project settings
├── CONTRIBUTING.md              # Contribution guidelines
├── LICENSE                      # Apache 2.0 Open Source License
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites
- **Android Phone**: Running Android 8.0 (API 26) or higher.
- **Target Device**: Android TV, Google TV, or Amazon Fire TV connected to the **same Wi-Fi network**.
- **Developer Options Enabled on TV**:
  1. On your TV, navigate to **Settings** ➔ **System** ➔ **About**.
  2. Click **Android TV OS Build** 7 times until Developer Mode is activated.
  3. Go to **Settings** ➔ **System** ➔ **Developer Options**.
  4. Enable **USB Debugging** (and **Wireless Debugging** if available).

### Connecting
1. Launch **ATV ADB Commander** on your phone.
2. Tap **+ TV** on the top bar or use the automatic subnet discovery scan.
3. Enter your TV's IP address (e.g. `192.168.1.40:5555`).
4. On your TV, a prompt will appear: **"Allow USB Debugging?"** ➔ Check *"Always allow"* and click **Allow**.

---

## 🔨 Building from Source

### Requirements
- **JDK 17** or higher
- **Android SDK** with platform `android-34` and build-tools `34.0.0`
- Gradle 8.7+ (wrapper included)

### Build Commands
```bash
# Clone the repository
git clone https://github.com/pumpkineater1/atv-adb-commander.git
cd atv-adb-commander

# Build debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk

# Install directly to a connected test phone via ADB:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🤝 Contributing

Contributions, feature suggestions, and pull requests are warmly welcomed! Please read our [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code style and submission process.

---

## 📄 License

This project is licensed under the **Apache License 2.0** — see the [LICENSE](LICENSE) file for details.

---

## 📬 Contact & Support

- **Maintainer**: Sekhar
- **Email**: [message2sekhar@gmail.com](mailto:message2sekhar@gmail.com)
- **GitHub**: [@pumpkineater1](https://github.com/pumpkineater1)
