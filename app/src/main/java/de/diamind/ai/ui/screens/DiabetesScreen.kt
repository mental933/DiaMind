package de.diamind.ai.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.diamind.ai.insulin.InsulinAdvisor
import de.diamind.ai.storage.Preferences.loadText
import de.diamind.ai.storage.Preferences.saveLong
import de.diamind.ai.storage.Preferences.saveText
import de.diamind.ai.ui.components.CardBox
import de.diamind.ai.ui.components.DiaMindMuted
import de.diamind.ai.ui.components.DiaMindRed
import de.diamind.ai.ui.components.DiaMindWarning
import de.diamind.ai.ui.components.EditField

@Composable
fun DiabetesScreen(context: Context) {
    InsulinAdvisor.initializeDefaults(context)

    var glucose by remember { mutableStateOf(loadText(context, "glucose", "118")) }
    var hba1c by remember { mutableStateOf(loadText(context, "hba1c", "6.8")) }
    var goal by remember { mutableStateOf(loadText(context, "goal", "HbA1c verbessern und ruhiger bleiben")) }
    var bolusInsulin by remember { mutableStateOf(loadText(context, "bolusInsulin", "Lyumjev")) }
    var basalInsulin by remember { mutableStateOf(loadText(context, "basalInsulin", "Tresiba")) }
    var factorMorning by remember { mutableStateOf(loadText(context, "factorMorning", "1.0")) }
    var factorNoon by remember { mutableStateOf(loadText(context, "factorNoon", "2.5")) }
    var factorEvening by remember { mutableStateOf(loadText(context, "factorEvening", "3.0")) }
    var followUpGlucose by remember { mutableStateOf("") }
    var learningText by remember { mutableStateOf(InsulinAdvisor.learningSummary(context)) }
    var proposalText by remember { mutableStateOf(InsulinAdvisor.openProposalText(context)) }

    CardBox(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("Diabetes-Profil", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        EditField("Glukose mg/dL", glucose) {
            glucose = it
            saveText(context, "glucose", it)
            saveText(context, "glucoseTrend", "manual")
            saveLong(context, "glucoseTime", System.currentTimeMillis())
        }

        EditField("HbA1c %", hba1c) {
            hba1c = it
            saveText(context, "hba1c", it)
        }

        EditField("Persönliches Ziel", goal) {
            goal = it
            saveText(context, "goal", it)
        }

        EditField("Mahlzeiteninsulin", bolusInsulin) {
            bolusInsulin = it
            saveText(context, "bolusInsulin", it)
        }

        EditField("Basalinsulin", basalInsulin) {
            basalInsulin = it
            saveText(context, "basalInsulin", it)
        }

        Spacer(Modifier.height(18.dp))
        Text("Persönliche Faktoren", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Startwerte für den Langzeittest. DiaMind nutzt diese Werte für die Essens-Schätzung.", color = DiaMindMuted)
        Spacer(Modifier.height(10.dp))

        EditField("Frühstück IE/KE", factorMorning) {
            factorMorning = it
            saveText(context, "factorMorning", it)
            learningText = InsulinAdvisor.learningSummary(context)
        }

        EditField("Mittag IE/KE", factorNoon) {
            factorNoon = it
            saveText(context, "factorNoon", it)
            learningText = InsulinAdvisor.learningSummary(context)
        }

        EditField("Abend IE/KE", factorEvening) {
            factorEvening = it
            saveText(context, "factorEvening", it)
            learningText = InsulinAdvisor.learningSummary(context)
        }

        Spacer(Modifier.height(18.dp))
        Text("Lernen nach Mahlzeiten", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Trage 2–3 Stunden nach der letzten Mahlzeit deinen Glukosewert ein. DiaMind bewertet, ob der Faktor eher passt, zu niedrig oder zu hoch war.",
            color = DiaMindMuted
        )
        Spacer(Modifier.height(10.dp))

        EditField("Glukose 2–3h nach letzter Mahlzeit", followUpGlucose) {
            followUpGlucose = it
        }

        Button(
            onClick = {
                val result = InsulinAdvisor.recordFollowUp(context, followUpGlucose)
                followUpGlucose = ""
                learningText = InsulinAdvisor.learningSummary(context)
                proposalText = InsulinAdvisor.openProposalText(context) + "\n\n" + result
            },
            colors = ButtonDefaults.buttonColors(containerColor = DiaMindRed)
        ) {
            Text("Follow-up speichern")
        }

        Spacer(Modifier.height(14.dp))
        Text("Offene Faktor-Vorschläge", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(proposalText, color = Color.LightGray)
        Spacer(Modifier.height(8.dp))

        Row {
            listOf("Frühstück", "Mittag", "Abend").forEach { slot ->
                Button(
                    onClick = {
                        val message = InsulinAdvisor.applyProposal(context, slot)
                        factorMorning = loadText(context, "factorMorning", "1.0")
                        factorNoon = loadText(context, "factorNoon", "2.5")
                        factorEvening = loadText(context, "factorEvening", "3.0")
                        learningText = InsulinAdvisor.learningSummary(context)
                        proposalText = InsulinAdvisor.openProposalText(context) + "\n\n" + message
                    },
                    modifier = Modifier.padding(end = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text(slot, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Lernzentrum", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(learningText, color = Color.LightGray)

        Spacer(Modifier.height(14.dp))
        Text("Datenquelle: xDrip aktiv", color = Color.LightGray)
        Text("Vorbereitet: Libre, Juggluco, IOB, COB und spätere Anpassungsvorschläge.", color = Color.LightGray)
        Text("Keine automatische Insulindosierung. Vorschläge bleiben transparent und überprüfbar.", color = DiaMindWarning)
        Spacer(Modifier.height(24.dp))
    }
}
