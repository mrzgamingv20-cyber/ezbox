package com.mrzgaming.ezbox

import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Satu tombol di panel tombol custom.
 *
 * Tiga mode powerfully, dipilih lewat field yang terisi:
 *  - char != null  -> ketik karakter itu (tunnel lewat [VncActivity.sendModifiedChar],
 *                    jadi Shift/Ctrl/Alt dari keyboard Android ikut dihormati)
 *  - mod  != null  -> modifier lengket (Ctrl/Alt/Shift), nyala-mati saat diketuk
 *  - selain itu    -> tekan keysym X11 sekali
 */
data class ExtraKey(
    val label: String,
    val keysym: Int = 0,
    val char: String? = null,
    val icon: String? = null,
    val mod: String? = null,
    val w: Int = 56,
    val h: Int = 44,
    val x: Int = 0,
    val y: Int = 0
)

/** Keysym X11 (XF86 / Latin-1 range) untuk tombol non-karakter. */
object Ks {
    const val BACKSPACE = 0xFF08
    const val TAB = 0xFF09
    const val ENTER = 0xFF0D
    const val ESC = 0xFF1B
    const val SHIFT = 0xFFE1
    const val CTRL = 0xFFE3
    const val CAPS = 0xFFE5
    const val ALT = 0xFFE9
    const val SUPER = 0xFFEB
    const val MENU = 0xFF67
    const val HOME = 0xFF50
    const val LEFT = 0xFF51
    const val UP = 0xFF52
    const val RIGHT = 0xFF53
    const val DOWN = 0xFF54
    const val PGUP = 0xFF55
    const val PGDN = 0xFF56
    const val END = 0xFF57
    const val INSERT = 0xFF63
    const val DEL = 0xFFFF
    const val F1 = 0xFFBE

    fun fkey(n: Int) = F1 + (n - 1)
}

/**
 * Kandidat tombol untuk dialog picker. Tiap grup = satu baris keyboard sungguhan,
 * jadi yang tampil di picker memang bentuk keyboard, bukan daftar nama datar.
 */
object KeyPresets {
    private fun sym(label: String, keysym: Int, icon: String? = null) = ExtraKey(label, keysym, icon = icon)
    private fun ch(c: String) = ExtraKey(c, char = c)
    private fun mod(name: String) = ExtraKey(name, mod = name)

    val groups: List<Pair<String, List<List<ExtraKey>>>> = listOf(
        "Windows" to listOf(
            listOf(sym("Esc", Ks.ESC), sym("Tab", Ks.TAB), sym("Caps", Ks.CAPS), sym("Bksp", Ks.BACKSPACE)),
            listOf(
                sym("Shift", Ks.SHIFT), mod("Ctrl"), sym("Win", Ks.SUPER), mod("Alt"),
                ExtraKey("Space", char = " "), sym("Menu", Ks.MENU), sym("Enter", Ks.ENTER)
            ),
            listOf(
                sym("", Ks.LEFT, "ic_arrow_left"), sym("", Ks.DOWN, "ic_arrow_down"),
                sym("", Ks.UP, "ic_arrow_up"), sym("", Ks.RIGHT, "ic_arrow_right")
            )
        ),
        "ABC" to listOf(
            listOf(ch("Q"), ch("W"), ch("E"), ch("R"), ch("T"), ch("Y"), ch("U"), ch("I"), ch("O"), ch("P")),
            listOf(ch("A"), ch("S"), ch("D"), ch("F"), ch("G"), ch("H"), ch("J"), ch("K"), ch("L")),
            listOf(ch("Z"), ch("X"), ch("C"), ch("V"), ch("B"), ch("N"), ch("M"))
        ),
        "Simbol" to listOf(
            listOf(ch("1"), ch("2"), ch("3"), ch("4"), ch("5"), ch("6"), ch("7"), ch("8"), ch("9"), ch("0")),
            listOf(ch("-"), ch("="), ch("["), ch("]"), ch("\\"), ch(";"), ch("'"), ch(",")),
            listOf(ch("."), ch("/"), ch("`"), ch("~"), ch("_"), ch("+"))
        ),
        "Fungsi" to listOf(
            (1..6).map { sym("F$it", Ks.fkey(it)) },
            (7..12).map { sym("F$it", Ks.fkey(it)) }
        ),
        "Navigasi" to listOf(
            listOf(sym("Ins", Ks.INSERT), sym("Home", Ks.HOME), sym("PgUp", Ks.PGUP)),
            listOf(sym("Del", Ks.DEL), sym("End", Ks.END), sym("PgDn", Ks.PGDN)),
            listOf(sym("Bksp", Ks.BACKSPACE), sym("Tab", Ks.TAB), sym("Enter", Ks.ENTER))
        )
    )
}

/**
 * Panel tombol custom di atas desktop VNC.
 *
 * Kosong secara default - user yang menentukan tombol apa saja yang mau muncul,
 * lalu bebas menggeser, memperbesar, mengganti, dan menghapusnya.
 * State disimpan sebagai JSON di SharedPreferences supaya bertahan antar-sesi.
 */
class ExtraKeysPanel(
    private val activity: androidx.appcompat.app.AppCompatActivity,
    private val prefs: SharedPreferences,
    private val container: FrameLayout,
    private val onPress: (ExtraKey) -> Unit
) {
    private val keys = mutableListOf<ExtraKey>()
    private val activeMods = mutableSetOf<String>()
    private val minW = dp(40)
    private val minH = dp(36)
    private val maxW = dp(240)
    private val maxH = dp(170)
    private val slop = ViewConfiguration.get(activity).scaledTouchSlop

    val isEmpty get() = keys.isEmpty()

    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).roundToInt()
    private fun toDp(px: Float) = (px / activity.resources.displayMetrics.density).roundToInt()

    private fun drawableRes(name: String): Int =
        activity.resources.getIdentifier(name, "drawable", activity.packageName)

    // ---------------------------------------------------------------- persistence

    fun load() {
        val raw = prefs.getString(KEY_PREFS, null) ?: return
        try {
            val arr = JSONArray(raw)
            keys.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                keys.add(
                    ExtraKey(
                        label = o.optString("label"),
                        keysym = o.optInt("keysym"),
                        char = str(o, "char"),
                        icon = str(o, "icon"),
                        mod = str(o, "mod"),
                        w = o.optInt("w", 56),
                        h = o.optInt("h", 44),
                        x = o.optInt("x", 0),
                        y = o.optInt("y", 0)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "gagal baca tombol tersimpan: ${e.message}")
        }
    }

    private fun str(o: JSONObject, k: String): String? {
        val v = o.optString(k, "")
        return if (v.isEmpty()) null else v
    }

    fun save() {
        val arr = JSONArray()
        for (k in keys) {
            arr.put(JSONObject().apply {
                put("label", k.label)
                put("keysym", k.keysym)
                k.char?.let { put("char", it) }
                k.icon?.let { put("icon", it) }
                k.mod?.let { put("mod", it) }
                put("w", k.w)
                put("h", k.h)
                put("x", k.x)
                put("y", k.y)
            })
        }
        prefs.edit().putString(KEY_PREFS, arr.toString()).apply()
    }

    fun clearAll() {
        keys.clear()
        activeMods.clear()
        save()
        render()
    }

    // ---------------------------------------------------------------- render

    fun render() {
        container.removeAllViews()
        keys.forEachIndexed { idx, key ->
            val tv = TextView(activity).apply {
                text = if (key.icon != null) "" else key.label
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                background = ContextCompat.getDrawable(activity, R.drawable.btn_glass_clear)
                if (key.icon != null) {
                    val id = drawableRes(key.icon!!)
                    if (id != 0) setCompoundDrawablesRelativeWithIntrinsicBounds(id, 0, 0, 0)
                }
                if (key.mod != null && key.mod in activeMods) setBackgroundColor(0x66000000)
            }
            container.addView(tv, FrameLayout.LayoutParams(dp(key.w), dp(key.h)))
            tv.x = dp(key.x).toFloat()
            tv.y = dp(key.y).toFloat()
            tv.setOnTouchListener(KeyTouch(idx))
        }
    }

    private fun applySize(v: View, w: Int, h: Int) {
        v.layoutParams = FrameLayout.LayoutParams(w, h)
    }

    private fun clampPos(v: View) {
        val maxX = (container.width - v.width).coerceAtLeast(0).toFloat()
        val maxY = (container.height - v.height).coerceAtLeast(0).toFloat()
        v.x = v.x.coerceIn(0f, maxX)
        v.y = v.y.coerceIn(0f, maxY)
    }

    /** Simpan posisi/ukuran view yang sedang diedit balik ke model. */
    private fun commit(idx: Int, v: View) {
        val k = keys.getOrNull(idx) ?: return
        keys[idx] = k.copy(
            x = toDp(v.x),
            y = toDp(v.y),
            w = toDp(v.width.toFloat()).coerceAtLeast(40),
            h = toDp(v.height.toFloat()).coerceAtLeast(36)
        )
        save()
    }

    // ---------------------------------------------------------------- gestures

    private fun spacing(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        val dx = e.getX(0) - e.getX(1)
        val dy = e.getY(0) - e.getY(1)
        return hypot(dx, dy)
    }

    private inner class KeyTouch(private val idx: Int) : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0f
        private var startY = 0f
        private var startW = 0
        private var startH = 0
        private var lastDist = 0f
        private var moved = false
        private var longPress: Runnable? = null

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX
                    downRawY = e.rawY
                    startX = v.x
                    startY = v.y
                    startW = v.width
                    startH = v.height
                    moved = false
                    lastDist = 0f
                    longPress = Runnable {
                        if (!moved) {
                            moved = true
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            showKeyMenu(idx)
                        }
                    }
                    longPress?.let { v.postDelayed(it, LONG_PRESS_MS) }
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    lastDist = spacing(e)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!moved && (abs(e.rawX - downRawX) > slop || abs(e.rawY - downRawY) > slop)) {
                        moved = true
                        longPress?.let { v.removeCallbacks(it) }
                    }
                    if (e.pointerCount >= 2) {
                        // dua jari = perbesar/perkecil
                        val d = spacing(e)
                        if (lastDist > 8f && d > 8f) {
                            val f = d / lastDist
                            applySize(
                                v,
                                (startW * f).roundToInt().coerceIn(minW, maxW),
                                (startH * f).roundToInt().coerceIn(minH, maxH)
                            )
                            lastDist = d
                        }
                    } else if (moved) {
                        v.x = startX + (e.rawX - downRawX)
                        v.y = startY + (e.rawY - downRawY)
                        clampPos(v)
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPress?.let { v.removeCallbacks(it) }
                    if (e.actionMasked == MotionEvent.ACTION_UP) {
                        if (moved) commit(idx, v) else keys.getOrNull(idx)?.let { onPress(it) }
                    }
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return false
        }
    }

    // ---------------------------------------------------------------- menu + picker

    private fun showKeyMenu(idx: Int) {
        val key = keys.getOrNull(idx) ?: return
        val opts = arrayOf("Ganti tombol", "Hapus tombol", "Besarkan", "Kecilkan")
        AlertDialog.Builder(activity)
            .setTitle(key.label.ifEmpty { "Tombol" })
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> showPicker { picked -> replaceKey(idx, picked) }
                    1 -> removeKey(idx)
                    2 -> scaleKey(idx, 1.25f)
                    3 -> scaleKey(idx, 0.8f)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun replaceKey(idx: Int, picked: ExtraKey) {
        val old = keys.getOrNull(idx) ?: return
        keys[idx] = picked.copy(x = old.x, y = old.y, w = old.w, h = old.h)
        save()
        render()
    }

    private fun removeKey(idx: Int) {
        if (idx !in keys.indices) return
        keys.removeAt(idx)
        save()
        render()
    }

    private fun scaleKey(idx: Int, factor: Float) {
        val k = keys.getOrNull(idx) ?: return
        keys[idx] = k.copy(
            w = (k.w * factor).roundToInt().coerceIn(40, 240),
            h = (k.h * factor).roundToInt().coerceIn(36, 170)
        )
        save()
        render()
    }

    fun addKey(picked: ExtraKey) {
        // tempatkan menumpuk ke bawah supaya tidak menimpa tombol yang sudah ada
        val n = keys.size
        val col = n % 5
        val row = n / 5
        keys.add(
            picked.copy(
                w = picked.w.coerceIn(40, 240),
                h = picked.h.coerceIn(36, 170),
                x = dp(12) + dp(col) * dp(60),
                y = dp(90) + dp(row) * dp(52)
            )
        )
        save()
        render()
    }

    fun showPicker(onPick: (ExtraKey) -> Unit) {
        val v = activity.layoutInflater.inflate(R.layout.dialog_key_picker, null)
        val tabs = v.findViewById<LinearLayout>(R.id.pickerTabs)
        val rows = v.findViewById<LinearLayout>(R.id.pickerRows)
        val dlg = AlertDialog.Builder(activity)
            .setView(v)
            .setNegativeButton("Batal", null)
            .create()

        fun keyButton(k: ExtraKey, wide: Boolean): Button = Button(activity).apply {
            text = if (k.icon != null) "" else k.label
            textSize = 11f
            setTextColor(Color.WHITE)
            background = ContextCompat.getDrawable(activity, R.drawable.btn_glass_clear)
            if (k.icon != null) {
                val id = drawableRes(k.icon!!)
                if (id != 0) setCompoundDrawablesRelativeWithIntrinsicBounds(id, 0, 0, 0)
            }
            setPadding(dp(6), dp(8), dp(6), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(if (wide) 90 else 46), dp(42)).also { lp ->
                lp.marginEnd = dp(3)
                lp.bottomMargin = dp(3)
            }
            setOnClickListener {
                dlg.dismiss()
                onPick(k)
            }
        }

        fun fill(group: Pair<String, List<List<ExtraKey>>>) {
            rows.removeAllViews()
            for (row in group.second) {
                val line = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                for (k in row) {
                    // spasi dan enter dipakai penuh satu baris
                    line.addView(keyButton(k, wide = k.label == "Space" || k.label == "Enter"))
                }
                rows.addView(
                    line,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }

        for (group in KeyPresets.groups) {
            val tab = Button(activity).apply {
                text = group.first
                textSize = 12f
                setTextColor(Color.WHITE)
                background = ContextCompat.getDrawable(activity, R.drawable.btn_glass_clear)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)
                ).also { lp -> lp.marginEnd = dp(4) }
                setOnClickListener { fill(group) }
            }
            tabs.addView(tab)
        }
        fill(KeyPresets.groups.first())
        dlg.show()
    }

    fun toggleMod(name: String) {
        if (name in activeMods) activeMods.remove(name) else activeMods.add(name)
        render()
    }

    fun isModActive(name: String) = name in activeMods

    /** Lepas semua modifier setelah satu karakter terkirim (perilaku tombol Ctrl/Alt lama). */
    fun clearMods() {
        if (activeMods.isEmpty()) return
        activeMods.clear()
        render()
    }

    companion object {
        private const val TAG = "ExtraKeys"
        private const val KEY_PREFS = "vnc_extra_keys"
        private const val LONG_PRESS_MS = 480L
    }
}
