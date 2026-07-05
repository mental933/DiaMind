package de.diamind.ai.insulin

import android.content.Context
import de.diamind.ai.storage.Preferences.loadText
import de.diamind.ai.storage.Preferences.saveText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object InsulinAdvisor {

    private val slots = listOf("Frühstück", "Mittag", "Abend")

    fun initializeDefaults(context: Context) {
        if (loadText(context, "factorMorning", "").isBlank()) saveText(context, "factorMorning", "1.0")
        if (loadText(context, "factorNoon", "").isBlank()) saveText(context, "factorNoon", "2.5")
        if (loadText(context, "factorEvening", "").isBlank()) saveText(context, "factorEvening", "3.0")
        if (loadText(context, "bolusInsulin", "").isBlank()) saveText(context, "bolusInsulin", "Lyumjev")
        if (loadText(context, "basalInsulin", "").isBlank()) saveText(context, "basalInsulin", "Tresiba")
        if (loadText(context, "bolusStep", "").isBlank()) saveText(context, "bolusStep", "1.0")
        if (loadText(context, "targetMin", "").isBlank()) saveText(context, "targetMin", "80")
        if (loadText(context, "targetMax", "").isBlank()) saveText(context, "targetMax", "160")
        if (loadText(context, "learningMealCount", "").isBlank()) saveText(context, "learningMealCount", "0")
        slots.forEach { slot ->
            if (loadText(context, slotKey(slot, "count"), "").isBlank()) saveText(context, slotKey(slot, "count"), "0")
            if (loadText(context, slotKey(slot, "high"), "").isBlank()) saveText(context, slotKey(slot, "high"), "0")
            if (loadText(context, slotKey(slot, "low"), "").isBlank()) saveText(context, slotKey(slot, "low"), "0")
            if (loadText(context, slotKey(slot, "ok"), "").isBlank()) saveText(context, slotKey(slot, "ok"), "0")
        }
    }

    fun currentSlot(hour: Int = currentHour()): String {
        return when (hour) {
            in 5..10 -> "Frühstück"
            in 11..15 -> "Mittag"
            else -> "Abend"
        }
    }

    fun factorForSlot(context: Context, slot: String): Double {
        val key = factorKey(slot)
        return loadText(context, key, defaultFactor(slot)).replace(',', '.').toDoubleOrNull() ?: defaultFactor(slot).toDouble()
    }

    fun defaultFactor(slot: String): String {
        return when (slot) {
            "Frühstück" -> "1.0"
            "Mittag" -> "2.5"
            else -> "3.0"
        }
    }

    fun doseForKe(ke: Double, factor: Double): Double = roundToHalf(ke * factor)

    fun doseForKe(context: Context, ke: Double, factor: Double): Double {
        initializeDefaults(context)
        val raw = ke * factor
        val step = loadText(context, "bolusStep", "1.0").replace(',', '.').toDoubleOrNull() ?: 1.0
        return when {
            step >= 1.0 -> raw.roundToInt().toDouble().coerceAtLeast(0.0)
            step >= 0.5 -> roundToHalf(raw)
            else -> roundToTenth(raw)
        }
    }

    fun formatDose(context: Context, dose: Double): String {
        val step = loadText(context, "bolusStep", "1.0").replace(',', '.').toDoubleOrNull() ?: 1.0
        return if (step >= 1.0) "${dose.roundToInt()}" else format(dose)
    }

    fun preMealTimingAdvice(context: Context, glucoseInput: String, trend: String, carbs: Int = 0): String {
        val glucose = glucoseInput.toIntOrNull()
        val trendLower = trend.lowercase(Locale.getDefault())
        val bolus = loadText(context, "bolusInsulin", "Lyumjev")
        val fastFalling = trendLower in listOf("fortyfivedown", "singledown", "doubledown")
        val rising = trendLower in listOf("fortyfiveup", "singleup", "doubleup")
        val veryFastRising = trendLower == "doubleup"
        return when {
            glucose == null -> "Spritz-Ess-Abstand: unbekannt, weil kein aktueller Glukosewert vorliegt. Bitte selbst prüfen."
            glucose < 80 || fastFalling -> "Spritz-Ess-Abstand: sehr vorsichtig. Bei $glucose mg/dL oder fallendem Trend eher erst essen bzw. keinen langen Vorab-Abstand wählen. Bitte nach deinem Therapieplan prüfen."
            glucose in 80..105 && !rising -> "Spritz-Ess-Abstand: eher kurz halten. Wert ist im unteren Bereich; erst Essen sicherstellen und Bolus bewusst bestätigen."
            glucose in 106..160 && trendLower == "flat" -> "Spritz-Ess-Abstand: bei $bolus meist normal/kurz möglich. Für deinen Alltag bitte individuell bestätigen."
            glucose in 106..180 && rising -> "Spritz-Ess-Abstand: Wert steigt. Ein kurzer Vorab-Abstand kann sinnvoll sein, aber nur wenn Essen sicher unmittelbar folgt."
            glucose > 180 || veryFastRising -> "Spritz-Ess-Abstand: Wert ist erhöht/steigend. Vorab-Abstand oder Korrektur nur nach deinem Plan; IOB/letzten Bolus beachten."
            carbs < 10 -> "Spritz-Ess-Abstand: kleine Mahlzeit. Bolus und Abstand besonders vorsichtig prüfen."
            else -> "Spritz-Ess-Abstand: individuell prüfen. DiaMind gibt nur eine Orientierung, keine automatische Therapieentscheidung."
        }
    }

    fun saveMealAssumption(
        context: Context,
        description: String,
        carbs: Int,
        ke: Double,
        recommendedDose: Double,
        actualDoseInput: String,
        slot: String,
        glucoseBefore: String,
        trendBefore: String
    ): String {
        initializeDefaults(context)
        val actualDose = actualDoseInput.replace(',', '.').toDoubleOrNull() ?: recommendedDose
        val status = if (actualDoseInput.isBlank()) "als ausgeführt angenommen" else "bestätigt/korrigiert"
        val time = SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(Date())
        val count = (loadText(context, "learningMealCount", "0").toIntOrNull() ?: 0) + 1
        saveText(context, "learningMealCount", count.toString())
        saveText(context, "lastMealSlot", slot)
        saveText(context, "lastRecommendedDose", format(recommendedDose))
        saveText(context, "lastActualDose", format(actualDose))
        saveText(context, "lastMealKe", format(ke))
        saveText(context, "lastMealCarbs", carbs.toString())
        saveText(context, "lastMealStatus", status)
        saveText(context, "lastMealTime", time)
        saveText(context, "lastMealDescription", description.ifBlank { "ohne Beschreibung" })
        saveText(context, "lastMealGlucoseBefore", glucoseBefore)
        saveText(context, "lastMealTrendBefore", trendBefore)
        saveText(context, "lastMealFollowUpDone", "false")
        saveText(context, "assistantBubble", "Mahlzeit gespeichert: ${description.ifBlank { "Essen" }} · ${carbs} g KH · ${formatDose(context, actualDose)} IE bestätigt.")

        val record = """
            $time · $slot
            Mahlzeit: ${description.ifBlank { "ohne Beschreibung" }}
            Vorher: $glucoseBefore mg/dL · ${trendLabel(trendBefore)}
            KH: ca. $carbs g · KE: ${format(ke)}
            Faktor: ${format(factorForSlot(context, slot))} IE/KE
            Empfehlung: ${formatDose(context, recommendedDose)} IE
            Gespeichert: ${formatDose(context, actualDose)} IE ($status)
            ${preMealTimingAdvice(context, glucoseBefore, trendBefore, carbs)}
            Follow-up: noch offen
        """.trimIndent()
        saveText(context, "lastBolusRecord", record)
        return record
    }

    fun recordFollowUp(context: Context, afterGlucoseInput: String): String {
        initializeDefaults(context)
        val after = afterGlucoseInput.trim().toIntOrNull()
            ?: return "Bitte einen gültigen Glukosewert eingeben."
        val slot = loadText(context, "lastMealSlot", currentSlot())
        val before = loadText(context, "lastMealGlucoseBefore", "?")
        val time = SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(Date())
        val outcome = when {
            after < 75 -> "zu niedrig"
            after > 180 -> "zu hoch"
            else -> "im Ziel"
        }
        increment(context, slotKey(slot, "count"))
        when (outcome) {
            "zu niedrig" -> increment(context, slotKey(slot, "low"))
            "zu hoch" -> increment(context, slotKey(slot, "high"))
            else -> increment(context, slotKey(slot, "ok"))
        }
        val currentFactor = factorForSlot(context, slot)
        val proposedFactor = when (outcome) {
            "zu hoch" -> currentFactor + 0.1
            "zu niedrig" -> (currentFactor - 0.1).coerceAtLeast(0.1)
            else -> currentFactor
        }
        val suggestion = if (outcome == "im Ziel") {
            "Kein Anpassungsvorschlag. Faktor beibehalten."
        } else {
            saveText(context, slotKey(slot, "proposal"), format(proposedFactor))
            "Vorschlag: $slot von ${format(currentFactor)} auf ${format(proposedFactor)} IE/KE ändern."
        }
        val text = """
            Follow-up gespeichert: $time
            Zeitraum: $slot
            Vor dem Essen: $before mg/dL
            Nach 2–3 Stunden: $after mg/dL
            Bewertung: $outcome
            $suggestion
        """.trimIndent()
        saveText(context, "lastMealFollowUpDone", "true")
        saveText(context, "lastFollowUpRecord", text)
        saveText(context, "lastBolusRecord", loadText(context, "lastBolusRecord", "") + "\n\n$text")
        saveText(context, "assistantBubble", "Follow-up bewertet: $outcome. $suggestion")
        return text
    }

    fun applyProposal(context: Context, slot: String): String {
        initializeDefaults(context)
        val proposal = loadText(context, slotKey(slot, "proposal"), "")
        if (proposal.isBlank()) return "Für $slot gibt es aktuell keinen offenen Vorschlag."
        saveText(context, factorKey(slot), proposal)
        saveText(context, slotKey(slot, "proposal"), "")
        val message = "Faktor für $slot übernommen: $proposal IE/KE"
        saveText(context, "lastFactorChange", message)
        saveText(context, "assistantBubble", message)
        return message
    }

    fun learningSummary(context: Context): String {
        initializeDefaults(context)
        val count = loadText(context, "learningMealCount", "0")
        val last = loadText(context, "lastBolusRecord", "Noch keine Mahlzeit mit Bolusannahme gespeichert.")
        val followUp = loadText(context, "lastFollowUpRecord", "Noch kein 2–3h-Follow-up gespeichert.")
        return """
            Therapieprofil
            Bolus: ${loadText(context, "bolusInsulin", "Lyumjev")} · Schrittweite: ${loadText(context, "bolusStep", "1.0")} IE
            Basal: ${loadText(context, "basalInsulin", "Tresiba")} · Dosis: ${loadText(context, "basalDose", "nicht gesetzt")}

            Start- und Lernfaktoren
            Frühstück: ${loadText(context, "factorMorning", "1.0")} IE/KE · ${slotStats(context, "Frühstück")}
            Mittag: ${loadText(context, "factorNoon", "2.5")} IE/KE · ${slotStats(context, "Mittag")}
            Abend: ${loadText(context, "factorEvening", "3.0")} IE/KE · ${slotStats(context, "Abend")}

            Mahlzeiten mit Bolusannahme: $count

            Letzter Eintrag
            $last

            Letztes Follow-up
            $followUp
        """.trimIndent()
    }

    fun openProposalText(context: Context): String {
        initializeDefaults(context)
        val proposals = slots.mapNotNull { slot ->
            val p = loadText(context, slotKey(slot, "proposal"), "")
            if (p.isBlank()) null else "$slot → $p IE/KE"
        }
        return if (proposals.isEmpty()) "Keine offenen Faktor-Vorschläge." else proposals.joinToString("\n")
    }

    fun format(value: Double): String = String.format(Locale.GERMAN, "%.1f", value)

    private fun currentHour(): Int = SimpleDateFormat("H", Locale.getDefault()).format(Date()).toIntOrNull() ?: 12
    private fun roundToHalf(value: Double): Double = kotlin.math.round(value * 2.0) / 2.0
    private fun roundToTenth(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0

    private fun factorKey(slot: String): String = when (slot) {
        "Frühstück" -> "factorMorning"
        "Mittag" -> "factorNoon"
        else -> "factorEvening"
    }

    private fun slotKey(slot: String, suffix: String): String {
        val prefix = when (slot) {
            "Frühstück" -> "morning"
            "Mittag" -> "noon"
            else -> "evening"
        }
        return "learning_${prefix}_$suffix"
    }

    private fun increment(context: Context, key: String) {
        val value = (loadText(context, key, "0").toIntOrNull() ?: 0) + 1
        saveText(context, key, value.toString())
    }

    private fun slotStats(context: Context, slot: String): String {
        val count = loadText(context, slotKey(slot, "count"), "0")
        val high = loadText(context, slotKey(slot, "high"), "0")
        val low = loadText(context, slotKey(slot, "low"), "0")
        val ok = loadText(context, slotKey(slot, "ok"), "0")
        val proposal = loadText(context, slotKey(slot, "proposal"), "")
        val proposalText = if (proposal.isBlank()) "kein Vorschlag" else "Vorschlag $proposal"
        return "$count Follow-ups · Ziel $ok · hoch $high · niedrig $low · $proposalText"
    }

    private fun trendLabel(trend: String): String = when (trend.lowercase()) {
        "doubleup" -> "schnell steigend"
        "singleup" -> "steigend"
        "fortyfiveup" -> "leicht steigend"
        "flat" -> "stabil"
        "fortyfivedown" -> "leicht fallend"
        "singledown" -> "fallend"
        "doubledown" -> "schnell fallend"
        "manual" -> "manuell"
        else -> trend
    }
}
