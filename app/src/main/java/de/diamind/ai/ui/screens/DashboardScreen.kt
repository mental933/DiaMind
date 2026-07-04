package de.diamind.ai.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(context: Context) {
    var glucose by remember { mutableStateOf(loadText(context, "glucose", "118")) }
    var trend by remember { mutableStateOf(loadText(context, "glucoseTrend", "manual")) }
    var glucoseTime by remember { mutableLongStateOf(loadLong(context, "glucoseTime", 0L)) }
    val hba1c = loadText(context, "hba1c", "6.8")
    val goal = loadText(context, "goal", "HbA1c verbessern und ruhiger bleiben")
    val lastFood = loadText(context, "lastFoodEstimate", "Noch keine Mahlzeit geschätzt")
    val lastBolus = loadText(context, "lastBolusRecord", "Noch keine Bolusannahme gespeichert")
    val slot = InsulinAdvisor.currentSlot()
    val factor = InsulinAdvisor.factorForSlot(context, slot)

    LaunchedEffect(Unit) {
        while (true) {
            glucose = loadText(context, "glucose", glucose)
            trend = loadText(context, "glucoseTrend", trend)
            glucoseTime = loadLong(context, "glucoseTime", glucoseTime)
            delay(5000)
        }
    }

    CardBox(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("Guten Tag, Adam", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text("Aktuelle Glukose", color = Color.LightGray)
        Text("$glucose mg/dL ${trendSymbol(trend)}", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(glucoseSourceText(trend, glucoseTime), color = DiaMindMuted, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Text("Geschätzter HbA1c", color = Color.LightGray)
        Text("$hba1c %", color = DiaMindRed, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Dein Ziel", color = Color.LightGray)
        Text(goal, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Text("Aktueller Essensfaktor", color = Color.LightGray)
        Text("$slot · ${InsulinAdvisor.format(factor)} IE/KE", color = Color.White)
        Spacer(Modifier.height(16.dp))
        Text("Letzte Essen-Schätzung", color = Color.LightGray)
        Text(lastFood, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Text("Letzte Bolusannahme", color = Color.LightGray)
        Text(lastBolus, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Text(currentGlucoseMessage(glucose), color = DiaMindMuted, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
    }
}

fun currentGlucoseMessage(glucose: String): String {
    val value = glucose.toIntOrNull() ?: return "Heute zählt nicht Perfektion. Heute zählt Richtung."
    return when {
        value < 70 -> "Achtung: Der Wert ist niedrig. Bitte prüfe ihn und handle nach deinem eigenen Diabetes-Plan."
        value in 70..180 -> "Der Wert liegt im Zielbereich. Das ist ein guter Moment für Ruhe."
        value in 181..250 -> "Der Wert ist erhöht. Ein einzelner Wert verändert deinen langfristigen Verlauf kaum."
        else -> "Der Wert ist deutlich erhöht. Bitte prüfe die Situation aufmerksam."
    }
}

fun trendSymbol(trend: String): String {
    return when (trend.lowercase()) {
        "doubleup" -> "⬆"
        "singleup" -> "↑"
        "fortyfiveup" -> "↗"
        "flat" -> "→"
        "fortyfivedown" -> "↘"
        "singledown" -> "↓"
        "doubledown" -> "⬇"
        else -> ""
    }
}

fun trendText(trend: String): String {
    return when (trend.lowercase()) {
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
}

fun glucoseSourceText(trend: String, time: Long): String {
    if (time <= 0L) return "Quelle: manuell oder noch kein xDrip-Wert empfangen"
    val date = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
    val source = if (trend == "manual") "manuell" else "xDrip"
    return "Quelle: $source · letzter Wert: $date · ${trendText(trend)}"
}
