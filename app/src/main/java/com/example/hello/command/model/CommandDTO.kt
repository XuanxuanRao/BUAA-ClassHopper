package com.example.hello.command.model

import com.google.gson.annotations.SerializedName

data class CommandDTO(
    @SerializedName("commandId")
    val commandId: String,
    
    @SerializedName("commandType")
    val commandType: String,
    
    @SerializedName("params")
    val params: Map<String, Any>? = null
)
