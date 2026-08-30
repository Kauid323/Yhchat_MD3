package com.yhchat.canary.utils

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * RSA 加密工具类
 * 用于加密 WebDAV 挂载点请求的密钥和 IV
 * 完全匹配 Python 示例的加密方式
 */
object RSAEncryptionUtil {
    
    private const val PUBLIC_KEY_URL = "https://chat.yhchat.com/assets/key/apps_public.pem"
    
    // 硬编码的公钥，与 Python 示例完全一致
    private const val HARDCODED_PUBLIC_KEY = """-----BEGIN RSA PUBLIC KEY-----
MIICCgKCAgEA5MSOx8O11qDYdmR40FUs3a0gjdEzQOfHJFVlSilg83sbl65D3alh
SDfP1h52dbr8m1XmQkjUaTCXfAGdN2p3M6wR6H7pniuHjSzXyPq7ZhmXxFa9dNeR
YDgePFVlLzBYEklYWa2YQ+bu2QRU3h2I94Go91vWVL9KEFe2fi1sfaycyU8h5DS5
D7f3SAtg1L2kcLU+2kfzF5XTyXJUlo0DkdV38BXq0gPqURiEscBRM5K5WF73xJfc
rUcPfDSp1OP8itTNPdgEUC8H1tEPnWhMC7vDNPxuGZ2dGXhedMuO9KW/QmdZ9qi1
5W+ZXdUQdKmTo/V8Z5gDxjWW3/LC6/PexS9HeIyuoLgYWd1GtOcl19FhvipM2Wuv
UjJvwUOqlyPa/MR8e5z5P2J4DEd74QSHaCuNHHDOZMuJWtNGcirXpzo0a41rwpfz
lo4SrzberFL1dl361OewJkqq4fg5dfGgGZcPTxZ+WxVWpmMSlimrpRNcZNy8+orn
iRRhVTW6cXvaku2HlSZGvI+7eoHIYaE0YcOzMzdODTKYl33FSbRRIn2ly0bfqoMd
192qmGAkPa7eqdI0FZSjHmRxc2DEXOq9A6BpJGq0zyVhoyGvfVc88qAh4gwGzvx/
yQGy3WJ+xqP1aUJardDi1g5VPLp0jQcg7k0QP98NfxhdOb2jiH0ClkcCAwEAAQ==
-----END RSA PUBLIC KEY-----"""
    
    /**
     * 生成随机字节数组
     */
    fun generateRandomBytes(length: Int): ByteArray {
        return SecureRandom().generateSeed(length)
    }
    
    /**
     * 从 URL 获取公钥（在IO线程执行）
     */
    suspend fun getPublicKey(): ByteArray? = withContext(Dispatchers.IO) {
        return@withContext try {
            android.util.Log.d("RSAEncryptionUtil", "开始获取公钥: $PUBLIC_KEY_URL")
            
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(PUBLIC_KEY_URL)
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                android.util.Log.d("RSAEncryptionUtil", "公钥获取成功，大小: ${bytes?.size} 字节")
                bytes
            } else {
                android.util.Log.w("RSAEncryptionUtil", "获取公钥失败: HTTP ${response.code}，使用硬编码公钥")
                // 如果获取失败，使用硬编码的公钥
                HARDCODED_PUBLIC_KEY.toByteArray(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            android.util.Log.w("RSAEncryptionUtil", "获取公钥失败，使用硬编码公钥", e)
            // 如果获取失败，使用硬编码的公钥
            HARDCODED_PUBLIC_KEY.toByteArray(Charsets.UTF_8)
        }
    }
    
    /**
     * 使用 RSA 公钥加密数据（在计算线程执行）
     * 完全匹配 Python 示例：使用 PKCS1v15 填充
     * @param publicKeyBytes PEM 格式的公钥字节数组
     * @param dataToEncrypt 要加密的数据
     * @return Base64 编码的加密数据，失败返回 null
     */
    suspend fun rsaEncrypt(publicKeyBytes: ByteArray, dataToEncrypt: ByteArray): String? = withContext(Dispatchers.Default) {
        return@withContext try {
            // 转换为字符串
            val pemContent = String(publicKeyBytes, Charsets.UTF_8)
            android.util.Log.d("RSAEncryptionUtil", "原始公钥内容长度: ${pemContent.length}")
            
            // 解析公钥
            val publicKey = parseRSAPublicKey(pemContent) ?: return@withContext null
            
            android.util.Log.d("RSAEncryptionUtil", "公钥创建成功，算法: ${publicKey.algorithm}")
            
            // 创建加密器，使用 PKCS1Padding 匹配 Python 的 PKCS1v15
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            
            android.util.Log.d("RSAEncryptionUtil", "使用加密算法: ${cipher.algorithm}")
            
            // 加密数据
            val encryptedData = cipher.doFinal(dataToEncrypt)
            
            android.util.Log.d("RSAEncryptionUtil", "加密成功，原始数据长度: ${dataToEncrypt.size}，加密数据长度: ${encryptedData.size}")
            
            // Base64 编码（NO_WRAP 匹配 Python）
            Base64.encodeToString(encryptedData, Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("RSAEncryptionUtil", "RSA 加密失败", e)
            null
        }
    }
    
    /**
     * 解析 RSA 公钥，支持 PKCS#1 和 X.509 格式
     */
    private fun parseRSAPublicKey(pemContent: String): java.security.PublicKey? {
        return try {
            // 清理 PEM 内容
            var cleanContent = pemContent
                .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                .replace("-----END RSA PUBLIC KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")
                .replace("\t", "")
            
            // 移除所有非Base64字符
            cleanContent = cleanContent.replace(Regex("[^A-Za-z0-9+/=]"), "")
            
            android.util.Log.d("RSAEncryptionUtil", "清理后的公钥内容长度: ${cleanContent.length}")
            
            if (cleanContent.isEmpty()) {
                android.util.Log.e("RSAEncryptionUtil", "公钥内容为空")
                return null
            }
            
            // Base64 解码
            val keyBytes = Base64.decode(cleanContent, Base64.DEFAULT)
            android.util.Log.d("RSAEncryptionUtil", "解码后的公钥字节长度: ${keyBytes.size}")
            
            // 尝试多个Provider，避免Android Keystore
            val providers = listOf("AndroidOpenSSL", "Conscrypt", "BC")
            
            for (providerName in providers) {
                try {
                    android.util.Log.d("RSAEncryptionUtil", "尝试使用Provider: $providerName")
                    val keyFactory = KeyFactory.getInstance("RSA", providerName)
                    
                    // 首先尝试 X.509 格式
                    try {
                        android.util.Log.d("RSAEncryptionUtil", "尝试X.509格式解析")
                        val x509KeySpec = X509EncodedKeySpec(keyBytes)
                        return keyFactory.generatePublic(x509KeySpec)
                    } catch (e: Exception) {
                        android.util.Log.w("RSAEncryptionUtil", "X.509格式解析失败，尝试PKCS#1格式", e)
                    }
                    
                    // 尝试 PKCS#1 格式转换为 X.509
                    try {
                        android.util.Log.d("RSAEncryptionUtil", "尝试PKCS#1格式解析")
                        val x509Bytes = convertPkcs1ToX509(keyBytes)
                        val x509KeySpec = X509EncodedKeySpec(x509Bytes)
                        return keyFactory.generatePublic(x509KeySpec)
                    } catch (e: Exception) {
                        android.util.Log.w("RSAEncryptionUtil", "Provider $providerName PKCS#1格式解析失败", e)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("RSAEncryptionUtil", "Provider $providerName 不可用", e)
                    continue
                }
            }
            
            android.util.Log.e("RSAEncryptionUtil", "所有Provider都解析失败")
            return null
        } catch (e: Exception) {
            android.util.Log.e("RSAEncryptionUtil", "公钥解析失败", e)
            null
        }
    }
    
    /**
     * 将PKCS#1格式转换为X.509格式
     */
    private fun convertPkcs1ToX509(pkcs1Bytes: ByteArray): ByteArray {
        android.util.Log.d("RSAEncryptionUtil", "开始PKCS#1到X.509转换，输入长度: ${pkcs1Bytes.size}")
        
        // RSA公钥的OID序列: 1.2.840.113549.1.1.1 (RSA加密)
        val rsaOidSequence = byteArrayOf(
            0x30, 0x0d, // SEQUENCE, 长度13
            0x06, 0x09, // OID, 长度9
            0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01, // RSA OID
            0x05, 0x00  // NULL
        )
        
        // 计算BIT STRING的长度 (PKCS#1数据 + 1个未使用位字节)
        val bitStringLength = pkcs1Bytes.size + 1
        val bitStringLengthBytes = encodeDerLength(bitStringLength)
        
        // 计算总的SEQUENCE长度
        val totalContentLength = rsaOidSequence.size + 1 + bitStringLengthBytes.size + bitStringLength
        val totalLengthBytes = encodeDerLength(totalContentLength)
        
        // 构建完整的X.509公钥
        val x509Bytes = ByteArray(1 + totalLengthBytes.size + totalContentLength)
        var offset = 0
        
        // 外层SEQUENCE标记
        x509Bytes[offset++] = 0x30
        
        // 外层SEQUENCE长度
        System.arraycopy(totalLengthBytes, 0, x509Bytes, offset, totalLengthBytes.size)
        offset += totalLengthBytes.size
        
        // RSA OID序列
        System.arraycopy(rsaOidSequence, 0, x509Bytes, offset, rsaOidSequence.size)
        offset += rsaOidSequence.size
        
        // BIT STRING标记
        x509Bytes[offset++] = 0x03
        
        // BIT STRING长度
        System.arraycopy(bitStringLengthBytes, 0, x509Bytes, offset, bitStringLengthBytes.size)
        offset += bitStringLengthBytes.size
        
        // 未使用的位数 (总是0)
        x509Bytes[offset++] = 0x00
        
        // PKCS#1公钥数据
        System.arraycopy(pkcs1Bytes, 0, x509Bytes, offset, pkcs1Bytes.size)
        
        android.util.Log.d("RSAEncryptionUtil", "X.509转换完成，输出长度: ${x509Bytes.size}")
        return x509Bytes
    }
    
    /**
     * 编码DER长度字段
     */
    private fun encodeDerLength(length: Int): ByteArray {
        return if (length < 128) {
            // 短格式
            byteArrayOf(length.toByte())
        } else if (length < 256) {
            // 长格式，1字节
            byteArrayOf(0x81.toByte(), length.toByte())
        } else if (length < 65536) {
            // 长格式，2字节
            byteArrayOf(0x82.toByte(), (length shr 8).toByte(), (length and 0xFF).toByte())
        } else {
            // 长格式，3字节 (理论上足够了)
            byteArrayOf(
                0x83.toByte(),
                (length shr 16).toByte(),
                ((length shr 8) and 0xFF).toByte(),
                (length and 0xFF).toByte()
            )
        }
    }
    
    /**
     * 准备 WebDAV 挂载点请求的加密参数（在后台线程执行）
     * @return Triple<encryptKey, encryptIv, rawKeyPair>，失败返回 null
     * rawKeyPair用于后续解密WebDAV密码
     */
    suspend fun prepareEncryptionParams(): Triple<String, String, Pair<ByteArray, ByteArray>>? = withContext(Dispatchers.IO) {
        return@withContext try {
            android.util.Log.d("RSAEncryptionUtil", "开始准备加密参数")
            
            // 1. 获取公钥
            val publicKeyBytes = getPublicKey() ?: return@withContext null
            
            // 2. 生成随机密钥和 IV（16 字节），匹配 Python 示例
            val encryptKeyRaw = generateRandomBytes(16)
            val encryptIvRaw = generateRandomBytes(16)
            
            android.util.Log.d("RSAEncryptionUtil", "生成的密钥长度: ${encryptKeyRaw.size}, IV长度: ${encryptIvRaw.size}")
            android.util.Log.d("RSAEncryptionUtil", "密钥: ${Base64.encodeToString(encryptKeyRaw, Base64.NO_WRAP)}")
            android.util.Log.d("RSAEncryptionUtil", "IV: ${Base64.encodeToString(encryptIvRaw, Base64.NO_WRAP)}")
            
            // 3. 使用公钥加密
            val encryptKey = rsaEncrypt(publicKeyBytes, encryptKeyRaw) ?: return@withContext null
            val encryptIv = rsaEncrypt(publicKeyBytes, encryptIvRaw) ?: return@withContext null
            
            android.util.Log.d("RSAEncryptionUtil", "加密参数准备成功")
            android.util.Log.d("RSAEncryptionUtil", "加密后密钥长度: ${encryptKey.length}")
            android.util.Log.d("RSAEncryptionUtil", "加密后IV长度: ${encryptIv.length}")
            
            // 4. 返回加密后的参数和原始密钥对
            Triple(encryptKey, encryptIv, Pair(encryptKeyRaw, encryptIvRaw))
        } catch (e: Exception) {
            android.util.Log.e("RSAEncryptionUtil", "准备加密参数失败", e)
            null
        }
    }
    
    /**
     * 使用AES解密WebDAV密码
     * 匹配 Python 示例的 AES/CBC/PKCS7 解密
     * @param encryptedPassword Base64编码的加密密码
     * @param key AES密钥
     * @param iv AES初始化向量
     * @return 解密后的密码，失败返回null
     */
    suspend fun decryptWebDAVPassword(
        encryptedPassword: String,
        key: ByteArray,
        iv: ByteArray
    ): String? = withContext(Dispatchers.Default) {
        return@withContext try {
            if (encryptedPassword.isEmpty()) {
                android.util.Log.d("RSAEncryptionUtil", "密码为空，返回空字符串")
                return@withContext ""
            }
            
            android.util.Log.d("RSAEncryptionUtil", "开始解密WebDAV密码")
            android.util.Log.d("RSAEncryptionUtil", "加密密码长度: ${encryptedPassword.length}")
            android.util.Log.d("RSAEncryptionUtil", "密钥长度: ${key.size}, IV长度: ${iv.size}")
            
            // 1. Base64解码
            val ciphertext = Base64.decode(encryptedPassword, Base64.DEFAULT)
            android.util.Log.d("RSAEncryptionUtil", "解码后密文长度: ${ciphertext.size}")
            
            // 2. 创建AES解密器，使用CBC模式和PKCS5Padding (等同于PKCS7)
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            
            // 3. 解密
            val decrypted = cipher.doFinal(ciphertext)
            android.util.Log.d("RSAEncryptionUtil", "解密后数据长度: ${decrypted.size}")
            
            // 4. 转换为字符串
            val password = String(decrypted, Charsets.UTF_8)
            android.util.Log.d("RSAEncryptionUtil", "解密成功，密码长度: ${password.length}")
            
            password
        } catch (e: Exception) {
            android.util.Log.e("RSAEncryptionUtil", "AES解密失败", e)
            null
        }
    }
}