package com.mrzgaming.ezbox

data class StorePackage(
    val name: String,
    val description: String,
    val pkgNames: List<String>,
    val checkBinary: String,
    val icon: String,       // emoji fallback, dipakai kalau iconRes null
    val colorRes: Int,
    val iconRes: Int? = null // drawable PNG logo asli, prioritas dipakai kalau ada
)
