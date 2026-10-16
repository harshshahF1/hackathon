package com.hackathon.apprisk

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.hackathon.apprisk.data.storage.AlertNotifier
import com.hackathon.apprisk.data.storage.AppStorage
import com.hackathon.apprisk.worker.AppMonitorWorker

class AppRiskApplication : Application() {
    private var packageReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        AlertNotifier.ensureChannel(this)
        registerForegroundPackageReceiver()
        schedulePeriodicMonitoring()
    }

    private fun registerForegroundPackageReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val packageName = intent.data?.schemeSpecificPart ?: return
                val action = when (intent.action) {
                    Intent.ACTION_PACKAGE_ADDED -> {
                        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) "UPDATE" else "NEW_APP"
                    }
                    Intent.ACTION_PACKAGE_REPLACED -> "UPDATE"
                    Intent.ACTION_PACKAGE_REMOVED -> {
                        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                        "REMOVE"
                    }
                    else -> return
                }
                AppMonitorWorker.enqueuePackage(context, packageName, action)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(receiver, filter)
            }
            packageReceiver = receiver
        } catch (_: Exception) {
            packageReceiver = null
        }
    }

    private fun schedulePeriodicMonitoring() {
        if (AppStorage(this).monitoringEnabled()) {
            AppMonitorWorker.schedulePeriodic(this)
        } else {
            WorkManager.getInstance(this).cancelUniqueWork(AppMonitorWorker.PERIODIC_WORK_NAME)
        }
    }
}
