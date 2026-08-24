package com.mrzgaming.ezbox

data class StorePackage(
    val name: String,
    val description: String,
    val pkgNames: List<String>,
    val checkBinary: String,
    val icon: String,
    val colorRes: Int
)
