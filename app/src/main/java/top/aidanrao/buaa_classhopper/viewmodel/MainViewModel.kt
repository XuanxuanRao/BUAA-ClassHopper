package top.aidanrao.buaa_classhopper.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.ByteString
import top.aidanrao.buaa_classhopper.command.CommandDispatcher
import top.aidanrao.buaa_classhopper.data.model.Result
import top.aidanrao.buaa_classhopper.data.model.dto.CourseDto
import top.aidanrao.buaa_classhopper.data.model.dto.UserInfoDto
import top.aidanrao.buaa_classhopper.data.repository.AuthRepository
import top.aidanrao.buaa_classhopper.data.repository.CourseRepository
import top.aidanrao.buaa_classhopper.data.repository.UserRepository
import top.aidanrao.buaa_classhopper.service.ChatWebSocketService
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
    private val commandDispatcher: CommandDispatcher,
    private val chatWebSocketService: ChatWebSocketService
) : ViewModel() {

    private val _courses = MutableLiveData<List<CourseDto>>()
    val courses: LiveData<List<CourseDto>> = _courses

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

    private var isRequestInProgress = false
    
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeSerializer())
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeDeserializer())
        .create()

    private val _userProfile = MutableLiveData<UserInfoDto>()
    val userProfile: LiveData<UserInfoDto> = _userProfile

    init {
        connectWebSocket()
    }

    private fun connectWebSocket() {
        viewModelScope.launch {
            val token = authRepository.getValidToken()
            if (token != null) {
                Log.d("MainViewModel", "获取token成功: $token")
                withContext(Dispatchers.Main) {
                    chatWebSocketService.connect(token, object : ChatWebSocketService.Listener {
                        override fun onOpen() = Unit

                        override fun onMessage(text: String) {
                            Log.d("ChatWS", "Text message: $text")
                            viewModelScope.launch(Dispatchers.IO) {
                                val result = commandDispatcher.dispatch(text)
                                if (result != null) {
                                    val responseJson = gson.toJson(result)
                                    chatWebSocketService.send(responseJson)
                                }
                            }
                        }

                        override fun onMessage(bytes: ByteString) {
                            Log.d("ChatWS", "Binary message: ${bytes.hex()}")
                        }

                        override fun onClosing(code: Int, reason: String) = Unit

                        override fun onClosed(code: Int, reason: String) = Unit

                        override fun onFailure(error: String) = Unit

                        override fun onReconnectAttempt(attempt: Int, delayMs: Long) = Unit
                    })
                }
            } else {
                Log.e("MainViewModel", "获取token失败")
            }
        }
    }

    fun getClassInfo(studentId: String, date: String) {
        if (studentId.isEmpty() || date.isEmpty()) {
            _toastMessage.postValue("请输入学号和日期")
            return
        }

        if (isRequestInProgress) {
            _toastMessage.postValue("操作进行中，请稍候")
            return
        }

        isRequestInProgress = true
        _isLoading.postValue(true)
        _isEmpty.postValue(false)

        viewModelScope.launch {
            when (val loginResult = courseRepository.login(studentId)) {
                is Result.Success -> {
                    val loginData = loginResult.data.result
                    if (loginData != null) {
                        _userInfo.postValue("${loginData.realName} - ${loginData.academyName}")
                        
                        val dateStr = date.replace("-", "")
                        fetchCourseSchedule(loginData.id, loginData.sessionId, dateStr)
                    } else {
                        _isLoading.postValue(false)
                        _error.postValue(loginResult.data.ERRMSG ?: "登录失败")
                        isRequestInProgress = false
                    }
                }
                is Result.Error -> {
                    if (courseRepository.isFallbackEnabledPublic() && authRepository.getValidToken() != null) {
                        val dateStr = date.replace("-", "")
                        fetchCourseScheduleFallback(dateStr)
                    } else {
                        _isLoading.postValue(false)
                        _error.postValue(loginResult.getErrorMessage() ?: "登录失败")
                        isRequestInProgress = false
                    }
                }
                Result.Loading -> {}
            }
        }
    }

    private suspend fun fetchCourseSchedule(userId: String, sessionId: String, dateStr: String) {
        when (val result = courseRepository.getCourseSchedule(userId, sessionId, dateStr)) {
            is Result.Success -> {
                _isLoading.postValue(false)
                val courseList = result.data
                if (courseList.isEmpty()) {
                    _isEmpty.postValue(true)
                } else {
                    _courses.postValue(courseList)
                }
                isRequestInProgress = false
            }
            is Result.Error -> {
                _isLoading.postValue(false)
                _error.postValue(result.getErrorMessage() ?: "获取课表失败")
                isRequestInProgress = false
            }
            Result.Loading -> {}
        }
    }

    private suspend fun fetchCourseScheduleFallback(dateStr: String) {
        when (val result = courseRepository.getCourseScheduleFallback(dateStr)) {
            is Result.Success -> {
                _isLoading.postValue(false)
                val courseList = result.data
                if (courseList.isEmpty()) {
                    _isEmpty.postValue(true)
                } else {
                    _courses.postValue(courseList)
                }
                isRequestInProgress = false
            }
            is Result.Error -> {
                _isLoading.postValue(false)
                _error.postValue(result.getErrorMessage() ?: "获取课表失败")
                isRequestInProgress = false
            }
            Result.Loading -> {}
        }
    }

    fun signClass(studentId: String, courseId: Int, date: String) {
        viewModelScope.launch {
            when (val result = courseRepository.signClass(studentId, courseId)) {
                is Result.Success -> {
                    _toastMessage.postValue("签到成功")
                    getClassInfo(studentId, date)
                }
                is Result.Error -> {
                    _error.postValue(result.getErrorMessage() ?: "签到失败")
                }
                Result.Loading -> {}
            }
        }
    }

    fun fetchUserProfile() {
        viewModelScope.launch {
            when (val result = userRepository.getUserInfo()) {
                is Result.Success -> {
                    _userProfile.postValue(result.data)
                }
                is Result.Error -> {
                    Log.e("MainViewModel", "获取用户信息失败: ${result.getErrorMessage()}")
                }
                Result.Loading -> {}
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        chatWebSocketService.close()
    }

    private class LocalDateTimeSerializer : JsonSerializer<LocalDateTime> {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        
        override fun serialize(src: LocalDateTime?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return JsonPrimitive(src?.format(formatter))
        }
    }
    
    private class LocalDateTimeDeserializer : JsonDeserializer<LocalDateTime> {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): LocalDateTime? {
            return json?.asString?.let {
                LocalDateTime.parse(it, formatter)
            }
        }
    }
}
