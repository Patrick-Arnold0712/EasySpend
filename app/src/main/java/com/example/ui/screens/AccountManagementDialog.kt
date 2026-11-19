package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.LocalAccount
import com.example.domain.finance.MoneyFormatter
import com.example.ui.theme.semanticColors

@Composable
fun AccountManagementDialog(
    currentAccount: LocalAccount,
    allAccountsCount: Int,
    onUpdateNickname: (String) -> Unit,
    onChangePassword: (oldPwd: String, newPwd: String) -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = MaterialTheme.semanticColors
    var nickname by remember(currentAccount.nickname) { mutableStateOf(currentAccount.nickname) }
    var showPasswordEditor by remember { mutableStateOf(false) }
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirmNewPwd by remember { mutableStateOf("") }
    var pwdError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("本地账号与数据空间", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Current Account Summary Card
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentAccount.displayLabel,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "账号名：${currentAccount.username} · 创建于 ${MoneyFormatter.formatTimestamp(currentAccount.createdAtMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "独立本地存储已启用（本机共 $allAccountsCount 个账号）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Nickname Update
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("修改个人昵称", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it },
                            label = { Text("昵称") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("account_nickname_input")
                        )
                        Button(
                            onClick = { onUpdateNickname(nickname) },
                            enabled = nickname.trim().isNotEmpty() && nickname.trim() != currentAccount.nickname,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier.testTag("save_nickname_button")
                        ) {
                            Text("保存")
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)

                // Change Password Section
                OutlinedButton(
                    onClick = { showPasswordEditor = !showPasswordEditor },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toggle_change_password_button")
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (showPasswordEditor) "收起修改密码" else "修改登录密码")
                }

                AnimatedVisibility(visible = showPasswordEditor) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = oldPwd,
                            onValueChange = { oldPwd = it; pwdError = null },
                            label = { Text("原密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPwd,
                            onValueChange = { newPwd = it; pwdError = null },
                            label = { Text("新密码（至少 6 位）") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirmNewPwd,
                            onValueChange = { confirmNewPwd = it; pwdError = null },
                            label = { Text("确认新密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!pwdError.isNullOrBlank()) {
                            Text(
                                text = pwdError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.danger
                            )
                        }
                        Button(
                            onClick = {
                                when {
                                    oldPwd.isBlank() -> pwdError = "请输入原密码"
                                    newPwd.length < 6 -> pwdError = "新密码不能少于 6 位"
                                    newPwd != confirmNewPwd -> pwdError = "两次输入的新密码不一致"
                                    else -> {
                                        onChangePassword(oldPwd, newPwd)
                                        oldPwd = ""
                                        newPwd = ""
                                        confirmNewPwd = ""
                                        showPasswordEditor = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("确认修改密码")
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)

                // Switch / Logout
                Button(
                    onClick = {
                        onLogout()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = semantic.dangerContainer,
                        contentColor = semantic.danger
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("logout_account_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("退出登录 / 切换账号")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
