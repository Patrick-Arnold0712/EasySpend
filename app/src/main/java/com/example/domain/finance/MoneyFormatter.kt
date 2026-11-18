package com.example.domain.finance

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MoneyFormatter {

    private val numberFormat = DecimalFormat("#,##0.00")
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.CHINA)
    private val timestampFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

    fun parseYuanToFen(raw: String): Long? {
        val cleaned = raw
            .replace("¥", "")
            .replace("￥", "")
            .replace("元", "")
            .replace("人民币", "")
            .replace(",", "")
            .trim()
        if (cleaned.isBlank()) return null
        return try {
            val decimal = BigDecimal(cleaned)
            decimal.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    fun formatFen(fen: Long): String {
        val absFen = kotlin.math.abs(fen)
        val yuan = absFen / 100.0
        val formatted = numberFormat.format(yuan)
        return if (fen < 0) "-¥$formatted" else "¥$formatted"
    }

    fun fenToYuanString(fen: Long): String {
        val bd = BigDecimal(fen).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        return bd.toPlainString()
    }

    fun monthKeyFromMillis(millis: Long): String {
        return synchronized(monthFormat) {
            monthFormat.format(Date(millis))
        }
    }

    fun formatTimestamp(millis: Long): String {
        return synchronized(timestampFormat) {
            timestampFormat.format(Date(millis))
        }
    }
}
