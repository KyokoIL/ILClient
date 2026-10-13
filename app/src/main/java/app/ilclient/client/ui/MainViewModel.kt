package app.ilclient.client.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ilclient.client.core.parser.ConfigParser
import app.ilclient.client.core.parser.ParseResult
import app.ilclient.client.core.routing.BypassIranRegistry
import app.ilclient.client.core.routing.GeoDataUpdater
import app.ilclient.client.core.routing.RuleTestEvaluation
import app.ilclient.client.core.routing.XrayConfigBuilder
import app.ilclient.client.core.security.SecretVault
import app.ilclient.client.core.xray.XrayCoreBridge
import app.ilclient.client.data.local.CustomRuleEntity
import app.ilclient.client.data.local.ILClientDao
import app.ilclient.client.data.preferences.AppSettings
import app.ilclient.client.data.preferences.RoutingMode
import app.ilclient.client.data.preferences.SettingsDataStore
import app.ilclient.client.domain.model.ProxyConfigEntity
import app.ilclient.client.ui.theme.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dao: ILClientDao,
    private val settingsDataStore: SettingsDataStore,
    private val parser: ConfigParser,
    private val secretVault: SecretVault,
    private val xrayCoreBridge: XrayCoreBridge,
    private val configBuilder: XrayConfigBuilder,
    private val bypassIranRegistry: BypassIranRegistry,
    private val geoDataUpdater: GeoDataUpdater
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val configs: StateFlow<List<ProxyConfigEntity>> = dao.observeConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customRules: StateFlow<List<CustomRuleEntity>> = dao.observeCustomRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val coreVersion: String = xrayCoreBridge.getXrayCoreVersion()
    val installedIranAppsCount: Int by lazy {
        bypassIranRegistry.getInstalledIranianApps().count { it.isInstalled }
    }

    fun redactHost(host: String): String = secretVault.redactHost(host)

    fun setBypassIran(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setBypassIran(enabled)
    }

    fun selectConfig(id: String) = viewModelScope.launch {
        settingsDataStore.setActiveConfigId(id)
    }

    fun setRoutingMode(mode: RoutingMode) = viewModelScope.launch {
        settingsDataStore.setRoutingMode(mode)
    }

    fun setThemeMode(mode: AppThemeMode) = viewModelScope.launch {
        settingsDataStore.setThemeMode(mode)
    }

    fun importFromText(raw: String) = viewModelScope.launch {
        val parsed = parser.parseBatch(raw).filterIsInstance<ParseResult.Success>()
        if (parsed.isNotEmpty()) {
            val entities = parsed.map { it.config }
            dao.upsertConfigs(entities)
            if (settings.value.activeConfigId == null) {
                settingsDataStore.setActiveConfigId(entities.first().id)
            }
        }
    }

    fun testSingleLatency(config: ProxyConfigEntity) = viewModelScope.launch {
        val ms = xrayCoreBridge.measureTcpReachabilityMs(config.serverHost, config.serverPort).getOrNull()
        dao.upsertConfig(config.copy(lastLatencyMs = ms))
    }

    fun deleteConfig(id: String) = viewModelScope.launch {
        dao.deleteConfigById(id)
    }

    fun updateGeoData() = viewModelScope.launch {
        geoDataUpdater.updateGeoDatabases()
    }

    fun evaluateRule(input: String): RuleTestEvaluation {
        return configBuilder.evaluateRuleTest(
            query = input,
            settings = settings.value,
            customRules = customRules.value,
            curatedIranPackages = BypassIranRegistry.CURATED_IRANIAN_PACKAGES.map { it.packageName }.toSet()
        )
    }
}