package com.example.hello.command

import com.example.hello.command.model.CommandDTO
import com.example.hello.command.model.CommandExecutionResult

class PingCommandHandler : CommandHandler {
    override fun getCommandType(): String = "ping"

    override fun execute(command: CommandDTO): CommandExecutionResult {
        val target = command.params?.get("target") as? String
        
        if (target.isNullOrEmpty()) {
             return CommandExecutionResult(
                commandId = command.commandId,
                success = false,
                message = "Target is missing"
            )
        }

        return try {
            // -c 1: count 1 packet
            // -w 5: deadline 5 seconds
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/ping", "-c", "1", "-w", "5", target))
            val exitValue = process.waitFor()
            
            val success = exitValue == 0
            val message = if (success) "Ping successful" else "Ping failed"
            
            CommandExecutionResult(
                commandId = command.commandId,
                success = success,
                message = message
            )
        } catch (e: Exception) {
            CommandExecutionResult(
                commandId = command.commandId,
                success = false,
                message = "Ping error: ${e.message}"
            )
        }
    }
}
