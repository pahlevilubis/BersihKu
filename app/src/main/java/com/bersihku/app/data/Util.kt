package com.bersihku.app.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.NumberFormat
import java.util.Locale

const val DAY = 24L * 60 * 60 * 1000

private val ID: Locale = Locale.forLanguageTag("id-ID")

fun formatSize(bytes: Long): String {
    val v = bytes.toDouble()
    return when {
        bytes >= 1_000_000_000L -> String.format(ID, "%.1f GB", v / 1e9)
        bytes >= 10_000_000L -> String.format(ID, "%.0f MB", v / 1e6)
        bytes >= 1_000_000L -> String.format(ID, "%.1f MB", v / 1e6)
        bytes >= 1_000L -> String.format(ID, "%.0f KB", v / 1e3)
        else -> "$bytes B"
    }
}

fun formatCount(n: Int): String = NumberFormat.getIntegerInstance(ID).format(n)

fun formatAgo(time: Long, now: Long = System.currentTimeMillis()): String {
    if (time <= 0L) return "tidak diketahui"
    val days = ((now - time) / DAY).toInt()
    return when {
        days <= 0 -> "hari ini"
        days == 1 -> "kemarin"
        days < 7 -> "$days hari lalu"
        days < 30 -> "${days / 7} minggu lalu"
        days < 365 -> "${days / 30} bulan lalu"
        else -> "${days / 365} tahun lalu"
    }
}

/** Dua nama folder terakhir, contoh: "WhatsApp Images › Sent". */
fun shortFolder(path: String, root: String): String {
    val rel = path.removePrefix(root).trim('/')
    if (rel.isEmpty()) return "Memori HP"
    val parts = rel.split('/')
    return if (parts.size <= 2) parts.joinToString(" › ") else "… › " + parts.takeLast(2).joinToString(" › ")
}

object Storage {
    @Suppress("DEPRECATION")
    val root: File get() = Environment.getExternalStorageDirectory()

    fun read(ctx: Context): StorageInfo = try {
        val ssm = ctx.getSystemService(StorageStatsManager::class.java)
        StorageInfo(ssm.getTotalBytes(StorageManager.UUID_DEFAULT), ssm.getFreeBytes(StorageManager.UUID_DEFAULT))
    } catch (e: Exception) {
        val st = StatFs(Environment.getDataDirectory().path)
        StorageInfo(st.totalBytes, st.availableBytes)
    }
}

/** Aturan pengaman: file & folder penting yang tidak boleh dipilih untuk dihapus. */
object Guard {
    private val mainFolders = setOf(
        "alarms", "android", "audiobooks", "dcim", "documents", "download", "movies", "music",
        "notifications", "pictures", "podcasts", "recordings", "ringtones",
    )

    fun isProtectedFile(root: String, path: String): Boolean {
        val rel = path.removePrefix(root).lowercase()
        return rel.endsWith("/.nomedia") ||
            rel.contains("/databases/") ||
            rel.contains("/backups/") ||
            rel.startsWith("/android/data/") ||
            rel.startsWith("/android/obb/")
    }

    fun isProtectedDir(root: String, path: String): Boolean {
        if (!path.startsWith("$root/")) return true
        val rel = path.removePrefix(root).lowercase()
        val depth = rel.count { it == '/' }
        if (depth == 1 && rel.removePrefix("/") in mainFolders) return true
        if (rel.startsWith("/android/") && depth <= 3) return true
        if (rel == "/dcim/camera") return true
        if (rel.contains("/databases") || rel.contains("/backups")) return true
        if (File(path, "Databases").exists() || File(path, "Backups").exists()) return true
        return false
    }
}

object Perms {
    fun hasFileAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    fun openAllFilesSettings(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
        } catch (e: Exception) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
            }
        }
    }

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            ctx.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun openUsageSettings(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        } catch (e: Exception) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    fun openAppInfo(ctx: Context, pkg: String) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        } catch (_: Exception) {
        }
    }

    fun uninstall(ctx: Context, pkg: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
        } catch (_: Exception) {
            openAppInfo(ctx, pkg)
        }
    }
}

/** Buka file di aplikasi lain (Galeri, pemutar video, dll) supaya bisa dicek dulu. */
fun openFile(ctx: Context, path: String, nameForType: String = path) {
    val f = File(path)
    try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val ext = nameForType.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "Tidak ada aplikasi untuk membuka file ini", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "File tidak bisa dibuka", Toast.LENGTH_SHORT).show()
    }
}

/** Nama file/folder yang belum dipakai, contoh: "foto (1).jpg". */
fun uniqueFile(f: File): File {
    if (!f.exists()) return f
    val base = if (f.isDirectory) f.name else f.nameWithoutExtension
    val ext = if (f.isDirectory) "" else f.extension
    var i = 1
    while (true) {
        val c = File(f.parentFile, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
        if (!c.exists()) return c
        i++
    }
}

/** Beri tahu Galeri bahwa file berubah (dihapus / dipindah / dikembalikan). */
fun scanMedia(ctx: Context, paths: List<String>) {
    if (paths.isEmpty()) return
    try {
        MediaScannerConnection.scanFile(ctx.applicationContext, paths.take(500).toTypedArray(), null, null)
    } catch (_: Exception) {
    }
}
