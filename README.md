
![Logo](Tak%20berjudul48_20260824185844.png)


# EZBox



![Build Status](https://github.com/mrzgamingv20-cyber/ezbox/actions/workflows/android-build.yml/badge.svg)




![Release](https://img.shields.io/github/v/release/mrzgamingv20-cyber/ezbox)




![License](https://img.shields.io/github/license/mrzgamingv20-cyber/ezbox)




![Platform](https://img.shields.io/badge/platform-Android%20ARM64-blue)



**EZBox** is an Android app that runs a full Linux desktop environment (XFCE4) directly on your phone using a Termux backend — similar to Winlator, but built on native Termux instead of proot-distro, chroot, or full virtualization.

- **Package:** `com.mrzgaming.ezbox`
- **Version:** `1.0 "La Peace"`
- **Target:** Android ARM64 (aarch64), minSdk 24, targetSdk 34

---

## ✨ Features

- Persistent XFCE4 Linux desktop (auto-resumes from the last session)
- Custom VNC client written entirely in Kotlin — no NDK, no native code
- Built-in software store: Wine, Box64, Firefox, GIMP, VLC, File Manager
- Configurable resolution, VNC password, and mouse mode (direct / trackpad)
- Keep-awake and auto-stop-in-background toggles
- Clipboard sync (Android → Desktop)
- In-app desktop screenshot

---

## 📦

1. Install [Termux](https://github.com/termux/termux-app) from github (**not** the Play Store version — it's outdated and incompatible).
2. Download the latest `EZBox-debug.apk` from the [Releases](../../releases) page.
3. Install the APK manually on your device.
4. Open Termux once, then open EZBox — grant the **RUN_COMMAND** and **All Files Access** permissions when prompted.

---

## 🚀 Usage

1. Open **EZBox** and tap **Launch Environment** on the Home screen.
2. Wait for the Termux backend to set up the desktop (first launch takes longer as packages are installed).
3. Once the status shows **Running**, tap **Open Desktop** to enter the VNC view.
4. Inside the desktop:
   - Tap = click (**Direct** mode), or drag = move cursor (**Trackpad** mode) — configurable in Settings.
   - Tap ⌨ to show the virtual keyboard and extra keys (Ctrl, Alt, Esc, Tab, arrows).
   - Tap 📋 to sync your Android clipboard into the desktop.
   - Tap 📷 to take a screenshot of the desktop.
5. Install software from the **Store** tab (Wine, Box64, Firefox, etc.).
6. Use the **Terminal** tab to jump into Termux directly or view debug logs.

---

## ⚙️ Settings

| Option | Description |
|---|---|
| Resolution | 800x480 / 960x540 / 1280x720 / 1600x900 |
| Mouse Mode | Direct (tap = absolute position) / Trackpad (drag = relative cursor) |
| VNC Password | Regenerated automatically on every launch |
| Keep Awake | Keeps the screen on while the desktop is active |
| Auto-stop Background | Automatically kills the desktop process when the app goes to background |
| Reset Desktop | Wipes all desktop data (`~/.ezos`) and starts fresh |

---

## 🐞 Debugging

EZBox doesn't rely on `adb` for debugging. Instead:

- Crash logs are automatically saved to your **Download** folder (`/storage/emulated/0/Download/`)
- Non-crash activity logs are written to `ezbox_debug.log` in the same folder

> **Note:** always confirm the installed APK matches the latest commit/build before reporting an issue.

---

## 🛠️ Building from Source (via Termux)

This project is developed entirely from Termux — no PC, Android Studio, or NDK required.

⚠️ Notes
EZBox does not bundle Termux — it must be installed separately.
Because it's built on Termux rather than a full virtualization/proot solution, it's significantly lighter on resources.
Wine/Box64 application support is still under active deve

# No License

This project is intentionally provided without a formal license.

You are free to use, modify, and build upon this project for your own purposes. However, you may not claim the original work as your own, remove or misrepresent its original authorship, or present the project as if you created it from scratch.

Feel free to fork it.
Feel free to modify it.
Feel free to build something new from it.

Just remember:

«Use it. Change it. Build on it. But don't claim it.»

The code is open to exploration and modification, while its original authorship remains respected.
