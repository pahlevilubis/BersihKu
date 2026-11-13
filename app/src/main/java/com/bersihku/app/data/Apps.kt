package com.bersihku.app.data

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppInfo(
    val pkg: String,
    val label: String,
    val icon: ImageBitmap?,
    /** 0 = belum pernah dibuka (dalam catatan Android). */
    val lastUsed: Long,
    val installed: Long,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val system: Boolean,
) {
    val totalBytes: Long get() = appBytes + dataBytes
    val neverUsed: Boolean get() = lastUsed <= 0L
    val idleDays: Int
        get() {
            val since = if (neverUsed) installed else lastUsed
            return ((System.currentTimeMillis() - since) / DAY).toInt().coerceAtLeast(0)
        }
}

object Apps {

    @Suppress("DEPRECATION")
    suspend fun load(ctx: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.queryIntentActivities(launcher, 0)
        }
        val pkgs = resolved.map { it.activityInfo.packageName }.distinct().filter { it != ctx.packageName }

        val now = System.currentTimeMillis()
        val usage: Map<String, UsageStats> = try {
            val usm = ctx.getSystemService(UsageStatsManager::class.java)
            usm.queryAndAggregateUsageStats(now - 2 * 365 * DAY, now)
        } catch (e: Exception) {
            emptyMap()
        }
        val ssm = ctx.getSystemService(StorageStatsManager::class.java)
        val user = Process.myUserHandle()

        pkgs.mapNotNull { pkg ->
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                val pi = pm.getPackageInfo(pkg, 0)
                val stats = try {
                    ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, user)
                } catch (e: Exception) {
                    null
                }
                val u = usage[pkg]
                val last = if (u == null) 0L else maxOf(u.lastTimeUsed, u.lastTimeVisible)
                val icon = try {
                    pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap()
                } catch (e: Exception) {
                    null
                }
                AppInfo(
                    pkg = pkg,
                    label = ai.loadLabel(pm).toString(),
                    icon = icon,
                    lastUsed = if (last > 0L && last >= pi.firstInstallTime - DAY) last else 0L,
                    installed = pi.firstInstallTime,
                    appBytes = stats?.appBytes ?: 0L,
                    dataBytes = stats?.dataBytes ?: 0L,
                    cacheBytes = stats?.cacheBytes ?: 0L,
                    system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    @Suppress("DEPRECATION")
    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
