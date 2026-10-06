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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skypulse.weather.agent.AgentModelConfig
import com.skypulse.weather.agent.AgentModelSettings
import com.skypulse.weather.agent.AgentProviderConfig
import com.skypulse.weather.agent.ModelProviderPreset
import com.skypulse.weather.agent.resolveOpenAiChatEndpoint
import com.skypulse.weather.agent.modelEndpointDisplay
import java.util.UUID

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
                                    "实时天气工具 + ${state.config.providerName} / ${state.config.model}"
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
            settings = state.modelSettings,
            secureStorageAvailable = state.secureStorageAvailable,
            isTestingModel = state.isTestingModel,
            testMessage = state.modelTestMessage,
            onDismiss = { showSettings = false },
            onSave = {
                viewModel.saveModelSettings(it)
                showSettings = false
            },
            onTest = viewModel::testModel
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
internal fun ModelSettingsDialog(
    settings: AgentModelSettings,
    secureStorageAvailable: Boolean,
    isTestingModel: Boolean,
    testMessage: String?,
    onDismiss: () -> Unit,
    onSave: (AgentModelSettings) -> Unit,
    onTest: (AgentModelConfig) -> Unit
) {
    var providers by remember(settings) { mutableStateOf(settings.providers) }
    var enabled by remember(settings) { mutableStateOf(settings.externalModelEnabled) }
    var activeProviderId by remember(settings) { mutableStateOf(settings.activeProviderId) }
    var activeModel by remember(settings) { mutableStateOf(settings.activeModel) }
    var expandedProviderId by remember(settings) { mutableStateOf(settings.activeProviderId) }
    var editingProvider by remember { mutableStateOf<AgentProviderConfig?>(null) }
    var addingProvider by remember { mutableStateOf(false) }

    if (addingProvider || editingProvider != null) {
        ProviderEditorDialog(
            initial = editingProvider,
            secureStorageAvailable = secureStorageAvailable,
            onDismiss = {
                addingProvider = false
                editingProvider = null
            },
            onSave = { provider ->
                providers = if (editingProvider == null) {
                    providers + provider
                } else {
                    providers.map { if (it.id == provider.id) provider else it }
                }
                if (activeProviderId == null) {
                    activeProviderId = provider.id
                    activeModel = provider.models.firstOrNull()
                } else if (activeProviderId == provider.id && activeModel !in provider.models) {
                    activeModel = provider.models.firstOrNull()
                }
                expandedProviderId = provider.id
                addingProvider = false
                editingProvider = null
            }
        )
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("模型厂商", style = MaterialTheme.typography.titleLarge)
                Text(
                    "支持多个 OpenAI 兼容厂商；点击厂商展开模型列表。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用外部模型", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, modifier = Modifier.testTag("external-model-enabled"))
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (providers.isEmpty()) {
                        Text(
                            "尚未配置模型厂商。未配置时继续使用本地天气 Agent。",
                            modifier = Modifier.padding(vertical = 18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    providers.forEach { provider ->
                        ProviderCard(
                            provider = provider,
                            expanded = expandedProviderId == provider.id,
                            activeProviderId = activeProviderId,
                            activeModel = activeModel,
                            isTestingModel = isTestingModel,
                            onToggle = {
                                expandedProviderId = if (expandedProviderId == provider.id) null else provider.id
                            },
                            onSelectModel = { model ->
                                activeProviderId = provider.id
                                activeModel = model
                            },
                            onTest = { model -> onTest(provider.modelConfig(model)) },
                            onEdit = { editingProvider = provider },
                            onDelete = {
                                providers = providers.filterNot { it.id == provider.id }
                                if (activeProviderId == provider.id) {
                                    val replacement = providers.firstOrNull()
                                    activeProviderId = replacement?.id
                                    activeModel = replacement?.models?.firstOrNull()
                                }
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = { addingProvider = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("添加模型厂商")
                    }
                    testMessage?.let { message ->
                        Text(
                            text = message,
                            color = if (message.contains("连接成功")) {
                                Color(0xFF166534)
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        if (secureStorageAvailable) {
                            "API Key 在设备端加密保存；局域网 HTTP 仅用于本地模型。"
                        } else {
                            "当前设备无法使用加密存储；API Key 仅在本次运行中保留。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = {
                            onSave(
                                AgentModelSettings(
                                    providers = providers,
                                    activeProviderId = activeProviderId,
                                    activeModel = activeModel,
                                    externalModelEnabled = enabled
                                ).normalized()
                            )
                        }
                    ) { Text("保存") }
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: AgentProviderConfig,
    expanded: Boolean,
    activeProviderId: String?,
    activeModel: String?,
    isTestingModel: Boolean,
    onToggle: () -> Unit,
    onSelectModel: (String) -> Unit,
    onTest: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isActiveProvider = activeProviderId == provider.id
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActiveProvider) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggle, modifier = Modifier.weight(1f).testTag("provider-${provider.id}")) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(provider.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${provider.models.size} 个模型 · ${if (expanded) "收起" else "展开"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                TextButton(onClick = onEdit, modifier = Modifier.testTag("edit-${provider.id}")) { Text("编辑") }
                TextButton(onClick = onDelete, modifier = Modifier.testTag("delete-${provider.id}")) { Text("删除") }
            }

            if (expanded) {
                Text(
                    resolveEndpointPreview(provider.baseUrl),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                if (provider.models.isEmpty()) {
                    Text("尚未配置模型，请点击编辑添加。", modifier = Modifier.padding(8.dp))
                }
                provider.models.forEach { model ->
                    val selected = isActiveProvider && activeModel == model
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { onSelectModel(model) })
                        TextButton(
                            onClick = { onSelectModel(model) },
                            modifier = Modifier.weight(1f).testTag("model-${provider.id}-$model")
                        ) {
                            Text(model, modifier = Modifier.fillMaxWidth())
                        }
                        TextButton(
                            onClick = { onTest(model) },
                            enabled = !isTestingModel,
                            modifier = Modifier.testTag("test-${provider.id}-$model")
                        ) {
                            Text(if (isTestingModel) "测试中" else "测试")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderEditorDialog(
    initial: AgentProviderConfig?,
    secureStorageAvailable: Boolean,
    onDismiss: () -> Unit,
    onSave: (AgentProviderConfig) -> Unit
) {
    var preset by remember(initial) { mutableStateOf(initial?.preset ?: ModelProviderPreset.OPENAI) }
    var name by remember(initial) { mutableStateOf(initial?.name ?: preset.displayName) }
    var baseUrl by remember(initial) { mutableStateOf(initial?.baseUrl ?: preset.defaultBaseUrl) }
    var apiKey by remember(initial) { mutableStateOf(initial?.apiKey.orEmpty()) }
    var modelsText by remember(initial) {
        mutableStateOf((initial?.models ?: preset.suggestedModels).joinToString("\n"))
    }
    var presetMenuExpanded by remember { mutableStateOf(false) }

    val models = modelsText.split(Regex("[,，\\n]"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    val endpointIsValid = runCatching { resolveOpenAiChatEndpoint(baseUrl) }.isSuccess &&
        !baseUrl.contains("your-oneapi.example.com")
    val canSave = name.isNotBlank() && models.isNotEmpty() && endpointIsValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加模型厂商" else "编辑模型厂商") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    OutlinedButton(
                        onClick = { presetMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("厂商类型：${preset.displayName}") }
                    DropdownMenu(
                        expanded = presetMenuExpanded,
                        onDismissRequest = { presetMenuExpanded = false }
                    ) {
                        ModelProviderPreset.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    val previous = preset
                                    preset = option
                                    if (name.isBlank() || name == previous.displayName) name = option.displayName
                                    if (baseUrl.isBlank() || baseUrl == previous.defaultBaseUrl) {
                                        baseUrl = option.defaultBaseUrl
                                    }
                                    if (modelsText.isBlank() || initial == null) {
                                        modelsText = option.suggestedModels.joinToString("\n")
                                    }
                                    presetMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("厂商显示名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL 或完整接口") },
                    placeholder = { Text("https://example.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    resolveEndpointPreview(baseUrl),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key（可空）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = modelsText,
                    onValueChange = { modelsText = it },
                    label = { Text("模型列表（每行一个）") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                if (preset == ModelProviderPreset.OLLAMA || preset == ModelProviderPreset.LM_STUDIO) {
                    Text(
                        "若模型服务运行在电脑上，请把 127.0.0.1 改成电脑的局域网 IP，并启用跨设备访问。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9A3412)
                    )
                }
                Text(
                    if (secureStorageAvailable) "API Key 将加密保存。" else "API Key 仅在本次运行中保留。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    onSave(
                        AgentProviderConfig(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            preset = preset,
                            name = name,
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            models = models
                        ).normalized()
                    )
                }
            ) { Text("保存厂商") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("返回") } }
    )
}

private fun resolveEndpointPreview(baseUrl: String): String = runCatching {
    "实际请求：${modelEndpointDisplay(baseUrl)}"
}.getOrElse { "地址提示：${it.message.orEmpty()}" }
