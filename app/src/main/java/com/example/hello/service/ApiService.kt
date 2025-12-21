package com.example.hello.service

import android.content.Context
import android.util.Log
import com.example.hello.model.Course
import com.example.hello.model.CourseResponse
import com.example.hello.utils.DeviceIdUtil
import com.example.hello.utils.SignUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.*
import java.util.*
import javax.net.ssl.*

class ApiService(private val context: Context) {
    // 创建信任所有证书的OkHttpClient
    private val client = OkHttpClient.Builder()
        .sslSocketFactory(createSSLSocketFactory(), object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        .hostnameVerifier { _, _ -> true }
        .build()
    private val gson = Gson()
    
    // 创建信任所有证书的SSL Socket Factory
    private fun createSSLSocketFactory(): SSLSocketFactory {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }), SecureRandom())
        return sslContext.socketFactory
    }
    
    // 鉴权相关变量
    var token: String? = null
    private var expireAt: Long? = null
    private val APP_KEY = "buaa-classhopper-android"

    interface OnLoginListener {
        fun onSuccess(userId: String, sessionId: String, realName: String, academyName: String)
        fun onFailure(error: String)
    }

    interface OnCourseScheduleListener {
        fun onSuccess(courses: List<Course>)
        fun onEmpty()
        fun onFailure(error: String)
    }

    interface OnSignListener {
        fun onSuccess()
        fun onFailure(error: String)
    }
    
    // 鉴权相关接口和数据模型
    interface OnAuthListener {
        fun onSuccess(token: String, expireAt: Long)
        fun onFailure(error: String)
    }
    
    data class AuthResponse(
        @SerializedName("code") val code: Int,
        @SerializedName("msg") val msg: String,
        @SerializedName("data") val data: AuthData?
    )
    
    data class AuthData(
        @SerializedName("token") val token: String,
        @SerializedName("expireAt") val expireAt: String
    )
    
    data class AuthRequest(
        @SerializedName("appKey") val appKey: String,
        @SerializedName("timestamp") val timestamp: Long,
        @SerializedName("signature") val signature: String,
        @SerializedName("appUUID") val appUUID: String
    )
    
    // 检查token是否有效
    private fun isTokenValid(): Boolean {
        if (token.isNullOrEmpty() || expireAt == null) {
            return false
        }
        return System.currentTimeMillis() < expireAt!!
    }
    
    // 获取鉴权token的方法
    fun getAuthToken(listener: OnAuthListener) {
        // 构建请求参数
        val timestamp = System.currentTimeMillis()
        val appUUID = DeviceIdUtil.getPersistentUUID(context)
        
        // 生成签名
        val params = mapOf(
            "appKey" to APP_KEY,
            "timestamp" to timestamp.toString(),
            "appUUID" to appUUID
        )
        val signature = SignUtils.generateSignature(params)
        
        val authRequest = AuthRequest(
            appKey = APP_KEY,
            timestamp = timestamp,
            signature = signature,
            appUUID = appUUID
        )
        
        // 构建请求体
        val jsonBody = gson.toJson(authRequest)
        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), jsonBody)
        
        // 构建请求
        val authUrl = "https://101.42.43.228/api/user/third-auth"
        val request = Request.Builder()
            .url(authUrl)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        
        // 发送请求
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure("获取鉴权token失败: ${e.message}")
                e.printStackTrace()
            }
            
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    Log.i("login", responseData.toString())
                    val authResponse = gson.fromJson(responseData, AuthResponse::class.java)
                    
                    if (authResponse.code == 1 && authResponse.data != null) {
                        // 保存token和过期时间
                        token = authResponse.data.token
                        // 解析ISO 8601格式的时间字符串
                        try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
                            val date = sdf.parse(authResponse.data.expireAt.substringBeforeLast('Z'))
                            expireAt = date?.time ?: 0
                        } catch (e: Exception) {
                            // 如果解析失败，使用当前时间+2小时作为过期时间
                            expireAt = System.currentTimeMillis() + 2 * 60 * 60 * 1000
                            e.printStackTrace()
                        }
                        listener.onSuccess(authResponse.data.token, expireAt!!)
                    } else {
                        listener.onFailure("获取鉴权token失败: ${authResponse.msg}")
                    }
                } catch (e: Exception) {
                    listener.onFailure("解析鉴权响应失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        })
    }

    fun login(id: String, listener: OnLoginListener) {
        // 先确保有有效的token
        if (!isTokenValid()) {
            getAuthToken(object : OnAuthListener {
                override fun onSuccess(newToken: String, newExpireAt: Long) {
                    // token获取成功后，重新调用login方法
                    login(id, listener)
                }
                
                override fun onFailure(error: String) {
                    listener.onFailure(error)
                }
            })
            return
        }
        
        val loginUrl = "https://iclass.buaa.edu.cn:8346/app/user/login.action".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("password", "")
            ?.addQueryParameter("phone", id)
            ?.addQueryParameter("userLevel", "1")
            ?.addQueryParameter("verificationType", "2")
            ?.addQueryParameter("verificationUrl", "")
            ?.build()
            ?.toString() ?: return

        val loginRequest = Request.Builder()
            .url(loginUrl)
            .get()
            .addHeader("Authorization", "$token")
            .build()

        client.newCall(loginRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure("登录失败: ${e.message}")
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val loginData = response.body?.string()
                    val loginJson = gson.fromJson(loginData, JsonObject::class.java)
                    val resultObject = loginJson.getAsJsonObject("result")
                    val userId = resultObject.get("id").asString
                    val sessionId = resultObject.get("sessionId").asString
                    val realName = resultObject.get("realName").asString
                    val academyName = resultObject.get("academyName").asString
                    listener.onSuccess(userId, sessionId, realName, academyName)
                } catch (e: Exception) {
                    listener.onFailure("登录失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        })
    }

    fun getCourseSchedule(userId: String, sessionId: String, dateStr: String, listener: OnCourseScheduleListener) {
        // 先确保有有效的token
        if (!isTokenValid()) {
            getAuthToken(object : OnAuthListener {
                override fun onSuccess(newToken: String, newExpireAt: Long) {
                    // token获取成功后，重新调用getCourseSchedule方法
                    getCourseSchedule(userId, sessionId, dateStr, listener)
                }
                
                override fun onFailure(error: String) {
                    listener.onFailure(error)
                }
            })
            return
        }
        
        val scheduleUrl = "https://iclass.buaa.edu.cn:8346/app/course/get_stu_course_sched.action".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("dateStr", dateStr)
            ?.addQueryParameter("id", userId)
            ?.build()
            ?.toString() ?: return

        val scheduleRequest = Request.Builder()
            .url(scheduleUrl)
            .addHeader("sessionId", sessionId)
            .addHeader("Authorization", "Bearer ${token}")
            .get()
            .build()

        client.newCall(scheduleRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure("获取课表失败: ${e.message}")
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val scheduleData = response.body?.string()
                try {
                    scheduleData?.let {
                        val courseResponse = gson.fromJson(it, CourseResponse::class.java)
                        // 检查STATUS是否为2，如果是则表示没有课程数据
                        if (courseResponse.STATUS == "2" || courseResponse.result.isNullOrEmpty()) {
                            listener.onEmpty()
                        } else {
                            val courses = courseResponse.result ?: emptyList()
                            if (courses.isNotEmpty()) {
                                listener.onSuccess(courses)
                            } else {
                                listener.onEmpty()
                            }
                        }
                    }
                } catch (e: Exception) {
                    listener.onFailure("解析数据失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        })
    }

    fun signClass(studentId: String, courseId: String, listener: OnSignListener) {
        // 首先登录获取sessionId
        login(studentId, object : OnLoginListener {
            override fun onSuccess(userId: String, sessionId: String, realName: String, academyName: String) {
                // 构建签到请求
                val timestamp = System.currentTimeMillis()
                val formBody = FormBody.Builder()
                    .add("id", userId)
                    .build()

                val signUrl = "http://iclass.buaa.edu.cn:8081/app/course/stu_scan_sign.action".toHttpUrlOrNull()?.newBuilder()
                    ?.addQueryParameter("courseSchedId", courseId)
                    ?.addQueryParameter("timestamp", timestamp.toString())
                    ?.build()
                    ?.toString() ?: return

                val signRequest = Request.Builder()
                        .url(signUrl)
                        .post(formBody)
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .addHeader("Authorization", "Bearer ${token}")
                        .build()

                client.newCall(signRequest).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        listener.onFailure("签到失败: ${e.message}")
                        e.printStackTrace()
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val signData = response.body?.string()
                        try {
                            signData?.let {
                                val signJson = gson.fromJson(it, JsonObject::class.java)
                                if (signJson.has("result")) {
                                    listener.onSuccess()
                                } else {
                                    listener.onFailure("签到失败: ${signJson.get("msg")?.asString ?: "未知错误"}")
                                }
                            }
                        } catch (e: Exception) {
                            listener.onFailure("解析响应失败: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                })
            }

            override fun onFailure(error: String) {
                listener.onFailure(error)
            }
        })
    }
}