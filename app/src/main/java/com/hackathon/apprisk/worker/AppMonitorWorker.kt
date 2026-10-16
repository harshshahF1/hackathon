package com.hackathon.apprisk.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hackathon.apprisk.data.scanner.AppScanner
import com.hackathon.apprisk.data.storage.AppStorage
import java.util.concurrent.TimeUnit

class AppMonitorWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val packageName = inputData.getString(KEY_PACKAGE)
            val eventType = inputData.getString(KEY_EVENT) ?: "UPDATE"
            val storage = AppStorage(applicationContext)
            if (packageName != null && eventType == "REMOVE") {
                storage.removePackageSnapshot(packageName)
                return Result.success()
            }
            val records = AppScanner.scan(
                context = applicationContext,
                includeSystemApps = packageName != null || storage.includeSystemApps(),
                onlyPackage = packageName,
            )
            if (packageName == null) {
                storage.mergeScan(applicationContext, records, notify = storage.monitoringEnabled())
            } else {
                records.firstOrNull()?.let { record ->
                    storage.processSinglePackage(
                        context = applicationContext,
                        record = record,
                        eventType = eventType,
                        notify = storage.monitoringEnabled(),
                    )
                }
            }
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "app-risk-periodic-package-diff"
        private const val KEY_PACKAGE = "package_name"
        private const val KEY_EVENT = "event_type"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<AppMonitorWorker>(15, TimeUnit.MINUTES)
                .addTag(PERIODIC_WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun enqueuePackage(context: Context, packageName: String, eventType: String) {
            val request = OneTimeWorkRequestBuilder<AppMonitorWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(KEY_PACKAGE, packageName)
                        .putString(KEY_EVENT, eventType)
                        .build(),
                )
                .addTag("package-change-$packageName")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "package-change-$packageName",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            if (enabled) {
                schedulePeriodic(context)
            } else {
                WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            }
        }
    }
}
