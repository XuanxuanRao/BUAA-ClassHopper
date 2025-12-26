package com.example.hello.command

import com.example.hello.command.model.CommandDTO
import com.example.hello.command.model.CommandExecutionResult
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

class CommandDispatcher {
    private val handlers = mutableMapOf<String, CommandHandler>()
    private val gson = Gson()

    init {
        registerHandler(PingCommandHandler())
    }

    fun registerHandler(handler: CommandHandler) {
        handlers[handler.getCommandType()] = handler
    }

    fun dispatch(json: String): CommandExecutionResult? {
        return try {
            val command = gson.fromJson(json, CommandDTO::class.java)
            // Ensure it's a valid command object

            val handler = handlers[command.commandType]
            handler?.execute(command)
                ?: CommandExecutionResult(
                    commandId = command.commandId,
                    success = false,
                    message = "Unknown command type: ${command.commandType}"
                )
        } catch (e: JsonSyntaxException) {
            // Not a JSON or not matching our schema, ignore or log
            null
        } catch (e: Exception) {
            // Other errors
            null
        }
    }
}
