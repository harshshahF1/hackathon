package com.hackathon.apprisk.domain

import android.content.pm.ApplicationInfo
import com.hackathon.apprisk.domain.model.AppRecord
import com.hackathon.apprisk.domain.model.AppReason
import com.hackathon.apprisk.domain.model.InstallerKind
import com.hackathon.apprisk.domain.model.PermissionCategory
import com.hackathon.apprisk.domain.model.RiskBand
import com.hackathon.apprisk.domain.model.RiskResult
import com.hackathon.apprisk.domain.model.SpecialAccess
import com.hackathon.apprisk.domain.model.StaticChecks
import kotlin.math.roundToInt

data class RiskProfile(
    val packageName: String,
    val appName: String,
    val grantedPermissions: Set<String> = emptySet(),
    val declaredPermissions: Set<String> = grantedPermissions,
    val specialAccess: SpecialAccess = SpecialAccess(),
    val installerKind: InstallerKind = InstallerKind.UNKNOWN,
    val installerSource: String = "Unknown / ADB / sideloaded",
    val targetSdk: Int = 34,
    val hasLauncherIcon: Boolean = true,
    val appCategory: Int = -1,
    val simpleUtilityLabel: String? = null,
    val isSystemApp: Boolean = false,
    val staticChecks: StaticChecks = StaticChecks(),
    val hasSensitivePermissionDiff: Boolean = false,
    val knownThreatFingerprint: Boolean = false,
)

object RiskEngine {
    private const val INTERNET = "android.permission.INTERNET"
    private const val BOOT = "android.permission.RECEIVE_BOOT_COMPLETED"
    private const val SMS_READ = "android.permission.READ_SMS"
    private const val SMS_RECEIVE = "android.permission.RECEIVE_SMS"
    private const val SMS_SEND = "android.permission.SEND_SMS"
    private const val CONTACTS = "android.permission.READ_CONTACTS"
    private const val CALL_LOG = "android.permission.READ_CALL_LOG"
    private const val CAMERA = "android.permission.CAMERA"
    private const val AUDIO = "android.permission.RECORD_AUDIO"
    private const val BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"
    private const val FOREGROUND_SERVICE = "android.permission.FOREGROUND_SERVICE"
    private const val INSTALL_PACKAGES = "android.permission.REQUEST_INSTALL_PACKAGES"
    private const val FULL_STORAGE = "android.permission.MANAGE_EXTERNAL_STORAGE"

    private data class WeightedReason(
        val text: String,
        val points: Int,
        val ruleId: String,
    )

    fun evaluate(record: AppRecord): RiskResult = evaluate(
        RiskProfile(
            packageName = record.packageName,
            appName = record.appName,
            grantedPermissions = record.grantedPermissions,
            declaredPermissions = record.declaredPermissions,
            specialAccess = record.specialAccess,
            installerKind = record.installerKind,
            installerSource = record.installerSource,
            targetSdk = record.targetSdk,
            hasLauncherIcon = record.hasLauncherIcon,
            appCategory = record.appCategory,
            isSystemApp = record.isSystemApp,
            staticChecks = record.staticChecks,
            hasSensitivePermissionDiff = record.permissionDiff.hasSensitiveAddition,
            knownThreatFingerprint = record.knownThreatFingerprint,
        ),
    )

    fun evaluate(profile: RiskProfile): RiskResult {
        if (profile.packageName == "com.hackathon.apprisk" ||
            profile.packageName == "com.hackathon.apprisk.debug"
        ) {
            return RiskResult(0, RiskBand.LOW, emptyList())
        }

        val reasons = mutableListOf<WeightedReason>()
        var baseSubtotal = 0
        var combinationSubtotal = 0
        var postMultiplierSubtotal = 0

        fun granted(permission: String) = permission in profile.grantedPermissions
        fun declared(permission: String) = permission in profile.declaredPermissions
        fun addBase(text: String, points: Int, ruleId: String) {
            if (points != 0) {
                baseSubtotal += points
                reasons += WeightedReason(text, points, ruleId)
            }
        }
        fun addCombination(text: String, points: Int, ruleId: String) {
            combinationSubtotal += points
            reasons += WeightedReason(text, points, ruleId)
        }
        fun addPostMultiplier(text: String, points: Int, ruleId: String) {
            postMultiplierSubtotal += points
            reasons += WeightedReason(text, points, ruleId)
        }
        fun scorePermission(permission: String, points: Int) {
            if (points <= 0 || (!declared(permission) && !granted(permission))) return
            val isGranted = granted(permission)
            val effective = if (isGranted) points else (points * RiskWeights.DECLARED_ONLY_FACTOR).roundToInt()
            val explanation = PermissionCatalog.explanation(permission).removePrefix("Can ")
            addBase(
                if (isGranted) "Can $explanation" else "Declares permission to $explanation",
                effective,
                "PERM_${permission.substringAfterLast('.').uppercase()}",
            )
        }

        val special = profile.specialAccess
        listOf(
            Triple(special.accessibilityEnabled, special.accessibilityDeclared, RiskWeights.ACCESSIBILITY_ENABLED to "Accessibility access is enabled"),
            Triple(special.deviceAdminActive, special.deviceAdminDeclared, RiskWeights.DEVICE_ADMIN_ACTIVE to "Device administrator access is active"),
            Triple(special.notificationListenerEnabled, special.notificationListenerDeclared, RiskWeights.NOTIFICATION_LISTENER_ENABLED to "Can read notifications, including private message previews"),
            Triple(special.overlayGranted, special.overlayDeclared, RiskWeights.OVERLAY_GRANTED to "Can display content over other apps"),
            Triple(special.installPackagesGranted, special.installPackagesDeclared, RiskWeights.INSTALL_PACKAGES_GRANTED to "Can request installation of other apps"),
            Triple(special.usageStatsGranted, special.usageStatsDeclared, RiskWeights.USAGE_STATS_GRANTED to "Can see which apps you use and when"),
        ).forEachIndexed { index, entry ->
            val active = entry.first
            val declaredOnly = entry.second && !active
            if (active || declaredOnly) {
                val points = if (active) entry.third.first else (entry.third.first * RiskWeights.DECLARED_ONLY_FACTOR).roundToInt()
                val text = if (declaredOnly) "Declares special access: ${entry.third.second.lowercase()}" else entry.third.second
                addBase(text, points, "SPECIAL_${index + 1}")
            }
        }

        listOf(
            SMS_READ to RiskWeights.READ_SMS,
            SMS_RECEIVE to RiskWeights.RECEIVE_SMS,
            SMS_SEND to RiskWeights.SEND_SMS,
        ).forEach { (permission, points) ->
            scorePermission(permission, points)
        }
        listOf(
            "android.permission.READ_CONTACTS" to RiskWeights.READ_CONTACTS,
            "android.permission.READ_CALL_LOG" to RiskWeights.READ_CALL_LOG,
            "android.permission.WRITE_CONTACTS" to RiskWeights.WRITE_CONTACTS_OR_CALL_LOG,
            "android.permission.WRITE_CALL_LOG" to RiskWeights.WRITE_CONTACTS_OR_CALL_LOG,
        ).forEach { (permission, points) -> scorePermission(permission, points) }
        scorePermission(CAMERA, RiskWeights.CAMERA)
        scorePermission(AUDIO, RiskWeights.RECORD_AUDIO)
        listOf(
            "android.permission.ACCESS_FINE_LOCATION" to RiskWeights.ACCESS_FINE_LOCATION,
            "android.permission.ACCESS_COARSE_LOCATION" to RiskWeights.ACCESS_COARSE_LOCATION,
            BACKGROUND_LOCATION to RiskWeights.ACCESS_BACKGROUND_LOCATION,
        ).forEach { (permission, points) -> scorePermission(permission, points) }
        listOf(
            "android.permission.READ_PHONE_STATE" to RiskWeights.READ_PHONE_STATE,
            "android.permission.READ_PHONE_NUMBERS" to RiskWeights.READ_PHONE_NUMBERS,
            "android.permission.CALL_PHONE" to RiskWeights.CALL_PHONE,
        ).forEach { (permission, points) -> scorePermission(permission, points) }

        val mediaPermissions = setOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            "android.permission.READ_EXTERNAL_STORAGE",
        )
        val mediaPresent = mediaPermissions.filter { declared(it) || granted(it) }
        val mediaGrantedPoints = (mediaPresent.sumOf {
            if (granted(it)) RiskWeights.READ_MEDIA_OR_SHARED_STORAGE
            else (RiskWeights.READ_MEDIA_OR_SHARED_STORAGE * RiskWeights.DECLARED_ONLY_FACTOR).roundToInt()
        })
            .coerceAtMost(RiskWeights.STORAGE_MEDIA_CAP)
        if (mediaGrantedPoints > 0) {
            val effective = (mediaPresent.count { granted(it) } * RiskWeights.READ_MEDIA_OR_SHARED_STORAGE)
                .coerceAtMost(RiskWeights.STORAGE_MEDIA_CAP)
            val declaredOnly = (mediaGrantedPoints - effective).coerceAtLeast(0)
            if (effective > 0) addBase("Can access photos, videos or shared files", effective, "STORAGE_MEDIA")
            if (declaredOnly > 0) addBase("Declares access to photos, videos or shared files", declaredOnly, "STORAGE_MEDIA_DECLARED")
        }
        if (special.manageStorageGranted || declared(FULL_STORAGE)) {
            val points = if (special.manageStorageGranted) RiskWeights.MANAGE_EXTERNAL_STORAGE
            else (RiskWeights.MANAGE_EXTERNAL_STORAGE * RiskWeights.DECLARED_ONLY_FACTOR).roundToInt()
            addBase(
                if (special.manageStorageGranted) "Can manage files across shared storage" else "Declares permission to manage files across shared storage",
                points,
                "MANAGE_EXTERNAL_STORAGE",
            )
        }

        val backgroundPermissions = (listOf(
            BOOT to RiskWeights.RECEIVE_BOOT_COMPLETED,
            FOREGROUND_SERVICE to RiskWeights.FOREGROUND_SERVICE,
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to RiskWeights.IGNORE_BATTERY_OPTIMIZATIONS,
        ) + (profile.grantedPermissions + profile.declaredPermissions)
            .filter { it.startsWith("android.permission.FOREGROUND_SERVICE_") }
            .distinct()
            .map { it to RiskWeights.FOREGROUND_SERVICE })
        var backgroundPoints = 0
        backgroundPermissions.forEach { (permission, points) ->
            if (declared(permission) || granted(permission)) {
                backgroundPoints += if (granted(permission)) points else (points * RiskWeights.DECLARED_ONLY_FACTOR).roundToInt()
                reasons += WeightedReason(
                    if (granted(permission)) {
                        "Can ${PermissionCatalog.explanation(permission).removePrefix("Can ")}"
                    } else {
                        "Declares permission to ${PermissionCatalog.explanation(permission).removePrefix("Can ")}"
                    },
                    if (granted(permission)) points else (points * RiskWeights.DECLARED_ONLY_FACTOR).roundToInt(),
                    "BACKGROUND_${permission.substringAfterLast('.').uppercase()}",
                )
            }
        }
        val cappedBackground = backgroundPoints.coerceAtMost(RiskWeights.BACKGROUND_CAP)
        if (cappedBackground > 0) {
            baseSubtotal += cappedBackground
            val overflow = (backgroundPoints - cappedBackground).coerceAtLeast(0)
            if (overflow > 0) {
                var left = overflow
                for (index in reasons.indices.reversed()) {
                    val reason = reasons[index]
                    if (!reason.ruleId.startsWith("BACKGROUND_") || left == 0) continue
                    val reduction = minOf(reason.points, left)
                    reasons[index] = reason.copy(points = reason.points - reduction)
                    left -= reduction
                }
            }
        } else {
            reasons.removeAll { it.ruleId.startsWith("BACKGROUND_") }
        }
        scorePermission(INTERNET, RiskWeights.INTERNET)

        val knownStore = profile.installerKind == InstallerKind.GOOGLE_PLAY ||
            profile.installerKind == InstallerKind.KNOWN_STORE
        val sideloaded = profile.installerKind == InstallerKind.SIDELOADED ||
            profile.installerKind == InstallerKind.UNKNOWN
        val multiplier = when (profile.installerKind) {
            InstallerKind.GOOGLE_PLAY -> RiskWeights.GOOGLE_PLAY_MULTIPLIER
            InstallerKind.KNOWN_STORE -> RiskWeights.KNOWN_STORE_MULTIPLIER
            InstallerKind.SIDELOADED, InstallerKind.UNKNOWN -> RiskWeights.SIDELOAD_MULTIPLIER
        }
        val adjustedBase = (baseSubtotal * multiplier).roundToInt()

        val sms = granted(SMS_READ) || granted(SMS_RECEIVE)
        val internet = granted(INTERNET) || declared(INTERNET)
        val boot = granted(BOOT) || declared(BOOT)
        if (sms && internet && boot) {
            val points = when (profile.installerKind) {
                InstallerKind.SIDELOADED, InstallerKind.UNKNOWN -> RiskWeights.OTP_STEALER_SIDELOAD
                InstallerKind.GOOGLE_PLAY -> RiskWeights.OTP_STEALER_PLAY
                InstallerKind.KNOWN_STORE -> 0
            }
            if (points > 0) addCombination("OTP stealer pattern: message access, internet and reboot persistence", points, "R1")
        }
        if (special.accessibilityEnabled && special.overlayGranted) {
            addCombination("Banking-trojan pattern: accessibility and overlay access are both enabled", RiskWeights.BANKING_TROJAN, "R2")
        }
        val trusted = RiskWeights.isTrustedPackage(profile.packageName)
        if (special.deviceAdminActive && !trusted) {
            addCombination("Rogue device admin: this app has active device-admin control", RiskWeights.ROGUE_DEVICE_ADMIN, "R3")
        }
        if (special.notificationListenerEnabled && internet) {
            addCombination("OTP via notifications: can read notifications and connect to the internet", RiskWeights.OTP_NOTIFICATIONS, "R4")
            if (sms) addCombination("SMS access adds risk to notification-based OTP monitoring", RiskWeights.OTP_NOTIFICATIONS_SMS_BONUS, "R4_SMS")
        }
        if (special.installPackagesGranted && sideloaded) {
            addCombination("Silent installer: can install apps and was not installed from a known store", RiskWeights.SILENT_INSTALLER, "R5")
        }
        val cameraOrAudio = granted(CAMERA) || granted(AUDIO)
        val hasForegroundServiceType = (profile.grantedPermissions + profile.declaredPermissions)
            .any { it.startsWith("android.permission.FOREGROUND_SERVICE_") }
        val persistentWork = granted(BACKGROUND_LOCATION) || granted(FOREGROUND_SERVICE) ||
            declared(BACKGROUND_LOCATION) || declared(FOREGROUND_SERVICE) || hasForegroundServiceType
        if (cameraOrAudio && persistentWork && internet && boot) {
            addCombination("Spy kit pattern: sensors, persistent background work and internet access", RiskWeights.SPY_KIT, "R6")
        }
        if (granted(CONTACTS) && granted(CALL_LOG) && internet) {
            addCombination("Contact harvester pattern: contacts and call history can be sent online", RiskWeights.CONTACT_HARVESTER, "R7")
        }
        if ((special.manageStorageGranted || granted(FULL_STORAGE)) && internet && sideloaded) {
            addCombination("Full-storage access combined with internet access and sideloaded installation", RiskWeights.FULL_STORAGE_SIDELoad, "R8")
        }
        if ((granted(BACKGROUND_LOCATION) || declared(BACKGROUND_LOCATION)) &&
            (cameraOrAudio) && boot && !profile.hasLauncherIcon
        ) {
            addCombination("Stalkerware profile: hidden app with background location, sensor access and reboot persistence", RiskWeights.STALKERWARE_PROFILE, "R9")
        }
        val dangerousPermission = profile.declaredPermissions.any { permission ->
            PermissionCatalog.category(permission) in setOf(
                PermissionCategory.SMS,
                PermissionCategory.CONTACTS,
                PermissionCategory.CAMERA_MIC,
                PermissionCategory.LOCATION,
                PermissionCategory.PHONE,
            )
        }
        if (profile.targetSdk in 1..22 && dangerousPermission) {
            addCombination("Old target SDK: sensitive permissions may have been granted without modern prompts", RiskWeights.OLD_TARGET_SDK, "R10")
        }

        if (!profile.isSystemApp && !profile.hasLauncherIcon &&
            (profile.grantedPermissions + profile.declaredPermissions).any { PermissionCatalog.isSensitive(it) }
        ) {
            addPostMultiplier("Hidden app: this app has sensitive permissions but no launcher icon", RiskWeights.HIDDEN_SENSITIVE_APP, "HIDDEN_APP")
        }
        if (profile.hasSensitivePermissionDiff) {
            addPostMultiplier("An update added newly sensitive permissions", RiskWeights.PERMISSION_DIFF, "PERMISSION_DIFF")
        }
        if (profile.staticChecks.debuggable) {
            addPostMultiplier("App is marked debuggable", RiskWeights.DEBUGGABLE_APP, "MANIFEST_DEBUGGABLE")
        }
        if (profile.staticChecks.cleartextTraffic) {
            addPostMultiplier("Allows unencrypted HTTP traffic", RiskWeights.CLEARTEXT_TRAFFIC, "MANIFEST_CLEARTEXT")
        }
        val exportedPoints = (profile.staticChecks.exportedUnprotectedComponents.size * RiskWeights.EXPORTED_COMPONENT)
            .coerceAtMost(RiskWeights.EXPORTED_COMPONENTS_CAP)
        if (exportedPoints > 0) {
            addPostMultiplier("Has exported components without a permission requirement", exportedPoints, "MANIFEST_EXPORTED")
        }
        val mismatchPermission = (profile.grantedPermissions + profile.declaredPermissions)
            .firstOrNull { isContextMismatchPermission(it) }
        val utility = profile.simpleUtilityLabel ?: detectUtility(profile.appName, profile.packageName, profile.appCategory)
        if (utility != null && mismatchPermission != null) {
            addPostMultiplier(
                "A $utility app has no clear reason to ${mismatchAction(mismatchPermission)}",
                RiskWeights.CONTEXT_MISMATCH,
                "CONTEXT_MISMATCH",
            )
        }
        if (trusted) {
            addPostMultiplier("Recognised security/MDM tool", RiskWeights.TRUSTED_ALLOWLIST_ADJUSTMENT, "TRUSTED_ALLOWLIST")
        }

        val subtotal = adjustedBase + combinationSubtotal + postMultiplierSubtotal
        val finalScore = if (profile.knownThreatFingerprint) 100 else subtotal.coerceIn(0, 100)
        if (profile.knownThreatFingerprint) {
            reasons += WeightedReason("Matches a known-threat fingerprint", 100, "KNOWN_THREAT_HASH")
        }
        val band = bandForScore(finalScore)
        val scoredReasons = reasons
            .filter { it.points != 0 }
            .map { reason ->
                val severity = when {
                    reason.points < 0 -> RiskBand.LOW
                    reason.points >= 20 -> RiskBand.CRITICAL
                    reason.points >= 10 -> RiskBand.HIGH
                    reason.points >= 5 -> RiskBand.MEDIUM
                    else -> RiskBand.LOW
                }
                AppReason(reason.text, reason.points, severity, reason.ruleId)
            }
            .distinctBy { it.ruleId }
            .sortedWith(compareByDescending<AppReason> { it.points }.thenBy { it.text })
            .toMutableList()

        if (scoredReasons.isEmpty()) {
            scoredReasons += AppReason(
                "No elevated permission or special-access signals were detected",
                0,
                RiskBand.LOW,
                "BASELINE_NO_ELEVATED_SIGNALS",
            )
        }

        if (band == RiskBand.HIGH || band == RiskBand.CRITICAL) {
            val fallbackReasons = contributingPermissionReasons(profile)
            for (fallback in fallbackReasons) {
                if (scoredReasons.size >= 3) break
                if (scoredReasons.none { it.ruleId == fallback.ruleId }) scoredReasons += fallback
            }
            val specialFallbacks = listOf(
                special.accessibilityEnabled to AppReason("Accessibility can observe the screen and interact on the user's behalf", 0, RiskBand.LOW, "DETAIL_ACCESSIBILITY"),
                special.deviceAdminActive to AppReason("Device-admin control can enforce policies and delay removal", 0, RiskBand.LOW, "DETAIL_DEVICE_ADMIN"),
                special.notificationListenerEnabled to AppReason("Notification access can expose private message previews", 0, RiskBand.LOW, "DETAIL_NOTIFICATION_LISTENER"),
                special.overlayGranted to AppReason("Overlay access can place prompts over trusted apps", 0, RiskBand.LOW, "DETAIL_OVERLAY"),
                special.installPackagesGranted to AppReason("Install access can request additional apps", 0, RiskBand.LOW, "DETAIL_INSTALL_PACKAGES"),
                special.usageStatsGranted to AppReason("Usage access can reveal which apps are opened", 0, RiskBand.LOW, "DETAIL_USAGE_STATS"),
            ).filter { it.first }.map { it.second }
            for (fallback in specialFallbacks) {
                if (scoredReasons.size >= 3) break
                if (scoredReasons.none { it.ruleId == fallback.ruleId }) scoredReasons += fallback
            }
            val detailFallbacks = listOf(
                AppReason("Several app and device signals contributed to this elevated score", 0, RiskBand.LOW, "DETAIL_COMBINED_SIGNALS"),
                AppReason("Review the enabled access settings and permission list for this app", 0, RiskBand.LOW, "DETAIL_REVIEW_ACCESS"),
                AppReason("This score describes risk indicators and is not a malware verdict", 0, RiskBand.LOW, "DETAIL_SCORE_CONTEXT"),
            )
            for (fallback in detailFallbacks) {
                if (scoredReasons.size >= 3) break
                if (scoredReasons.none { it.ruleId == fallback.ruleId }) scoredReasons += fallback
            }
        }
        return RiskResult(finalScore, band, scoredReasons)
    }

    fun bandForScore(score: Int): RiskBand = when (score.coerceIn(0, 100)) {
        in 0..24 -> RiskBand.LOW
        in 25..49 -> RiskBand.MEDIUM
        in 50..74 -> RiskBand.HIGH
        else -> RiskBand.CRITICAL
    }

    fun detectUtility(label: String, packageName: String, appCategory: Int = ApplicationInfo.CATEGORY_UNDEFINED): String? {
        if (appCategory == ApplicationInfo.CATEGORY_GAME) return "game"
        val source = "$label $packageName".lowercase()
        val keywords = listOf(
            "flashlight" to listOf("flashlight", "torch"),
            "calculator" to listOf("calculator", "calc"),
            "wallpaper" to listOf("wallpaper"),
            "theme" to listOf("theme"),
            "compass" to listOf("compass"),
            "ruler" to listOf("ruler"),
            "clock" to listOf("clock", "alarm"),
            "QR scanner" to listOf("qr", "scanner"),
            "notes" to listOf("notes", "notepad"),
            "keyboard" to listOf("keyboard", "ime"),
            "launcher" to listOf("launcher", "home screen"),
            "battery utility" to listOf("battery", "cleaner", "booster"),
            "game" to listOf("game"),
            "ringtone app" to listOf("ringtone"),
            "sticker app" to listOf("sticker"),
        )
        return keywords.firstOrNull { (_, terms) -> terms.any(source::contains) }?.first
    }

    private fun isContextMismatchPermission(permission: String): Boolean =
        PermissionCatalog.category(permission) in setOf(
            PermissionCategory.SMS,
            PermissionCategory.CONTACTS,
        ) || permission == AUDIO || permission == BACKGROUND_LOCATION ||
            permission == "android.permission.BIND_ACCESSIBILITY_SERVICE" ||
            permission == "android.permission.BIND_DEVICE_ADMIN" ||
            permission == "android.permission.SYSTEM_ALERT_WINDOW"

    private fun mismatchAction(permission: String): String = when (PermissionCatalog.category(permission)) {
        PermissionCategory.SMS -> "read your text messages"
        PermissionCategory.CONTACTS -> "read contacts or call history"
        PermissionCategory.CAMERA_MIC -> "record audio"
        PermissionCategory.LOCATION -> "track your location in the background"
        else -> "use special device access"
    }

    private fun contributingPermissionReasons(profile: RiskProfile): List<AppReason> {
        val permissions = (profile.grantedPermissions + profile.declaredPermissions)
            .filter { PermissionCatalog.category(it) != PermissionCategory.OTHER }
            .distinct()
            .mapNotNull { permission ->
                val definition = PermissionCatalog.definition(permission) ?: return@mapNotNull null
                AppReason(
                    if (permission in profile.grantedPermissions) {
                        "Can ${definition.explanation.removePrefix("Can ")}"
                    } else {
                        "Declares permission to ${definition.explanation.removePrefix("Can ")}"
                    },
                    if (permission in profile.grantedPermissions) definition.baseWeight.coerceAtLeast(1) else 0,
                    RiskBand.LOW,
                    "DETAIL_${permission.substringAfterLast('.').uppercase()}",
                )
            }
        val installerReason = if (
            profile.installerKind == InstallerKind.SIDELOADED ||
            profile.installerKind == InstallerKind.UNKNOWN
        ) {
            listOf(AppReason("Installed from outside a recognized app store", 0, RiskBand.LOW, "DETAIL_INSTALLER"))
        } else {
            emptyList()
        }
        return (permissions + installerReason).sortedByDescending { it.points }
    }
}
