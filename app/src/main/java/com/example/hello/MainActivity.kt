package com.example.hello

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.hello.model.Course
import com.example.hello.service.ApiService
import com.example.hello.service.ChatWebSocketService
import com.example.hello.ui.CourseTableRenderer
import com.example.hello.ui.WebSocketStatusIndicator
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit
import okio.ByteString

class MainActivity : AppCompatActivity() {
    private lateinit var tableLayout: TableLayout
    private lateinit var editTextId: EditText
    private lateinit var textViewDate: TextView
    private lateinit var datePickerContainer: RelativeLayout
    private lateinit var calendarIcon: ImageView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var userInfoTextView: TextView
    private lateinit var webSocketStatusIcon: ImageView
    private lateinit var apiService: ApiService
    private lateinit var chatWebSocketService: ChatWebSocketService
    private lateinit var webSocketStatusIndicator: WebSocketStatusIndicator
    private lateinit var courseTableRenderer: CourseTableRenderer

    private val FIXED_CHAT_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJ1c2VySWQiOjEsInJvbGUiOiJBRE1JTiIsImV4cCI6MTc2NjEyOTQ3MH0.Z8a_lMcHPyuT9mjLtNKpn7xJqES9f9slsZdo9w_E3JjMaPe7ZBL3oJ7aYnyHfCchYn4MxkZOKLq_VJtM2ul9-FEh_-CGq6EF0C6ed_bGbXpK-VfwwWz2CoMLhwXL9yxWrUx57zmDECrR6nQHexFfoS_8k_vScFw6QG1_3wQFiuwFz7OoBf82yUwk-7a73G-E3vkeo2C8rV6Tol8CdPYLqe0bJhvymC9MIerylgwae_KMKxIuqm2VY4A2s-JQ7wWu5KUSibEc88o_gdTxBkLKHS1LChK8NGL-P_zMKv8Znuj1sQjQkkWUnY6Wfkbmxkh1I0tA76RRqd1jE3PrhfaJ1w"

    // WARNING: 临时跳过 TLS 证书校验（仅用于测试环境，正式环境务必改回 false）
    private val ALLOW_INSECURE_TLS = true

    private val PREFS_NAME = "ClassHopperPrefs"
    private val KEY_STUDENT_ID = "student_id"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tableLayout = findViewById(R.id.tableLayout)
        editTextId = findViewById(R.id.editTextId)
        textViewDate = findViewById(R.id.textViewDate)
        datePickerContainer = findViewById(R.id.datePickerContainer)
        calendarIcon = findViewById(R.id.calendarIcon)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        userInfoTextView = findViewById(R.id.userInfoTextView)
        webSocketStatusIcon = findViewById(R.id.webSocketStatusIcon)

        webSocketStatusIndicator = WebSocketStatusIndicator(this, webSocketStatusIcon)
        webSocketStatusIndicator.showConnecting()

        courseTableRenderer = CourseTableRenderer(
            context = this,
            tableLayout = tableLayout,
            onSignClick = { courseId -> signClass(courseId) }
        )

        // 从SharedPreferences读取保存的学号
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedStudentId = sharedPreferences.getString(KEY_STUDENT_ID, "22370000")
        editTextId.setText(savedStudentId)
        
        // 将默认日期设置为当天
        val currentDate = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        textViewDate.text = dateFormat.format(currentDate.time)

        // 设置日期选择监听器
        datePickerContainer.setOnClickListener {
            showDatePickerDialog()
        }
        calendarIcon.setOnClickListener {
            showDatePickerDialog()
        }

        // 初始化API服务
        apiService = ApiService(this)
        findViewById<Button>(R.id.btnGetClass).setOnClickListener {
            getClassInfo()
        }

        // 使用固定 token 建立 WebSocket 连接
        // 临时不校验证书
        chatWebSocketService = ChatWebSocketService(allowInsecureForDebug = ALLOW_INSECURE_TLS)
        chatWebSocketService.connect(FIXED_CHAT_TOKEN, object : ChatWebSocketService.Listener {
            override fun onOpen() {
                runOnUiThread { webSocketStatusIndicator.showConnected() }
            }

            override fun onMessage(text: String) {
                // no-op
            }

            override fun onMessage(bytes: ByteString) {
                // no-op
            }

            override fun onClosing(code: Int, reason: String) {
                runOnUiThread { webSocketStatusIndicator.showDisconnected() }
            }

            override fun onClosed(code: Int, reason: String) {
                runOnUiThread { webSocketStatusIndicator.showDisconnected() }
            }

            override fun onFailure(error: String) {
                runOnUiThread { webSocketStatusIndicator.showDisconnected() }
            }
        })
    }
    
    override fun onPause() {
        super.onPause()
        // 在应用暂停时保存学号
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        sharedPreferences.edit {
            putString(KEY_STUDENT_ID, editTextId.text.toString())
        }
    }

    private fun showEmptyState() {
        tableLayout.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
    }

    private fun hideEmptyState() {
        tableLayout.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
    }


    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        // 如果已经有日期，使用已有的日期作为默认值
        try {
            val dateStr = textViewDate.text.toString()
            if (dateStr.isNotEmpty()) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = dateFormat.parse(dateStr)
                if (date != null) {
                    calendar.time = date
                }
            }
        } catch (_: Exception) {
            // 解析失败时使用当前日期
        }

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                // 月份从0开始，所以需要+1
                val formattedMonth = String.format(Locale.getDefault(), "%02d", selectedMonth + 1)
                val formattedDay = String.format(Locale.getDefault(), "%02d", selectedDayOfMonth)
                val formattedDate = "$selectedYear-$formattedMonth-$formattedDay"
                textViewDate.text = formattedDate
                // 自动加载班级信息
                getClassInfo()
            },
            year,
            month,
            day
        )

        datePickerDialog.show()
    }

    private fun getClassInfo() {
        val id = editTextId.text.toString()
        val date = textViewDate.text.toString()

        if (id.isEmpty() || date.isEmpty()) {
            Toast.makeText(this, "请输入学号和日期", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 保存学号到SharedPreferences
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        sharedPreferences.edit {
            putString(KEY_STUDENT_ID, id)
        }

        // 登录并获取课表
        apiService.login(id, object : ApiService.OnLoginListener {
            override fun onSuccess(userId: String, sessionId: String, realName: String, academyName: String) {
                // 显示用户信息
                runOnUiThread {
                    userInfoTextView.text = "${realName} - ${academyName}"
                }
                
                // 获取课表
                val dateStr = date.replace("-", "")
                apiService.getCourseSchedule(userId, sessionId, dateStr, object : ApiService.OnCourseScheduleListener {
                    override fun onSuccess(courses: List<Course>) {
                        runOnUiThread {
                            hideEmptyState()
                            courseTableRenderer.render(courses)
                        }
                    }

                    override fun onEmpty() {
                        runOnUiThread {
                            showEmptyState()
                        }
                    }

                    override fun onFailure(error: String) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                        }
                    }
                })
            }

            override fun onFailure(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                }
            }
        })
    }


    private fun signClass(courseId: String) {
        val studentId = editTextId.text.toString()

        apiService.signClass(studentId, courseId, object : ApiService.OnSignListener {
            override fun onSuccess() {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "签到成功", Toast.LENGTH_SHORT).show()
                    // 刷新课程列表
                    getClassInfo()
                }
            }

            override fun onFailure(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::chatWebSocketService.isInitialized) {
            chatWebSocketService.close()
        }
    }
}
