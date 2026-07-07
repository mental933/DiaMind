package de.diamind.ai.ai

import android.content.Context
import de.diamind.ai.insulin.InsulinAdvisor
import de.diamind.ai.storage.Preferences.loadText
import java.util.Locale

object RecommendationFormatter {
    fun meal(
        context: Context,
        bolusText: String,
        timingText: String,
        reason: String,
        carbs: Int,
        ke: Double,
        be: Double = carbs / 12.0,
        glucose: String,
        trend: String,
        factor: Double,
        learning: String = "Nach Bestaetigung lerne ich aus deiner Korrektur."
    ): String {
        val iob = realIobText(context)
        return buildString {
            appendLine("🔴 Bolus: $bolusText IE")
            appendLine("⏱ Spritz-Ess-Abstand: ${seaLabel(timingText)}")
            appendLine("🧠 ${reason.oneSentence()}")
            appendLine()
            appendLine("Details:")
            appendLine("- KH: ca. $carbs g")
            appendLine("- KE: ${fmt(ke)}")
            appendLine("- BE: ${fmt(be)}")
            appendLine("- Glukose: $glucose mg/dL")
            appendLine("- Trend: ${trendLabel(trend)}")
            appendLine("- Faktor: ${fmt(factor)} IE/KE")
            appendLine("- IOB: ${iob ?: "keine echte Berechnung"}")
            appendLine("- Restinsulin: ${iob ?: "nicht berechnet"}")
            appendLine("- Lernhinweis: $learning")
        }.trim()
    }

    fun correction(
        context: Context,
        bolusText: String,
        timingText: String,
        reason: String,
        glucose: String,
        trend: String,
        factorText: String
    ): String {
        val iob = realIobText(context)
        return buildString {
            appendLine("🔴 Bolus: $bolusText IE")
            appendLine("⏱ Spritz-Ess-Abstand: ${seaLabel(timingText)}")
            appendLine("🧠 ${reason.oneSentence()}")
            appendLine()
            appendLine("Details:")
            appendLine("- KH: 0 g")
            appendLine("- KE: 0,0")
            appendLine("- BE: 0,0")
            appendLine("- Glukose: $glucose mg/dL")
            appendLine("- Trend: ${trendLabel(trend)}")
            appendLine("- Faktor: $factorText")
            appendLine("- IOB: ${iob ?: "keine echte Berechnung"}")
            appendLine("- Restinsulin: ${iob ?: "nicht berechnet"}")
            appendLine("- Lernhinweis: Bitte nur bestaetigen, wenn es zu deinem Plan passt.")
        }.trim()
    }

    fun seaLabel(timingText: String): String {
        val lower = timingText.lowercase(Locale.getDefault())
        return when {
            lower.contains("erst essen") || lower.contains("nach dem essen") || lower.contains("niedrig") || lower.contains("fall") -> "Nach dem Essen"
            lower.contains("10") -> "10 Minuten"
            lower.contains("5") -> "5 Minuten"
            lower.contains("erhoeht") || lower.contains("erhöht") || lower.contains("steig") || lower.contains("direkt") || lower.contains("sofort") -> "Jetzt"
            else -> "Jetzt"
        }
    }

    fun shortReason(glucose: String, trend: String, carbs: Int): String {
        val value = glucose.toIntOrNull()
        val trendLower = trend.lowercase(Locale.getDefault())
        return when {
            value != null && value < 90 -> "Der Wert ist niedrig, deshalb erst essen und den Bolus vorsichtig pruefen."
            trendLower.contains("down") -> "Der Trend faellt, deshalb ist ein Abstand nach dem Essen sicherer."
            value != null && value >= 180 -> "Die Glukose ist erhoeht, deshalb nicht lange warten."
            trendLower.contains("up") -> "Glukose steigt bereits, deshalb jetzt oder mit kurzem Abstand spritzen."
            carbs >= 70 -> "Die Mahlzeit hat viele Kohlenhydrate, deshalb den Verlauf spaeter kontrollieren."
            else -> "Die Schaetzung passt als Startpunkt, bitte kurz pruefen und dann bestaetigen."
        }
    }

    fun realIobText(context: Context): String? {
        val lastBolusMs = loadText(context, "lastBolusTimeMs", "0").toLongOrNull() ?: 0L
        if (lastBolusMs <= 0L) return null
        val iob = InsulinAdvisor.activeInsulin(context)
        return if (iob > 0.05) "${InsulinAdvisor.format(iob)} IE" else null
    }

    private fun trendLabel(trend: String): String {
        val t = trend.lowercase(Locale.getDefault())
        return when {
            t.contains("doubleup") -> "schnell steigend"
            t.contains("singleup") || t.contains("fortyfiveup") -> "steigend"
            t.contains("doubledown") -> "schnell fallend"
            t.contains("singledown") || t.contains("fortyfivedown") -> "fallend"
            t.contains("flat") -> "stabil"
            t.contains("manual") -> "manuell"
            else -> trend
        }
    }

    private fun fmt(value: Double): String = String.format(Locale.GERMAN, "%.1f", value)

    private fun String.oneSentence(): String {
        val clean = trim().replace('\n', ' ')
        val first = clean.substringBefore(".").trim()
        return when {
            first.isBlank() -> "Bitte kurz pruefen und dann bestaetigen."
            first.endsWith("!") || first.endsWith("?") -> first
            else -> "$first."
        }
    }
}
