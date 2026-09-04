package com.skypulse.weather.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skypulse.weather.agent.AgentModelConfig

private val suggestions = listOf(
    "明天适合户外跑步吗？",
    "今天怎么穿，需要带伞吗？",
    "空气质量如何，适合开窗吗？",
    "未来三天天气趋势和出行风险"
)

@Composable
fun AgentChatScreen(
    cityId: String?,
    onBack: () -> Unit,
    viewModel: AgentChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(cityId) {
        viewModel.selectCity(cityId)
    }

    LaunchedEffect(state.messages.size, state.isThinking) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF102A56), Color(0xFF315B8F), Color(0xFFF1F6FC))
                )
            )
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("返回", color = Color.White) }
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI 天气助手", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(
                        buildString {
                            state.activeCityName?.let {
                                append(it)
                                append(" · ")
                            }
                            append(
                                if (state.config.isUsable) {
                                    "实时天气工具 + ${state.config.model}"
                                } else {
                                    "实时天气工具 + 本地 Agent"
                                }
                            )
                        },
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { viewModel.clearConversation() }) {
                    Text("清空", color = Color.White)
                }
                TextButton(onClick = { showSettings = true }) {
                    Text("模型", color = Color.White)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    SuggestionCard(
                        suggestions = suggestions,
                        onSuggestionClick = viewModel::sendMessage
                    )
                }
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
                if (state.isThinking) {
                    item {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                            Text("正在调用天气工具并分析……")
                        }
                    }
                }
                state.errorMessage?.let { error ->
                    item {
                        Text(
                            text = error,
                            color = Color(0xFF9A3412),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(4.dp)) }
            }

            AgentInputBar(
                onSend = viewModel::sendMessage,
                enabled = !state.isThinking
            )
        }
    }

    if (showSettings) {
        ModelSettingsDialog(
            config = state.config,
            secureStorageAvailable = state.secureStorageAvailable,
            onDismiss = { showSettings = false },
            onSave = {
                viewModel.saveConfig(it)
                showSettings = false
            }
        )
    }
}

@Composable
private fun MessageBubble(message: AgentMessage) {
    val isUser = message.role == MessageRole.USER
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.82f else 0.94f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) Color(0xFF2563EB) else Color.White.copy(alpha = 0.94f)
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = message.content,
                    color = if (isUser) Color.White else Color(0xFF16233B)
                )
                if (!isUser) {
                    Text(
                        text = if (message.source == ResponseSource.EXTERNAL_MODEL) "外部模型综合" else "本地 Agent 推理",
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        if (message.traces.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            ToolTraceCard(message.traces)
        }
    }
}

@Composable
private fun ModelSettingsDialog(
    config: AgentModelConfig,
    secureStorageAvailable: Boolean,
    onDismiss: () -> Unit,
    onSave: (AgentModelConfig) -> Unit
) {
    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var baseUrl by remember(config) { mutableStateOf(config.baseUrl) }
    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var model by remember(config) { mutableStateOf(config.model) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OpenAI 兼容模型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用外部模型", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Text("不配置也能使用本地 Agent。支持 OpenAI、OneAPI、Ollama 等兼容接口。")
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://example.com/v1") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名称") },
                    placeholder = { Text("gpt-4.1-mini") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key（可空）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Text(
                    if (secureStorageAvailable) {
                        "配置在设备端加密保存；局域网 HTTP 仅用于本地模型。"
                    } else {
                        "当前设备无法使用加密存储；API Key 仅在本次运行中保留，重启后需重新输入。"
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        AgentModelConfig(
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey.trim(),
                            model = model.trim(),
                            enabled = enabled
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
