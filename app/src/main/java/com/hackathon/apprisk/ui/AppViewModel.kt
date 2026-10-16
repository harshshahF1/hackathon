package com.hackathon.apprisk.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.apprisk.data.scanner.AppScanner
import com.hackathon.apprisk.data.scanner.ScanProgress
import com.hackathon.apprisk.data.storage.AppStorage
import com.hackathon.apprisk.domain.PermissionCatalog
import com.hackathon.apprisk.domain.RiskEngine
import com.hackathon.apprisk.domain.model.AppReason
import com.hackathon.apprisk.domain.model.CategorySummary
import com.hackathon.apprisk.domain.model.PermissionCategory
import com.hackathon.apprisk.domain.model.ScanSnapshot
import com.hackathon.apprisk.domain.model.SecurityAlert
import com.hackathon.apprisk.worker.AppMonitorWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = AppStorage(application)
    private val _snapshot = MutableStateFlow(ScanSnapshot())
    val snapshot: StateFlow<ScanSnapshot> = _snapshot.asStateFlow()

    private val _progress = MutableStateFlow<ScanProgress?>(null)
    val progress: StateFlow<ScanProgress?> = _progress.asStateFlow()

    private val _alerts = MutableStateFlow(storage.alerts())
    val alerts: StateFlow<List<SecurityAlert>> = _alerts.asStateFlow()

    private val _includeSystemApps = MutableStateFlow(storage.includeSystemApps())
    val includeSystemApps: StateFlow<Boolean> = _includeSystemApps.asStateFlow()

    private val _darkTheme = MutableStateFlow(storage.darkTheme())
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _monitoring = MutableStateFlow(storage.monitoringEnabled())
    val monitoring: StateFlow<Boolean> = _monitoring.asStateFlow()

    private val _onboardingComplete = MutableStateFlow(storage.onboardingComplete())
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete.asStateFlow()

    private val _hashConsent = MutableStateFlow(storage.hashLookupConsented())
    val hashConsent: StateFlow<Boolean> = _hashConsent.asStateFlow()

    private val _hashEndpoint = MutableStateFlow(storage.hashEndpoint())
    val hashEndpoint: StateFlow<String> = _hashEndpoint.asStateFlow()

    init {
        scan()
    }

    fun scan() {
        if (_snapshot.value.isLoading) return
        viewModelScope.launch {
            _snapshot.value = _snapshot.value.copy(isLoading = true, error = null)
            _progress.value = ScanProgress(0, 0, "Preparing scan")
            try {
                val apps = AppScanner.scan(
                    context = getApplication(),
                    includeSystemApps = _includeSystemApps.value,
                    onProgress = { _progress.value = it },
                )
                val processed = withContext(Dispatchers.IO) {
                    storage.mergeScan(
                        context = getApplication(),
                        records = apps,
                        notify = storage.monitoringEnabled(),
                    )
                }
                _snapshot.value = ScanSnapshot(
                    apps = processed,
                    scannedAt = storage.lastScanTime(),
                    isLoading = false,
                )
                refreshAlerts()
            } catch (error: Exception) {
                _snapshot.value = _snapshot.value.copy(
                    isLoading = false,
                    error = error.localizedMessage ?: "The device scan could not be completed.",
                )
            } finally {
                _progress.value = null
            }
        }
    }

    fun setIncludeSystemApps(value: Boolean) {
        storage.setIncludeSystemApps(value)
        _includeSystemApps.value = value
        scan()
    }

    fun setDarkTheme(value: Boolean) {
        storage.setDarkTheme(value)
        _darkTheme.value = value
    }

    fun setMonitoring(value: Boolean) {
        storage.setMonitoringEnabled(value)
        AppMonitorWorker.setEnabled(getApplication(), value)
        _monitoring.value = value
    }

    fun completeOnboarding() {
        storage.setOnboardingComplete(true)
        _onboardingComplete.value = true
    }

    fun setHashConsent(value: Boolean) {
        storage.setHashLookupConsented(value)
        _hashConsent.value = value
    }

    fun setHashEndpoint(value: String) {
        storage.setHashEndpoint(value)
        _hashEndpoint.value = storage.hashEndpoint()
    }

    fun refreshAlerts() {
        _alerts.value = storage.alerts()
    }

    fun setHashVerdict(packageName: String, isKnownThreat: Boolean) {
        val apps = _snapshot.value.apps.map { app ->
            if (app.packageName != packageName) app else {
                val updated = app.copy(knownThreatFingerprint = isKnownThreat)
                updated.copy(risk = RiskEngine.evaluate(updated))
            }
        }
        _snapshot.value = _snapshot.value.copy(apps = apps)
    }

    fun categorySummaries(): List<CategorySummary> {
        val apps = _snapshot.value.apps
        return PermissionCatalog.categoryOrder().map { category ->
            val holdingGranted = apps.count { app ->
                app.permissions.any { it.category == category && it.granted }
            }
            val holdingDeclared = apps.count { app ->
                app.permissions.any { it.category == category && it.declared }
            }
            CategorySummary(category, holdingGranted, holdingDeclared)
        }
    }

    fun markPermissionDetailsSeen() = Unit
}
