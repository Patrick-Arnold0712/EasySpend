package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.LocalAccount
import com.example.ui.motion.AtmosphericHeroCard
import com.example.ui.motion.FluidSegmentOption
import com.example.ui.motion.FluidSegmentedBar
import com.example.ui.motion.SpringAnimatedVisibility
import com.example.ui.motion.StaggeredEntrance
import com.example.ui.theme.semanticColors

@Composable
fun AuthScreen(
    existingAccounts: List<LocalAccount>,
    authErrorMessage: String?,
    onClearError: () -> Unit,
    onLogin: (username: String, password: String) -> Unit,
    onRegister: (username: String, password: String, nickname: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = MaterialTheme.semanticColors
    var isRegisterMode by rememberSaveable(existingAccounts.isEmpty()) {
        mutableStateOf(existingAccounts.isEmpty())
    }
    var username by rememberSaveable { mutableStateOf(existingAccounts.firstOrNull()?.username.orEmpty()) }
    var nickname by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var localValidationError by remember { mutableStateOf<String?>(null) }

    val activeError = localValidationError ?: authErrorMessage

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sculptural Atmospheric Brand Header
            StaggeredEntrance(index = 0) {
                AtmosphericHeroCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(semantic.heroPillBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = semantic.heroTextPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "易支道 EasySpend",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = semantic.heroTextPrimary
                                )
                                Text(
                                    text = "个人智能记账 · 多账号本地独立空间",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semantic.heroTextSecondary
                                )
                            }
                        }

                        Surface(
                            color = semantic.heroPillBackground,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = semantic.heroTextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "账号密码本地加盐加密验证，每个账号拥有独立本地账本、分类与预算数据。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = semantic.heroTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Main Auth Card with Fluid Sliding Mode Pill
            StaggeredEntrance(index = 1) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = semantic.cardBorder,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        FluidSegmentedBar(
                            options = listOf(
                                FluidSegmentOption("账号登录", "auth_mode_login"),
                                FluidSegmentOption("注册新账号", "auth_mode_register")
                            ),
                            selectedIndex = if (isRegisterMode) 1 else 0,
                            onSelect = { idx ->
                                isRegisterMode = idx == 1
                                localValidationError = null
                                onClearError()
                            }
                        )

                        // Existing local accounts quick-select in Login mode
                        SpringAnimatedVisibility(visible = !isRegisterMode && existingAccounts.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "本机已注册账号（点选快速填入）：",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    existingAccounts.forEach { acc ->
                                        FilterChip(
                                            selected = username.equals(acc.username, ignoreCase = true),
                                            onClick = {
                                                username = acc.username
                                                localValidationError = null
                                                onClearError()
                                            },
                                            label = {
                                                Text(
                                                    text = if (acc.nickname != acc.username) "${acc.nickname} (${acc.username})" else acc.username,
                                                    maxLines = 1
                                                )
                                            },
                                            modifier = Modifier.testTag("quick_account_${acc.username}")
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                localValidationError = null
                                onClearError()
                            },
                            label = { Text("账号名（2~24 位字母/数字/汉字）") },
                            placeholder = { Text("例如：xiaoming") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_username_input")
                        )

                        SpringAnimatedVisibility(visible = isRegisterMode) {
                            OutlinedTextField(
                                value = nickname,
                                onValueChange = {
                                    nickname = it
                                    localValidationError = null
                                    onClearError()
                                },
                                label = { Text("个人昵称（选填）") },
                                placeholder = { Text("不填则默认使用账号名") },
                                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_nickname_input")
                            )
                        }

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                localValidationError = null
                                onClearError()
                            },
                            label = { Text("登录密码（至少 6 位）") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = if (isRegisterMode) ImeAction.Next else ImeAction.Done
                            ),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_input")
                        )

                        SpringAnimatedVisibility(visible = isRegisterMode) {
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    localValidationError = null
                                    onClearError()
                                },
                                label = { Text("确认密码") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_confirm_password_input")
                            )
                        }

                        SpringAnimatedVisibility(visible = !activeError.isNullOrBlank()) {
                            Surface(
                                color = semantic.dangerContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_error_banner")
                            ) {
                                Text(
                                    text = activeError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = semantic.danger,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                localValidationError = null
                                onClearError()
                                if (isRegisterMode) {
                                    when {
                                        username.trim().length < 2 ->
                                            localValidationError = "账号名至少需要 2 个字符"
                                        password.length < 6 ->
                                            localValidationError = "密码长度不能少于 6 位"
                                        password != confirmPassword ->
                                            localValidationError = "两次输入的密码不一致，请核对"
                                        else ->
                                            onRegister(username, password, nickname)
                                    }
                                } else {
                                    when {
                                        username.isBlank() ->
                                            localValidationError = "请输入账号名"
                                        password.isBlank() ->
                                            localValidationError = "请输入登录密码"
                                        else ->
                                            onLogin(username, password)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(vertical = 15.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_submit_button")
                        ) {
                            AnimatedContent(
                                targetState = isRegisterMode,
                                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                                label = "authSubmitLabel"
                            ) { reg ->
                                Text(
                                    text = if (reg) "注册并进入专属账本" else "登录账本",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
