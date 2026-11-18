package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.model.CandidateResolution
import com.example.data.model.CategoryEntity
import com.example.data.model.DiscrepancyType
import com.example.data.model.FundingSource
import com.example.data.model.ImportCandidateEntity
import com.example.data.model.PaymentChannel
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import com.example.domain.finance.MoneyFormatter
import com.example.service.PaymentNotificationListenerService
import com.example.ui.EasySpendUiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.NumericSmallStyle
import com.example.ui.theme.semanticColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsAndImportScreen(
    state: EasySpendUiState,
    initialTab: Int = 0,
    onImportCsvUri: (Uri, PaymentChannel?) -> Unit,
    onLoadDemoQueue: () -> Unit,
    onSimulateNotification: (Boolean) -> Unit,
    onRunAiBatchClassify: () -> Unit,
    onConfirmAllClean: () -> Unit,
    onConfirmCandidate: (Long, String, FundingSource, Boolean, String) -> Unit,
    onSkipOrDismissCandidate: (Long, CandidateResolution) -> Unit,
    onAddManualTransaction: (String, TransactionDirection, PaymentChannel, FundingSource, String, String, String) -> Unit,
    onUpdateTxCategory: (Long, String, Boolean) -> Unit,
    onDeleteTx: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val semantic = MaterialTheme.semanticColors
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var pendingChannelPicker by remember { mutableStateOf<PaymentChannel?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var directionFilter by remember { mutableStateOf<TransactionDirection?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportCsvUri(uri, pendingChannelPicker)
        }
    }

    val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }
    val lifecycleOwner = LocalLifecycleOwner.current
    var listenerEnabled by remember {
        mutableStateOf(PaymentNotificationListenerService.isNotificationAccessEnabled(context))
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = PaymentNotificationListenerService.isNotificationAccessEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.testTag("tab_import_review")
                ) {
                    Text("待核对 (${state.pendingCandidates.size})")
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("tab_formal_ledger")
                ) {
                    Text("已入账 (${state.transactions.size})")
                }
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = semantic.cardBorder,
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "导入微信 / 支付宝账单",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "选择微信或支付宝导出的 CSV 账单文件，或开启支付通知自动抓账。所有新记录都会先帮你查重和预分类，核对无误再写入账本。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            pendingChannelPicker = PaymentChannel.WECHAT_PAY
                                            csvLauncher.launch(arrayOf("text/*", "application/csv", "*/*"))
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("import_wechat_csv_button")
                                    ) {
                                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("微信账单", maxLines = 1)
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            pendingChannelPicker = PaymentChannel.ALIPAY
                                            csvLauncher.launch(arrayOf("text/*", "application/csv", "*/*"))
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("import_alipay_csv_button")
                                    ) {
                                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("支付宝账单", maxLines = 1)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onLoadDemoQueue,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("load_demo_csv_queue_button")
                                    ) {
                                        Text("载入演示账单", maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = { onSimulateNotification(true) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("simulate_wechat_notif_button")
                                    ) {
                                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("模拟通知记账", maxLines = 1)
                                    }
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("notification_listener_status_card")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (listenerEnabled) "通知自动抓账：已开启" else "通知自动抓账：未授权（可先点上方模拟体验）",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    )
                                                }
                                            },
                                            modifier = Modifier.testTag("open_notification_settings_button")
                                        ) {
                                            Text(if (listenerEnabled) "去管理" else "去开启")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (state.pendingCandidates.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = onRunAiBatchClassify,
                                    enabled = !state.isAiClassifying,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ai_batch_classify_button")
                                ) {
                                    if (state.isAiClassifying) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(6.dp))
                                        Text("AI 分类中...")
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("AI 帮我预分类", maxLines = 1)
                                    }
                                }
                                Button(
                                    onClick = onConfirmAllClean,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("confirm_all_clean_button")
                                ) {
                                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("一键确认清晰项", maxLines = 1)
                                }
                            }
                        }
                    }

                    if (state.pendingCandidates.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.Check,
                                title = "待核对列表很干净",
                                subtitle = "点上方「载入演示账单」或导入微信/支付宝 CSV，系统会自动帮你找出重复扣款、退款与拿不准的商户。"
                            )
                        }
                    } else {
                        items(state.pendingCandidates, key = { it.id }) { candidate ->
                            CandidateReviewCard(
                                candidate = candidate,
                                categories = state.categories,
                                categoryMap = categoryMap,
                                onConfirm = onConfirmCandidate,
                                onSkipOrDismiss = onSkipOrDismissCandidate
                            )
                        }
                    }
                }
            } else {
                val filteredTxs = remember(state.transactions, searchQuery, directionFilter) {
                    state.transactions.filter { tx ->
                        val matchQuery = searchQuery.isBlank() ||
                            tx.merchant.contains(searchQuery, true) ||
                            tx.rawDescription.contains(searchQuery, true) ||
                            tx.note.contains(searchQuery, true)
                        val matchDir = directionFilter == null || tx.direction == directionFilter
                        matchQuery && matchDir
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索商家名称或备注...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ledger_search_input")
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = directionFilter == null,
                                onClick = { directionFilter = null },
                                label = { Text("全部") }
                            )
                            TransactionDirection.entries.forEach { dir ->
                                FilterChip(
                                    selected = directionFilter == dir,
                                    onClick = { directionFilter = if (directionFilter == dir) null else dir },
                                    label = { Text(dir.displayName) }
                                )
                            }
                        }
                    }

                    if (filteredTxs.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                title = "还没有已入账记录",
                                subtitle = "你可以点右下角「+」手动记一笔，或先在「待核对」页签确认导入的账单。"
                            )
                        }
                    } else {
                        items(filteredTxs, key = { it.id }) { tx ->
                            val catName = categoryMap[tx.categoryId]?.name ?: tx.categoryId
                            TransactionLedgerCard(
                                tx = tx,
                                categoryName = catName,
                                onEdit = { editingTx = tx },
                                onDelete = { onDeleteTx(tx.id) }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showManualDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_transaction_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_transaction))
        }
    }

    if (showManualDialog) {
        ManualTransactionDialog(
            categories = state.categories,
            onDismiss = { showManualDialog = false },
            onSave = { amt, dir, ch, fs, catId, merchant, note ->
                onAddManualTransaction(amt, dir, ch, fs, catId, merchant, note)
                showManualDialog = false
            }
        )
    }

    editingTx?.let { tx ->
        EditTxCategoryDialog(
            tx = tx,
            categories = state.categories,
            onDismiss = { editingTx = null },
            onSave = { newCatId, rememberPref ->
                onUpdateTxCategory(tx.id, newCatId, rememberPref)
                editingTx = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandidateReviewCard(
    candidate: ImportCandidateEntity,
    categories: List<CategoryEntity>,
    categoryMap: Map<String, CategoryEntity>,
    onConfirm: (Long, String, FundingSource, Boolean, String) -> Unit,
    onSkipOrDismiss: (Long, CandidateResolution) -> Unit
) {
    val semantic = MaterialTheme.semanticColors
    var selectedCategoryId by remember(candidate.id, candidate.suggestedCategoryId) {
        mutableStateOf(candidate.suggestedCategoryId)
    }
    var selectedFunding by remember(candidate.id) { mutableStateOf(candidate.fundingSource) }
    var rememberPref by remember(candidate.id) { mutableStateOf(false) }
    var catMenuExpanded by remember { mutableStateOf(false) }

    val badgeColor = when (candidate.discrepancyType) {
        DiscrepancyType.NONE -> semantic.safe
        DiscrepancyType.POTENTIAL_DUPLICATE -> semantic.danger
        DiscrepancyType.AMOUNT_MISMATCH -> semantic.danger
        DiscrepancyType.REFUND_MATCH -> MaterialTheme.colorScheme.secondary
        DiscrepancyType.UNCERTAIN_CATEGORY -> semantic.warning
    }
    val badgeBg = when (candidate.discrepancyType) {
        DiscrepancyType.NONE -> semantic.safeContainer
        DiscrepancyType.POTENTIAL_DUPLICATE -> semantic.dangerContainer
        DiscrepancyType.AMOUNT_MISMATCH -> semantic.dangerContainer
        DiscrepancyType.REFUND_MATCH -> MaterialTheme.colorScheme.secondaryContainer
        DiscrepancyType.UNCERTAIN_CATEGORY -> semantic.warningContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("candidate_card_${candidate.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = semantic.cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (candidate.discrepancyType != DiscrepancyType.NONE) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = candidate.discrepancyType.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor
                        )
                    }
                }
                Text(
                    text = "${candidate.channel.displayName} · ${MoneyFormatter.formatTimestamp(candidate.occurredAtMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = candidate.merchant,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (candidate.rawDescription.isNotBlank()) {
                        Text(
                            text = candidate.rawDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "${if (candidate.direction == TransactionDirection.INCOME || candidate.direction == TransactionDirection.REFUND) "+" else "-"}${MoneyFormatter.formatFen(candidate.amountFen)}",
                    style = NumericSmallStyle.copy(fontWeight = FontWeight.Bold),
                    color = if (candidate.direction == TransactionDirection.EXPENSE) MaterialTheme.colorScheme.onSurface else semantic.safe
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${candidate.classificationSource.displayName} · ${candidate.discrepancyNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp)
                )
            }

            ExposedDropdownMenuBox(
                expanded = catMenuExpanded,
                onExpandedChange = { catMenuExpanded = !catMenuExpanded }
            ) {
                OutlinedTextField(
                    value = categoryMap[selectedCategoryId]?.name ?: selectedCategoryId,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("分类") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = catMenuExpanded,
                    onDismissRequest = { catMenuExpanded = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text("${cat.name} (${cat.group.displayName})") },
                            onClick = {
                                selectedCategoryId = cat.id
                                if (cat.id == "allowance") selectedFunding = FundingSource.LIVING_ALLOWANCE
                                if (cat.id == "part_time") selectedFunding = FundingSource.PART_TIME_INCOME
                                catMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = rememberPref,
                    onCheckedChange = { rememberPref = it }
                )
                Text(
                    text = "记住「${candidate.merchant}」的常用分类",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (candidate.discrepancyType == DiscrepancyType.POTENTIAL_DUPLICATE) {
                    Button(
                        onClick = { onSkipOrDismiss(candidate.id, CandidateResolution.MERGED_SKIP) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("merge_skip_button_${candidate.id}")
                    ) {
                        Text("跳过重复", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            onConfirm(candidate.id, selectedCategoryId, selectedFunding, rememberPref, "")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("仍要入账", maxLines = 1)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSkipOrDismiss(candidate.id, CandidateResolution.DISMISSED) },
                        modifier = Modifier.testTag("dismiss_candidate_${candidate.id}")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("忽略")
                    }
                    Button(
                        onClick = {
                            onConfirm(candidate.id, selectedCategoryId, selectedFunding, rememberPref, "")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_candidate_${candidate.id}")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("确认入账")
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionLedgerCard(
    tx: TransactionEntity,
    categoryName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val semantic = MaterialTheme.semanticColors
    val isInflow = tx.direction == TransactionDirection.INCOME || tx.direction == TransactionDirection.REFUND

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = semantic.cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tx.merchant,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = categoryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${tx.channel.displayName} · ${MoneyFormatter.formatTimestamp(tx.occurredAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (tx.note.isNotBlank() || tx.rawDescription.isNotBlank()) {
                    Text(
                        text = tx.note.ifBlank { tx.rawDescription },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isInflow) "+" else "-"}${MoneyFormatter.formatFen(tx.amountFen)}",
                    style = NumericSmallStyle.copy(fontWeight = FontWeight.Bold),
                    color = if (isInflow) semantic.safe else MaterialTheme.colorScheme.onSurface
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "修改分类", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除记录", tint = semantic.danger, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualTransactionDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (String, TransactionDirection, PaymentChannel, FundingSource, String, String, String) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(TransactionDirection.EXPENSE) }
    var channel by remember { mutableStateOf(PaymentChannel.WECHAT_PAY) }
    var fundingSource by remember { mutableStateOf(FundingSource.LIVING_ALLOWANCE) }
    var categoryId by remember { mutableStateOf("food") }
    var catExpanded by remember { mutableStateOf(false) }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("手动记一笔") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("金额（元）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_amount_input")
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TransactionDirection.entries.forEach { d ->
                        FilterChip(
                            selected = direction == d,
                            onClick = {
                                direction = d
                                if (d == TransactionDirection.INCOME) categoryId = "part_time"
                                else if (d == TransactionDirection.EXPENSE) categoryId = "food"
                            },
                            label = { Text(d.displayName) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(PaymentChannel.WECHAT_PAY, PaymentChannel.ALIPAY, PaymentChannel.BANK_CARD).forEach { ch ->
                        FilterChip(
                            selected = channel == ch,
                            onClick = { channel = ch },
                            label = { Text(ch.displayName) }
                        )
                    }
                }
                if (direction == TransactionDirection.INCOME) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = fundingSource == FundingSource.LIVING_ALLOWANCE,
                            onClick = {
                                fundingSource = FundingSource.LIVING_ALLOWANCE
                                categoryId = "allowance"
                            },
                            label = { Text("家庭生活费") }
                        )
                        FilterChip(
                            selected = fundingSource == FundingSource.PART_TIME_INCOME,
                            onClick = {
                                fundingSource = FundingSource.PART_TIME_INCOME
                                categoryId = "part_time"
                            },
                            label = { Text("兼职收入") }
                        )
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = !catExpanded }
                ) {
                    OutlinedTextField(
                        value = categoryMap[categoryId]?.name ?: categoryId,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("分类") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = {
                                    categoryId = c.id
                                    catExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("商家或对方名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(amountStr, direction, channel, fundingSource, categoryId, merchant, note)
                },
                modifier = Modifier.testTag("manual_save_button")
            ) {
                Text("保存入账")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun EditTxCategoryDialog(
    tx: TransactionEntity,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit
) {
    var selectedCat by remember(tx.id) { mutableStateOf(tx.categoryId) }
    var rememberPref by remember(tx.id) { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("修改分类：${tx.merchant}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pair.forEach { c ->
                            FilterChip(
                                selected = selectedCat == c.id,
                                onClick = { selectedCat = c.id },
                                label = { Text(c.name) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rememberPref, onCheckedChange = { rememberPref = it })
                    Text("记住此商家分类，下次自动匹配", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedCat, rememberPref) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
