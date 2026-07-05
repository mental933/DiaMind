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

        if (text.lowercase().startsWith("merke")) {
            val note = text.substringAfter(':', text).removePrefix("Merke").removePrefix("merke").trim(' ', ',', ':', '.')
            return saveRoadmapNote(context, note.ifBlank { text })
        }

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

        detectTherapyUpdate(context, text)?.let { return it }

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

            text.contains("insulin", true) ->
                "Dein Therapieprofil kann per Chat aktualisiert werden. Beispiel: 'Ich nehme Lyumjev als Bolus und Tresiba 26 IE abends.'"

            text.contains("xdrip", true) ->
                "xDrip ist lokal angebunden. DiaMind nutzt Glukose und Trend für Hinweise, Spritz-Ess-Abstand und Lernbewertung."

            text.contains("roadmap", true) || text.contains("liste", true) || text.contains("ideen", true) ->
                loadText(context, "roadmapNotes", "Noch keine lokalen Verbesserungspunkte in der App gespeichert.")

            text.contains("name", true) || text.contains("wie heißt", true) ->
                "Ich heiße DiaMind. Ich soll mit dir lernen, nicht über dich entscheiden."

            else ->
                "Das weiß ich noch nicht sicher. Wenn es dauerhaft wichtig ist, schreibe: Merke: ... Dann landet es in der Build-Liste."
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

        val answer = """
            🔴 Empfehlung: ${InsulinAdvisor.formatDose(context, dose)} IE
            ⏱ SEA: ${shortTiming(timing)}

            Ich rechne mit: $foodName.
            KH: ca. $carbs g · KE: ${String.format(Locale.GERMAN, "%.1f", ke)}
            Faktor: $slot · ${String.format(Locale.GERMAN, "%.1f", factor)} IE/KE
            Glukose: $glucose mg/dL · Trend: ${trendLabelShort(trend)}

            $timing

            Du kannst direkt korrigieren: „es waren 3 Stücke“, „nur halbe Pizza“, „das waren 60 g KH“ oder „ich habe 10 IE gespritzt“. Mit Bestätigen speichert DiaMind die Mahlzeit zum Lernen.
        """.trimIndent()
        saveText(context, "assistantBubble", answer)
        appendSystemNews(context, "Mahlzeit", "$foodName · $carbs g KH · ${InsulinAdvisor.formatDose(context, dose)} IE")
        return answer
    }

    private fun pieceMultiplier(text: String, foods: List<String>): Double {
        val number = Regex("(\\d{1,2})\\s*(stück|stücke|stueck|scheiben|teile)", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?: return 1.0
        val primary = foods.joinToString(" ").lowercase(Locale.getDefault())
        return when {
            primary.contains("pizza") -> (number / 4.0).coerceIn(0.4, 2.0)
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


    private fun detectMealCorrection(context: Context, text: String): String? {
        val lower = text.lowercase(Locale.getDefault())
        val lastFood = loadText(context, "lastMealDescription", "")
        val lastCarbs = loadText(context, "lastMealCarbs", "").toIntOrNull()
        val lastDose = loadText(context, "lastRecommendedDose", "").replace(',', '.').toDoubleOrNull()
        val actualDose = extractDose(text)
        val pieceCount = extractPieceCount(text)

        if (lower in listOf("bestätigen", "bestaetigen", "ok", "passt", "stimmt", "bestätige", "bestaetige")) {
            val carbs = lastCarbs ?: return "Ich habe noch keine offene Mahlzeit zum Bestätigen. Sag mir z. B. 'Pizza' oder mache ein Foto."
            val slot = InsulinAdvisor.currentSlot()
            val ke = carbs / 10.0
            val factor = InsulinAdvisor.factorForSlot(context, slot)
            val recommended = lastDose ?: InsulinAdvisor.doseForKe(context, ke, factor)
            val glucose = loadText(context, "glucose", "?")
            val trend = loadText(context, "glucoseTrend", "manual")
            val record = InsulinAdvisor.saveMealAssumption(context, lastFood, carbs, ke, recommended, "", slot, glucose, trend)
            appendSystemNews(context, "Bestätigt", "$lastFood · $carbs g KH · ${InsulinAdvisor.formatDose(context, recommended)} IE")
            return "✅ Bestätigt und gespeichert.\n\n$record"
        }

        if (lower.contains("mehr bolus") || lower == "+" || lower.contains("plus bolus")) {
            val current = lastDose ?: return "Ich habe noch keinen aktuellen Bolusvorschlag zum Erhöhen."
            val updated = current + 1.0
            saveText(context, "lastRecommendedDose", InsulinAdvisor.formatDose(context, updated))
            appendSystemNews(context, "Bolus +", "Vorschlag auf ${InsulinAdvisor.formatDose(context, updated)} IE")
            return "🔴 Bolus auf ${InsulinAdvisor.formatDose(context, updated)} IE erhöht. Wenn das passt, drücke Bestätigen."
        }
        if (lower.contains("weniger bolus") || lower == "-" || lower.contains("minus bolus")) {
            val current = lastDose ?: return "Ich habe noch keinen aktuellen Bolusvorschlag zum Senken."
            val updated = (current - 1.0).coerceAtLeast(0.0)
            saveText(context, "lastRecommendedDose", InsulinAdvisor.formatDose(context, updated))
            appendSystemNews(context, "Bolus −", "Vorschlag auf ${InsulinAdvisor.formatDose(context, updated)} IE")
            return "🔴 Bolus auf ${InsulinAdvisor.formatDose(context, updated)} IE gesenkt. Wenn das passt, drücke Bestätigen."
        }

        val explicitCarbs = Regex("(\\d{1,3})\\s*(g|gramm)?\\s*(kh|kohlenhydrat)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (explicitCarbs != null) {
            val answer = recalcFromCarbs(context, explicitCarbs, "Ich habe deine KH-Korrektur übernommen.")
            if (actualDose != null && (lower.contains("gespritzt") || lower.contains("genommen") || lower.contains("gegeben"))) {
                return saveActualDoseAfterCorrection(context, explicitCarbs, actualDose, answer)
            }
            return answer
        }

        if (pieceCount != null && lastFood.isNotBlank()) {
            val baseFood = lastFood.lowercase(Locale.getDefault())
            val carbs = when {
                baseFood.contains("pizza") -> (pieceCount * 22.5).roundToInt().coerceAtLeast(1)
                baseFood.contains("kartoff") -> (pieceCount * 15).roundToInt().coerceAtLeast(1)
                baseFood.contains("brot") -> (pieceCount * 20).roundToInt().coerceAtLeast(1)
                else -> lastCarbs ?: return null
            }
            val answer = recalcFromCarbs(context, carbs, "Okay, ich rechne neu mit ${pieceCount.cleanNumber()} Stück von $lastFood.")
            if (actualDose != null && (lower.contains("gespritzt") || lower.contains("genommen") || lower.contains("gegeben"))) {
                return saveActualDoseAfterCorrection(context, carbs, actualDose, answer)
            }
            return answer
        }

        if ((lower.contains("halb") || lower.contains("halbe")) && lastCarbs != null) {
            val carbs = (lastCarbs * 0.5).roundToInt().coerceAtLeast(1)
            val answer = recalcFromCarbs(context, carbs, "Okay, ich rechne neu mit der halben Portion.")
            if (actualDose != null && (lower.contains("gespritzt") || lower.contains("genommen") || lower.contains("gegeben"))) {
                return saveActualDoseAfterCorrection(context, carbs, actualDose, answer)
            }
            return answer
        }

        if (actualDose != null && (lower.contains("gespritzt") || lower.contains("genommen") || lower.contains("gegeben"))) {
            val carbs = lastCarbs ?: return "Ich speichere die gespritzten ${InsulinAdvisor.formatDose(context, actualDose)} IE, brauche aber noch die Mahlzeit/KH dazu. Schreib z. B. '3 Stücke Pizza' oder '60 g KH'."
            return saveActualDoseAfterCorrection(context, carbs, actualDose, "")
        }

        return null
    }

    private fun saveActualDoseAfterCorrection(context: Context, carbs: Int, actualDose: Double, prefix: String): String {
        val lastFood = loadText(context, "lastMealDescription", "")
        val slot = InsulinAdvisor.currentSlot()
        val ke = carbs / 10.0
        val factor = InsulinAdvisor.factorForSlot(context, slot)
        val recommended = InsulinAdvisor.doseForKe(context, ke, factor)
        val glucose = loadText(context, "glucose", "?")
        val trend = loadText(context, "glucoseTrend", "manual")
        val record = InsulinAdvisor.saveMealAssumption(context, lastFood, carbs, ke, recommended, actualDose.toString(), slot, glucose, trend)
        val delta = actualDose - recommended
        val note = when {
            delta < -2.0 -> "Du hast weniger als den Vorschlag angegeben. Ich beobachte später, ob der Wert stärker steigt."
            delta > 2.0 -> "Du hast mehr als den Vorschlag angegeben. Bitte Verlauf beobachten, besonders wenn der Trend dreht."
            else -> "Das liegt nahe am aktuellen Vorschlag."
        }
        appendSystemNews(context, "Bolus gespeichert", "$lastFood · $carbs g KH · ${InsulinAdvisor.formatDose(context, actualDose)} IE")
        val intro = if (prefix.isBlank()) "" else "$prefix\n\n"
        return intro + "✅ Gespeichert: ${InsulinAdvisor.formatDose(context, actualDose)} IE gespritzt.\n$note\n\n$record"
    }

    private fun Double.cleanNumber(): String = if (this % 1.0 == 0.0) this.roundToInt().toString() else String.format(Locale.GERMAN, "%.1f", this)

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
        val answer = """
            $intro

            🔴 Neue Empfehlung: ${InsulinAdvisor.formatDose(context, dose)} IE
            ⏱ SEA: ${shortTiming(timing)}

            KH: ca. $carbs g · KE: ${String.format(Locale.GERMAN, "%.1f", ke)}
            Faktor: $slot · ${String.format(Locale.GERMAN, "%.1f", factor)} IE/KE
            Glukose: $glucose mg/dL · Trend: ${trendLabelShort(trend)}

            $timing
        """.trimIndent()
        saveText(context, "assistantBubble", answer)
        appendSystemNews(context, "Korrektur", "$carbs g KH · ${InsulinAdvisor.formatDose(context, dose)} IE")
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

    private fun appendSystemNews(context: Context, title: String, body: String) {
        val time = SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(Date())
        val old = loadText(context, "systemNews", "")
        val line = "$time · $title\n$body"
        saveText(context, "systemNews", (line + "\n" + old).lines().take(8).joinToString("\n"))
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
