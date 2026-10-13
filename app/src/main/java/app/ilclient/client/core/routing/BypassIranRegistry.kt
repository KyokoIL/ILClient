package app.ilclient.client.core.routing

import android.content.Context
import app.ilclient.client.data.local.CustomRuleEntity
import app.ilclient.client.data.local.RuleAction
import app.ilclient.client.data.local.RuleMatchType
import app.ilclient.client.data.preferences.SplitTunnelMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class IranianAppEntry(
    val packageName: String,
    val displayName: String,
    val category: String,
    val isInstalled: Boolean = false
)

@Singleton
class BypassIranRegistry @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val APPROX_GEOSITE_IR_DOMAINS = 3420
        const val APPROX_GEOIP_IR_CIDRS = 1890

        val CURATED_IRANIAN_PACKAGES: List<IranianAppEntry> = listOf(
            IranianAppEntry("com.sibche.aspardproject.app", "Asan Pardakht (Ap)", "Banking & Payment"),
            IranianAppEntry("ir.sep.sesoot", "724 (Sep)", "Banking & Payment"),
            IranianAppEntry("com.bmi.bam", "Baam (Bank Melli)", "Banking & Payment"),
            IranianAppEntry("ir.tejaratbank.tata.mobile.android.tejarat", "Tejarat Mobile", "Banking & Payment"),
            IranianAppEntry("com.parsian.parshang", "Parshang", "Banking & Payment"),
            IranianAppEntry("com.tosan.aha", "Hamrah Card", "Banking & Payment"),
            IranianAppEntry("ir.BluBank", "Blu Bank", "Banking & Payment"),
            IranianAppEntry("com.myirancell", "MyIrancell", "Telecom & Payment"),
            IranianAppEntry("ir.mci.ecareapp", "MyMCI", "Telecom & Payment"),
            IranianAppEntry("com.digikala", "Digikala", "Marketplace"),
            IranianAppEntry("ir.divar", "Divar", "Marketplace"),
            IranianAppEntry("ir.basalam.app", "Basalam", "Marketplace"),
            IranianAppEntry("net.sheypoor.mobile", "Sheypoor", "Marketplace"),
            IranianAppEntry("cab.snapp.passenger.play", "Snapp!", "Ride-Hailing"),
            IranianAppEntry("ir.tapsi.passenger", "Tapsi", "Ride-Hailing"),
            IranianAppEntry("com.zf.snappfood", "Snappfood", "Delivery"),
            IranianAppEntry("ir.eitaa.messenger", "Eitaa", "Messenger"),
            IranianAppEntry("ir.resaneh1.iptv", "Rubika", "Messenger"),
            IranianAppEntry("ir.nasim", "Bale", "Messenger"),
            IranianAppEntry("mobi.mmdt.ott", "Soroush+", "Messenger"),
            IranianAppEntry("com.gapafzar.messenger", "Gap", "Messenger"),
            IranianAppEntry("ir.gov.my", "MyGov.ir", "Government"),
            IranianAppEntry("com.farsitel.bazaar", "Cafe Bazaar", "App Store"),
            IranianAppEntry("com.aparat", "Aparat", "Streaming"),
            IranianAppEntry("ir.filimo.android", "Filimo", "Streaming"),
            IranianAppEntry("ir.namava.mobile", "Namava", "Streaming")
        )
    }

    fun getInstalledIranianApps(): List<IranianAppEntry> {
        val pm = context.packageManager
        return CURATED_IRANIAN_PACKAGES.map { entry ->
            val installed = runCatching { pm.getPackageInfo(entry.packageName, 0); true }.getOrDefault(false)
            entry.copy(isInstalled = installed)
        }
    }

    fun computeDisallowedPackages(
        bypassIranEnabled: Boolean,
        splitTunnelMode: SplitTunnelMode,
        userSelectedApps: Set<String>,
        customAppRules: List<CustomRuleEntity>
    ): Set<String> {
        val disallowed = mutableSetOf<String>()
        customAppRules
            .filter { it.matchType == RuleMatchType.PACKAGE_NAME && it.outboundAction == RuleAction.DIRECT }
            .forEach { disallowed.add(it.value.trim()) }
        if (bypassIranEnabled) {
            CURATED_IRANIAN_PACKAGES.forEach { disallowed.add(it.packageName) }
        }
        if (splitTunnelMode == SplitTunnelMode.EXCLUDE_SELECTED) {
            disallowed.addAll(userSelectedApps)
        }
        return disallowed
    }
}