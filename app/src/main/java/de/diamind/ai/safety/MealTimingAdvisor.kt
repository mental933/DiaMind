package de.diamind.ai.safety

import android.content.Context
import de.diamind.ai.therapy.TherapyProfile

object MealTimingAdvisor {
    fun advice(context: Context, glucoseText: String, trend: String, plannedCarbs: Int): String {
        val glucose = glucoseText.toIntOrNull()
        val trendText = trendLabel(trend)
        val bolus = TherapyProfile.bolusInsulin(context)

        val sea = when {
            glucose == null -> "SEA: Glukosewert unbekannt – bitte Wert und Trend prüfen."
            glucose < 80 -> "SEA: Wert niedrig. Eher erst essen/kurzer Abstand; Vorab-Bolus nur sehr vorsichtig nach deinem Plan prüfen."
            glucose in 80..100 && isFalling(trend) -> "SEA: Wert normal-niedrig und fallend. Erst Essen beginnen oder Abstand stark verkürzen."
            glucose in 80..100 -> "SEA: Wert normal-niedrig. Kurzer Spritz-Ess-Abstand, besonders bei schneller Mahlzeit."
            glucose in 101..160 && isRising(trend) -> "SEA: Wert im Bereich, aber steigend. Normaler bis etwas längerer Abstand kann sinnvoll sein."
            glucose in 101..160 -> "SEA: normaler Abstand nach deinem Plan."
            glucose in 161..220 -> "SEA: Wert erhöht. Abstand kann je nach Trend sinnvoll sein; bitte IOB und deinen Plan beachten."
            else -> "SEA: deutlich erhöhter Wert. Korrektur/Abstand nur nach deinem Plan und mit Vorsicht prüfen."
        }

        val carbNote = when {
            plannedCarbs < 15 -> "Kleine Mahlzeit/Snack: Wirkung kann schneller vorbei sein."
            plannedCarbs > 80 -> "Große Mahlzeit: Fett/Protein können den Verlauf verzögern."
            else -> "Normale Mahlzeitgröße."
        }

        return "$sea\nTrend: $trendText · Insulin: $bolus · $carbNote"
    }

    fun trendLabel(trend: String): String {
        return when (trend.lowercase()) {
            "doubleup" -> "schnell steigend"
            "singleup" -> "steigend"
            "fortyfiveup" -> "leicht steigend"
            "flat" -> "stabil"
            "fortyfivedown" -> "leicht fallend"
            "singledown" -> "fallend"
            "doubledown" -> "schnell fallend"
            "manual" -> "manuell"
            else -> "unbekannt"
        }
    }

    private fun isFalling(trend: String): Boolean {
        val t = trend.lowercase()
        return t.contains("down")
    }

    private fun isRising(trend: String): Boolean {
        val t = trend.lowercase()
        return t.contains("up")
    }
}
