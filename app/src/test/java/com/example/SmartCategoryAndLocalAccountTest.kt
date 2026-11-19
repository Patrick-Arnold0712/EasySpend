package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ApiProviderType
import com.example.data.local.EasySpendDatabase
import com.example.data.local.LocalAccountStore
import com.example.data.local.UserSettingsDataStore
import com.example.data.model.CategoryGroup
import com.example.data.model.FundingSource
import com.example.data.model.PaymentChannel
import com.example.data.model.TransactionDirection
import com.example.data.repository.EasySpendRepository
import com.example.domain.finance.ClassificationEngine
import com.example.domain.finance.StatementImporter
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CategoryManagementDialog
import com.example.ui.theme.EasySpendTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartCategoryAndLocalAccountTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: EasySpendDatabase
    private lateinit var repository: EasySpendRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, EasySpendDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = EasySpendRepository(db)
    }

    @After
    fun teardown() {
        db.close()
        EasySpendDatabase.closeAllForTests()
    }

    @Test
    fun `amount-aware smart classification distinguishes Meituan bike vs delivery vs group dining vs hotel`() {
        val bike = ClassificationEngine.classify(
            merchant = "美团",
            description = "美团订单",
            direction = TransactionDirection.EXPENSE,
            preferences = emptyList(),
            amountFen = 180L // ¥1.80
        )
        assertEquals("transit", bike.categoryId)
        assertFalse(bike.needsReview)
        assertTrue(bike.reason.contains("单车"))

        val delivery = ClassificationEngine.classify(
            merchant = "美团",
            description = "美团订单",
            direction = TransactionDirection.EXPENSE,
            preferences = emptyList(),
            amountFen = 2250L // ¥22.50
        )
        assertEquals("food", delivery.categoryId)
        assertFalse(delivery.needsReview)
        assertTrue(delivery.reason.contains("外卖"))

        val groupDinner = ClassificationEngine.classify(
            merchant = "美团",
            description = "美团订单",
            direction = TransactionDirection.EXPENSE,
            preferences = emptyList(),
            amountFen = 18800L // ¥188.00
        )
        assertEquals("social", groupDinner.categoryId)
        assertTrue(groupDinner.needsReview)

        val hotel = ClassificationEngine.classify(
            merchant = "美团",
            description = "美团订单",
            direction = TransactionDirection.EXPENSE,
            preferences = emptyList(),
            amountFen = 32800L // ¥328.00
        )
        assertEquals("travel", hotel.categoryId)
        assertTrue(hotel.needsReview)

        // Campus generic order number: ¥12 -> food, ¥100 -> housing
        val campusMeal = ClassificationEngine.classify(
            merchant = "中山大学",
            description = "商户订单号: 15722744079",
            direction = TransactionDirection.EXPENSE,
            preferences = emptyList(),
            amountFen = 1200L
        )
        assertEquals("food", campusMeal.categoryId)

        val campusUtility = ClassificationEngine.classify(
            merchant = "中山大学",
            description = "商户订单号: 15722744080",
            direction = TransactionDirection.EXPENSE,
            preferences = emptyList(),
            amountFen = 10000L
        )
        assertEquals("housing", campusUtility.categoryId)

        // Convenience store amount hint while preserving confirmation flag
        val smallStore = ClassificationEngine.classify(
            merchant = "全家便利店",
            description = "消费",
            direction = TransactionDirection.EXPENSE,
            preferences = emptyList(),
            amountFen = 1200L
        )
        assertEquals("food", smallStore.categoryId)
        assertTrue(smallStore.needsReview)

        val largeStore = ClassificationEngine.classify(
            merchant = "全家便利店",
            description = "消费",
            direction = TransactionDirection.EXPENSE,
            preferences = emptyList(),
            amountFen = 6800L
        )
        assertEquals("daily", largeStore.categoryId)
        assertTrue(largeStore.needsReview)
    }

    @Test
    fun `expanded default categories and custom category CRUD with keyword auto-classification work end-to-end`() = runBlocking {
        val monthKey = "2026-11"
        repository.ensureInitialized(monthKey)

        val initialCats = repository.getCategoriesSnapshot()
        assertTrue("Expected >= 20 built-in categories", initialCats.size >= 20)
        assertTrue(initialCats.any { it.id == "housing" })
        assertTrue(initialCats.any { it.id == "medical" })
        assertTrue(initialCats.any { it.id == "beverage" })
        assertTrue(initialCats.any { it.id == "scholarship" })

        // Create custom category with auto-match keywords
        val petCat = repository.createCustomCategory(
            name = "宠物开销",
            group = CategoryGroup.FLEXIBLE_EXPENSE,
            keywords = "猫粮,猫砂,宠物医院",
            monthlyLimitFen = 30000L,
            monthKey = monthKey
        ).getOrThrow()
        assertTrue(petCat.isCustom)

        // Import statement containing Meituan ¥1.80, Meituan ¥22.50, and a custom keyword row
        val csv = """
            微信支付账单明细
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号
            2026-11-10 08:15:00,商户消费,美团,美团订单,支出,¥1.80,零钱,支付成功,MT_BIKE_001
            2026-11-10 12:15:00,商户消费,美团,美团订单,支出,¥22.50,零钱,支付成功,MT_FOOD_002
            2026-11-11 16:00:00,商户消费,萌宠生活馆,进口猫粮2kg,支出,¥89.00,零钱,支付成功,PET_003
        """.trimIndent()

        repository.importCsvIntoReviewQueue(csv, "智能分类测试", PaymentChannel.WECHAT_PAY).getOrThrow()
        val candidates = repository.pendingCandidatesFlow.first()
        assertEquals(3, candidates.size)

        val bikeCand = candidates.first { it.externalRefId == "MT_BIKE_001" }
        val foodCand = candidates.first { it.externalRefId == "MT_FOOD_002" }
        val petCand = candidates.first { it.externalRefId == "PET_003" }
        assertEquals("transit", bikeCand.suggestedCategoryId)
        assertEquals("food", foodCand.suggestedCategoryId)
        assertEquals(petCat.id, petCand.suggestedCategoryId)

        // Confirm pet transaction then delete custom category -> safely reassigned to other_expense
        repository.confirmCandidateToLedger(petCand.id, petCat.id, FundingSource.LIVING_ALLOWANCE, false, "")
        assertTrue(repository.deleteCustomCategory("food").isFailure) // Built-in protected
        assertTrue(repository.deleteCustomCategory(petCat.id).isSuccess)

        val txsAfterDelete = repository.getTransactionsSnapshot(monthKey)
        assertEquals(1, txsAfterDelete.size)
        assertEquals("other_expense", txsAfterDelete.first().categoryId)
    }

    @Test
    fun `local accounts hash passwords and keep Room databases and settings strictly isolated per account`() = runBlocking {
        val accountStore = LocalAccountStore(context)

        val alice = accountStore.register("alice_dev", "pass1234", "爱丽丝").getOrThrow()
        assertNotEquals("pass1234", alice.passwordHash)
        assertTrue(alice.passwordSalt.isNotBlank())

        // Duplicate username rejected
        assertTrue(accountStore.register("ALICE_DEV", "another99").isFailure)

        // Wrong password rejected
        assertTrue(accountStore.login("alice_dev", "wrongpwd").isFailure)

        // Change password works
        assertTrue(accountStore.changePassword(alice.id, "pass1234", "newpass567").isSuccess)
        assertTrue(accountStore.login("alice_dev", "pass1234").isFailure)
        assertTrue(accountStore.login("alice_dev", "newpass567").isSuccess)

        // Register second account Bob
        val bob = accountStore.register("bob_student", "bobpass88", "鲍勃").getOrThrow()

        // Verify isolated Room DB files
        val aliceDb = EasySpendDatabase.getInstanceForAccount(context, alice.id)
        val bobDb = EasySpendDatabase.getInstanceForAccount(context, bob.id)
        val aliceRepo = EasySpendRepository(aliceDb)
        val bobRepo = EasySpendRepository(bobDb)

        aliceRepo.ensureInitialized("2026-11")
        bobRepo.ensureInitialized("2026-11")

        aliceRepo.createCustomCategory("Alice摄影", CategoryGroup.FLEXIBLE_EXPENSE, "相机", 50000L, "2026-11").getOrThrow()
        aliceRepo.addManualTransaction(
            amountFen = 6600L,
            direction = TransactionDirection.EXPENSE,
            channel = PaymentChannel.WECHAT_PAY,
            fundingSource = FundingSource.LIVING_ALLOWANCE,
            categoryId = "food",
            merchant = "Alice专属午餐",
            note = "isolated"
        )

        val aliceTxs = aliceRepo.getTransactionsSnapshot("2026-11")
        val bobTxs = bobRepo.getTransactionsSnapshot("2026-11")
        assertEquals(1, aliceTxs.size)
        assertEquals(0, bobTxs.size)

        val aliceCats = aliceRepo.getCategoriesSnapshot()
        val bobCats = bobRepo.getCategoriesSnapshot()
        assertTrue(aliceCats.any { it.name == "Alice摄影" })
        assertFalse(bobCats.any { it.name == "Alice摄影" })

        // Verify isolated UserSettingsDataStore
        val aliceSettings = UserSettingsDataStore(context, alice.id)
        val bobSettings = UserSettingsDataStore(context, bob.id)
        aliceSettings.saveApiConfig(ApiProviderType.DEEPSEEK, "https://api.deepseek.com/v1", "sk-alice-only", "deepseek-chat")
        aliceSettings.setHideBeginnerGuide(true)

        assertEquals("sk-alice-only", aliceSettings.settingsFlow.first().apiKey)
        assertTrue(aliceSettings.settingsFlow.first().hideBeginnerGuide)
        assertEquals("", bobSettings.settingsFlow.first().apiKey)
        assertFalse(bobSettings.settingsFlow.first().hideBeginnerGuide)

        // Logout clears active session
        accountStore.logout()
        assertEquals(null, accountStore.getCurrentAccount())
    }

    @Test
    fun `AuthScreen validates registration input and submits valid local account`() {
        var registeredUser: String? = null
        var registeredPwd: String? = null

        composeTestRule.setContent {
            EasySpendTheme(darkTheme = false) {
                AuthScreen(
                    existingAccounts = emptyList(),
                    authErrorMessage = null,
                    onClearError = {},
                    onLogin = { _, _ -> },
                    onRegister = { u, p, _ ->
                        registeredUser = u
                        registeredPwd = p
                    }
                )
            }
        }

        // Default mode is register when no existing accounts
        composeTestRule.onNodeWithTag("auth_username_input").performScrollTo().performTextInput("student01")
        composeTestRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("123456")
        composeTestRule.onNodeWithTag("auth_confirm_password_input").performScrollTo().performTextInput("654321")
        composeTestRule.onNodeWithTag("auth_submit_button").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("auth_error_banner").assertIsDisplayed()
        assertEquals(null, registeredUser)
    }

    @Test
    fun `CategoryManagementDialog allows creating a custom category`() {
        var createdName: String? = null
        var createdKeywords: String? = null

        composeTestRule.setContent {
            EasySpendTheme(darkTheme = false) {
                CategoryManagementDialog(
                    categories = ClassificationEngine.defaultCategories,
                    onCreateCategory = { name, _, keywords, _ ->
                        createdName = name
                        createdKeywords = keywords
                    },
                    onUpdateCategory = { _, _, _, _ -> },
                    onDeleteCategory = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("open_create_category_button").performClick()
        composeTestRule.onNodeWithTag("custom_category_name_input").performTextInput("宠物开销")
        composeTestRule.onNodeWithTag("custom_category_keywords_input").performTextInput("猫粮,宠物")
        composeTestRule.onNodeWithTag("save_custom_category_button").performClick()

        assertEquals("宠物开销", createdName)
        assertEquals("猫粮,宠物", createdKeywords)
        assertNotNull(createdName)
    }
}
