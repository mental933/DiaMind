package de.diamind.ai.ai

import android.content.Context
import de.diamind.ai.insulin.InsulinAdvisor
import de.diamind.ai.storage.Preferences.loadText
import de.diamind.ai.storage.Preferences.saveText

object DiaMindBrain {
    fun answer(context: Context, input: String): String {
        val text = input.trim()

        if (text.lowercase().startsWith("merke dir:")) {
            val content = text.substringAfter(":").trim()
            val parts = content.split("=")
            return if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                saveText(context, "memory_${key.lowercase()}", value)
                "Okay, ich merke mir: $key = $value"
            } else {
                "Benutze: Merke dir: Begriff = Antwort"
            }
        }

        val remembered = loadText(context, "memory_${text.lowercase()}", "")
        if (remembered.isNotBlank()) return remembered

        return when {
            text.contains("hallo", true) ->
                "Hallo Adam. Schön, dass du da bist."
            text.contains("motivation", true) ->
                "Du musst nicht perfekt sein. Viele kleine gute Entscheidungen verbessern langfristig deinen Verlauf."
            text.contains("hba1c", true) ->
                "Der HbA1c ist dein Langzeit-Kompass. Einzelne schwierige Tage verändern ihn kaum."
            text.contains("datenschutz", true) ->
                "DiaMind arbeitet offline. Keine Cloud, keine Werbung, keine versteckte Datenübertragung."
            text.contains("faktor", true) || text.contains("lern", true) ->
                InsulinAdvisor.learningSummary(context)
            text.contains("essen", true) || text.contains("mahlzeit", true) || text.contains("ke", true) ->
                "Im Essen-Bereich kannst du ein Foto machen, KE schätzen und eine Bolusannahme auf Basis deiner Tagesfaktoren speichern. Wenn du keinen anderen Wert einträgst, wird die Empfehlung als ausgeführt angenommen."
            text.contains("insulin", true) ->
                "Insulin-Funktionen sind vorbereitet. Automatische Dosierungen gibt es nicht."
            text.contains("xdrip", true) ->
                "xDrip ist aktiv vorbereitet. Wenn xDrip Broadcasts sendet, übernimmt DiaMind den aktuellen Glukosewert lokal."
            text.contains("name", true) || text.contains("wie heißt", true) ->
                "Ich heiße DiaMind."
            else ->
                "Das weiß ich noch nicht. Du kannst es mir beibringen mit: Merke dir: Begriff = Antwort"
        }
    }
}
