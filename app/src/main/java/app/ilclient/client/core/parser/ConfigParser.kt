package app.ilclient.client.core.parser

import android.net.Uri
import android.util.Base64
import app.ilclient.client.core.security.SecretVault
import app.ilclient.client.domain.model.*
import kotlinx.serialization.json.*
import java.net.URLDecoder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class ParseResult {
    data class Success(val config: ProxyConfigEntity, val dedupKey: String) : ParseResult()
    data class Failure(val inputSnippet: String, val actionableError: String) : ParseResult()
}

@Singleton
class ConfigParser @Inject constructor(
    private val secretVault: SecretVault
) {
    private val supportedSsCiphers = setOf(
        "2022-blake3-aes-128-gcm",
        "2022-blake3-aes-256-gcm",
        "2022-blake3-chacha20-poly1305",
        "aes-256-gcm",
        "aes-128-gcm",
        "chacha20-ietf-poly1305",
        "chacha20-poly1305",
        "none",
        "plain"
    )

    fun parseBatch(rawInput: String, subscriptionId: String? = null): List<ParseResult> {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return listOf(ParseResult.Failure("", "Input is empty."))
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return parseRawJson(trimmed, subscriptionId)
        }
        val decodedIfBase64 = tryDecodeSubscriptionBase64(trimmed)
        val lines = (decodedIfBase64 ?: trimmed)
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        return lines.map { line -> parseSingleUri(line, subscriptionId) }
    }

    fun parseSingleUri(uriString: String, subscriptionId: String? = null): ParseResult {
        return runCatching {
            when {
                uriString.startsWith("vless://", ignoreCase = true) -> parseVlessOrTrojan(uriString, ProxyProtocol.VLESS, subscriptionId)
                uriString.startsWith("trojan://", ignoreCase = true) -> parseVlessOrTrojan(uriString, ProxyProtocol.TROJAN, subscriptionId)
                uriString.startsWith("vmess://", ignoreCase = true) -> parseVmess(uriString, subscriptionId)
                uriString.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(uriString, subscriptionId)
                uriString.startsWith("socks://", ignoreCase = true) -> parseSocks(uriString, subscriptionId)
                else -> ParseResult.Failure(uriString.take(32), "Unsupported URI scheme.")
            }
        }.getOrElse { e ->
            ParseResult.Failure(uriString.take(32), e.message ?: "Malformed share link.")
        }
    }

    private fun parseVlessOrTrojan(rawUri: String, protocol: ProxyProtocol, subscriptionId: String?): ParseResult {
        val uri = Uri.parse(rawUri)
        val credential = uri.userInfo?.trim().orEmpty()
        if (credential.isEmpty()) return ParseResult.Failure(rawUri.take(28), "Missing ${protocol.badge} credential.")
        val host = uri.host?.trim().orEmpty()
        val port = uri.port
        if (host.isEmpty() || port !in 1..65535) return ParseResult.Failure(rawUri.take(28), "Invalid host or port.")

        val typeParam = uri.getQueryParameter("type")?.lowercase() ?: "tcp"
        val transport = when (typeParam) {
            "tcp" -> TransportType.TCP
            "ws", "websocket" -> TransportType.WS
            "grpc" -> TransportType.GRPC
            "httpupgrade" -> TransportType.HTTPUPGRADE
            "xhttp", "splithttp" -> TransportType.XHTTP
            else -> return ParseResult.Failure(rawUri.take(28), "Unsupported transport '$typeParam'.")
        }

        val securityParam = uri.getQueryParameter("security")?.lowercase()
            ?: if (protocol == ProxyProtocol.TROJAN) "tls" else "none"
        val security = when (securityParam) {
            "none", "" -> SecurityType.NONE
            "tls" -> SecurityType.TLS
            "reality" -> SecurityType.REALITY
            else -> return ParseResult.Failure(rawUri.take(28), "Unsupported security '$securityParam'.")
        }

        val pbk = uri.getQueryParameter("pbk").orEmpty()
        if (security == SecurityType.REALITY && pbk.isBlank()) {
            return ParseResult.Failure(rawUri.take(28), "REALITY requires 'pbk' parameter.")
        }

        val remark = uri.fragment?.let { URLDecoder.decode(it, "UTF-8") }?.takeIf { it.isNotBlank() }
            ?: "${protocol.badge} $host:$port"
        val path = uri.getQueryParameter("path") ?: uri.getQueryParameter("serviceName") ?: ""
        val sni = uri.getQueryParameter("sni").orEmpty()
        val flow = uri.getQueryParameter("flow").orEmpty()
        val fp = uri.getQueryParameter("fp")?.ifBlank { "chrome" } ?: "chrome"
        val sid = uri.getQueryParameter("sid").orEmpty()
        val spx = uri.getQueryParameter("spx").orEmpty()
        val hostHeader = uri.getQueryParameter("host").orEmpty()
        val encryption = uri.getQueryParameter("encryption") ?: "none"

        val entity = ProxyConfigEntity(
            id = UUID.randomUUID().toString(),
            name = remark,
            protocol = protocol,
            serverHost = host,
            serverPort = port,
            encryptedCredential = secretVault.encrypt(credential),
            methodOrEncryption = encryption,
            flow = flow,
            transport = transport,
            security = security,
            sni = sni,
            fingerprint = fp,
            publicKey = pbk,
            shortId = sid,
            spiderX = spx,
            pathOrServiceName = path,
            hostHeader = hostHeader,
            subscriptionId = subscriptionId
        )
        return ParseResult.Success(entity, "${protocol.name}:$host:$port:$credential:$transport:$security:$sni:$pbk:$path")
    }

    private fun parseVmess(rawUri: String, subscriptionId: String?): ParseResult {
        val b64Payload = rawUri.removePrefix("vmess://").removePrefix("VMESS://").trim()
        val decodedJson = runCatching { String(Base64.decode(b64Payload, Base64.DEFAULT), Charsets.UTF_8) }
            .getOrElse { return ParseResult.Failure("vmess://...", "Invalid Base64 in VMess link.") }
        val obj = runCatching { Json.parseToJsonElement(decodedJson).jsonObject }
            .getOrElse { return ParseResult.Failure("vmess://...", "Malformed JSON in VMess link.") }

        val host = obj["add"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val port = obj["port"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: -1
        val id = obj["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (host.isEmpty() || port !in 1..65535 || id.isEmpty()) {
            return ParseResult.Failure("vmess://...", "Missing host, port, or id.")
        }
        val net = obj["net"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "tcp"
        val transport = when (net) {
            "tcp" -> TransportType.TCP
            "ws" -> TransportType.WS
            "grpc" -> TransportType.GRPC
            "httpupgrade" -> TransportType.HTTPUPGRADE
            else -> return ParseResult.Failure("vmess://...", "Unsupported VMess network '$net'.")
        }
        val security = if (obj["tls"]?.jsonPrimitive?.contentOrNull?.lowercase() == "tls") SecurityType.TLS else SecurityType.NONE
        val ps = obj["ps"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "VMess $host:$port"
        val scy = obj["scy"]?.jsonPrimitive?.contentOrNull ?: "auto"
        val sni = obj["sni"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val path = obj["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val hostHeader = obj["host"]?.jsonPrimitive?.contentOrNull.orEmpty()

        val entity = ProxyConfigEntity(
            id = UUID.randomUUID().toString(),
            name = ps,
            protocol = ProxyProtocol.VMESS,
            serverHost = host,
            serverPort = port,
            encryptedCredential = secretVault.encrypt(id),
            methodOrEncryption = scy,
            transport = transport,
            security = security,
            sni = sni,
            pathOrServiceName = path,
            hostHeader = hostHeader,
            subscriptionId = subscriptionId
        )
        return ParseResult.Success(entity, "VMESS:$host:$port:$id:$transport:$security:$path")
    }

    private fun parseShadowsocks(rawUri: String, subscriptionId: String?): ParseResult {
        val withoutScheme = rawUri.substring(5)
        val remark = withoutScheme.substringAfter("#", "").let { URLDecoder.decode(it, "UTF-8") }
        val mainPart = withoutScheme.substringBefore("#")
        val resolved = if ("@" in mainPart) {
            val userInfoB64 = mainPart.substringBefore("@")
            val hostPort = mainPart.substringAfter("@")
            val decodedUser = runCatching {
                String(Base64.decode(userInfoB64, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)
            }.getOrDefault(URLDecoder.decode(userInfoB64, "UTF-8"))
            "$decodedUser@$hostPort"
        } else {
            String(Base64.decode(mainPart, Base64.DEFAULT), Charsets.UTF_8)
        }
        val methodPassword = resolved.substringBeforeLast("@")
        val hostPort = resolved.substringAfterLast("@").substringBefore("?")
        val method = methodPassword.substringBefore(":").lowercase()
        val password = methodPassword.substringAfter(":")
        val host = hostPort.substringBeforeLast(":")
        val port = hostPort.substringAfterLast(":").toIntOrNull() ?: -1

        if (method !in supportedSsCiphers) return ParseResult.Failure("ss://...", "Unsupported cipher '$method'.")
        if (host.isBlank() || port !in 1..65535 || password.isBlank()) return ParseResult.Failure("ss://...", "Invalid SS host/port.")

        val entity = ProxyConfigEntity(
            id = UUID.randomUUID().toString(),
            name = remark.ifBlank { "SS $host:$port" },
            protocol = ProxyProtocol.SHADOWSOCKS,
            serverHost = host,
            serverPort = port,
            encryptedCredential = secretVault.encrypt(password),
            methodOrEncryption = method,
            subscriptionId = subscriptionId
        )
        return ParseResult.Success(entity, "SS:$host:$port:$method:$password")
    }

    private fun parseSocks(rawUri: String, subscriptionId: String?): ParseResult {
        val uri = Uri.parse(rawUri)
        val host = uri.host.orEmpty()
        val port = uri.port
        if (host.isBlank() || port !in 1..65535) return ParseResult.Failure("socks://...", "Invalid SOCKS host/port.")
        val userPass = uri.userInfo.orEmpty()
        val entity = ProxyConfigEntity(
            id = UUID.randomUUID().toString(),
            name = uri.fragment ?: "SOCKS $host:$port",
            protocol = ProxyProtocol.SOCKS,
            serverHost = host,
            serverPort = port,
            encryptedCredential = secretVault.encrypt(userPass),
            subscriptionId = subscriptionId
        )
        return ParseResult.Success(entity, "SOCKS:$host:$port:$userPass")
    }

    private fun parseRawJson(rawJson: String, subscriptionId: String?): List<ParseResult> {
        return runCatching {
            val element = Json.parseToJsonElement(rawJson)
            val objects = if (element is JsonArray) element.map { it.jsonObject } else listOf(element.jsonObject)
            objects.mapIndexed { idx, obj ->
                val outbounds = obj["outbounds"]?.jsonArray
                if (outbounds.isNullOrEmpty()) {
                    ParseResult.Failure("JSON[$idx]", "Xray JSON is missing 'outbounds'.")
                } else {
                    val remarks = obj["remarks"]?.jsonPrimitive?.contentOrNull ?: "Custom JSON #${idx + 1}"
                    val entity = ProxyConfigEntity(
                        id = UUID.randomUUID().toString(),
                        name = remarks,
                        protocol = ProxyProtocol.CUSTOM_JSON,
                        serverHost = "json-config",
                        serverPort = 443,
                        encryptedCredential = "",
                        rawCustomJson = obj.toString(),
                        subscriptionId = subscriptionId
                    )
                    ParseResult.Success(entity, "JSON:${obj.toString().hashCode()}")
                }
            }
        }.getOrElse { e ->
            listOf(ParseResult.Failure("JSON", "Malformed JSON: ${e.message}"))
        }
    }

    private fun tryDecodeSubscriptionBase64(input: String): String? {
        if (input.contains("://") || input.contains("{")) return null
        return runCatching {
            val decoded = String(Base64.decode(input, Base64.DEFAULT), Charsets.UTF_8)
            if (decoded.contains("://")) decoded else null
        }.getOrNull()
    }
}