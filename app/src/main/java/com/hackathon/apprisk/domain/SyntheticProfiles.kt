package com.hackathon.apprisk.domain

import com.hackathon.apprisk.domain.model.InstallerKind
import com.hackathon.apprisk.domain.model.RiskBand
import com.hackathon.apprisk.domain.model.SpecialAccess

data class SyntheticProfile(
    val name: String,
    val description: String,
    val expectedBand: RiskBand,
    val profile: RiskProfile,
)

object SyntheticProfiles {
    private const val SMS_READ = "android.permission.READ_SMS"
    private const val SMS_RECEIVE = "android.permission.RECEIVE_SMS"
    private const val SMS_SEND = "android.permission.SEND_SMS"
    private const val CONTACTS = "android.permission.READ_CONTACTS"
    private const val CALL_LOG = "android.permission.READ_CALL_LOG"
    private const val INTERNET = "android.permission.INTERNET"
    private const val BOOT = "android.permission.RECEIVE_BOOT_COMPLETED"
    private const val CAMERA = "android.permission.CAMERA"
    private const val AUDIO = "android.permission.RECORD_AUDIO"
    private const val BG_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"
    private const val FG_SERVICE = "android.permission.FOREGROUND_SERVICE"

    val all = listOf(
        SyntheticProfile(
            "Fake Torch",
            "Synthetic profile — sideloaded app with messaging and contact access.",
            RiskBand.CRITICAL,
            RiskProfile(
                "demo.fake.torch", "Fake Torch",
                grantedPermissions = setOf(SMS_READ, SMS_RECEIVE, SMS_SEND, CONTACTS, CALL_LOG, INTERNET),
                installerKind = InstallerKind.SIDELOADED,
                simpleUtilityLabel = "flashlight",
            ),
        ),
        SyntheticProfile(
            "Fake Banking Trojan",
            "Synthetic profile — accessibility and overlay are enabled together.",
            RiskBand.CRITICAL,
            RiskProfile(
                "demo.fake.banking", "Fake Banking Trojan",
                specialAccess = SpecialAccess(accessibilityEnabled = true, overlayGranted = true),
            ),
        ),
        SyntheticProfile(
            "Clean Notes",
            "Synthetic profile — no sensitive permissions or special access.",
            RiskBand.LOW,
            RiskProfile("demo.clean.notes", "Clean Notes"),
        ),
        SyntheticProfile(
            "Rogue Device Admin",
            "Synthetic profile — active device administrator outside the trusted list.",
            RiskBand.HIGH,
            RiskProfile(
                "demo.rogue.admin", "Rogue Device Admin",
                specialAccess = SpecialAccess(deviceAdminActive = true),
            ),
        ),
        SyntheticProfile(
            "Spy Kit",
            "Synthetic profile — sensor access, background work, internet and reboot persistence.",
            RiskBand.CRITICAL,
            RiskProfile(
                "demo.spy.kit", "Spy Kit",
                grantedPermissions = setOf(CAMERA, AUDIO, BG_LOCATION, FG_SERVICE, INTERNET, BOOT),
                hasLauncherIcon = false,
            ),
        ),
        SyntheticProfile(
            "Play Store Social",
            "Synthetic profile — ordinary social app permissions installed from Google Play.",
            RiskBand.LOW,
            RiskProfile(
                "demo.play.social", "Play Store Social",
                grantedPermissions = setOf(INTERNET, "android.permission.ACCESS_COARSE_LOCATION"),
                installerKind = InstallerKind.GOOGLE_PLAY,
            ),
        ),
        SyntheticProfile(
            "Security Tool",
            "Synthetic profile — recognized password manager on the trusted allowlist.",
            RiskBand.LOW,
            RiskProfile(
                "com.x8bit.bitwarden", "Security Tool",
                specialAccess = SpecialAccess(deviceAdminActive = true),
                installerKind = InstallerKind.GOOGLE_PLAY,
            ),
        ),
        SyntheticProfile(
            "Update Adds Permissions",
            "Synthetic profile — an update added sensitive permission declarations.",
            RiskBand.LOW,
            RiskProfile(
                "demo.permission.update", "Update Adds Permissions",
                grantedPermissions = setOf(INTERNET),
                hasSensitivePermissionDiff = true,
            ),
        ),
    )
}
