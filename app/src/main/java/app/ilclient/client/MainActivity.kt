package app.ilclient.client

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.ilclient.client.core.vpn.ILClientVpnService
import app.ilclient.client.core.xray.VpnConnectionState
import app.ilclient.client.ui.MainViewModel
import app.ilclient.client.ui.navigation.ILClientRoute
import app.ilclient.client.ui.screens.*
import app.ilclient.client.ui.theme.ILClientTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val vm: MainViewModel = hiltViewModel()
            val settings by vm.settings.collectAsState()
            val configs by vm.configs.collectAsState()
            val customRules by vm.customRules.collectAsState()
            val connectionState by ILClientVpnService.connectionState.collectAsState()
            val traffic by ILClientVpnService.trafficStats.collectAsState()
            val lastError by ILClientVpnService.lastErrorMessage.collectAsState()

            val vpnPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    startVpnAction(ILClientVpnService.ACTION_CONNECT)
                }
            }

            ILClientTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = backStackEntry?.destination?.route?.contains("Home") == true,
                                onClick = { navController.navigate(ILClientRoute.Home) { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Shield, contentDescription = "Home") },
                                label = { Text("Home") }
                            )
                            NavigationBarItem(
                                selected = backStackEntry?.destination?.route?.contains("Configs") == true,
                                onClick = { navController.navigate(ILClientRoute.Configs) { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Dns, contentDescription = "Configs") },
                                label = { Text("Configs") }
                            )
                            NavigationBarItem(
                                selected = backStackEntry?.destination?.route?.contains("Rules") == true,
                                onClick = { navController.navigate(ILClientRoute.Rules) { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Route, contentDescription = "Rules") },
                                label = { Text("Rules") }
                            )
                            NavigationBarItem(
                                selected = backStackEntry?.destination?.route?.contains("Settings") == true,
                                onClick = { navController.navigate(ILClientRoute.Settings) { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = ILClientRoute.Home,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable<ILClientRoute.Home> {
                            val activeConfig = configs.firstOrNull { it.id == settings.activeConfigId }
                            HomeScreen(
                                connectionState = connectionState,
                                traffic = traffic,
                                activeConfig = activeConfig,
                                settings = settings,
                                lastError = lastError,
                                onToggleConnect = { toggleVpn(connectionState) { vpnPermissionLauncher.launch(it) } },
                                onToggleBypassIran = vm::setBypassIran,
                                onNavigateToConfigs = { navController.navigate(ILClientRoute.Configs) },
                                onTestActiveLatency = { activeConfig?.let { vm.testSingleLatency(it) } }
                            )
                        }
                        composable<ILClientRoute.Configs> {
                            ConfigsScreen(
                                configs = configs,
                                activeConfigId = settings.activeConfigId,
                                redactor = vm::redactHost,
                                onSelectConfig = vm::selectConfig,
                                onImportFromText = vm::importFromText,
                                onTestSingleLatency = vm::testSingleLatency,
                                onDeleteConfig = vm::deleteConfig
                            )
                        }
                        composable<ILClientRoute.Rules> {
                            RulesScreen(
                                settings = settings,
                                customRules = customRules,
                                installedIranAppsCount = vm.installedIranAppsCount,
                                onToggleBypassIran = vm::setBypassIran,
                                onSelectRoutingMode = vm::setRoutingMode,
                                onEvaluateRuleTest = vm::evaluateRule
                            )
                        }
                        composable<ILClientRoute.Settings> {
                            SettingsScreen(
                                settings = settings,
                                coreVersion = vm.coreVersion,
                                onSelectTheme = vm::setThemeMode,
                                onUpdateGeoData = vm::updateGeoData
                            )
                        }
                    }
                }
            }
        }
    }

    private fun toggleVpn(currentState: VpnConnectionState, launchPermission: (Intent) -> Unit) {
        if (currentState == VpnConnectionState.CONNECTED || currentState == VpnConnectionState.CONNECTING) {
            startVpnAction(ILClientVpnService.ACTION_DISCONNECT)
        } else {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) launchPermission(prepareIntent)
            else startVpnAction(ILClientVpnService.ACTION_CONNECT)
        }
    }

    private fun startVpnAction(action: String) {
        val intent = Intent(this, ILClientVpnService::class.java).apply { this.action = action }
        startForegroundService(intent)
    }
}