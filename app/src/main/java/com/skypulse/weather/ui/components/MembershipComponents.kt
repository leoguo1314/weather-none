package com.skypulse.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skypulse.weather.data.ActivationResult

enum class MembershipRole(val title: String, val subtitle: String) {
    Free("普通用户", "基础天气"),
    Premium("完整用户", "全部功能")
}

@Composable
fun VipBadge(modifier: Modifier = Modifier) {
    Text(
        text = "FULL",
        color = Color(0xFF7A4C00),
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(Color(0xFFFFE3A3), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun VipStatusCard(deviceId: String, onMembershipCenter: () -> Unit) {
    Card(
        onClick = onMembershipCenter,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFFFFE9B2), Color(0xFFF6C768)))
                )
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("SkyPulse 完整版", style = MaterialTheme.typography.titleMedium)
                Text("设备 ${deviceId.take(8)} · 永久解锁", style = MaterialTheme.typography.bodySmall)
            }
            VipBadge()
        }
    }
}

@Composable
fun RoleCompareCard(
    role: MembershipRole,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val outline = if (selected) Color(0xFF2563EB) else Color(0xFFD7DFEA)
    Card(
        modifier = modifier.border(1.dp, outline, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(role.title, style = MaterialTheme.typography.titleMedium)
            Text(role.subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
        }
    }
}

@Composable
fun VipUpgradeButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth()
    ) { Text(text) }
}

@Composable
fun FreeUserCard(onUpgrade: () -> Unit) {
    Card(
        onClick = onUpgrade,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF2FF))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("SkyPulse 完整版", style = MaterialTheme.typography.titleMedium)
                Text("解锁全部天气卡片与智能提醒", style = MaterialTheme.typography.bodySmall)
            }
            Text("查看权益", color = Color(0xFF2563EB))
        }
    }
}

@Composable
fun MembershipDialog(
    onDismiss: () -> Unit,
    onActivate: (String) -> ActivationResult,
    deviceId: String
) {
    var code by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SkyPulse 完整版") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("v4 开放构建已默认解锁全部本地天气与 AI 功能。")
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("兼容旧版激活码") },
                    singleLine = true
                )
                status?.let { Text(it, color = Color(0xFF2563EB)) }
                Text("设备标识：${deviceId.take(12)}", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                status = when (onActivate(code.trim())) {
                    ActivationResult.SUCCESS -> "已激活"
                    ActivationResult.ALREADY_ACTIVATED -> "当前版本已解锁全部功能"
                    ActivationResult.INVALID_CODE -> "激活码无效"
                }
            }) { Text("确认") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
