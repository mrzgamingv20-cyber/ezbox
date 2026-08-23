package com.mrzgaming.ezbox

/**
 * Model data untuk satu entri package yang bisa diinstall lewat Store fragment.
 *
 * @param name          Nama tampilan (mis. "Wine")
 * @param description   Deskripsi singkat yang muncul di card
 * @param pkgNames      Daftar nama paket Termux (pkg) yang akan diinstall,
 *                      bisa lebih dari satu (mis. beberapa dependency sekaligus)
 * @param checkBinary   Nama binary yang bisa dipakai untuk cek apakah package
 *                      ini sudah terinstall (via `command -v <checkBinary>`),
 *                      belum dipakai di StoreFragment sekarang tapi disiapkan
 *                      untuk fitur status "Installed" nanti
 */
data class StorePackage(
    val name: String,
    val description: String,
    val pkgNames: List<String>,
    val checkBinary: String
)
