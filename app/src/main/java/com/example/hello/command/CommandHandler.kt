package com.example.hello.command

import com.example.hello.command.model.CommandDTO
import com.example.hello.command.model.CommandExecutionResult

interface CommandHandler {
    fun execute(command: CommandDTO): CommandExecutionResult
    fun getCommandType(): String
}
