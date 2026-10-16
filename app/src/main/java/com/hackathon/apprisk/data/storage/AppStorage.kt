package com.hackathon.apprisk.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.hackathon.apprisk.domain.PermissionCatalog
import com.hackathon.apprisk.domain.RiskEngine
import com.hackathon.apprisk.domain.model.AppRecord
import com.hackathon.apprisk.domain.model.PermissionDiff
import com.hackathon.apprisk.domain.model.RiskBand
import com.hackathon.apprisk.domain.model.SecurityAlert
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AppStorage(context: Context) {
    private val systemDark: Boolean =
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("app-risk-auditor", Context.MODE_PRIVATE)
    private val snapshotsKey = "package_snapshots"
    private val alertsKey = "security_alerts"

    fun includeSystemApps(): Boolean = prefs.getBoolean("include_system_apps", false)
    fun setIncludeSystemApps(value: Boolean) = prefs.edit().putBoolean("include_system_apps", value).apply()
    fun darkTheme(): Boolean = prefs.getBoolean("dark_theme", systemDark)
    fun setDarkTheme(value: Boolean) = prefs.edit().putBoolean("dark_theme", value).apply()
    fun monitoringEnabled(): Boolean = prefs.getBoolean("monitor_installs", true)
    fun setMonitoringEnabled(value: Boolean) = prefs.edit().putBoolean("monitor_installs", value).apply()
    fun onboardingComplete(): Boolean = prefs.getBoolean("onboarding_complete", false)
    fun setOnboardingComplete(value: Boolean) = prefs.edit().putBoolean("onboarding_complete", value).apply()
    fun hashLookupConsented(): Boolean = prefs.getBoolean("hash_lookup_consented", false)
    fun setHashLookupConsented(value: Boolean) = prefs.edit().putBoolean("hash_lookup_consented", value).apply()
    fun hashEndpoint(): String = prefs.getString("hash_endpoint", DEFAULT_HASH_ENDPOINT) ?: DEFAULT_HASH_ENDPOINT
    fun setHashEndpoint(value: String) = prefs.edit().putString("hash_endpoint", value.trim()).apply()
    fun lastScanTime(): Long = prefs.getLong("last_scan_time", 0L)
    fun setLastScanTime(value: Long) = prefs.edit().putLong("last_scan_time", value).apply()

    @Synchronized
    fun mergeScan(
        context: Context,
        records: List<AppRecord>,
        notify: Boolean,
    ): List<AppRecord> {
        val old = readSnapshots()
        val isBaseline = !prefs.getBoolean("snapshot_baseline_saved", false)
        val updated = records.map { record ->
            val prior = old.optJSONObject(record.packageName)
            if (prior == null) {
                if (!isBaseline && notify) notifyForNewPackage(context, record)
                record
            } else {
                val diff = compare(prior, record)
                val withDiff = record.copy(
                    permissionDiff = diff,
                    risk = RiskEngine.evaluate(record.copy(permissionDiff = diff)),
                )
                if (notify) {
                    val isUpdate = record.versionCode != prior.optLong("versionCode") ||
                        record.lastUpdateTime != prior.optLong("lastUpdateTime")
                    val type = if (diff.hasSensitiveAddition) "NEW_PERMISSIONS" else if (isUpdate) "UPDATE" else null
                    if (type != null && withDiff.risk.band in setOf(RiskBand.HIGH, RiskBand.CRITICAL)) {
                        val added = (diff.added + diff.newlyGranted).distinct().take(3)
                            .joinToString(", ") { it.substringAfterLast('.') }
                        val message = if (type == "NEW_PERMISSIONS" && added.isNotBlank()) {
                            "${record.appName} update added: $added"
                        } else {
                            "${record.appName} scored ${withDiff.risk.band.label} (${withDiff.risk.score}/100)"
                        }
                        addAlert(
                            context,
                            SecurityAlert(
                                id = UUID.randomUUID().toString(),
                                packageName = record.packageName,
                                appName = record.appName,
                                type = type,
                                score = withDiff.risk.score,
                                band = withDiff.risk.band,
                                message = message,
                                createdAt = System.currentTimeMillis(),
                            ),
                            notifyUser = true,
                        )
                    }
                }
                withDiff
            }
        }
        val next = JSONObject()
        updated.forEach { record -> next.put(record.packageName, snapshotJson(record)) }
        prefs.edit()
            .putString(snapshotsKey, next.toString())
            .putBoolean("snapshot_baseline_saved", true)
            .putLong("last_scan_time", System.currentTimeMillis())
            .apply()
        return updated
    }

    @Synchronized
    fun processSinglePackage(
        context: Context,
        record: AppRecord,
        eventType: String,
        notify: Boolean,
    ): AppRecord {
        val old = readSnapshots()
        val prior = old.optJSONObject(record.packageName)
        val diff = if (prior == null) PermissionDiff() else compare(prior, record)
        val withDiff = record.copy(
            permissionDiff = diff,
            risk = RiskEngine.evaluate(record.copy(permissionDiff = diff)),
        )
        if (notify && withDiff.risk.band in setOf(RiskBand.HIGH, RiskBand.CRITICAL)) {
            val actualType = if (diff.hasSensitiveAddition) "NEW_PERMISSIONS" else eventType
            val message = if (actualType == "NEW_PERMISSIONS") {
                val names = (diff.added + diff.newlyGranted).distinct().take(3).map { it.substringAfterLast('.') }
                "${record.appName} update added: ${names.joinToString(", ")}"
            } else {
                "${record.appName} scored ${withDiff.risk.band.label} (${withDiff.risk.score}/100): ${withDiff.risk.reasons.firstOrNull()?.text.orEmpty()}"
            }
            addAlert(
                context,
                SecurityAlert(
                    id = UUID.randomUUID().toString(),
                    packageName = record.packageName,
                    appName = record.appName,
                    type = actualType,
                    score = withDiff.risk.score,
                    band = withDiff.risk.band,
                    message = message,
                    createdAt = System.currentTimeMillis(),
                ),
                notifyUser = true,
            )
        }
        old.put(record.packageName, snapshotJson(withDiff))
        prefs.edit().putString(snapshotsKey, old.toString()).apply()
        return withDiff
    }

    fun alerts(): List<SecurityAlert> {
        val json = try {
            JSONArray(prefs.getString(alertsKey, "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
        return (0 until json.length()).mapNotNull { index ->
            runCatching {
                val item = json.getJSONObject(index)
                val band = RiskBand.entries.firstOrNull { it.label == item.optString("band") } ?: RiskBand.LOW
                SecurityAlert(
                    id = item.optString("id"),
                    packageName = item.optString("packageName"),
                    appName = item.optString("appName"),
                    type = item.optString("type"),
                    score = item.optInt("score"),
                    band = band,
                    message = item.optString("message"),
                    createdAt = item.optLong("createdAt"),
                )
            }.getOrNull()
        }.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun removePackageSnapshot(packageName: String) {
        val snapshots = readSnapshots()
        snapshots.remove(packageName)
        prefs.edit().putString(snapshotsKey, snapshots.toString()).apply()
    }

    private fun notifyForNewPackage(context: Context, record: AppRecord) {
        // System apps appear/disappear from snapshots when the "include system apps" toggle changes,
        // so they must never be reported as freshly installed.
        if (record.isSystemApp) return
        if (!monitoringEnabled() || record.risk.band !in setOf(RiskBand.HIGH, RiskBand.CRITICAL)) return
        addAlert(
            context,
            SecurityAlert(
                id = UUID.randomUUID().toString(),
                packageName = record.packageName,
                appName = record.appName,
                type = "NEW_APP",
                score = record.risk.score,
                band = record.risk.band,
                message = "${record.appName} scored ${record.risk.band.label} (${record.risk.score}/100)",
                createdAt = System.currentTimeMillis(),
            ),
            notifyUser = true,
        )
    }

    private fun compare(old: JSONObject, record: AppRecord): PermissionDiff {
        val oldDeclared = old.optJSONArray("declared").toStringSet()
        val oldGranted = old.optJSONArray("granted").toStringSet()
        val currentDeclared = record.declaredPermissions
        val currentGranted = record.grantedPermissions
        val oldSpecial = old.optJSONObject("special")
        val oldSpecialGranted = oldSpecial?.let { saved ->
            buildSet {
                if (saved.optBoolean("accessibility")) add("android.permission.BIND_ACCESSIBILITY_SERVICE")
                if (saved.optBoolean("deviceAdmin")) add("android.permission.BIND_DEVICE_ADMIN")
                if (saved.optBoolean("notificationListener")) add("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")
                if (saved.optBoolean("overlay")) add("android.permission.SYSTEM_ALERT_WINDOW")
                if (saved.optBoolean("installPackages")) add("android.permission.REQUEST_INSTALL_PACKAGES")
                if (saved.optBoolean("usageStats")) add("android.permission.PACKAGE_USAGE_STATS")
                if (saved.optBoolean("manageStorage")) add("android.permission.MANAGE_EXTERNAL_STORAGE")
            }
        } ?: emptySet()
        val currentSpecialGranted = buildSet {
            if (record.specialAccess.accessibilityEnabled) add("android.permission.BIND_ACCESSIBILITY_SERVICE")
            if (record.specialAccess.deviceAdminActive) add("android.permission.BIND_DEVICE_ADMIN")
            if (record.specialAccess.notificationListenerEnabled) add("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")
            if (record.specialAccess.overlayGranted) add("android.permission.SYSTEM_ALERT_WINDOW")
            if (record.specialAccess.installPackagesGranted) add("android.permission.REQUEST_INSTALL_PACKAGES")
            if (record.specialAccess.usageStatsGranted) add("android.permission.PACKAGE_USAGE_STATS")
            if (record.specialAccess.manageStorageGranted) add("android.permission.MANAGE_EXTERNAL_STORAGE")
        }
        val oldAllGranted = oldGranted + oldSpecialGranted
        val currentAllGranted = currentGranted + currentSpecialGranted
        val added = currentDeclared - oldDeclared
        val removed = oldDeclared - currentDeclared
        val newlyGranted = currentAllGranted - oldAllGranted
        return PermissionDiff(
            added = added.filterTo(mutableSetOf()) { PermissionCatalog.isSensitive(it) },
            removed = removed.filterTo(mutableSetOf()) { PermissionCatalog.isSensitive(it) },
            newlyGranted = newlyGranted.filterTo(mutableSetOf()) { PermissionCatalog.isSensitive(it) },
        )
    }

    private fun snapshotJson(record: AppRecord) = JSONObject().apply {
        put("versionCode", record.versionCode)
        put("lastUpdateTime", record.lastUpdateTime)
        put("declared", JSONArray(record.declaredPermissions.sorted()))
        put("granted", JSONArray(record.grantedPermissions.sorted()))
        put("score", record.risk.score)
        put("band", record.risk.band.label)
        put(
            "special",
            JSONObject().apply {
                put("accessibility", record.specialAccess.accessibilityEnabled)
                put("deviceAdmin", record.specialAccess.deviceAdminActive)
                put("notificationListener", record.specialAccess.notificationListenerEnabled)
                put("overlay", record.specialAccess.overlayGranted)
                put("installPackages", record.specialAccess.installPackagesGranted)
                put("usageStats", record.specialAccess.usageStatsGranted)
                put("manageStorage", record.specialAccess.manageStorageGranted)
            },
        )
    }

    private fun readSnapshots(): JSONObject = try {
        JSONObject(prefs.getString(snapshotsKey, "{}"))
    } catch (_: Exception) {
        JSONObject()
    }

    private fun JSONArray?.toStringSet(): Set<String> =
        if (this == null) emptySet() else (0 until length()).mapTo(mutableSetOf()) { getString(it) }

    private fun addAlert(context: Context, alert: SecurityAlert, notifyUser: Boolean) {
        val all = alerts().toMutableList()
        all.removeAll { it.packageName == alert.packageName && it.type == alert.type && it.message == alert.message }
        all.add(0, alert)
        val array = JSONArray()
        all.take(200).forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("packageName", item.packageName)
                    put("appName", item.appName)
                    put("type", item.type)
                    put("score", item.score)
                    put("band", item.band.label)
                    put("message", item.message)
                    put("createdAt", item.createdAt)
                },
            )
        }
        prefs.edit().putString(alertsKey, array.toString()).apply()
        if (notifyUser) AlertNotifier.notify(context, alert)
    }

    companion object {
        const val DEFAULT_HASH_ENDPOINT = "https://mb-api.abuse.ch/api/v1/"
    }
}
