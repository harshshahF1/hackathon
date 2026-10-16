package com.hackathon.apprisk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hackathon.apprisk.worker.AppMonitorWorker

class PackageChangeReceiver : BroadcastReceiver() {
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
