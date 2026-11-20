package com.example

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.EasySpendAiService
import com.example.data.local.EasySpendDatabase
import com.example.data.local.UserSettings
import com.example.data.local.UserSettingsDataStore
import com.example.data.model.PaymentChannel
import com.example.data.model.TransactionDirection
import com.example.data.repository.EasySpendRepository
import com.example.domain.finance.StatementImporter
import com.example.domain.finance.XlsxStatementParser
import com.example.ui.EasySpendUiState
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.EasySpendTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeChatXlsxAndUXPolishTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: EasySpendDatabase
    private lateinit var repository: EasySpendRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, EasySpendDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = EasySpendRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `WeChat official xlsx export parses expense income and neutral rows correctly and stages into review queue`() = runBlocking {
        val xlsxBytes = buildSampleWeChatXlsx()
        assertTrue(XlsxStatementParser.isXlsxBytes(xlsxBytes))

        val parsed = StatementImporter.parseXlsxStatement(xlsxBytes, PaymentChannel.WECHAT_PAY)
        assertEquals(4, parsed.size)

        // Row 1: 沙县小吃支出
        assertEquals("福建沙县小吃广州下渡西街店", parsed[0].merchant)
        assertEquals(1200L, parsed[0].amountFen)
        assertEquals(TransactionDirection.EXPENSE, parsed[0].direction)
        assertEquals(PaymentChannel.WECHAT_PAY, parsed[0].channel)

        // Row 2: 中山大学校园餐饮 (含 inlineStr 与数字日期串测试)
        assertEquals("中山大学", parsed[1].merchant)
        assertEquals(500L, parsed[1].amountFen)
        assertEquals(TransactionDirection.EXPENSE, parsed[1].direction)

        // Row 3: 微信转账收入 (收/支 = 收入, 支付方式 = /)
        assertEquals("峻峰妈妈（evening bag@Alice）", parsed[2].merchant)
        assertEquals(24000L, parsed[2].amountFen)
        assertEquals(TransactionDirection.INCOME, parsed[2].direction)

        // Row 4: 中性交易 零钱通存入 (收/支 = /) -> TRANSFER (不计入支出)
        assertEquals("零钱通", parsed[3].merchant)
        assertEquals(50000L, parsed[3].amountFen)
        assertEquals(TransactionDirection.TRANSFER, parsed[3].direction)

        // Stage through repository into review queue
        val batch = repository.importXlsxIntoReviewQueue(
            xlsxBytes = xlsxBytes,
            sourceLabel = "微信支付Excel导入",
            preferredChannel = PaymentChannel.WECHAT_PAY
        ).getOrThrow()

        assertEquals(4, batch.totalCount)
        val pending = repository.pendingCandidatesFlow.first()
        assertEquals(4, pending.size)
        assertEquals("food", pending[0].suggestedCategoryId)
        assertEquals("food", pending[1].suggestedCategoryId)
        assertEquals("allowance", pending[2].suggestedCategoryId)
    }

    @Test
    fun `formatConciseReply strips markdown headings bold bullets dividers and code fences`() {
        val messy = """
            ### 结论
            **本月储蓄目标可以达成**，预计月底结余 `¥1,250.00`。
            ---
            - **已到账收入**：¥2,300.00
            * **饮食支出**：¥860.00
            ```text
            1. 控制奶茶等弹性消费在 ¥200 以内
            ```
            > 点击下方卡片即可确认调整。
        """.trimIndent()

        val cleaned = EasySpendAiService.formatConciseReply(messy)
        assertFalse(cleaned.contains("**"))
        assertFalse(cleaned.contains("###"))
        assertFalse(cleaned.contains("---"))
        assertFalse(cleaned.contains("```"))
        assertFalse(cleaned.contains("`"))
        assertTrue(cleaned.contains("结论"))
        assertTrue(cleaned.contains("• 已到账收入：¥2,300.00"))
        assertTrue(cleaned.contains("• 饮食支出：¥860.00"))
        assertTrue(cleaned.contains("点击下方卡片即可确认调整。"))
    }

    @Test
    fun `UserSettingsDataStore persists onboardingCompleted and hideBeginnerGuide`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = UserSettingsDataStore(context, accountId = "test_onboarding_${System.nanoTime()}")

        assertFalse(store.settingsFlow.first().hideBeginnerGuide)
        assertFalse(store.settingsFlow.first().onboardingCompleted)
        store.setHideBeginnerGuide(true)
        store.setOnboardingCompleted(true)
        assertTrue(store.settingsFlow.first().hideBeginnerGuide)
        assertTrue(store.settingsFlow.first().onboardingCompleted)
    }

    @Test
    fun `DashboardScreen stays uncluttered without inline beginner guide card`() {
        composeTestRule.setContent {
            EasySpendTheme(darkTheme = false) {
                DashboardScreen(
                    state = EasySpendUiState(settings = UserSettings(hideBeginnerGuide = false)),
                    onLoadDemoQueue = {},
                    onLoadDemoBaseline = {},
                    onAdd300Essential = {},
                    onNavigateToImportQueue = {},
                    onNavigateToBudget = {},
                    onNavigateToAgentWithPrompt = {}
                )
            }
        }

        composeTestRule.onAllNodesWithTag("beginner_guide_card").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("dismiss_beginner_guide_button").assertCountEquals(0)
    }

    private fun buildSampleWeChatXlsx(): ByteArray {
        val rows = listOf(
            listOf("微信支付账单明细"),
            listOf("微信昵称：[P.A.]"),
            listOf("起始时间：[2026-10-17 00:00:00] 终止时间：[2026-11-17 22:04:22]"),
            listOf("导出类型：[全部账单]"),
            listOf("导出时间：[2026-11-17 22:04:22]"),
            emptyList(),
            listOf("共4笔记录"),
            listOf("收入：1笔 240.00元"),
            listOf("支出：2笔 17.00元"),
            listOf("中性交易：1笔 500.00元"),
            listOf("注："),
            listOf("1. 充值/提现/理财通购买/零钱通存取/信用卡还款等交易，将计入中性交易"),
            listOf("2. 若交易记录明细无有效内容，则代表该时间段内此微信号无交易"),
            listOf("3. 本明细仅供个人对账使用"),
            listOf("4. 本账单中所有时间均为UTC+08:00时间"),
            listOf("----------微信支付账单明细列表----------"),
            listOf("交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)", "支付方式", "当前状态", "交易单号"),
            listOf("2026-11-17 17:14:09", "商户消费", "福建沙县小吃广州下渡西街店", "沙县小吃中大新店", "支出", "¥12.00", "零钱", "支付成功", "4500000557202611176560122301"),
            listOf("2026-11-17 07:57:41", "商户消费", "中山大学", "南春晖园东厅一楼北方大饼18", "支出", "¥5.00", "零钱", "支付成功", "4200003153202611177314619015"),
            listOf("2026-11-15 08:25:56", "转账", "峻峰妈妈（evening bag@Alice）", "转账备注:微信转账", "收入", "¥240.00", "/", "已存入零钱", "10000500012026111407324604665"),
            listOf("2026-11-14 09:00:00", "零钱通", "零钱通", "零钱通存入", "/", "¥500.00", "零钱", "支付成功", "4500000999202611140000000001")
        )

        val sharedStrings = mutableListOf<String>()
        val stringIndex = mutableMapOf<String, Int>()
        fun intern(value: String): Int {
            return stringIndex.getOrPut(value) {
                sharedStrings.add(value)
                sharedStrings.lastIndex
            }
        }

        val sheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            rows.forEachIndexed { rIdx, cols ->
                val rowNum = rIdx + 1
                append("""<row r="$rowNum">""")
                cols.forEachIndexed { cIdx, cellText ->
                    val colLetter = ('A' + cIdx).toString()
                    val ref = "$colLetter$rowNum"
                    if (rIdx == rows.lastIndex - 2 && cIdx == 3) {
                        // Also exercise inlineStr cell format
                        append("""<c r="$ref" t="inlineStr"><is><t>${escapeXml(cellText)}</t></is></c>""")
                    } else {
                        val idx = intern(cellText)
                        append("""<c r="$ref" t="s"><v>$idx</v></c>""")
                    }
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        val sstXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${sharedStrings.size}" uniqueCount="${sharedStrings.size}">""")
            sharedStrings.forEach { text ->
                append("<si><t>${escapeXml(text)}</t></si>")
            }
            append("</sst>")
        }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write("""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"></Types>""".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zos.write(sstXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zos.write(sheetXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
