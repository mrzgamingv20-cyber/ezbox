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

    // Minta server kirim pixel dalam format 32bpp RGBA (biar gampang dipetakan ke Bitmap Android)
    private fun setPixelFormat() {
        output.writeByte(0) // message type: SetPixelFormat
        output.write(ByteArray(3)) // padding
        output.writeByte(32) // bits-per-pixel
        output.writeByte(24) // depth
        output.writeByte(0)  // big-endian-flag = false
        output.writeByte(1)  // true-color-flag = true
        output.writeShort(255) // red-max
        output.writeShort(255) // green-max
        output.writeShort(255) // blue-max
        output.writeByte(16) // red-shift
        output.writeByte(8)  // green-shift
        output.writeByte(0)  // blue-shift
        output.write(ByteArray(3)) // padding
        output.flush()
    }

    // Cuma pakai Raw encoding (type 0) dulu - paling simpel, tidak perlu decompress
    private fun setEncodings() {
        output.writeByte(2) // message type: SetEncodings
        output.writeByte(0) // padding
        output.writeShort(1) // number-of-encodings
        output.writeInt(0)   // Raw encoding
        output.flush()
    }

    fun requestFramebufferUpdate(incremental: Boolean) {
        output.writeByte(3) // message type: FramebufferUpdateRequest
        output.writeByte(if (incremental) 1 else 0)
        output.writeShort(0) // x
        output.writeShort(0) // y
        output.writeShort(width)
        output.writeShort(height)
        output.flush()
    }

    // Baca satu pesan dari server, return true kalau ada update framebuffer yang berhasil diproses
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
        input.skipBytes(1) // padding
        val numRects = input.readUnsignedShort()

        for (i in 0 until numRects) {
            val x = input.readUnsignedShort()
            val y = input.readUnsignedShort()
            val w = input.readUnsignedShort()
            val h = input.readUnsignedShort()
            val encodingType = input.readInt()

            if (encodingType == 0) {
                readRawRectangle(x, y, w, h)
            } else {
                Log.e("RfbClient", "Unsupported encoding: $encodingType")
                return false
            }
        }
        return true
    }

    private fun readRawRectangle(x: Int, y: Int, w: Int, h: Int) {
        val pixels = IntArray(w * h)
        val rowBytes = ByteArray(w * 4)

        for (row in 0 until h) {
            input.readFully(rowBytes)
            for (col in 0 until w) {
                val offset = col * 4
                val blue = rowBytes[offset].toInt() and 0xFF
                val green = rowBytes[offset + 1].toInt() and 0xFF
                val red = rowBytes[offset + 2].toInt() and 0xFF
                pixels[row * w + col] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }

        bitmap.setPixels(pixels, 0, w, x, y, w, h)
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

    // Kirim event pointer (mouse/touch): mask bit 0 = tombol kiri
    fun sendPointerEvent(x: Int, y: Int, buttonMask: Int) {
        output.writeByte(5)
        output.writeByte(buttonMask)
        output.writeShort(x)
        output.writeShort(y)
        output.flush()
    }

    // Kirim event keyboard
    fun sendKeyEvent(keysym: Int, down: Boolean) {
        output.writeByte(4)
        output.writeByte(if (down) 1 else 0)
        output.writeShort(0) // padding
        output.writeInt(keysym)
        output.flush()
    }

    fun close() {
        try {
            socket.close()
        } catch (e: Exception) {
            Log.e("RfbClient", "Error closing socket: ${e.message}")
        }
    }
}
