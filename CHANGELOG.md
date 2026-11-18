# Changelog 📝

All notable changes to the **ATV ADB Commander** project are documented in this file.

---

## [v1.3.0] - 2026-11-19

### 🌐 Full IPv6 Network ADB Support
- **Dual-Stack IPv4 & IPv6 ADB Connection Engine**:
  - Native support for Global IPv6 addresses (e.g. `2401:9640:...`), link-local IPv6 (`fe80::...`), and bracketed notations (`[2401:...]:5555`).
  - Automatic Wi-Fi interface scope ID resolution (`fe80::...%wlan0`) preventing socket connection drops.
- **Enhanced Radar Scanner with IPv6 Neighbor Discovery**:
  - Scanner automatically queries local IPv6 neighbor tables (`ip -6 neigh`) to discover Android TV devices over IPv6.
  - TV targets display distinctive `IPv6` / `IPv4` badge pills and RFC 5952 formatted addresses.
- **Direct Manual IP / Host Connect Card**:
  - Direct manual IP input card in `TvDiscoveryDialog` supporting both IPv4 and IPv6 with one-tap clipboard paste and port configuration.
  - Saved devices list tracks and displays IPv6 targets with 1-tap re-linking.
- **Live IPv6 Network Telemetry**:
  - Shows active TV IPv6 address in the Wireless & Bluetooth dialog and displays an `IPv6 · Running fine` indicator in the Overview header.

---

## [v1.2.0] - 2026-11-17

### 🚀 Major Additions & New Features

#### 📊 Continuous App Installation Progress Bar (Phone ➔ TV)
- **Smooth End-to-End Progress Lifecycle**:
  - **Stage 1 (0% - 70%)**: Real-time continuous APK streaming progress bar with transferred bytes (e.g. `24.5 MB / 48.0 MB`) and live upload speed (`8.4 MB/s`).
  - **Stage 2 (70% - 98%)**: Continuous TV OS installation progress with animated pulsing progress bar and real-time status stages (*"Verifying package..."*, *"Extracting DEX code & native libraries..."*, *"Writing to Android TV storage..."*, *"Optimizing runtime performance..."*, *"Finalizing registration..."*).
  - **Stage 3 (100%)**: Instant completion indicator with a direct **[ Open on TV ]** button to launch the newly installed app on your TV with one tap.
- **Global AppInstallProgressDialog**:
  - Modal Titanium OLED dialog appears automatically during sideloading or APK installation from any screen.
  - Can be minimized to a floating bottom pill (*"Installing App (75%)..."*) while continuing to browse other tabs.
- **Direct Sideload & Install Buttons**:
  - Added "+ Install APK" directly into the Apps tab header alongside Search and TV Play Store.
  - Sideload buttons inside Phone App list show live inline percentage badges (`Installing 65%`).

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
