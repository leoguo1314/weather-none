package com.skypulse.weather.ui.agent

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
fun AgentInputBar(
    onSend: (String) -> Unit
) {
    val text = remember { mutableStateOf("") }

    Row {
        OutlinedTextField(
            value = text.value,
            onValueChange = { text.value = it },
            placeholder = { }
        )

        Button(
            onClick = {
                if (text.value.isNotBlank()) {
                    onSend(text.value)
                    text.value = ""
                }
            }
        ) {
            androidx.compose.material3.Text("发送")
        }
    }
}
