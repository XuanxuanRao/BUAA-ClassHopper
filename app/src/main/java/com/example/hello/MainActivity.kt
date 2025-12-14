package com.example.hello

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.hello.model.Course
import com.example.hello.service.ApiService
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit
import androidx.core.graphics.toColorInt

class MainActivity : AppCompatActivity() {
    private lateinit var tableLayout: TableLayout
    private lateinit var editTextId: EditText
    private lateinit var textViewDate: TextView
    private lateinit var datePickerContainer: RelativeLayout
    private lateinit var calendarIcon: ImageView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var userInfoTextView: TextView
    private lateinit var apiService: ApiService

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
}