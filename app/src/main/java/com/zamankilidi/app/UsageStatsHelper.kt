package com.zamankilidi.app

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

/**
 * "İzin verilen uygulamalar" listesini son kullanılana ya da en çok
 * kullanılana göre sıralayabilmek için Android'in Kullanım Erişimi (Usage
 * Access) iznini kullanır.
 *
 * Bu izin, kilit mekanizmasının çalışması için GEREKLİ DEĞİL - sadece listeyi
 * daha kullanışlı sıralamak için. İzin verilmezse liste alfabetik sıralanmaya
 * devam eder, hiçbir çekirdek özellik bu izne bağlı değildir.
 */
object UsageStatsHelper {

    data class UsageInfo(val lastTimeUsed: Long, val totalForegroundMs: Long)

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    /**
     * packageName -> (son kullanım zamanı, son 14 gündeki toplam ön plan
     * süresi) haritası. İzin yoksa boş harita döner.
     */
    fun getUsageMap(context: Context): Map<String, UsageInfo> {
        if (!hasUsageAccess(context)) return emptyMap()

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        val end = System.currentTimeMillis()
        val start = end - 14L * 24 * 60 * 60 * 1000

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
            ?: return emptyMap()

        val result = HashMap<String, UsageInfo>()
        for (s in stats) {
            if (s.totalTimeInForeground <= 0L && s.lastTimeUsed <= 0L) continue
            val existing = result[s.packageName]
            val lastUsed = maxOf(existing?.lastTimeUsed ?: 0L, s.lastTimeUsed)
            val totalForeground = (existing?.totalForegroundMs ?: 0L) + s.totalTimeInForeground
            result[s.packageName] = UsageInfo(lastUsed, totalForeground)
        }
        return result
    }
}
