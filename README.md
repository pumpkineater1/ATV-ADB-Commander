# ATV ADB Commander

A native Android mobile application built with **Kotlin** and **Jetpack Compose (Material 3)** to wirelessly interact with, control, manage, and monitor **Android TV / Google TV / Fire TV** devices over network ADB without requiring a computer.

---

## ✨ Features Overview

### 1. 🏠 Android TV Launcher Manager (Change Default Home Screen)
- **Automatic Launcher Detection**: Queries all installed home launchers on the TV (Stock Google TV Launcher, Android TV Home, Fire TV Home, Projectivy Launcher, FLauncher, Emotn UI, Wolf Launcher, etc.).
- **Set Default Launcher**: Switch your default home launcher with one tap.
- **Bypass Stock Ads & Bloatware**: Disable the stock Google TV / Amazon Fire TV home screen (`pm disable-user --user 0`) so pressing the **HOME button on your physical remote permanently launches your custom launcher** (Projectivy, FLauncher, etc.) without ads.
- **Restore Stock Launcher**: Re-enable stock launchers with one tap at any time.
- **Popular Launchers Guide**: Recommendations and quick shortcuts to sideload Projectivy, FLauncher, or Emotn UI directly from your phone.

### 2. 🎨 Material 3 Modern Design Polish
- Expressive Material 3 dark color palette with midnight-tinted surface containers (`#0C1017`, `#131924`, `#1D2434`).
- Refined 20dp/24dp rounded corners, tonal elevation, pill-shaped status badges, and glowing indicators.
- Quick-access top toolbar for **Terminal**, **TV Power Tools**, and **Wi-Fi Auto-Discovery**.

### 3. 🔍 Automatic TV Discovery (Wi-Fi Subnet Scanner)
- **One-Tap Wi-Fi Scan**: Automatically scans your local network subnet on port 5555 to discover all nearby Android TVs and Fire TV sticks.
- Tap any discovered TV to instantly connect without manually looking up or typing IP addresses.

### 4. 📺 3-in-1 Smart Remote Controller
- **D-Pad Mode**: Classic directional navigation (Up, Down, Left, Right), Center Select/OK with haptic vibration feedback.
- **Virtual Trackpad (Air Mouse)**: Large smooth touchpad surface allowing you to swipe to move cursor/scroll and tap to click inside sideloaded mobile apps or TV browsers that require touch or mouse input.
- **Numpad & Channel Mode**: 12-button keypad (0–9, Del, OK) with dedicated Channel Up / Channel Down buttons for live TV channels, setup codes, and PINs.
- **Direct Keyboard / Text Input**: Send typed text, long URLs, and passwords directly from your phone's software keyboard to TV input fields.
- **Playback & Audio**: Volume Up/Down/Mute, Play/Pause, Fast Forward, Rewind.

### 5. 📸 Live TV Screen Capture & Screenshot Tool
- View live screenshots of what is currently playing or displaying on your Android TV screen.
- Export and save TV screenshots directly to your phone's **Pictures** gallery in one tap.

### 6. 📲 Sideload Phone Installed Apps Directly to TV
- **Zero-Download Sideloading**: Scans apps installed on your phone (e.g. VLC, Kodi, SmartTube, Chrome, Stremio, etc.).
- Extracts the APK directly from your phone storage and streams it to your TV with automated installation (`pm install -r -d -g`).
- No need to search for or manually download `.apk` files from third-party websites!

### 7. 📂 Interactive 2-Way TV File Explorer
- Browse the full file system on your Android TV (`/sdcard/`, `/sdcard/Download`, `/sdcard/Movies`, `/data/local/tmp`, etc.).
- **Download/Pull files** from TV storage directly to your phone's `Downloads/` directory.
- **Push files** (videos, movies, music, photos, documents) from phone to TV.
- **Delete files and folders** on TV (`rm -rf`) with confirmation.
- **Create new folders** on TV (`mkdir -p`).

### 8. ⚡ TV Power & Display Tweaks
- **Reboot Modes**: Normal Reboot, Soft Reboot (`restart zygote`), and Recovery (`reboot recovery`).
- **Sleep & Wake Up**: Put TV display to sleep or wake it up wirelessly.
- **Display Resolution & DPI Presets**: Quick switch between 1080p (`1920x1080`), 4K (`3840x2160`), 720p (`1280x720`), or Reset to default (`wm size reset`), plus display density adjustment.

### 9. 📊 Real-Time TV Monitor & App Manager (Debloat & Disable Apps)
- **Live CPU Usage**: Displays total CPU utilization %, User %, System %, Idle %, and top CPU-consuming active TV processes with PID.
- **RAM Memory**: Live TV RAM consumption meter showing Total, Used, Free, and Cached memory in MB and %.
- **TV Storage**: Displays `/sdcard` disk space (Used vs Available).
- **TV App Manager & Debloater**:
  - **4 Smart Filters with Live Counts**:
    - **User Apps**: Installed third-party packages.
    - **System Apps**: Pre-installed manufacturer / system apps.
    - **Disabled Apps**: All apps currently disabled on the TV.
    - **All Apps**: Complete view of every installed package.
  - **Disable TV Apps**: Debloat your TV by disabling unwanted bloatware without root (`pm disable-user --user 0`).
  - **Enable TV Apps**: Re-enable previously disabled packages with one tap (`pm enable`).
  - **Launch & Force Stop**: Test launch apps or force-stop running processes.
  - **Uninstall**: Delete user apps with a confirmation dialog.

### 10. 💻 Interactive ADB Shell Terminal
- Fast-access top-bar shortcut and dialog to run any arbitrary ADB shell command directly on the TV (`dumpsys`, `getprop`, `uptime`, `netstat`, `reboot`, etc.) with quick-action shortcut chips.

---

## 🛠️ Architecture & Under the Hood

- **Pure Kotlin ADB Engine**: Implements the native ADB TCP protocol (ports 5555/custom) with RSA 2048-bit key authentication (`A_AUTH`), token challenge signing, and ADB public key exchange format. No computer or `adb` server daemon is required on the mobile device.
- **ADB Sync Protocol**: Direct byte-stream file pushing (`SEND`) and file pulling (`RECV`).
- **Modern Jetpack Compose UI**: Clean dark theme with Material 3 components, reactive state management via `StateFlow` and Coroutines.

---

## 📦 Building & APK File

Pre-built APK is located at:
- `ATV_ADB_Commander.apk` (in the project root)
- `app/build/outputs/apk/debug/app-debug.apk`

To build again using Gradle:
```bash
./gradlew assembleDebug
```
