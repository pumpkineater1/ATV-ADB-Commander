# Changelog 📝

All notable changes to the **ATV ADB Commander** project are documented in this file.

---

## [v1.2.0] - 2026-11-17

### 🚀 Major Additions & New Features

#### 📶 Wireless & Bluetooth Control Hub
- **Silent Saved Wi-Fi Switching**:
  - Connect to and switch between saved Wi-Fi networks (e.g., `Chandra_5G`, `Aaa`) with a single tap.
  - Powered by direct Android `IWifiManager` shell service calls (`service call wifi 27` for `enableNetwork` and `service call wifi 39` for `reconnect`), switching networks 100% silently in the background without opening disruptive TV setup wizards.
- **Wi-Fi Scanner**:
  - Scan nearby 2.4 GHz and 5 GHz networks with live signal ratings (dBm), channel frequency, and security protocols.
- **Bluetooth Device Manager**:
  - View all paired/bonded Bluetooth accessories (headphones, earbuds, soundbars, remotes, and gamepads) with battery percentage indicators.
  - **1-Tap Connect**: Automated native accessory connection sequence for Google TV and Android TV 14 to immediately route TV audio to your Bluetooth headphones.
  - **1-Tap Disconnect**: Immediately releases audio routing and disconnects the device.
  - **Device Settings**: Quick gear icon (⚙️) to open the TV's native accessory manager to rename or unpair.

#### 🎬 Instant "Play on TV" (Media Casting)
- Tap any media file directly within the TV File Explorer or your pushed media:
  - **Audio** (`.mp3`, `.m4a`, `.flac`, `.wav`, `.aac`, `.ogg`): Launches the TV's default audio player.
  - **Video** (`.mp4`, `.mkv`, `.avi`, `.mov`, `.webm`): Opens the TV video player for playback.
  - **Images** (`.png`, `.jpg`, `.jpeg`, `.webp`, `.gif`): Displays full-screen in the TV photo viewer.
  - **APKs** (`.apk`): Triggers the TV package installer dialog.

#### ⚡ TV Sleep Detection & Instant Wake Up
- Real-time TV power state monitoring via `dumpsys power` (`mWakefulness`).
- Shows a **"TV in Sleep Mode"** banner on the Overview and Remote screens when the TV is in standby or dozing.
- Added a one-tap **"Wake Up TV"** button to instantly wake the screen and restore active ADB communication.
- Low-latency socket auto-recovery to prevent "ADB not connected" warnings during app sideloading.

#### 🔊 Physical Phone Volume Rocker Sync
- Intercepts physical volume keys (`KEYCODE_VOLUME_UP` / `KEYCODE_VOLUME_DOWN`) on your Android phone to adjust TV master volume in real-time.
- Direct `cmd media_session volume` execution for smooth, responsive volume stepping and mute toggling.

#### 🛠️ Tweaks Screen 3-Dots Overflow Menu (⋮)
- Added an OLED-styled circular 3-dots button on the Tweaks header containing:
  - **About & Contact**: Version details and developer contact (`message2sekhar@gmail.com`).
  - **Wireless & Bluetooth**: Quick launch to the Wi-Fi & Bluetooth manager dialog.
  - **TV Developer Options**: Instant shortcut to hidden TV developer settings.
  - **Keep TV Screen Awake (Stay Awake)**: Toggle `svc power stayon true/false`.
  - **Take TV Screenshot**: Capture TV screen and save directly to phone.
  - **Flush TV Logcat Buffer**: Quick `logcat -c` buffer memory purge.
  - **Reboot TV**: Fast reboot confirmation.

#### 🎨 UI & Navigation Enhancements
- Renamed the header store button from "Play Store" to a clean **Store** pill.
- Enhanced the **+ TV** connection button with higher contrast and clearer tap feedback.
- Restored and expanded navigation tabs: **Overview**, **Apps**, **Remote**, **Files**, and **Tweaks**.

---

## [v1.1.0] - 2026-11-17

### 🌟 Features & Refinements
- Added one-tap TV Play Store direct launcher.
- Multi-core CPU meter normalization to prevent false 100% spikes across multicore TV processors.
- Auto-refresh button with animated loading spinner during app indexing.
- Comprehensive Apache 2.0 open-source licensing and contribution guidelines.

---

## [v1.0.0] - Initial Open Source Release

### ✨ Features
- Pure Kotlin standalone ADB client with RSA 2048-bit keypair generation.
- Zero-download APK sideloading from phone to TV (`pm install -r -d -g`).
- Full rootless debloater (`pm disable-user --user 0`).
- 3-in-1 Smart Remote (D-Pad, Virtual Trackpad/Air Mouse, Numeric Pad).
- Two-way interactive TV File Explorer (`SEND` / `RECV`).
- Real-time CPU, RAM, Network I/O, and Storage telemetry.
- Titanium OLED dark theme with Jetpack Compose Material 3.
