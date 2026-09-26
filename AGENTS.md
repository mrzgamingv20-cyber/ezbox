# EZBox — build & install conventions

## WAJIB: install setelah setiap build sukses

Setiap `gh run` selesai `conclusion=success`, **langsung download artifact dan install ke HP**.
Jangan berhenti di "build hijau" — user harus bisa langsung tes.

```bash
D=$(mktemp -d) && gh run download <RUN_ID> -n EZBox-debug -D "$D" >/dev/null 2>&1
cp "$(find $D -name '*.apk'|head -1)" /storage/emulated/0/Download/EZBox-1.2-debug.apk
am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d "file:///storage/emulated/0/Download/EZBox-1.2-debug.apk" >/dev/null 2>&1
am start -a android.intent.action.VIEW \
  -d "file:///storage/emulated/0/Download/EZBox-1.2-debug.apk" \
  -t application/vnd.android.package-archive
```

Lalu **bilang ke user untuk tap "Install"** di layar installer.

### Kenapa tidak bisa install sendiri
`pm install` dan `cmd package install` dari Termux **selalu gagal** — dua sebab:
- Termux jalan sebagai `u0_a899`, bukan shell/root, jadi tidak punya `INSTALL_PACKAGES`
- SELinux: `system_server` tidak boleh membaca `app_data_file` milik Termux

`pm install` juga menyuruh file ditaruh di `/data/local/tmp`, tapi tetap gagal karena
masalah permission-nya, bukan lokasi file. Satu-satunya jalur adalah melempar intent
ke installer sistem, dan itu butuh tap manusia.

### Verifikasi benar-benar terinstall
```bash
P=$(pm path com.mrzgaming.ezbox | sed 's/package://')
md5sum "$P" | cut -d' ' -f1          # harus sama dengan md5 APK yang di-build
```

## Repo

- **Canonical: `/data/data/com.termux/files/home/ezbox`** — hanya ini yang di-commit/push.
- `/data/data/com.termux/files/home/github-repos/ezbox` = clone cadangan. **Jangan pernah
  commit/push dari sana**; pernah bentrok dengan remote. Sync pakai `fetch` + `reset --hard`.

## Build

- Termux tidak punya Android SDK → build hanya lewat GitHub Actions.
- `gh` sudah terautentikasi sebagai `mrzgamingv20-cyber`.
- Workflow: `.github/workflows/android-build.yml`, artifact `EZBox-debug`.
- **NDK tidak diperlukan** — tidak ada native code (`ndk`, `externalNativeBuild`, CMake,
  C/C++, `jniLibs`).

## Backend Termux

EZBox menjalankan desktop lewat `com.termux.RUN_COMMAND`. Termux **menolak** perintah dari
app luar kecuali `~/.termux/termux.properties` memuat:

```
allow-external-apps = true
```

Sudah diset di perangkat ini. Kalau user reinstall Termux / clears data, set ulang — tanpa
itu desktop tidak akan start dan EZBox menampilkan dialog yang menyuruh mengaktifkan property tersebut.

## Jebakan yang sudah pernah ditemukan

- **`BottomNavigationView` max 5 item.** `NavigationBarMenu.addInternal` melempar
  `IllegalArgumentException` di item ke-6 → activity crash saat inflate. Menu dan
`when (item.itemId)` di `MainActivity` harus sinkron, dan tidak boleh ada `R.id.nav_*`
yang tidak ada di `bottom_nav_menu.xml`.
- **`activity_main.xml` root harus `FrameLayout`.** Dulu `LinearLayout` vertikal:
  `layout_gravity` vertikal diabaikan AOSP, jadi navbar + hamburger tergeser keluar layar.
- **`activity_vnc.xml` root harus `RelativeLayout`.** Dulu `FrameLayout` dengan param
  RelativeLayout → param dibuang diam-diam, semua kontrol VNC numpuk di top-left.
- **Akses `/data/data/com.termux` mustahil dari EZBox** (mode 0700, owner berbeda).
  Berarti EZBox tidak bisa `File.exists()` di sana. Verifikasi install Store memakai marker
  file di `getExternalFilesDir()` — satu-satunya tempat yang bisa diakses dua app.
- **`Channel.CONFLATED` menyimpan satu elemen** — pasangan press/release yang dikirim
  beruntan akan hilang press-nya. Pakai `UNLIMITED` untuk event yang harus berpasangan.
- **Ubah resource = build bisa gagal diam-diam** kalau ada referensi ke resource yang
  tidak ada (mis. `@color/nav_icon_tint`). Cache lama menutupi ini.
