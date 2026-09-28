# Development

Building and debugging EZBox.

## Building from source (via Termux)

This project is developed entirely from Termux — no PC, Android Studio, or NDK required.

1. Clone the repository inside Termux:

   ```
   git clone https://github.com/mrzgamingv20-cyber/ezbox.git
   cd ezbox
   ```

2. Build a debug APK:

   ```
   ./gradlew assembleDebug
   ```

3. The APK lands in `app/build/outputs/apk/debug/`.

Every push to `main` also builds automatically through GitHub Actions
(`.github/workflows/android-build.yml`); download the `EZBox-debug` artifact
from the run page if you don't want to build locally.

### Notes

- **No NDK.** There is no native code — no `ndk`, `externalNativeBuild`, CMake, C/C++, or `jniLibs`.
- EZBox does not bundle Termux — it must be installed separately.
- Because it's built on Termux rather than a full virtualization/proot solution, it's significantly lighter on resources.
- Wine/Box64 application support is still under active development.

## Termux backend requirement

EZBox starts the desktop through `com.termux.RUN_COMMAND`. Termux refuses
commands from external apps unless `~/.termux/termux.properties` contains:

```
allow-external-apps = true
```

If this is missing (for example after a Termux reinstall or a data clear), the
desktop will not start and EZBox shows a dialog telling you to enable it.

## Debugging

EZBox doesn't rely on `adb` for debugging. Instead:

- Crash logs are automatically saved to your **Download** folder (`/storage/emulated/0/Download/`)
- Non-crash activity logs are written to `ezbox_debug.log` in the same folder

> **Note:** always confirm the installed APK matches the latest commit/build before reporting an issue.

## Layout constraints

These broke the app before — keep them when editing layouts:

- `activity_main.xml` root must stay a `FrameLayout`. With a vertical
  `LinearLayout`, AOSP ignores vertical `layout_gravity` and the navbar and
  hamburger get pushed off screen.
- `activity_vnc.xml` root must stay a `RelativeLayout`. With a `FrameLayout`
  root, `RelativeLayout.LayoutParams` are silently dropped and every VNC
  control stacks into the top-left corner.
- `BottomNavigationView` supports at most 5 items; a sixth throws
  `IllegalArgumentException` while inflating the menu. Keep
  `bottom_nav_menu.xml` and the `when (item.itemId)` in `MainActivity` in sync.

See also: [Usage](USAGE.md) and the [Changelog](CHANGELOG.md).
