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
import com.example.data.local.ApiProviderType
import com.example.data.local.EasySpendDatabase
import com.example.data.local.ThemeMode
import com.example.data.local.UserSettings
import com.example.data.local.UserSettingsDataStore
import com.example.data.model.AgentToolLogEntity
import com.example.data.model.CandidateResolution
import com.example.data.model.CategoryEntity
import com.example.data.model.ClassificationSource
import com.example.data.model.FundingSource
import com.example.data.model.ImportBatchEntity
import com.example.data.model.ImportCandidateEntity
import com.example.data.model.MerchantPreferenceEntity
import com.example.data.model.PaymentChannel
import com.example.data.model.SavingsGoalEntity
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import com.example.data.repository.EasySpendRepository
import com.example.domain.finance.BudgetAndForecastEngine
import com.example.domain.finance.MoneyFormatter
import com.example.domain.finance.MonthlyFinancialSummary
import com.example.domain.finance.XlsxStatementParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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

data class EasySpendUiState(
    val settings: UserSettings = UserSettings(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
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
    val agentMessages: List<AgentChatMessage> = listOf(
        AgentChatMessage(
            isUser = false,
            text = "你好，我是易支道财务助手。配置上方 API Key 后，可直接问我存钱空间、超支风险或预算调整建议："
        )
    ),
    val apiTestFeedback: ApiTestFeedback = ApiTestFeedback(),
    val bannerMessage: String? = null
)

class EasySpendViewModel(application: Application) : AndroidViewModel(application) {

    private val db = EasySpendDatabase.getInstance(application)
    private val repository = EasySpendRepository(db)
    private val settingsStore = UserSettingsDataStore(application)
    private val aiService = EasySpendAiService(repository)

    private val _aiInsightState = MutableStateFlow<AiInsightState>(AiInsightState.Idle)
    private val _isAiClassifying = MutableStateFlow(false)
    private val _isAgentResponding = MutableStateFlow(false)
    private val _agentProgressText = MutableStateFlow<String?>(null)
    private val _streamingAgentText = MutableStateFlow("")
    private val _agentMessages = MutableStateFlow(EasySpendUiState().agentMessages)
    private val _apiTestFeedback = MutableStateFlow(ApiTestFeedback())
    private val _bannerMessage = MutableStateFlow<String?>(null)

    private val conversationHistory = mutableListOf<ConversationTurn>()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val monthDataFlow = settingsStore.settingsFlow.flatMapLatest { settings ->
        combine(
            repository.observeTransactionsForMonth(settings.activeMonthKey),
            repository.observeMonthlySummary(settings.activeMonthKey),
            repository.observeSavingsGoalForMonth(settings.activeMonthKey)
        ) { txs, summary, goal ->
            Triple(txs, summary, goal)
        }
    }

    val uiState: StateFlow<EasySpendUiState> = combine(
        settingsStore.settingsFlow,
        repository.categoriesFlow,
        monthDataFlow,
        repository.pendingCandidatesFlow,
        repository.importBatchesFlow,
        repository.merchantPreferencesFlow,
        repository.toolLogsFlow,
        _aiInsightState,
        _isAiClassifying,
        _isAgentResponding,
        _agentProgressText,
        _streamingAgentText,
        _agentMessages,
        _apiTestFeedback,
        _bannerMessage
    ) { values ->
        val settings = values[0] as UserSettings
        @Suppress("UNCHECKED_CAST")
        val categories = values[1] as List<CategoryEntity>
        @Suppress("UNCHECKED_CAST")
        val monthTriple = values[2] as Triple<List<TransactionEntity>, MonthlyFinancialSummary, SavingsGoalEntity?>
        @Suppress("UNCHECKED_CAST")
        val pendingCandidates = values[3] as List<ImportCandidateEntity>
        @Suppress("UNCHECKED_CAST")
        val batches = values[4] as List<ImportBatchEntity>
        @Suppress("UNCHECKED_CAST")
        val prefs = values[5] as List<MerchantPreferenceEntity>
        @Suppress("UNCHECKED_CAST")
        val logs = values[6] as List<AgentToolLogEntity>
        val insight = values[7] as AiInsightState
        val classifying = values[8] as Boolean
        val responding = values[9] as Boolean
        val progressText = values[10] as String?
        val streamingText = values[11] as String
        @Suppress("UNCHECKED_CAST")
        val messages = values[12] as List<AgentChatMessage>
        val apiTest = values[13] as ApiTestFeedback
        val banner = values[14] as String?

        EasySpendUiState(
            settings = settings,
            categories = categories,
            transactions = monthTriple.first,
            summary = monthTriple.second,
            savingsGoal = monthTriple.third,
            pendingCandidates = pendingCandidates,
            importBatches = batches,
            merchantPreferences = prefs,
            toolLogs = logs,
            aiInsightState = insight,
            isAiClassifying = classifying,
            isAgentResponding = responding,
            agentProgressText = progressText,
            streamingAgentText = streamingText,
            agentMessages = messages,
            apiTestFeedback = apiTest,
            bannerMessage = banner
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = EasySpendUiState()
    )

    init {
        viewModelScope.launch {
            val activeMonth = settingsStore.settingsFlow.first().activeMonthKey
            repository.ensureInitialized(activeMonth)
        }
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun loadDemoIntoReviewQueue() {
        viewModelScope.launch {
            val month = uiState.value.settings.activeMonthKey
            repository.loadCompetitionDemoIntoQueue(month)
            _bannerMessage.value = "已加载演示账单到「待核对」，你可以逐笔体验查重与分类确认。"
        }
    }

    fun loadDemoCommittedBaseline() {
        viewModelScope.launch {
            val month = uiState.value.settings.activeMonthKey
            repository.loadCompetitionDemoCommittedBaseline(month)
            _bannerMessage.value = "已载入本月体验账单，其中几笔重复和退款记录已留在「待核对」等你处理。"
        }
    }

    fun addDemo300EssentialExpense() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.addManualTransaction(
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
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("无法读取所选文件")
                }
                val isXlsx = XlsxStatementParser.isXlsxBytes(bytes)
                val sourceLabel = "${preferredChannel?.displayName ?: "账单"}${if (isXlsx) "Excel" else "CSV"}导入"
                val result = if (isXlsx) {
                    repository.importXlsxIntoReviewQueue(
                        xlsxBytes = bytes,
                        sourceLabel = sourceLabel,
                        preferredChannel = preferredChannel
                    )
                } else {
                    val content = withContext(Dispatchers.Default) { decodeStatementBytes(bytes) }
                    repository.importCsvIntoReviewQueue(
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

    fun simulatePaymentNotification(isWeChat: Boolean) {
        viewModelScope.launch {
            val (pkg, title, text) = if (isWeChat) {
                Triple(
                    "com.tencent.mm",
                    "微信支付",
                    "已向 美团外卖 支付人民币28.50元（来自通知监听POC）"
                )
            } else {
                Triple(
                    "com.eg.android.AlipayGphone",
                    "支付宝",
                    "你通过支付宝向 中大学一食堂 付款15.00元（来自通知监听POC）"
                )
            }
            val res = repository.stageSimulatedNotification(pkg, title, text)
            res.onSuccess {
                _bannerMessage.value = "已捕获 ${it.channel.displayName} 通知「${it.merchant} ${MoneyFormatter.formatFen(it.amountFen)}」，已加入待核对队列。"
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
        note: String
    ) {
        viewModelScope.launch {
            repository.confirmCandidateToLedger(
                candidateId = candidateId,
                selectedCategoryId = categoryId,
                selectedFundingSource = fundingSource,
                rememberMerchantPreference = rememberMerchant,
                note = note
            )
            _bannerMessage.value = "已核对确认并记入正式账本。"
        }
    }

    fun confirmAllCleanCandidates() {
        viewModelScope.launch {
            repository.confirmAllCleanCandidates()
            _bannerMessage.value = "已批量确认所有无冲突的清晰交易。"
        }
    }

    fun skipOrDismissCandidate(candidateId: Long, resolution: CandidateResolution) {
        viewModelScope.launch {
            repository.resolveCandidateAsSkipOrDismiss(candidateId, resolution)
            _bannerMessage.value = if (resolution == CandidateResolution.MERGED_SKIP) {
                "已标记为重复并合并跳过，账本不重复入账。"
            } else {
                "已忽略该条导入记录。"
            }
        }
    }

    fun runAiBatchClassification() {
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
                val suggestions = aiService.suggestCategoriesForCandidates(pending, state.categories, apiConfig)
                for (s in suggestions) {
                    repository.updateCandidateSuggestion(
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
        note: String
    ) {
        val fen = MoneyFormatter.parseYuanToFen(amountYuanStr)
        if (fen == null || fen <= 0L) {
            _bannerMessage.value = "请输入有效的大于 0 的金额。"
            return
        }
        viewModelScope.launch {
            repository.addManualTransaction(
                amountFen = fen,
                direction = direction,
                channel = channel,
                fundingSource = fundingSource,
                categoryId = categoryId,
                merchant = merchant,
                note = note
            )
            _bannerMessage.value = "已手动记入 ${MoneyFormatter.formatFen(fen)}。"
        }
    }

    fun updateTransactionCategory(txId: Long, categoryId: String, rememberMerchant: Boolean) {
        viewModelScope.launch {
            repository.updateTransactionCategory(txId, categoryId, rememberMerchant)
            _bannerMessage.value = "交易分类已更新并记录。"
        }
    }

    fun deleteTransaction(txId: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(txId)
            _bannerMessage.value = "已删除该条交易记录。"
        }
    }

    fun updateBudgetLimit(categoryId: String, limitYuanStr: String) {
        val fen = MoneyFormatter.parseYuanToFen(limitYuanStr)
        if (fen == null || fen < 0L) {
            _bannerMessage.value = "请输入合法的预算金额。"
            return
        }
        viewModelScope.launch {
            val month = uiState.value.settings.activeMonthKey
            repository.updateBudgetLimit(month, categoryId, fen)
            _bannerMessage.value = "预算额度已更新。"
        }
    }

    fun applyDeterministicProposal(categoryId: String, newLimitFen: Long) {
        viewModelScope.launch {
            val month = uiState.value.settings.activeMonthKey
            repository.updateBudgetLimit(month, categoryId, newLimitFen)
            _bannerMessage.value = "已按您的确认更新该分类预算为 ${MoneyFormatter.formatFen(newLimitFen)}。"
        }
    }

    fun updateSavingsGoal(targetYuanStr: String, allowanceYuanStr: String, partTimeExpectedYuanStr: String) {
        val targetFen = MoneyFormatter.parseYuanToFen(targetYuanStr)
        val allowanceFen = MoneyFormatter.parseYuanToFen(allowanceYuanStr)
        val partTimeFen = MoneyFormatter.parseYuanToFen(partTimeExpectedYuanStr)
        if (targetFen == null || allowanceFen == null || partTimeFen == null) {
            _bannerMessage.value = "请输入合法的金额数字。"
            return
        }
        viewModelScope.launch {
            val month = uiState.value.settings.activeMonthKey
            repository.updateSavingsGoal(month, targetFen, allowanceFen, partTimeFen)
            _bannerMessage.value = "储蓄挑战目标与月度收入基线已更新。"
        }
    }

    fun generateAiInsights() {
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
                val report = aiService.generateSpendingInsights(state.settings.activeMonthKey, apiConfig)
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
                val reply: AgentChatReply = aiService.askFinancialAgent(
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
        viewModelScope.launch {
            repository.updateBudgetLimit(proposal.monthKey, proposal.categoryId, proposal.proposedLimitFen)
            repository.logToolCall(
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
        viewModelScope.launch {
            settingsStore.setDailyReviewTime(hour, minute)
            _bannerMessage.value = String.format("每日集中对账提醒时间已设为 %02d:%02d", hour, minute)
        }
    }

    fun setAiCloudAnalysisEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setAiCloudAnalysisEnabled(enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsStore.setThemeMode(mode)
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
        viewModelScope.launch {
            settingsStore.setHideBeginnerGuide(hidden)
        }
    }

    fun saveApiConfig(
        provider: ApiProviderType,
        baseUrl: String,
        apiKey: String,
        model: String
    ) {
        viewModelScope.launch {
            settingsStore.saveApiConfig(provider, baseUrl, apiKey, model)
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
        viewModelScope.launch {
            _apiTestFeedback.value = ApiTestFeedback(isTesting = true, message = "正在测试接口连通性...")
            val cfg = ApiConfig(provider, baseUrl.trim(), apiKey.trim(), model.trim())
            val res = aiService.testConnection(cfg)
            res.onSuccess { msg ->
                settingsStore.saveApiConfig(provider, baseUrl, apiKey, model)
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
