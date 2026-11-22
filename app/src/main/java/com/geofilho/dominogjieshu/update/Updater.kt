package com.geofilho.dominogjieshu.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Atualização pelo próprio app: consulta a página de Releases do GitHub
 * (tag "ultima-versao"), baixa o APK novo e abre o instalador do Android.
 */
class Updater(private val context: Context) {
    private val base = "https://github.com/geofilho/dominogjieshu/releases/download/ultima-versao/"

    val installedVersion: Long
        get() = PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0),
        )

    val installedVersionName: String
        get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /** Novidades da versão publicada (texto do último commit). */
    suspend fun latestNotes(): String? = withContext(Dispatchers.IO) {
        runCatching {
            open(base + "novidades.txt").run {
                try { inputStream.bufferedReader().readText().trim() } finally { disconnect() }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun open(url: String): HttpURLConnection {
        var u = URL(url)
        // Segue redirecionamentos manualmente (o GitHub redireciona para outro domínio).
        repeat(5) {
            val c = u.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "DominoJieshuoG-app")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: error("redirecionamento sem destino")
                c.disconnect()
                u = URL(u, loc)
            } else {
                if (code != 200) { c.disconnect(); error("HTTP $code") }
                return c
            }
        }
        error("redirecionamentos demais")
    }

    /** Número da versão publicada (ou null se não deu para consultar). */
    suspend fun latestVersion(): Long? = withContext(Dispatchers.IO) {
        runCatching {
            open(base + "version.txt").run {
                try { inputStream.bufferedReader().readText().trim().toLong() } finally { disconnect() }
            }
        }.getOrNull()
    }

    /** Baixa o APK novo; [progress] recebe 0..100. */
    suspend fun download(progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "DominoJieshuoG.apk")
        val c = open(base + "DominoJieshuoG.apk")
        try {
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done * 100 / total).toInt()
                            if (p != last) { last = p; withContext(Dispatchers.Main) { progress(p) } }
                        }
                    }
                }
            }
        } finally { c.disconnect() }
        file
    }

    /** O Android exige permitir "instalar apps desconhecidos" para este app. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
