package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.EasySpendDatabase
import com.example.data.local.ThemeMode
import com.example.data.local.UserSettingsDataStore
import com.example.data.model.CandidateResolution
import com.example.data.model.DiscrepancyType
import com.example.data.model.PaymentChannel
import com.example.data.repository.EasySpendRepository
import com.example.domain.finance.DemoStatementFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

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
    fun `read app_name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("易支道", appName)
    }

    @Test
    fun `import staging detects duplicates and only commits after user confirmation`() = runTest {
        repository.ensureInitialized("2026-11")

        // Stage WeChat demo CSV -> should NOT write directly to transactions
        val wxBatch = repository.importCsvIntoReviewQueue(
            DemoStatementFactory.demoWeChatCsv,
            "微信演示账单",
            PaymentChannel.WECHAT_PAY
        ).getOrThrow()

        assertTrue(wxBatch.totalCount > 0)
        assertEquals(0, repository.getTransactionsSnapshot("2026-11").size)

        // Confirm clean items -> transactions populate
        repository.confirmAllCleanCandidates()
        val committedAfterWx = repository.getTransactionsSnapshot("2026-11").size
        assertTrue(committedAfterWx > 0)

        // Stage Alipay demo CSV (contains intentional Steam 98元 duplicate + 38元 refund)
        repository.importCsvIntoReviewQueue(
            DemoStatementFactory.demoAlipayCsv,
            "支付宝演示账单",
            PaymentChannel.ALIPAY
        ).getOrThrow()

        val pending = repository.pendingCandidatesFlow.first()
        assertTrue(pending.any { it.discrepancyType == DiscrepancyType.POTENTIAL_DUPLICATE })
        assertTrue(pending.any { it.discrepancyType == DiscrepancyType.REFUND_MATCH })

        // Skip duplicate -> committed count should NOT increase for duplicate
        val dup = pending.first { it.discrepancyType == DiscrepancyType.POTENTIAL_DUPLICATE }
        repository.resolveCandidateAsSkipOrDismiss(dup.id, CandidateResolution.MERGED_SKIP)
        assertEquals(committedAfterWx, repository.getTransactionsSnapshot("2026-11").size)
    }

    @Test
    fun `theme mode persists light dark and system settings`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = UserSettingsDataStore(context)
        assertEquals(ThemeMode.SYSTEM, store.settingsFlow.first().themeMode)

        store.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, store.settingsFlow.first().themeMode)

        store.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, store.settingsFlow.first().themeMode)
    }
}
