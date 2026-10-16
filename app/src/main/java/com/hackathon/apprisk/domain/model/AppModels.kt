package com.hackathon.apprisk.domain.model

enum class RiskBand(val label: String, val minScore: Int, val maxScore: Int) {
    LOW("Low", 0, 24),
    MEDIUM("Medium", 25, 49),
    HIGH("High", 50, 74),
    CRITICAL("Critical", 75, 100),
}

enum class PermissionCategory(val title: String) {
    SPECIAL("Special access"),
    SMS("SMS"),
    CONTACTS("Contacts & call logs"),
    CAMERA_MIC("Camera & microphone"),
    LOCATION("Location"),
    PHONE("Phone & identity"),
    STORAGE("Storage & media"),
    BACKGROUND("Background & persistence"),
    NETWORK("Network"),
    OTHER("Other"),
}

data class PermissionGrant(
    val name: String,
    val declared: Boolean = true,
    val granted: Boolean,
    val grantKnown: Boolean = true,
    val protectionLevel: String,
    val category: PermissionCategory,
    val title: String,
    val explanation: String,
    val weight: Int = 0,
)

data class SpecialAccess(
    val accessibilityEnabled: Boolean = false,
    val accessibilityDeclared: Boolean = false,
    val deviceAdminActive: Boolean = false,
    val deviceAdminDeclared: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val notificationListenerDeclared: Boolean = false,
    val overlayGranted: Boolean = false,
    val overlayDeclared: Boolean = false,
    val installPackagesGranted: Boolean = false,
    val installPackagesDeclared: Boolean = false,
    val usageStatsGranted: Boolean = false,
    val usageStatsDeclared: Boolean = false,
    val manageStorageGranted: Boolean = false,
    val manageStorageDeclared: Boolean = false,
    val writeSettingsGranted: Boolean = false,
    val vpnDeclared: Boolean = false,
) {
    fun anyGranted(): Boolean =
        accessibilityEnabled || deviceAdminActive || notificationListenerEnabled ||
            overlayGranted || installPackagesGranted || usageStatsGranted ||
            manageStorageGranted || writeSettingsGranted
}

data class StaticChecks(
    val debuggable: Boolean = false,
    val cleartextTraffic: Boolean = false,
    val exportedUnprotectedComponents: List<String> = emptyList(),
    val allowBackup: Boolean = false,
    val customNormalPermissions: List<String> = emptyList(),
)

data class AppReason(
    val text: String,
    val points: Int,
    val severity: RiskBand,
    val ruleId: String,
)

data class RiskResult(
    val score: Int,
    val band: RiskBand,
    val reasons: List<AppReason>,
)

data class PermissionDiff(
    val added: Set<String> = emptySet(),
    val removed: Set<String> = emptySet(),
    val newlyGranted: Set<String> = emptySet(),
) {
    val hasSensitiveAddition: Boolean
        get() = added.isNotEmpty() || newlyGranted.isNotEmpty()
}

data class AppRecord(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val installerSource: String,
    val installerKind: InstallerKind,
    val targetSdk: Int,
    val isSystemApp: Boolean,
    val hasLauncherIcon: Boolean,
    val appCategory: Int,
    val permissions: List<PermissionGrant>,
    val specialAccess: SpecialAccess,
    val staticChecks: StaticChecks = StaticChecks(),
    val permissionDiff: PermissionDiff = PermissionDiff(),
    val risk: RiskResult = RiskResult(0, RiskBand.LOW, emptyList()),
    val mlLikelihood: Int? = null,
    val knownThreatFingerprint: Boolean = false,
) {
    val grantedPermissions: Set<String>
        get() = permissions.filter { it.granted }.mapTo(mutableSetOf()) { it.name }

    val declaredPermissions: Set<String>
        get() = permissions.filter { it.declared }.mapTo(mutableSetOf()) { it.name }
}

enum class InstallerKind {
    GOOGLE_PLAY,
    KNOWN_STORE,
    SIDELOADED,
    UNKNOWN,
}

data class CategorySummary(
    val category: PermissionCategory,
    val appCountGranted: Int,
    val appCountDeclared: Int,
)

data class ScanSnapshot(
    val apps: List<AppRecord> = emptyList(),
    val scannedAt: Long = 0L,
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class SecurityAlert(
    val id: String,
    val packageName: String,
    val appName: String,
    val type: String,
    val score: Int,
    val band: RiskBand,
    val message: String,
    val createdAt: Long,
)
