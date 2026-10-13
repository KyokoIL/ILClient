package app.ilclient.client.core.subscription

import app.ilclient.client.core.parser.ConfigParser
import app.ilclient.client.core.parser.ParseResult
import app.ilclient.client.core.security.SecretVault
import app.ilclient.client.data.local.DiagnosticEventEntity
import app.ilclient.client.data.local.ILClientDao
import app.ilclient.client.data.local.SubscriptionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionManager @Inject constructor(
    private val dao: ILClientDao,
    private val parser: ConfigParser,
    private val secretVault: SecretVault
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun refreshSubscription(sub: SubscriptionEntity): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val url = secretVault.decrypt(sub.encryptedUrl)
            val body = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", "ILClient/1.0 (Android; Xray-core)")
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                response.body?.string() ?: throw IllegalStateException("Empty body")
            }

            val validConfigs = parser.parseBatch(body, subscriptionId = sub.id).filterIsInstance<ParseResult.Success>()
            val existingForSub = dao.getAllConfigs().filter { it.subscriptionId == sub.id }
            val manuallyEdited = existingForSub.filter { it.isManuallyEdited }
            existingForSub.filterNot { it.isManuallyEdited }.forEach { dao.deleteConfigById(it.id) }

            val deduplicatedNew = validConfigs.distinctBy { it.dedupKey }.mapIndexed { idx, res ->
                res.config.copy(groupName = sub.name, manualOrder = idx)
            }
            dao.upsertConfigs(deduplicatedNew)
            dao.upsertSubscription(
                sub.copy(
                    lastUpdatedAt = System.currentTimeMillis(),
                    importedCount = deduplicatedNew.size + manuallyEdited.size,
                    lastError = null
                )
            )
            deduplicatedNew.size
        }.onFailure { err ->
            val safeError = secretVault.redactLog(err.message ?: "Subscription error")
            dao.upsertSubscription(sub.copy(lastError = safeError))
            dao.insertDiagnosticEvent(DiagnosticEventEntity(category = "SUBSCRIPTION", level = "ERROR", message = safeError))
        }
    }
}