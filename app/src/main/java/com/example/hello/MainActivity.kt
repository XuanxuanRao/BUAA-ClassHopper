package com.example.hello

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.hello.ui.CourseTableRenderer
import com.example.hello.ui.WebSocketStatusIndicator
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var tableLayout: TableLayout
    private lateinit var editTextId: EditText
    private lateinit var textViewDate: TextView
    private lateinit var datePickerContainer: RelativeLayout
    private lateinit var calendarIcon: ImageView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var userInfoTextView: TextView
    private lateinit var webSocketStatusIcon: ImageView
    private lateinit var webSocketStatusIndicator: WebSocketStatusIndicator
    private lateinit var courseTableRenderer: CourseTableRenderer

    private val viewModel: MainViewModel by viewModels()

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

        initViews()
        initObservers()
        
        // 恢复保存的学号
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedStudentId = sharedPreferences.getString(KEY_STUDENT_ID, "22370000")
        editTextId.setText(savedStudentId)
        
        // 设置默认日期
        val currentDate = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        textViewDate.text = dateFormat.format(currentDate.time)
    }

    private fun initViews() {
        tableLayout = findViewById(R.id.tableLayout)
        editTextId = findViewById(R.id.editTextId)
        textViewDate = findViewById(R.id.textViewDate)
        datePickerContainer = findViewById(R.id.datePickerContainer)
        calendarIcon = findViewById(R.id.calendarIcon)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        userInfoTextView = findViewById(R.id.userInfoTextView)
        webSocketStatusIcon = findViewById(R.id.webSocketStatusIcon)

        webSocketStatusIndicator = WebSocketStatusIndicator(this, webSocketStatusIcon)
        
        courseTableRenderer = CourseTableRenderer(
            context = this,
            tableLayout = tableLayout,
            onSignClick = { courseId -> 
                viewModel.signClass(
                    editTextId.text.toString(), 
                    courseId, 
                    textViewDate.text.toString()
                ) 
            }
        )

        datePickerContainer.setOnClickListener { showDatePickerDialog() }
        calendarIcon.setOnClickListener { showDatePickerDialog() }

        findViewById<Button>(R.id.btnGetClass).setOnClickListener {
            val id = editTextId.text.toString()
            val date = textViewDate.text.toString()
            viewModel.getClassInfo(id, date)
            
            // 保存学号
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                putString(KEY_STUDENT_ID, id)
            }
        }
    }

    private fun initObservers() {
        viewModel.courses.observe(this) { courses ->
            hideEmptyState()
            courseTableRenderer.render(courses)
        }

        viewModel.userInfo.observe(this) { info ->
            userInfoTextView.text = info
        }

        viewModel.isEmpty.observe(this) { isEmpty ->
            if (isEmpty) showEmptyState() else hideEmptyState()
        }

        viewModel.error.observe(this) { errorMsg ->
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
        }

        viewModel.toastMessage.observe(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        viewModel.webSocketStatus.observe(this) { status ->
            when (status) {
                MainViewModel.WebSocketStatus.CONNECTED -> webSocketStatusIndicator.showConnected()
                MainViewModel.WebSocketStatus.CONNECTING -> webSocketStatusIndicator.showConnecting()
                MainViewModel.WebSocketStatus.DISCONNECTED -> webSocketStatusIndicator.showDisconnected()
                else -> webSocketStatusIndicator.showDisconnected()
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
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

        try {
            val dateStr = textViewDate.text.toString()
            if (dateStr.isNotEmpty()) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = dateFormat.parse(dateStr)
                if (date != null) {
                    calendar.time = date
                }
            }
        } catch (_: Exception) { }

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDayOfMonth ->
            val formattedMonth = String.format(Locale.getDefault(), "%02d", selectedMonth + 1)
            val formattedDay = String.format(Locale.getDefault(), "%02d", selectedDayOfMonth)
            val formattedDate = "$selectedYear-$formattedMonth-$formattedDay"
            textViewDate.text = formattedDate
            
            // 自动加载
            val id = editTextId.text.toString()
            if (id.isNotEmpty()) {
                viewModel.getClassInfo(id, formattedDate)
            }
        }, year, month, day).show()
    }
}
