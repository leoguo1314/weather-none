package com.skypulse.weather.agent

interface LLMClient {
    suspend fun generate(prompt: String): String
}

class DemoLLMClient : LLMClient {
    override suspend fun generate(prompt: String): String {
        return "AI Weather Agent response:\n$prompt"
    }
}
