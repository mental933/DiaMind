package de.diamind.ai.insulin

import android.content.Context
import de.diamind.ai.storage.Preferences.loadText
import de.diamind.ai.storage.Preferences.saveText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InsulinAdvisor {

    private val slots = listOf("Frühstück", "Mittag", "Abend")

    fun initializeDefaults(context: Context) {
        if (loadText(context, "factorMorning", "").isBlank()) saveText(context, "factorMorning", "1.0")
        if (loadText(context, "factorNoon", "").isBlank()) saveText(context, "factorNoon", "2.5")
        if (loadText(context, "factorEvening", "").isBlank()) saveText(context, "factorEvening", "3.0")
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

    fun doseForKe(ke: Double, factor: Double): Double {
        return roundToHalf(ke * factor)
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
        val status = if (actualDoseInput.isBlank()) "als ausgeführt angenommen" else "manuell korrigiert"
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

        val record = """
            $time · $slot
            Mahlzeit: ${description.ifBlank { "ohne Beschreibung" }}
            Vorher: $glucoseBefore mg/dL · ${trendLabel(trendBefore)}
            KH: ca. $carbs g · KE: ${format(ke)}
            Faktor: ${format(factorForSlot(context, slot))} IE/KE
            Empfehlung: ${format(recommendedDose)} IE
            Gespeichert: ${format(actualDose)} IE ($status)
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
        return message
    }

    fun learningSummary(context: Context): String {
        initializeDefaults(context)
        val count = loadText(context, "learningMealCount", "0")
        val last = loadText(context, "lastBolusRecord", "Noch keine Mahlzeit mit Bolusannahme gespeichert.")
        val followUp = loadText(context, "lastFollowUpRecord", "Noch kein 2–3h-Follow-up gespeichert.")
        return """
            Start- und Lernfaktoren
            Frühstück: ${loadText(context, "factorMorning", "1.0")} IE/KE · ${slotStats(context, "Frühstück")}
            Mittag: ${loadText(context, "factorNoon", "2.5")} IE/KE · ${slotStats(context, "Mittag")}
            Abend: ${loadText(context, "factorEvening", "3.0")} IE/KE · ${slotStats(context, "Abend")}

            Mahlzeiten mit Bolusannahme: $count

            Letzter Eintrag
            $last

            Letztes Follow-up
            $followUp

            Lernmodus
            DiaMind nimmt die Empfehlung als ausgeführt an, solange du keinen anderen gespritzten Wert einträgst. Vorschläge werden nur vorbereitet und erst übernommen, wenn du sie aktiv bestätigst.
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

    fun format(value: Double): String {
        return String.format(Locale.GERMAN, "%.1f", value)
    }

    private fun currentHour(): Int {
        return SimpleDateFormat("H", Locale.getDefault()).format(Date()).toIntOrNull() ?: 12
    }

    private fun roundToHalf(value: Double): Double {
        return kotlin.math.round(value * 2.0) / 2.0
    }

    private fun factorKey(slot: String): String {
        return when (slot) {
            "Frühstück" -> "factorMorning"
            "Mittag" -> "factorNoon"
            else -> "factorEvening"
        }
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

    private fun trendLabel(trend: String): String {
        return when (trend.lowercase()) {
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
}
