package com.example.hello.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.hello.model.Course
import java.text.SimpleDateFormat
import java.util.Locale

class CourseTableRenderer(
    private val context: Context,
    private val tableLayout: TableLayout,
    private val onSignClick: (courseId: String) -> Unit,
) {
    fun render(courses: List<Course>) {
        // 清除之前的数据行，保留表头
        for (i in tableLayout.childCount - 1 downTo 1) {
            tableLayout.removeViewAt(i)
        }

        courses.forEachIndexed { index, course ->
            val row = TableRow(context).apply {
                layoutParams = TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.WRAP_CONTENT
                )
                setPadding(8, 8, 8, 8)
                setBackgroundColor(if (index % 2 == 0) Color.WHITE else "#F5F5F5".toColorInt())
            }

            row.addView(TextView(context).apply {
                text = course.courseName
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4, 4, 4, 4)
            })

            row.addView(TextView(context).apply {
                text = formatTime(course.classBeginTime, course.classEndTime)
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4, 4, 4, 4)
            })

            row.addView(TextView(context).apply {
                text = course.classroomName
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.75f)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4, 4, 4, 4)
            })

            val signButton = Button(context).apply {
                text = if (course.signStatus == "1") "已签到" else "签到"
                isEnabled = course.signStatus != "1"
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.75f)
                setOnClickListener { onSignClick(course.id) }
            }
            row.addView(signButton)

            tableLayout.addView(row)
        }
    }

    private fun formatTime(beginTime: String, endTime: String): String {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val begin = dateFormat.parse(beginTime)
            val end = dateFormat.parse(endTime)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            if (begin != null && end != null) {
                "${timeFormat.format(begin)} - ${timeFormat.format(end)}"
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }
}

