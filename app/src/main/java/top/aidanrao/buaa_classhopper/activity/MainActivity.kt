package top.aidanrao.buaa_classhopper.activity

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import top.aidanrao.buaa_classhopper.NavigationManager
import top.aidanrao.buaa_classhopper.R
import top.aidanrao.buaa_classhopper.data.model.dto.UserInfoDto
import top.aidanrao.buaa_classhopper.data.repository.CourseRepository
import top.aidanrao.buaa_classhopper.ui.CourseTableRenderer
import top.aidanrao.buaa_classhopper.ui.IClassAvailabilityIndicator
import top.aidanrao.buaa_classhopper.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var tableLayout: TableLayout
    private lateinit var editTextId: EditText
    private lateinit var textViewDate: TextView
    private lateinit var datePickerContainer: RelativeLayout
    private lateinit var calendarIcon: ImageView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var userInfoTextView: TextView
    private lateinit var statusIndicatorIcon: ImageView
    private lateinit var iclassAvailabilityIndicator: IClassAvailabilityIndicator
    private lateinit var courseTableRenderer: CourseTableRenderer
    private lateinit var scanButton: ImageButton
    private lateinit var scanLauncher: ActivityResultLauncher<ScanOptions>
    
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var hamburgerButton: ImageButton

    private val viewModel: MainViewModel by viewModels()
    @Inject
    @Named("authClient")
    lateinit var iclassStatusHttpClient: OkHttpClient
    private var iclassStatusPollingJob: Job? = null

    private val PREFS_NAME = "ClassHopperPrefs"
    private val KEY_STUDENT_ID = "student_id"
    private val ICLASS_STATUS_URL = "https://iclass.buaa.edu.cn:8346/"
    private val ICLASS_STATUS_POLL_INTERVAL_MS = 30_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        scanLauncher = registerForActivityResult(ScanContract()) { result ->
            val contents = result.contents
            if (contents.isNullOrEmpty()) {
                Toast.makeText(this, "未识别二维码", Toast.LENGTH_SHORT).show()
            } else {
                handleScanResult(contents)
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.content_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        initObservers()
        initDrawer()
        
        // 获取用户信息
        viewModel.fetchUserProfile()
        
        // 恢复保存的学号
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedStudentId = sharedPreferences.getString(KEY_STUDENT_ID, "22370000")
        editTextId.setText(savedStudentId)
        applyIdentityLine(studentId = savedStudentId, rawName = null)
        
        // 设置默认日期
        val currentDate = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        textViewDate.text = dateFormat.format(currentDate.time)
        
        // 如果学号和日期都已填充，自动加载课表
        val studentId = editTextId.text.toString()
        val date = textViewDate.text.toString()
        if (studentId.isNotEmpty() && date.isNotEmpty()) {
            viewModel.getClassInfo(studentId, date)
        }
    }

    private fun initViews() {
        tableLayout = findViewById(R.id.tableLayout)
        editTextId = findViewById(R.id.editTextId)
        textViewDate = findViewById(R.id.textViewDate)
        datePickerContainer = findViewById(R.id.datePickerContainer)
        calendarIcon = findViewById(R.id.calendarIcon)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        userInfoTextView = findViewById(R.id.userInfoTextView)
        statusIndicatorIcon = findViewById(R.id.webSocketStatusIcon)
        hamburgerButton = findViewById(R.id.hamburger_button)
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        scanButton = findViewById(R.id.scanButton)

        iclassAvailabilityIndicator = IClassAvailabilityIndicator(this, statusIndicatorIcon)
        
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

        scanButton.setOnClickListener { startScan() }
    }

    /**
     * 初始化侧边栏
     */
    private fun initDrawer() {
        // 获取侧边栏容器
        val drawerContainer = findViewById<View>(R.id.drawer_container)

        // 汉堡按钮点击事件
        hamburgerButton.setOnClickListener {
            drawerContainer?.let { container ->
                drawerLayout.openDrawer(container)
            }
        }

        // 侧边栏菜单项点击事件
        navView.setNavigationItemSelectedListener {
            val container = findViewById<View>(R.id.drawer_container)
            when (it.itemId) {
                R.id.menu_home -> {
                    Toast.makeText(this, "首页", Toast.LENGTH_SHORT).show()
                    if (container != null) drawerLayout.closeDrawer(container)
                    true
                }
                R.id.menu_announcement -> {
                    NavigationManager.navigate(this, "/announcement")
                    if (container != null) drawerLayout.closeDrawer(container)
                    true
                }
                R.id.menu_lab -> {
                    NavigationManager.navigate(this, "/lab")
                    if (container != null) drawerLayout.closeDrawer(container)
                    true
                }
                R.id.menu_settings -> {
                    viewModel.fetchUserProfile()
                    NavigationManager.navigate(this, "/settings")
                    if (container != null) drawerLayout.closeDrawer(container)
                    true
                }
                R.id.menu_about -> {
                    NavigationManager.navigate(this, "/about")
                    if (container != null) drawerLayout.closeDrawer(container)
                    true
                }
                else -> {
                    false
                }
            }
        }
    }

    private fun initObservers() {
        viewModel.courses.observe(this) { courses ->
            hideEmptyState()
            courseTableRenderer.render(courses)
        }

        viewModel.userInfo.observe(this) { info ->
            applyIdentityLine(
                studentId = editTextId.text?.toString(),
                rawName = info
            )
        }

        viewModel.isEmpty.observe(this) { isEmpty ->
            if (isEmpty) showEmptyState() else hideEmptyState()
        }

        viewModel.error.observe(this) { errorMsg ->
            if (errorMsg == CourseRepository.VPN_SESSION_EXPIRED_MESSAGE) {
                showVpnSessionExpiredDialog()
            } else {
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.toastMessage.observe(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        
        // 观察用户信息变化
        viewModel.userProfile.observe(this) { userInfo ->
            applyIdentityLine(
                studentId = userInfo.studentId,
                rawName = userInfo.username
            )
            updateDrawerHeader(userInfo)
        }
    }

    override fun onStart() {
        super.onStart()
        startIClassAvailabilityPolling()
    }

    override fun onStop() {
        stopIClassAvailabilityPolling()
        super.onStop()
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

    private var vpnExpiredDialogShown = false
    private fun showVpnSessionExpiredDialog() {
        if (vpnExpiredDialogShown) return
        vpnExpiredDialogShown = true
        AlertDialog.Builder(this)
            .setTitle("VPN 会话已失效")
            .setMessage("登录状态已过期，请重新通过 SSO 登录北航 VPN 后继续使用")
            .setCancelable(false)
            .setPositiveButton("去登录") { dialog, _ ->
                dialog.dismiss()
                startActivity(Intent(this, VpnLoginActivity::class.java))
            }
            .setNegativeButton("取消", null)
            .setOnDismissListener { vpnExpiredDialogShown = false }
            .show()
    }

    private fun hideEmptyState() {
        tableLayout.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()

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
        
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

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

    private fun startScan() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("请对准二维码")
            .setBeepEnabled(true)
            .setOrientationLocked(true)
            .setCaptureActivity(ScanCaptureActivity::class.java)
        scanLauncher.launch(options)
    }

    private fun handleScanResult(contents: String) {
        val success = NavigationManager.navigate(this, contents)
        if (!success) {
            Toast.makeText(this, "无法处理二维码内容", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyIdentityLine(studentId: String?, rawName: String?) {
        val cleanedStudentId = studentId?.trim().orEmpty()
        val cleanedName = rawName
            ?.substringBefore(" - ")
            ?.trim()
            .orEmpty()

        userInfoTextView.text = listOf(cleanedStudentId, cleanedName)
            .filter { it.isNotEmpty() }
            .joinToString("  ")
            .ifEmpty { "学号  姓名" }
    }

    private fun startIClassAvailabilityPolling() {
        if (iclassStatusPollingJob?.isActive == true) return

        iclassStatusPollingJob = lifecycleScope.launch {
            while (isActive) {
                updateIClassAvailabilityIndicator()
                delay(ICLASS_STATUS_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopIClassAvailabilityPolling() {
        iclassStatusPollingJob?.cancel()
        iclassStatusPollingJob = null
    }

    private suspend fun updateIClassAvailabilityIndicator() {
        val isReachable = kotlinx.coroutines.withContext(Dispatchers.IO) {
            checkIClassAvailability()
        }

        if (isReachable) {
            iclassAvailabilityIndicator.showReachable()
        } else {
            iclassAvailabilityIndicator.showUnreachable()
        }
    }

    private fun checkIClassAvailability(): Boolean {
        val request = Request.Builder()
            .url(ICLASS_STATUS_URL)
            .head()
            .build()

        return try {
            iclassStatusHttpClient.newCall(request).execute().use { response ->
                response.code in 200..499
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun updateDrawerHeader(userInfo: UserInfoDto) {
        val headerView = findViewById<View>(R.id.drawer_header) ?: return
        
        val avatarImage = headerView.findViewById<ImageView>(R.id.avatar_image)
        val studentIdText = headerView.findViewById<TextView>(R.id.student_id_text)
        val verifiedText = headerView.findViewById<TextView>(R.id.verified_text)
        
        studentIdText.text = userInfo.studentId
        
        verifiedText.text = if (userInfo.verified) "已认证" else "未认证 / 点击登录"
        verifiedText.setTextColor(
            if (userInfo.verified) {
                ContextCompat.getColor(this, android.R.color.holo_green_light)
            } else {
                ContextCompat.getColor(this, R.color.home_text_on_hero)
            }
        )
        
        if (!userInfo.avatar.isNullOrEmpty()) {
            try {
                Glide.with(this)
                    .load(userInfo.avatar)
                    .circleCrop()
                    .placeholder(R.drawable.ic_home_student)
                    .error(R.drawable.ic_home_student)
                    .into(avatarImage)
            } catch (e: Exception) {
                e.printStackTrace()
                avatarImage.setImageResource(R.drawable.ic_home_student)
            }
        } else {
            avatarImage.setImageResource(R.drawable.ic_home_student)
        }
    }

    override fun onResume() {
        super.onResume()
        navView.setCheckedItem(R.id.menu_home)
    }
}
