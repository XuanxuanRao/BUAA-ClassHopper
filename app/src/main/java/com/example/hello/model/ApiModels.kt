package com.example.hello.model

data class ApiResponse(
    val code: Int,
    val msg: String,
    val data: String
)

data class CourseResponse(
    val STATUS: String,
    val total: String,
    val result: List<Course>? = null
)

data class Course(
    val id: String,
    val courseName: String,
    val classroomName: String,
    val signStatus: String,
    val classBeginTime: String,
    val classEndTime: String
)