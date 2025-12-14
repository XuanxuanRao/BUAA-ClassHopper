package com.example.hello.service

import android.content.Context
import com.example.hello.model.Course
import com.example.hello.model.CourseResponse
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

class ApiService(private val context: Context) {
    private val client = OkHttpClient()
    private val gson = Gson()

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

    fun login(id: String, listener: OnLoginListener) {
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
        val scheduleUrl = "https://iclass.buaa.edu.cn:8346/app/course/get_stu_course_sched.action".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("dateStr", dateStr)
            ?.addQueryParameter("id", userId)
            ?.build()
            ?.toString() ?: return

        val scheduleRequest = Request.Builder()
            .url(scheduleUrl)
            .addHeader("sessionId", sessionId)
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