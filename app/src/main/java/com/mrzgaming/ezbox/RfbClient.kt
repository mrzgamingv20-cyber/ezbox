package com.mrzgaming.ezbox

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

    fun close() {
        try {
            socket.close()
        } catch (e: Exception) {
            Log.e("RfbClient", "Error closing socket: ${e.message}")
        }
    }
}
