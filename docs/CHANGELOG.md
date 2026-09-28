# Changelog

All notable changes to EZBox. Version numbers follow `app/build.gradle`
(`versionName` / `versionCode`).

## v1.3 (versionCode 4)

### Added
- Custom VNC key panel replacing the 17 hard-coded extra keys — build your own key row:
  - Drag with one finger to reposition, pinch two fingers to resize, long-press a key to edit it
  - Preset key groups: Windows, ABC, Symbols, Functions, Navigation
  - Keys are saved in `SharedPreferences` (`vnc_extra_keys`) and survive restarts
- Theme Engine: selectable accent themes via the Theme screen
- Enter / Stop desktop buttons directly on the Home screen
- Open Source Licenses screen (Menu / About / Licenses); About now shows the real app version
- Hamburger drawer menu (Tutorial, Terminal, Theme, About)

### Changed
- Full UI redesign: Winlator-style drawer + Primer Dark glass, varied palette, restored nav labels
- Every emoji icon replaced with Lucide vector drawables
- Bottom navigation trimmed to four tabs: Home, Files, Store, Settings (Apps tab removed; fixes the launch crash caused by the BottomNavigationView 5-item cap)
- All user-facing strings translated to English

### Fixed
- Custom keys rendering off-screen (position stored in pixels while the model uses dp) and new keys overlapping existing keys (first free grid slot is now chosen)
- Crash entering the desktop (notification channel created too early in the lifecycle)
- Termux `RUN_COMMAND` failures now surface an error dialog instead of failing silently
- VSync toggle is wired to the renderer; the quality toggle no longer pretends to do something
- Store duplicate icon mapping (`iconRes` collision)
- Teardown coroutine cancellation, broken layouts, dead tabs, and remaining crash/leak paths

### Build & docs
- Official Gradle 8.5 wrapper replaces the hand-rolled one
- GitHub Actions workflow now provisions the Android SDK itself, so every push produces a downloadable debug APK
- Version bumped to 1.3 (versionCode 4)
- Added `THIRD_PARTY_NOTICES.md` plus bundled noVNC license texts (MPL 2.0) — see the License section in the main README

## v1.2
- Added: Container-based navigation with RecyclerView (Home tab)
- Added: Apps Library tab with grid of installed Linux apps
- Added: Files Manager tab with storage browser
- Added: Home uptime and RAM usage indicator (ring progress view)
- Added: Store category filter pills with count badges
- Added: Typing preview bar above the soft keyboard
- Added: Graphics settings (VSync, High Quality Rendering)
- Added: Gamepad/controller support (DPAD, analog sticks, A/B/X/Y buttons)
- Added: VNC status notification channel (running/idle)
- Added: Splash screen animation (scale + fade)
- Added: Real PNG logos for store apps (Wine, Box64, Firefox, GIMP, VLC)
- Added: Permission explainer dialogs before requesting Termux / storage access
- UI Refresh: Glassmorphism design across all layouts, colors, themes, drawables
- Refactored: `EZBoxNotificationManager` for notification lifecycle

## v1.1
- Fixed: Software Store now performs actual `pkg install` via Termux with real verification polling
- Fixed: Shortcut intent "Launch Desktop" now properly triggers desktop launch
- Fixed: NoVncActivity uses password from settings instead of hardcoded value
- Fixed: Background stop only kills desktop when app truly leaves foreground
- Refactored: Extracted Termux command execution to `TermuxCommand` helper
- Added: ProGuard rules and release build configuration
- Fixed: Deprecated `Environment.getExternalStoragePublicDirectory` usage
- Fixed: RFB protocol `skipFully` negative skip handling

## v1.0 "La Peace"
