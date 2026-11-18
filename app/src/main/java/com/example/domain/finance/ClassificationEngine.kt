package com.example.domain.finance

import com.example.data.model.CategoryEntity
import com.example.data.model.CategoryGroup
import com.example.data.model.ClassificationSource
import com.example.data.model.MerchantPreferenceEntity
import com.example.data.model.TransactionDirection

data class ClassificationResult(
    val categoryId: String,
    val source: ClassificationSource,
    val confidence: Float,
    val needsReview: Boolean,
    val reason: String
)

object ClassificationEngine {

    val defaultCategories: List<CategoryEntity> = listOf(
        CategoryEntity("food", "饮食餐饮", CategoryGroup.ESSENTIAL_EXPENSE, 1),
        CategoryEntity("study", "学习资料", CategoryGroup.ESSENTIAL_EXPENSE, 2),
        CategoryEntity("transit", "交通出行", CategoryGroup.ESSENTIAL_EXPENSE, 3),
        CategoryEntity("daily", "日常必需", CategoryGroup.ESSENTIAL_EXPENSE, 4),
        CategoryEntity("entertainment", "娱乐休闲", CategoryGroup.FLEXIBLE_EXPENSE, 5),
        CategoryEntity("shopping", "数码服饰", CategoryGroup.FLEXIBLE_EXPENSE, 6),
        CategoryEntity("other_expense", "其他支出", CategoryGroup.FLEXIBLE_EXPENSE, 7),
        CategoryEntity("allowance", "家庭生活费", CategoryGroup.INCOME, 8),
        CategoryEntity("part_time", "兼职收入", CategoryGroup.INCOME, 9),
        CategoryEntity("refund", "退款冲减", CategoryGroup.NEUTRAL, 10),
        CategoryEntity("transfer", "转账不计收支", CategoryGroup.NEUTRAL, 11)
    )

    private val multiPurposeKeywords = listOf(
        "全家", "便利店", "7-11", "罗森", "美宜佳", "淘宝", "天猫", "京东", "拼多多", "超市", "百货"
    )

    fun classify(
        merchant: String,
        description: String,
        direction: TransactionDirection,
        preferences: List<MerchantPreferenceEntity>
    ): ClassificationResult {
        val m = merchant.trim()
        val d = description.trim()
        val combined = "$m $d".lowercase()

        // 1. Authorized merchant preference always wins
        val pref = preferences.firstOrNull {
            it.merchantKey.equals(m, ignoreCase = true)
        }
        if (pref != null) {
            return ClassificationResult(
                categoryId = pref.categoryId,
                source = ClassificationSource.MERCHANT_PREFERENCE,
                confidence = 1.0f,
                needsReview = false,
                reason = "已匹配您授权记住的商户「$m」分类偏好"
            )
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
                return when {
                    combined.contains("生活费") || combined.contains("家庭") || combined.contains("爸") || combined.contains("妈") ->
                        ClassificationResult("allowance", ClassificationSource.RULE_ENGINE, 0.97f, false, "识别为家庭生活费到账")
                    combined.contains("兼职") || combined.contains("助研") || combined.contains("家教") || combined.contains("津贴") || combined.contains("劳务") ->
                        ClassificationResult("part_time", ClassificationSource.RULE_ENGINE, 0.95f, false, "识别为兼职/劳务收入到账")
                    else ->
                        ClassificationResult("part_time", ClassificationSource.RULE_ENGINE, 0.62f, true, "收入来源尚不明确，请确认属于生活费还是兼职收入")
                }
            }
            TransactionDirection.EXPENSE -> Unit
        }

        // 3. Multi-purpose merchants require user confirmation unless authorized
        val isMultiPurpose = multiPurposeKeywords.any { m.contains(it, ignoreCase = true) }
        if (isMultiPurpose) {
            val guessed = when {
                combined.contains("外卖") || combined.contains("早餐") || combined.contains("午餐") -> "food"
                combined.contains("教材") || combined.contains("文具") || combined.contains("书") -> "study"
                combined.contains("耳机") || combined.contains("服饰") || combined.contains("鞋") -> "shopping"
                else -> "daily"
            }
            return ClassificationResult(
                categoryId = guessed,
                source = ClassificationSource.RULE_ENGINE,
                confidence = 0.65f,
                needsReview = true,
                reason = "「$m」属于多用途商户，系统暂未直接定性，请确认实际消费类别"
            )
        }

        // 4. Deterministic keyword rules for common campus scenarios
        return when {
            combined.contains("食堂") || combined.contains("餐厅") || combined.contains("外卖") ||
                combined.contains("咖啡") || combined.contains("奶茶") || combined.contains("餐饮") ||
                combined.contains("麦当劳") || combined.contains("肯德基") || combined.contains("饭") ||
                combined.contains("小吃") || combined.contains("水饺") || combined.contains("大饼") ||
                combined.contains("烧仙草") || combined.contains("春晖园") || combined.contains("松涛园") ||
                combined.contains("学五") || combined.contains("面") || combined.contains("东厅") ->
                ClassificationResult("food", ClassificationSource.RULE_ENGINE, 0.94f, false, "规则匹配饮食餐饮")

            combined.contains("打印") || combined.contains("教材") || combined.contains("出版") ||
                combined.contains("书店") || combined.contains("知网") || combined.contains("考试") ||
                combined.contains("文具") || combined.contains("学习") || combined.contains("讲义") ->
                ClassificationResult("study", ClassificationSource.RULE_ENGINE, 0.95f, false, "规则匹配学习资料")

            combined.contains("地铁") || combined.contains("公交") || combined.contains("羊城通") ||
                combined.contains("滴滴") || combined.contains("打车") || combined.contains("高铁") ||
                combined.contains("火车") || combined.contains("单车") || combined.contains("交通") ->
                ClassificationResult("transit", ClassificationSource.RULE_ENGINE, 0.95f, false, "规则匹配交通出行")

            combined.contains("水电") || combined.contains("话费") || combined.contains("流量") ||
                combined.contains("宿舍") || combined.contains("理发") || combined.contains("洗衣") ||
                combined.contains("日用") || combined.contains("药") ->
                ClassificationResult("daily", ClassificationSource.RULE_ENGINE, 0.90f, false, "规则匹配日常必需")

            combined.contains("影城") || combined.contains("电影") || combined.contains("steam") ||
                combined.contains("游戏") || combined.contains("ktv") || combined.contains("音乐") ||
                combined.contains("会员") || combined.contains("视频") || combined.contains("演出") ->
                ClassificationResult("entertainment", ClassificationSource.RULE_ENGINE, 0.93f, false, "规则匹配娱乐休闲")

            combined.contains("数码") || combined.contains("服装") || combined.contains("鞋") ||
                combined.contains("护肤") || combined.contains("化妆品") || combined.contains("饰品") ->
                ClassificationResult("shopping", ClassificationSource.RULE_ENGINE, 0.90f, false, "规则匹配数码服饰")

            else ->
                ClassificationResult("other_expense", ClassificationSource.RULE_ENGINE, 0.58f, true, "未命中明确分类规则，待用户确认")
        }
    }
}
