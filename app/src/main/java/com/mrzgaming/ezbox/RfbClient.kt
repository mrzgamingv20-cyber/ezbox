package com.mrzgaming.ezbox

import android.graphics.Bitmap
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

class RfbClient(private val host: String, private val port: Int, private val password: String) {

    private lateinit var socket: Socket
    private lateinit var input: DataInputStream
    private lateinit var output: DataOutputStream
    private val writeLock = Any()

    var width: Int = 0
    var height: Int = 0
    var bitsPerPixel: Int = 32

    lateinit var bitmap: Bitmap
        private set

    fun connect(): Boolean {
        try {
            socket = Socket(host, port)
            input = DataInputStream(socket.getInputStream())
            output = DataOutputStream(socket.getOutputStream())

            val serverVersion = ByteArray(12)
            input.readFully(serverVersion)
            Log.d("RfbClient", "Server version: ${String(serverVersion)}")

            val clientVersion = "RFB 003.008\n"
            output.write(clientVersion.toByteArray())
            output.flush()

            val numTypes = input.readUnsignedByte()
            if (numTypes == 0) {
                Log.e("RfbClient", "Server rejected connection")
                return false
            }
            val types = ByteArray(numTypes)
            input.readFully(types)

            val chosenType = if (types.contains(2.toByte())) 2 else 1
            output.writeByte(chosenType)
            output.flush()

            if (chosenType == 2) {
                if (!doVncAuth()) return false
            }

            val securityResult = input.readInt()
            if (securityResult != 0) {
                Log.e("RfbClient", "Authentication failed")
                return false
            }

            output.writeByte(1)
            output.flush()

            width = input.readUnsignedShort()
            height = input.readUnsignedShort()

            bitsPerPixel = input.readUnsignedByte()
            input.skipBytes(15)

            val nameLength = input.readInt()
            val nameBytes = ByteArray(nameLength)
            input.readFully(nameBytes)
            Log.d("RfbClient", "Connected to desktop '${String(nameBytes)}' (${width}x${height})")

            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            setPixelFormat()
            setEncodings()

            return true
        } catch (e: Exception) {
            Log.e("RfbClient", "Connection failed: ${e.message}")
            return false
        }
    }

    private fun doVncAuth(): Boolean {
        val challenge = ByteArray(16)
        input.readFully(challenge)

        val response = desEncryptChallenge(challenge, password)
        output.write(response)
        output.flush()
        return true
    }

    private fun desEncryptChallenge(challenge: ByteArray, password: String): ByteArray {
        val key = ByteArray(8)
        val pwBytes = password.toByteArray()
        for (i in 0 until 8) {
            key[i] = if (i < pwBytes.size) reverseBits(pwBytes[i]) else 0
        }

        val cipher = javax.crypto.Cipher.getInstance("DES/ECB/NoPadding")
        val keySpec = javax.crypto.spec.SecretKeySpec(key, "DES")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec)
        return cipher.doFinal(challenge)
    }

    private fun reverseBits(b: Byte): Byte {
        var v = b.toInt() and 0xFF
        var result = 0
        for (i in 0 until 8) {
            result = (result shl 1) or (v and 1)
            v = v shr 1
        }
        return result.toByte()
    }

    // RGB565: 16-bit, jauh lebih hemat bandwidth dibanding 32-bit RGBA
    private fun setPixelFormat() {
        synchronized(writeLock) {
            output.writeByte(0)
            output.write(ByteArray(3))
            output.writeByte(16)  // bits-per-pixel
            output.writeByte(16)  // depth
            output.writeByte(0)   // big-endian-flag = false
            output.writeByte(1)   // true-color-flag = true
            output.writeShort(31)  // red-max (5 bit)
            output.writeShort(63)  // green-max (6 bit)
            output.writeShort(31)  // blue-max (5 bit)
            output.writeByte(11)   // red-shift
            output.writeByte(5)    // green-shift
            output.writeByte(0)    // blue-shift
            output.write(ByteArray(3))
            output.flush()
        }
        bitsPerPixel = 16
    }

    private fun setEncodings() {
        synchronized(writeLock) {
            output.writeByte(2)
            output.writeByte(0)
            output.writeShort(3) // jumlah encoding: Hextile + CopyRect + Raw
            output.writeInt(5)   // Hextile (prioritas utama, hemat bandwidth)
            output.writeInt(1)   // CopyRect
            output.writeInt(0)   // Raw (fallback)
            output.flush()
        }
    }

    fun requestFramebufferUpdate(incremental: Boolean) {
        synchronized(writeLock) {
            output.writeByte(3)
            output.writeByte(if (incremental) 1 else 0)
            output.writeShort(0)
            output.writeShort(0)
            output.writeShort(width)
            output.writeShort(height)
            output.flush()
        }
    }

    fun readServerMessage(): Boolean {
        val messageType = input.readUnsignedByte()
        when (messageType) {
            0 -> return readFramebufferUpdate()
            1 -> readColourMapEntry()
            2 -> { /* Bell - abaikan */ }
            3 -> readServerCutText()
            else -> Log.e("RfbClient", "Unknown server message type: $messageType")
        }
        return false
    }

    private fun readFramebufferUpdate(): Boolean {
        input.skipBytes(1)
        val numRects = input.readUnsignedShort()

        for (i in 0 until numRects) {
            val x = input.readUnsignedShort()
            val y = input.readUnsignedShort()
            val w = input.readUnsignedShort()
            val h = input.readUnsignedShort()
            val encodingType = input.readInt()

            when (encodingType) {
                0 -> readRawRectangle(x, y, w, h)
                1 -> readCopyRect(x, y, w, h)
                5 -> readHextile(x, y, w, h)
                else -> {
                    Log.e("RfbClient", "Unsupported encoding: $encodingType")
                    return false
                }
            }
        }
        return true
    }

    // RGB565: 2 byte per pixel (bukan 4 seperti sebelumnya)
    private fun readRawRectangle(x: Int, y: Int, w: Int, h: Int) {
        val pixels = IntArray(w * h)
        val rowBytes = ByteArray(w * 2)

        for (row in 0 until h) {
            input.readFully(rowBytes)
            for (col in 0 until w) {
                val offset = col * 2
                // Little-endian 16-bit: byte rendah dulu
                val raw = (rowBytes[offset].toInt() and 0xFF) or ((rowBytes[offset + 1].toInt() and 0xFF) shl 8)

                val r5 = (raw shr 11) and 0x1F
                val g6 = (raw shr 5) and 0x3F
                val b5 = raw and 0x1F

                // Skala balik ke 8-bit per channel
                val red = (r5 * 255) / 31
                val green = (g6 * 255) / 63
                val blue = (b5 * 255) / 31

                pixels[row * w + col] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }

        bitmap.setPixels(pixels, 0, w, x, y, w, h)
    }

    // CopyRect: server cuma kasih koordinat sumber, kita copy dari bitmap yang sudah ada (murah, tanpa transfer pixel)
    private fun readCopyRect(x: Int, y: Int, w: Int, h: Int) {
        val srcX = input.readUnsignedShort()
        val srcY = input.readUnsignedShort()

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, srcX, srcY, w, h)
        bitmap.setPixels(pixels, 0, w, x, y, w, h)
    }

    // Hextile: rectangle dibagi tile 16x16, tiap tile bisa "raw" atau "solid warna" (jauh lebih hemat untuk area polos/UI)
    private fun readHextile(x: Int, y: Int, w: Int, h: Int) {
        var bgColor = 0
        var fgColor = 0

        var tileY = y
        while (tileY < y + h) {
            val tileHeight = minOf(16, y + h - tileY)
            var tileX = x
            while (tileX < x + w) {
                val tileWidth = minOf(16, x + w - tileX)
                val subEncoding = input.readUnsignedByte()

                when {
                    subEncoding and 0x01 != 0 -> {
                        // Raw tile
                        val pixels = IntArray(tileWidth * tileHeight)
                        val rowBytes = ByteArray(tileWidth * 2)
                        for (row in 0 until tileHeight) {
                            input.readFully(rowBytes)
                            for (col in 0 until tileWidth) {
                                pixels[row * tileWidth + col] = rgb565ToArgb(rowBytes, col * 2)
                            }
                        }
                        bitmap.setPixels(pixels, 0, tileWidth, tileX, tileY, tileWidth, tileHeight)
                    }
                    else -> {
                        if (subEncoding and 0x02 != 0) {
                            val c = ByteArray(2)
                            input.readFully(c)
                            bgColor = rgb565ToArgb(c, 0)
                        }
                        if (subEncoding and 0x04 != 0) {
                            val c = ByteArray(2)
                            input.readFully(c)
                            fgColor = rgb565ToArgb(c, 0)
                        }

                        // Fill tile dengan bgColor dulu
                        val pixels = IntArray(tileWidth * tileHeight) { bgColor }

                        if (subEncoding and 0x08 != 0) {
                            val numSubrects = input.readUnsignedByte()
                            val useForeground = subEncoding and 0x10 == 0

                            for (s in 0 until numSubrects) {
                                val color = if (useForeground) {
                                    fgColor
                                } else {
                                    val c = ByteArray(2)
                                    input.readFully(c)
                                    rgb565ToArgb(c, 0)
                                }
                                val xy = input.readUnsignedByte()
                                val wh = input.readUnsignedByte()
                                val sx = (xy shr 4) and 0x0F
                                val sy = xy and 0x0F
                                val sw = ((wh shr 4) and 0x0F) + 1
                                val sh = (wh and 0x0F) + 1

                                for (row in sy until minOf(sy + sh, tileHeight)) {
                                    for (col in sx until minOf(sx + sw, tileWidth)) {
                                        pixels[row * tileWidth + col] = color
                                    }
                                }
                            }
                        }

                        bitmap.setPixels(pixels, 0, tileWidth, tileX, tileY, tileWidth, tileHeight)
                    }
                }

                tileX += 16
            }
            tileY += 16
        }
    }

    private fun rgb565ToArgb(bytes: ByteArray, offset: Int): Int {
        val raw = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
        val r5 = (raw shr 11) and 0x1F
        val g6 = (raw shr 5) and 0x3F
        val b5 = raw and 0x1F
        val red = (r5 * 255) / 31
        val green = (g6 * 255) / 63
        val blue = (b5 * 255) / 31
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun readColourMapEntry() {
        input.skipBytes(5)
        val numColours = input.readUnsignedShort()
        input.skipBytes(numColours * 6)
    }

    private fun readServerCutText() {
        input.skipBytes(7)
        val length = input.readInt()
        input.skipBytes(length)
    }

    fun sendPointerEvent(x: Int, y: Int, buttonMask: Int) {
        synchronized(writeLock) {
            output.writeByte(5)
            output.writeByte(buttonMask)
            output.writeShort(x)
            output.writeShort(y)
            output.flush()
        }
    }

    fun sendKeyEvent(keysym: Int, down: Boolean) {
        synchronized(writeLock) {
            output.writeByte(4)
            output.writeByte(if (down) 1 else 0)
            output.writeShort(0)
            output.writeInt(keysym)
            output.flush()
        }
    }

    fun close() {
        try {
            socket.close()
        } catch (e: Exception) {
            Log.e("RfbClient", "Error closing socket: ${e.message}")
        }
    }
}
