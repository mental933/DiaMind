package de.diamind.ai.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.diamind.ai.insulin.InsulinAdvisor
import de.diamind.ai.storage.Preferences.loadLong
import de.diamind.ai.storage.Preferences.loadText
import de.diamind.ai.ui.components.CardBox
import de.diamind.ai.ui.components.DiaMindMuted
import de.diamind.ai.ui.components.DiaMindRed
import de.diamind.ai.ui.components.DiaMindWarning
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(context: Context, onOpenFood: () -> Unit, onOpenChat: () -> Unit) {
    InsulinAdvisor.initializeDefaults(context)
    var glucose by remember { mutableStateOf(loadText(context, "glucose", "118")) }
    var trend by remember { mutableStateOf(loadText(context, "glucoseTrend", "manual")) }
    var glucoseTime by remember { mutableLongStateOf(loadLong(context, "glucoseTime", 0L)) }
    var assistantBubble by remember { mutableStateOf(loadText(context, "assistantBubble", "Bereit. Foto machen oder mir direkt schreiben.")) }

    val hba1c = loadText(context, "hba1c", "6.8")
    val lastBolus = loadText(context, "lastBolusRecord", "Noch keine Bolusannahme gespeichert")
    val slot = InsulinAdvisor.currentSlot()
    val factor = InsulinAdvisor.factorForSlot(context, slot)
    val sea = InsulinAdvisor.preMealTimingAdvice(context, glucose, trend)

    LaunchedEffect(Unit) {
        while (true) {
            glucose = loadText(context, "glucose", glucose)
            trend = loadText(context, "glucoseTrend", trend)
            glucoseTime = loadLong(context, "glucoseTime", glucoseTime)
            assistantBubble = loadText(context, "assistantBubble", assistantBubble)
            delay(5000)
        }
    }

    CardBox(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("DiaMind Daily", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Schnell essen, schnell prüfen, schnell bestätigen.", color = DiaMindMuted)
        Spacer(Modifier.height(14.dp))

        Text("Aktuelle Glukose", color = Color.LightGray)
        Text("$glucose mg/dL ${trendSymbol(trend)}", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(glucoseSourceText(trend, glucoseTime), color = DiaMindMuted, fontSize = 14.sp)

        Spacer(Modifier.height(14.dp))
        Row {
            Button(
                onClick = onOpenFood,
                modifier = Modifier.weight(1f).padding(end = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DiaMindRed)
            ) { Text("📷 Essen scannen") }
            Button(
                onClick = onOpenChat,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) { Text("💬 Fragen") }
        }

        Spacer(Modifier.height(14.dp))
        CardBox(color = Color(0xFF262626)) {
            Text("DiaMind sagt", color = DiaMindRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(assistantBubble, color = Color.White)
        }

        Spacer(Modifier.height(14.dp))
        Text("Spritz-Ess-Abstand", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(sea, color = DiaMindWarning)

        Spacer(Modifier.height(14.dp))
        Text("Aktueller Essensfaktor", color = Color.LightGray)
        Text("$slot · ${InsulinAdvisor.format(factor)} IE/KE · Bolus-Schritt ${loadText(context, "bolusStep", "1.0")} IE", color = Color.White)

        Spacer(Modifier.height(14.dp))
        Text("Langzeit-Kompass", color = Color.LightGray)
        Text("HbA1c $hba1c %", color = DiaMindRed, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(14.dp))
        Text("Letzte Bolusannahme", color = Color.LightGray)
        Text(lastBolus, color = Color.White)

        Spacer(Modifier.height(14.dp))
        Text(currentGlucoseMessage(glucose), color = DiaMindMuted, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
    }
}

fun currentGlucoseMessage(glucose: String): String {
    val value = glucose.toIntOrNull() ?: return "Heute zählt nicht Perfektion. Heute zählt Richtung."
    return when {
        value < 70 -> "Achtung: niedriger Wert. Bitte nach deinem Plan handeln."
        value in 70..180 -> "Im Zielbereich. Gute Ausgangslage."
        value in 181..250 -> "Erhöht. Einzelne Werte sind wichtig, aber nicht die ganze Geschichte."
        else -> "Deutlich erhöht. Situation prüfen und Verlauf beobachten."
    }
}

fun trendSymbol(trend: String): String = when (trend.lowercase()) {
    "doubleup" -> "⬆"
    "singleup" -> "↑"
    "fortyfiveup" -> "↗"
    "flat" -> "→"
    "fortyfivedown" -> "↘"
    "singledown" -> "↓"
    "doubledown" -> "⬇"
    else -> ""
}

fun trendText(trend: String): String = when (trend.lowercase()) {
    "doubleup" -> "schnell steigend"
    "singleup" -> "steigend"
    "fortyfiveup" -> "leicht steigend"
    "flat" -> "stabil"
    "fortyfivedown" -> "leicht fallend"
    "singledown" -> "fallend"
    "doubledown" -> "schnell fallend"
    "manual" -> "manuell"
    else -> "Trend unbekannt"
}

fun glucoseSourceText(trend: String, time: Long): String {
    if (time <= 0L) return "Quelle: manuell oder noch kein xDrip-Wert empfangen"
    val date = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
    val source = if (trend == "manual") "manuell" else "xDrip"
    return "Quelle: $source · letzter Wert: $date · ${trendText(trend)}"
}
