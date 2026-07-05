package de.diamind.ai.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.diamind.ai.ui.components.DiaMindBackground
import de.diamind.ai.ui.components.DiaMindCyan
import de.diamind.ai.ui.components.DiaMindMuted
import de.diamind.ai.ui.components.DiaMindPurple
import de.diamind.ai.ui.components.DiaMindRed
import de.diamind.ai.ui.components.GlassCard

@Composable
fun SettingsHubScreen(context: Context, onBack: () -> Unit) {
    var section by remember { mutableStateOf("Übersicht") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DiaMindBackground, Color(0xFF050914))))
            .padding(18.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) { Text("← Start") }
            Spacer(Modifier.weight(1f))
            Text("Settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Übersicht", "Essen", "Chat", "Diabetes", "KI", "Ideen", "Schutz").forEach { item ->
                Button(
                    onClick = { section = item },
                    modifier = Modifier.padding(end = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (section == item) DiaMindRed else Color.DarkGray)
                ) { Text(item, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))

        when (section) {
            "Essen" -> FoodScreen(context)
            "Chat" -> ChatScreen(context)
            "Diabetes" -> DiabetesScreen(context)
            "KI" -> AiSettingsScreen(context)
            "Ideen" -> RoadmapScreen(context)
            "Schutz" -> SecurityScreen()
            else -> SettingsOverview(onSection = { section = it })
        }
    }
}

@Composable
private fun SettingsOverview(onSection: (String) -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        GlassCard {
            Text("Alles Komplizierte liegt jetzt hier.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Die Startseite bleibt für Alltag, Wert, Kamera und DiaMind-Chat. Therapie, KI, Datenschutz, Lernspeicher und ältere Detailseiten findest du in den Settings.",
                color = DiaMindMuted,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        SettingsButton("Essen & KE-Schätzung", "Alte Detailanalyse, Bestätigung und Learning Bridge", DiaMindPurple) { onSection("Essen") }
        SettingsButton("Chat-Verlauf", "Kompletter Gesprächsverlauf und Therapie-Chat", DiaMindCyan) { onSection("Chat") }
        SettingsButton("Diabetes-Profil", "Faktoren, Basal/Bolus, Follow-up und Lernvorschläge", DiaMindRed) { onSection("Diabetes") }
        SettingsButton("KI-Einstellungen", "Gemini, OpenAI, lokal und API-Keys", DiaMindPurple) { onSection("KI") }
        SettingsButton("Build-Liste", "Merke-Punkte und Roadmap", DiaMindCyan) { onSection("Ideen") }
        SettingsButton("Datenschutz", "Sicherheit und Datenübertragung", Color.Gray) { onSection("Schutz") }
    }
}

@Composable
private fun SettingsButton(title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
        }
    }
}
