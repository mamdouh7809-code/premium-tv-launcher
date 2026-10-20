package com.premiumtv.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import java.text.Collator
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

class InstalledApps(context: Context) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var job: Future<*>? = null

    @Suppress("DEPRECATION")
    fun refresh(locale: Locale, callback: (Result<List<AppEntry>>) -> Unit) {
        val token = ++generation
        job?.cancel(true)
        job = worker.submit {
            val result = try {
                val apps = LinkedHashMap<String, AppEntry>()
                // Prefer the television front door if an app exposes both.
                listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).forEach { category ->
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
                    pm.queryIntentActivities(intent, 0).forEach { resolved ->
                        val info = resolved.activityInfo ?: return@forEach
                        if (info.packageName != appContext.packageName && info.exported &&
                            info.enabled && info.applicationInfo.enabled && info.packageName !in apps) {
                            val stamp = try { pm.getPackageInfo(info.packageName, 0).lastUpdateTime }
                                        catch (_: PackageManager.NameNotFoundException) { 0L }
                            val label = try { resolved.loadLabel(pm).toString() }
                                        catch (_: RuntimeException) { info.packageName }
                            apps[info.packageName] = AppEntry(info.packageName, info.name, label,
                                category == Intent.CATEGORY_LEANBACK_LAUNCHER, stamp)
                        }
                    }
                }
                val collator = Collator.getInstance(locale)
                Result.success(apps.values.sortedWith { a, b ->
                    val labelOrder = collator.compare(a.label, b.label)
                    if (labelOrder != 0) labelOrder else a.packageName.compareTo(b.packageName)
                })
            } catch (error: Exception) {
                Result.failure(error)
            }
            main.post { if (token == generation) callback(result) }
        }
    }

    fun close() { generation++; job?.cancel(true); worker.shutdownNow(); main.removeCallbacksAndMessages(null) }
}
