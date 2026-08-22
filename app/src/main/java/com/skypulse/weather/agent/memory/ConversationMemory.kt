package com.skypulse.weather.agent.memory

/**
 * Lightweight conversation memory for SkyPulse Agent.
 * Stores recent user interactions and provides context for future reasoning.
 */
class ConversationMemory(private val maxSize: Int = 20) {

    private val messages = mutableListOf<String>()

    fun add(message: String) {
        messages.add(message)
        if (messages.size > maxSize) {
            messages.removeAt(0)
        }
    }

    fun recent(): List<String> = messages.toList()

    fun clear() {
        messages.clear()
    }
}
