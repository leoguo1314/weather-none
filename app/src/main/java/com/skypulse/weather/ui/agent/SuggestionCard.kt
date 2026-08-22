package com.skypulse.weather.ui.agent

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SuggestionCard(
    suggestions: List<String>
) {
    Card {
        Column {
            Text(text = "你可以问")
            suggestions.forEach {
                Text(text = "• $it")
            }
        }
    }
}
