package com.tunnellight.stop_scroll.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import com.tunnellight.stop_scroll.analytics.Aggregator
import com.tunnellight.stop_scroll.data.model.AppCatalog
import java.util.concurrent.ConcurrentHashMap

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val installed: Boolean = true,
)

/**
 * Labels and launcher icons for the packages that show up in charts, cached because the rows
 * re-read them on every recomposition. Icon rasterisation happens once per package.
 */
class AppInfoProvider(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val cache = ConcurrentHashMap<String, AppInfo>()

    fun info(packageName: String): AppInfo = cache.getOrPut(packageName) {
        if (packageName == Aggregator.OTHER_PACKAGE) {
            return@getOrPut AppInfo(packageName, "Other apps", null)
        }
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            AppInfo(
                packageName = packageName,
                label = packageManager.getApplicationLabel(appInfo).toString(),
                icon = packageManager.getApplicationIcon(appInfo).toImageBitmap(),
            )
        } catch (_: PackageManager.NameNotFoundException) {
            AppInfo(
                packageName = packageName,
                label = AppCatalog.labelFor(packageName) ?: packageName.substringAfterLast('.'),
                icon = null,
                installed = false,
            )
        }
    }

    fun isInstalled(packageName: String): Boolean = info(packageName).installed

    /**
     * Every app with a launcher entry. Resolved through a `<queries>` launcher intent filter
     * in the manifest, which avoids asking for the far broader QUERY_ALL_PACKAGES permission.
     */
    fun launchableApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != appContext.packageName }
            .distinct()
            .map { info(it) }
            .sortedBy { it.label.lowercase() }
    }

    private fun Drawable.toImageBitmap(sizePx: Int = ICON_PX): ImageBitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, sizePx, sizePx)
        draw(canvas)
        return bitmap.asImageBitmap()
    }

    private companion object {
        const val ICON_PX = 144
    }
}
