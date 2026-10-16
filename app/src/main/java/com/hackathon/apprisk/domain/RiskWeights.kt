package com.hackathon.apprisk.domain

object RiskWeights {
    const val DECLARED_ONLY_FACTOR = 0.30
    const val GOOGLE_PLAY_MULTIPLIER = 0.85
    const val KNOWN_STORE_MULTIPLIER = 1.0
    const val SIDELOAD_MULTIPLIER = 1.25
    const val TRUSTED_ALLOWLIST_ADJUSTMENT = -20
    const val PERMISSION_DIFF = 10
    const val HIDDEN_SENSITIVE_APP = 10
    const val CONTEXT_MISMATCH = 20

    const val ACCESSIBILITY_ENABLED = 30
    const val DEVICE_ADMIN_ACTIVE = 25
    const val NOTIFICATION_LISTENER_ENABLED = 20
    const val OVERLAY_GRANTED = 20
    const val INSTALL_PACKAGES_GRANTED = 15
    const val USAGE_STATS_GRANTED = 10

    const val READ_SMS = 15
    const val RECEIVE_SMS = 8
    const val SEND_SMS = 12
    const val READ_CONTACTS = 8
    const val READ_CALL_LOG = 10
    const val WRITE_CONTACTS_OR_CALL_LOG = 4
    const val CAMERA = 6
    const val RECORD_AUDIO = 8
    const val ACCESS_FINE_LOCATION = 6
    const val ACCESS_COARSE_LOCATION = 3
    const val ACCESS_BACKGROUND_LOCATION = 10
    const val READ_PHONE_STATE = 4
    const val READ_PHONE_NUMBERS = 4
    const val CALL_PHONE = 5
    const val READ_MEDIA_OR_SHARED_STORAGE = 4
    const val STORAGE_MEDIA_CAP = 8
    const val MANAGE_EXTERNAL_STORAGE = 12
    const val RECEIVE_BOOT_COMPLETED = 4
    const val FOREGROUND_SERVICE = 2
    const val IGNORE_BATTERY_OPTIMIZATIONS = 4
    const val INTERNET = 1

    const val OTP_STEALER_SIDELOAD = 30
    const val OTP_STEALER_PLAY = 12
    const val BANKING_TROJAN = 35
    const val ROGUE_DEVICE_ADMIN = 30
    const val OTP_NOTIFICATIONS = 15
    const val OTP_NOTIFICATIONS_SMS_BONUS = 10
    const val SILENT_INSTALLER = 20
    const val SPY_KIT = 20
    const val CONTACT_HARVESTER = 15
    const val FULL_STORAGE_SIDELoad = 15
    const val STALKERWARE_PROFILE = 25
    const val OLD_TARGET_SDK = 8

    const val DEBUGGABLE_APP = 8
    const val CLEARTEXT_TRAFFIC = 4
    const val EXPORTED_COMPONENT = 2
    const val EXPORTED_COMPONENTS_CAP = 10

    const val BACKGROUND_CAP = 8

    val bands = listOf(
        "Low" to 0..24,
        "Medium" to 25..49,
        "High" to 50..74,
        "Critical" to 75..100,
    )

    val installerMultipliers = listOf(
        "Google Play" to GOOGLE_PLAY_MULTIPLIER,
        "Other known store" to KNOWN_STORE_MULTIPLIER,
        "Sideloaded or unknown" to SIDELOAD_MULTIPLIER,
    )

    val specialAccessWeights = listOf(
        "Enabled accessibility service" to ACCESSIBILITY_ENABLED,
        "Active device administrator" to DEVICE_ADMIN_ACTIVE,
        "Enabled notification listener" to NOTIFICATION_LISTENER_ENABLED,
        "Granted overlay access" to OVERLAY_GRANTED,
        "Granted install-apps access" to INSTALL_PACKAGES_GRANTED,
        "Granted usage stats access" to USAGE_STATS_GRANTED,
    )

    val trustedPackagePrefixes = listOf(
        "com.google.android.apps.work.clouddpc",
        "com.microsoft.intune",
        "com.airwatch.androidagent",
        "com.lastpass.lpandroid",
        "com.x8bit.bitwarden",
        "com.kaspersky.",
        "com.avast.",
        "com.bitdefender.",
        "com.eset.",
        "com.lookout",
    )

    data class CombinationRule(
        val id: String,
        val name: String,
        val trigger: String,
        val points: Int,
    )

    val combinationRules = listOf(
        CombinationRule("R1", "OTP stealer pattern", "SMS read/receive + internet + boot persistence; sideloaded/unknown", OTP_STEALER_SIDELOAD),
        CombinationRule("R2", "Banking-trojan pattern", "Accessibility enabled + overlay granted", BANKING_TROJAN),
        CombinationRule("R3", "Rogue device admin", "Active device admin outside the trusted security/MDM list", ROGUE_DEVICE_ADMIN),
        CombinationRule("R4", "OTP via notifications", "Notification listener + internet; +10 when SMS is also granted", OTP_NOTIFICATIONS),
        CombinationRule("R5", "Silent installer", "Install packages granted + sideloaded installer", SILENT_INSTALLER),
        CombinationRule("R6", "Spy kit", "Camera/microphone + background location/foreground service + internet + boot persistence", SPY_KIT),
        CombinationRule("R7", "Contact harvester", "Contacts + call log + internet; SMS raises confidence", CONTACT_HARVESTER),
        CombinationRule("R8", "Full-storage access", "Manage all files + internet + sideloaded installer", FULL_STORAGE_SIDELoad),
        CombinationRule("R9", "Stalkerware profile", "Background location + camera/microphone + boot persistence + no launcher", STALKERWARE_PROFILE),
        CombinationRule("R10", "Old target SDK", "Target below API 23 with a dangerous permission", OLD_TARGET_SDK),
    )

    fun isTrustedPackage(packageName: String): Boolean =
        trustedPackagePrefixes.any { prefix ->
            if (prefix.endsWith('.')) packageName.startsWith(prefix) else packageName == prefix
        }
}
