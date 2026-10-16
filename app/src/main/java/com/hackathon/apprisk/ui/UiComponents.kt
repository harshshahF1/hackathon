package com.hackathon.apprisk.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.hackathon.apprisk.domain.model.AppRecord
import com.hackathon.apprisk.domain.model.RiskBand

private val appIconCache = LruCache<String, Bitmap>(120)

fun riskColor(band: RiskBand): Color = when (band) {
    RiskBand.LOW -> Color(0xFF16825D)
    RiskBand.MEDIUM -> Color(0xFFA06A00)
    RiskBand.HIGH -> Color(0xFFC3581A)
    RiskBand.CRITICAL -> Color(0xFFC43B3B)
}

fun riskTint(band: RiskBand): Color = when (band) {
    RiskBand.LOW -> Color(0xFFE1F3E9)
    RiskBand.MEDIUM -> Color(0xFFFFF0D2)
    RiskBand.HIGH -> Color(0xFFFFE9D8)
    RiskBand.CRITICAL -> Color(0xFFFFE3E1)
}

@Composable
fun RiskPill(
    band: RiskBand,
    score: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(riskTint(band))
            .paddingCompat(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(riskColor(band)))
        Text("${band.label} · $score", color = riskColor(band), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppIcon(record: AppRecord, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(record.packageName, record.lastUpdateTime) {
        appIconCache.get(record.packageName) ?: runCatching {
            context.packageManager.getApplicationIcon(record.packageName).toBitmap(128, 128).also {
                appIconCache.put(record.packageName, it)
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "${record.appName} icon",
            modifier = modifier.size(48.dp).clip(RoundedCornerShape(13.dp)),
        )
    } else {
        Box(
            modifier = modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("A", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 23.sp)
        }
    }
}

@Composable
fun AppRiskRow(
    record: AppRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().paddingCompat(if (compact) 12.dp else 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AppIcon(record)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        record.appName,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    RiskPill(record.risk.band, record.risk.score)
                }
                Text(
                    record.packageName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text(if (record.installerKind.name == "GOOGLE_PLAY") "Google Play" else if (record.installerKind.name == "SIDELOADED") "Sideloaded" else record.installerSource, fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp),
                    )
                    if (record.risk.reasons.any { it.ruleId == "CONTEXT_MISMATCH" }) {
                        AssistChip(onClick = onClick, label = { Text("Context mismatch", fontSize = 10.sp) }, modifier = Modifier.height(28.dp))
                    }
                }
                if (!compact) {
                    record.risk.reasons.take(2).forEach { reason ->
                        Text(
                            text = "• ${reason.text}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (trailing != null) Text(trailing, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun InfoCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().paddingCompat(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

private fun Modifier.paddingCompat(all: androidx.compose.ui.unit.Dp): Modifier =
    this.padding(all)

private fun Modifier.paddingCompat(horizontal: androidx.compose.ui.unit.Dp, vertical: androidx.compose.ui.unit.Dp): Modifier =
    this.padding(horizontal = horizontal, vertical = vertical)
