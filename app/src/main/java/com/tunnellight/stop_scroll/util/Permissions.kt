package com.tunnellight.stop_scroll.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import com.tunnellight.stop_scroll.service.ScrollAccessibilityService

object Permissions {

    /**
     * Reads the enabled-services list rather than trusting a flag the service sets, so the
     * answer is still right after the process has been killed and restarted, and after the
     * user has revoked the permission from Settings while the app was in the background.
     */
    fun isTrackingServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ScrollAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (entry in splitter) {
            val component = ComponentName.unflattenFromString(entry) ?: continue
            if (component == expected) return true
        }
        return false
    }

    fun openAccessibilitySettings(context: Context) =
        startSettings(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openUsageAccessSettings(context: Context) =
        startSettings(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    fun openNotificationSettings(context: Context) = startSettings(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )

    fun openAppSettings(context: Context) = startSettings(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null)),
    )

    private fun startSettings(context: Context, intent: Intent) {
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
