package com.hackathon.apprisk.domain

import android.content.Context
import com.hackathon.apprisk.domain.model.AppRecord
import org.json.JSONObject
import kotlin.math.exp

object MlScorer {
    private var model: JSONObject? = null

    @Synchronized
    private fun load(context: Context): JSONObject? {
        model?.let { return it }
        return runCatching {
            context.assets.open("ml_model.json").bufferedReader().use { JSONObject(it.readText()) }
        }.getOrNull().also { model = it }
    }

    fun score(context: Context, app: AppRecord): Int? {
        val weights = load(context)?.optJSONObject("weights") ?: return null
        val intercept = load(context)?.optDouble("intercept", 0.0) ?: 0.0
        val features = (app.grantedPermissions + app.declaredPermissions).toSet()
        val logit = weights.keys().asSequence()
            .filter { it in features }
            .sumOf { weights.optDouble(it, 0.0) } + intercept
        val probability = 1.0 / (1.0 + exp(-logit.coerceIn(-30.0, 30.0)))
        return (probability * 100).toInt().coerceIn(0, 100)
    }
}
