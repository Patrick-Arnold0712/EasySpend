package com.example.domain.finance

import com.example.data.model.CategoryEntity
import com.example.data.model.CategoryGroup
import com.example.data.model.ClassificationSource
import com.example.data.model.MerchantPreferenceEntity
import com.example.data.model.PaymentChannel
import com.example.data.model.ReviewStatus
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import java.util.Calendar
import kotlin.math.abs

data class ClassificationResult(
    val categoryId: String,
    val source: ClassificationSource,
    val confidence: Float,
    val needsReview: Boolean,
    val reason: String
)

object ClassificationEngine {

    val defaultCategories: List<CategoryEntity> = listOf(
        // Essential expenses (7)
        CategoryEntity("food", "饮食餐饮", CategoryGroup.ESSENTIAL_EXPENSE, 1),
        CategoryEntity("study", "学习资料", CategoryGroup.ESSENTIAL_EXPENSE, 2),
        CategoryEntity("transit", "交通出行", CategoryGroup.ESSENTIAL_EXPENSE, 3),
        CategoryEntity("daily", "日常必需", CategoryGroup.ESSENTIAL_EXPENSE, 4),
        CategoryEntity("housing", "住宿水电", CategoryGroup.ESSENTIAL_EXPENSE, 5),
        CategoryEntity("medical", "医疗健康", CategoryGroup.ESSENTIAL_EXPENSE, 6),
        CategoryEntity("telecom", "通讯网络", CategoryGroup.ESSENTIAL_EXPENSE, 7),
        // Flexible expenses (8)
        CategoryEntity("beverage", "奶茶咖啡", CategoryGroup.FLEXIBLE_EXPENSE, 8),
        CategoryEntity("entertainment", "娱乐休闲", CategoryGroup.FLEXIBLE_EXPENSE, 9),
        CategoryEntity("shopping", "数码服饰", CategoryGroup.FLEXIBLE_EXPENSE, 10),
        CategoryEntity("beauty", "美妆护肤", CategoryGroup.FLEXIBLE_EXPENSE, 11),
        CategoryEntity("social", "社交聚会", CategoryGroup.FLEXIBLE_EXPENSE, 12),
        CategoryEntity("sports", "运动健身", CategoryGroup.FLEXIBLE_EXPENSE, 13),
        CategoryEntity("travel", "旅行出游", CategoryGroup.FLEXIBLE_EXPENSE, 14),
        CategoryEntity("other_expense", "其他支出", CategoryGroup.FLEXIBLE_EXPENSE, 15),
        // Income (4)
        CategoryEntity("allowance", "家庭生活费", CategoryGroup.INCOME, 16),
        CategoryEntity("part_time", "兼职收入", CategoryGroup.INCOME, 17),
        CategoryEntity("scholarship", "奖学金补助", CategoryGroup.INCOME, 18),
        CategoryEntity("other_income", "其他收入", CategoryGroup.INCOME, 19),
        // Neutral (2)
        CategoryEntity("refund", "退款冲减", CategoryGroup.NEUTRAL, 20),
        CategoryEntity("transfer", "转账不计收支", CategoryGroup.NEUTRAL, 21)
    )

    private val convenienceStoreKeywords = listOf(
        "全家", "便利店", "7-11", "711", "罗森", "美宜佳", "超市", "百货", "天福", "喜士多", "华润万家"
    )

    private val ecommerceKeywords = listOf(
        "淘宝", "天猫", "京东", "拼多多", "抖音商城", "得物", "唯品会", "亚马逊"
    )

    private val meituanKeywords = listOf(
        "美团", "三快", "点评"
    )

    private val rideOrBikeKeywords = listOf(
        "哈啰", "青桔", "滴滴", "高德", "曹操", "t3", "骑安", "享道", "花小猪", "如祺"
    )

    private val campusGenericKeywords = listOf(
        "中山大学", "大学", "学院", "校园卡", "一卡通", "后勤"
    )

    private val branchNoiseRegex = Regex("""[\(（\[【].*?[\)）\]】]|#?\d+号?店?|\d+楼.*$|窗口\d+.*$|分店$""")
    private val tokenSplitRegex = Regex("""[\s,，、;；/\\\-_|·:：()（）\[\]【】]+""")

    fun classify(
        merchant: String,
        description: String,
        direction: TransactionDirection,
        preferences: List<MerchantPreferenceEntity>,
        amountFen: Long = 0L,
        customCategories: List<CategoryEntity> = emptyList(),
        occurredAtMillis: Long = 0L,
        channel: PaymentChannel? = null,
        note: String = "",
        historicalTransactions: List<TransactionEntity> = emptyList(),
        locationLabel: String = ""
    ): ClassificationResult {
        val m = merchant.trim()
        val d = description.trim()
        val n = note.trim()
        val loc = locationLabel.trim()
        val combined = "$m $d $n".lowercase()
        val hourOfDay = extractHourOfDay(occurredAtMillis)
        val isMultiPurposePlatform = isMultiPurposeMerchant(m)

        // 0. Historical manual-classification memory (amount-band + location aware!)
        // Especially powerful when the same merchant (e.g., Meituan / Campus / Convenience store)
        // or same physical place has been classified in past reviews.
        if (historicalTransactions.isNotEmpty() && (m.isNotBlank() || loc.isNotBlank())) {
            matchHistoricalMemory(
                merchant = m,
                description = d,
                note = n,
                locationLabel = loc,
                direction = direction,
                amountFen = amountFen,
                hourOfDay = hourOfDay,
                historicalTransactions = historicalTransactions,
                strictAmountBandFirst = isMultiPurposePlatform
            )?.let { return it }
        }

        // 1. Authorized merchant preference (exact match, then normalized core merchant match)
        val exactPref = preferences.firstOrNull {
            it.merchantKey.equals(m, ignoreCase = true)
        }
        if (exactPref != null) {
            return ClassificationResult(
                categoryId = exactPref.categoryId,
                source = ClassificationSource.MERCHANT_PREFERENCE,
                confidence = 1.0f,
                needsReview = false,
                reason = "已匹配您授权记住的商户「$m」分类偏好"
            )
        }
        val coreMerchant = normalizeMerchantCore(m)
        if (coreMerchant.length >= 2) {
            val fuzzyPref = preferences.firstOrNull { pref ->
                val prefCore = normalizeMerchantCore(pref.merchantKey)
                prefCore.length >= 2 && (
                    coreMerchant.equals(prefCore, ignoreCase = true) ||
                        (prefCore.length >= 3 && coreMerchant.contains(prefCore, ignoreCase = true)) ||
                        (coreMerchant.length >= 3 && prefCore.contains(coreMerchant, ignoreCase = true))
                    )
            }
            if (fuzzyPref != null) {
                return ClassificationResult(
                    categoryId = fuzzyPref.categoryId,
                    source = ClassificationSource.MERCHANT_PREFERENCE,
                    confidence = 0.94f,
                    needsReview = false,
                    reason = "关联到已记住的相似商户「${fuzzyPref.merchantKey}」分类记忆"
                )
            }
        }

        // 2. Non-expense directions
        when (direction) {
            TransactionDirection.REFUND -> {
                return ClassificationResult(
                    categoryId = "refund",
                    source = ClassificationSource.RULE_ENGINE,
                    confidence = 0.96f,
                    needsReview = false,
                    reason = "识别为退款记录，入账后将冲减净支出"
                )
            }
            TransactionDirection.TRANSFER -> {
                return ClassificationResult(
                    categoryId = "transfer",
                    source = ClassificationSource.RULE_ENGINE,
                    confidence = 0.95f,
                    needsReview = false,
                    reason = "识别为内部转账/理财划转，不计入消费支出"
                )
            }
            TransactionDirection.INCOME -> {
                val customIncomeMatch = matchCustomCategoryByKeywords(
                    combined = combined,
                    customCategories = customCategories.filter { it.group == CategoryGroup.INCOME }
                )
                if (customIncomeMatch != null) return customIncomeMatch

                return when {
                    combined.contains("奖学金") || combined.contains("助学金") || combined.contains("补助") ||
                        combined.contains("奖金") || combined.contains("补贴") ->
                        ClassificationResult("scholarship", ClassificationSource.RULE_ENGINE, 0.96f, false, "识别为奖学金/补助到账")
                    combined.contains("生活费") || combined.contains("家庭") || combined.contains("爸") || combined.contains("妈") ->
                        ClassificationResult("allowance", ClassificationSource.RULE_ENGINE, 0.97f, false, "识别为家庭生活费到账")
                    combined.contains("兼职") || combined.contains("助研") || combined.contains("家教") ||
                        combined.contains("津贴") || combined.contains("劳务") || combined.contains("工资") ||
                        combined.contains("实习") ->
                        ClassificationResult("part_time", ClassificationSource.RULE_ENGINE, 0.95f, false, "识别为兼职/劳务收入到账")
                    combined.contains("闲鱼") || combined.contains("二手") || combined.contains("理财") ||
                        combined.contains("收益") || combined.contains("红包") ->
                        ClassificationResult("other_income", ClassificationSource.RULE_ENGINE, 0.88f, false, "识别为其他收入到账")
                    else ->
                        ClassificationResult("part_time", ClassificationSource.RULE_ENGINE, 0.62f, true, "收入来源尚不明确，请确认属于生活费还是兼职/其他收入")
                }
            }
            TransactionDirection.EXPENSE -> Unit
        }

        // 3. User-defined custom expense categories with auto-match keywords
        val customExpenseMatch = matchCustomCategoryByKeywords(
            combined = combined,
            customCategories = customCategories.filter {
                it.group == CategoryGroup.ESSENTIAL_EXPENSE || it.group == CategoryGroup.FLEXIBLE_EXPENSE
            }
        )
        if (customExpenseMatch != null) return customExpenseMatch

        // 4. Amount + time + description aware super-app / platform classification
        classifySuperAppByAmount(m, d, combined, amountFen, hourOfDay)?.let { return it }

        // 5. Multi-purpose convenience stores & e-commerce (amount + time-of-day + description aware)
        val isConvenienceStore = convenienceStoreKeywords.any { m.contains(it, ignoreCase = true) }
        if (isConvenienceStore) {
            return classifyConvenienceStoreByAmount(m, combined, amountFen, hourOfDay)
        }

        val isEcommerce = ecommerceKeywords.any { m.contains(it, ignoreCase = true) }
        if (isEcommerce) {
            return classifyEcommerceByAmount(m, combined, amountFen)
        }

        // 6. Deterministic keyword rules across expanded categories (merchant + goods description + user note)
        val keywordResult = matchDeterministicExpenseKeywords(combined)
        if (keywordResult != null) return keywordResult

        // 6.5 Real-time location scene recognition (especially accurate for personal QR codes & generic merchants)
        if (loc.isNotBlank()) {
            classifyByLocationScene(
                merchant = m,
                locationLabel = loc,
                amountFen = amountFen,
                hourOfDay = hourOfDay
            )?.let { return it }
        }

        // 7. Multi-signal contextual fallback (time-of-day + amount + channel)
        classifyByContextSignals(
            merchant = m,
            combined = combined,
            amountFen = amountFen,
            hourOfDay = hourOfDay,
            channel = channel
        )?.let { return it }

        return ClassificationResult("other_expense", ClassificationSource.RULE_ENGINE, 0.58f, true, "未命中明确分类规则，待用户确认")
    }

    private fun isMultiPurposeMerchant(merchant: String): Boolean {
        return meituanKeywords.any { merchant.contains(it, ignoreCase = true) } ||
            convenienceStoreKeywords.any { merchant.contains(it, ignoreCase = true) } ||
            ecommerceKeywords.any { merchant.contains(it, ignoreCase = true) } ||
            campusGenericKeywords.any { merchant.contains(it, ignoreCase = true) }
    }

    private fun extractHourOfDay(occurredAtMillis: Long): Int? {
        if (occurredAtMillis <= 0L) return null
        val cal = Calendar.getInstance()
        cal.timeInMillis = occurredAtMillis
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    private fun normalizeMerchantCore(merchant: String): String {
        return merchant.trim()
            .replace(branchNoiseRegex, "")
            .replace(Regex("""[\s\-_·]+"""), "")
            .lowercase()
    }

    private fun amountBand(amountFen: Long): Int = when {
        amountFen <= 0L -> 0
        amountFen <= 600L -> 1       // <= ¥6 (bike / metro / small snack)
        amountFen <= 2000L -> 2      // ¥6 - ¥20 (breakfast / canteen / coffee)
        amountFen <= 6000L -> 3      // ¥20 - ¥60 (takeout / meal / movie)
        amountFen <= 18000L -> 4     // ¥60 - ¥180 (shopping / dinner / study books)
        else -> 5                    // > ¥180 (travel / big purchase / tuition)
    }

    private fun isSimilarAmount(aFen: Long, bFen: Long): Boolean {
        if (aFen <= 0L || bFen <= 0L) return false
        if (amountBand(aFen) == amountBand(bFen)) return true
        val minVal = minOf(aFen, bFen).toDouble()
        val maxVal = maxOf(aFen, bFen).toDouble()
        return maxVal / minVal <= 1.85 && abs(aFen - bFen) <= 2500L
    }

    private fun matchHistoricalMemory(
        merchant: String,
        description: String,
        note: String,
        locationLabel: String,
        direction: TransactionDirection,
        amountFen: Long,
        hourOfDay: Int?,
        historicalTransactions: List<TransactionEntity>,
        strictAmountBandFirst: Boolean
    ): ClassificationResult? {
        val eligible = historicalTransactions.filter { tx ->
            tx.direction == direction &&
                (tx.classificationSource == ClassificationSource.USER_CONFIRMED ||
                    tx.classificationSource == ClassificationSource.MERCHANT_PREFERENCE ||
                    tx.reviewStatus == ReviewStatus.USER_CONFIRMED) &&
                tx.categoryId.isNotBlank() &&
                tx.categoryId != "other_expense"
        }
        if (eligible.isEmpty()) return null

        val targetCore = normalizeMerchantCore(merchant)
        val exactMerchantHistory = eligible.filter { it.merchant.equals(merchant, ignoreCase = true) }

        // Tier A: Exact merchant + similar amount band memory (solves Meituan ¥1.8 vs ¥25 learned memory)
        if (exactMerchantHistory.isNotEmpty() && amountFen > 0L) {
            val bandMatches = exactMerchantHistory.filter { isSimilarAmount(it.amountFen, amountFen) }
            if (bandMatches.isNotEmpty()) {
                val best = selectWeightedCategory(bandMatches, amountFen, hourOfDay, "$description $note")
                if (best != null && best.second >= 0.60f) {
                    val amtLabel = MoneyFormatter.formatFen(amountFen)
                    return ClassificationResult(
                        categoryId = best.first,
                        source = ClassificationSource.MERCHANT_PREFERENCE,
                        confidence = (0.90f + 0.08f * best.second).coerceAtMost(0.98f),
                        needsReview = false,
                        reason = "智能记忆命中：过往「$merchant」相近金额($amtLabel)均核对归类于此（${bandMatches.size}次历史记录）"
                    )
                }
            }
        }

        // Tier B: Exact merchant overall consensus (skip if multi-purpose merchant has conflicting history in other amount bands)
        if (exactMerchantHistory.isNotEmpty() && !strictAmountBandFirst) {
            val best = selectWeightedCategory(exactMerchantHistory, amountFen, hourOfDay, "$description $note")
            if (best != null && best.second >= 0.65f) {
                return ClassificationResult(
                    categoryId = best.first,
                    source = ClassificationSource.MERCHANT_PREFERENCE,
                    confidence = (0.86f + 0.10f * best.second).coerceAtMost(0.96f),
                    needsReview = false,
                    reason = "智能记忆命中：根据过往 ${exactMerchantHistory.size} 笔「$merchant」手动核对记录自动归类"
                )
            }
        }

        // Tier C: Fuzzy core merchant match (e.g., "沙县小吃(东校)" matches historical "沙县小吃(南校)")
        if (targetCore.length >= 2) {
            val coreMatches = eligible.filter { tx ->
                val txCore = normalizeMerchantCore(tx.merchant)
                txCore.length >= 2 && (
                    txCore == targetCore ||
                        (txCore.length >= 3 && targetCore.contains(txCore)) ||
                        (targetCore.length >= 3 && txCore.contains(targetCore))
                    )
            }
            val scopedCore = if (strictAmountBandFirst && amountFen > 0L) {
                coreMatches.filter { isSimilarAmount(it.amountFen, amountFen) }
            } else {
                coreMatches
            }
            if (scopedCore.isNotEmpty()) {
                val best = selectWeightedCategory(scopedCore, amountFen, hourOfDay, "$description $note")
                if (best != null && best.second >= 0.65f) {
                    val sampleMerchant = scopedCore.first().merchant
                    return ClassificationResult(
                        categoryId = best.first,
                        source = ClassificationSource.MERCHANT_PREFERENCE,
                        confidence = 0.88f,
                        needsReview = false,
                        reason = "智能记忆命中：关联历史相似商户「$sampleMerchant」的手动分类习惯"
                    )
                }
            }
        }

        // Tier D: Distinctive description/note token memory (e.g., "羽毛球馆订场", "助研打印")
        val targetTokens = extractMeaningfulTokens("$description $note")
        if (targetTokens.isNotEmpty()) {
            val tokenMatches = eligible.filter { tx ->
                val txTokens = extractMeaningfulTokens("${tx.rawDescription} ${tx.note}")
                txTokens.any { it in targetTokens }
            }
            if (tokenMatches.size >= 2 || (tokenMatches.size == 1 && isSimilarAmount(tokenMatches.first().amountFen, amountFen))) {
                val best = selectWeightedCategory(tokenMatches, amountFen, hourOfDay, "$description $note")
                if (best != null && best.second >= 0.70f) {
                    return ClassificationResult(
                        categoryId = best.first,
                        source = ClassificationSource.MERCHANT_PREFERENCE,
                        confidence = 0.85f,
                        needsReview = false,
                        reason = "智能记忆命中：商品说明/备注与过往 ${tokenMatches.size} 笔手动分类记录高度一致"
                    )
                }
            }
        }

        // Tier E: Same physical location memory (solves generic personal QR codes at regular canteens/clinics/gyms)
        if (locationLabel.isNotBlank()) {
            val targetPlaceTokens = extractLocationPlaceTokens(locationLabel)
            if (targetPlaceTokens.isNotEmpty()) {
                val locMatches = eligible.filter { tx ->
                    tx.locationLabel.isNotBlank() &&
                        extractLocationPlaceTokens(tx.locationLabel).any { it in targetPlaceTokens }
                }
                val scopedLoc = if (amountFen > 0L) {
                    locMatches.filter { isSimilarAmount(it.amountFen, amountFen) }.ifEmpty { locMatches }
                } else {
                    locMatches
                }
                if (scopedLoc.size >= 2 || (scopedLoc.size == 1 && isSimilarAmount(scopedLoc.first().amountFen, amountFen))) {
                    val best = selectWeightedCategory(scopedLoc, amountFen, hourOfDay, "$description $note")
                    if (best != null && best.second >= 0.72f) {
                        return ClassificationResult(
                            categoryId = best.first,
                            source = ClassificationSource.MERCHANT_PREFERENCE,
                            confidence = 0.87f,
                            needsReview = false,
                            reason = "定位场景记忆：你此前在「$locationLabel」的 ${scopedLoc.size} 笔消费均归类于此"
                        )
                    }
                }
            }
        }

        return null
    }

    private fun extractLocationPlaceTokens(locationLabel: String): Set<String> {
        val genericAreaSuffixes = setOf("定位", "中国", "省", "市", "区", "县", "镇", "街道")
        return locationLabel.lowercase()
            .split(tokenSplitRegex)
            .map { it.trim() }
            .filter { token ->
                token.length >= 2 &&
                    token !in genericAreaSuffixes &&
                    !token.endsWith("省") &&
                    !token.endsWith("市") &&
                    !token.endsWith("区") &&
                    !token.all { ch -> ch.isDigit() || ch == '.' || ch == '°' || ch == 'n' || ch == 'e' || ch == 's' || ch == 'w' }
            }
            .toSet()
    }

    private fun classifyByLocationScene(
        merchant: String,
        locationLabel: String,
        amountFen: Long,
        hourOfDay: Int?
    ): ClassificationResult? {
        val loc = locationLabel.lowercase()
        val displayPlace = locationLabel.take(24)

        return when {
            loc.contains("医院") || loc.contains("诊所") || loc.contains("门诊") ||
                loc.contains("药房") || loc.contains("药店") || loc.contains("卫生院") ||
                loc.contains("体检") || loc.contains("口腔") || loc.contains("眼科") ->
                ClassificationResult(
                    categoryId = "medical",
                    source = ClassificationSource.RULE_ENGINE,
                    confidence = 0.91f,
                    needsReview = false,
                    reason = "同步定位到医疗场所「$displayPlace」，精准归类为医疗健康"
                )

            loc.contains("食堂") || loc.contains("饭堂") || loc.contains("美食") ||
                loc.contains("餐厅") || loc.contains("餐饮") || loc.contains("小吃") ||
                loc.contains("火锅") || loc.contains("烧烤") || loc.contains("餐馆") ||
                loc.contains("春晖园") || loc.contains("松涛园") || loc.contains("学五") ->
                ClassificationResult(
                    categoryId = "food",
                    source = ClassificationSource.RULE_ENGINE,
                    confidence = 0.91f,
                    needsReview = false,
                    reason = "同步定位到餐饮场所「$displayPlace」，精准归类为饮食餐饮"
                )

            loc.contains("咖啡") || loc.contains("奶茶") || loc.contains("茶饮") ||
                loc.contains("甜品") || loc.contains("茶室") ->
                ClassificationResult(
                    categoryId = "beverage",
                    source = ClassificationSource.RULE_ENGINE,
                    confidence = 0.90f,
                    needsReview = false,
                    reason = "同步定位到饮品门店「$displayPlace」，精准归类为奶茶咖啡"
                )

            loc.contains("影城") || loc.contains("电影院") || loc.contains("剧院") ||
                loc.contains("ktv") || loc.contains("密室") || loc.contains("剧本杀") ||
                loc.contains("电竞") || loc.contains("网咖") || loc.contains("游艺") ->
                ClassificationResult(
                    categoryId = "entertainment",
                    source = ClassificationSource.RULE_ENGINE,
                    confidence = 0.90f,
                    needsReview = false,
                    reason = "同步定位到休闲娱乐场所「$displayPlace」，精准归类为娱乐休闲"
                )

            loc.contains("体育馆") || loc.contains("球馆") || loc.contains("健身") ||
                loc.contains("游泳馆") || loc.contains("运动中心") || loc.contains("羽毛球") ||
                loc.contains("篮球场") || loc.contains("网球") ->
                ClassificationResult(
                    categoryId = "sports",
                    source = ClassificationSource.RULE_ENGINE,
                    confidence = 0.90f,
                    needsReview = false,
                    reason = "同步定位到运动场馆「$displayPlace」，精准归类为运动健身"
                )

            loc.contains("景区") || loc.contains("景点") || loc.contains("风景") ||
                loc.contains("博物馆") || loc.contains("游乐场") || loc.contains("度假") ||
                loc.contains("酒店") || loc.contains("民宿") || loc.contains("宾馆") ->
                ClassificationResult(
                    categoryId = "travel",
                    source = ClassificationSource.RULE_ENGINE,
                    confidence = 0.89f,
                    needsReview = false,
                    reason = "同步定位到酒旅/景区「$displayPlace」，精准归类为旅行出游"
                )

            loc.contains("地铁") || loc.contains("火车站") || loc.contains("高铁") ||
                loc.contains("机场") || loc.contains("客运") || loc.contains("汽车站") ||
                loc.contains("公交") || loc.contains("停车场") || loc.contains("加油站") -> {
                if ((loc.contains("机场") || loc.contains("高铁") || loc.contains("火车站")) && amountFen >= 15000L) {
                    ClassificationResult(
                        categoryId = "travel",
                        source = ClassificationSource.RULE_ENGINE,
                        confidence = 0.84f,
                        needsReview = true,
                        reason = "同步定位到交通枢纽「$displayPlace」大额消费，推测为出行车票/机票"
                    )
                } else {
                    ClassificationResult(
                        categoryId = "transit",
                        source = ClassificationSource.RULE_ENGINE,
                        confidence = 0.89f,
                        needsReview = false,
                        reason = "同步定位到交通场所「$displayPlace」，精准归类为交通出行"
                    )
                }
            }

            loc.contains("图书馆") || loc.contains("教学楼") || loc.contains("实验楼") ||
                loc.contains("书店") || loc.contains("文印") || loc.contains("打印") ||
                loc.contains("文具") ->
                ClassificationResult(
                    categoryId = "study",
                    source = ClassificationSource.RULE_ENGINE,
                    confidence = 0.88f,
                    needsReview = false,
                    reason = "同步定位到学习场所「$displayPlace」，精准归类为学习资料"
                )

            loc.contains("菜鸟") || loc.contains("驿站") || loc.contains("快递") ||
                loc.contains("理发") || loc.contains("美发") || loc.contains("洗衣") ->
                ClassificationResult(
                    categoryId = "daily",
                    source = ClassificationSource.RULE_ENGINE,
                    confidence = 0.88f,
                    needsReview = false,
                    reason = "同步定位到生活服务点「$displayPlace」，精准归类为日常必需"
                )

            loc.contains("物业") || loc.contains("宿舍") || loc.contains("公寓") -> {
                if (amountFen >= 5000L) {
                    ClassificationResult(
                        categoryId = "housing",
                        source = ClassificationSource.RULE_ENGINE,
                        confidence = 0.82f,
                        needsReview = true,
                        reason = "同步定位在居住区「$displayPlace」且金额较大，推测为住宿水电/物业费"
                    )
                } else null
            }

            loc.contains("商场") || loc.contains("购物中心") || loc.contains("广场") ||
                loc.contains("百货") || loc.contains("奥特莱斯") || loc.contains("数码城") -> {
                val isMealHour = hourOfDay != null && (hourOfDay in 11..13 || hourOfDay in 17..20)
                if (isMealHour && amountFen in 1L..5500L) {
                    ClassificationResult(
                        categoryId = "food",
                        source = ClassificationSource.RULE_ENGINE,
                        confidence = 0.78f,
                        needsReview = true,
                        reason = "同步定位在商圈「$displayPlace」且处于用餐时段，推测为商圈餐饮（商户：$merchant）"
                    )
                } else if (amountFen >= 5500L) {
                    ClassificationResult(
                        categoryId = "shopping",
                        source = ClassificationSource.RULE_ENGINE,
                        confidence = 0.82f,
                        needsReview = true,
                        reason = "同步定位在商圈「$displayPlace」消费，推测为服饰/数码购物"
                    )
                } else null
            }

            else -> null
        }
    }

    private fun selectWeightedCategory(
        matches: List<TransactionEntity>,
        amountFen: Long,
        hourOfDay: Int?,
        detailText: String
    ): Pair<String, Float>? {
        if (matches.isEmpty()) return null
        val targetTokens = extractMeaningfulTokens(detailText)
        val scoreByCategory = mutableMapOf<String, Float>()
        var totalWeight = 0f

        for (tx in matches) {
            var w = 1.0f
            if (tx.classificationSource == ClassificationSource.USER_CONFIRMED) w += 0.5f
            if (amountFen > 0L && isSimilarAmount(tx.amountFen, amountFen)) w += 0.8f
            val txHour = extractHourOfDay(tx.occurredAtMillis)
            if (hourOfDay != null && txHour != null && abs(hourOfDay - txHour) <= 2) w += 0.35f
            if (targetTokens.isNotEmpty()) {
                val txTokens = extractMeaningfulTokens("${tx.rawDescription} ${tx.note}")
                if (txTokens.any { it in targetTokens }) w += 0.7f
            }
            scoreByCategory[tx.categoryId] = (scoreByCategory[tx.categoryId] ?: 0f) + w
            totalWeight += w
        }
        val top = scoreByCategory.maxByOrNull { it.value } ?: return null
        if (totalWeight <= 0f) return null
        return top.key to (top.value / totalWeight)
    }

    private fun extractMeaningfulTokens(text: String): Set<String> {
        val stopWords = setOf(
            "支付成功", "交易成功", "商户消费", "零钱", "微信", "支付宝", "订单", "消费", "支出", "收入", "手动记账", "演示基线账单"
        )
        return text.lowercase()
            .split(tokenSplitRegex)
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stopWords && !it.all { ch -> ch.isDigit() } }
            .toSet()
    }

    private fun matchDeterministicExpenseKeywords(combined: String): ClassificationResult? = when {
        combined.contains("瑞幸") || combined.contains("星巴克") || combined.contains("喜茶") ||
            combined.contains("霸王茶姬") || combined.contains("奈雪") || combined.contains("茶百道") ||
            combined.contains("古茗") || combined.contains("蜜雪") || combined.contains("书亦") ||
            combined.contains("一点点") || combined.contains("库迪") || combined.contains("咖啡") ||
            combined.contains("奶茶") || combined.contains("烧仙草") || combined.contains("茶饮") ->
            ClassificationResult("beverage", ClassificationSource.RULE_ENGINE, 0.94f, false, "规则匹配奶茶咖啡")

        combined.contains("食堂") || combined.contains("饭堂") || combined.contains("餐厅") ||
            combined.contains("外卖") || combined.contains("餐饮") || combined.contains("麦当劳") ||
            combined.contains("肯德基") || combined.contains("饭") || combined.contains("小吃") ||
            combined.contains("水饺") || combined.contains("大饼") || combined.contains("春晖园") ||
            combined.contains("松涛园") || combined.contains("大众窗口") || combined.contains("窗口") ||
            combined.contains("学五") || combined.contains("面") || combined.contains("东厅") ||
            combined.contains("火锅") || combined.contains("烧烤") || combined.contains("麻辣烫") ||
            combined.contains("快餐") || combined.contains("面包") || combined.contains("水果") ||
            combined.contains("早餐") || combined.contains("午餐") || combined.contains("晚餐") ||
            combined.contains("夜宵") ->
            ClassificationResult("food", ClassificationSource.RULE_ENGINE, 0.94f, false, "规则匹配饮食餐饮")

        combined.contains("打印") || combined.contains("教材") || combined.contains("出版") ||
            combined.contains("书店") || combined.contains("知网") || combined.contains("考试") ||
            combined.contains("文具") || combined.contains("学习") || combined.contains("讲义") ||
            combined.contains("报名") || combined.contains("课程") || combined.contains("网课") ->
            ClassificationResult("study", ClassificationSource.RULE_ENGINE, 0.95f, false, "规则匹配学习资料")

        combined.contains("地铁") || combined.contains("公交") || combined.contains("羊城通") ||
            combined.contains("滴滴") || combined.contains("打车") || combined.contains("高铁") ||
            combined.contains("火车") || combined.contains("12306") || combined.contains("单车") ||
            combined.contains("骑行") || combined.contains("交通") || combined.contains("乘车") ||
            combined.contains("通勤") || combined.contains("停车") ->
            ClassificationResult("transit", ClassificationSource.RULE_ENGINE, 0.95f, false, "规则匹配交通出行")

        combined.contains("水电") || combined.contains("电费") || combined.contains("水费") ||
            combined.contains("房租") || combined.contains("宿舍") || combined.contains("物业") ||
            combined.contains("空调费") || combined.contains("燃气") ->
            ClassificationResult("housing", ClassificationSource.RULE_ENGINE, 0.94f, false, "规则匹配住宿水电")

        combined.contains("医院") || combined.contains("药房") || combined.contains("大药房") ||
            combined.contains("药店") || combined.contains("挂号") || combined.contains("体检") ||
            combined.contains("牙科") || combined.contains("诊所") || combined.contains("买药") ||
            combined.contains("医疗") || combined.contains("药") ->
            ClassificationResult("medical", ClassificationSource.RULE_ENGINE, 0.94f, false, "规则匹配医疗健康")

        combined.contains("话费") || combined.contains("流量") || combined.contains("移动") ||
            combined.contains("联通") || combined.contains("电信") || combined.contains("校园网") ||
            combined.contains("宽带") || combined.contains("通信") || combined.contains("通讯") ->
            ClassificationResult("telecom", ClassificationSource.RULE_ENGINE, 0.94f, false, "规则匹配通讯网络")

        combined.contains("理发") || combined.contains("洗衣") || combined.contains("日用") ||
            combined.contains("纸巾") || combined.contains("洗发") || combined.contains("沐浴") ||
            combined.contains("快递") || combined.contains("顺丰") || combined.contains("菜鸟") ||
            combined.contains("证件照") ->
            ClassificationResult("daily", ClassificationSource.RULE_ENGINE, 0.90f, false, "规则匹配日常必需")

        combined.contains("健身") || combined.contains("球馆") || combined.contains("羽毛球") ||
            combined.contains("篮球") || combined.contains("游泳") || combined.contains("keep") ||
            combined.contains("迪卡侬") || combined.contains("运动") || combined.contains("瑜伽") ->
            ClassificationResult("sports", ClassificationSource.RULE_ENGINE, 0.93f, false, "规则匹配运动健身")

        combined.contains("酒店") || combined.contains("民宿") || combined.contains("携程") ||
            combined.contains("去哪儿") || combined.contains("飞猪") || combined.contains("机票") ||
            combined.contains("景点") || combined.contains("门票") || combined.contains("旅游") ->
            ClassificationResult("travel", ClassificationSource.RULE_ENGINE, 0.93f, false, "规则匹配旅行出游")

        combined.contains("护肤") || combined.contains("化妆品") || combined.contains("美妆") ||
            combined.contains("面膜") || combined.contains("防晒") || combined.contains("口红") ||
            combined.contains("屈臣氏") || combined.contains("丝芙兰") ->
            ClassificationResult("beauty", ClassificationSource.RULE_ENGINE, 0.92f, false, "规则匹配美妆护肤")

        combined.contains("红包") || combined.contains("聚餐") || combined.contains("礼物") ||
            combined.contains("生日") || combined.contains("社团") || combined.contains("份子") ||
            combined.contains("请客") ->
            ClassificationResult("social", ClassificationSource.RULE_ENGINE, 0.90f, false, "规则匹配社交聚会")

        combined.contains("影城") || combined.contains("电影") || combined.contains("steam") ||
            combined.contains("游戏") || combined.contains("ktv") || combined.contains("音乐") ||
            combined.contains("会员") || combined.contains("视频") || combined.contains("演出") ||
            combined.contains("剧本杀") || combined.contains("桌游") || combined.contains("哔哩哔哩") ||
            combined.contains("网易云") ->
            ClassificationResult("entertainment", ClassificationSource.RULE_ENGINE, 0.93f, false, "规则匹配娱乐休闲")

        combined.contains("数码") || combined.contains("服装") || combined.contains("衣服") ||
            combined.contains("鞋") || combined.contains("饰品") || combined.contains("优衣库") ||
            combined.contains("耳机") || combined.contains("键盘") || combined.contains("手机") ->
            ClassificationResult("shopping", ClassificationSource.RULE_ENGINE, 0.90f, false, "规则匹配数码服饰")

        else -> null
    }

    private fun classifyByContextSignals(
        merchant: String,
        combined: String,
        amountFen: Long,
        hourOfDay: Int?,
        channel: PaymentChannel?
    ): ClassificationResult? {
        if (amountFen <= 0L) return null
        val formattedAmt = MoneyFormatter.formatFen(amountFen)
        val isMealHour = hourOfDay != null && (hourOfDay in 7..9 || hourOfDay in 11..13 || hourOfDay in 17..20)
        val isLateNight = hourOfDay != null && (hourOfDay >= 22 || hourOfDay <= 1)
        val isAfternoonTea = hourOfDay != null && hourOfDay in 14..16

        if (isMealHour && amountFen in 400L..4500L) {
            return ClassificationResult(
                categoryId = "food",
                source = ClassificationSource.RULE_ENGINE,
                confidence = 0.74f,
                needsReview = true,
                reason = "结合用餐时段(${hourOfDay}点)与金额($formattedAmt)，智能推测「$merchant」为饮食餐饮，请核对"
            )
        }
        if (isLateNight && amountFen in 800L..5500L) {
            return ClassificationResult(
                categoryId = "food",
                source = ClassificationSource.RULE_ENGINE,
                confidence = 0.70f,
                needsReview = true,
                reason = "结合夜间时段(${hourOfDay}点)与金额($formattedAmt)，推测为夜宵/餐饮，请核对"
            )
        }
        if (isAfternoonTea && amountFen in 900L..3000L && (combined.contains("饮") || combined.contains("茶") || combined.contains("铺"))) {
            return ClassificationResult(
                categoryId = "beverage",
                source = ClassificationSource.RULE_ENGINE,
                confidence = 0.75f,
                needsReview = true,
                reason = "结合下午茶时段(${hourOfDay}点)与金额($formattedAmt)，推测为奶茶咖啡"
            )
        }
        if (channel == PaymentChannel.BANK_CARD && amountFen >= 30000L) {
            return ClassificationResult(
                categoryId = "housing",
                source = ClassificationSource.RULE_ENGINE,
                confidence = 0.65f,
                needsReview = true,
                reason = "银行卡大额支出($formattedAmt)，推测为住宿水电或大额缴费，请确认"
            )
        }
        return null
    }

    private fun matchCustomCategoryByKeywords(
        combined: String,
        customCategories: List<CategoryEntity>
    ): ClassificationResult? {
        for (cat in customCategories) {
            if (!cat.isCustom && cat.keywords.isBlank()) continue
            val tokens = cat.keywords
                .split(',', '，', '、', ';', '；', ' ')
                .map { it.trim().lowercase() }
                .filter { it.length >= 2 }
            val matchedToken = tokens.firstOrNull { combined.contains(it) }
            if (matchedToken != null) {
                return ClassificationResult(
                    categoryId = cat.id,
                    source = ClassificationSource.RULE_ENGINE,
                    confidence = 0.92f,
                    needsReview = false,
                    reason = "命中自定义分类「${cat.name}」关键词「$matchedToken」"
                )
            }
        }
        return null
    }

    private fun classifySuperAppByAmount(
        merchant: String,
        description: String,
        combined: String,
        amountFen: Long,
        hourOfDay: Int? = null
    ): ClassificationResult? {
        val formattedAmt = if (amountFen > 0L) MoneyFormatter.formatFen(amountFen) else ""

        // 1. Meituan super-app (美团 / 美团平台商户 / 美团支付 / 美团点评)
        if (meituanKeywords.any { merchant.contains(it, ignoreCase = true) || combined.contains("美团") }) {
            // Explicit product keywords take highest priority
            return when {
                combined.contains("单车") || combined.contains("骑行") || combined.contains("电单车") ||
                    combined.contains("打车") || combined.contains("火车") || combined.contains("车票") ->
                    ClassificationResult("transit", ClassificationSource.RULE_ENGINE, 0.95f, false, "美团交通/单车订单，匹配交通出行")

                combined.contains("买药") || combined.contains("药房") || combined.contains("药店") || combined.contains("医药") ->
                    ClassificationResult("medical", ClassificationSource.RULE_ENGINE, 0.94f, false, "美团买药订单，匹配医疗健康")

                combined.contains("酒店") || combined.contains("民宿") || combined.contains("门票") || combined.contains("景点") ->
                    ClassificationResult("travel", ClassificationSource.RULE_ENGINE, 0.93f, false, "美团酒旅门票订单，匹配旅行出游")

                combined.contains("电影") || combined.contains("猫眼") || combined.contains("影城") ||
                    combined.contains("ktv") || combined.contains("剧本杀") || combined.contains("足疗") ->
                    ClassificationResult("entertainment", ClassificationSource.RULE_ENGINE, 0.92f, false, "美团休闲娱乐订单，匹配娱乐休闲")

                combined.contains("奶茶") || combined.contains("咖啡") || combined.contains("烧仙草") ||
                    combined.contains("茶饮") || combined.contains("蜜雪") || combined.contains("瑞幸") ->
                    ClassificationResult("beverage", ClassificationSource.RULE_ENGINE, 0.92f, false, "美团饮品订单，匹配奶茶咖啡")

                combined.contains("超市") || combined.contains("便利") || combined.contains("闪购") || combined.contains("小象") ->
                    if (amountFen in 1L..2500L) {
                        ClassificationResult("food", ClassificationSource.RULE_ENGINE, 0.84f, false, "美团便利店/闪购小额订单（$formattedAmt），判断为零食饮品/餐饮")
                    } else {
                        ClassificationResult("daily", ClassificationSource.RULE_ENGINE, 0.84f, false, "美团超市/闪购订单（$formattedAmt），判断为日用百货采购")
                    }

                combined.contains("外卖") || combined.contains("点餐") || combined.contains("餐厅") || combined.contains("食堂") ->
                    ClassificationResult("food", ClassificationSource.RULE_ENGINE, 0.94f, false, "美团外卖/餐饮订单，匹配饮食餐饮")

                // Amount + time-of-day aware smart classification for generic Meituan bills
                amountFen in 1L..500L ->
                    ClassificationResult(
                        categoryId = "transit",
                        source = ClassificationSource.RULE_ENGINE,
                        confidence = 0.89f,
                        needsReview = false,
                        reason = if (hourOfDay != null && (hourOfDay in 7..9 || hourOfDay in 17..19)) {
                            "美团通勤时段(${hourOfDay}点)小额账单（$formattedAmt ≤ ¥5），智能归类为共享单车/骑行"
                        } else {
                            "美团小额账单（$formattedAmt ≤ ¥5），按金额区间智能归类为共享单车/骑行"
                        }
                    )

                amountFen in 501L..1200L ->
                    if (hourOfDay != null && hourOfDay in 14..16) {
                        ClassificationResult(
                            categoryId = "beverage",
                            source = ClassificationSource.RULE_ENGINE,
                            confidence = 0.82f,
                            needsReview = false,
                            reason = "美团下午茶时段(${hourOfDay}点)小额订单（$formattedAmt），推测为奶茶/饮品"
                        )
                    } else {
                        ClassificationResult(
                            categoryId = "food",
                            source = ClassificationSource.RULE_ENGINE,
                            confidence = 0.84f,
                            needsReview = false,
                            reason = "美团轻食/小吃区间（$formattedAmt），按金额智能归类为饮食餐饮"
                        )
                    }

                amountFen in 1201L..12000L ->
                    ClassificationResult(
                        categoryId = "food",
                        source = ClassificationSource.RULE_ENGINE,
                        confidence = 0.88f,
                        needsReview = false,
                        reason = "美团常见餐费区间（$formattedAmt），按金额智能归类为外卖/到店餐饮"
                    )

                amountFen in 12001L..25000L ->
                    ClassificationResult(
                        categoryId = "social",
                        source = ClassificationSource.RULE_ENGINE,
                        confidence = 0.72f,
                        needsReview = true,
                        reason = "美团中高额账单（$formattedAmt），按金额推测为多人聚餐或团购，请核对"
                    )

                amountFen > 25000L ->
                    ClassificationResult(
                        categoryId = "travel",
                        source = ClassificationSource.RULE_ENGINE,
                        confidence = 0.70f,
                        needsReview = true,
                        reason = "美团大额账单（$formattedAmt），按金额推测为酒店住宿或门票出游，请确认"
                    )

                else ->
                    ClassificationResult("food", ClassificationSource.RULE_ENGINE, 0.78f, false, "美团常规消费，默认归类饮食餐饮")
            }
        }

        // 2. Ride-hailing / Shared bike platforms (哈啰 / 青桔 / 滴滴 / 骑安 / 高德)
        if (rideOrBikeKeywords.any { merchant.contains(it, ignoreCase = true) || description.contains(it, ignoreCase = true) }) {
            return when {
                amountFen in 1L..600L ->
                    ClassificationResult("transit", ClassificationSource.RULE_ENGINE, 0.95f, false, "出行平台小额账单（$formattedAmt），智能判断为共享单车/电单车骑行")
                amountFen > 15000L ->
                    ClassificationResult("travel", ClassificationSource.RULE_ENGINE, 0.76f, true, "出行平台大额账单（$formattedAmt），推测为跨城顺风车或租车出游，请核对")
                else ->
                    ClassificationResult("transit", ClassificationSource.RULE_ENGINE, 0.95f, false, "出行平台账单（$formattedAmt），智能归类交通出行")
            }
        }

        // 3. University / Campus card generic merchant (e.g. 中山大学 + 商户订单号 / 校园轻松付...)
        if (campusGenericKeywords.any { merchant.contains(it, ignoreCase = true) || description.contains(it, ignoreCase = true) } ||
            combined.contains("校园轻松付")
        ) {
            // Check explicit campus facility keywords first
            return when {
                combined.contains("春晖园") || combined.contains("松涛园") || combined.contains("学五") ||
                    combined.contains("食堂") || combined.contains("饭堂") || combined.contains("大众窗口") ||
                    combined.contains("窗口") || combined.contains("大饼") || combined.contains("水饺") ||
                    combined.contains("面") || combined.contains("东厅") || combined.contains("餐") ->
                    ClassificationResult("food", ClassificationSource.RULE_ENGINE, 0.95f, false, "校园食堂/餐饮消费，匹配饮食餐饮")

                combined.contains("水电") || combined.contains("电费") || combined.contains("水费") ||
                    combined.contains("宿管") || combined.contains("宿舍") ->
                    ClassificationResult("housing", ClassificationSource.RULE_ENGINE, 0.95f, false, "校园住宿水电缴费，匹配住宿水电")

                combined.contains("校医院") || combined.contains("医院") || combined.contains("体检") || combined.contains("挂号") ->
                    ClassificationResult("medical", ClassificationSource.RULE_ENGINE, 0.95f, false, "校医院/体检消费，匹配医疗健康")

                combined.contains("校园网") || combined.contains("网费") ->
                    ClassificationResult("telecom", ClassificationSource.RULE_ENGINE, 0.95f, false, "校园网缴费，匹配通讯网络")

                combined.contains("教材") || combined.contains("打印") || combined.contains("考试") ||
                    combined.contains("报名") || combined.contains("图书") || combined.contains("资料") ->
                    ClassificationResult("study", ClassificationSource.RULE_ENGINE, 0.95f, false, "校园学习资料/打印缴费，匹配学习资料")

                // Amount-aware campus inference when description is just order number
                amountFen in 1L..5000L ->
                    ClassificationResult(
                        categoryId = "food",
                        source = ClassificationSource.RULE_ENGINE,
                        confidence = 0.86f,
                        needsReview = false,
                        reason = "校园常规餐费区间（$formattedAmt），按金额智能归类为饮食餐饮"
                    )

                amountFen in 5001L..20000L ->
                    ClassificationResult(
                        categoryId = "housing",
                        source = ClassificationSource.RULE_ENGINE,
                        confidence = 0.72f,
                        needsReview = true,
                        reason = "校园中等金额账单（$formattedAmt），推测为宿舍水电/网费充值或教材费，请核对"
                    )

                amountFen > 20000L ->
                    ClassificationResult(
                        categoryId = "study",
                        source = ClassificationSource.RULE_ENGINE,
                        confidence = 0.70f,
                        needsReview = true,
                        reason = "校园大额缴费（$formattedAmt），推测为教材/考试或住宿费，请确认分类"
                    )

                else -> null
            }
        }

        return null
    }

    private fun classifyConvenienceStoreByAmount(
        merchant: String,
        combined: String,
        amountFen: Long,
        hourOfDay: Int? = null
    ): ClassificationResult {
        val formattedAmt = if (amountFen > 0L) MoneyFormatter.formatFen(amountFen) else ""
        val isMealTime = hourOfDay != null && (hourOfDay in 7..9 || hourOfDay in 11..13 || hourOfDay in 17..20 || hourOfDay >= 22)
        val guessed = when {
            combined.contains("外卖") || combined.contains("早餐") || combined.contains("午餐") ||
                combined.contains("便当") || combined.contains("饭团") -> "food"
            combined.contains("奶茶") || combined.contains("咖啡") -> "beverage"
            combined.contains("教材") || combined.contains("文具") || combined.contains("书") -> "study"
            combined.contains("药") -> "medical"
            amountFen in 1L..2000L || (isMealTime && amountFen <= 3500L) -> "food"
            else -> "daily"
        }
        val hint = when {
            isMealTime && guessed == "food" ->
                "「$merchant」在用餐时段(${hourOfDay}点)消费 $formattedAmt，推测为便当/早餐零食，请确认"
            amountFen in 1L..2000L && guessed == "food" ->
                "「$merchant」小额消费（$formattedAmt ≤ ¥20），推测为早餐/零食餐饮，请确认"
            amountFen > 2000L && guessed == "daily" ->
                "「$merchant」中大额消费（$formattedAmt），推测为日用百货采购，请确认"
            else ->
                "「$merchant」属于多用途商户，系统暂未直接定性，请确认实际消费类别"
        }
        return ClassificationResult(
            categoryId = guessed,
            source = ClassificationSource.RULE_ENGINE,
            confidence = 0.68f,
            needsReview = true,
            reason = hint
        )
    }

    private fun classifyEcommerceByAmount(
        merchant: String,
        combined: String,
        amountFen: Long
    ): ClassificationResult {
        val formattedAmt = if (amountFen > 0L) MoneyFormatter.formatFen(amountFen) else ""
        val guessed = when {
            combined.contains("零食") || combined.contains("水果") || combined.contains("外卖") -> "food"
            combined.contains("教材") || combined.contains("文具") || combined.contains("书") -> "study"
            combined.contains("护肤") || combined.contains("美妆") || combined.contains("面膜") -> "beauty"
            combined.contains("药") || combined.contains("医疗") -> "medical"
            combined.contains("运动") || combined.contains("健身") -> "sports"
            combined.contains("耳机") || combined.contains("服饰") || combined.contains("鞋") || combined.contains("数码") -> "shopping"
            amountFen in 1L..3000L -> "daily"
            else -> "shopping"
        }
        val hint = when {
            amountFen in 1L..3000L && guessed == "daily" ->
                "「$merchant」小额订单（$formattedAmt ≤ ¥30），推测为日用小物，请确认"
            amountFen > 3000L && guessed == "shopping" ->
                "「$merchant」中大额订单（$formattedAmt），推测为服饰/数码购物，请确认"
            else ->
                "「$merchant」属于多用途电商平台，请确认实际消费类别"
        }
        return ClassificationResult(
            categoryId = guessed,
            source = ClassificationSource.RULE_ENGINE,
            confidence = 0.66f,
            needsReview = true,
            reason = hint
        )
    }
}
