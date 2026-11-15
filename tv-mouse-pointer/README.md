# TV Mouse Pointer (Virtual Remote Mouse for Android TV & Fire TV)

A native Android TV application built with **Kotlin** and **Jetpack Compose (Material 3)** that turns your physical TV remote control into a virtual mouse pointer. This allows you to smoothly move an on-screen cursor and click anywhere—especially inside sideloaded mobile apps, streaming services, and web browsers that normally require touch or mouse input.

---

## 🚀 Key Features

### 1. 🎯 Precision Cursor & Physics
- **Smooth 60 FPS Cursor Movement**: Glide the cursor with your remote's D-pad (▲ ▼ ◄ ►).
- **Adaptive Acceleration**: Initial presses move with sub-pixel precision for clicking small links; holding a direction smoothly accelerates across 4K / 1080p screens.
- **Visual Feedback**:
  - High-visibility drop shadow and crisp outline ensuring visibility against dark movie scenes and bright web pages.
  - Animated ripple pulse upon clicking.
  - Mode indicators for "Scroll Mode" and "Drag Mode".

### 2. 🎮 TV Remote Control Mapping
- **Activation Toggle**:
  - **Double-Press Play/Pause** (Default): Turn pointer ON / OFF instantly without leaving your current app.
  - Also configurable to: Double-Press Volume Down, Double-Press Mute, Long-Press Menu, or Double-Press Fast-Forward.
- **Click / Select**:
  - Short press on **OK / Center Button**: Dispatches an instant tap at the pointer tip.
  - Long press (> 500ms): Dispatches a long-press or hold gesture.
- **Scroll Mode**:
  - Press **Menu** or **Info**: Toggles Scroll Mode (where D-pad Up/Down triggers real vertical swipes to scroll long lists or web pages).
  - Alternatively, use **Fast-Forward / Rewind** or **Channel Up / Down** for instant page scrolling.
- **Quick Dismiss**:
  - Pressing the **Back Button** automatically hides the mouse cursor and restores standard remote navigation without exiting your current app.

### 3. 🎨 In-App Dashboard & Settings
- **Live Status Card**: Direct indicator showing if Accessibility Service is active, with a 1-click button to launch TV Accessibility Settings (`Settings.ACTION_ACCESSIBILITY_SETTINGS`).
- **Interactive Playground**: Test mouse movement, button clicks, checkboxes, and scroll actions inside the app before switching to other apps.
- **Customization Options**:
  - **Pointer Speed**: Slow (8px), Normal (16px), Fast (24px), Turbo (36px).
  - **Pointer Size**: Small (28dp), Standard (40dp), Large (56dp), Extra Large (72dp).
  - **Color Palette**: Neon Cyan, Arctic White, Matrix Green, Amber Gold, Vivid Purple.
  - **Inactivity Auto-Hide**: 10s, 20s, 30s, or Never.

---

## 📦 Ready-to-Install APK

The pre-built APK is located at:
```
/Users/sekhar/Downloads/apk/TV_Mouse_Pointer.apk
```

---

## 📲 How to Install & Setup on Android TV / Fire TV

### Option A: Using ADB directly
```bash
adb connect <TV_IP_ADDRESS>:5555
adb install -r TV_Mouse_Pointer.apk
```

### Option B: Using ATV ADB Commander
If you have **ATV ADB Commander** installed on your phone:
1. Open ATV ADB Commander on your phone.
2. Connect to your TV over Wi-Fi.
3. Sideload `TV_Mouse_Pointer.apk` to your TV with one tap.

---

## ⚙️ Enabling the Accessibility Service on TV

Android TV requires granting Accessibility permission so the app can draw the cursor over other apps and simulate clicks:
1. Open **TV Mouse Pointer** on your TV.
2. Tap **"Open TV Accessibility Settings"** (or go to `Settings > System > Accessibility` on your TV).
3. Find **TV Mouse Pointer Service** and toggle it **ON**.
4. Double-press the **Play/Pause** button on your remote to test the cursor!
