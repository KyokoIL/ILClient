package app.ilclient.client.core.routing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeoDataUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val httpClient = OkHttpClient()

    companion object {
        const val GEOSITE_URL = "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat"
        const val GEOSITE_SHA256_URL = "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat.sha256sum"
        const val GEOIP_URL = "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat"
        const val GEOIP_SHA256_URL = "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat.sha256sum"
    }

    suspend fun updateGeoDatabases(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            downloadAndVerify(GEOSITE_URL, GEOSITE_SHA256_URL, File(context.filesDir, "geosite.dat"))
            downloadAndVerify(GEOIP_URL, GEOIP_SHA256_URL, File(context.filesDir, "geoip.dat"))
            "Updated & SHA-256 verified"
        }
    }

    private fun downloadAndVerify(fileUrl: String, shaUrl: String, targetFile: File) {
        val expectedSha = httpClient.newCall(Request.Builder().url(shaUrl).build()).execute().use { resp ->
            resp.body?.string()?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.lowercase()
                ?: throw IllegalStateException("Empty checksum")
        }
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        httpClient.newCall(Request.Builder().url(fileUrl).build()).execute().use { resp ->
            resp.body?.byteStream()?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val actualSha = sha256Hex(tempFile)
        if (!actualSha.equals(expectedSha, ignoreCase = true)) {
            tempFile.delete()
            throw SecurityException("SHA-256 mismatch for ${targetFile.name}")
        }
        tempFile.renameTo(targetFile)
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}