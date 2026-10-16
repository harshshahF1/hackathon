package com.hackathon.apprisk.data.scanner

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.hackathon.apprisk.domain.PermissionCatalog
import com.hackathon.apprisk.domain.RiskEngine
import com.hackathon.apprisk.domain.model.AppRecord
import com.hackathon.apprisk.domain.model.InstallerKind
import com.hackathon.apprisk.domain.model.PermissionCategory
import com.hackathon.apprisk.domain.model.PermissionGrant
import com.hackathon.apprisk.domain.model.StaticChecks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanProgress(val current: Int, val total: Int, val label: String)

object AppScanner {
    private const val FLAGS = PackageManager.GET_PERMISSIONS or
        PackageManager.GET_ACTIVITIES or
        PackageManager.GET_SERVICES or
        PackageManager.GET_RECEIVERS or
        PackageManager.GET_PROVIDERS

    suspend fun scan(
        context: Context,
        includeSystemApps: Boolean,
        onlyPackage: String? = null,
        onProgress: (ScanProgress) -> Unit = {},
    ): List<AppRecord> = withContext(Dispatchers.Default) {
        val packageManager = context.packageManager
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        SpecialAccessDetector.refreshDeviceState(context)
        val packages = installedPackages(packageManager).filter { info ->
            (onlyPackage == null || info.packageName == onlyPackage) &&
                (includeSystemApps || !isSystemApp(info.applicationInfo))
        }
        val results = ArrayList<AppRecord>(packages.size)
        packages.forEachIndexed { index, packageInfo ->
            onProgress(ScanProgress(index + 1, packages.size, packageInfo.packageName))
            try {
                val record = inspect(context, packageManager, appOps, packageInfo)
                if (record.packageName != context.packageName) {
                    results += record.copy(risk = RiskEngine.evaluate(record))
                }
            } catch (_: Exception) {
                // One broken package must not cancel the rest of the device scan.
            }
        }
        results
    }

    private fun installedPackages(packageManager: PackageManager): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(FLAGS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(FLAGS)
        }

    private fun inspect(
        context: Context,
        packageManager: PackageManager,
        appOps: AppOpsManager,
        packageInfo: PackageInfo,
    ): AppRecord {
        val applicationInfo = packageInfo.applicationInfo ?: throw IllegalStateException("Missing application information")
        val requested = packageInfo.requestedPermissions.orEmpty()
        val flags = packageInfo.requestedPermissionsFlags ?: intArrayOf()
        val permissions = requested.mapIndexed { index, name ->
            val requestedGranted = index < flags.size &&
                (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            SpecialAccessDetector.permissionGrant(context, packageInfo, appOps, name, requestedGranted)
        }.toMutableList()
        val declaredServices = SpecialAccessDetector.declaredServicePermissions(packageInfo)
        declaredServices.forEach { servicePermission ->
            if (permissions.none { it.name == servicePermission.name }) permissions += servicePermission
        }
        val specialAccess = SpecialAccessDetector.inspect(context, packageInfo, appOps)
        updateSpecialServiceGrants(permissions, specialAccess)
        val launcherClass = launcherActivity(packageManager, packageInfo.packageName)
        val installer = installer(packageManager, packageInfo.packageName)
        val record = AppRecord(
            packageName = packageInfo.packageName,
            appName = try {
                packageManager.getApplicationLabel(applicationInfo).toString()
            } catch (_: Exception) {
                packageInfo.packageName
            },
            versionName = packageInfo.versionName ?: "—",
            versionCode = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else @Suppress("DEPRECATION") packageInfo.versionCode.toLong(),
            firstInstallTime = packageInfo.firstInstallTime,
            lastUpdateTime = packageInfo.lastUpdateTime,
            installerSource = installer.first,
            installerKind = installer.second,
            targetSdk = applicationInfo.targetSdkVersion,
            isSystemApp = isSystemApp(applicationInfo),
            hasLauncherIcon = launcherClass != null,
            appCategory = if (Build.VERSION.SDK_INT >= 26) applicationInfo.category else ApplicationInfo.CATEGORY_UNDEFINED,
            permissions = permissions.sortedWith(compareBy({ it.category.ordinal }, { it.title.lowercase() })),
            specialAccess = specialAccess,
            staticChecks = staticChecks(packageInfo, applicationInfo, launcherClass),
        )
        return record
    }

    private fun updateSpecialServiceGrants(
        permissions: MutableList<PermissionGrant>,
        special: com.hackathon.apprisk.domain.model.SpecialAccess,
    ) {
        val actualStates = mapOf(
            "android.permission.BIND_ACCESSIBILITY_SERVICE" to special.accessibilityEnabled,
            "android.permission.BIND_DEVICE_ADMIN" to special.deviceAdminActive,
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" to special.notificationListenerEnabled,
        )
        actualStates.forEach { (name, isEnabled) ->
            val index = permissions.indexOfFirst { it.name == name }
            if (index >= 0) permissions[index] = permissions[index].copy(granted = isEnabled, grantKnown = true)
        }
    }

    private fun installer(packageManager: PackageManager, packageName: String): Pair<String, InstallerKind> {
        val installerPackage = try {
            if (Build.VERSION.SDK_INT >= 30) {
                val source = packageManager.getInstallSourceInfo(packageName)
                source.initiatingPackageName ?: source.installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
        } catch (_: Exception) {
            null
        }
        return when (installerPackage) {
            "com.android.vending" -> "Google Play" to InstallerKind.GOOGLE_PLAY
            "com.amazon.venezia" -> "Amazon Appstore" to InstallerKind.KNOWN_STORE
            "com.sec.android.app.samsungapps" -> "Galaxy Store" to InstallerKind.KNOWN_STORE
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.google.android.apps.nbu.files",
            -> "Sideloaded (APK file)" to InstallerKind.SIDELOADED
            null -> "Unknown / ADB / sideloaded" to InstallerKind.UNKNOWN
            else -> "Sideloaded (APK file)" to InstallerKind.SIDELOADED
        }
    }

    private fun isSystemApp(info: ApplicationInfo): Boolean =
        (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
            (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

    /**
     * Returns the class name of the app's MAIN/LAUNCHER activity, or null when the app has no
     * launcher icon (a hidden app). Queries the launcher category directly instead of relying on
     * getLaunchIntentForPackage, which can also return null for apps that only expose a
     * LEANBACK launcher or are disabled.
     */
    @Suppress("DEPRECATION")
    private fun launcherActivity(packageManager: PackageManager, packageName: String): String? {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
        return try {
            val matches = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                packageManager.queryIntentActivities(query, 0)
            }
            matches.firstOrNull()?.activityInfo?.name
        } catch (_: Exception) {
            null
        }
    }

    private fun staticChecks(
        packageInfo: PackageInfo,
        applicationInfo: ApplicationInfo,
        launcherClass: String?,
    ): StaticChecks {
        val exported = mutableListOf<String>()
        packageInfo.activities.orEmpty().forEach { activity ->
            if (activity.exported && activity.permission == null && activity.name != launcherClass) {
                exported += "Activity: ${activity.name}"
            }
        }
        packageInfo.services.orEmpty().forEach { service ->
            if (service.exported && service.permission == null) exported += "Service: ${service.name}"
        }
        packageInfo.receivers.orEmpty().forEach { receiver ->
            if (receiver.exported && receiver.permission == null) exported += "Receiver: ${receiver.name}"
        }
        packageInfo.providers.orEmpty().forEach { provider ->
            if (provider.exported && provider.readPermission == null && provider.writePermission == null) {
                exported += "Provider: ${provider.name}"
            }
        }
        val customNormal = packageInfo.permissions.orEmpty().filter { permission ->
            permission.name?.startsWith("android.") != true &&
                (permission.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE) ==
                android.content.pm.PermissionInfo.PROTECTION_NORMAL
        }.mapNotNull { it.name }
        return StaticChecks(
            debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            cleartextTraffic = Build.VERSION.SDK_INT >= 23 &&
                applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0,
            exportedUnprotectedComponents = exported.take(100),
            allowBackup = applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0,
            customNormalPermissions = customNormal,
        )
    }
}
