package com.mrzgaming.ezbox

data class EzSession(
    val id: String,
    var name: String,
    var resolution: String = "960x540",
    var wineVariant: String = "wine-staging", // "wine-staging" atau "wine"
    var lastUsed: Long = 0L
)
