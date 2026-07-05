package de.diamind.ai.learning

import android.content.Context
import de.diamind.ai.storage.Preferences.loadText
import de.diamind.ai.storage.Preferences.saveText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object FoodLearningBridge {

    fun previewText(
        mode: String,
        localCarbs: Int,
        onlineCarbs: Int,
        proposedCarbs: Int,
        primaryFood: String
    ): String {
        val onlineText = if (onlineCarbs > 0) "$onlineCarbs g" else "nicht verwendet"
        val deltaText = if (onlineCarbs > 0) {
            val delta = onlineCarbs - localCarbs
            val sign = if (delta >= 0) "+" else ""
            "$sign$delta g zur lokalen Schätzung"
        } else {
            "nur lokale Schätzung"
        }

        return "Online/Lokal-Lernen" +
            "\nLebensmittel: ${primaryFood.ifBlank { "unbekannt" }}" +
            "\nLokale Schätzung: $localCarbs g KH" +
            "\nOnline-Schätzung: $onlineText" +
            "\nAktueller Vorschlag: $proposedCarbs g KH" +
            "\nAbgleich: $deltaText" +
            "\nModus: $mode" +
            "\nNach Bestätigung merkt DiaMind sich, welche Schätzung bei dir realistischer war."
    }

    fun recordConfirmedMeal(
        context: Context,
        mode: String,
        localCarbs: Int,
        onlineCarbs: Int,
        confirmedCarbs: Int,
        primaryFood: String,
        portion: String,
        slot: String
    ): String {
        val total = (loadText(context, "bridge_total_count", "0").toIntOrNull() ?: 0) + 1
        saveText(context, "bridge_total_count", total.toString())

        val usedOnline = onlineCarbs > 0 && mode != "Lokal"
        if (usedOnline) increment(context, "bridge_online_count") else increment(context, "bridge_local_count")

        val localError = abs(confirmedCarbs - localCarbs)
        val onlineError = if (onlineCarbs > 0) abs(confirmedCarbs - onlineCarbs) else localError

        when {
            onlineCarbs <= 0 -> increment(context, "bridge_winner_local")
            onlineError < localError -> increment(context, "bridge_winner_online")
            localError < onlineError -> increment(context, "bridge_winner_local")
            else -> increment(context, "bridge_winner_equal")
        }

        val key = foodKey(primaryFood)
        val countKey = "bridge_food_${key}_count"
        val avgKey = "bridge_food_${key}_avg_carbs"
        val count = (loadText(context, countKey, "0").toIntOrNull() ?: 0) + 1
        val oldAvg = loadText(context, avgKey, confirmedCarbs.toString()).replace(',', '.').toDoubleOrNull() ?: confirmedCarbs.toDouble()
        val newAvg = ((oldAvg * (count - 1)) + confirmedCarbs) / count
        saveText(context, countKey, count.toString())
        saveText(context, avgKey, format(newAvg))
        saveText(context, "bridge_last_food_key", key)
        saveText(context, "bridge_last_food_name", primaryFood.ifBlank { "unbekannt" })

        val time = SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(Date())
        val winner = when {
            onlineCarbs <= 0 -> "lokal"
            onlineError < localError -> "online"
            localError < onlineError -> "lokal"
            else -> "gleich gut"
        }

        val record = """
            $time · Learning-Bridge
            Lebensmittel: ${primaryFood.ifBlank { "unbekannt" }} · Portion: $portion · $slot
            Lokal: $localCarbs g KH
            Online: ${if (onlineCarbs > 0) "$onlineCarbs g KH" else "nicht genutzt"}
            Bestätigt: $confirmedCarbs g KH
            Näher dran: $winner
            Persönlicher Durchschnitt für dieses Essen: ${format(newAvg)} g KH aus $count Bestätigung(en)
        """.trimIndent()

        saveText(context, "bridge_last_record", record)
        saveText(context, "foodLearningBridgeSummary", summary(context))
        return record
    }

    fun summary(context: Context): String {
        val total = loadText(context, "bridge_total_count", "0")
        val online = loadText(context, "bridge_online_count", "0")
        val local = loadText(context, "bridge_local_count", "0")
        val onlineWins = loadText(context, "bridge_winner_online", "0")
        val localWins = loadText(context, "bridge_winner_local", "0")
        val equal = loadText(context, "bridge_winner_equal", "0")
        val last = loadText(context, "bridge_last_record", "Noch keine bestätigte Mahlzeit für den Online/Lokal-Abgleich.")

        return """
            Online/Lokal-Lernstand
            Bestätigte Mahlzeiten: $total
            Mit Online-KI: $online · nur lokal: $local
            Näher dran: Online $onlineWins · Lokal $localWins · gleich $equal

            Letzter Abgleich
            $last
        """.trimIndent()
    }

    private fun increment(context: Context, key: String) {
        val value = (loadText(context, key, "0").toIntOrNull() ?: 0) + 1
        saveText(context, key, value.toString())
    }

    private fun foodKey(name: String): String {
        return name.lowercase(Locale.getDefault())
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "unknown" }
    }

    private fun format(value: Double): String {
        return String.format(Locale.GERMAN, "%.1f", value)
    }
}
