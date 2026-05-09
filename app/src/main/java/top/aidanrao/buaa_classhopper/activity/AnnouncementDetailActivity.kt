package top.aidanrao.buaa_classhopper.activity

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import top.aidanrao.buaa_classhopper.R
import top.aidanrao.buaa_classhopper.viewmodel.AnnouncementViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AnnouncementDetailActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var titleTextView: TextView
    private lateinit var timeTextView: TextView
    private lateinit var coverImageView: ImageView
    private lateinit var contentTextView: TextView
    private lateinit var authorNameTextView: TextView
    private lateinit var authorAvatarImageView: ImageView
    private lateinit var progressBar: ProgressBar

    private val viewModel: AnnouncementViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_announcement_detail)

        initViews()
        initObservers()
        
        val announcementId = intent.getStringExtra("ANNOUNCEMENT_ID")
        if (announcementId != null) {
            loadAnnouncementDetail(announcementId)
        } else {
            showError("未获取到公告ID")
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        titleTextView = findViewById(R.id.announcement_title)
        timeTextView = findViewById(R.id.announcement_time)
        coverImageView = findViewById(R.id.announcement_cover)
        contentTextView = findViewById(R.id.announcement_content)
        authorNameTextView = findViewById(R.id.author_name)
        authorAvatarImageView = findViewById(R.id.author_avatar)
        progressBar = findViewById(R.id.progress_bar)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "公告详情"

        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun initObservers() {
        viewModel.announcementDetail.observe(this) { detail ->
            displayAnnouncementDetail(detail.title, detail.createTime, detail.cover, detail.content, detail.posterUsername, detail.posterAvatar)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                showProgress()
            } else {
                hideProgress()
            }
        }

        viewModel.error.observe(this) { errorMsg ->
            showError(errorMsg)
        }
    }

    private fun loadAnnouncementDetail(announcementId: String) {
        viewModel.loadAnnouncementDetail(announcementId)
    }

    @SuppressLint("SetTextI18n")
    private fun displayAnnouncementDetail(title: String, createTime: String, cover: String?, content: String?, posterUsername: String?, posterAvatar: String?) {
        titleTextView.text = title
        timeTextView.text = createTime.substringBefore('T')

        if (!cover.isNullOrEmpty()) {
            coverImageView.visibility = View.VISIBLE
            try {
                Glide.with(this)
                    .load(cover)
                    .apply(
                        RequestOptions().transform(
                            CenterCrop(),
                            RoundedCorners((resources.displayMetrics.density * 14).toInt())
                        )
                    )
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(coverImageView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            coverImageView.visibility = View.GONE
        }

        contentTextView.text = content ?: ""

        authorNameTextView.text = posterUsername ?: "未知"
        authorNameTextView.visibility = View.VISIBLE

        authorAvatarImageView.visibility = View.VISIBLE
        if (!posterAvatar.isNullOrEmpty()) {
            try {
                Glide.with(this)
                    .load(posterAvatar)
                    .circleCrop()
                    .placeholder(R.drawable.ic_home_student)
                    .error(R.drawable.ic_home_student)
                    .into(authorAvatarImageView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            authorAvatarImageView.setImageResource(R.drawable.ic_home_student)
        }
    }

    private fun showProgress() {
        progressBar.visibility = View.VISIBLE
        findViewById<ScrollView>(R.id.scroll_view).visibility = View.GONE
    }

    private fun hideProgress() {
        progressBar.visibility = View.GONE
        findViewById<ScrollView>(R.id.scroll_view).visibility = View.VISIBLE
    }

    private fun showError(error: String) {
        Toast.makeText(this, "加载公告详情失败: $error", Toast.LENGTH_SHORT).show()
    }
}
