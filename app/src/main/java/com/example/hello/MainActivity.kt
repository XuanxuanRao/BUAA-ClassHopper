package com.example.hello

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.hello.model.Course
import com.example.hello.service.ApiService
import com.example.hello.service.ChatWebSocketService
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
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

    private val FIXED_CHAT_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJ1c2VySWQiOjEsInJvbGUiOiJBRE1JTiIsImV4cCI6MTc2NjEyNzQxOH0.VaPp4V7pFWA1W9NI4fatXwKbUBK-3EYFVit-Ns9OiScDMgrgFqmT5XCR3Y8SNRjtgS83IoMptH-4BvlgvhGL16QCsq53xbULxEP-i-yuZMM1yN1GGr157cjN8PQ36XUCEywfg7IhmSHnxelQlpTasxQhq4Sqw-m2HZdzTX6QPEfsB5nczx8O5TuRqaB-Jc2cd9x3Yr41ZxA69qjH1e2DaozCxLFu1PWX71b2emqMMLKGrS5rqgX2MmdYVD2C2Cczk3V4GCBLxCOuWwTN2BfyZHYnusGD-V9_CpSXRU663s3VKsM-mLh4mb2fR8pYbyeNv9T1ChsIC_QHjZu3dSbtJg"

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

        setWebSocketStatusConnecting()

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
                runOnUiThread { setWebSocketStatusConnected() }
            }

            override fun onMessage(text: String) {
                // no-op
            }

            override fun onMessage(bytes: ByteString) {
                // no-op
            }

            override fun onClosing(code: Int, reason: String) {
                runOnUiThread { setWebSocketStatusDisconnected() }
            }

            override fun onClosed(code: Int, reason: String) {
                runOnUiThread { setWebSocketStatusDisconnected() }
            }

            override fun onFailure(error: String) {
                runOnUiThread { setWebSocketStatusDisconnected() }
            }
        })
    }

    private fun setWebSocketStatusConnected() {
        webSocketStatusIcon.setImageResource(android.R.drawable.presence_online)
        webSocketStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.ws_connected))
        webSocketStatusIcon.contentDescription = "WebSocket 已连接"
    }

    private fun setWebSocketStatusConnecting() {
        webSocketStatusIcon.setImageResource(android.R.drawable.presence_away)
        webSocketStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.ws_connecting))
        webSocketStatusIcon.contentDescription = "WebSocket 连接中"
    }

    private fun setWebSocketStatusDisconnected() {
        webSocketStatusIcon.setImageResource(android.R.drawable.presence_offline)
        webSocketStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.ws_disconnected))
        webSocketStatusIcon.contentDescription = "WebSocket 未连接"
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

    // 格式化时间，只保留时间部分（HH:mm - HH:mm）
    private fun formatTime(beginTime: String, endTime: String): String {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val begin = dateFormat.parse(beginTime)
            val end = dateFormat.parse(endTime)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            if (begin != null && end != null) {
                return "${timeFormat.format(begin)} - ${timeFormat.format(end)}"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
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
                            updateTable(courses)
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

    private fun updateTable(courses: List<Course>) {
        // 清除之前的数据行，保留表头
        for (i in tableLayout.childCount - 1 downTo 1) {
            tableLayout.removeViewAt(i)
        }

        // 添加新数据
        courses.forEachIndexed { index, course ->
            val row = TableRow(this).apply {
                layoutParams = TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.WRAP_CONTENT
                )
                setPadding(8, 8, 8, 8)
                setBackgroundColor(if (index % 2 == 0) Color.WHITE else "#F5F5F5".toColorInt())
            }

            // 添加课程名称
            row.addView(TextView(this).apply {
                text = course.courseName
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4, 4, 4, 4)
            })

            // 添加课程时间
            row.addView(TextView(this).apply {
                text = formatTime(course.classBeginTime, course.classEndTime)
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4, 4, 4, 4)
            })

            // 添加教室名称
            row.addView(TextView(this).apply {
                text = course.classroomName
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.75f)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4, 4, 4, 4)
            })

            // 添加签到按钮
            val signButton = Button(this).apply {
                text = if (course.signStatus == "1") "已签到" else "签到"
                isEnabled = course.signStatus != "1"
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.75f)
                setOnClickListener {
                    signClass(course.id)
                }
            }
            row.addView(signButton)

            tableLayout.addView(row)
        }
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
