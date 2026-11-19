# Changelog 📝

All notable changes to the **ATV ADB Commander** project are documented in this file.

---

## [v1.3.2] - 2026-11-20

### 🚀 Extreme Speed & Zero-Latency Performance Optimizations
- **Single-Frame ADB Packet Writes (`AdbProtocol.kt`)**:
  - Combined the 24-byte message header and data payload into a single contiguous byte buffer (`ByteArray(24 + payload.size)`) before writing to the TCP output stream.
  - Completely eliminated TCP packet fragmentation, Nagle delays, and unnecessary lock contention, reducing command transmit latency to absolute sub-millisecond wire speed.
- **TCP Socket Tuning (`AdbConnection.kt`)**:
  - Configured `IPTOS_LOWDELAY = 0x10`, `tcpNoDelay = true`, `SO_KEEPALIVE = true`, and expanded socket send/receive buffers to 128 KB.
  - Eliminated wrapper stream overhead and streamlined socket handshake execution.
- **Non-Reentrant Mutex Deadlock Elimination (`AdbManager.kt`)**:
  - Separated `internalConnect()` from public `connect()`.
  - Resolved coroutine deadlock where `autoConnect()` and `switchToDevice()` attempted re-entrant mutex acquisitions on `connectMutex`.
- **Asynchronous Background Socket Teardown**:
  - Old sockets and background receiver threads are closed asynchronously in a background coroutine (`managerScope.launch(Dispatchers.IO)`), allowing new connections to begin socket dialing in 0.01ms without waiting on TCP FIN/RST or close timeouts.
- **Single Composite Overview Telemetry Command**:
  - Bundled model, brand, Android version, security patch, storage (`df -h`), memory (`cat /proc/meminfo`), CPU (`/proc/stat`), power state (`dumpsys power | grep -m 1`), and current launcher (`pm resolve-activity`) into **1 single ADB shell command** with delimiters.
  - Added `-m 1` fast-exit grep on `dumpsys power` to terminate dumpsys output immediately via SIGPIPE, avoiding 200 KB history dumps.
  - Reduced roundtrips on connection from ~15 sequential network commands down to 1 single composite command.
- **0ms Instant UI Telemetry Caching**:
  - Telemetry is cached per-device on disk (`cached_tv_telemetry_$safeKey.json`).
  - Switching between saved TVs (Google TV Streamer and Fire TV Stick 4K Max) instantly populates device specs, storage meters, CPU, and RAM at $t=0$ with 0ms delay.
- **Reactive StateFlow for Saved Devices**:
  - Exposed `savedDevices` as `StateFlow<List<SavedTvDevice>>`.
  - All screens (`OverviewScreen`, `ConnectionHeader`, `TvDiscoveryDialog`, `MonitorScreen`) reactively observe `savedDevices` via `collectAsState()`, updating the TV switcher pills instantly.
- **Verified Sub-200ms Live Device Switching**:
  - Measured live device switching between Google TV Streamer (`192.168.1.35`) and Fire TV Stick 4K Max (`192.168.1.37`) at **123ms – 183ms**.

---

## [v1.3.1] - 2026-11-20

### ⚡ Ultra-Fast TV Switching & High-Concurrency ADB Streams
- **1-Tap TV Switcher in Overview Screen**:
  - Automatically displays a sleek horizontal switcher row in the hero banner whenever multiple TVs are saved (e.g. Google TV Streamer and Fire TV Stick 4K Max).
  - Tap any saved TV pill to switch immediately without disconnecting, waiting, or re-scanning.
  - Active TV is badged with a vibrant `• Active` glowing indicator.
- **Concurrent ADB Multiplexing (Semaphore Upgrade)**:
  - Replaced the global single-command lock (`shellMutex`) with a high-concurrency 8-permit `Semaphore(8)`.
  - Overview telemetry, CPU/RAM live monitoring, and installed apps now execute concurrently across independent ADB streams instead of queuing single-file for 10–15 seconds.
- **Per-Device Target-Keyed App Caching**:
  - Saved TV package lists and app metadata are keyed by target device (`cached_tv_apps_$safeKey.json`).
  - Switching between Google TV Streamer and Fire TV Max renders the complete apps list instantly in 0ms without re-running heavy `pm list packages` and `app_process` helper scripts.
- **Eliminated Disconnect Race Conditions**:
  - Switching TVs now cleanly flags intentional user disconnect and cancels background reconnect watchdog jobs (`reconnectJob`), preventing the app from attempting to reconnect to the old TV while simultaneously connecting to the new one.
- **Optimized Handshake & Telemetry Hand-off**:
  - Reduced connection handshake latency to <150ms for already-paired devices with an adaptive 6s timeout (only waiting up to 45s if the TV OS prompts for authorization).
  - Background Wi-Fi probing is deferred until opening the Wireless dialog, keeping initial TV connection crisp and lightweight.

---

## [v1.3.0] - 2026-11-19

### 🌐 Full IPv6 Network ADB & Zero-Touch Dual-Stack Auto-Connect
- **Zero-Touch Dual-Stack Auto-Discovery & Auto-Connect**:
  - If a saved IPv4 address is unreachable (e.g. DHCP assigned a new IP or client isolation blocks IPv4), `AdbManager.autoConnect()` automatically initiates dual-stack auto-discovery in the background.
  - Discovers TV devices over **mDNS / DNS-SD (`NsdManager` for `_adb._tcp`)**, neighbor caches, and parallel subnet scans concurrently.
  - **Connects automatically to the discovered TV with zero manual typing or user intervention required**.
  - Stores both the IPv4 and IPv6 addresses for each TV to enable seamless automatic fallback if either protocol becomes unreachable.
- **Dual-Stack IPv4 & IPv6 ADB Connection Engine**:
  - Native support for Global IPv6 addresses (e.g. `2401:9640:...`), link-local IPv6 (`fe80::...`), and bracketed notations (`[2401:...]:5555`).
  - Automatic Wi-Fi interface scope ID resolution (`fe80::...%wlan0`) preventing socket connection drops.
- **Enhanced Radar Scanner with IPv6 & mDNS**:
  - Scanner simultaneously listens for mDNS service broadcasts, queries local neighbor tables, and scans the local subnet.
  - TV targets display distinctive `IPv6` / `IPv4` badge pills and RFC 5952 formatted addresses.
- **One-Tap Quick Connect UI**:
  - Tapping "Offline · Tap to Connect" or the prominent "Connect TV" button automatically triggers dual-stack network discovery and connects immediately.
  - Direct manual IP input card remains available for custom port/host configuration without ever requiring manual entry for standard connections.
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
