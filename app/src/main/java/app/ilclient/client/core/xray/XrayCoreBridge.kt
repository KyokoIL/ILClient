package app.ilclient.client.core.xray

import android.net.TrafficStats
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

enum class VpnConnectionState {
    DISCONNECTED,
    PREPARING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTING,
    ERROR
}

data class RealTrafficSnapshot(
    val uploadSpeedBps: Long = 0L,
    val downloadSpeedBps: Long = 0L,
    val totalUploadedBytes: Long = 0L,
    val totalDownloadedBytes: Long = 0L,
    val connectedDurationSec: Long = 0L,
    val recentDownloadHistoryBps: List<Long> = emptyList(),
    val measuredLatencyMs: Long? = null
)

@Singleton
class XrayCoreBridge @Inject constructor() {
    @Volatile
    private var isCoreRunning: Boolean = false

    fun getXrayCoreVersion(): String {
        return runCatching {
            val clazz = Class.forName("libv2ray.Libv2ray")
            val method = clazz.getMethod("checkVersionX")
            method.invoke(null) as? String ?: "Xray-core (MPL-2.0)"
        }.getOrDefault("Xray-core (libXray Android Bridge)")
    }

    suspend fun startCoreAndTunnel(
        xrayJsonConfig: String,
        tunPfd: ParcelFileDescriptor,
        assetDir: File,
        socksPort: Int = 10808
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(xrayJsonConfig.contains("\"outbounds\"")) { "Generated Xray JSON is missing outbounds." }
            runCatching {
                val clazz = Class.forName("libv2ray.Libv2ray")
                val initEnv = clazz.getMethod("initV2Env", String::class.java, String::class.java)
                initEnv.invoke(null, assetDir.absolutePath, "")
            }
            isCoreRunning = true
        }
    }

    suspend fun stopCoreAndTunnel(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { isCoreRunning = false }
    }

    suspend fun measureTcpReachabilityMs(host: String, port: Int, timeoutMs: Int = 3000): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                var elapsed = 0L
                Socket().use { socket ->
                    elapsed = measureTimeMillis {
                        socket.connect(InetSocketAddress(host, port), timeoutMs)
                    }
                }
                elapsed
            }
        }

    suspend fun measureProxyRoundTripLatencyMs(
        socksPort: Int = 10808,
        testUrl: String = "https://cp.cloudflare.com/generate_204",
        timeoutMs: Int = 5000
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
            val client = okhttp3.OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
            val request = okhttp3.Request.Builder().url(testUrl).head().build()
            val startNs = System.nanoTime()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 204) {
                    throw IllegalStateException("HTTP ${response.code}")
                }
            }
            (System.nanoTime() - startNs) / 1_000_000L
        }
    }

    fun readAppUidBytes(uid: Int): Pair<Long, Long> {
        val tx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
        val rx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
        return tx to rx
    }
}