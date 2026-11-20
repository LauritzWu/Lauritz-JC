package io.nekohasekai.sagernet.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object UpdateApk {
    private const val MAX_BYTES = 128L * 1024 * 1024
    private val client = OkHttpClient.Builder()
        .followSslRedirects(false)
        .callTimeout(3, TimeUnit.MINUTES)
        .build()

    suspend fun download(context: Context, url: HttpUrl, build: Long): File =
        runInterruptible(Dispatchers.IO) {
            val directory = File(context.cacheDir, "app-updates")
            directory.mkdirs()
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
            directory.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
            val apk = File.createTempFile("veyr-", ".apk", directory)
            var verified = false
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    val body = response.body ?: throw IOException("Empty APK response")
                    if (!response.isSuccessful || !response.request.url.isHttps || body.contentLength() > MAX_BYTES) {
                        throw IOException("Invalid APK response")
                    }
                    body.byteStream().use { input ->
                        apk.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var total = 0L
                            while (true) {
                                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > MAX_BYTES) throw IOException("APK exceeds download limit")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
                verify(context, apk, build)
                verified = true
                apk
            } finally {
                if (!verified) apk.delete()
            }
        }

    @Suppress("DEPRECATION")
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.cache", apk)
        context.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    @Suppress("DEPRECATION")
    private fun verify(context: Context, apk: File, build: Long) {
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES
        val installed = manager.getPackageInfo(context.packageName, flags)
        val candidate = manager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw IOException("Invalid APK")
        val installedSigners = signers(installed)
        if (candidate.packageName != installed.packageName ||
            PackageInfoCompat.getLongVersionCode(candidate) != build ||
            !ReleaseUpdate.isUpgrade(build, PackageInfoCompat.getLongVersionCode(installed)) ||
            installedSigners.isEmpty() || installedSigners != signers(candidate)
        ) {
            throw IOException("APK identity, build or signature mismatch")
        }
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<Signature> =
        if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners.orEmpty().toSet()
        else info.signatures.orEmpty().toSet()
}
