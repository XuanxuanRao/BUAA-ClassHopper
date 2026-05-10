package top.aidanrao.buaa_classhopper.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import top.aidanrao.buaa_classhopper.R
import top.aidanrao.buaa_classhopper.data.model.dto.AnnouncementDto
import top.aidanrao.buaa_classhopper.viewmodel.AnnouncementViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AnnouncementActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var announcementListView: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyContainer: View
    private lateinit var announcementAdapter: AnnouncementAdapter

    private val viewModel: AnnouncementViewModel by viewModels()
    private val announcements = mutableListOf<AnnouncementDto>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_announcement)

        initViews()
        initObservers()
        loadAnnouncements()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        announcementListView = findViewById(R.id.announcement_list)
        progressBar = findViewById(R.id.progress_bar)
        emptyContainer = findViewById(R.id.empty_container)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "公告"

        toolbar.setNavigationOnClickListener { finish() }

        announcementAdapter = AnnouncementAdapter(announcements)
        announcementListView.adapter = announcementAdapter

        announcementListView.setOnItemClickListener { _, _, position, _ ->
            // 点击公告项，跳转到详情页
            val announcement = announcements[position]
            val intent = Intent(this, AnnouncementDetailActivity::class.java)
            intent.putExtra("ANNOUNCEMENT_ID", announcement.id)
            startActivity(intent)
        }
    }

    private fun initObservers() {
        viewModel.announcements.observe(this) { announcementList ->
            announcements.clear()
            announcements.addAll(announcementList)
            announcementAdapter.notifyDataSetChanged()
            
            if (announcements.isEmpty()) {
                showEmptyState()
            } else {
                hideProgress()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                showProgress()
            }
        }

        viewModel.error.observe(this) { errorMsg ->
            showError(errorMsg)
        }
    }

    private fun loadAnnouncements() {
        viewModel.loadAnnouncements()
    }

    private fun showProgress() {
        progressBar.visibility = View.VISIBLE
        announcementListView.visibility = View.GONE
        emptyContainer.visibility = View.GONE
    }

    private fun hideProgress() {
        progressBar.visibility = View.GONE
        announcementListView.visibility = View.VISIBLE
        emptyContainer.visibility = View.GONE
    }

    private fun showEmptyState() {
        emptyContainer.visibility = View.VISIBLE
        announcementListView.visibility = View.GONE
        progressBar.visibility = View.GONE
    }

    private fun showError(error: String) {
        Toast.makeText(this, "加载公告失败: $error", Toast.LENGTH_SHORT).show()
        showEmptyState()
    }

    private inner class AnnouncementAdapter(private val announcements: List<AnnouncementDto>) : BaseAdapter() {

        override fun getCount(): Int = announcements.size

        override fun getItem(position: Int): Any = announcements[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_announcement, parent, false)
            
            val announcement = announcements[position]
            
            val coverImage = view.findViewById<ImageView>(R.id.announcement_cover)
            val titleTextView = view.findViewById<TextView>(R.id.announcement_title)
            val timeTextView = view.findViewById<TextView>(R.id.announcement_time)
            val authorTextView = view.findViewById<TextView>(R.id.announcement_author)

            titleTextView.text = announcement.title
            timeTextView.text = announcement.createTime.substringBefore('T')
            val posterName = announcement.posterUsername
            if (!posterName.isNullOrBlank()) {
                authorTextView.text = "发布者: $posterName"
                authorTextView.visibility = View.VISIBLE
            } else {
                authorTextView.visibility = View.GONE
            }

            // 加载封面图片
            if (!announcement.cover.isNullOrEmpty()) {
                try {
                    Glide.with(this@AnnouncementActivity)
                        .load(announcement.cover)
                        .apply(
                            RequestOptions().transform(
                                CenterCrop(),
                                RoundedCorners(resources.displayMetrics.density.toInt() * 14)
                            )
                        )
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .into(coverImage)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                coverImage.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            
            return view
        }
    }
}
