package com.hackathon.apprisk.data.scanner

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import com.hackathon.apprisk.domain.PermissionCatalog
import com.hackathon.apprisk.domain.model.PermissionCategory
import com.hackathon.apprisk.domain.model.PermissionGrant
import com.hackathon.apprisk.domain.model.SpecialAccess

internal data class AppOpResult(val granted: Boolean, val known: Boolean)

internal object SpecialAccessDetector {
    private val accessibilityPackages = mutableSetOf<String>()
    private val adminPackages = mutableSetOf<String>()
    private val listenerPackages = mutableSetOf<String>()

    @Synchronized
    fun refreshDeviceState(context: Context) {
        accessibilityPackages.clear()
        adminPackages.clear()
        listenerPackages.clear()
        try {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .forEach { info -> info.resolveInfo?.serviceInfo?.packageName?.let(accessibilityPackages::add) }
        } catch (_: Exception) {
        }
        try {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            enabled.split(':').forEach { flattened ->
                ComponentName.unflattenFromString(flattened)?.packageName?.let(accessibilityPackages::add)
            }
        } catch (_: Exception) {
        }
        try {
            val policy = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            policy.activeAdmins.orEmpty().forEach { adminPackages += it.packageName }
        } catch (_: Exception) {
        }
        try {
            listenerPackages += NotificationManagerCompat.getEnabledListenerPackages(context)
        } catch (_: Exception) {
        }
    }

    fun inspect(
        context: Context,
        packageInfo: PackageInfo,
        appOps: AppOpsManager,
    ): SpecialAccess {
        val packageName = packageInfo.packageName
        val services = packageInfo.services.orEmpty()
        val receivers = packageInfo.receivers.orEmpty()
        val accessibilityDeclared = services.any { it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE" }
        val listenerDeclared = services.any { it.permission == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" }
        val adminDeclared = receivers.any { it.permission == "android.permission.BIND_DEVICE_ADMIN" }

        val overlay = queryOp(appOps, "android:system_alert_window", packageInfo)
        val install = queryOp(appOps, "android:request_install_packages", packageInfo)
        val usage = queryOp(appOps, "android:get_usage_stats", packageInfo)
        val manage = if (Build.VERSION.SDK_INT >= 30) {
            queryOp(appOps, "android:manage_external_storage", packageInfo)
        } else {
            AppOpResult(false, false)
        }
        val writeSettings = queryOp(appOps, "android:write_settings", packageInfo)
        return SpecialAccess(
            accessibilityEnabled = packageName in accessibilityPackages,
            accessibilityDeclared = accessibilityDeclared,
            deviceAdminActive = packageName in adminPackages,
            deviceAdminDeclared = adminDeclared,
            notificationListenerEnabled = packageName in listenerPackages,
            notificationListenerDeclared = listenerDeclared,
            overlayGranted = overlay.granted,
            overlayDeclared = requested(packageInfo, "android.permission.SYSTEM_ALERT_WINDOW"),
            installPackagesGranted = install.granted,
            installPackagesDeclared = requested(packageInfo, "android.permission.REQUEST_INSTALL_PACKAGES"),
            usageStatsGranted = usage.granted,
            usageStatsDeclared = requested(packageInfo, "android.permission.PACKAGE_USAGE_STATS"),
            manageStorageGranted = manage.granted,
            manageStorageDeclared = requested(packageInfo, "android.permission.MANAGE_EXTERNAL_STORAGE"),
            writeSettingsGranted = writeSettings.granted,
            vpnDeclared = services.any { it.permission == "android.permission.BIND_VPN_SERVICE" },
        )
    }

    fun permissionGrant(
        context: Context,
        packageInfo: PackageInfo,
        appOps: AppOpsManager,
        name: String,
        requestedGranted: Boolean,
    ): PermissionGrant {
        val definition = PermissionCatalog.definition(name)
        val isSpecial = PermissionCatalog.isSpecial(name) ||
            name in setOf(
                "android.permission.SYSTEM_ALERT_WINDOW",
                "android.permission.REQUEST_INSTALL_PACKAGES",
                "android.permission.PACKAGE_USAGE_STATS",
                "android.permission.MANAGE_EXTERNAL_STORAGE",
                "android.permission.WRITE_SETTINGS",
                "android.permission.SCHEDULE_EXACT_ALARM",
            )
        var granted = requestedGranted
        var grantKnown = true
        val op = when (name) {
            "android.permission.SYSTEM_ALERT_WINDOW" -> "android:system_alert_window"
            "android.permission.REQUEST_INSTALL_PACKAGES" -> "android:request_install_packages"
            "android.permission.PACKAGE_USAGE_STATS" -> "android:get_usage_stats"
            "android.permission.MANAGE_EXTERNAL_STORAGE" -> "android:manage_external_storage"
            "android.permission.WRITE_SETTINGS" -> "android:write_settings"
            else -> null
        }
        if (op != null) {
            val state = if (name == "android.permission.MANAGE_EXTERNAL_STORAGE" && Build.VERSION.SDK_INT < 30) {
                AppOpResult(false, false)
            } else {
                queryOp(appOps, op, packageInfo)
            }
            grantKnown = state.known
            granted = state.granted
        }
        val protection = try {
            val info = context.packageManager.getPermissionInfo(name, 0)
            val base = info.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE
            when {
                isSpecial || info.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_FLAG_APPOP != 0 ->
                    "SPECIAL"
                base == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS -> "DANGEROUS"
                base == android.content.pm.PermissionInfo.PROTECTION_SIGNATURE -> "SIGNATURE"
                base == android.content.pm.PermissionInfo.PROTECTION_NORMAL -> "NORMAL"
                else -> "CUSTOM / UNKNOWN"
            }
        } catch (_: Exception) {
            if (isSpecial) "SPECIAL" else "CUSTOM / UNKNOWN"
        }
        return PermissionGrant(
            name = name,
            declared = true,
            granted = granted,
            grantKnown = grantKnown,
            protectionLevel = protection,
            category = definition?.category ?: PermissionCategory.OTHER,
            title = definition?.title ?: PermissionCatalog.title(name),
            explanation = definition?.explanation ?: PermissionCatalog.explanation(name),
            weight = definition?.baseWeight ?: 0,
        )
    }

    fun declaredServicePermissions(packageInfo: PackageInfo): List<PermissionGrant> {
        val services: List<ServiceInfo> = packageInfo.services.orEmpty().toList()
        val names = services.mapNotNull { service ->
            service.permission?.takeIf {
                it == "android.permission.BIND_ACCESSIBILITY_SERVICE" ||
                    it == "android.permission.BIND_DEVICE_ADMIN" ||
                    it == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" ||
                    it == "android.permission.BIND_VPN_SERVICE"
            }
        }.distinct()
        return names.map { name ->
            PermissionGrant(
                name = name,
                declared = true,
                granted = false,
                grantKnown = false,
                protectionLevel = "SPECIAL",
                category = PermissionCategory.SPECIAL,
                title = PermissionCatalog.title(name),
                explanation = PermissionCatalog.explanation(name),
                weight = PermissionCatalog.baseWeight(name),
            )
        }
    }

    private fun requested(packageInfo: PackageInfo, name: String): Boolean =
        packageInfo.requestedPermissions.orEmpty().contains(name)

    private fun queryOp(appOps: AppOpsManager, operation: String, packageInfo: PackageInfo): AppOpResult =
        try {
            val mode = appOps.checkOpNoThrow(operation, packageInfo.applicationInfo?.uid ?: return AppOpResult(false, false), packageInfo.packageName)
            AppOpResult(mode == AppOpsManager.MODE_ALLOWED, true)
        } catch (_: Exception) {
            AppOpResult(false, false)
        }
}
