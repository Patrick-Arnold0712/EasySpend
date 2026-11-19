package com.example.data.local

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.accountDataStore: DataStore<Preferences> by preferencesDataStore(name = "easyspend_accounts")

data class LocalAccount(
    val id: String,
    val username: String,
    val nickname: String,
    val passwordSalt: String,
    val passwordHash: String,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val displayLabel: String
        get() = nickname.ifBlank { username }
}

data class AccountState(
    val isLoaded: Boolean = false,
    val currentAccount: LocalAccount? = null,
    val accounts: List<LocalAccount> = emptyList()
)

object PasswordHasher {
    private const val SALT_BYTES = 16
    private const val PBKDF2_ITERATIONS = 10_000
    private const val KEY_LENGTH_BITS = 256

    fun generateSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hashPassword(password: String, saltBase64: String): String {
        val saltBytes = runCatching { Base64.decode(saltBase64, Base64.NO_WRAP) }
            .getOrElse { saltBase64.toByteArray(Charsets.UTF_8) }
        return try {
            val spec = PBEKeySpec(password.toCharArray(), saltBytes, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            Base64.encodeToString(skf.generateSecret(spec).encoded, Base64.NO_WRAP)
        } catch (_: Exception) {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(saltBytes)
            digest.update(password.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
        }
    }

    fun verify(password: String, saltBase64: String, expectedHash: String): Boolean {
        val actual = hashPassword(password, saltBase64)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }
}

class LocalAccountStore(private val context: Context) {

    private object Keys {
        val ACCOUNTS_JSON = stringPreferencesKey("accounts_json")
        val CURRENT_ACCOUNT_ID = stringPreferencesKey("current_account_id")
    }

    val accountStateFlow: Flow<AccountState> = context.accountDataStore.data.map { prefs ->
        val accounts = decodeAccounts(prefs[Keys.ACCOUNTS_JSON])
        val currentId = prefs[Keys.CURRENT_ACCOUNT_ID]
        val current = accounts.firstOrNull { it.id == currentId }
        AccountState(
            isLoaded = true,
            currentAccount = current,
            accounts = accounts
        )
    }

    suspend fun getCurrentAccount(): LocalAccount? {
        return accountStateFlow.first().currentAccount
    }

    suspend fun register(
        rawUsername: String,
        password: String,
        rawNickname: String = ""
    ): Result<LocalAccount> {
        val username = rawUsername.trim()
        val nickname = rawNickname.trim().ifBlank { username }
        if (username.length !in 2..24) {
            return Result.failure(IllegalArgumentException("账号名称长度需为 2~24 个字符"))
        }
        if (username.any { it.isWhitespace() }) {
            return Result.failure(IllegalArgumentException("账号名称不能包含空格"))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("密码长度不能少于 6 位"))
        }

        val currentAccounts = accountStateFlow.first().accounts
        if (currentAccounts.any { it.username.equals(username, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("账号「$username」已存在，请直接登录或更换用户名"))
        }

        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(password, salt)
        val newAccount = LocalAccount(
            id = UUID.randomUUID().toString().replace("-", "").take(12),
            username = username,
            nickname = nickname,
            passwordSalt = salt,
            passwordHash = hash,
            createdAtMillis = System.currentTimeMillis()
        )

        context.accountDataStore.edit { prefs ->
            val updated = decodeAccounts(prefs[Keys.ACCOUNTS_JSON]) + newAccount
            prefs[Keys.ACCOUNTS_JSON] = encodeAccounts(updated)
            prefs[Keys.CURRENT_ACCOUNT_ID] = newAccount.id
        }
        return Result.success(newAccount)
    }

    suspend fun login(rawUsername: String, password: String): Result<LocalAccount> {
        val username = rawUsername.trim()
        if (username.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("请输入账号和密码"))
        }
        val accounts = accountStateFlow.first().accounts
        val found = accounts.firstOrNull { it.username.equals(username, ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("账号「$username」不存在，请先注册"))

        if (!PasswordHasher.verify(password, found.passwordSalt, found.passwordHash)) {
            return Result.failure(IllegalArgumentException("密码错误，请重新输入"))
        }

        context.accountDataStore.edit { prefs ->
            prefs[Keys.CURRENT_ACCOUNT_ID] = found.id
        }
        return Result.success(found)
    }

    suspend fun logout() {
        context.accountDataStore.edit { prefs ->
            prefs.remove(Keys.CURRENT_ACCOUNT_ID)
        }
    }

    suspend fun updateNickname(accountId: String, rawNickname: String): Result<LocalAccount> {
        val nickname = rawNickname.trim()
        if (nickname.isBlank() || nickname.length > 24) {
            return Result.failure(IllegalArgumentException("昵称需为 1~24 个字符"))
        }
        var updatedAccount: LocalAccount? = null
        context.accountDataStore.edit { prefs ->
            val list = decodeAccounts(prefs[Keys.ACCOUNTS_JSON]).map { acc ->
                if (acc.id == accountId) {
                    acc.copy(nickname = nickname).also { updatedAccount = it }
                } else acc
            }
            prefs[Keys.ACCOUNTS_JSON] = encodeAccounts(list)
        }
        return updatedAccount?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("未找到当前账号"))
    }

    suspend fun changePassword(
        accountId: String,
        oldPassword: String,
        newPassword: String
    ): Result<Unit> {
        if (newPassword.length < 6) {
            return Result.failure(IllegalArgumentException("新密码长度不能少于 6 位"))
        }
        val accounts = accountStateFlow.first().accounts
        val found = accounts.firstOrNull { it.id == accountId }
            ?: return Result.failure(IllegalStateException("未找到当前账号"))
        if (!PasswordHasher.verify(oldPassword, found.passwordSalt, found.passwordHash)) {
            return Result.failure(IllegalArgumentException("原密码不正确"))
        }
        val newSalt = PasswordHasher.generateSalt()
        val newHash = PasswordHasher.hashPassword(newPassword, newSalt)
        context.accountDataStore.edit { prefs ->
            val list = decodeAccounts(prefs[Keys.ACCOUNTS_JSON]).map { acc ->
                if (acc.id == accountId) {
                    acc.copy(passwordSalt = newSalt, passwordHash = newHash)
                } else acc
            }
            prefs[Keys.ACCOUNTS_JSON] = encodeAccounts(list)
        }
        return Result.success(Unit)
    }

    private fun encodeAccounts(accounts: List<LocalAccount>): String {
        val arr = JSONArray()
        accounts.forEach { acc ->
            arr.put(
                JSONObject()
                    .put("id", acc.id)
                    .put("username", acc.username)
                    .put("nickname", acc.nickname)
                    .put("passwordSalt", acc.passwordSalt)
                    .put("passwordHash", acc.passwordHash)
                    .put("createdAtMillis", acc.createdAtMillis)
            )
        }
        return arr.toString()
    }

    private fun decodeAccounts(raw: String?): List<LocalAccount> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val id = obj.optString("id")
                val username = obj.optString("username")
                if (id.isBlank() || username.isBlank()) return@mapNotNull null
                LocalAccount(
                    id = id,
                    username = username,
                    nickname = obj.optString("nickname", username),
                    passwordSalt = obj.optString("passwordSalt"),
                    passwordHash = obj.optString("passwordHash"),
                    createdAtMillis = obj.optLong("createdAtMillis", 0L)
                )
            }
        }.getOrDefault(emptyList())
    }
}
