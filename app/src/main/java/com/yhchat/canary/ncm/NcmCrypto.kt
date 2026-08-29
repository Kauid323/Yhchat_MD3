package com.yhchat.canary.ncm

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云音乐 API 加解密工具类
 * 涵盖 LinuxAPI (AES-128-ECB), EAPI (AES-128-ECB + MD5), WEAPI (AES-128-CBC + RSA)
 */
object NcmCrypto {

    private const val LINUX_API_KEY = "rFgB&h#%2?^eDg:Q"
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val WEAPI_PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val WEAPI_IV = "0102030405060708"
    private const val BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val RSA_PUBLIC_KEY_PEM =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"

    // ─── MD5 ─────────────────────────────────────────────────────────────────

    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytesToHex(digest).lowercase()
    }

    // ─── LinuxAPI (AES-128-ECB) ──────────────────────────────────────────────

    /**
     * LinuxAPI 请求加密
     */
    fun encryptLinuxApi(text: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val keySpec = SecretKeySpec(LINUX_API_KEY.toByteArray(StandardCharsets.UTF_8), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encrypted = cipher.doFinal(text.toByteArray(StandardCharsets.UTF_8))
        return bytesToHex(encrypted).uppercase()
    }

    /**
     * LinuxAPI 响应解密
     */
    fun decryptLinuxApi(hexString: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            val keySpec = SecretKeySpec(LINUX_API_KEY.toByteArray(StandardCharsets.UTF_8), "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val bytes = hexToBytes(hexString)
            val decrypted = cipher.doFinal(bytes)
            String(decrypted, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            hexString
        }
    }

    // ─── EAPI (AES-128-ECB + MD5) ────────────────────────────────────────────

    /**
     * EAPI 请求加密
     */
    fun encryptEApi(url: String, jsonObjectString: String): String {
        val message = "nobody${url}use${jsonObjectString}md5forencrypt"
        val digest = md5(message)
        val data = "$url-36cd479b6b5-$jsonObjectString-36cd479b6b5-$digest"

        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val keySpec = SecretKeySpec(EAPI_KEY.toByteArray(StandardCharsets.UTF_8), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encrypted = cipher.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return bytesToHex(encrypted).uppercase()
    }

    /**
     * EAPI 响应解密
     */
    fun decryptEApi(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val rawStr = String(bytes, StandardCharsets.UTF_8).trim()
        if (rawStr.startsWith("{") && rawStr.endsWith("}")) {
            return rawStr
        }
        return try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            val keySpec = SecretKeySpec(EAPI_KEY.toByteArray(StandardCharsets.UTF_8), "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decrypted = cipher.doFinal(bytes)
            String(decrypted, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            try {
                if (rawStr.matches(Regex("^[0-9a-fA-F]+$"))) {
                    val hexBytes = hexToBytes(rawStr)
                    val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
                    val keySpec = SecretKeySpec(EAPI_KEY.toByteArray(StandardCharsets.UTF_8), "AES")
                    cipher.init(Cipher.DECRYPT_MODE, keySpec)
                    val decrypted = cipher.doFinal(hexBytes)
                    return String(decrypted, StandardCharsets.UTF_8)
                }
            } catch (_: Exception) {}
            rawStr
        }
    }

    /**
     * EAPI 响应 Hex 字符串解密
     */
    fun decryptEApiHex(hexString: String): String {
        return try {
            val bytes = hexToBytes(hexString.trim())
            decryptEApi(bytes)
        } catch (_: Exception) {
            hexString
        }
    }

    // ─── WEAPI (AES-128-CBC + RSA) ───────────────────────────────────────────

    fun encryptWeApi(text: String): Pair<String, String> {
        // 生成 16 位随机密钥
        val secretKey = buildString {
            repeat(16) {
                append(BASE62.random())
            }
        }

        // 第 1 次 AES-128-CBC 加密 (presetKey, iv)
        val firstEnc = aesCbcEncrypt(text, WEAPI_PRESET_KEY, WEAPI_IV)
        // 第 2 次 AES-128-CBC 加密 (secretKey, iv)
        val params = aesCbcEncrypt(firstEnc, secretKey, WEAPI_IV)
        // RSA 加密逆序的 secretKey
        val encSecKey = rsaEncrypt(secretKey.reversed())

        return Pair(params, encSecKey)
    }

    private fun aesCbcEncrypt(text: String, key: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(iv.toByteArray(StandardCharsets.UTF_8))
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(text.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun rsaEncrypt(text: String): String {
        return try {
            val keyBytes = Base64.decode(RSA_PUBLIC_KEY_PEM, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)

            val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val padded = ByteArray(128)
            val textBytes = text.toByteArray(StandardCharsets.UTF_8)
            System.arraycopy(textBytes, 0, padded, 128 - textBytes.size, textBytes.size)
            val encrypted = cipher.doFinal(padded)
            bytesToHex(encrypted).lowercase()
        } catch (_: Exception) {
            ""
        }
    }

    // ─── 辅助函数 ────────────────────────────────────────────────────────────

    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789ABCDEF".toCharArray()
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = hexArray[v ushr 4]
            hexChars[i * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
