package app.ilclient.client.core.routing

import app.ilclient.client.core.security.SecretVault
import app.ilclient.client.data.local.CustomRuleEntity
import app.ilclient.client.data.local.RuleMatchType
import app.ilclient.client.data.preferences.AppSettings
import app.ilclient.client.data.preferences.RoutingMode
import app.ilclient.client.domain.model.*
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

data class RuleTestEvaluation(
    val input: String,
    val matchedLayer: String,
    val matchedRuleDescription: String,
    val resultingAction: String
)

@Singleton
class XrayConfigBuilder @Inject constructor(
    private val secretVault: SecretVault
) {
    fun buildXrayJson(
        config: ProxyConfigEntity,
        settings: AppSettings,
        customRules: List<CustomRuleEntity>
    ): String {
        if (config.protocol == ProxyProtocol.CUSTOM_JSON && !config.rawCustomJson.isNullOrBlank() && !settings.bypassIranEnabled) {
            return config.rawCustomJson
        }
        val credential = secretVault.decrypt(config.encryptedCredential)

        val root = buildJsonObject {
            putJsonObject("log") { put("loglevel", "warning") }
            putJsonObject("stats") {}
            putJsonArray("inbounds") {
                addJsonObject {
                    put("tag", "socks-in")
                    put("port", 10808)
                    put("listen", "127.0.0.1")
                    put("protocol", "socks")
                    putJsonObject("settings") {
                        put("auth", "noauth")
                        put("udp", true)
                    }
                    putJsonObject("sniffing") {
                        put("enabled", true)
                        putJsonArray("destOverride") {
                            add("http")
                            add("tls")
                            add("quic")
                        }
                        put("routeOnly", true)
                    }
                }
            }
            putJsonObject("dns") {
                put("queryStrategy", "UseIPv4")
                putJsonArray("servers") {
                    if (settings.bypassIranEnabled) {
                        addJsonObject {
                            put("address", settings.directIranDnsServer.ifBlank { "1.1.1.1" })
                            putJsonArray("domains") {
                                add("domain:ir")
                                add("geosite:ir")
                                add("geosite:category-ir")
                            }
                            putJsonArray("expectIPs") { add("geoip:ir") }
                            put("tag", "dns-direct-ir")
                        }
                    }
                    add(JsonPrimitive(settings.remoteProxyDnsServer.ifBlank { "https://1.1.1.1/dns-query" }))
                }
            }
            putJsonArray("outbounds") {
                add(buildPrimaryProxyOutbound(config, credential))
                addJsonObject {
                    put("tag", "direct")
                    put("protocol", "freedom")
                    putJsonObject("settings") { put("domainStrategy", "UseIP") }
                }
                addJsonObject {
                    put("tag", "block")
                    put("protocol", "blackhole")
                    putJsonObject("settings") {
                        putJsonObject("response") { put("type", "none") }
                    }
                }
            }
            putJsonObject("routing") {
                put("domainStrategy", "IPIfNonMatch")
                putJsonArray("rules") {
                    customRules.filter { it.enabled }.sortedBy { it.priorityOrder }.forEach { rule ->
                        when (rule.matchType) {
                            RuleMatchType.DOMAIN -> addJsonObject {
                                put("type", "field")
                                putJsonArray("domain") { add(rule.value.trim()) }
                                put("outboundTag", rule.outboundAction.outboundTag)
                            }
                            RuleMatchType.IP_CIDR -> addJsonObject {
                                put("type", "field")
                                putJsonArray("ip") { add(rule.value.trim()) }
                                put("outboundTag", rule.outboundAction.outboundTag)
                            }
                            RuleMatchType.PACKAGE_NAME -> {}
                        }
                    }
                    if (settings.bypassIranEnabled) {
                        addJsonObject {
                            put("type", "field")
                            putJsonArray("domain") {
                                add("domain:ir")
                                add("geosite:ir")
                                add("geosite:category-ir")
                            }
                            put("outboundTag", "direct")
                        }
                        addJsonObject {
                            put("type", "field")
                            putJsonArray("ip") { add("geoip:ir") }
                            put("outboundTag", "direct")
                        }
                    }
                    when (settings.routingMode) {
                        RoutingMode.BYPASS_LAN -> addJsonObject {
                            put("type", "field")
                            putJsonArray("ip") { add("geoip:private") }
                            put("outboundTag", "direct")
                        }
                        RoutingMode.DIRECT_ALL -> addJsonObject {
                            put("type", "field")
                            put("network", "tcp,udp")
                            put("outboundTag", "direct")
                        }
                        RoutingMode.GLOBAL_PROXY, RoutingMode.CUSTOM_RULES -> {}
                    }
                }
            }
        }
        return root.toString()
    }

    private fun buildPrimaryProxyOutbound(config: ProxyConfigEntity, credential: String): JsonObject =
        buildJsonObject {
            put("tag", "proxy")
            put("protocol", config.protocol.name.lowercase())
            putJsonObject("settings") {
                when (config.protocol) {
                    ProxyProtocol.VLESS -> putJsonArray("vnext") {
                        addJsonObject {
                            put("address", config.serverHost)
                            put("port", config.serverPort)
                            putJsonArray("users") {
                                addJsonObject {
                                    put("id", credential)
                                    put("encryption", config.methodOrEncryption.ifBlank { "none" })
                                    if (config.flow.isNotBlank()) put("flow", config.flow)
                                }
                            }
                        }
                    }
                    ProxyProtocol.VMESS -> putJsonArray("vnext") {
                        addJsonObject {
                            put("address", config.serverHost)
                            put("port", config.serverPort)
                            putJsonArray("users") {
                                addJsonObject {
                                    put("id", credential)
                                    put("security", config.methodOrEncryption.ifBlank { "auto" })
                                }
                            }
                        }
                    }
                    ProxyProtocol.TROJAN, ProxyProtocol.SHADOWSOCKS -> putJsonArray("servers") {
                        addJsonObject {
                            put("address", config.serverHost)
                            put("port", config.serverPort)
                            put("password", credential)
                            if (config.protocol == ProxyProtocol.SHADOWSOCKS) put("method", config.methodOrEncryption)
                        }
                    }
                    ProxyProtocol.SOCKS, ProxyProtocol.HTTP -> putJsonArray("servers") {
                        addJsonObject {
                            put("address", config.serverHost)
                            put("port", config.serverPort)
                        }
                    }
                    ProxyProtocol.CUSTOM_JSON -> {}
                }
            }
            putJsonObject("streamSettings") {
                put("network", config.transport.wireName)
                put("security", config.security.wireName)
                if (config.security == SecurityType.TLS) {
                    putJsonObject("tlsSettings") {
                        if (config.sni.isNotBlank()) put("serverName", config.sni)
                        put("allowInsecure", config.allowInsecure)
                        put("fingerprint", config.fingerprint)
                    }
                } else if (config.security == SecurityType.REALITY) {
                    putJsonObject("realitySettings") {
                        put("serverName", config.sni)
                        put("fingerprint", config.fingerprint)
                        put("publicKey", config.publicKey)
                        put("shortId", config.shortId)
                        put("spiderX", config.spiderX)
                    }
                }
            }
        }

    fun evaluateRuleTest(
        query: String,
        settings: AppSettings,
        customRules: List<CustomRuleEntity>,
        curatedIranPackages: Set<String>
    ): RuleTestEvaluation {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return RuleTestEvaluation(query, "Invalid Input", "Empty query", "NONE")

        for (rule in customRules.filter { it.enabled }.sortedBy { it.priorityOrder }) {
            val pattern = rule.value.trim().lowercase().removePrefix("domain:").removePrefix("full:")
            if (clean == pattern || clean.endsWith(".$pattern")) {
                return RuleTestEvaluation(
                    input = clean,
                    matchedLayer = "1. User Custom Rule (${rule.name})",
                    matchedRuleDescription = "${rule.matchType}: ${rule.value}",
                    resultingAction = rule.outboundAction.name
                )
            }
        }

        if (settings.bypassIranEnabled) {
            if (clean in curatedIranPackages || clean.startsWith("ir.")) {
                return RuleTestEvaluation(clean, "2. Bypass Iran • Layer 3 (App)", "Excluded from VpnService TUN", "DIRECT")
            }
            if (clean.endsWith(".ir") || clean in setOf("digikala.com", "aparat.com", "shaparak.ir")) {
                return RuleTestEvaluation(clean, "2. Bypass Iran • Layer 1 (Domain)", "Matches domain:ir / geosite:category-ir", "DIRECT")
            }
            if (clean.startsWith("2.176.") || clean.startsWith("5.160.")) {
                return RuleTestEvaluation(clean, "2. Bypass Iran • Layer 2 (IP)", "Matches geoip:ir", "DIRECT")
            }
        }

        return when (settings.routingMode) {
            RoutingMode.DIRECT_ALL -> RuleTestEvaluation(clean, "3. Global Mode (Direct)", "All traffic Direct", "DIRECT")
            RoutingMode.BYPASS_LAN -> if (clean.startsWith("192.168.") || clean.startsWith("10.")) {
                RuleTestEvaluation(clean, "3. Global Mode (Bypass LAN)", "Matches geoip:private", "DIRECT")
            } else {
                RuleTestEvaluation(clean, "3. Global Mode (Bypass LAN)", "Default proxy outbound", "PROXY")
            }
            RoutingMode.GLOBAL_PROXY, RoutingMode.CUSTOM_RULES ->
                RuleTestEvaluation(clean, "3. Global Mode (Proxy)", "Default Xray proxy outbound", "PROXY")
        }
    }
}