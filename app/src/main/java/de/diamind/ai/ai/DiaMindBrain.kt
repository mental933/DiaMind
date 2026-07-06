package de.diamind.ai.ai

import android.content.Context
import de.diamind.ai.food.FoodEstimator
import de.diamind.ai.insulin.InsulinAdvisor
import de.diamind.ai.storage.Preferences.loadText
import de.diamind.ai.storage.Preferences.saveText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object DiaMindBrain {
    fun answer(context: Context, input: String): String {
        val text = input.trim()
        if (text.isBlank()) return "Ich höre zu. Schreib oder sprich einfach, was du brauchst."

        if (text.lowercase().startsWith("merke dir:")) {
            val content = text.substringAfter(":").trim()
            val parts = content.split("=")
            return if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                saveText(context, "memory_${key.lowercase()}", value)
                saveText(context, "assistantBubble", "Ich habe mir gemerkt: $key")
                "Okay, ich merke mir: $key = $value"
            } else {
                "Benutze: Merke dir: Begriff = Antwort"
            }
        }

        if (text.lowercase().startsWith("merke")) {
            val note = text.substringAfter(':', text).removePrefix("Merke").removePrefix("merke").trim(' ', ',', ':', '.')
            return saveRoadmapNote(context, note.ifBlank { text })
        }

        detectTherapyUpdate(context, text)?.let { return it }

        detectCorrectionRequest(context, text)?.let { return it }

        detectMealCorrection(context, text)?.let { return it }

        detectMealRequest(context, text)?.let { return it }

        val remembered = loadText(context, "memory_${text.lowercase()}", "")
        if (remembered.isNotBlank()) return remembered

        return when {
            text.contains("hallo", true) ->
                "Hallo Adam. Ich bin bereit. Du kannst mir direkt Essen, Insulin, Therapieänderungen oder Beobachtungen sagen."

            text.contains("spritz", true) || text.contains("ess-abstand", true) || text.contains("abstand", true) -> {
                val glucose = loadText(context, "glucose", "?")
                val trend = loadText(context, "glucoseTrend", "manual")
                InsulinAdvisor.preMealTimingAdvice(context, glucose, trend)
            }

            text.contains("motivation", true) ->
                "Du musst nicht perfekt sein. Entscheidend ist, dass DiaMind mit dir Muster erkennt und dich Schritt für Schritt entlastet."

            text.contains("hba1c", true) ->
                "Der HbA1c ist dein Langzeit-Kompass. Einzelne schwierige Werte sind wichtig, aber sie bestimmen nicht allein deine Richtung."

            text.contains("datenschutz", true) ->
                "DiaMind arbeitet standardmäßig lokal. Online-KI wird nur genutzt, wenn du sie aktivierst und einen API-Key hinterlegst."

            text.contains("faktor", true) || text.contains("lern", true) ->
                InsulinAdvisor.learningSummary(context)

            text.contains("ki", true) || text.contains("gemini", true) || text.contains("openai", true) ->
                "DiaMind nutzt ein Hybrid-Prinzip: lokal für Datenschutz, Gemini/OpenAI optional als Lehrer. Bestätigte Mahlzeiten werden als Wahrheit gespeichert, damit die lokale Schätzung mitlernt."

            text.contains("essen", true) || text.contains("mahlzeit", true) || text.contains("ke", true) ->
                "Gehe auf Essen oder nutze die Kamera auf der Startseite. Nach der Schätzung kannst du KE und Bolus korrigieren und übernehmen. Erst dann lernt DiaMind daraus."

            text.contains("insulin", true) || text.contains("restinsulin", true) || text.contains("iob", true) -> {
                val iob = InsulinAdvisor.activeInsulin(context)
                "Restinsulin aktuell rechnerisch: ${InsulinAdvisor.format(iob)} IE. Du kannst dein Therapieprofil direkt per Chat ändern, z. B.: 'Ich nehme Lyumjev als Bolus und Tresiba 26 IE abends.'"
            }

            text.contains("xdrip", true) ->
                "xDrip ist lokal angebunden. DiaMind nutzt Glukose und Trend für Hinweise, Spritz-Ess-Abstand und Lernbewertung."

            text.contains("roadmap", true) || text.contains("liste", true) || text.contains("ideen", true) ->
                loadText(context, "roadmapNotes", "Noch keine lokalen Verbesserungspunkte in der App gespeichert.")

            text.contains("name", true) || text.contains("wie heißt", true) ->
                "Ich heiße DiaMind. Ich soll mit dir lernen, nicht über dich entscheiden."

            else ->
                "Ich habe dich noch nicht eindeutig verstanden. Schreib einfach wie im Alltag, z. B. '3 Stücke Pizza', 'doch nur 2 Stück', 'ich habe 8 IE gespritzt', 'mein Wert steigt auf 180' oder 'rechne Korrektur'. Ich versuche dann direkt Bolus, SEA und Erklärung zusammenzufassen."
        }
    }


    private fun actionFirstMealMessage(
        context: Context,
        doseText: String,
        timing: String,
        headline: String,
        details: String,
        note: String = ""
    ): String {
        val sea = actionSea(timing)
        val shortNote = note.ifBlank { "Wenn etwas nicht stimmt, schreib es direkt in den Chat." }
        return """
            ━━━━━━━━━━━━━━
            🔴 $doseText IE spritzen
            ⏱ $sea
            💬 $headline

            Details:
            $details

            $shortNote
        """.trimIndent()
    }

    private fun actionSea(timing: String): String {
        val lower = timing.lowercase(Locale.getDefault())
        return when {
            lower.contains("erst essen") || lower.contains("niedrig") || lower.contains("fall") -> "erst essen / keinen Vorabstand"
            lower.contains("10") -> "10 Minuten warten"
            lower.contains("5") -> "5 Minuten warten"
            lower.contains("erhöht") || lower.contains("steig") -> "jetzt spritzen, kurz warten"
            lower.contains("direkt") || lower.contains("sofort") -> "jetzt / zum ersten Bissen"
            else -> "jetzt / kurz prüfen"
        }
    }

    private fun shortGlucoseReason(glucose: String, trend: String, carbs: Int): String {
        val g = glucose.toIntOrNull()
        val t = trendLabelShort(trend)
        return when {
            g != null && g < 100 && t.contains("fall") -> "Wert ist niedrig/fallend. Erst essen, nicht lange warten."
            g != null && g >= 180 -> "Wert ist erhöht. Bolus nicht aufschieben."
            t.contains("steigend") -> "Wert steigt. Ein kurzer Abstand kann sinnvoll sein."
            carbs >= 70 -> "Viele KH. Verlauf später nochmal prüfen."
            else -> "Normale Mahlzeit. Erst bestätigen, dann lerne ich daraus."
        }
    }

    private fun detectMealRequest(context: Context, text: String): String? {
        val lower = text.lowercase(Locale.getDefault())
        val foodWords = listOf(
            "pizza", "nudel", "nudeln", "pasta", "spaghetti", "reis", "kartoffel", "klöße", "kloesse",
            "pommes", "brot", "brötchen", "broetchen", "döner", "doener", "burger", "banane", "apfel",
            "müsli", "muesli", "corny", "riegel", "schokolade", "kuchen", "salat", "suppe", "eis",
            "esse", "gegessen", "mahlzeit", "frühstück", "mittagessen", "abendessen", "stücke", "stuecke", "stück"
        )
        if (foodWords.none { lower.contains(it) }) return null

        val previousFood = loadText(context, "lastMealDescription", "")
        val normalized = (lower + " " + if (foodWords.any { lower.contains(it) && !listOf("stücke", "stuecke", "stück").contains(it) }) "" else previousFood.lowercase(Locale.getDefault()))
            .replace("klöße", "kartoffel klöße")
            .replace("kloesse", "kartoffel kloesse")
            .replace("corny", "müsli riegel")
        val portion = when {
            lower.contains("klein") -> "klein"
            lower.contains("groß") || lower.contains("gross") -> "groß"
            else -> "normal"
        }
        val estimate = FoodEstimator.estimate(normalized, portion)
        val multiplier = pieceMultiplier(lower, estimate.matchedFoods)
        val carbs = (estimate.totalCarbs * multiplier).toInt().coerceAtLeast(1)
        val ke = carbs / 10.0
        val slot = InsulinAdvisor.currentSlot()
        val factor = InsulinAdvisor.factorForSlot(context, slot)
        val dose = InsulinAdvisor.doseForKe(context, ke, factor)
        val glucose = loadText(context, "glucose", "?")
        val trend = loadText(context, "glucoseTrend", "manual")
        val timing = InsulinAdvisor.preMealTimingAdvice(context, glucose, trend, carbs)
        val foodName = estimate.matchedFoods.firstOrNull() ?: text.take(40)

        saveText(context, "lastMealDescription", foodName)
        saveText(context, "lastMealCarbs", carbs.toString())
        saveText(context, "lastMealKe", String.format(Locale.GERMAN, "%.1f", ke))
        saveText(context, "lastRecommendedDose", InsulinAdvisor.formatDose(context, dose))

        val details = """
            Essen: $foodName
            KH: ca. $carbs g · KE: ${String.format(Locale.GERMAN, "%.1f", ke)}
            Faktor: $slot · ${String.format(Locale.GERMAN, "%.1f", factor)} IE/KE
            Glukose: $glucose mg/dL · Trend: ${trendLabelShort(trend)}
        """.trimIndent()
        val answer = actionFirstMealMessage(
            context = context,
            doseText = InsulinAdvisor.formatDose(context, dose),
            timing = timing,
            headline = shortGlucoseReason(glucose, trend, carbs),
            details = details,
            note = "Korrektur: z. B. „60 g KH“, „halbe Portion“ oder „ich habe 8 IE gespritzt“."
        )
        saveText(context, "assistantBubble", answer)
        return answer
    }

    private fun pieceMultiplier(text: String, foods: List<String>): Double {
        val number = extractPieceCount(text) ?: return 1.0
        val primary = foods.joinToString(" ").lowercase(Locale.getDefault())
        return when {
            primary.contains("pizza") -> (number / 4.0).coerceIn(0.25, 2.0)
            primary.contains("brot") -> (number / 2.0).coerceIn(0.5, 2.5)
            primary.contains("kartoff") -> (number / 3.0).coerceIn(0.5, 2.0)
            else -> 1.0
        }
    }

    private fun shortTiming(timing: String): String {
        val clean = timing.substringAfter("Spritz-Ess-Abstand:", timing).substringBefore(".").trim()
        return clean.ifBlank { "bitte individuell prüfen" }
    }

    private fun trendLabelShort(trend: String): String {
        val t = trend.lowercase(Locale.getDefault())
        return when {
            t.contains("doubleup") -> "schnell steigend"
            t.contains("singleup") || t.contains("fortyfiveup") -> "steigend"
            t.contains("doubledown") -> "schnell fallend"
            t.contains("singledown") || t.contains("fortyfivedown") -> "fallend"
            t.contains("flat") -> "stabil"
            else -> trend
        }
    }


    private fun detectCorrectionRequest(context: Context, text: String): String? {
        val lower = text.lowercase(Locale.getDefault())
        val wantsCorrection = lower.contains("korrigier") || lower.contains("korrektur") || lower.contains("nachsprit") || lower.contains("zu hoch") || lower.contains("steigt") || lower.contains("steigend")
        val glucoseMention = Regex("""\b(\d{2,3})\b""").find(lower)?.groupValues?.getOrNull(1)
        if (!wantsCorrection && glucoseMention == null) return null
        if (lower.contains("pizza") || lower.contains("stück") || lower.contains("stueck") || lower.contains("essen")) return null
        val glucose = glucoseMention ?: loadText(context, "glucose", "?")
        val trend = when {
            lower.contains("schnell") && lower.contains("steig") -> "DoubleUp"
            lower.contains("steig") -> "SingleUp"
            lower.contains("schnell") && lower.contains("fall") -> "DoubleDown"
            lower.contains("fall") -> "SingleDown"
            else -> loadText(context, "glucoseTrend", "manual")
        }
        val correction = InsulinAdvisor.correctionSuggestion(context, glucose, trend)
        val doseText = Regex("""(\d{1,2})(?:[,.](\d))?\s*IE""").find(correction)?.value?.substringBefore(" ")
        if (doseText != null) saveText(context, "lastRecommendedDose", doseText)
        val answer = """
            ━━━━━━━━━━━━━━
            🔴 Korrektur prüfen
            💉 $correction
            ⏱ jetzt, wenn kein starkes Restinsulin wirkt
            💬 Kurz: Wert/Trend sprechen für eine Korrektur. Unten mit +/− anpassen und bestätigen.
        """.trimIndent()
        saveText(context, "assistantBubble", answer)
        return answer
    }

    private fun detectMealCorrection(context: Context, text: String): String? {
        val lower = text.lowercase(Locale.getDefault())
        val lastFood = loadText(context, "lastMealDescription", "")
        val lastCarbs = loadText(context, "lastMealCarbs", "").toIntOrNull()
        val lastDose = loadText(context, "lastRecommendedDose", "").replace(',', '.').toDoubleOrNull()

        if (lower in listOf("bestätigen", "bestaetigen", "ok", "passt", "stimmt", "bestätige", "bestaetige")) {
            val carbs = lastCarbs ?: return "Ich habe noch keine offene Mahlzeit zum Bestätigen."
            val slot = InsulinAdvisor.currentSlot()
            val ke = carbs / 10.0
            val factor = InsulinAdvisor.factorForSlot(context, slot)
            val recommended = lastDose ?: InsulinAdvisor.doseForKe(context, ke, factor)
            val glucose = loadText(context, "glucose", "?")
            val trend = loadText(context, "glucoseTrend", "manual")
            val record = InsulinAdvisor.saveMealAssumption(context, lastFood, carbs, ke, recommended, "", slot, glucose, trend)
            return "━━━━━━━━━━━━━━\n✅ Gespeichert\n💉 ${InsulinAdvisor.formatDose(context, recommended)} IE\n💬 Mahlzeit gespeichert. Ich nutze das zum Lernen.\n\nDetails:\n$record"
        }

        if (lower.contains("mehr bolus") || lower == "+" || lower.contains("plus bolus")) {
            val current = lastDose ?: return "Ich habe noch keinen aktuellen Bolusvorschlag zum Erhöhen."
            val updated = current + 1.0
            saveText(context, "lastRecommendedDose", InsulinAdvisor.formatDose(context, updated))
            return "🔴 Bolus auf ${InsulinAdvisor.formatDose(context, updated)} IE erhöht. Wenn das passt, drücke Bestätigen."
        }
        if (lower.contains("weniger bolus") || lower == "-" || lower.contains("minus bolus")) {
            val current = lastDose ?: return "Ich habe noch keinen aktuellen Bolusvorschlag zum Senken."
            val updated = (current - 1.0).coerceAtLeast(0.0)
            saveText(context, "lastRecommendedDose", InsulinAdvisor.formatDose(context, updated))
            return "🔴 Bolus auf ${InsulinAdvisor.formatDose(context, updated)} IE gesenkt. Wenn das passt, drücke Bestätigen."
        }

        val actualDose = extractDose(text)
        val pieceCountEarly = extractPieceCount(text)
        val correctedCarbsFromPieces = if (pieceCountEarly != null && (lastFood.isNotBlank() || lower.contains("pizza"))) {
            val baseFood = (lastFood.ifBlank { text }).lowercase(Locale.getDefault())
            when {
                baseFood.contains("pizza") -> (pieceCountEarly * 22.5).roundToInt().coerceAtLeast(1)
                baseFood.contains("kartoff") -> (pieceCountEarly * 15).roundToInt().coerceAtLeast(1)
                baseFood.contains("brot") -> (pieceCountEarly * 20).roundToInt().coerceAtLeast(1)
                else -> lastCarbs
            }
        } else null

        if (actualDose != null && (lower.contains("gespritzt") || lower.contains("genommen") || lower.contains("gegeben"))) {
            val carbs = correctedCarbsFromPieces ?: lastCarbs
                ?: return "Ich speichere die gespritzten ${InsulinAdvisor.formatDose(context, actualDose)} IE, brauche aber noch Mahlzeit/KH dazu. Sag z. B.: 'Pizza 3 Stücke' oder '60 g KH'."
            val slot = InsulinAdvisor.currentSlot()
            val ke = carbs / 10.0
            val factor = InsulinAdvisor.factorForSlot(context, slot)
            val recommended = InsulinAdvisor.doseForKe(context, ke, factor)
            val glucose = loadText(context, "glucose", "?")
            val trend = loadText(context, "glucoseTrend", "manual")
            val food = when {
                lower.contains("pizza") -> "Pizza"
                lastFood.isNotBlank() -> lastFood
                else -> "Mahlzeit"
            }
            saveText(context, "lastMealDescription", food)
            saveText(context, "lastMealCarbs", carbs.toString())
            saveText(context, "lastMealKe", String.format(Locale.GERMAN, "%.1f", ke))
            saveText(context, "lastRecommendedDose", InsulinAdvisor.formatDose(context, recommended))
            val record = InsulinAdvisor.saveMealAssumption(context, food, carbs, ke, recommended, actualDose.toString(), slot, glucose, trend)
            val delta = actualDose - recommended
            val note = when {
                delta < -2.0 -> "Du hast weniger als die rechnerische Empfehlung gespritzt. Ich merke mir das und beobachte den späteren Verlauf als Lernsignal."
                delta > 2.0 -> "Du hast mehr als die rechnerische Empfehlung gespritzt. Ich merke mir das, bitte Verlauf eng beobachten."
                else -> "Das liegt nahe an der Empfehlung."
            }
            return "━━━━━━━━━━━━━━\n✅ Gespeichert\n💉 ${InsulinAdvisor.formatDose(context, actualDose)} IE\n💬 $note\n\nDetails:\n$record"
        }

        val explicitCarbs = Regex("(\\d{1,3})\\s*(g|gramm)?\\s*(kh|kohlenhydrat)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (explicitCarbs != null) {
            return recalcFromCarbs(context, explicitCarbs, "Ich habe deine KH-Korrektur übernommen.")
        }

        val pieceCount = extractPieceCount(text)
        if (pieceCount != null && lastFood.isNotBlank()) {
            val baseFood = lastFood.lowercase(Locale.getDefault())
            val carbs = when {
                baseFood.contains("pizza") -> (pieceCount * 22.5).roundToInt().coerceAtLeast(1)
                baseFood.contains("kartoff") -> (pieceCount * 15).roundToInt().coerceAtLeast(1)
                baseFood.contains("brot") -> (pieceCount * 20).roundToInt().coerceAtLeast(1)
                else -> lastCarbs ?: return null
            }
            return recalcFromCarbs(context, carbs, "Okay, ich rechne neu mit $pieceCount Stück von $lastFood.")
        }

        if ((lower.contains("halb") || lower.contains("halbe")) && lastCarbs != null) {
            return recalcFromCarbs(context, (lastCarbs * 0.5).roundToInt().coerceAtLeast(1), "Okay, ich rechne neu mit der halben Portion.")
        }

        return null
    }

    private fun recalcFromCarbs(context: Context, carbs: Int, intro: String): String {
        val slot = InsulinAdvisor.currentSlot()
        val factor = InsulinAdvisor.factorForSlot(context, slot)
        val ke = carbs / 10.0
        val dose = InsulinAdvisor.doseForKe(context, ke, factor)
        val glucose = loadText(context, "glucose", "?")
        val trend = loadText(context, "glucoseTrend", "manual")
        val timing = InsulinAdvisor.preMealTimingAdvice(context, glucose, trend, carbs)
        saveText(context, "lastMealCarbs", carbs.toString())
        saveText(context, "lastMealKe", String.format(Locale.GERMAN, "%.1f", ke))
        saveText(context, "lastRecommendedDose", InsulinAdvisor.formatDose(context, dose))
        val details = """
            $intro
            KH: ca. $carbs g · KE: ${String.format(Locale.GERMAN, "%.1f", ke)}
            Faktor: $slot · ${String.format(Locale.GERMAN, "%.1f", factor)} IE/KE
            Glukose: $glucose mg/dL · Trend: ${trendLabelShort(trend)}
        """.trimIndent()
        val answer = actionFirstMealMessage(
            context = context,
            doseText = InsulinAdvisor.formatDose(context, dose),
            timing = timing,
            headline = "Neu gerechnet. Unten kannst du direkt bestätigen.",
            details = details
        )
        saveText(context, "assistantBubble", answer)
        return answer
    }

    private fun extractPieceCount(text: String): Double? {
        val lower = text.lowercase(Locale.getDefault())
        val digit = Regex("(\\d{1,2})\\s*(stück|stücke|stueck|stuecke|teile|scheiben)", RegexOption.IGNORE_CASE).find(lower)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        if (digit != null) return digit
        val words = mapOf("ein" to 1.0, "eine" to 1.0, "zwei" to 2.0, "drei" to 3.0, "vier" to 4.0, "fünf" to 5.0, "fuenf" to 5.0, "sechs" to 6.0)
        for ((word, value) in words) {
            if (lower.contains("$word stück") || lower.contains("$word stueck") || lower.contains("$word teile") || lower.contains("$word scheiben")) return value
        }
        return null
    }

    private fun extractDose(text: String): Double? {
        val lower = text.lowercase(Locale.getDefault())
        val digit = Regex("(\\d{1,2})(?:[,.](\\d))?\\s*(ie|einheiten|einheit)", RegexOption.IGNORE_CASE).find(lower)
        if (digit != null) return digit.value.substringBefore(" ").replace(',', '.').toDoubleOrNull()
        val words = mapOf("eins" to 1.0, "eine" to 1.0, "zwei" to 2.0, "drei" to 3.0, "vier" to 4.0, "fünf" to 5.0, "fuenf" to 5.0, "sechs" to 6.0, "sieben" to 7.0, "acht" to 8.0, "neun" to 9.0, "zehn" to 10.0, "elf" to 11.0, "zwölf" to 12.0, "zwoelf" to 12.0)
        for ((word, value) in words) {
            if (lower.contains("$word einheit") || lower.contains("$word ie")) return value
        }
        return null
    }

    private fun detectTherapyUpdate(context: Context, text: String): String? {
        val lower = text.lowercase(Locale.getDefault())
        var changed = false
        val messages = mutableListOf<String>()

        val bolusNames = listOf("lyumjev", "humalog", "novorapid", "fiasp", "apidra", "lumjev")
        val basalNames = listOf("tresiba", "lantus", "toujeo", "levemir", "abasaglar")

        bolusNames.firstOrNull { lower.contains(it) }?.let { raw ->
            val name = if (raw == "lumjev") "Lyumjev" else raw.replaceFirstChar { it.uppercase() }
            saveText(context, "bolusInsulin", name)
            if (name.contains("Lyumjev", true)) saveText(context, "bolusStep", "1.0")
            messages += "Bolusinsulin: $name"
            changed = true
        }

        basalNames.firstOrNull { lower.contains(it) }?.let { raw ->
            val name = raw.replaceFirstChar { it.uppercase() }
            saveText(context, "basalInsulin", name)
            messages += "Basalinsulin: $name"
            changed = true
        }

        val doseRegex = Regex("(\\d{1,3})(?:[,.](\\d))?\\s*(ie|einheiten|einheit)", RegexOption.IGNORE_CASE)
        doseRegex.find(text)?.let { match ->
            val dose = match.value.substringBefore(" ").replace(',', '.')
            if (lower.contains("tresiba") || lower.contains("basal") || lower.contains("abends") || lower.contains("morgens")) {
                saveText(context, "basalDose", dose)
                messages += "Basaldosis: $dose IE"
                changed = true
            }
        }

        if (lower.contains("nur ganze") || lower.contains("keine 0,5") || lower.contains("keine komma") || lower.contains("ganze stellen")) {
            saveText(context, "bolusStep", "1.0")
            messages += "Bolusschritt: ganze IE"
            changed = true
        }

        val factorRegex = Regex("(frühstück|fruehstueck|morgen|mittag|abend).*?(\\d+[,.]?\\d*)\\s*(ie)?\\s*/?\\s*(ke|10 g|10g)?", RegexOption.IGNORE_CASE)
        factorRegex.findAll(text).forEach { match ->
            val slotText = match.groupValues[1].lowercase(Locale.getDefault())
            val value = match.groupValues[2].replace(',', '.')
            when {
                slotText.contains("früh") || slotText.contains("frueh") || slotText.contains("morgen") -> saveText(context, "factorMorning", value)
                slotText.contains("mittag") -> saveText(context, "factorNoon", value)
                slotText.contains("abend") -> saveText(context, "factorEvening", value)
            }
            messages += "Faktor ${match.groupValues[1]}: $value IE/KE"
            changed = true
        }

        if (!changed) return null
        val result = "Therapieprofil aktualisiert:\n" + messages.distinct().joinToString("\n") + "\n\nBitte prüfe die Angaben im Diabetes-Profil."
        saveText(context, "assistantBubble", result)
        return result
    }

    private fun saveRoadmapNote(context: Context, note: String): String {
        val time = SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(Date())
        val old = loadText(context, "roadmapNotes", "")
        val entry = "☐ $time · $note"
        val updated = if (old.isBlank()) entry else old + "\n" + entry
        saveText(context, "roadmapNotes", updated)
        saveText(context, "assistantBubble", "Ich habe den Punkt für die nächste Build-Liste gespeichert.")
        return "Gemerkt für die Build-Liste."
    }
}
