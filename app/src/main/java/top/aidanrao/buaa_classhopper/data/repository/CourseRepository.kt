package top.aidanrao.buaa_classhopper.data.repository

import android.content.Context
import android.util.Log
import top.aidanrao.buaa_classhopper.data.api.FallbackApi
import top.aidanrao.buaa_classhopper.data.api.IclassApi
import top.aidanrao.buaa_classhopper.data.model.Result
import top.aidanrao.buaa_classhopper.data.model.dto.CourseDto
import top.aidanrao.buaa_classhopper.data.model.dto.FallbackCourseDto
import top.aidanrao.buaa_classhopper.data.model.dto.IclassLoginResponse
import top.aidanrao.buaa_classhopper.data.vpn.VpnPreferences
import top.aidanrao.buaa_classhopper.data.vpn.VpnSessionExpiredException
import top.aidanrao.buaa_classhopper.di.NetworkModule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class CourseRepository @Inject constructor(
    @Named(NetworkModule.API_ICLASS_DIRECT) private val iclassDirectApi: IclassApi,
    @Named(NetworkModule.API_ICLASS_VPN) private val iclassVpnApi: IclassApi,
    private val fallbackApi: FallbackApi,
    private val tokenManager: TokenManager,
    private val vpnPreferences: VpnPreferences,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CourseRepository"
        private const val PREFS_NAME = "course_checkin_settings"
        private const val KEY_FALLBACK_ENABLED = "fallback_enabled"
        const val VPN_SESSION_EXPIRED_MESSAGE = "VPN 会话已失效，请到设置中重新通过 SSO 登录"
    }

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun iclassApi(): IclassApi =
        if (vpnPreferences.isVpnEnabled) iclassVpnApi else iclassDirectApi

    suspend fun login(studentId: String): Result<IclassLoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = iclassApi().login(phone = studentId)
                if (response.result != null) {
                    Result.success(response)
                } else {
                    val errorMsg = response.ERRMSG ?: "登录失败"
                    Result.error(Exception(errorMsg), errorMsg)
                }
            } catch (e: VpnSessionExpiredException) {
                Result.error(e, VPN_SESSION_EXPIRED_MESSAGE)
            } catch (e: Exception) {
                Result.error(e, "登录失败: ${e.message}")
            }
        }
    }

    suspend fun getCourseSchedule(
        userId: String,
        sessionId: String,
        dateStr: String
    ): Result<List<CourseDto>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = iclassApi().getCourseSchedule(dateStr, userId, sessionId)
                if (response.status == "2" || response.result.isNullOrEmpty()) {
                    Result.success(emptyList())
                } else {
                    Result.success(response.result)
                }
            } catch (e: VpnSessionExpiredException) {
                Result.error(e, VPN_SESSION_EXPIRED_MESSAGE)
            } catch (e: Exception) {
                if (isFallbackEnabled()) {
                    getCourseScheduleFallback(dateStr)
                } else {
                    Result.error(e, "获取课表失败: ${e.message}")
                }
            }
        }
    }

    suspend fun getCourseScheduleFallback(dateStr: String): Result<List<CourseDto>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = tokenManager.getValidToken() ?: return@withContext Result.error(
                    Exception("No token"),
                    "未获取到授权令牌"
                )

                val formattedDate = if (dateStr.length == 8) {
                    "${dateStr.take(4)}-${dateStr.substring(4, 6)}-${dateStr.substring(6, 8)}"
                } else {
                    dateStr
                }
                
                val response = fallbackApi.getCourseSchedule(formattedDate, token)
                if (response.code != 1) {
                    return@withContext Result.error(Exception(response.msg), response.msg)
                }
                
                val courses = response.data.result.mapNotNull { convertFallbackCourse(it) }
                Result.success(courses)
            } catch (e: Exception) {
                Log.e(TAG, "Fallback API failed", e)
                Result.error(e, "Fallback接口失败: ${e.message}")
            }
        }
    }

    suspend fun signClass(studentId: String, courseId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val loginResult = login(studentId)
                if (loginResult.isError) {
                    // VPN 失效时直接把原始错误抛给用户，不再 fallback
                    val cause = (loginResult as? Result.Error)?.exception
                    if (cause is VpnSessionExpiredException) {
                        return@withContext Result.error(cause, VPN_SESSION_EXPIRED_MESSAGE)
                    }
                    if (isFallbackEnabled()) {
                        return@withContext signClassFallback(courseId)
                    }
                    return@withContext loginResult.map { Unit }
                }

                val loginData = loginResult.getOrThrow().result ?: return@withContext Result.error(
                    Exception("登录失败"),
                    "登录失败"
                )
                val timestamp = System.currentTimeMillis()
                
                val response = iclassApi().signClass(courseId, timestamp, loginData.id)
                if (response.result != null) {
                    Result.success(Unit)
                } else {
                    if (isFallbackEnabled()) {
                        signClassFallback(courseId)
                    } else {
                        Result.error(Exception(response.msg), response.msg ?: "签到失败")
                    }
                }
            } catch (e: VpnSessionExpiredException) {
                Result.error(e, VPN_SESSION_EXPIRED_MESSAGE)
            } catch (e: Exception) {
                if (isFallbackEnabled()) {
                    signClassFallback(courseId)
                } else {
                    Result.error(e, "签到失败: ${e.message}")
                }
            }
        }
    }

    suspend fun signClassFallback(courseId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val token = tokenManager.getValidToken() ?: return@withContext Result.error(
                    Exception("No token"),
                    "未获取到授权令牌"
                )

                val response = fallbackApi.signClass(courseId, token)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.error(Exception("HTTP ${response.code()}"), "签到失败: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fallback sign failed", e)
                Result.error(e, "Fallback签到失败: ${e.message}")
            }
        }
    }

    private fun isFallbackEnabled(): Boolean {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(KEY_FALLBACK_ENABLED, false)
    }

    fun isFallbackEnabledPublic(): Boolean = isFallbackEnabled()

    private fun convertFallbackCourse(fallback: FallbackCourseDto): CourseDto? {
        return try {
            CourseDto(
                id = fallback.id,
                courseId = fallback.courseId,
                courseName = fallback.courseName,
                courseType = fallback.courseType,
                weekDay = fallback.weekDay,
                courseNum = fallback.courseNum,
                teacherName = fallback.teacherName,
                classroomName = fallback.classroomName,
                signStatus = if (fallback.signStatus == "已签到") 1 else 0,
                classBeginTime = LocalDateTime.parse(fallback.classBeginTime, dateTimeFormatter),
                classEndTime = LocalDateTime.parse(fallback.classEndTime, dateTimeFormatter)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert fallback course", e)
            null
        }
    }
}
