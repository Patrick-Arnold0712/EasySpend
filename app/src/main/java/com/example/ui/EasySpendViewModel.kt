package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AgentChatReply
import com.example.ai.ApiConfig
import com.example.ai.ConversationTurn
import com.example.ai.EasySpendAiService
import com.example.ai.PendingBudgetProposal
import com.example.ai.ProposalStatus
import com.example.ai.SpendingInsightReport
import com.example.ai.ToolExecutionResult
import com.example.ai.toApiConfig
import com.example.data.local.AccountState
import com.example.data.local.ApiProviderType
import com.example.data.local.EasySpendDatabase
import com.example.data.local.LocalAccount
import com.example.data.local.LocalAccountStore
import com.example.data.local.ThemeMode
import com.example.data.local.UserSettings
import com.example.data.local.UserSettingsDataStore
import com.example.data.model.AgentToolLogEntity
import com.example.data.model.CandidateResolution
import com.example.data.model.CategoryEntity
import com.example.data.model.CategoryGroup
import com.example.data.model.ClassificationSource
import com.example.data.model.FundVault
import com.example.data.model.FundingSource
import com.example.data.model.ImportBatchEntity
import com.example.data.model.ImportCandidateEntity
import com.example.data.model.MerchantPreferenceEntity
import com.example.data.model.PaymentChannel
import com.example.data.model.SavingsGoalEntity
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import com.example.data.repository.EasySpendRepository
import com.example.domain.finance.MoneyFormatter
import com.example.domain.finance.MonthlyFinancialSummary
import com.example.domain.finance.XlsxStatementParser
import com.example.service.LocationContextResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import java.util.UUID

sealed interface AiInsightState {
    data object Idle : AiInsightState
    data object Loading : AiInsightState
    data class Success(val report: SpendingInsightReport) : AiInsightState
    data class Error(val message: String) : AiInsightState
}

data class AgentChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val toolTraces: List<ToolExecutionResult> = emptyList(),
    val proposals: List<PendingBudgetProposal> = emptyList(),
    val isError: Boolean = false
)

data class ApiTestFeedback(
    val isTesting: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean = false
)

private fun defaultWelcomeMessages(): List<AgentChatMessage> = listOf(
    AgentChatMessage(
        isUser = false,
        text = "你好，我是易支道财务助手。配置上方 API Key 后，可直接问我存钱空间、超支风险或预算调整建议："
    )
)

private sealed interface LedgerUndoAction {
    val label: String

    data class DeletedTransaction(
        val tx: TransactionEntity,
        override val label: String
    ) : LedgerUndoAction

    data class UpdatedCategory(
        val txId: Long,
        val previousCategoryId: String,
        val previousVault: FundVault,
        val previousSource: ClassificationSource,
        override val label: String
    ) : LedgerUndoAction

    data class ConfirmedCandidate(
        val insertedTxId: Long,
        val candidateId: Long,
        override val label: String
    ) : LedgerUndoAction

    data class AddedManualTransaction(
        val insertedTxId: Long,
        override val label: String
    ) : LedgerUndoAction
}

data class EasySpendUiState(
    val accountState: AccountState = AccountState(),
    val authErrorMessage: String? = null,
    val settings: UserSettings = UserSettings(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val allTransactions: List<TransactionEntity> = emptyList(),
    val pendingCandidates: List<ImportCandidateEntity> = emptyList(),
    val importBatches: List<ImportBatchEntity> = emptyList(),
    val merchantPreferences: List<MerchantPreferenceEntity> = emptyList(),
    val toolLogs: List<AgentToolLogEntity> = emptyList(),
    val savingsGoal: SavingsGoalEntity? = null,
    val summary: MonthlyFinancialSummary? = null,
    val aiInsightState: AiInsightState = AiInsightState.Idle,
    val isAiClassifying: Boolean = false,
    val isAgentResponding: Boolean = false,
    val agentProgressText: String? = null,
    val streamingAgentText: String = "",
    val agentMessages: List<AgentChatMessage> = defaultWelcomeMessages(),
    val apiTestFeedback: ApiTestFeedback = ApiTestFeedback(),
    val bannerMessage: String? = null,
    val isReplayingOnboarding: Boolean = false,
    val lastUndoLabel: String? = null
)

private data class AccountSession(
    val account: LocalAccount,
    val repository: EasySpendRepository,
    val settingsStore: UserSettingsDataStore,
    val aiService: EasySpendAiService
)

private data class TransientUiState(
    val authError: String?,
    val aiInsightState: AiInsightState,
    val isAiClassifying: Boolean,
    val isAgentResponding: Boolean,
    val agentProgressText: String?,
    val streamingAgentText: String,
    val agentMessages: List<AgentChatMessage>,
    val apiTestFeedback: ApiTestFeedback,
    val bannerMessage: String?,
    val isReplayingOnboarding: Boolean,
    val lastUndoLabel: String?
)

class EasySpendViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val accountStore = LocalAccountStore(appContext)

    @Volatile
    private var activeSession: AccountSession? = null

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    private val _aiInsightState = MutableStateFlow<AiInsightState>(AiInsightState.Idle)
    private val _isAiClassifying = MutableStateFlow(false)
    private val _isAgentResponding = MutableStateFlow(false)
    private val _agentProgressText = MutableStateFlow<String?>(null)
    private val _streamingAgentText = MutableStateFlow("")
    private val _agentMessages = MutableStateFlow(defaultWelcomeMessages())
    private val _apiTestFeedback = MutableStateFlow(ApiTestFeedback())
    private val _bannerMessage = MutableStateFlow<String?>(null)
    private val _isReplayingOnboarding = MutableStateFlow(false)
    private val _lastUndoLabel = MutableStateFlow<String?>(null)

    private val undoStack = ArrayDeque<LedgerUndoAction>()
    private val conversationHistory = mutableListOf<ConversationTurn>()

    private fun pushUndoAction(action: LedgerUndoAction) {
        if (undoStack.size >= 20) {
            undoStack.removeFirst()
        }
        undoStack.addLast(action)
        _lastUndoLabel.value = action.label
    }

    private fun getOrCreateSession(account: LocalAccount): AccountSession {
        val current = activeSession
        if (current != null && current.account.id == account.id) {
            return current.copy(account = account).also { activeSession = it }
        }
        val db = EasySpendDatabase.getInstanceForAccount(appContext, account.id)
        val repo = EasySpendRepository(db)
        val settingsStore = UserSettingsDataStore(appContext, account.id)
        val aiService = EasySpendAiService(repo)
        val created = AccountSession(account, repo, settingsStore, aiService)
        activeSession = created
        viewModelScope.launch {
            val activeMonth = settingsStore.settingsFlow.first().activeMonthKey
            repo.ensureInitialized(activeMonth)
        }
        return created
    }

    private val transientFlow = combine(
        _authErrorMessage,
        _aiInsightState,
        _isAiClassifying,
        _isAgentResponding,
        _agentProgressText,
        _streamingAgentText,
        _agentMessages,
        _apiTestFeedback,
        _bannerMessage,
        _isReplayingOnboarding,
        _lastUndoLabel
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        TransientUiState(
            authError = arr[0] as String?,
            aiInsightState = arr[1] as AiInsightState,
            isAiClassifying = arr[2] as Boolean,
            isAgentResponding = arr[3] as Boolean,
            agentProgressText = arr[4] as String?,
            streamingAgentText = arr[5] as String,
            agentMessages = arr[6] as List<AgentChatMessage>,
            apiTestFeedback = arr[7] as ApiTestFeedback,
            bannerMessage = arr[8] as String?,
            isReplayingOnboarding = arr[9] as Boolean,
            lastUndoLabel = arr[10] as String?
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<EasySpendUiState> = accountStore.accountStateFlow.flatMapLatest { accState ->
        val currentAcc = accState.currentAccount
        if (currentAcc == null) {
            activeSession = null
            combine(flowOf(accState), transientFlow) { st, tr ->
                EasySpendUiState(
                    accountState = st,
                    authErrorMessage = tr.authError,
                    bannerMessage = tr.bannerMessage
                )
            }
        } else {
            val session = getOrCreateSession(currentAcc)
            val repo = session.repository
            val monthDataFlow = session.settingsStore.settingsFlow.flatMapLatest { settings ->
                combine(
                    repo.observeTransactionsForMonth(settings.activeMonthKey),
                    repo.observeMonthlySummary(settings.activeMonthKey),
                    repo.observeSavingsGoalForMonth(settings.activeMonthKey)
                ) { txs, summary, goal ->
                    Triple(txs, summary, goal)
                }
            }
            combine(
                session.settingsStore.settingsFlow,
                repo.categoriesFlow,
                monthDataFlow,
                repo.allTransactionsFlow,
                repo.pendingCandidatesFlow,
                repo.importBatchesFlow,
                repo.merchantPreferencesFlow,
                repo.toolLogsFlow,
                transientFlow
            ) { values ->
                val settings = values[0] as UserSettings
                @Suppress("UNCHECKED_CAST")
                val categories = values[1] as List<CategoryEntity>
                @Suppress("UNCHECKED_CAST")
                val monthTriple = values[2] as Triple<List<TransactionEntity>, MonthlyFinancialSummary, SavingsGoalEntity?>
                @Suppress("UNCHECKED_CAST")
                val allTransactions = values[3] as List<TransactionEntity>
                @Suppress("UNCHECKED_CAST")
                val pendingCandidates = values[4] as List<ImportCandidateEntity>
                @Suppress("UNCHECKED_CAST")
                val batches = values[5] as List<ImportBatchEntity>
                @Suppress("UNCHECKED_CAST")
                val prefs = values[6] as List<MerchantPreferenceEntity>
                @Suppress("UNCHECKED_CAST")
                val logs = values[7] as List<AgentToolLogEntity>
                val tr = values[8] as TransientUiState

                EasySpendUiState(
                    accountState = accState,
                    authErrorMessage = tr.authError,
                    settings = settings,
                    categories = categories,
                    transactions = monthTriple.first,
                    allTransactions = allTransactions,
                    summary = monthTriple.second,
                    savingsGoal = monthTriple.third,
                    pendingCandidates = pendingCandidates,
                    importBatches = batches,
                    merchantPreferences = prefs,
                    toolLogs = logs,
                    aiInsightState = tr.aiInsightState,
                    isAiClassifying = tr.isAiClassifying,
                    isAgentResponding = tr.isAgentResponding,
                    agentProgressText = tr.agentProgressText,
                    streamingAgentText = tr.streamingAgentText,
                    agentMessages = tr.agentMessages,
                    apiTestFeedback = tr.apiTestFeedback,
                    bannerMessage = tr.bannerMessage,
                    isReplayingOnboarding = tr.isReplayingOnboarding,
                    lastUndoLabel = tr.lastUndoLabel
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = EasySpendUiState()
    )

    private fun resetSessionTransientState() {
        conversationHistory.clear()
        undoStack.clear()
        _lastUndoLabel.value = null
        _aiInsightState.value = AiInsightState.Idle
        _isAiClassifying.value = false
        _isAgentResponding.value = false
        _agentProgressText.value = null
        _streamingAgentText.value = ""
        _agentMessages.value = defaultWelcomeMessages()
        _apiTestFeedback.value = ApiTestFeedback()
        _authErrorMessage.value = null
        _isReplayingOnboarding.value = false
    }

    // --- Account Authentication & Management ---

    fun clearAuthError() {
        _authErrorMessage.value = null
    }

    fun registerAccount(username: String, password: String, nickname: String) {
        viewModelScope.launch {
            _authErrorMessage.value = null
            val res = accountStore.register(username, password, nickname)
            res.onSuccess { acc ->
                resetSessionTransientState()
                getOrCreateSession(acc)
                _bannerMessage.value = "欢迎，${acc.displayLabel}！已为您创建独立的本地账本空间。"
            }.onFailure { err ->
                _authErrorMessage.value = err.localizedMessage ?: "注册失败，请检查输入"
            }
        }
    }

    fun loginAccount(username: String, password: String) {
        viewModelScope.launch {
            _authErrorMessage.value = null
            val res = accountStore.login(username, password)
            res.onSuccess { acc ->
                resetSessionTransientState()
                getOrCreateSession(acc)
                _bannerMessage.value = "欢迎回来，${acc.displayLabel}！"
            }.onFailure { err ->
                _authErrorMessage.value = err.localizedMessage ?: "登录失败"
            }
        }
    }

    fun logoutAccount() {
        viewModelScope.launch {
            accountStore.logout()
            activeSession = null
            resetSessionTransientState()
            _bannerMessage.value = null
        }
    }

    fun updateAccountNickname(newNickname: String) {
        val currentAcc = activeSession?.account ?: return
        viewModelScope.launch {
            val res = accountStore.updateNickname(currentAcc.id, newNickname)
            res.onSuccess { updated ->
                _bannerMessage.value = "昵称已更新为「${updated.displayLabel}」。"
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "修改昵称失败"
            }
        }
    }

    fun changeAccountPassword(oldPassword: String, newPassword: String) {
        val currentAcc = activeSession?.account ?: return
        viewModelScope.launch {
            val res = accountStore.changePassword(currentAcc.id, oldPassword, newPassword)
            res.onSuccess {
                _bannerMessage.value = "登录密码已安全更新。"
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "修改密码失败"
            }
        }
    }

    // --- Custom Category Management ---

    fun createCustomCategory(
        name: String,
        group: CategoryGroup,
        keywords: String,
        monthlyLimitYuanStr: String
    ) {
        val session = activeSession ?: return
        viewModelScope.launch {
            val month = uiState.value.settings.activeMonthKey
            val limitFen = if (monthlyLimitYuanStr.isBlank()) null else MoneyFormatter.parseYuanToFen(monthlyLimitYuanStr)
            val res = session.repository.createCustomCategory(
                name = name,
                group = group,
                keywords = keywords,
                monthlyLimitFen = limitFen,
                monthKey = month
            )
            res.onSuccess { cat ->
                _bannerMessage.value = "已创建自定义分类「${cat.name}」。"
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "创建分类失败"
            }
        }
    }

    fun updateCustomCategory(
        categoryId: String,
        name: String,
        group: CategoryGroup,
        keywords: String
    ) {
        val session = activeSession ?: return
        viewModelScope.launch {
            val res = session.repository.updateCustomCategory(categoryId, name, group, keywords)
            res.onSuccess { cat ->
                _bannerMessage.value = "已更新分类「${cat.name}」。"
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "更新分类失败"
            }
        }
    }

    fun deleteCustomCategory(categoryId: String) {
        val session = activeSession ?: return
        viewModelScope.launch {
            val res = session.repository.deleteCustomCategory(categoryId)
            res.onSuccess {
                _bannerMessage.value = "已删除该自定义分类，原记录已自动移至其他分类。"
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "删除分类失败"
            }
        }
    }

    // --- Core Ledger, Demo, Import, Budget & Agent Actions ---

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun showBanner(message: String) {
        _bannerMessage.value = message
    }

    fun loadDemoIntoReviewQueue() {
        val session = activeSession ?: return
        viewModelScope.launch {
            val month = uiState.value.settings.activeMonthKey
            session.repository.loadCompetitionDemoIntoQueue(month)
            _bannerMessage.value = "已加载演示账单到「待核对」，你可以逐笔体验查重与分类确认。"
        }
    }

    fun loadDemoCommittedBaseline() {
        val session = activeSession ?: return
        viewModelScope.launch {
            val month = uiState.value.settings.activeMonthKey
            session.repository.loadCompetitionDemoCommittedBaseline(month)
            _bannerMessage.value = "已载入本月体验账单，其中几笔重复和退款记录已留在「待核对」等你处理。"
        }
    }

    fun addDemo300EssentialExpense() {
        val session = activeSession ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            session.repository.addManualTransaction(
                amountFen = 30000L,
                direction = TransactionDirection.EXPENSE,
                channel = PaymentChannel.WECHAT_PAY,
                fundingSource = FundingSource.LIVING_ALLOWANCE,
                categoryId = "study",
                merchant = "中山大学教材服务站",
                note = "专业实验教材与四六级复习资料（必要支出）",
                occurredAtMillis = now
            )
            _bannerMessage.value = "已记录一笔 ¥300.00 学习资料费，预算和存钱进度已自动重算。"
        }
    }

    fun importCsvFromUri(context: Context, uri: Uri, preferredChannel: PaymentChannel? = null) {
        val session = activeSession ?: return
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("无法读取所选文件")
                }
                val isXlsx = XlsxStatementParser.isXlsxBytes(bytes)
                val sourceLabel = "${preferredChannel?.displayName ?: "账单"}${if (isXlsx) "Excel" else "CSV"}导入"
                val result = if (isXlsx) {
                    session.repository.importXlsxIntoReviewQueue(
                        xlsxBytes = bytes,
                        sourceLabel = sourceLabel,
                        preferredChannel = preferredChannel
                    )
                } else {
                    val content = withContext(Dispatchers.Default) { decodeStatementBytes(bytes) }
                    session.repository.importCsvIntoReviewQueue(
                        csvContent = content,
                        sourceLabel = sourceLabel,
                        preferredChannel = preferredChannel
                    )
                }
                result.onSuccess { batch ->
                    _bannerMessage.value = "已解析 ${batch.totalCount} 笔记录（正常 ${batch.cleanCount} 笔，差异/存疑 ${batch.discrepancyCount} 笔），请核对后入账。"
                }.onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "账单解析失败"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Statement import failed", e)
                _bannerMessage.value = "导入失败：${e.localizedMessage ?: "文件读取异常"}"
            }
        }
    }

    fun exportLedgerToUri(context: Context, uri: Uri) {
        val session = activeSession ?: return
        viewModelScope.launch {
            try {
                val csv = session.repository.exportAllTransactionsCsv()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: throw IllegalStateException("无法写入目标文件")
                }
                val totalCount = uiState.value.allTransactions.size
                _bannerMessage.value = "已一键导出全部 $totalCount 条收支记录（CSV 格式）。"
            } catch (e: Exception) {
                Log.e(TAG, "Ledger export failed", e)
                _bannerMessage.value = "导出失败：${e.localizedMessage ?: "文件写入异常"}"
            }
        }
    }

    fun importAndAutoClassifyFromUri(context: Context, uri: Uri) {
        val session = activeSession ?: return
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("无法读取所选文件")
                }
                val isXlsx = XlsxStatementParser.isXlsxBytes(bytes)
                val result = if (isXlsx) {
                    session.repository.importAndAutoClassifyXlsx(
                        xlsxBytes = bytes,
                        sourceLabel = "一键导入自动分类(Excel)"
                    )
                } else {
                    val content = withContext(Dispatchers.Default) { decodeStatementBytes(bytes) }
                    session.repository.importAndAutoClassifyCsv(
                        csvContent = content,
                        sourceLabel = "一键导入自动分类(CSV)"
                    )
                }
                result.onSuccess { summary ->
                    _bannerMessage.value = if (summary.duplicateSkippedCount > 0) {
                        "已一键导入并自动分类 ${summary.importedCount} 笔记录（已自动跳过 ${summary.duplicateSkippedCount} 笔重复）。"
                    } else {
                        "已一键导入并自动分类 ${summary.importedCount} 笔记录。"
                    }
                }.onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "导入失败"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-classify import failed", e)
                _bannerMessage.value = "一键导入失败：${e.localizedMessage ?: "文件读取异常"}"
            }
        }
    }

    fun simulatePaymentNotification(isWeChat: Boolean) {
        val session = activeSession ?: return
        viewModelScope.launch {
            val (pkg, title, text) = if (isWeChat) {
                Triple(
                    "com.tencent.mm",
                    "微信支付",
                    "已向 美团 支付人民币22.50元（来自通知监听POC）"
                )
            } else {
                Triple(
                    "com.eg.android.AlipayGphone",
                    "支付宝",
                    "你通过支付宝向 美团 付款1.80元（来自通知监听POC）"
                )
            }
            val locLabel = if (uiState.value.settings.locationClassificationEnabled) {
                runCatching { LocationContextResolver.captureCurrentLocation(appContext)?.label.orEmpty() }
                    .getOrDefault("")
            } else {
                ""
            }
            val res = session.repository.stageSimulatedNotification(pkg, title, text, locationLabel = locLabel)
            res.onSuccess {
                val locHint = if (it.locationLabel.isNotBlank()) "（📍 ${it.locationLabel}）" else ""
                _bannerMessage.value = "已捕获 ${it.channel.displayName} 通知「${it.merchant} ${MoneyFormatter.formatFen(it.amountFen)}」$locHint，已加入待核对队列。"
            }.onFailure {
                _bannerMessage.value = it.localizedMessage ?: "通知解析失败"
            }
        }
    }

    fun confirmCandidate(
        candidateId: Long,
        categoryId: String,
        fundingSource: FundingSource,
        rememberMerchant: Boolean,
        note: String,
        fundVault: FundVault? = null
    ) {
        val session = activeSession ?: return
        viewModelScope.launch {
            val candidateMerchant = uiState.value.pendingCandidates.firstOrNull { it.id == candidateId }?.merchant ?: "交易"
            val insertedTxId = session.repository.confirmCandidateToLedger(
                candidateId = candidateId,
                selectedCategoryId = categoryId,
                selectedFundingSource = fundingSource,
                rememberMerchantPreference = rememberMerchant,
                note = note,
                selectedFundVault = fundVault
            )
            if (insertedTxId != null) {
                pushUndoAction(
                    LedgerUndoAction.ConfirmedCandidate(
                        insertedTxId = insertedTxId,
                        candidateId = candidateId,
                        label = "撤回入账「$candidateMerchant」"
                    )
                )
            }
            _bannerMessage.value = "已核对确认并记入正式账本。"
        }
    }

    fun confirmAllCleanCandidates() {
        val session = activeSession ?: return
        viewModelScope.launch {
            session.repository.confirmAllCleanCandidates()
            _bannerMessage.value = "已批量确认所有无冲突的清晰交易。"
        }
    }

    fun skipOrDismissCandidate(candidateId: Long, resolution: CandidateResolution) {
        val session = activeSession ?: return
        viewModelScope.launch {
            session.repository.resolveCandidateAsSkipOrDismiss(candidateId, resolution)
            _bannerMessage.value = if (resolution == CandidateResolution.MERGED_SKIP) {
                "已标记为重复并合并跳过，账本不重复入账。"
            } else {
                "已忽略该条导入记录。"
            }
        }
    }

    fun runAiBatchClassification() {
        val session = activeSession ?: return
        val state = uiState.value
        if (!state.settings.aiCloudAnalysisEnabled) {
            _bannerMessage.value = "当前已关闭云端 AI 分析授权，请先在右上角设置中开启后再使用 AI 预分类。"
            return
        }
        val apiConfig = state.settings.toApiConfig()
        if (!apiConfig.isValid) {
            _bannerMessage.value = "请先在右上角设置中填写模型 API Key，再使用 AI 智能预分类。"
            return
        }
        val pending = state.pendingCandidates
        if (pending.isEmpty()) {
            _bannerMessage.value = "待核对队列为空，无需 AI 预分类。"
            return
        }
        viewModelScope.launch {
            _isAiClassifying.value = true
            try {
                val suggestions = session.aiService.suggestCategoriesForCandidates(pending, state.categories, apiConfig)
                for (s in suggestions) {
                    session.repository.updateCandidateSuggestion(
                        candidateId = s.candidateId,
                        categoryId = s.suggestedCategoryId,
                        confidence = s.confidence,
                        source = ClassificationSource.AI_SUGGESTED,
                        note = "AI 预分类建议（${(s.confidence * 100).toInt()}%）：${s.reason}"
                    )
                }
                _bannerMessage.value = "${apiConfig.model} 已完成 ${suggestions.size} 条待确认交易的智能预分类，请您最终确认。"
            } catch (e: TimeoutCancellationException) {
                _bannerMessage.value = "AI 预分类请求超时（60秒），请稍后重试。"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI batch classify failed", e)
                _bannerMessage.value = "AI 预分类失败：${e.localizedMessage ?: "网络或服务异常"}"
            } finally {
                _isAiClassifying.value = false
            }
        }
    }

    fun addManualTransaction(
        amountYuanStr: String,
        direction: TransactionDirection,
        channel: PaymentChannel,
        fundingSource: FundingSource,
        categoryId: String,
        merchant: String,
        note: String,
        fundVault: FundVault = FundVault.POCKET,
        locationLabel: String = ""
    ) {
        val session = activeSession ?: return
        val fen = MoneyFormatter.parseYuanToFen(amountYuanStr)
        if (fen == null || fen <= 0L) {
            _bannerMessage.value = "请输入有效的大于 0 的金额。"
            return
        }
        viewModelScope.launch {
            val resolvedLocation = locationLabel.trim().ifBlank {
                if (uiState.value.settings.locationClassificationEnabled) {
                    runCatching { LocationContextResolver.captureCurrentLocation(appContext)?.label.orEmpty() }
                        .getOrDefault("")
                } else {
                    ""
                }
            }
            val insertedId = session.repository.addManualTransaction(
                amountFen = fen,
                direction = direction,
                channel = channel,
                fundingSource = fundingSource,
                categoryId = categoryId,
                merchant = merchant,
                note = note,
                fundVault = fundVault,
                locationLabel = resolvedLocation
            )
            val title = merchant.ifBlank { "手动记账" }
            pushUndoAction(
                LedgerUndoAction.AddedManualTransaction(
                    insertedTxId = insertedId,
                    label = "撤回新增「$title」"
                )
            )
            _bannerMessage.value = "已手动记入 ${MoneyFormatter.formatFen(fen)}。"
        }
    }

    fun updateTransactionCategory(txId: Long, categoryId: String, rememberMerchant: Boolean) {
        val existingVault = uiState.value.allTransactions.firstOrNull { it.id == txId }?.fundVault ?: FundVault.POCKET
        updateTransactionCategoryAndVault(txId, categoryId, existingVault, rememberMerchant)
    }

    fun updateTransactionCategoryAndVault(
        txId: Long,
        categoryId: String,
        fundVault: FundVault,
        rememberMerchant: Boolean
    ) {
        val session = activeSession ?: return
        viewModelScope.launch {
            val previous = session.repository.updateTransactionCategoryAndVault(
                txId = txId,
                categoryId = categoryId,
                fundVault = fundVault,
                rememberMerchant = rememberMerchant
            )
            if (previous != null && (previous.categoryId != categoryId || previous.fundVault != fundVault)) {
                pushUndoAction(
                    LedgerUndoAction.UpdatedCategory(
                        txId = txId,
                        previousCategoryId = previous.categoryId,
                        previousVault = previous.fundVault,
                        previousSource = previous.classificationSource,
                        label = "撤回修改「${previous.merchant}」"
                    )
                )
            }
            _bannerMessage.value = "交易分类已更新并学习记忆。"
        }
    }

    fun deleteTransaction(txId: Long) {
        val session = activeSession ?: return
        viewModelScope.launch {
            val deleted = session.repository.deleteTransaction(txId)
            if (deleted != null) {
                pushUndoAction(
                    LedgerUndoAction.DeletedTransaction(
                        tx = deleted,
                        label = "撤回删除「${deleted.merchant}」"
                    )
                )
            }
            _bannerMessage.value = "已删除该条交易记录（可点撤销恢复）。"
        }
    }

    fun undoLastLedgerAction() {
        val session = activeSession ?: return
        val action = undoStack.removeLastOrNull() ?: return
        _lastUndoLabel.value = undoStack.lastOrNull()?.label
        viewModelScope.launch {
            when (action) {
                is LedgerUndoAction.DeletedTransaction -> {
                    session.repository.restoreDeletedTransaction(action.tx)
                    _bannerMessage.value = "已恢复记录「${action.tx.merchant} ${MoneyFormatter.formatFen(action.tx.amountFen)}」。"
                }
                is LedgerUndoAction.UpdatedCategory -> {
                    session.repository.revertTransactionCategoryAndVault(
                        txId = action.txId,
                        previousCategoryId = action.previousCategoryId,
                        previousVault = action.previousVault,
                        previousSource = action.previousSource
                    )
                    _bannerMessage.value = "已撤销修改，恢复原分类与资金库。"
                }
                is LedgerUndoAction.ConfirmedCandidate -> {
                    session.repository.revertConfirmedCandidate(
                        txId = action.insertedTxId,
                        candidateId = action.candidateId
                    )
                    _bannerMessage.value = "已撤回入账，记录已放回「待核对」队列。"
                }
                is LedgerUndoAction.AddedManualTransaction -> {
                    session.repository.deleteTransaction(action.insertedTxId)
                    _bannerMessage.value = "已撤回刚才新增的记账记录。"
                }
            }
        }
    }

    fun updateBudgetLimit(categoryId: String, limitYuanStr: String) {
        val session = activeSession ?: return
        val fen = MoneyFormatter.parseYuanToFen(limitYuanStr)
        if (fen == null || fen < 0L) {
            _bannerMessage.value = "请输入合法的预算金额。"
            return
        }
        viewModelScope.launch {
            val month = uiState.value.settings.activeMonthKey
            session.repository.updateBudgetLimit(month, categoryId, fen)
            _bannerMessage.value = "预算额度已更新。"
        }
    }

    fun applyDeterministicProposal(categoryId: String, newLimitFen: Long) {
        val session = activeSession ?: return
        viewModelScope.launch {
            val month = uiState.value.settings.activeMonthKey
            session.repository.updateBudgetLimit(month, categoryId, newLimitFen)
            _bannerMessage.value = "已按您的确认更新该分类预算为 ${MoneyFormatter.formatFen(newLimitFen)}。"
        }
    }

    fun updateSavingsGoal(targetYuanStr: String, allowanceYuanStr: String, partTimeExpectedYuanStr: String) {
        val session = activeSession ?: return
        val targetFen = MoneyFormatter.parseYuanToFen(targetYuanStr)
        val allowanceFen = MoneyFormatter.parseYuanToFen(allowanceYuanStr)
        val partTimeFen = MoneyFormatter.parseYuanToFen(partTimeExpectedYuanStr)
        if (targetFen == null || allowanceFen == null || partTimeFen == null) {
            _bannerMessage.value = "请输入合法的金额数字。"
            return
        }
        viewModelScope.launch {
            val month = uiState.value.settings.activeMonthKey
            session.repository.updateSavingsGoal(month, targetFen, allowanceFen, partTimeFen)
            _bannerMessage.value = "储蓄挑战目标与月度收入基线已更新。"
        }
    }

    fun generateAiInsights() {
        val session = activeSession ?: return
        val state = uiState.value
        if (!state.settings.aiCloudAnalysisEnabled) {
            _aiInsightState.value = AiInsightState.Error("当前已关闭云端 AI 分析授权，请在设置中开启「允许 AI 分析脱敏账单」后再生成洞察。")
            return
        }
        val apiConfig = state.settings.toApiConfig()
        if (!apiConfig.isValid) {
            _aiInsightState.value = AiInsightState.Error("尚未配置模型 API Key，请点击右上角设置图标填写 API Key、Base URL 与模型名称。")
            return
        }
        viewModelScope.launch {
            _aiInsightState.value = AiInsightState.Loading
            try {
                val report = session.aiService.generateSpendingInsights(state.settings.activeMonthKey, apiConfig)
                _aiInsightState.value = AiInsightState.Success(report)
            } catch (e: TimeoutCancellationException) {
                _aiInsightState.value = AiInsightState.Error("AI 消费分析超时（60秒），请检查网络后重试。")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI insights failed", e)
                _aiInsightState.value = AiInsightState.Error(
                    "AI 分析调用失败：${e.localizedMessage ?: "未知错误"}"
                )
            }
        }
    }

    fun sendAgentMessage(prompt: String) {
        val session = activeSession ?: return
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || _isAgentResponding.value) return

        val state = uiState.value
        if (!state.settings.aiCloudAnalysisEnabled) {
            _agentMessages.update {
                it + AgentChatMessage(isUser = true, text = trimmed) + AgentChatMessage(
                    isUser = false,
                    text = "已拦截请求：您当前关闭了云端 AI 模型授权。如需使用财务助手，请点击右上角设置开启「允许 AI 分析脱敏账单」。",
                    isError = true
                )
            }
            return
        }

        val apiConfig = state.settings.toApiConfig()
        if (!apiConfig.isValid) {
            _agentMessages.update {
                it + AgentChatMessage(isUser = true, text = trimmed) + AgentChatMessage(
                    isUser = false,
                    text = "尚未填写模型 API Key，暂时无法调用云端大模型。请点击上方「配置 API」填写 DeepSeek / 通义千问 / Gemini / OpenAI 兼容接口密钥后立即提问。",
                    isError = true
                )
            }
            return
        }

        _agentMessages.update { it + AgentChatMessage(isUser = true, text = trimmed) }
        viewModelScope.launch {
            _isAgentResponding.value = true
            _agentProgressText.value = "正在连接 ${apiConfig.model} 并规划查账工具..."
            _streamingAgentText.value = ""
            try {
                val reply: AgentChatReply = session.aiService.askFinancialAgent(
                    userPrompt = trimmed,
                    monthKey = state.settings.activeMonthKey,
                    config = apiConfig,
                    conversationHistory = conversationHistory.toList(),
                    onProgress = { step -> _agentProgressText.value = step },
                    onPartialText = { partial -> _streamingAgentText.value = partial }
                )
                conversationHistory.add(ConversationTurn("user", trimmed))
                conversationHistory.add(ConversationTurn("assistant", reply.text))

                _agentMessages.update {
                    it + AgentChatMessage(
                        isUser = false,
                        text = reply.text,
                        toolTraces = reply.toolTraces,
                        proposals = reply.proposals
                    )
                }
            } catch (e: TimeoutCancellationException) {
                _agentMessages.update {
                    it + AgentChatMessage(
                        isUser = false,
                        text = "Agent 单轮响应超时（65秒），请检查网络或 API 地址是否可达。",
                        isError = true
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Agent chat failed", e)
                _agentMessages.update {
                    it + AgentChatMessage(
                        isUser = false,
                        text = "Agent 调用出错：${e.localizedMessage ?: "请检查 API Key、Base URL 或网络连接"}",
                        isError = true
                    )
                }
            } finally {
                _isAgentResponding.value = false
                _agentProgressText.value = null
                _streamingAgentText.value = ""
            }
        }
    }

    fun confirmAgentProposal(messageId: String, proposal: PendingBudgetProposal) {
        val session = activeSession ?: return
        viewModelScope.launch {
            session.repository.updateBudgetLimit(proposal.monthKey, proposal.categoryId, proposal.proposedLimitFen)
            session.repository.logToolCall(
                sessionId = "user_confirm",
                toolName = "apply_budget_change_confirmed",
                argumentsJson = "{\"categoryId\":\"${proposal.categoryId}\",\"newLimitFen\":${proposal.proposedLimitFen}}",
                resultSummary = "用户已确认执行「${proposal.categoryName}」预算调整为 ${MoneyFormatter.formatFen(proposal.proposedLimitFen)}",
                requiresConfirmation = true,
                confirmedByUser = true
            )
            _agentMessages.update { list ->
                list.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(
                            proposals = msg.proposals.map {
                                if (it.id == proposal.id) it.copy(status = ProposalStatus.CONFIRMED) else it
                            }
                        )
                    } else msg
                }
            }
            _bannerMessage.value = "已确认更新「${proposal.categoryName}」月度预算至 ${MoneyFormatter.formatFen(proposal.proposedLimitFen)}。"
        }
    }

    fun rejectAgentProposal(messageId: String, proposal: PendingBudgetProposal) {
        _agentMessages.update { list ->
            list.map { msg ->
                if (msg.id == messageId) {
                    msg.copy(
                        proposals = msg.proposals.map {
                            if (it.id == proposal.id) it.copy(status = ProposalStatus.REJECTED) else it
                        }
                    )
                } else msg
            }
        }
        _bannerMessage.value = "已拒绝本次预算调整建议，原预算保持不变。"
    }

    fun setDailyReviewTime(hour: Int, minute: Int) {
        val session = activeSession ?: return
        viewModelScope.launch {
            session.settingsStore.setDailyReviewTime(hour, minute)
            _bannerMessage.value = String.format("每日集中对账提醒时间已设为 %02d:%02d", hour, minute)
        }
    }

    fun setAiCloudAnalysisEnabled(enabled: Boolean) {
        val session = activeSession ?: return
        viewModelScope.launch {
            session.settingsStore.setAiCloudAnalysisEnabled(enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        val session = activeSession ?: return
        viewModelScope.launch {
            session.settingsStore.setThemeMode(mode)
        }
    }

    fun toggleDarkMode(currentlyDark: Boolean) {
        val next = if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK
        setThemeMode(next)
    }

    fun dismissBeginnerGuide() {
        setHideBeginnerGuide(true)
    }

    fun setHideBeginnerGuide(hidden: Boolean) {
        val session = activeSession ?: return
        viewModelScope.launch {
            session.settingsStore.setHideBeginnerGuide(hidden)
        }
    }

    fun openOnboardingReplay() {
        _isReplayingOnboarding.value = true
    }

    fun skipOnboarding() {
        val session = activeSession
        _isReplayingOnboarding.value = false
        if (session != null) {
            viewModelScope.launch {
                session.settingsStore.setOnboardingCompleted(true)
            }
        }
    }

    fun completeOnboarding(
        monthlyAllowanceYuan: String,
        savingsTargetYuan: String,
        expectedPartTimeYuan: String,
        recordAllowanceIncomeNow: Boolean,
        loadDemoData: Boolean = false
    ) {
        val session = activeSession ?: return
        viewModelScope.launch {
            val month = uiState.value.settings.activeMonthKey
            session.repository.ensureInitialized(month)

            val allowanceFen = MoneyFormatter.parseYuanToFen(monthlyAllowanceYuan)?.coerceAtLeast(0L) ?: 0L
            val targetFen = MoneyFormatter.parseYuanToFen(savingsTargetYuan)?.coerceAtLeast(0L) ?: 0L
            val partTimeFen = MoneyFormatter.parseYuanToFen(expectedPartTimeYuan)?.coerceAtLeast(0L) ?: 0L

            if (allowanceFen > 0L || targetFen > 0L || partTimeFen > 0L) {
                session.repository.updateSavingsGoal(
                    monthKey = month,
                    targetFen = targetFen,
                    livingAllowanceExpectedFen = allowanceFen,
                    partTimeExpectedFen = partTimeFen
                )
            }

            if (loadDemoData) {
                session.repository.loadCompetitionDemoCommittedBaseline(month)
            } else if (recordAllowanceIncomeNow && allowanceFen > 0L) {
                val existingTxs = uiState.value.transactions
                val alreadyRecorded = existingTxs.any {
                    it.direction == TransactionDirection.INCOME &&
                        it.categoryId == "allowance" &&
                        it.amountFen == allowanceFen
                }
                if (!alreadyRecorded) {
                    session.repository.addManualTransaction(
                        amountFen = allowanceFen,
                        direction = TransactionDirection.INCOME,
                        channel = PaymentChannel.BANK_CARD,
                        fundingSource = FundingSource.LIVING_ALLOWANCE,
                        categoryId = "allowance",
                        merchant = "每月生活费",
                        note = "新手引导初始化"
                    )
                }
            }

            session.settingsStore.setOnboardingCompleted(true)
            _isReplayingOnboarding.value = false
            _bannerMessage.value = if (loadDemoData) {
                "新手引导已完成，已为你载入体验账单与存钱目标！"
            } else {
                "新手引导已完成，本月目标与设置已生效！"
            }
        }
    }

    fun saveApiConfig(
        provider: ApiProviderType,
        baseUrl: String,
        apiKey: String,
        model: String
    ) {
        val session = activeSession ?: return
        viewModelScope.launch {
            session.settingsStore.saveApiConfig(provider, baseUrl, apiKey, model)
            _apiTestFeedback.value = ApiTestFeedback()
            _bannerMessage.value = "已保存 ${provider.label}（$model）API 配置。"
        }
    }

    fun testApiConnection(
        provider: ApiProviderType,
        baseUrl: String,
        apiKey: String,
        model: String
    ) {
        val session = activeSession ?: return
        viewModelScope.launch {
            _apiTestFeedback.value = ApiTestFeedback(isTesting = true, message = "正在测试接口连通性...")
            val cfg = ApiConfig(provider, baseUrl.trim(), apiKey.trim(), model.trim())
            val res = session.aiService.testConnection(cfg)
            res.onSuccess { msg ->
                session.settingsStore.saveApiConfig(provider, baseUrl, apiKey, model)
                _apiTestFeedback.value = ApiTestFeedback(isTesting = false, message = msg, isSuccess = true)
            }.onFailure { err ->
                _apiTestFeedback.value = ApiTestFeedback(
                    isTesting = false,
                    message = err.localizedMessage ?: "连接失败，请核对 Key 与地址",
                    isSuccess = false
                )
            }
        }
    }

    fun clearApiTestFeedback() {
        _apiTestFeedback.value = ApiTestFeedback()
    }

    fun setBackgroundKeepAliveEnabled(enabled: Boolean) {
        val session = activeSession ?: return
        viewModelScope.launch {
            session.settingsStore.setBackgroundKeepAliveEnabled(enabled)
            com.example.service.NotificationKeepAliveService.sync(getApplication(), enabled)
            _bannerMessage.value = if (enabled) {
                "已开启后台防掉线守护，息屏或切后台时将持续保护自动抓账。"
            } else {
                "已关闭前台守护通知（仍保留系统定时巡检）。"
            }
        }
    }

    fun setDetailedModeEnabled(enabled: Boolean) {
        val session = activeSession ?: return
        viewModelScope.launch {
            session.settingsStore.setDetailedModeEnabled(enabled)
            _bannerMessage.value = if (enabled) {
                "已开启「详细版」，首页与记账页将显示零花库 / 储蓄库明细。"
            } else {
                "已切换为「简洁版」，已隐藏资金库面板（数据完整保留）。"
            }
        }
    }

    fun setLocationClassificationEnabled(enabled: Boolean) {
        val session = activeSession ?: return
        viewModelScope.launch {
            session.settingsStore.setLocationClassificationEnabled(enabled)
            _bannerMessage.value = if (enabled) {
                "已开启同步定位辅助分类，读账时将结合消费地点提升分类准度。"
            } else {
                "已关闭读取账单时的定位辅助分类。"
            }
        }
    }

    private fun decodeStatementBytes(bytes: ByteArray): String {
        val utf8Text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val hasReplacementChar = utf8Text.contains('\uFFFD')
        val looksLikeValidHeader = utf8Text.contains("交易时间") || utf8Text.contains("time", ignoreCase = true)
        if (!hasReplacementChar && looksLikeValidHeader) {
            return utf8Text
        }
        return runCatching {
            val gbText = bytes.toString(Charset.forName("GB18030")).removePrefix("\uFEFF")
            if (gbText.contains("交易时间") || hasReplacementChar) gbText else utf8Text
        }.getOrDefault(utf8Text)
    }

    companion object {
        private const val TAG = "EasySpendViewModel"
    }
}
