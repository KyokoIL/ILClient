package app.ilclient.client

import app.ilclient.client.core.routing.XrayConfigBuilder
import app.ilclient.client.core.security.SecretVault
import app.ilclient.client.data.local.CustomRuleEntity
import app.ilclient.client.data.local.RuleAction
import app.ilclient.client.data.local.RuleMatchType
import app.ilclient.client.data.preferences.AppSettings
import app.ilclient.client.data.preferences.RoutingMode
import app.ilclient.client.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class XrayRoutingAndBypassIranTest {

    private val fakeVault = object : SecretVault() {
        override fun encrypt(plainText: String): String = plainText
        override fun decrypt(encoded: String): String = encoded
    }
    private val builder = XrayConfigBuilder(fakeVault)

    private val sampleConfig = ProxyConfigEntity(
        id = "test-1",
        name = "Test VLESS REALITY",
        protocol = ProxyProtocol.VLESS,
        serverHost = "edge.example.com",
        serverPort = 443,
        encryptedCredential = "11111111-2222-3333-4444-555555555555",
        security = SecurityType.REALITY,
        publicKey = "testPublicKey123",
        sni = "www.microsoft.com"
    )

    @Test
    fun `generated Xray JSON includes Iran domain, geoip, and split-DNS rules when Bypass Iran is ON`() {
        val settings = AppSettings(bypassIranEnabled = true, routingMode = RoutingMode.GLOBAL_PROXY)
        val json = builder.buildXrayJson(sampleConfig, settings, emptyList())
        assertTrue(json.contains("\"domain:ir\""))
        assertTrue(json.contains("\"geosite:category-ir\""))
        assertTrue(json.contains("\"geoip:ir\""))
        assertTrue(json.contains("\"dns-direct-ir\""))
    }

    @Test
    fun `generated Xray JSON omits Iran bypass rules when Bypass Iran is OFF`() {
        val settings = AppSettings(bypassIranEnabled = false, routingMode = RoutingMode.GLOBAL_PROXY)
        val json = builder.buildXrayJson(sampleConfig, settings, emptyList())
        assertFalse(json.contains("geosite:category-ir"))
        assertFalse(json.contains("geoip:ir"))
    }

    @Test
    fun `custom user rule takes precedence over Bypass Iran rule in Rule Test Tool`() {
        val settings = AppSettings(bypassIranEnabled = true, routingMode = RoutingMode.GLOBAL_PROXY)
        val customRule = listOf(
            CustomRuleEntity("r1", "Force Proxy", RuleMatchType.DOMAIN, "special.ir", RuleAction.PROXY, 1)
        )
        val eval = builder.evaluateRuleTest("special.ir", settings, customRule, emptySet())
        assertEquals("PROXY", eval.resultingAction)
    }
}