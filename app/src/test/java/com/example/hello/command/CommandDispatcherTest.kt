package com.example.hello.command

import org.junit.Assert.*
import org.junit.Test

class CommandDispatcherTest {

    @Test
    fun testDispatchPing() {
        val dispatcher = CommandDispatcher()
        val json = """
            {
                "commandId": "123",
                "commandType": "ping",
                "params": {
                    "target": "127.0.0.1"
                }
            }
        """.trimIndent()

        val result = dispatcher.dispatch(json)
        
        assertNotNull("Result should not be null", result)
        assertEquals("123", result?.commandId)
        // Expected to fail on local machine due to /system/bin/ping path
        assertFalse("Ping should fail locally or be mocked", result?.success ?: true)
        println("Message: ${result?.message}")
    }

    @Test
    fun testDispatchUnknown() {
        val dispatcher = CommandDispatcher()
        val json = """
            {
                "commandId": "456",
                "commandType": "unknown",
                "params": {}
            }
        """.trimIndent()

        val result = dispatcher.dispatch(json)
        
        assertNotNull(result)
        assertEquals("456", result?.commandId)
        assertFalse(result?.success ?: true)
        assertTrue(result?.message?.contains("Unknown command type") ?: false)
    }
    
    @Test
    fun testInvalidJson() {
        val dispatcher = CommandDispatcher()
        val json = "invalid json"
        val result = dispatcher.dispatch(json)
        assertNull(result)
    }
}
