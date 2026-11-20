package com.example.data.model

enum class PaymentChannel(val displayName: String) {
    WECHAT_PAY("微信支付"),
    ALIPAY("支付宝"),
    BANK_CARD("银行卡"),
    CASH("现金"),
    OTHER("其他")
}

enum class TransactionDirection(val displayName: String) {
    EXPENSE("支出"),
    INCOME("收入"),
    REFUND("退款"),
    TRANSFER("转账")
}

enum class FundingSource(val displayName: String) {
    LIVING_ALLOWANCE("家庭生活费"),
    PART_TIME_INCOME("兼职收入"),
    UNASSIGNED("未归属")
}

enum class FundVault(val displayName: String, val shortLabel: String) {
    POCKET("零花库", "零花"),
    SAVINGS("储蓄库", "储蓄")
}

enum class TransactionSourceType(val displayName: String) {
    WECHAT_BILL_IMPORT("微信账单导入"),
    ALIPAY_BILL_IMPORT("支付宝账单导入"),
    NOTIFICATION_CAPTURE("通知捕获"),
    MANUAL_ENTRY("手动记账")
}

enum class ClassificationSource(val displayName: String) {
    MERCHANT_PREFERENCE("商户偏好"),
    RULE_ENGINE("规则引擎"),
    AI_SUGGESTED("AI 预分类"),
    USER_CONFIRMED("用户确认")
}

enum class ReviewStatus(val displayName: String) {
    PENDING("待确认"),
    USER_CONFIRMED("已确认"),
    AUTO_RULE_CLEAN("规则通过")
}

enum class DiscrepancyType(val displayName: String) {
    NONE("清晰"),
    POTENTIAL_DUPLICATE("疑似重复"),
    AMOUNT_MISMATCH("金额差异"),
    REFUND_MATCH("退款匹配"),
    UNCERTAIN_CATEGORY("分类待定")
}

enum class CandidateResolution(val displayName: String) {
    PENDING("待处理"),
    CONFIRMED_ADDED("已入账"),
    MERGED_SKIP("合并跳过"),
    DISMISSED("已忽略")
}

enum class CategoryGroup(val displayName: String) {
    ESSENTIAL_EXPENSE("必要支出"),
    FLEXIBLE_EXPENSE("弹性支出"),
    INCOME("收入"),
    NEUTRAL("不计收支")
}
