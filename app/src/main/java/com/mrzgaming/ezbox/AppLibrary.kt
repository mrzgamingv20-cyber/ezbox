package com.mrzgaming.ezbox

data class AppInfo(
    val id: String = "",
    val name: String = "",
    val iconRes: Int = R.drawable.ic_launcher_foreground,
    val packageName: String = "",
    val category: String = "Other",
    val isInstalled: Boolean = false
)

class AppLibrary(private val context: android.content.Context) {
    fun getInstalledApps(): List<AppInfo> {
        return listOf(
            AppInfo("firefox", "Firefox", R.drawable.ic_launcher_foreground, "firefox", "Internet", true),
            AppInfo("libreoffice", "LibreOffice", R.drawable.ic_launcher_foreground, "libreoffice", "Office", true),
            AppInfo("thunderbird", "Thunderbird", R.drawable.ic_launcher_foreground, "thunderbird", "Internet", false),
            AppInfo("vlc", "VLC Media Player", R.drawable.ic_launcher_foreground, "vlc", "Multimedia", true),
            AppInfo("steam", "Steam", R.drawable.ic_launcher_foreground, "steam", "Games", false),
            AppInfo("wine", "Wine", R.drawable.ic_launcher_foreground, "wine", "System", true),
            AppInfo("box64", "Box64", R.drawable.ic_launcher_foreground, "box64", "System", true),
            AppInfo("gimp", "GIMP", R.drawable.ic_launcher_foreground, "gimp", "Graphics", false),
            AppInfo("inkscape", "Inkscape", R.drawable.ic_launcher_foreground, "inkscape", "Graphics", false),
            AppInfo("chromium", "Chromium", R.drawable.ic_launcher_foreground, "chromium", "Internet", true),
            AppInfo("code", "VS Code", R.drawable.ic_launcher_foreground, "code", "Development", false),
            AppInfo("mpv", "mpv", R.drawable.ic_launcher_foreground, "mpv", "Multimedia", true),
            AppInfo("git", "Git", R.drawable.ic_launcher_foreground, "git", "Development", true),
            AppInfo("neovim", "Neovim", R.drawable.ic_launcher_foreground, "neovim", "Development", false),
            AppInfo("obs", "OBS Studio", R.drawable.ic_launcher_foreground, "obs", "Multimedia", false)
        ).filter { it.isInstalled }
    }
}
