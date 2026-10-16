package com.hackathon.apprisk.util

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.hackathon.apprisk.domain.PermissionCatalog
import com.hackathon.apprisk.domain.model.AppRecord
import com.hackathon.apprisk.domain.model.PermissionCategory
import com.hackathon.apprisk.domain.model.RiskBand
import java.io.File
import java.text.DateFormat
import java.util.Date

object ReportExporter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val LEFT = 44f
    private const val RIGHT = 550f
    private const val TOP = 54f
    private const val BOTTOM = 790f

    fun writePdf(context: Context, uri: Uri, apps: List<AppRecord>, scannedAt: Long): Boolean =
        runCatching {
            val document = createDocument(apps, scannedAt)
            try {
                context.contentResolver.openOutputStream(uri)?.use { output -> document.writeTo(output) } ?: return false
                true
            } finally {
                document.close()
            }
        }.getOrDefault(false)

    fun createShareIntent(context: Context, apps: List<AppRecord>, scannedAt: Long): Intent? =
        runCatching {
            val file = File(context.cacheDir, "AppRiskAuditor-report.pdf")
            val document = createDocument(apps, scannedAt)
            try {
                file.outputStream().use { output -> document.writeTo(output) }
            } finally {
                document.close()
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.getOrNull()

    fun writeJson(context: Context, uri: Uri, apps: List<AppRecord>, scannedAt: Long): Boolean =
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(
                    buildString {
                        appendLine("{")
                        appendLine("  \"device\": \"${escape("${Build.MANUFACTURER} ${Build.MODEL}")}\",")
                        appendLine("  \"androidVersion\": \"${escape(Build.VERSION.RELEASE)}\",")
                        appendLine("  \"scannedAt\": $scannedAt,")
                        appendLine("  \"apps\": [")
                        apps.sortedByDescending { it.risk.score }.forEachIndexed { index, app ->
                            val comma = if (index < apps.size - 1) "," else ""
                            appendLine("    {")
                            appendLine("      \"name\": \"${escape(app.appName)}\",")
                            appendLine("      \"packageName\": \"${escape(app.packageName)}\",")
                            appendLine("      \"versionName\": \"${escape(app.versionName)}\",")
                            appendLine("      \"versionCode\": ${app.versionCode},")
                            appendLine("      \"installer\": \"${escape(app.installerSource)}\",")
                            appendLine("      \"targetSdk\": ${app.targetSdk},")
                            appendLine("      \"riskScore\": ${app.risk.score},")
                            appendLine("      \"riskBand\": \"${app.risk.band.label}\",")
                            appendLine("      \"firstInstallTime\": ${app.firstInstallTime},")
                            appendLine("      \"lastUpdateTime\": ${app.lastUpdateTime},")
                            appendLine("      \"systemApp\": ${app.isSystemApp},")
                            appendLine("      \"hasLauncherIcon\": ${app.hasLauncherIcon},")
                            appendLine("      \"mlLikelihood\": ${app.mlLikelihood ?: "null"},")
                            appendLine("      \"knownThreatFingerprint\": ${app.knownThreatFingerprint},")
                            appendLine("      \"permissions\": [")
                            app.permissions.forEachIndexed { permissionIndex, permission ->
                                val permissionComma = if (permissionIndex < app.permissions.size - 1) "," else ""
                                appendLine("        {\"name\":\"${escape(permission.name)}\",\"title\":\"${escape(permission.title)}\",\"category\":\"${permission.category.title}\",\"declared\":${permission.declared},\"granted\":${permission.granted},\"grantKnown\":${permission.grantKnown},\"protection\":\"${permission.protectionLevel}\",\"description\":\"${escape(permission.explanation)}\"}$permissionComma")
                            }
                            appendLine("      ],")
                            appendLine("      \"specialAccess\": {\"accessibility\":${app.specialAccess.accessibilityEnabled},\"deviceAdmin\":${app.specialAccess.deviceAdminActive},\"notificationListener\":${app.specialAccess.notificationListenerEnabled},\"overlay\":${app.specialAccess.overlayGranted},\"installPackages\":${app.specialAccess.installPackagesGranted},\"usageStats\":${app.specialAccess.usageStatsGranted},\"manageStorage\":${app.specialAccess.manageStorageGranted}},")
                            appendLine("      \"permissionDiff\": {\"added\":${jsonArray(app.permissionDiff.added.sorted())},\"newlyGranted\":${jsonArray(app.permissionDiff.newlyGranted.sorted())},\"removed\":${jsonArray(app.permissionDiff.removed.sorted())}},")
                            appendLine("      \"reasons\": [")
                            app.risk.reasons.forEachIndexed { reasonIndex, reason ->
                                val reasonComma = if (reasonIndex < app.risk.reasons.size - 1) "," else ""
                                appendLine("        {\"text\":\"${escape(reason.text)}\",\"points\":${reason.points},\"severity\":\"${reason.severity.label}\",\"ruleId\":\"${escape(reason.ruleId)}\"}$reasonComma")
                            }
                            appendLine("      ]")
                            appendLine("    }$comma")
                        }
                        appendLine("  ]")
                        appendLine("}")
                    },
                )
            } ?: return false
            true
        }.getOrDefault(false)

    private fun createDocument(apps: List<AppRecord>, scannedAt: Long): PdfDocument {
        val document = PdfDocument()
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = TOP
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(18, 75, 66)
            textSize = 22f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(23, 45, 40)
            textSize = 15f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(42, 54, 50)
            textSize = 10.5f
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(91, 106, 99)
            textSize = 8.5f
        }

        fun startPage(): PdfDocument.Page {
            pageNumber += 1
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val newPage = document.startPage(info)
            newPage.canvas.drawColor(android.graphics.Color.rgb(250, 252, 251))
            newPage.canvas.drawText("APP RISK AUDITOR", LEFT, 31f, smallPaint)
            newPage.canvas.drawText("Page $pageNumber", RIGHT - 45f, 31f, smallPaint)
            newPage.canvas.drawLine(LEFT, 40f, RIGHT, 40f, smallPaint)
            y = TOP
            page = newPage
            return newPage
        }

        fun finishPage() {
            page?.let(document::finishPage)
            page = null
        }

        fun ensureSpace(height: Float) {
            if (page == null) startPage()
            if (y + height > BOTTOM) {
                finishPage()
                startPage()
            }
        }

        fun line(text: String, paint: Paint = textPaint, indent: Float = 0f, lineHeight: Float = 15f) {
            val canvas = page?.canvas ?: startPage().canvas
            val maxWidth = RIGHT - LEFT - indent
            val words = text.split(Regex("\\s+"))
            var current = ""
            words.forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                    ensureSpace(lineHeight)
                    page?.canvas?.drawText(current, LEFT + indent, y, paint)
                    y += lineHeight
                    current = word
                } else current = candidate
            }
            if (current.isNotEmpty()) {
                ensureSpace(lineHeight)
                page?.canvas?.drawText(current, LEFT + indent, y, paint)
                y += lineHeight
            }
        }

        fun heading(text: String) {
            ensureSpace(28f)
            y += 7f
            line(text, headingPaint, lineHeight = 21f)
        }

        startPage()
        line("App Risk Auditor", titlePaint, lineHeight = 30f)
        line("On-device app inventory and explainable risk summary", smallPaint, lineHeight = 20f)
        line("Device: ${Build.MANUFACTURER} ${Build.MODEL}", textPaint)
        line("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", textPaint)
        line("Scan time: ${DateFormat.getDateTimeInstance().format(Date(scannedAt))}", textPaint)
        line("Apps scanned: ${apps.size}", textPaint)
        RiskBand.entries.reversed().forEach { band ->
            line("${band.label}: ${apps.count { it.risk.band == band }}", textPaint)
        }

        heading("Category summary")
        PermissionCatalog.categoryOrder().forEach { category ->
            val granted = apps.count { it.permissions.any { permission -> permission.category == category && permission.granted } }
            val declared = apps.count { it.permissions.any { permission -> permission.category == category && permission.declared } }
            line("${category.title}: $granted apps granted; $declared apps declare", textPaint)
        }

        heading("App inventory — highest risk first")
        line("Risk | App | Package | Installer", smallPaint)
        apps.sortedByDescending { it.risk.score }.forEach { app ->
            line("${app.risk.band.label} ${app.risk.score} | ${app.appName} | ${app.packageName} | ${app.installerSource}", textPaint, lineHeight = 14f)
        }

        apps.filter { it.risk.band == RiskBand.HIGH || it.risk.band == RiskBand.CRITICAL }
            .sortedByDescending { it.risk.score }
            .forEach { app ->
                heading("${app.appName} — ${app.risk.band.label} (${app.risk.score}/100)")
                line(app.packageName, smallPaint)
                line("Recommended actions", headingPaint, lineHeight = 19f)
                recommendedActions(app).forEachIndexed { index, action -> line("${index + 1}. $action", textPaint) }
                line("Why this score", headingPaint, lineHeight = 19f)
                app.risk.reasons.forEach { reason -> line("• ${reason.text} (${reason.points} points)", textPaint, indent = 8f) }
            }
        finishPage()
        return document
    }

    private fun recommendedActions(app: AppRecord): List<String> = buildList {
        if (app.specialAccess.accessibilityEnabled) add("Turn off Accessibility access if you do not rely on this app.")
        if (app.specialAccess.deviceAdminActive) add("Revoke device-admin access before trying to uninstall.")
        if (app.specialAccess.notificationListenerEnabled) add("Remove Notification access if it is not needed.")
        if (app.specialAccess.overlayGranted) add("Turn off Display over other apps.")
        if (app.permissions.any { it.category == PermissionCategory.SMS && it.granted }) add("Revoke SMS access in app permissions.")
        add("Uninstall the app if you do not recognize or trust it.")
    }

    private fun jsonArray(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]") { "\"${escape(it)}\"" }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}
