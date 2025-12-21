package com.example.hello.utils

import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.*

object SignUtils {
    private const val HMAC_SHA256 = "HmacSHA256"
    private val SECRET = com.example.hello.BuildConfig.APP_SECRET
    
    /**
     * 生成签名
     * @param params 参与签名的参数
     * @return 签名后的十六进制字符串
     */
    fun generateSignature(params: Map<String, String>): String {
        try {
            // 1. 字典排序并拼接：key1=value1&key2=value2
            val baseString = TreeMap(params).entries
                .filter { it.value != null }
                .joinToString("&") { "${it.key}=${it.value}" }
            Log.i("APP_SECRET", SECRET)
            
            // 2. 初始化 HmacSHA256
            val hmacSHA256 = Mac.getInstance(HMAC_SHA256)
            val secretKey = SecretKeySpec(
                SECRET.toByteArray(StandardCharsets.UTF_8), HMAC_SHA256
            )
            hmacSHA256.init(secretKey)
            
            // 3. 计算哈希并转为 Hex
            val hashBytes = hmacSHA256.doFinal(baseString.toByteArray(StandardCharsets.UTF_8))
            return bytesToHex(hashBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }
    
    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}