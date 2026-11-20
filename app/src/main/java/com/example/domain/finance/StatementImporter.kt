package com.example.domain.finance

import com.example.data.model.CandidateResolution
import com.example.data.model.CategoryEntity
import com.example.data.model.DiscrepancyType
import com.example.data.model.FundVault
import com.example.data.model.FundingSource
import com.example.data.model.ImportCandidateEntity
import com.example.data.model.MerchantPreferenceEntity
import com.example.data.model.PaymentChannel
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionSourceType
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

data class ParsedRawRecord(
    val externalRefId: String,
    val occurredAtMillis: Long,
    val amountFen: Long,
    val direction: TransactionDirection,
    val channel: PaymentChannel,
    val fundingSource: FundingSource,
    val sourceType: TransactionSourceType,
    val merchant: String,
    val rawDescription: String,
    val note: String = "",
    val fundVault: FundVault = FundVault.POCKET,
    val locationLabel: String = ""
)

object StatementImporter {

    private val dateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA),
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.CHINA),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA),
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA)
    )

    fun parseCsvStatement(
        csvContent: String,
        preferredChannel: PaymentChannel? = null
    ): List<ParsedRawRecord> {
        val normalizedContent = csvContent.removePrefix("\uFEFF")
        val lines = normalizedContent.lines().map { it.trim().removePrefix("\uFEFF") }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()
        val rows = lines.map { splitCsvLine(it) }
        return parseStatementRows(rows, preferredChannel)
    }

    fun parseXlsxStatement(
        xlsxBytes: ByteArray,
        preferredChannel: PaymentChannel? = null
    ): List<ParsedRawRecord> {
        val rows = XlsxStatementParser.parseRows(xlsxBytes)
        if (rows.isEmpty()) return emptyList()
        return parseStatementRows(rows, preferredChannel)
    }

    fun parseStatementRows(
        rows: List<List<String>>,
        preferredChannel: PaymentChannel? = null
    ): List<ParsedRawRecord> {
        if (rows.isEmpty()) return emptyList()

        val headerIndex = rows.indexOfFirst { row ->
            val joined = row.joinToString(" ")
            (joined.contains("交易时间") && (joined.contains("金额") || joined.contains("收/支"))) ||
                (joined.contains("time", ignoreCase = true) && joined.contains("amount", ignoreCase = true))
        }
        if (headerIndex == -1 || headerIndex >= rows.lastIndex) return emptyList()

        val headers = rows[headerIndex].map { it.trim() }
        val headerMetaText = rows.take(headerIndex + 1).flatten().joinToString(" ")
        val isWeChat = preferredChannel == PaymentChannel.WECHAT_PAY ||
            headerMetaText.contains("微信") ||
            (headers.any { it == "商品" } && headers.any { it.contains("交易单号") })
        val isAlipay = preferredChannel == PaymentChannel.ALIPAY ||
            headerMetaText.contains("支付宝") ||
            headers.any { it.contains("商品说明") || it.contains("交易订单号") }

        val channel = when {
            preferredChannel != null -> preferredChannel
            isWeChat -> PaymentChannel.WECHAT_PAY
            isAlipay -> PaymentChannel.ALIPAY
            else -> PaymentChannel.OTHER
        }
        val sourceType = when (channel) {
            PaymentChannel.WECHAT_PAY -> TransactionSourceType.WECHAT_BILL_IMPORT
            PaymentChannel.ALIPAY -> TransactionSourceType.ALIPAY_BILL_IMPORT
            else -> TransactionSourceType.WECHAT_BILL_IMPORT
        }

        val timeIdx = headers.indexOfFirst { it.contains("交易时间") || it.equals("time", true) }
        val txTypeIdx = headers.indexOfFirst { it.contains("交易类型") || it.contains("交易分类") }
        val merchantIdx = headers.indexOfFirst { it.contains("交易对方") || it.equals("merchant", true) }
        val descIdx = headers.indexOfFirst { it.contains("商品说明") || it == "商品" || it.contains("description", true) }
        val dirIdx = headers.indexOfFirst { it.contains("收/支") || it.equals("direction", true) }
        val amountIdx = headers.indexOfFirst { it.contains("金额") || it.equals("amount", true) }
        val statusIdx = headers.indexOfFirst { it.contains("状态") || it.equals("status", true) }
        val refIdx = headers.indexOfFirst { it.contains("交易单号") || it.contains("交易订单号") || it.equals("id", true) }
        val payChannelIdx = headers.indexOfFirst { it.contains("支付方式") || it.equals("channel", true) }
        val noteIdx = headers.indexOfFirst { it == "备注" || it.equals("note", true) }
        val vaultIdx = headers.indexOfFirst { it.contains("资金库") || it.equals("vault", true) || it == "库" }
        val locationIdx = headers.indexOfFirst { it.contains("消费地点") || it.contains("地点") || it.equals("location", true) }

        if (timeIdx == -1 || amountIdx == -1) return emptyList()

        val records = mutableListOf<ParsedRawRecord>()
        for (i in (headerIndex + 1) until rows.size) {
            val cols = rows[i]
            if (cols.size <= maxOf(timeIdx, amountIdx)) continue

            val timeStr = cols.getOrNull(timeIdx)?.trim().orEmpty()
            val millis = parseDateMillis(timeStr) ?: continue
            val amountStr = cols.getOrNull(amountIdx)?.trim().orEmpty()
            val amountFen = MoneyFormatter.parseYuanToFen(amountStr) ?: continue
            if (amountFen <= 0L) continue

            val dirStr = cols.getOrNull(dirIdx)?.trim().orEmpty()
            val txTypeStr = cols.getOrNull(txTypeIdx)?.trim().orEmpty()
            val statusStr = cols.getOrNull(statusIdx)?.trim().orEmpty()
            val payChannelStr = cols.getOrNull(payChannelIdx)?.trim().orEmpty()
            val noteStr = cols.getOrNull(noteIdx)?.trim().orEmpty().let { if (it == "/") "" else it }
            val merchant = cols.getOrNull(merchantIdx)?.trim().orEmpty().ifBlank { "未知商户" }
            val rawGoods = cols.getOrNull(descIdx)?.trim().orEmpty().let { if (it == "/") "" else it }
            val desc = when {
                rawGoods.isNotBlank() -> rawGoods
                txTypeStr.isNotBlank() -> txTypeStr
                else -> ""
            }

            val rowChannel = when {
                payChannelStr.contains("微信") || payChannelStr.contains("零钱") -> PaymentChannel.WECHAT_PAY
                payChannelStr.contains("支付宝") || payChannelStr.contains("花呗") -> PaymentChannel.ALIPAY
                payChannelStr.contains("银行") || payChannelStr.contains("储蓄卡") || payChannelStr.contains("信用卡") -> PaymentChannel.BANK_CARD
                payChannelStr.contains("现金") -> PaymentChannel.CASH
                else -> channel
            }

            val direction = when {
                statusStr.contains("退款") || dirStr.contains("退款") || desc.contains("退款") -> TransactionDirection.REFUND
                dirStr.contains("收入") || dirStr == "+" -> TransactionDirection.INCOME
                dirStr == "/" || dirStr.contains("中性") || dirStr.contains("不计收支") ||
                    dirStr.contains("转账") || statusStr.contains("转账") -> TransactionDirection.TRANSFER
                else -> TransactionDirection.EXPENSE
            }

            val fundingSource = when (direction) {
                TransactionDirection.INCOME -> when {
                    merchant.contains("家庭") || merchant.contains("爸") || merchant.contains("妈") || desc.contains("生活费") ->
                        FundingSource.LIVING_ALLOWANCE
                    desc.contains("兼职") || desc.contains("家教") || desc.contains("助研") || merchant.contains("科技") ->
                        FundingSource.PART_TIME_INCOME
                    else -> FundingSource.UNASSIGNED
                }
                TransactionDirection.EXPENSE -> FundingSource.LIVING_ALLOWANCE
                else -> FundingSource.UNASSIGNED
            }

            val vaultStr = cols.getOrNull(vaultIdx)?.trim().orEmpty()
            val locationStr = cols.getOrNull(locationIdx)?.trim().orEmpty().let { if (it == "/") "" else it }
            val inferredVault = inferDefaultVault(
                explicitVaultText = vaultStr,
                merchant = merchant,
                description = desc,
                note = noteStr,
                payChannelStr = payChannelStr
            )

            val refId = cols.getOrNull(refIdx)?.trim()?.takeIf { it.isNotBlank() && it != "/" }
                ?: "${rowChannel.name}_${millis}_${amountFen}_${merchant.hashCode()}"

            records.add(
                ParsedRawRecord(
                    externalRefId = refId,
                    occurredAtMillis = millis,
                    amountFen = amountFen,
                    direction = direction,
                    channel = rowChannel,
                    fundingSource = fundingSource,
                    sourceType = sourceType,
                    merchant = merchant,
                    rawDescription = if (statusStr.isNotBlank()) "$desc ($statusStr)".trim() else desc,
                    note = noteStr,
                    fundVault = inferredVault,
                    locationLabel = locationStr
                )
            )
        }
        return records
    }

    internal fun inferDefaultVault(
        explicitVaultText: String = "",
        merchant: String = "",
        description: String = "",
        note: String = "",
        payChannelStr: String = ""
    ): FundVault {
        val explicit = explicitVaultText.trim()
        if (explicit.contains("储蓄") || explicit.equals("SAVINGS", ignoreCase = true)) {
            return FundVault.SAVINGS
        }
        if (explicit.contains("零花") || explicit.equals("POCKET", ignoreCase = true)) {
            return FundVault.POCKET
        }
        val combined = "$merchant $description $note $payChannelStr".lowercase()
        return if (
            combined.contains("储蓄") ||
            combined.contains("定期存款") ||
            combined.contains("奖学金") ||
            combined.contains("攒钱") ||
            combined.contains("理财收益")
        ) {
            FundVault.SAVINGS
        } else {
            FundVault.POCKET
        }
    }

    private val strictNonPaymentNoiseKeywords = listOf(
        "语音通话", "视频通话", "撤回了一条消息", "正在登录", "邀请你加入", "请求添加你为朋友"
    )

    private val leadingBundlePrefixRegex = Regex("""^\s*(?:\[\d+条\]|\d+条新消息[：:\s]*)+\s*""")

    private val paymentContextKeywords = listOf(
        "微信支付", "微信收款", "支付宝", "支付成功", "付款", "收款", "到账", "退款",
        "转入", "转出", "扣款", "扣费", "代扣", "自动扣", "免密", "轻松付", "先享后付", "先乘后付",
        "消费", "支出", "零钱入账", "通过零钱", "零钱扣", "零钱支付", "零钱通", "亲属卡", "刷脸支付",
        "花呗", "余额宝", "账单", "收钱码", "支付凭证", "扣费凭证", "扣款凭证", "交易提醒",
        "支付人民币", "支付￥", "支付¥"
    )

    private val currencyMarkedAmountRegex = Regex(
        """(?:(?:￥|¥|人民币|CNY)\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)|(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\s*元)"""
    )

    private val verbDecimalAmountRegex = Regex(
        """(?:(?<!微信)支付|付款|收款|到账|消费|扣款|扣费|代扣|自动扣款|免密支付|支出|退款|转入|转出|金额)[:：\s]*(?:人民币|￥|¥)?\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)(?![\d条个笔次件张分秒时点号月日年楼层窗口%:：-])"""
    )

    private val detailFieldMerchantRegex = Regex(
        """(?:扣费内容|消费商户|扣款商户|商户名称|收款商户|交易对方|收款方|商户)[：:\s]+([^\n，,。；;¥￥]{2,28})"""
    )

    private val merchantRegex = Regex(
        """(?:向|在|来自|付款给|收到)\s*([^\s，,。：:()（）¥￥]{2,24}?)(?=\s*(?:支付|付款|消费|转账|退款|收款|扣款|扣费|代扣|自动扣|的|人民币|￥|¥|\d+(?:\.\d{1,2})?\s*元|$))"""
    )

    private val preDebitMerchantRegex = Regex(
        """^([^\s，,。：:()（）¥￥]{2,20})\s+(?:通过零钱|通过银行卡|自动扣|免密|扣款|扣费|代扣)"""
    )

    private val genericNotificationTitles = setOf(
        "微信", "微信支付", "微信收款助手", "微信支付凭证", "服务通知",
        "支付宝", "支付宝通知", "支付宝账单", "收钱码"
    )

    fun parsePaymentNotification(
        packageName: String,
        title: String,
        text: String,
        nowMillis: Long = System.currentTimeMillis(),
        locationLabel: String = ""
    ): ParsedRawRecord? {
        val cleanTitle = leadingBundlePrefixRegex.replace(title.trim(), "").trim()
        val cleanText = leadingBundlePrefixRegex.replace(text.trim(), "").trim()
        if (cleanText.isBlank()) return null

        val combined = "$cleanTitle $cleanText".trim()
        if (strictNonPaymentNoiseKeywords.any { combined.contains(it) }) return null

        val channel = when {
            packageName.contains("tencent.mm", ignoreCase = true) || combined.contains("微信") -> PaymentChannel.WECHAT_PAY
            packageName.contains("Alipay", ignoreCase = true) || combined.contains("支付宝") -> PaymentChannel.ALIPAY
            else -> return null
        }

        val hasPaymentContext = paymentContextKeywords.any { combined.contains(it, ignoreCase = true) }
        if (!hasPaymentContext) return null

        val rawAmount = currencyMarkedAmountRegex.find(combined)?.let { m ->
            m.groupValues[1].ifBlank { m.groupValues[2] }
        } ?: verbDecimalAmountRegex.find(combined)?.groupValues?.getOrNull(1)
            ?: return null

        val amountFen = MoneyFormatter.parseYuanToFen(rawAmount) ?: return null
        if (amountFen <= 0L) return null

        val hasExplicitExpensePhrase =
            combined.contains("已向") ||
                combined.contains("付款") ||
                combined.contains("支付人民币") ||
                combined.contains("支付￥") ||
                combined.contains("支付¥") ||
                combined.contains("扣款") ||
                combined.contains("扣费") ||
                combined.contains("消费")
        val direction = when {
            combined.contains("退款") -> TransactionDirection.REFUND
            !hasExplicitExpensePhrase &&
                (combined.contains("收款") || combined.contains("到账") || combined.contains("转入")) -> TransactionDirection.INCOME
            combined.contains("转出") || combined.contains("提现") -> TransactionDirection.TRANSFER
            else -> TransactionDirection.EXPENSE
        }

        val merchant = extractNotificationMerchant(cleanTitle, cleanText, combined, channel)
        val normalizedBody = combined.replace(Regex("""\s+"""), " ").trim()
        val fingerprint = (merchant.lowercase() + "|" + normalizedBody.lowercase()).hashCode().toUInt().toString(16)

        return ParsedRawRecord(
            externalRefId = "NOTIF_${channel.name}_${nowMillis / 1000}_${amountFen}_$fingerprint",
            occurredAtMillis = nowMillis,
            amountFen = amountFen,
            direction = direction,
            channel = channel,
            fundingSource = if (direction == TransactionDirection.INCOME) FundingSource.PART_TIME_INCOME else FundingSource.LIVING_ALLOWANCE,
            sourceType = TransactionSourceType.NOTIFICATION_CAPTURE,
            merchant = merchant,
            rawDescription = normalizedBody.take(160),
            fundVault = inferDefaultVault(merchant = merchant, description = normalizedBody),
            locationLabel = locationLabel.trim()
        )
    }

    private fun extractNotificationMerchant(
        cleanTitle: String,
        cleanText: String,
        combined: String,
        channel: PaymentChannel
    ): String {
        val detailField = detailFieldMerchantRegex.find(combined)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.replace(Regex("""\s+(?:管理|对订单|账单详情|扣费服务).*$"""), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it !in genericNotificationTitles }
        if (detailField != null) return detailField.take(24)

        val verbExtracted = merchantRegex.find(combined)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotBlank() && it !in genericNotificationTitles }
        if (verbExtracted != null) return verbExtracted.take(24)

        val preDebit = preDebitMerchantRegex.find(cleanText)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotBlank() && it !in genericNotificationTitles }
        if (preDebit != null) return preDebit.take(24)

        val nonGenericTitle = cleanTitle.take(24)
            .takeIf { it.isNotBlank() && it !in genericNotificationTitles }
        if (nonGenericTitle != null) return nonGenericTitle

        if (combined.contains("校园轻松付")) return "校园轻松付"
        return cleanTitle.take(18).ifBlank { "${channel.displayName}通知商户" }
    }

    private fun isGenericMerchantName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isBlank() ||
            trimmed in genericNotificationTitles ||
            trimmed.endsWith("通知商户") ||
            trimmed == "校园轻松付"
    }

    private fun isLikelyFuzzyDuplicate(
        existingSource: TransactionSourceType,
        existingOccurredAt: Long,
        existingMerchant: String,
        existingDescription: String,
        existingAmountFen: Long,
        existingDirection: TransactionDirection,
        rec: ParsedRawRecord
    ): Boolean {
        if (existingDirection != rec.direction || existingAmountFen != rec.amountFen) return false

        val timeDelta = abs(existingOccurredAt - rec.occurredAtMillis)
        val bothNotifications =
            existingSource == TransactionSourceType.NOTIFICATION_CAPTURE &&
                rec.sourceType == TransactionSourceType.NOTIFICATION_CAPTURE

        if (bothNotifications) {
            val bothSpecificMerchants = !isGenericMerchantName(existingMerchant) && !isGenericMerchantName(rec.merchant)
            if (bothSpecificMerchants && !existingMerchant.equals(rec.merchant, ignoreCase = true)) {
                // Distinct cafeteria windows / merchants (e.g. 松涛园1楼大众窗口19 vs 窗口15)
                return false
            }
            // If descriptions explicitly differ (e.g. different window/order detail), treat as separate consecutive payment
            val normExistingDesc = existingDescription.replace(Regex("""\s+"""), " ").trim()
            val normRecDesc = rec.rawDescription.replace(Regex("""\s+"""), " ").trim()
            if (normExistingDesc.isNotBlank() && normRecDesc.isNotBlank() &&
                !normExistingDesc.equals(normRecDesc, ignoreCase = true) &&
                timeDelta > 5_000L
            ) {
                return false
            }
            // Genuine Android system duplicate re-notification arrives within ~20 seconds
            return timeDelta <= 20_000L
        }

        // Cross-source reconciliation (e.g. notification vs official WeChat/Alipay statement)
        return timeDelta <= 30 * 60 * 1000L ||
            (existingMerchant.equals(rec.merchant, ignoreCase = true) && timeDelta <= 24 * 3600 * 1000L)
    }

    fun buildStagingCandidates(
        batchId: Long,
        records: List<ParsedRawRecord>,
        existingTransactions: List<TransactionEntity>,
        preferences: List<MerchantPreferenceEntity>,
        existingPendingCandidates: List<ImportCandidateEntity> = emptyList(),
        customCategories: List<CategoryEntity> = emptyList()
    ): List<ImportCandidateEntity> {
        val seenInBatch = mutableListOf<ParsedRawRecord>()
        return records.map { rec ->
            val classification = ClassificationEngine.classify(
                merchant = rec.merchant,
                description = rec.rawDescription,
                direction = rec.direction,
                preferences = preferences,
                amountFen = rec.amountFen,
                customCategories = customCategories,
                occurredAtMillis = rec.occurredAtMillis,
                channel = rec.channel,
                note = rec.note,
                historicalTransactions = existingTransactions,
                locationLabel = rec.locationLabel
            )

            // 1. Exact externalRefId duplicate check (ledger + pending queue + earlier in same batch)
            val exactDup = existingTransactions.firstOrNull {
                !it.externalRefId.isNullOrBlank() && it.externalRefId == rec.externalRefId
            }
            val pendingExactDup = if (exactDup == null) {
                existingPendingCandidates.firstOrNull {
                    it.externalRefId.isNotBlank() && it.externalRefId == rec.externalRefId
                }
            } else null
            val batchExactDup = if (exactDup == null && pendingExactDup == null) {
                seenInBatch.firstOrNull { it.externalRefId == rec.externalRefId }
            } else null

            // 2. Fuzzy duplicate check (smart handling for consecutive notifications vs cross-source statements)
            val fuzzyDup = if (exactDup == null && pendingExactDup == null && batchExactDup == null) {
                existingTransactions.firstOrNull { tx ->
                    isLikelyFuzzyDuplicate(
                        existingSource = tx.sourceType,
                        existingOccurredAt = tx.occurredAtMillis,
                        existingMerchant = tx.merchant,
                        existingDescription = tx.rawDescription,
                        existingAmountFen = tx.amountFen,
                        existingDirection = tx.direction,
                        rec = rec
                    )
                }
            } else null
            val pendingFuzzyDup = if (exactDup == null && pendingExactDup == null && batchExactDup == null && fuzzyDup == null) {
                existingPendingCandidates.firstOrNull { c ->
                    isLikelyFuzzyDuplicate(
                        existingSource = c.sourceType,
                        existingOccurredAt = c.occurredAtMillis,
                        existingMerchant = c.merchant,
                        existingDescription = c.rawDescription,
                        existingAmountFen = c.amountFen,
                        existingDirection = c.direction,
                        rec = rec
                    )
                }
            } else null

            // 3. Same-time/merchant amount discrepancy check (cross-source only so consecutive cafeteria windows never false-alarm)
            val amountMismatch = if (exactDup == null && pendingExactDup == null && batchExactDup == null && fuzzyDup == null && pendingFuzzyDup == null) {
                existingTransactions.firstOrNull { tx ->
                    tx.sourceType != rec.sourceType &&
                        tx.direction == rec.direction &&
                        !isGenericMerchantName(tx.merchant) &&
                        tx.merchant.equals(rec.merchant, ignoreCase = true) &&
                        abs(tx.occurredAtMillis - rec.occurredAtMillis) <= 15 * 60 * 1000L &&
                        tx.amountFen != rec.amountFen
                }
            } else null

            // 4. Refund matching check (against existing ledger OR earlier expense in the same batch)
            val refundMatch = if (rec.direction == TransactionDirection.REFUND) {
                existingTransactions.firstOrNull { tx ->
                    tx.direction == TransactionDirection.EXPENSE &&
                        (tx.merchant.contains(rec.merchant, ignoreCase = true) || rec.merchant.contains(tx.merchant, ignoreCase = true)) &&
                        tx.amountFen >= rec.amountFen
                }
            } else null
            val batchRefundMatch = if (rec.direction == TransactionDirection.REFUND && refundMatch == null) {
                seenInBatch.firstOrNull { prior ->
                    prior.direction == TransactionDirection.EXPENSE &&
                        (prior.merchant.contains(rec.merchant, ignoreCase = true) || rec.merchant.contains(prior.merchant, ignoreCase = true)) &&
                        prior.amountFen >= rec.amountFen
                }
            } else null

            seenInBatch.add(rec)

            val (discrepancyType, matchedId, discrepancyNote) = when {
                exactDup != null -> Triple(
                    DiscrepancyType.POTENTIAL_DUPLICATE,
                    exactDup.id,
                    "交易单号与账本记录 #${exactDup.id} 完全一致，建议合并跳过防重复入账"
                )
                pendingExactDup != null -> Triple(
                    DiscrepancyType.POTENTIAL_DUPLICATE,
                    null,
                    "待核对队列中已存在同单号记录「${pendingExactDup.merchant}」，疑似重复捕获"
                )
                batchExactDup != null -> Triple(
                    DiscrepancyType.POTENTIAL_DUPLICATE,
                    null,
                    "与同批次导入记录「${batchExactDup.merchant}」交易单号完全一致，疑似重复"
                )
                fuzzyDup != null -> Triple(
                    DiscrepancyType.POTENTIAL_DUPLICATE,
                    fuzzyDup.id,
                    "与已有记录「${fuzzyDup.merchant} ${MoneyFormatter.formatFen(fuzzyDup.amountFen)}」时间及金额高度接近，疑似通知采集与账单重复"
                )
                pendingFuzzyDup != null -> Triple(
                    DiscrepancyType.POTENTIAL_DUPLICATE,
                    null,
                    "待核对队列中已有高度相似记录「${pendingFuzzyDup.merchant} ${MoneyFormatter.formatFen(pendingFuzzyDup.amountFen)}」，疑似重复推送或账单重叠"
                )
                amountMismatch != null -> Triple(
                    DiscrepancyType.AMOUNT_MISMATCH,
                    amountMismatch.id,
                    "同商户近时段金额不一致（账本记录为 ${MoneyFormatter.formatFen(amountMismatch.amountFen)}），请核对折扣/满减差异"
                )
                refundMatch != null -> Triple(
                    DiscrepancyType.REFUND_MATCH,
                    refundMatch.id,
                    "匹配到原支出「${refundMatch.merchant} ${MoneyFormatter.formatFen(refundMatch.amountFen)}」，确认后将冲减净支出"
                )
                batchRefundMatch != null -> Triple(
                    DiscrepancyType.REFUND_MATCH,
                    null,
                    "匹配到同批次原支出「${batchRefundMatch.merchant} ${MoneyFormatter.formatFen(batchRefundMatch.amountFen)}」，确认后将冲减净支出"
                )
                classification.needsReview -> Triple(
                    DiscrepancyType.UNCERTAIN_CATEGORY,
                    null,
                    classification.reason
                )
                else -> Triple(
                    DiscrepancyType.NONE,
                    null,
                    "校验通过，等待用户确认入账"
                )
            }

            ImportCandidateEntity(
                batchId = batchId,
                externalRefId = rec.externalRefId,
                occurredAtMillis = rec.occurredAtMillis,
                monthKey = MoneyFormatter.monthKeyFromMillis(rec.occurredAtMillis),
                amountFen = rec.amountFen,
                direction = rec.direction,
                channel = rec.channel,
                fundingSource = rec.fundingSource,
                sourceType = rec.sourceType,
                merchant = rec.merchant,
                rawDescription = rec.rawDescription,
                suggestedCategoryId = classification.categoryId,
                classificationSource = classification.source,
                confidenceScore = classification.confidence,
                discrepancyType = discrepancyType,
                matchedTransactionId = matchedId,
                discrepancyNote = discrepancyNote,
                resolutionStatus = CandidateResolution.PENDING,
                fundVault = rec.fundVault,
                locationLabel = rec.locationLabel
            )
        }
    }

    private fun parseDateMillis(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        for (fmt in dateFormats) {
            try {
                val parsed = synchronized(fmt) { fmt.parse(trimmed) }
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        val serial = trimmed.toDoubleOrNull()
        if (serial != null) {
            return XlsxStatementParser.excelSerialToMillis(serial)
        }
        return null
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString().trim())
        return result
    }

    /**
     * Exports ledger transactions as UTF-8 BOM CSV compatible with Excel and one-click re-import.
     */
    fun exportTransactionsToCsv(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>
    ): String {
        val catMap = categories.associateBy { it.id }
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.appendLine("交易时间,收/支,金额(元),交易对方,商品说明,支付方式,分类,资金库,消费地点,备注,交易单号")
        transactions.sortedByDescending { it.occurredAtMillis }.forEach { tx ->
            val timeStr = synchronized(fmt) { fmt.format(java.util.Date(tx.occurredAtMillis)) }
            val dirStr = when (tx.direction) {
                TransactionDirection.EXPENSE -> "支出"
                TransactionDirection.INCOME -> "收入"
                TransactionDirection.REFUND -> "退款"
                TransactionDirection.TRANSFER -> "转账"
            }
            val amountStr = MoneyFormatter.fenToYuanString(tx.amountFen)
            val catName = catMap[tx.categoryId]?.name ?: tx.categoryId
            val refId = tx.externalRefId.orEmpty().ifBlank { "ES_${tx.id}_${tx.occurredAtMillis}" }
            val cols = listOf(
                timeStr,
                dirStr,
                amountStr,
                tx.merchant,
                tx.rawDescription,
                tx.channel.displayName,
                catName,
                tx.fundVault.displayName,
                tx.locationLabel,
                tx.note,
                refId
            ).joinToString(",") { escapeCsvCell(it) }
            sb.appendLine(cols)
        }
        return sb.toString()
    }

    private fun escapeCsvCell(raw: String): String {
        val clean = raw.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
        return if (clean.contains(',') || clean.contains('"')) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else {
            clean
        }
    }
}

object DemoStatementFactory {
    // Compliant synthetic WeChat + Alipay bill CSVs matching the SYSU competition demo scenario
    val demoWeChatCsv: String = """
        微信支付账单明细（合规合成演示数据）
        交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号
        2026-11-01 09:00:00,亲属转账,家庭生活费转账,11月生活费,收入,¥1800.00,零钱,已存入零钱,WX202611010001
        2026-11-02 12:15:00,商户消费,中大学一食堂,午餐套餐,支出,¥18.50,零钱,支付成功,WX202611020012
        2026-11-03 18:20:00,商户消费,瑞幸咖啡,生椰拿铁,支出,¥19.90,微信零钱通,支付成功,WX202611030045
        2026-11-05 14:10:00,商户消费,校园打印店,课程讲义打印,支出,¥24.00,零钱,支付成功,WX202611050081
        2026-11-07 20:05:00,商户消费,美团外卖,夜宵订单,支出,¥32.50,零钱,支付成功,WX202611070112
        2026-11-09 19:30:00,商户消费,万达影城,周末电影票,支出,¥58.00,零钱,支付成功,WX202611090190
        2026-11-11 22:15:00,商户消费,全家便利店,日用品与零食,支出,¥46.80,零钱,支付成功,WX202611110233
        2026-11-12 12:30:00,商户消费,中大松园食堂,自选快餐,支出,¥16.00,零钱,支付成功,WX202611120299
        2026-11-14 16:40:00,商户消费,Steam游戏平台,特惠游戏,支出,¥98.00,零钱,支付成功,WX202611140340
    """.trimIndent()

    val demoAlipayCsv: String = """
        支付宝交易明细（合规合成演示数据）
        交易时间,交易分类,交易对方,商品说明,收/支,金额,交易状态,交易订单号
        2026-11-04 17:00:00,劳务收入,广州智算助研项目,11月上旬兼职助研津贴,收入,500.00,交易成功,ALI202611040009
        2026-11-06 08:30:00,交通出行,广州地铁羊城通,地铁通勤充值,支出,50.00,交易成功,ALI202611060024
        2026-11-08 15:20:00,文化教育,高等教育出版社,专业课教材购买,支出,78.00,交易成功,ALI202611080055
        2026-11-10 21:00:00,日用百货,淘宝电商平台,宿舍收纳与耳机配件,支出,128.00,交易成功,ALI202611100088
        2026-11-13 11:00:00,退款,淘宝电商平台,收纳退货退款,不计收支,38.00,已全额退款,ALI202611130101
        2026-11-14 16:40:00,娱乐消费,Steam游戏平台,特惠游戏重复核对测试,支出,98.00,交易成功,ALI202611140340
    """.trimIndent()
}
