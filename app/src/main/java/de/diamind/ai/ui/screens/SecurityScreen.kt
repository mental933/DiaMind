package de.diamind.ai.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.diamind.ai.ui.components.CardBox

@Composable
fun SecurityScreen() {
    CardBox(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("Datenschutz & Sicherheit", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text("✓ Offline-Modus", color = Color.LightGray)
        Text("✓ Keine Cloud", color = Color.LightGray)
        Text("✓ Keine Werbung", color = Color.LightGray)
        Text("✓ Keine Telemetrie", color = Color.LightGray)
        Text("✓ xDrip-Daten bleiben lokal auf deinem Gerät", color = Color.LightGray)
        Text("✓ Essensfotos bleiben lokal auf deinem Gerät", color = Color.LightGray)
        Text("✓ Kamera wird nur nach deiner Erlaubnis geöffnet", color = Color.LightGray)
        Text("✓ Der Mensch entscheidet immer", color = Color.LightGray)
        Spacer(Modifier.height(24.dp))
    }
}
