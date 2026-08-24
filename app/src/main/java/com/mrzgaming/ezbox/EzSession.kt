package com.mrzgaming.ezbox

data class EzSession(
    val id: String,
    var name: String,
    var resolution: String = "960x540",
    var wineVariant: String = "wine-staging",
    var displayNum: Int = 1,
    var password: String = "ezbox123",
    var mouseMode: String = "direct", // "direct" (tap = posisi absolut) atau "trackpad" (drag relatif)
    var lastUsed: Long = 0L
) {
    val vncPort: Int get() = 5900 + displayNum
}
