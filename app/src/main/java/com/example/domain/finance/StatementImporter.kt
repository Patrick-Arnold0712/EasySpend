package com.example.domain.finance

import com.example.data.model.CandidateResolution
import com.example.data.model.DiscrepancyType
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
    val rawDescription: String
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
            val merchant = cols.getOrNull(merchantIdx)?.trim().orEmpty().ifBlank { "未知商户" }
            val rawGoods = cols.getOrNull(descIdx)?.trim().orEmpty().let { if (it == "/") "" else it }
            val desc = when {
                rawGoods.isNotBlank() -> rawGoods
                txTypeStr.isNotBlank() -> txTypeStr
                else -> ""
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

            val refId = cols.getOrNull(refIdx)?.trim()?.takeIf { it.isNotBlank() && it != "/" }
                ?: "${channel.name}_${millis}_${amountFen}_${merchant.hashCode()}"

            records.add(
                ParsedRawRecord(
                    externalRefId = refId,
                    occurredAtMillis = millis,
                    amountFen = amountFen,
                    direction = direction,
                    channel = channel,
                    fundingSource = fundingSource,
                    sourceType = sourceType,
                    merchant = merchant,
                    rawDescription = if (statusStr.isNotBlank()) "$desc ($statusStr)".trim() else desc
                )
            )
        }
        return records
    }

    private val nonPaymentNoiseKeywords = listOf(
        "条新消息", "语音通话", "视频通话", "撤回了一条消息", "正在登录", "邀请你加入", "请求添加你为朋友"
    )

    private val paymentContextKeywords = listOf(
        "微信支付", "微信收款", "支付宝", "支付成功", "付款", "收款", "到账", "退款",
        "转入", "转出", "扣款", "消费", "支出", "零钱入账", "花呗", "余额宝", "账单", "收钱码",
        "支付人民币", "支付￥", "支付¥"
    )

    private val currencyMarkedAmountRegex = Regex(
        """(?:(?:￥|¥|人民币|CNY)\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)|(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\s*元)"""
    )

    private val verbDecimalAmountRegex = Regex(
        """(?:支付|付款|收款|到账|消费|扣款|退款|转入|转出|金额)[:：\s]*(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)"""
    )

    private val merchantRegex = Regex(
        """(?:向|在|来自|商户[：:]?|收款方[：:]?|付款给|收到)\s*([^\s，,。：:()（）¥￥\d]{2,20}?)(?=\s*(?:支付|付款|消费|转账|退款|收款|的|人民币|￥|¥|\d|$))"""
    )

    fun parsePaymentNotification(
        packageName: String,
        title: String,
        text: String,
        nowMillis: Long = System.currentTimeMillis()
    ): ParsedRawRecord? {
        val cleanTitle = title.trim()
        val cleanText = text.trim()
        if (cleanText.isBlank()) return null

        val combined = "$cleanTitle $cleanText".trim()
        if (nonPaymentNoiseKeywords.any { combined.contains(it) }) return null

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

        val direction = when {
            combined.contains("退款") -> TransactionDirection.REFUND
            combined.contains("收款") || combined.contains("到账") || combined.contains("转入") -> TransactionDirection.INCOME
            combined.contains("转出") || combined.contains("提现") -> TransactionDirection.TRANSFER
            else -> TransactionDirection.EXPENSE
        }

        val extractedMerchant = merchantRegex.find(combined)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotBlank() && it !in listOf("微信", "支付宝", "微信支付") }
        val merchant = extractedMerchant
            ?: cleanTitle.take(18).ifBlank { "${channel.displayName}通知商户" }

        return ParsedRawRecord(
            externalRefId = "NOTIF_${channel.name}_${nowMillis / 1000}_${amountFen}",
            occurredAtMillis = nowMillis,
            amountFen = amountFen,
            direction = direction,
            channel = channel,
            fundingSource = if (direction == TransactionDirection.INCOME) FundingSource.PART_TIME_INCOME else FundingSource.LIVING_ALLOWANCE,
            sourceType = TransactionSourceType.NOTIFICATION_CAPTURE,
            merchant = merchant,
            rawDescription = combined.take(120)
        )
    }

    fun buildStagingCandidates(
        batchId: Long,
        records: List<ParsedRawRecord>,
        existingTransactions: List<TransactionEntity>,
        preferences: List<MerchantPreferenceEntity>,
        existingPendingCandidates: List<ImportCandidateEntity> = emptyList()
    ): List<ImportCandidateEntity> {
        val seenInBatch = mutableListOf<ParsedRawRecord>()
        return records.map { rec ->
            val classification = ClassificationEngine.classify(
                merchant = rec.merchant,
                description = rec.rawDescription,
                direction = rec.direction,
                preferences = preferences
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

            // 2. Fuzzy duplicate check (same amount & direction within 30 mins, or same merchant + amount same day)
            val fuzzyDup = if (exactDup == null && pendingExactDup == null && batchExactDup == null) {
                existingTransactions.firstOrNull { tx ->
                    tx.direction == rec.direction &&
                        tx.amountFen == rec.amountFen &&
                        (abs(tx.occurredAtMillis - rec.occurredAtMillis) <= 30 * 60 * 1000L ||
                            (tx.merchant.equals(rec.merchant, ignoreCase = true) &&
                                abs(tx.occurredAtMillis - rec.occurredAtMillis) <= 24 * 3600 * 1000L))
                }
            } else null
            val pendingFuzzyDup = if (exactDup == null && pendingExactDup == null && batchExactDup == null && fuzzyDup == null) {
                existingPendingCandidates.firstOrNull { c ->
                    c.direction == rec.direction &&
                        c.amountFen == rec.amountFen &&
                        (abs(c.occurredAtMillis - rec.occurredAtMillis) <= 30 * 60 * 1000L ||
                            (c.merchant.equals(rec.merchant, ignoreCase = true) &&
                                abs(c.occurredAtMillis - rec.occurredAtMillis) <= 24 * 3600 * 1000L))
                }
            } else null

            // 3. Same-time/merchant amount discrepancy check (e.g. partial capture vs official bill)
            val amountMismatch = if (exactDup == null && pendingExactDup == null && batchExactDup == null && fuzzyDup == null && pendingFuzzyDup == null) {
                existingTransactions.firstOrNull { tx ->
                    tx.direction == rec.direction &&
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
                resolutionStatus = CandidateResolution.PENDING
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
