package com.example.hello

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.hello.model.Course
import com.example.hello.service.ApiService
import com.example.hello.service.ChatWebSocketService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.ByteString

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // UI State
    private val _courses = MutableLiveData<List<Course>>()
    val courses: LiveData<List<Course>> = _courses

    private val _userInfo = MutableLiveData<String>()
    val userInfo: LiveData<String> = _userInfo

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isEmpty = MutableLiveData<Boolean>()
    val isEmpty: LiveData<Boolean> = _isEmpty

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    // WebSocket State
    private val _webSocketStatus = MutableLiveData<WebSocketStatus>()
    val webSocketStatus: LiveData<WebSocketStatus> = _webSocketStatus

    enum class WebSocketStatus {
        CONNECTED, CONNECTING, DISCONNECTED
    }

    // Services
    private val apiService = ApiService(application)
    // 临时跳过 TLS 证书校验（仅用于测试环境，正式环境务必改回 false）
    private val ALLOW_INSECURE_TLS = true
    private val chatWebSocketService = ChatWebSocketService(allowInsecureForDebug = ALLOW_INSECURE_TLS)

    // Data
    private var currentUserId: String? = null
    private var currentSessionId: String? = null

    init {
        _webSocketStatus.value = WebSocketStatus.CONNECTING
        connectWebSocket()
    }

    private fun connectWebSocket() {
        apiService.getAuthToken(object : ApiService.OnAuthListener {
            override fun onSuccess(token: String, expireAt: Long) {
                Log.d("MainViewModel", "自动获取token成功")
                viewModelScope.launch(Dispatchers.Main) {
                    chatWebSocketService.connect(token, object : ChatWebSocketService.Listener {
                        override fun onOpen() {
                            _webSocketStatus.postValue(WebSocketStatus.CONNECTED)
                        }

                        override fun onMessage(text: String) {
                            Log.d("ChatWS", "Text message: $text")
                        }

                        override fun onMessage(bytes: ByteString) {
                            Log.d("ChatWS", "Binary message: ${bytes.hex()}")
                        }

                        override fun onClosing(code: Int, reason: String) {
                            _webSocketStatus.postValue(WebSocketStatus.DISCONNECTED)
                        }

                        override fun onClosed(code: Int, reason: String) {
                            _webSocketStatus.postValue(WebSocketStatus.DISCONNECTED)
                        }

                        override fun onFailure(error: String) {
                            _webSocketStatus.postValue(WebSocketStatus.DISCONNECTED)
                        }

                        override fun onReconnectAttempt(attempt: Int, delayMs: Long) {
                            _webSocketStatus.postValue(WebSocketStatus.CONNECTING)
                        }
                    })
                }
            }

            override fun onFailure(error: String) {
                Log.e("MainViewModel", "获取token失败: $error")
                _webSocketStatus.postValue(WebSocketStatus.DISCONNECTED)
            }
        })
    }

    fun getClassInfo(studentId: String, date: String) {
        if (studentId.isEmpty() || date.isEmpty()) {
            _toastMessage.value = "请输入学号和日期"
            return
        }

        _isLoading.value = true
        _isEmpty.value = false

        apiService.login(studentId, object : ApiService.OnLoginListener {
            override fun onSuccess(userId: String, sessionId: String, realName: String, academyName: String) {
                currentUserId = userId
                currentSessionId = sessionId
                _userInfo.postValue("$realName - $academyName")

                val dateStr = date.replace("-", "")
                apiService.getCourseSchedule(userId, sessionId, dateStr, object : ApiService.OnCourseScheduleListener {
                    override fun onSuccess(courses: List<Course>) {
                        _isLoading.postValue(false)
                        _courses.postValue(courses)
                    }

                    override fun onEmpty() {
                        _isLoading.postValue(false)
                        _isEmpty.postValue(true)
                    }

                    override fun onFailure(error: String) {
                        _isLoading.postValue(false)
                        _error.postValue(error)
                    }
                })
            }

            override fun onFailure(error: String) {
                _isLoading.postValue(false)
                _error.postValue(error)
            }
        })
    }

    fun signClass(studentId: String, courseId: String, date: String) {
        apiService.signClass(studentId, courseId, object : ApiService.OnSignListener {
            override fun onSuccess() {
                _toastMessage.postValue("签到成功")
                // 刷新课程列表
                getClassInfo(studentId, date)
            }

            override fun onFailure(error: String) {
                _error.postValue(error)
            }
        })
    }

    override fun onCleared() {
        super.onCleared()
        chatWebSocketService.close()
    }
}
