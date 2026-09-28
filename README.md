![Logo](Tak%20berjudul48_20260824185844.png)

# EZBox

![Build Status](https://github.com/mrzgamingv20-cyber/ezbox/actions/workflows/android-build.yml/badge.svg)

![Release](https://img.shields.io/github/v/release/mrzgamingv20-cyber/ezbox)

![License](https://img.shields.io/github/license/mrzgamingv20-cyber/ezbox)

![Platform](https://img.shields.io/badge/platform-Android%20ARM64-blue)

**EZBox** is an Android app that runs a full Linux desktop environment (XFCE4 / LXQt) directly on your phone using a Termux backend — similar to Winlator, but built on native Termux instead of proot-distro, chroot, or full virtualization.

| | |
|---|---|
| **Package** | `com.mrzgaming.ezbox` |
| **Version** | `1.3` (versionCode 4) |
| **Target** | Android ARM64 (aarch64), minSdk 24, targetSdk 34 |

## Features

- Persistent XFCE4 / LXQt Linux desktop that resumes your last session
- Custom VNC client written entirely in Kotlin (`RfbClient`) — no NDK, no native code
- Customizable VNC key panel (drag, resize, and assign your own keys)
- Software store with category filters: Wine, Box64, Firefox, GIMP, VLC, File Manager
- Configurable resolution, VNC password, and mouse mode (direct / trackpad)
- View-Only, clipboard sync, screenshots, and low-bandwidth tuning
- Keep-awake and auto-stop-in-background toggles
- Theme Engine with selectable accent themes
- Desktop uptime and RAM gauge on the Home screen

## Download

1. Install [Termux](https://github.com/termux/termux-app) from GitHub (**not** the Play Store version — it's outdated and incompatible).
2. Download the latest `EZBox-debug.apk` from the [Releases](../../releases) page (or grab the `EZBox-debug` artifact from the latest [Actions](../../actions) run).
3. Install the APK manually on your device.
4. Open Termux once, then open EZBox — grant the **RUN_COMMAND** and **All Files Access** permissions when prompted.
5. Tap **Launch Environment**, wait for the status to show **Running**, then tap **Open Desktop**.

## Documentation

| Document | Contents |
|---|---|
| [Usage](docs/USAGE.md) | Starting the desktop, in-desktop controls, tabs, settings reference |
| [Changelog](docs/CHANGELOG.md) | What changed in every release |
| [Development](docs/DEVELOPMENT.md) | Building from Termux, Termux backend setup, debugging |
| [Third-party notices](THIRD_PARTY_NOTICES.md) | Bundled components and their licenses |

## License

EZBox's own source code is intentionally provided **without a formal license**.

You are free to use, modify, and build upon this project for your own purposes. However, you may not claim the original work as your own, remove or misrepresent its original authorship, or present the project as if you created it from scratch.

> Use it. Change it. Build on it. But don't claim it.

Bundled third-party components keep their own licenses:

| Component | Where | License |
|---|---|---|
| [noVNC](https://github.com/novnc/noVNC) | `app/src/main/assets/novnc/` | MPL 2.0 |
| [pako](https://github.com/nodeca/pako) | `app/src/main/assets/novnc/vendor/pako/` | MIT |

Full license texts are bundled in the app and readable from **Menu / About / Licenses**. Details in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
