package com.hackathon.apprisk.domain

import com.hackathon.apprisk.domain.model.InstallerKind
import com.hackathon.apprisk.domain.model.RiskBand
import com.hackathon.apprisk.domain.model.SpecialAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskEngineTest {
    private val internet = "android.permission.INTERNET"
    private val boot = "android.permission.RECEIVE_BOOT_COMPLETED"
    private val readSms = "android.permission.READ_SMS"
    private val receiveSms = "android.permission.RECEIVE_SMS"
    private val sendSms = "android.permission.SEND_SMS"

    private fun profile(
        permissions: Set<String> = emptySet(),
        installer: InstallerKind = InstallerKind.UNKNOWN,
        special: SpecialAccess = SpecialAccess(),
        target: Int = 34,
        hidden: Boolean = false,
        declared: Set<String> = permissions,
        systemApp: Boolean = false,
        packageName: String = "test.app",
        diff: Boolean = false,
        knownHash: Boolean = false,
    ) = RiskProfile(
        packageName = packageName,
        appName = "Test",
        grantedPermissions = permissions,
        declaredPermissions = declared,
        specialAccess = special,
        installerKind = installer,
        targetSdk = target,
        hasLauncherIcon = !hidden,
        isSystemApp = systemApp,
        hasSensitivePermissionDiff = diff,
        knownThreatFingerprint = knownHash,
    )

    @Test
    fun `R1 sideloaded OTP pattern is named and weighted`() {
        val result = RiskEngine.evaluate(profile(setOf(readSms, internet, boot), InstallerKind.SIDELOADED))
        assertTrue(result.reasons.any { it.ruleId == "R1" && it.points == RiskWeights.OTP_STEALER_SIDELOAD })
    }

    @Test
    fun `R1 Play install uses the reduced bonus`() {
        val result = RiskEngine.evaluate(profile(setOf(receiveSms, internet, boot), InstallerKind.GOOGLE_PLAY))
        assertTrue(result.reasons.any { it.ruleId == "R1" && it.points == RiskWeights.OTP_STEALER_PLAY })
    }

    @Test
    fun `R2 accessibility and overlay pattern is named`() {
        val result = RiskEngine.evaluate(
            profile(special = SpecialAccess(accessibilityEnabled = true, overlayGranted = true)),
        )
        assertTrue(result.reasons.any { it.ruleId == "R2" })
    }

    @Test
    fun `R3 active unknown device admin is flagged`() {
        val result = RiskEngine.evaluate(profile(special = SpecialAccess(deviceAdminActive = true)))
        assertTrue(result.reasons.any { it.ruleId == "R3" })
    }

    @Test
    fun `trusted security tool avoids R3`() {
        val result = RiskEngine.evaluate(
            profile(
                special = SpecialAccess(deviceAdminActive = true),
                installer = InstallerKind.GOOGLE_PLAY,
                packageName = "com.x8bit.bitwarden",
            ),
        )
        assertTrue(result.reasons.none { it.ruleId == "R3" })
        assertTrue(result.reasons.any { it.ruleId == "TRUSTED_ALLOWLIST" })
    }

    @Test
    fun `R4 notification listener and internet is flagged`() {
        val result = RiskEngine.evaluate(
            profile(
                permissions = setOf(internet, readSms),
                special = SpecialAccess(notificationListenerEnabled = true),
            ),
        )
        assertTrue(result.reasons.any { it.ruleId == "R4" })
        assertTrue(result.reasons.any { it.ruleId == "R4_SMS" })
    }

    @Test
    fun `R5 sideloaded installer permission is flagged`() {
        val result = RiskEngine.evaluate(
            profile(
                permissions = setOf("android.permission.REQUEST_INSTALL_PACKAGES"),
                special = SpecialAccess(installPackagesGranted = true),
                installer = InstallerKind.SIDELOADED,
            ),
        )
        assertTrue(result.reasons.any { it.ruleId == "R5" })
    }

    @Test
    fun `R6 sensor spy pattern is flagged`() {
        val result = RiskEngine.evaluate(
            profile(setOf("android.permission.CAMERA", internet, boot, "android.permission.FOREGROUND_SERVICE")),
        )
        assertTrue(result.reasons.any { it.ruleId == "R6" })
    }

    @Test
    fun `R7 contacts and call history pattern is flagged`() {
        val result = RiskEngine.evaluate(
            profile(setOf("android.permission.READ_CONTACTS", "android.permission.READ_CALL_LOG", internet)),
        )
        assertTrue(result.reasons.any { it.ruleId == "R7" })
    }

    @Test
    fun `R8 full storage sideload combination is flagged`() {
        val result = RiskEngine.evaluate(
            profile(
                permissions = setOf("android.permission.MANAGE_EXTERNAL_STORAGE", internet),
                installer = InstallerKind.SIDELOADED,
                special = SpecialAccess(manageStorageGranted = true),
            ),
        )
        assertTrue(result.reasons.any { it.ruleId == "R8" })
    }

    @Test
    fun `R9 hidden app with background sensors is flagged`() {
        val result = RiskEngine.evaluate(
            profile(
                permissions = setOf("android.permission.ACCESS_BACKGROUND_LOCATION", "android.permission.CAMERA", boot),
                hidden = true,
            ),
        )
        assertTrue(result.reasons.any { it.ruleId == "R9" })
    }

    @Test
    fun `R10 old target with dangerous permission is flagged`() {
        val result = RiskEngine.evaluate(profile(setOf(readSms), target = 22))
        assertTrue(result.reasons.any { it.ruleId == "R10" })
    }

    @Test
    fun `24 is Low`() {
        assertEquals(RiskBand.LOW, RiskEngine.bandForScore(24))
    }

    @Test
    fun `25 is Medium`() {
        assertEquals(RiskBand.MEDIUM, RiskEngine.bandForScore(25))
    }

    @Test
    fun `49 is Medium`() {
        assertEquals(RiskBand.MEDIUM, RiskEngine.bandForScore(49))
    }

    @Test
    fun `50 is High`() {
        assertEquals(RiskBand.HIGH, RiskEngine.bandForScore(50))
    }

    @Test
    fun `74 is High`() {
        assertEquals(RiskBand.HIGH, RiskEngine.bandForScore(74))
    }

    @Test
    fun `75 is Critical`() {
        assertEquals(RiskBand.CRITICAL, RiskEngine.bandForScore(75))
    }

    @Test
    fun `high risk has at least three explanations`() {
        val result = RiskEngine.evaluate(profile(special = SpecialAccess(deviceAdminActive = true)))
        assertEquals(RiskBand.HIGH, result.band)
        assertTrue(result.reasons.size >= 3)
    }

    @Test
    fun `score is clamped to 100`() {
        val result = RiskEngine.evaluate(
            profile(
                permissions = setOf(
                    internet, boot, readSms, receiveSms, sendSms,
                    "android.permission.READ_CONTACTS",
                    "android.permission.READ_CALL_LOG",
                    "android.permission.CAMERA",
                    "android.permission.RECORD_AUDIO",
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.ACCESS_BACKGROUND_LOCATION",
                    "android.permission.READ_PHONE_STATE",
                    "android.permission.CALL_PHONE",
                    "android.permission.MANAGE_EXTERNAL_STORAGE",
                    "android.permission.RECEIVE_BOOT_COMPLETED",
                    "android.permission.FOREGROUND_SERVICE",
                    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                ),
                special = SpecialAccess(
                    accessibilityEnabled = true,
                    deviceAdminActive = true,
                    notificationListenerEnabled = true,
                    overlayGranted = true,
                    installPackagesGranted = true,
                    usageStatsGranted = true,
                    manageStorageGranted = true,
                ),
                installer = InstallerKind.SIDELOADED,
                hidden = true,
                knownHash = true,
            ),
        )
        assertEquals(100, result.score)
        assertEquals(RiskBand.CRITICAL, result.band)
    }

    @Test
    fun `internet permission alone remains Low`() {
        val result = RiskEngine.evaluate(profile(setOf(internet), installer = InstallerKind.SIDELOADED))
        assertTrue(result.score <= 24)
        assertEquals(RiskBand.LOW, result.band)
    }

    @Test
    fun `application never scores itself`() {
        val result = RiskEngine.evaluate(
            profile(
                permissions = setOf(readSms, sendSms, internet, boot),
                special = SpecialAccess(accessibilityEnabled = true, deviceAdminActive = true),
                packageName = "com.hackathon.apprisk",
            ),
        )
        assertEquals(0, result.score)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun `permission diff contributes its named reason`() {
        val result = RiskEngine.evaluate(profile(diff = true))
        assertTrue(result.reasons.any { it.ruleId == "PERMISSION_DIFF" })
    }

    @Test
    fun `declared but ungranted sensitive permission uses thirty percent`() {
        val result = RiskEngine.evaluate(
            profile(
                permissions = emptySet(),
                declared = setOf(readSms),
                installer = InstallerKind.KNOWN_STORE,
            ),
        )
        assertEquals(5, result.score)
        assertTrue(result.reasons.any { it.ruleId == "PERM_READ_SMS" && it.points == 5 })
    }

    @Test
    fun `foreground service variants are categorized and score in background`() {
        val permission = "android.permission.FOREGROUND_SERVICE_CAMERA"
        assertEquals(PermissionCategory.BACKGROUND, PermissionCatalog.category(permission))
        val result = RiskEngine.evaluate(profile(setOf(permission)))
        assertTrue(result.reasons.any { it.ruleId.startsWith("BACKGROUND_") })
    }

    @Test
    fun `empty inventory app still has a plain language explanation`() {
        val result = RiskEngine.evaluate(profile())
        assertTrue(result.reasons.isNotEmpty())
        assertTrue(result.reasons.first().text.contains("No elevated"))
    }

    @Test
    fun `hidden system apps do not get the hidden user app rule`() {
        val result = RiskEngine.evaluate(
            profile(
                permissions = setOf(readSms),
                hidden = true,
                systemApp = true,
            ),
        )
        assertTrue(result.reasons.none { it.ruleId == "HIDDEN_APP" })
    }
}
