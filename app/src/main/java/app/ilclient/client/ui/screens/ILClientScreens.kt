package app.ilclient.client.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ilclient.client.core.routing.BypassIranRegistry
import app.ilclient.client.core.routing.RuleTestEvaluation
import app.ilclient.client.core.xray.RealTrafficSnapshot
import app.ilclient.client.core.xray.VpnConnectionState
import app.ilclient.client.data.local.CustomRuleEntity
import app.ilclient.client.data.local.DiagnosticEventEntity
import app.ilclient.client.data.preferences.AppSettings
import app.ilclient.client.data.preferences.RoutingMode
import app.ilclient.client.domain.model.ProxyConfigEntity
import app.ilclient.client.ui.components.ILClientTriangleLogo
import app.ilclient.client.ui.theme.AppThemeMode
import app.ilclient.client.ui.theme.LocalILClientColors

@Composable
fun HomeScreen(
    connectionState: VpnConnectionState,
    traffic: RealTrafficSnapshot,
    activeConfig: ProxyConfigEntity?,
    settings: AppSettings,
    lastError: String?,
    onToggleConnect: () -> Unit,
    onToggleBypassIran: (Boolean) -> Unit,
    onNavigateToConfigs: () -> Unit,
    onTestActiveLatency: () -> Unit
) {
    val colors = LocalILClientColors.current
    val isConnected = connectionState == VpnConnectionState.CONNECTED
    val isConnecting = connectionState == VpnConnectionState.CONNECTING || connectionState == VpnConnectionState.PREPARING

    val ringColor by animateColorAsState(
        targetValue = when (connectionState) {
            VpnConnectionState.CONNECTED -> colors.connectedGreen
            VpnConnectionState.ERROR -> colors.errorRed
            VpnConnectionState.CONNECTING, VpnConnectionState.PREPARING -> colors.accentOrange
            else -> MaterialTheme.colorScheme.primary
        },
        label = "ring_color"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ILClientTriangleLogo(size = 36.dp, isConnected = isConnected, reducedMotion = settings.reducedMotion)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("ILClient", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(connectionState.name, style = MaterialTheme.typography.labelMedium, color = ringColor)
                    }
                }
                AssistChip(onClick = onNavigateToConfigs, label = { Text(activeConfig?.protocol?.badge ?: "No Profile") })
            }
        }
        item {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(210.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(ringColor.copy(alpha = 0.20f), colors.elevatedSurface)))
                    .border(2.5.dp, ringColor.copy(alpha = 0.75f), CircleShape)
                    .clickable { onToggleConnect() }
                    .semantics { contentDescription = "VPN Connect Toggle" }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ILClientTriangleLogo(size = 84.dp, isConnected = isConnected, isConnecting = isConnecting)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (isConnected) "DISCONNECT" else if (isConnecting) "CONNECTING..." else "CONNECT",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
        if (lastError != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = colors.errorRed.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth()) {
                    Text(lastError, color = colors.errorRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = colors.elevatedSurface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bypass Iran (3-Layer Direct)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (settings.bypassIranEnabled) "Active: .ir domains, Iranian IPs & apps route directly." else "Off: Follows global routing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = settings.bypassIranEnabled, onCheckedChange = onToggleBypassIran)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = colors.elevatedSurface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("DOWNLOAD", style = MaterialTheme.typography.labelSmall)
                            Text("${formatBytes(traffic.downloadSpeedBps)}/s", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Total: ${formatBytes(traffic.totalDownloadedBytes)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("UPLOAD", style = MaterialTheme.typography.labelSmall)
                            Text("${formatBytes(traffic.uploadSpeedBps)}/s", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Total: ${formatBytes(traffic.totalUploadedBytes)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    SpeedSparklineGraph(traffic.recentDownloadHistoryBps, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth().height(48.dp))
                }
            }
        }
    }
}

@Composable
fun SpeedSparklineGraph(historyBps: List<Long>, lineColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (historyBps.size < 2) return@Canvas
        val maxVal = (historyBps.maxOrNull() ?: 1L).coerceAtLeast(1024L).toFloat()
        val stepX = size.width / (historyBps.size - 1).coerceAtLeast(1)
        val path = Path()
        historyBps.forEachIndexed { idx, value ->
            val x = idx * stepX
            val y = size.height - ((value.toFloat() / maxVal) * size.height)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 4f))
    }
}

@Composable
fun ConfigsScreen(
    configs: List<ProxyConfigEntity>,
    activeConfigId: String?,
    redactor: (String) -> String,
    onSelectConfig: (String) -> Unit,
    onImportFromText: (String) -> Unit,
    onTestSingleLatency: (ProxyConfigEntity) -> Unit,
    onDeleteConfig: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Configurations", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { onImportFromText(clipboard.getText()?.text.orEmpty()) }) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Paste URI/JSON")
            }
        }
        if (configs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No configurations yet. Copy a VLESS/VMess/Trojan/SS link and tap Paste.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(configs, key = { it.id }) { item ->
                    Card(
                        onClick = { onSelectConfig(item.id) },
                        border = if (item.id == activeConfigId) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${item.protocol.badge} • ${item.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${redactor(item.serverHost)}:${item.serverPort}", style = MaterialTheme.typography.bodySmall)
                            }
                            Row {
                                TextButton(onClick = { onTestSingleLatency(item) }) { Text(item.lastLatencyMs?.let { "${it} ms" } ?: "Ping") }
                                IconButton(onClick = { onDeleteConfig(item.id) }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RulesScreen(
    settings: AppSettings,
    customRules: List<CustomRuleEntity>,
    installedIranAppsCount: Int,
    onToggleBypassIran: (Boolean) -> Unit,
    onSelectRoutingMode: (RoutingMode) -> Unit,
    onEvaluateRuleTest: (String) -> RuleTestEvaluation
) {
    var testInput by remember { mutableStateOf("digikala.com") }
    val testResult = remember(testInput, settings, customRules) { onEvaluateRuleTest(testInput) }

    LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Routing & Bypass Iran", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Precedence: 1. Custom Rules → 2. Bypass Iran → 3. Global Mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Bypass Iran (3-Layer Engine)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Switch(checked = settings.bypassIranEnabled, onCheckedChange = onToggleBypassIran)
                    }
                    Text(
                        "• Domains: .ir & geosite:category-ir (~${BypassIranRegistry.APPROX_GEOSITE_IR_DOMAINS})\n" +
                        "• IPs: geoip:ir (~${BypassIranRegistry.APPROX_GEOIP_IR_CIDRS} CIDRs)\n" +
                        "• Apps: ${BypassIranRegistry.CURATED_IRANIAN_PACKAGES.size} packages ($installedIranAppsCount installed)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Rule Test Tool", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = testInput, onValueChange = { testInput = it }, label = { Text("Enter domain, IP, or package") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Result: ${testResult.resultingAction} (${testResult.matchedLayer})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            RoutingMode.entries.forEach { mode ->
                Row(modifier = Modifier.fillMaxWidth().clickable { onSelectRoutingMode(mode) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = settings.routingMode == mode, onClick = { onSelectRoutingMode(mode) })
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(mode.title, fontWeight = FontWeight.SemiBold)
                        Text(mode.plainExplanation, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    coreVersion: String,
    onSelectTheme: (AppThemeMode) -> Unit,
    onUpdateGeoData: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Appearance & Themes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    AppThemeMode.entries.forEach { mode ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onSelectTheme(mode) }.padding(vertical = 4.dp)) {
                            RadioButton(selected = settings.themeMode == mode, onClick = { onSelectTheme(mode) })
                            Text(mode.name.replace("_", " "), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Xray Core & Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Engine: $coreVersion", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) } }) { Text("System Kill Switch") }
                        FilledTonalButton(onClick = onUpdateGeoData) { Text("Update Geo Data") }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    return "%.2f MB".format(mb)
}