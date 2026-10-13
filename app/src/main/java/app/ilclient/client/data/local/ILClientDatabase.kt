package app.ilclient.client.data.local

import androidx.room.*
import app.ilclient.client.domain.model.ProxyConfigEntity
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val encryptedUrl: String,
    val enabled: Boolean = true,
    val lastUpdatedAt: Long? = null,
    val importedCount: Int = 0,
    val lastError: String? = null
)

@Entity(tableName = "custom_rules")
data class CustomRuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val matchType: RuleMatchType,
    val value: String,
    val outboundAction: RuleAction,
    val priorityOrder: Int,
    val enabled: Boolean = true
)

enum class RuleMatchType { DOMAIN, IP_CIDR, PACKAGE_NAME }
enum class RuleAction(val outboundTag: String) {
    PROXY("proxy"),
    DIRECT("direct"),
    BLOCK("block")
}

@Entity(tableName = "diagnostic_events")
data class DiagnosticEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String,
    val level: String,
    val message: String
)

@Dao
interface ILClientDao {
    @Query("SELECT * FROM proxy_configs ORDER BY isFavorite DESC, manualOrder ASC, name ASC")
    fun observeConfigs(): Flow<List<ProxyConfigEntity>>

    @Query("SELECT * FROM proxy_configs")
    suspend fun getAllConfigs(): List<ProxyConfigEntity>

    @Query("SELECT * FROM proxy_configs WHERE id = :id LIMIT 1")
    suspend fun getConfigById(id: String): ProxyConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: ProxyConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfigs(configs: List<ProxyConfigEntity>)

    @Query("DELETE FROM proxy_configs WHERE id = :id")
    suspend fun deleteConfigById(id: String)

    @Query("SELECT * FROM subscriptions ORDER BY name ASC")
    fun observeSubscriptions(): Flow<List<SubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubscription(sub: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteSubscription(id: String)

    @Query("SELECT * FROM custom_rules ORDER BY priorityOrder ASC")
    fun observeCustomRules(): Flow<List<CustomRuleEntity>>

    @Query("SELECT * FROM custom_rules WHERE enabled = 1 ORDER BY priorityOrder ASC")
    suspend fun getEnabledCustomRules(): List<CustomRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomRule(rule: CustomRuleEntity)

    @Query("DELETE FROM custom_rules WHERE id = :id")
    suspend fun deleteCustomRule(id: String)

    @Query("SELECT * FROM diagnostic_events ORDER BY timestamp DESC LIMIT 250")
    fun observeDiagnosticEvents(): Flow<List<DiagnosticEventEntity>>

    @Insert
    suspend fun insertDiagnosticEvent(event: DiagnosticEventEntity)

    @Query("DELETE FROM diagnostic_events")
    suspend fun clearDiagnostics()
}

@Database(
    entities = [
        ProxyConfigEntity::class,
        SubscriptionEntity::class,
        CustomRuleEntity::class,
        DiagnosticEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ILClientDatabase : RoomDatabase() {
    abstract fun dao(): ILClientDao
}