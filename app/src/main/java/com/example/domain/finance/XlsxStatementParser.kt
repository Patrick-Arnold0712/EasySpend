package com.example.domain.finance

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream
import kotlin.math.roundToLong

object XlsxStatementParser {

    fun isXlsxBytes(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()
    }

    fun parseRows(xlsxBytes: ByteArray): List<List<String>> {
        if (!isXlsxBytes(xlsxBytes)) return emptyList()

        var sharedStringsBytes: ByteArray? = null
        val worksheetEntries = mutableListOf<Pair<String, ByteArray>>()

        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase(Locale.ROOT)
                if (!entry.isDirectory) {
                    when {
                        name == "xl/sharedstrings.xml" -> {
                            sharedStringsBytes = zis.readBytes()
                        }
                        name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") -> {
                            worksheetEntries.add(name to zis.readBytes())
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sheetBytes = worksheetEntries
            .sortedBy { it.first }
            .firstOrNull { it.first == "xl/worksheets/sheet1.xml" }
            ?.second
            ?: worksheetEntries.minByOrNull { it.first }?.second
            ?: return emptyList()

        val sharedStrings = sharedStringsBytes?.let { parseSharedStrings(it) }.orEmpty()
        return parseWorksheet(sheetBytes, sharedStrings)
    }

    private fun newXmlParser(bytes: ByteArray): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        return factory.newPullParser().apply {
            setInput(InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8))
        }
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val parser = newXmlParser(bytes)
        var insideSi = false
        var insideT = false
        val currentSi = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = localTagName(parser.name)
                    when (tag) {
                        "si" -> {
                            insideSi = true
                            currentSi.clear()
                        }
                        "t" -> if (insideSi) insideT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideSi && insideT) {
                        currentSi.append(parser.text.orEmpty())
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = localTagName(parser.name)
                    when (tag) {
                        "t" -> insideT = false
                        "si" -> {
                            if (insideSi) {
                                strings.add(currentSi.toString())
                                insideSi = false
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return strings
    }

    private fun parseWorksheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = newXmlParser(bytes)

        var currentRow: MutableList<String>? = null
        var insideCell = false
        var cellType = ""
        var cellColIndex = -1
        var nextSequentialCol = 0
        var insideValue = false
        var insideInlineText = false
        val valueBuilder = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = localTagName(parser.name)
                    when (tag) {
                        "row" -> {
                            currentRow = mutableListOf()
                            nextSequentialCol = 0
                        }
                        "c" -> {
                            if (currentRow != null) {
                                insideCell = true
                                cellType = parser.getAttributeValue(null, "t").orEmpty()
                                val ref = parser.getAttributeValue(null, "r").orEmpty()
                                cellColIndex = parseColumnIndex(ref) ?: nextSequentialCol
                                nextSequentialCol = cellColIndex + 1
                                valueBuilder.clear()
                            }
                        }
                        "v" -> if (insideCell) insideValue = true
                        "t" -> if (insideCell && (cellType == "inlineStr" || cellType == "str")) insideInlineText = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideCell && (insideValue || insideInlineText)) {
                        valueBuilder.append(parser.text.orEmpty())
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = localTagName(parser.name)
                    when (tag) {
                        "v" -> insideValue = false
                        "t" -> insideInlineText = false
                        "c" -> {
                            if (insideCell && currentRow != null) {
                                val raw = valueBuilder.toString()
                                val resolved = when (cellType) {
                                    "s" -> {
                                        val idx = raw.trim().toIntOrNull()
                                        if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                                    }
                                    "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                                    else -> raw
                                }.trim()

                                while (currentRow.size <= cellColIndex) {
                                    currentRow.add("")
                                }
                                if (cellColIndex >= 0) {
                                    currentRow[cellColIndex] = resolved
                                }
                                insideCell = false
                                insideValue = false
                                insideInlineText = false
                            }
                        }
                        "row" -> {
                            currentRow?.let { row ->
                                if (row.any { it.isNotBlank() }) {
                                    rows.add(row)
                                }
                            }
                            currentRow = null
                        }
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun localTagName(rawName: String?): String {
        val name = rawName.orEmpty()
        val colonIdx = name.indexOf(':')
        return if (colonIdx >= 0) name.substring(colonIdx + 1) else name
    }

    private fun parseColumnIndex(cellRef: String): Int? {
        if (cellRef.isBlank()) return null
        var col = 0
        var hasLetters = false
        for (ch in cellRef) {
            if (ch in 'A'..'Z' || ch in 'a'..'z') {
                hasLetters = true
                col = col * 26 + (ch.uppercaseChar() - 'A' + 1)
            } else {
                break
            }
        }
        return if (hasLetters && col > 0) col - 1 else null
    }

    fun excelSerialToMillis(serial: Double): Long? {
        if (serial < 30000.0 || serial > 80000.0) return null
        // Excel serial epoch with 1900 leap-year bug offset: 1899-12-30 00:00:00 in Asia/Shanghai (+08:00)
        val epochFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
        val baseMillis = epochFmt.parse("1899-12-30 00:00:00")?.time ?: return null
        val offsetMillis = (serial * 86_400_000.0).roundToLong()
        return baseMillis + offsetMillis
    }

    fun formatMillisForDebug(millis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
        return fmt.format(Date(millis))
    }
}
