package com.hackathon.apprisk.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.hackathon.apprisk.data.storage.AppStorage
import com.hackathon.apprisk.domain.PermissionCatalog
import com.hackathon.apprisk.domain.RiskWeights
import com.hackathon.apprisk.domain.SyntheticProfiles
import com.hackathon.apprisk.domain.model.AppRecord
import com.hackathon.apprisk.domain.model.PermissionCategory
import com.hackathon.apprisk.domain.model.RiskBand
import com.hackathon.apprisk.util.ReportExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun DashboardScreen(vm: AppViewModel, navController: NavController) {
    val snapshot by vm.snapshot.collectAsState()
    val progress by vm.progress.collectAsState()
    val includeSystem by vm.includeSystemApps.collectAsState()
    val onboardingComplete by vm.onboardingComplete.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) Toast.makeText(context, "Notifications enabled", Toast.LENGTH_SHORT).show()
        vm.completeOnboarding()
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    ReportExporter.writePdf(context, uri, snapshot.apps, snapshot.scannedAt)
                }
                Toast.makeText(context, if (ok) "PDF report saved" else "Could not save the PDF report", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    ReportExporter.writeJson(context, uri, snapshot.apps, snapshot.scannedAt)
                }
                Toast.makeText(context, if (ok) "JSON scan exported" else "Could not export JSON", Toast.LENGTH_SHORT).show()
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!onboardingComplete) {
            item {
                OnboardingCard(
                    onContinue = {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.completeOnboarding()
                        }
                    },
                    onDismiss = vm::completeOnboarding,
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Device overview", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("A local scan of apps and permissions on this device", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = vm::scan, enabled = !snapshot.isLoading) {
                    Text(if (snapshot.isLoading) "Scanning" else "Rescan")
                }
            }
        }
        item {
            if (snapshot.isLoading) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        progress?.let { "Checking ${it.current} of ${it.total}: ${it.label}" } ?: "Scanning installed apps…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (snapshot.error != null) {
                InfoCard {
                    Text("Scan could not finish", fontWeight = FontWeight.SemiBold)
                    Text(snapshot.error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = vm::scan) { Text("Try again") }
                }
            } else {
                RiskSummary(snapshot.apps)
            }
        }
        if (snapshot.scannedAt > 0) {
            item {
                Text(
                    "Last scan: ${DateFormat.getMediumDateFormat(context).format(Date(snapshot.scannedAt))} · ${DateFormat.getTimeFormat(context).format(Date(snapshot.scannedAt))}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Include system apps", fontWeight = FontWeight.SemiBold)
                    Text("Adds preinstalled apps to the inventory", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = includeSystem, onCheckedChange = vm::setIncludeSystemApps)
            }
        }
        item {
            SectionTitle("Permission categories", "Granted and declared apps")
        }
        items(vm.categorySummaries(), key = { it.category.name }) { summary ->
            CategorySummaryRow(summary) {
                navController.navigate("category/${summary.category.name}")
            }
        }
        item {
            SectionTitle("Top 5 riskiest apps", "Highest risk first")
        }
        if (snapshot.apps.isEmpty() && !snapshot.isLoading) {
            item { EmptyState("No apps to show", "Run a scan to create an app inventory.") }
        } else {
            items(snapshot.apps.sortedByDescending { it.risk.score }.take(5), key = { it.packageName }) { app ->
                AppRiskRow(app, onClick = { navController.navigate("app/${app.packageName}") })
            }
        }
        item {
            SectionTitle("Highest risk first")
        }
        items(snapshot.apps.sortedByDescending { it.risk.score }.take(10), key = { "highest-${it.packageName}" }) { app ->
            AppRiskRow(app, onClick = { navController.navigate("app/${app.packageName}") }, compact = true)
        }
        item {
            SectionTitle("Export scan")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { pdfLauncher.launch("AppRiskReport.pdf") }) { Text("Export report") }
                OutlinedButton(onClick = { jsonLauncher.launch("AppRiskScan.json") }) { Text("Export JSON") }
                OutlinedButton(
                    onClick = {
                        val intent = ReportExporter.createShareIntent(context, snapshot.apps, snapshot.scannedAt)
                        if (intent != null) {
                            runCatching { context.startActivity(Intent.createChooser(intent, "Share scan report")) }
                                .onFailure { Toast.makeText(context, "Could not share the PDF", Toast.LENGTH_SHORT).show() }
                        } else Toast.makeText(context, "Could not create the PDF", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("Share PDF") }
            }
        }
    }
}

@Composable
private fun RiskSummary(apps: List<AppRecord>) {
    val counts = RiskBand.entries.associateWith { band -> apps.count { it.risk.band == band } }
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Risk summary", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("${apps.size} apps checked · scores are explainable and computed on-device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            RiskBand.entries.reversed().forEach { band ->
                Column(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(riskTint(band)).padding(9.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(counts[band].toString(), fontWeight = FontWeight.Bold, color = riskColor(band), style = MaterialTheme.typography.titleLarge)
                    Text(band.label, color = riskColor(band), fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun OnboardingCard(onContinue: () -> Unit, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Your apps, checked privately", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "App Risk Auditor reads the permissions installed apps request and highlights combinations worth reviewing. Analysis stays on this device. Network access is used only for the optional hash lookup after you consent.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onContinue) { Text("Continue") }
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        }
    }
}

@Composable
fun AppsScreen(vm: AppViewModel, navController: NavController) {
    val snapshot by vm.snapshot.collectAsState()
    val includeSystem by vm.includeSystemApps.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedBand by remember { mutableStateOf<RiskBand?>(null) }
    var sortBy by remember { mutableStateOf("Risk") }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val filtered = remember(snapshot.apps, query, selectedBand, sortBy) {
        val found = snapshot.apps.filter { app ->
            (selectedBand == null || app.risk.band == selectedBand) &&
                (app.appName.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true))
        }
        when (sortBy) {
            "Name" -> found.sortedBy { it.appName.lowercase() }
            "Install date" -> found.sortedByDescending { it.firstInstallTime }
            else -> found.sortedByDescending { it.risk.score }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search apps or package names") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Include system apps", style = MaterialTheme.typography.labelLarge)
                Text("${filtered.size} of ${snapshot.apps.size} apps", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = includeSystem, onCheckedChange = vm::setIncludeSystemApps)
            Box {
                TextButtonWithMenu(sortBy, onClick = { sortMenuOpen = true })
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    listOf("Risk", "Name", "Install date").forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { sortBy = option; sortMenuOpen = false })
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = selectedBand == null, onClick = { selectedBand = null }, label = { Text("All") })
            RiskBand.entries.reversed().forEach { band ->
                FilterChip(selected = selectedBand == band, onClick = { selectedBand = if (selectedBand == band) null else band }, label = { Text(band.label) })
            }
        }
        Spacer(Modifier.height(6.dp))
        if (snapshot.isLoading && snapshot.apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (filtered.isEmpty()) {
            EmptyState("No matching apps", "Try a different search or risk filter.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    AppRiskRow(app, onClick = { navController.navigate("app/${app.packageName}") })
                }
            }
        }
    }
}

@Composable
private fun TextButtonWithMenu(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text("Sort: $text") }
}

@Composable
fun CategoriesScreen(vm: AppViewModel, navController: NavController) {
    val summaries = vm.categorySummaries()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Permission access across this device", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Tap a category to see the apps that declare or hold its permissions.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(summaries, key = { it.category.name }) { summary ->
                CategorySummaryRow(summary) { navController.navigate("category/${summary.category.name}") }
            }
        }
    }
}

@Composable
fun CategoryDetailScreen(category: PermissionCategory, vm: AppViewModel, navController: NavController) {
    val snapshot by vm.snapshot.collectAsState()
    val apps = snapshot.apps.filter { app ->
        app.permissions.any { it.category == category && (it.declared || it.granted) }
    }.sortedByDescending { it.risk.score }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(category.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${apps.size} apps declare or hold permissions in this category.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (apps.isEmpty()) EmptyState("No apps found", "No installed apps currently request this category.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(apps, key = { it.packageName }) { app ->
                AppRiskRow(app, onClick = { navController.navigate("app/${app.packageName}") }, compact = true)
            }
        }
    }
}

@Composable
private fun CategorySummaryRow(summary: com.hackathon.apprisk.domain.model.CategorySummary, onClick: () -> Unit) {
    val max = maxOf(summary.appCountDeclared, 1)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(summary.category.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text("${summary.appCountGranted} granted · ${summary.appCountDeclared} declared", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            LinearProgressIndicator(
                progress = { summary.appCountGranted.toFloat() / max },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
fun AlertsScreen(vm: AppViewModel, navController: NavController) {
    val alerts by vm.alerts.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Install and update alerts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Only high- and critical-risk events are sent as notifications.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (alerts.isEmpty()) {
            EmptyState("No alerts yet", "New high-risk installs and updates will appear here when monitoring is enabled.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(alerts, key = { it.id }) { alert ->
                    Card(
                        onClick = { navController.navigate("app/${alert.packageName}") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(alert.type.replace('_', ' '), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                RiskPill(alert.band, alert.score)
                            }
                            Text(alert.appName, fontWeight = FontWeight.SemiBold)
                            Text(alert.message, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(DateFormat.format("MMM d, yyyy · h:mm a", alert.createdAt).toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, navController: NavController) {
    val monitoring by vm.monitoring.collectAsState()
    val darkTheme by vm.darkTheme.collectAsState()
    val hashConsent by vm.hashConsent.collectAsState()
    val endpoint by vm.hashEndpoint.collectAsState()
    var endpointText by remember(endpoint) { mutableStateOf(endpoint) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InfoCard {
                    Text("Monitoring", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SettingSwitch(
                        title = "Monitor installs and updates",
                        subtitle = "Checks new packages and diffs installed apps periodically.",
                        checked = monitoring,
                        onCheckedChange = vm::setMonitoring,
                    )
                }
            }
            item {
                InfoCard {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SettingSwitch(
                        title = "Dark theme",
                        subtitle = "Use a dark color scheme throughout the app.",
                        checked = darkTheme,
                        onCheckedChange = vm::setDarkTheme,
                    )
                }
            }
            item {
                InfoCard {
                    Text("Optional hash lookup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (hashConsent) "Consent is enabled. A lookup sends only an APK's SHA-256 fingerprint." else "Off by default. No network request is made until you enable consent here and choose a lookup.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingSwitch(
                        title = "Allow consented hash lookups",
                        subtitle = "The APK itself and other device data are never sent.",
                        checked = hashConsent,
                        onCheckedChange = vm::setHashConsent,
                    )
                    OutlinedTextField(
                        value = endpointText,
                        onValueChange = {
                            endpointText = it
                            vm.setHashEndpoint(it)
                        },
                        label = { Text("Hash lookup endpoint") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                InfoCard {
                    Text("Testing and transparency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { navController.navigate("self-test") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Run synthetic self-test")
                    }
                    OutlinedButton(onClick = { navController.navigate("rubric") }, modifier = Modifier.fillMaxWidth()) {
                        Text("View scoring rubric")
                    }
                    Text("Synthetic profiles never appear in the real app inventory.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                InfoCard {
                    Text("Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("App inventory, permission checks and scores stay on this device. The only optional network feature sends a SHA-256 APK fingerprint after explicit consent.")
                    Text("No analytics, ads, backend account or real malware samples are included.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun RubricScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            InfoCard {
                Text("Score bands", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                RiskWeights.bands.forEach { (name, range) ->
                    Text("$name: ${range.first}–${range.last}")
                }
                Text("Scores are clamped to 0–100. The app itself is never scored.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            InfoCard {
                Text("Permission weights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                PermissionCatalog.allDefinitions().entries.filter { it.value.baseWeight > 0 }.forEach { (name, definition) ->
                    Text("${name.substringAfterLast('.')} · ${definition.category.title}: +${definition.baseWeight}")
                }
                Text("Declared-only weight: ${RiskWeights.DECLARED_ONLY_FACTOR * 100}%. Storage/media cap: ${RiskWeights.STORAGE_MEDIA_CAP}. Background cap: ${RiskWeights.BACKGROUND_CAP}.")
                Text("Each FOREGROUND_SERVICE_* permission uses the foreground-service weight.")
            }
        }
        item {
            InfoCard {
                Text("Special access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                RiskWeights.specialAccessWeights.forEach { (label, points) -> Text("$label: +$points") }
            }
        }
        item {
            InfoCard {
                Text("Installer adjustment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                RiskWeights.installerMultipliers.forEach { (installer, multiplier) -> Text("$installer: ×$multiplier") }
                Text("Applied to the permission subtotal before combination rules.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            InfoCard {
                Text("Combination rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                RiskWeights.combinationRules.forEach { rule ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${rule.id} · ${rule.name} (+${rule.points})", fontWeight = FontWeight.SemiBold)
                        Text(rule.trigger, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("R1 Google Play bonus: +${RiskWeights.OTP_STEALER_PLAY}; other recognized stores do not receive this installer bonus.")
            }
        }
        item {
            InfoCard {
                Text("Additional rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Simple-utility context mismatch: +${RiskWeights.CONTEXT_MISMATCH}")
                Text("Hidden app with sensitive permissions: +${RiskWeights.HIDDEN_SENSITIVE_APP}")
                Text("Update adds sensitive permissions: +${RiskWeights.PERMISSION_DIFF}")
                Text("Debuggable: +${RiskWeights.DEBUGGABLE_APP}; cleartext: +${RiskWeights.CLEARTEXT_TRAFFIC}; exported unprotected component: +${RiskWeights.EXPORTED_COMPONENT} each (cap ${RiskWeights.EXPORTED_COMPONENTS_CAP}).")
            }
        }
        item {
            InfoCard {
                Text("Trusted tool adjustment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Recognized security, MDM and password-manager packages receive ${RiskWeights.TRUSTED_ALLOWLIST_ADJUSTMENT} points. Device-admin rule R3 does not fire for these packages.")
                Text(RiskWeights.trustedPackagePrefixes.joinToString(", "), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            InfoCard {
                Text("Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("All core scanning and scoring are local. Only an explicitly consented hash lookup sends the APK SHA-256 fingerprint; the APK is never uploaded.")
            }
        }
    }
}

@Composable
fun SelfTestScreen() {
    val results = remember {
        SyntheticProfiles.all.map { synthetic -> synthetic to com.hackathon.apprisk.domain.RiskEngine.evaluate(synthetic.profile) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            InfoCard {
                Text("Synthetic profiles only", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("These examples exercise the rules and never enter the real device scan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(results, key = { it.first.name }) { (synthetic, actual) ->
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(synthetic.name, fontWeight = FontWeight.Bold)
                        Text(synthetic.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(
                        if (actual.band == synthetic.expectedBand) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = if (actual.band == synthetic.expectedBand) "Passed" else "Check expected result",
                        tint = if (actual.band == synthetic.expectedBand) Color(0xFF16825D) else MaterialTheme.colorScheme.error,
                    )
                }
                Text("Expected ${synthetic.expectedBand.label} · Actual ${actual.band.label} (${actual.score}/100)")
                actual.reasons.take(3).forEach { Text("• ${it.text} (${it.points})", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
internal fun EmptyState(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

