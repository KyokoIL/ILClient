package app.ilclient.client.core.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.*
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.core.app.NotificationCompat
import app.ilclient.client.MainActivity
import app.ilclient.client.R
import app.ilclient.client.core.routing.BypassIranRegistry
import app.ilclient.client.core.routing.XrayConfigBuilder
import app.ilclient.client.core.xray.RealTrafficSnapshot
import app.ilclient.client.core.xray.VpnConnectionState
import app.ilclient.client.core.xray.XrayCoreBridge
import app.ilclient.client.data.local.DiagnosticEventEntity
import app.ilclient.client.data.local.ILClientDao
import app.ilclient.client.data.preferences.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@AndroidEntryPoint
class ILClientVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "app.ilclient.client.action.CONNECT"
        const val ACTION_DISCONNECT = "app.ilclient.client.action.DISCONNECT"
        private const val NOTIFICATION_ID = 4040
        private const val CHANNEL_ID = "ilclient_vpn_status_channel"

        private val _connectionState = MutableStateFlow(VpnConnectionState.DISCONNECTED)
        val connectionState: StateFlow<VpnConnectionState> = _connectionState.asStateFlow()

        private val _trafficStats = MutableStateFlow(RealTrafficSnapshot())
        val trafficStats: StateFlow<RealTrafficSnapshot> = _trafficStats.asStateFlow()

        private val _lastErrorMessage = MutableStateFlow<String?>(null)
        val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()
    }

    @Inject lateinit var xrayCoreBridge: XrayCoreBridge
    @Inject lateinit var configBuilder: XrayConfigBuilder
    @Inject lateinit var dao: ILClientDao
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var bypassIranRegistry: BypassIranRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connectMutex = Mutex()
    private var tunInterface: ParcelFileDescriptor? = null
    private var statsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, "ILClient VPN Status", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> serviceScope.launch { establishVpnTunnel() }
            ACTION_DISCONNECT -> serviceScope.launch { teardownVpnTunnel() }
        }
        return START_STICKY
    }

    private suspend fun establishVpnTunnel() {
        if (!connectMutex.tryLock()) return
        try {
            if (_connectionState.value == VpnConnectionState.CONNECTED) return
            _lastErrorMessage.value = null
            _connectionState.value = VpnConnectionState.PREPARING
            startForeground(NOTIFICATION_ID, buildSafeNotification("Preparing ILClient tunnel..."))

            val settings = settingsDataStore.settingsFlow.first()
            val activeId = settings.activeConfigId ?: throw IllegalStateException("Select or import a configuration first.")
            val config = dao.getConfigById(activeId) ?: throw IllegalStateException("Active configuration not found.")
            val customRules = dao.getEnabledCustomRules()

            _connectionState.value = VpnConnectionState.CONNECTING
            val xrayJson = configBuilder.buildXrayJson(config, settings, customRules)

            val builder = Builder()
                .setSession(getString(R.string.vpn_session_name))
                .setMtu(1500)
                .addAddress("10.25.0.1", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
            builder.addDisallowedApplication(packageName)

            val excluded = bypassIranRegistry.computeDisallowedPackages(
                bypassIranEnabled = settings.bypassIranEnabled,
                splitTunnelMode = settings.splitTunnelMode,
                userSelectedApps = settings.splitTunnelPackages,
                customAppRules = customRules
            )
            excluded.forEach { pkg -> runCatching { builder.addDisallowedApplication(pkg) } }

            val pfd = builder.establish() ?: throw IllegalStateException("VPN permission not granted.")
            tunInterface = pfd

            xrayCoreBridge.startCoreAndTunnel(xrayJson, pfd, filesDir).getOrThrow()
            _connectionState.value = VpnConnectionState.CONNECTED
            dao.insertDiagnosticEvent(DiagnosticEventEntity(category = "VPN", level = "INFO", message = "Connected: ${config.name}"))
            startRealTrafficSampler()
        } catch (e: Exception) {
            _lastErrorMessage.value = e.message
            _connectionState.value = VpnConnectionState.ERROR
            dao.insertDiagnosticEvent(DiagnosticEventEntity(category = "VPN", level = "ERROR", message = e.message ?: "Error"))
            teardownInternal()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } finally {
            connectMutex.unlock()
        }
    }

    private suspend fun teardownVpnTunnel() {
        connectMutex.withLock {
            _connectionState.value = VpnConnectionState.DISCONNECTING
            teardownInternal()
            _connectionState.value = VpnConnectionState.DISCONNECTED
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun teardownInternal() {
        statsJob?.cancel()
        xrayCoreBridge.stopCoreAndTunnel()
        runCatching { tunInterface?.close() }
        tunInterface = null
        _trafficStats.value = RealTrafficSnapshot()
    }

    private fun startRealTrafficSampler() {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            val uid = Process.myUid()
            val (baseTx, baseRx) = xrayCoreBridge.readAppUidBytes(uid)
            var prevTx = baseTx
            var prevRx = baseRx
            val startMs = System.currentTimeMillis()
            val history = ArrayDeque<Long>(30)

            while (isActive && _connectionState.value == VpnConnectionState.CONNECTED) {
                delay(1000L)
                val (currTx, currRx) = xrayCoreBridge.readAppUidBytes(uid)
                val up = (currTx - prevTx).coerceAtLeast(0L)
                val down = (currRx - prevRx).coerceAtLeast(0L)
                prevTx = currTx
                prevRx = currRx
                if (history.size >= 30) history.removeFirst()
                history.addLast(down)
                _trafficStats.value = RealTrafficSnapshot(
                    uploadSpeedBps = up,
                    downloadSpeedBps = down,
                    totalUploadedBytes = (currTx - baseTx).coerceAtLeast(0L),
                    totalDownloadedBytes = (currRx - baseRx).coerceAtLeast(0L),
                    connectedDurationSec = ((System.currentTimeMillis() - startMs) / 1000L).coerceAtLeast(0L),
                    recentDownloadHistoryBps = history.toList()
                )
            }
        }
    }

    private fun buildSafeNotification(statusText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disconnectIntent = PendingIntent.getService(
            this, 1, Intent(this, ILClientVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(statusText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, "Disconnect", disconnectIntent)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}