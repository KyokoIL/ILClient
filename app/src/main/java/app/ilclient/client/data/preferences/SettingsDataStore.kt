package app.ilclient.client.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import app.ilclient.client.ui.theme.AppThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "ilclient_settings")

enum class RoutingMode(val title: String, val plainExplanation: String) {
    GLOBAL_PROXY("Global Proxy", "Routes all traffic through the selected Xray server (unless overridden by Bypass Iran or Custom Rules)."),
    BYPASS_LAN("Bypass LAN", "Routes private local network addresses (192.168.x.x, 10.x.x.x) directly while proxying internet traffic."),
    DIRECT_ALL("Direct", "Routes all traffic directly without proxying."),
    CUSTOM_RULES("Custom Rules", "Applies your prioritized custom domain, IP/CIDR, and app rules before defaulting to proxy.")
}

enum class SplitTunnelMode {
    ALL_APPS,
    EXCLUDE_SELECTED,
    ONLY_SELECTED
}

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val activeConfigId: String? = null,
    val bypassIranEnabled: Boolean = true,
    val routingMode: RoutingMode = RoutingMode.BYPASS_LAN,
    val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.EXCLUDE_SELECTED,
    val splitTunnelPackages: Set<String> = emptySet(),
    val directIranDnsServer: String = "1.1.1.1",
    val remoteProxyDnsServer: String = "https://1.1.1.1/dns-query",
    val themeMode: AppThemeMode = AppThemeMode.AMETHYST_NIGHT,
    val dynamicColor: Boolean = false,
    val reducedMotion: Boolean = false,
    val autoReconnectOnBoot: Boolean = false
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val ACTIVE_CONFIG_ID = stringPreferencesKey("active_config_id")
        val BYPASS_IRAN = booleanPreferencesKey("bypass_iran_enabled")
        val ROUTING_MODE = stringPreferencesKey("routing_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val DIRECT_IRAN_DNS = stringPreferencesKey("direct_iran_dns")
        val REMOTE_PROXY_DNS = stringPreferencesKey("remote_proxy_dns")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false,
            activeConfigId = prefs[Keys.ACTIVE_CONFIG_ID],
            bypassIranEnabled = prefs[Keys.BYPASS_IRAN] ?: true,
            routingMode = prefs[Keys.ROUTING_MODE]?.let { runCatching { RoutingMode.valueOf(it) }.getOrNull() }
                ?: RoutingMode.BYPASS_LAN,
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.AMETHYST_NIGHT,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
            directIranDnsServer = prefs[Keys.DIRECT_IRAN_DNS] ?: "1.1.1.1",
            remoteProxyDnsServer = prefs[Keys.REMOTE_PROXY_DNS] ?: "https://1.1.1.1/dns-query"
        )
    }

    suspend fun setBypassIran(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BYPASS_IRAN] = enabled }
    }

    suspend fun setActiveConfigId(id: String) {
        context.dataStore.edit { it[Keys.ACTIVE_CONFIG_ID] = id }
    }

    suspend fun setRoutingMode(mode: RoutingMode) {
        context.dataStore.edit { it[Keys.ROUTING_MODE] = mode.name }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }
}