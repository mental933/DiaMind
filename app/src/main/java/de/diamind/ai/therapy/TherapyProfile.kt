package de.diamind.ai.therapy

import android.content.Context
import de.diamind.ai.storage.Preferences.loadText
import de.diamind.ai.storage.Preferences.saveText
import java.util.Locale

object TherapyProfile {
    fun initializeDefaults(context: Context) {
        if (loadText(context, "therapyBolusInsulin", "").isBlank()) saveText(context, "therapyBolusInsulin", "Lyumjev")
        if (loadText(context, "therapyBasalInsulin", "").isBlank()) saveText(context, "therapyBasalInsulin", "Tresiba")
        if (loadText(context, "therapyDoseStep", "").isBlank()) saveText(context, "therapyDoseStep", "1.0")
        if (loadText(context, "therapyNotes", "").isBlank()) saveText(context, "therapyNotes", "Noch keine Therapie-Notizen gespeichert.")
    }

    fun bolusInsulin(context: Context): String {
        initializeDefaults(context)
        return loadText(context, "therapyBolusInsulin", "Lyumjev")
    }

    fun basalInsulin(context: Context): String {
        initializeDefaults(context)
        return loadText(context, "therapyBasalInsulin", "Tresiba")
    }

    fun doseStep(context: Context): Double {
        initializeDefaults(context)
        return loadText(context, "therapyDoseStep", "1.0").replace(',', '.').toDoubleOrNull() ?: 1.0
    }

    fun roundDose(context: Context, dose: Double): Double {
        val step = doseStep(context).coerceAtLeast(0.1)
        return kotlin.math.round(dose / step) * step
    }

    fun rememberTherapyFromText(context: Context, raw: String): String {
        initializeDefaults(context)
        val text = raw.lowercase(Locale.getDefault())
        val updates = mutableListOf<String>()

        val bolus = when {
            text.contains("lyumjev") -> "Lyumjev"
            text.contains("fiasp") -> "Fiasp"
            text.contains("novorapid") || text.contains("novo rapid") -> "NovoRapid"
            text.contains("humalog") -> "Humalog"
            text.contains("apidra") -> "Apidra"
            else -> null
        }
        if (bolus != null) {
            saveText(context, "therapyBolusInsulin", bolus)
            updates += "Bolusinsulin: $bolus"
        }

        val basal = when {
            text.contains("tresiba") -> "Tresiba"
            text.contains("lantus") -> "Lantus"
            text.contains("toujeo") -> "Toujeo"
            text.contains("levemir") -> "Levemir"
            text.contains("basaglar") -> "Basaglar"
            else -> null
        }
        if (basal != null) {
            saveText(context, "therapyBasalInsulin", basal)
            updates += "Basalinsulin: $basal"
        }

        if (text.contains("ganze") || text.contains("nur ganze") || text.contains("keine komma") || text.contains("1er schritt")) {
            saveText(context, "therapyDoseStep", "1.0")
            updates += "Dosierschritt: ganze IE"
        } else if (text.contains("0,5") || text.contains("0.5") || text.contains("halbe")) {
            saveText(context, "therapyDoseStep", "0.5")
            updates += "Dosierschritt: 0,5 IE"
        }

        val oldNotes = loadText(context, "therapyNotes", "")
        val newNotes = (oldNotes + "\n" + raw.trim()).trim()
        saveText(context, "therapyNotes", newNotes)

        return if (updates.isEmpty()) {
            "Ich habe deine Therapie-Notiz gespeichert. Noch nicht alles konnte strukturiert erkannt werden.\n\n${summary(context)}"
        } else {
            "Therapieprofil aktualisiert:\n${updates.joinToString("\n")}\n\n${summary(context)}"
        }
    }

    fun summary(context: Context): String {
        initializeDefaults(context)
        val doseText = if (doseStep(context) >= 1.0) "nur ganze IE" else "${format(doseStep(context))} IE-Schritte"
        return """
            Therapieprofil
            Bolusinsulin: ${bolusInsulin(context)}
            Basalinsulin: ${basalInsulin(context)}
            Dosierschritt: $doseText

            Hinweis: DiaMind unterstützt deine Entscheidung, übernimmt aber keine automatische Therapieentscheidung.
        """.trimIndent()
    }

    private fun format(value: Double): String {
        return String.format(Locale.GERMAN, "%.1f", value)
    }
}
