package com.skypulse.weather.ui.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SuggestionCard(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = "你可以这样问")
            suggestions.forEach {
                Text(
                    text = "• $it",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionClick(it) }
                        .padding(vertical = 7.dp)
                )
            }
        }
    }
}
