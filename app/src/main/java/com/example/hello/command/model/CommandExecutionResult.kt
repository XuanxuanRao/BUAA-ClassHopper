package com.example.hello.command.model

import com.google.gson.annotations.SerializedName

data class CommandExecutionResult(
    @SerializedName("commandId")
    val commandId: String,
    
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String? = null,
    
    @SerializedName("data")
    val data: Any? = null
)
