# Usage

Day-to-day guide for running and controlling the EZBox desktop.

## Starting the desktop

1. Open **EZBox** and tap **Launch Environment** on the Home screen.
2. Wait for the Termux backend to set up the desktop (first launch takes longer as packages are installed).
3. Once the status shows **Running**, tap **Open Desktop** to enter the VNC view.

## Inside the desktop

- Tap to click (**Direct** mode), or drag to move the cursor (**Trackpad** mode) — configurable in Settings.
- Tap the keyboard button to toggle the virtual keyboard.
- Tap the key-panel button to open the custom key editor:
  - **Add key** places a new key
  - drag a key to move it, pinch two fingers to resize it
  - long-press a key to change its key code or delete it
  - **Delete all keys** clears the row
- Tap the clipboard button to sync your Android clipboard into the desktop.
- Tap the camera button to take a screenshot of the desktop.
- Tap **Stop** to shut the desktop down.

## Tabs

| Tab | What it does |
|---|---|
| Home | Launch/enter the desktop, uptime and RAM gauge, Enter / Stop buttons |
| Files | Browse phone storage |
| Store | Install Linux apps (Wine, Box64, Firefox, GIMP, VLC, File Manager) with category filters |
| Settings | Desktop environment, resolution, mouse mode, VNC options |

## Drawer (hamburger menu, top-right)

**Tutorial**, **Theme**, **Terminal**, **Stop Desktop**, and **About**
(the About dialog contains the bundled open source licenses).

## Settings reference

| Option | Description |
|---|---|
| Desktop Environment | XFCE4 or LXQt |
| Width / Height | Custom desktop resolution via +/- stepper |
| Mouse Mode | Direct (tap = absolute position) / Trackpad (drag = relative cursor) |
| VNC Password | Regenerated automatically on every launch |
| VSync | Toggle vertical sync in the renderer |
| Render Quality | Toggle higher-quality rendering |
| Keep Awake | Keeps the screen on while the desktop is active |
| Auto-stop Background | Automatically kills the desktop process when the app goes to background |
| View-Only | Render the desktop without sending input back |
| Disable Clipboard | Turn off clipboard sync entirely |
| Low Bandwidth | Reduce traffic over the VNC link |
| Reset Desktop | Wipes all desktop data (`~/.ezos`) and starts fresh |

See also: [Development](DEVELOPMENT.md) for building and debugging.
