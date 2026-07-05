package de.diamind.ai.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import de.diamind.ai.storage.Preferences.loadText
import de.diamind.ai.storage.Preferences.saveText
import de.diamind.ai.ui.components.CardBox
import de.diamind.ai.ui.components.DiaMindMuted
import de.diamind.ai.ui.components.DiaMindRed

@Composable
fun RoadmapScreen(context: Context) {
    var notes by remember { mutableStateOf(loadText(context, "roadmapNotes", "")) }
    val basePlan = """
        Hohe Priorität
        ☐ Startseite: Wert + Kamera + Sprechblase ganz nach vorne
        ☐ Spritz-Ess-Abstand mit Glukose/Trend/Essen
        ☐ Bolus nur ganze IE, solange dein Pen keine 0,5er Schritte hat
        ☐ Spracheingabe und Enter zum Senden
        ☐ Therapieprofil per Chat merken und ändern
        ☐ Online-KI und lokale KI lernen aus bestätigten Mahlzeiten
        ☐ Hintergrundüberwachung vorbereiten
        ☐ Widget und Smartwatch vorbereiten

        Nächste Stufe
        ☐ echte OCR / Verpackungstexte
        ☐ bessere Portionserkennung
        ☐ persönliche Lebensmittel-Historie
        ☐ Meldungen auf Smartwatch
        ☐ Feedback an Entwickler-KI sammeln
    """.trimIndent()

    CardBox(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("Build-Liste & Merke-Punkte", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Alles, was du im Chat mit 'Merke:' eingibst, landet hier lokal für spätere Builds.", color = DiaMindMuted)
        Spacer(Modifier.height(14.dp))

        Text("Aktuelle Roadmap", color = DiaMindRed, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(basePlan, color = Color.White)

        Spacer(Modifier.height(18.dp))
        Text("Deine gemerkten Punkte", color = DiaMindRed, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(notes.ifBlank { "Noch keine lokalen Merke-Punkte in der App gespeichert." }, color = Color.White)

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                saveText(context, "roadmapNotes", "")
                notes = ""
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
        ) { Text("Lokale Merke-Liste leeren") }
    }
}
