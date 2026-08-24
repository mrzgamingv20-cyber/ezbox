package com.mrzgaming.ezbox

data class EzSession(
    val id: String,
    var name: String,
    var resolution: String = "960x540",
    var wineVariant: String = "wine-staging",
    var displayNum: Int = 1, // :1, :2, :3... -> port 5901, 5902, 5903...
    var lastUsed: Long = 0L
) {
    val vncPort: Int get() = 5900 + displayNum
}
