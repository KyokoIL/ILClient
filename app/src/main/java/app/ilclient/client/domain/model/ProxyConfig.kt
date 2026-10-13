package app.ilclient.client.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class ProxyProtocol(val badge: String) {
    VLESS("VLESS"),
    VMESS("VMess"),
    TROJAN("Trojan"),
    SHADOWSOCKS("SS"),
    SOCKS("SOCKS5"),
    HTTP("HTTP"),
    CUSTOM_JSON("JSON")
}

@Serializable
enum class TransportType(val wireName: String) {
    TCP("tcp"),
    WS("ws"),
    GRPC("grpc"),
    HTTPUPGRADE("httpupgrade"),
    XHTTP("xhttp")
}

@Serializable
enum class SecurityType(val wireName: String) {
    NONE("none"),
    TLS("tls"),
    REALITY("reality")
}

@Entity(tableName = "proxy_configs")
data class ProxyConfigEntity(
    @PrimaryKey val id: String,
    val name: String,
    val protocol: ProxyProtocol,
    val serverHost: String,
    val serverPort: Int,
    val encryptedCredential: String,
    val methodOrEncryption: String = "none",
    val flow: String = "",
    val transport: TransportType = TransportType.TCP,
    val security: SecurityType = SecurityType.NONE,
    val sni: String = "",
    val fingerprint: String = "chrome",
    val alpn: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val pathOrServiceName: String = "",
    val hostHeader: String = "",
    val allowInsecure: Boolean = false,
    val rawCustomJson: String? = null,
    val subscriptionId: String? = null,
    val groupName: String = "Default",
    val isFavorite: Boolean = false,
    val manualOrder: Int = 0,
    val isManuallyEdited: Boolean = false,
    val lastLatencyMs: Long? = null,
    val lastConnectedAt: Long? = null,
    val lastTestStatus: String? = null,
    val muxEnabled: Boolean = false,
    val muxConcurrency: Int = 8,
    val tlsFragmentEnabled: Boolean = false,
    val tlsFragmentLength: String = "10-30",
    val tlsFragmentInterval: String = "10-20"
)