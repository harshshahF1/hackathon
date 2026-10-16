package com.hackathon.apprisk.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hackathon.apprisk.data.scanner.ApkHashService
import com.hackathon.apprisk.data.scanner.HashLookupResult
import com.hackathon.apprisk.domain.MlScorer
import com.hackathon.apprisk.domain.PermissionCatalog
import com.hackathon.apprisk.domain.model.AppRecord
import com.hackathon.apprisk.domain.model.PermissionCategory
import com.hackathon.apprisk.domain.model.RiskBand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun AppDetailScreen(app: AppRecord?, vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val consent by vm.hashConsent.collectAsState()
    val endpoint by vm.hashEndpoint.collectAsState()
    val clipboard = LocalClipboardManager.current
    var showUninstallConfirmation by remember { mutableStateOf(false) }
    var showHashConsent by remember { mutableStateOf(false) }
    var hash by remember(app?.packageName, app?.lastUpdateTime) { mutableStateOf<String?>(null) }
    var hashLoading by remember { mutableStateOf(false) }
    var hashResult by remember { mutableStateOf<HashLookupResult?>(null) }
    var mlScore by remember(app?.packageName, app?.lastUpdateTime) { mutableStateOf<Int?>(null) }
    var technicalExpanded by remember { mutableStateOf(false) }
    var otherExpanded by remember { mutableStateOf(false) }
    var hashKnownLocally by remember(app?.packageName, app?.lastUpdateTime) { mutableStateOf(false) }

    if (app == null) {
        EmptyState("App is no longer available", "It may have been removed. Rescan the device to refresh the inventory.")
        return
    }

    LaunchedEffect(app.packageName, app.lastUpdateTime) {
        mlScore = withContext(Dispatchers.IO) { MlScorer.score(context, app) }
    }

    val threatMatch = app.knownThreatFingerprint || hashKnownLocally || hashResult?.status == "match"
    val displayedScore = if (threatMatch) 100 else app.risk.score
    val displayedBand = if (threatMatch) RiskBand.CRITICAL else app.risk.band

    fun computeHash() {
        if (hashLoading) return
        hashLoading = true
        scope.launch {
            val result = runCatching { ApkHashService.sha256(context, app) }
            hash = result.getOrNull()
            hashKnownLocally = hash?.let { isKnownSyntheticHash(context, it) } ?: false
            if (hashKnownLocally) vm.setHashVerdict(app.packageName, true)
            hashLoading = false
            if (result.isFailure) {
                hashResult = HashLookupResult("", "unavailable", "Could not read this APK fingerprint.")
            }
        }
    }

    fun performLookup() {
        if (hash == null) {
            computeHash()
            return
        }
        val fingerprint = hash ?: return
        hashLoading = true
        scope.launch {
            hashResult = ApkHashService.lookup(context, fingerprint, endpoint)
            val known = hashResult?.status == "match" || hashKnownLocally
            vm.setHashVerdict(app.packageName, known)
            hashLoading = false
        }
    }

    if (showUninstallConfirmation) {
        AlertDialog(
            onDismissRequest = { showUninstallConfirmation = false },
            title = { Text("Uninstall ${app.appName}?") },
            text = {
                Text(
                    if (app.specialAccess.deviceAdminActive) {
                        "This app is an active device administrator. Revoke its device-admin access first, then retry uninstalling."
                    } else {
                        "Android will ask you to confirm the uninstall. The app's data may be removed with it."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUninstallConfirmation = false
                        if (!app.specialAccess.deviceAdminActive) {
                            launchAppIntent(
                                context,
                                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")),
                                app.packageName,
                            )
                        } else {
                            launchAppIntent(context, Intent(Settings.ACTION_SECURITY_SETTINGS), app.packageName)
                        }
                    },
                ) { Text(if (app.specialAccess.deviceAdminActive) "Open security settings" else "Continue") }
            },
            dismissButton = { TextButton(onClick = { showUninstallConfirmation = false }) { Text("Cancel") } },
        )
    }

    if (showHashConsent) {
        AlertDialog(
            onDismissRequest = { showHashConsent = false },
            title = { Text("Allow one hash lookup?") },
            text = { Text("Only the file's SHA-256 fingerprint is sent to the configured lookup service. The app itself and your inventory are never uploaded.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showHashConsent = false
                        vm.setHashConsent(true)
                        if (hash == null) computeHash()
                        else performLookup()
                    },
                ) { Text("Allow and look up") }
            },
            dismissButton = { TextButton(onClick = { showHashConsent = false }) { Text("Cancel") } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, Modifier.size(68.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(app.appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        RiskPill(displayedBand, displayedScore)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Install date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(app.firstInstallTime)), fontWeight = FontWeight.Medium)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Last update", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(app.lastUpdateTime)), fontWeight = FontWeight.Medium)
                    }
                }
                KeyValue("Version", "${app.versionName} (${app.versionCode})")
                KeyValue("Installer", app.installerSource)
                KeyValue("Target SDK", app.targetSdk.toString())
                if (app.isSystemApp) AssistChip(onClick = {}, label = { Text("System app") })
            }
        }
        item {
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { displayedScore / 100f },
                            modifier = Modifier.size(82.dp),
                            strokeWidth = 7.dp,
                            color = riskColor(displayedBand),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(displayedScore.toString(), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Text("/100", fontSize = 10.sp)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${displayedBand.label} risk", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = riskColor(displayedBand))
                        Text("Rule-based score with named reasons. Same app state produces the same score.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (mlScore != null) {
                            Text("Experimental ML likelihood: $mlScore%", fontWeight = FontWeight.SemiBold)
                            Text("The rule score is the source of explanations.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        if (threatMatch) Text("Known-threat fingerprint match", color = riskColor(RiskBand.CRITICAL), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (app.risk.reasons.isNotEmpty() || threatMatch) {
            item {
                InfoCard {
                    Text("Why flagged", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    val reasons = if (threatMatch && app.risk.reasons.none { it.ruleId == "KNOWN_THREAT_HASH" }) {
                        app.risk.reasons + com.hackathon.apprisk.domain.model.AppReason(
                            "Matches a known-threat fingerprint",
                            100,
                            RiskBand.CRITICAL,
                            "KNOWN_THREAT_HASH",
                        )
                    } else app.risk.reasons
                    reasons.forEach { reason ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.padding(top = 5.dp).size(8.dp).background(riskColor(reason.severity), CircleShape))
                            Column(Modifier.weight(1f)) {
                                Text(reason.text)
                                Text("${reason.points} points · ${reason.ruleId}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                InfoCard {
                    Text("No high-risk patterns detected", fontWeight = FontWeight.SemiBold)
                    Text("This does not guarantee an app is safe. Review the permission list and only keep apps you recognize.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            InfoCard {
                Text("Special access status", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                StatusRow("Accessibility service", app.specialAccess.accessibilityEnabled, app.specialAccess.accessibilityDeclared)
                StatusRow("Device administrator", app.specialAccess.deviceAdminActive, app.specialAccess.deviceAdminDeclared)
                StatusRow("Notification listener", app.specialAccess.notificationListenerEnabled, app.specialAccess.notificationListenerDeclared)
                StatusRow("Display over other apps", app.specialAccess.overlayGranted, app.specialAccess.overlayDeclared)
                StatusRow("Install other apps", app.specialAccess.installPackagesGranted, app.specialAccess.installPackagesDeclared)
                StatusRow("Usage statistics", app.specialAccess.usageStatsGranted, app.specialAccess.usageStatsDeclared)
                StatusRow("Manage all files", app.specialAccess.manageStorageGranted, app.specialAccess.manageStorageDeclared)
                val shortcuts = specialShortcuts(app)
                if (shortcuts.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Shortcuts", fontWeight = FontWeight.SemiBold)
                    shortcuts.forEach { shortcut ->
                        OutlinedButton(
                            onClick = { launchSettings(context, shortcut.intent, app.packageName) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(shortcut.label) }
                    }
                }
            }
        }
        if (app.permissionDiff.hasSensitiveAddition || app.permissionDiff.removed.isNotEmpty()) {
            item {
                InfoCard {
                    Text("What changed", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Before: ${app.permissionDiff.removed.size + (app.declaredPermissions.size - app.permissionDiff.added.size)} declared permissions")
                    Text("Added: ${app.permissionDiff.added.joinToString().ifBlank { "None" }}")
                    Text("Newly granted: ${app.permissionDiff.newlyGranted.joinToString().ifBlank { "None" }}")
                    Text("Removed: ${app.permissionDiff.removed.joinToString().ifBlank { "None" }}")
                }
            }
        }
        item {
            InfoCard {
                Text("Recommended actions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                recommendedActions(app).forEachIndexed { index, action ->
                    Text("${index + 1}. $action")
                }
            }
        }
        item {
            InfoCard {
                Text("Permissions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                PermissionCatalog.categoryOrder().forEach { category ->
                    val permissions = app.permissions.filter { it.category == category }
                    Text(category.title, fontWeight = FontWeight.SemiBold)
                    if (permissions.isEmpty()) {
                        Text("No permissions in this category.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    } else {
                        permissions.forEach { PermissionRow(it) }
                    }
                    HorizontalDivider()
                }
                val other = app.permissions.filter { it.category == PermissionCategory.OTHER }
                if (other.isNotEmpty()) {
                    TextButton(onClick = { otherExpanded = !otherExpanded }) {
                        Text(if (otherExpanded) "Hide ${other.size} other permissions" else "Show ${other.size} other permissions")
                    }
                    if (otherExpanded) other.forEach { PermissionRow(it) }
                }
            }
        }
        item {
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("APK fingerprint", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Text("SHA-256 is computed locally on demand. It is not sent anywhere unless you choose a lookup.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hash != null) {
                    Text(hash.orEmpty(), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(hash.orEmpty())) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Copy")
                        }
                        Button(
                            onClick = {
                                if (consent) performLookup() else showHashConsent = true
                            },
                            enabled = !hashLoading,
                        ) { Text("Look up hash") }
                    }
                } else {
                    Button(onClick = ::computeHash, enabled = !hashLoading) {
                        Text(if (hashLoading) "Computing…" else "Compute SHA-256")
                    }
                }
                hashResult?.let { result ->
                    Text(result.detail, color = if (result.status == "match") riskColor(RiskBand.CRITICAL) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (hashKnownLocally) Text("Matches a synthetic local test fingerprint.", color = riskColor(RiskBand.CRITICAL))
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { technicalExpanded = !technicalExpanded }) {
                        Text(if (technicalExpanded) "Hide technical details" else "Show technical details")
                    }
                    if (technicalExpanded) {
                        KeyValue("Debuggable", if (app.staticChecks.debuggable) "Yes (+8)" else "No")
                        KeyValue("Cleartext HTTP", if (app.staticChecks.cleartextTraffic) "Allowed (+4)" else "Not declared")
                        KeyValue("Allow backup", if (app.staticChecks.allowBackup) "Enabled" else "Disabled")
                        KeyValue("Exported components", "${app.staticChecks.exportedUnprotectedComponents.size}")
                        app.staticChecks.exportedUnprotectedComponents.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        if (app.staticChecks.customNormalPermissions.isNotEmpty()) {
                            Text("Custom normal permissions: ${app.staticChecks.customNormalPermissions.joinToString()}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        launchAppIntent(
                            context,
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")),
                            app.packageName,
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("App settings")
                }
                OutlinedButton(onClick = { showUninstallConfirmation = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("Uninstall")
                }
            }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusRow(label: String, active: Boolean, declared: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(9.dp).background(
                when {
                    active -> Color(0xFF16825D)
                    declared -> Color(0xFFA06A00)
                    else -> Color(0xFF89968F)
                },
                CircleShape,
            ),
        )
        Spacer(Modifier.width(9.dp))
        Text(label, modifier = Modifier.weight(1f))
        Text(
            when {
                active -> "Enabled"
                declared -> "Capable, not enabled"
                else -> "Not detected"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PermissionRow(permission: com.hackathon.apprisk.domain.model.PermissionGrant) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(permission.title, fontWeight = FontWeight.Medium)
        Text(permission.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallTag(if (permission.granted) "GRANTED" else if (!permission.grantKnown) "DECLARED · STATE UNKNOWN" else "DECLARED")
            SmallTag(permission.protectionLevel)
            Text(permission.name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SmallTag(value: String) {
    Box(
        Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(100.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(value, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
    }
}

private data class Shortcut(val label: String, val intent: Intent)

private fun specialShortcuts(app: AppRecord): List<Shortcut> = buildList {
    if (app.specialAccess.accessibilityEnabled || app.specialAccess.accessibilityDeclared) {
        add(Shortcut("Open Accessibility settings", Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
    }
    if (app.specialAccess.deviceAdminActive || app.specialAccess.deviceAdminDeclared) {
        add(Shortcut("Open device security settings", Intent(Settings.ACTION_SECURITY_SETTINGS)))
    }
    if (app.specialAccess.notificationListenerEnabled || app.specialAccess.notificationListenerDeclared) {
        add(Shortcut("Open Notification access settings", Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
    }
    if (app.specialAccess.overlayGranted || app.specialAccess.overlayDeclared) {
        add(Shortcut("Open Display over other apps", Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${app.packageName}"))))
    }
    if (app.specialAccess.installPackagesGranted || app.specialAccess.installPackagesDeclared) {
        add(Shortcut("Open Install unknown apps", Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))))
    }
    if (app.specialAccess.usageStatsGranted || app.specialAccess.usageStatsDeclared) {
        add(Shortcut("Open Usage access settings", Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))
    }
    if (app.specialAccess.manageStorageGranted || app.specialAccess.manageStorageDeclared) {
        if (Build.VERSION.SDK_INT >= 30) {
            add(Shortcut("Open Manage all files", Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${app.packageName}"))))
        }
    }
    if (app.specialAccess.writeSettingsGranted) {
        add(Shortcut("Open Modify system settings", Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${app.packageName}"))))
    }
}

private fun recommendedActions(app: AppRecord): List<String> = buildList {
    if (app.specialAccess.accessibilityEnabled) add("Turn off Accessibility access if you do not rely on this app.")
    if (app.specialAccess.deviceAdminActive) add("Revoke device-admin access before trying to uninstall.")
    if (app.specialAccess.notificationListenerEnabled) add("Remove Notification access if this app does not need to read notifications.")
    if (app.specialAccess.overlayGranted) add("Turn off Display over other apps.")
    if (app.permissions.any { it.category == PermissionCategory.SMS && it.granted }) add("Revoke SMS access in the app's permission settings.")
    if (app.permissions.any { it.category == PermissionCategory.CONTACTS && it.granted }) add("Revoke Contacts and call-log access if it is not needed.")
    add("Uninstall the app if you do not recognize or trust it.")
}

private fun launchSettings(context: Context, intent: Intent, packageName: String) {
    launchAppIntent(context, intent, packageName)
}

private fun launchAppIntent(context: Context, intent: Intent, packageName: String) {
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ),
            )
        } catch (_: Exception) {
            Toast.makeText(context, "Android settings could not be opened on this device.", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun isKnownSyntheticHash(context: Context, hash: String): Boolean =
    runCatching {
        context.assets.open("known_bad_hashes.txt").bufferedReader().useLines { lines ->
            lines.map { it.substringBefore('#').trim().lowercase() }
                .filter { it.matches(Regex("[a-f0-9]{64}")) }
                .any { it == hash.lowercase() }
        }
    }.getOrDefault(false)
