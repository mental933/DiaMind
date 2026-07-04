package de.diamind.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.diamind.ai.ui.components.DiaMindBackground
import de.diamind.ai.ui.components.DiaMindRed
import de.diamind.ai.ui.screens.ChatScreen
import de.diamind.ai.ui.screens.DashboardScreen
import de.diamind.ai.ui.screens.DiabetesScreen
import de.diamind.ai.ui.screens.FoodScreen
import de.diamind.ai.ui.screens.SecurityScreen

@Composable
fun DiaMindApp() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf("Dashboard") }
    val navScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DiaMindBackground)
            .padding(18.dp)
    ) {
        Text("DiaMind", color = DiaMindRed, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text("Dein persönlicher Diabetes-Begleiter", color = Color.LightGray)

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.horizontalScroll(navScroll)
        ) {
            listOf("Dashboard", "Chat", "Essen", "Diabetes", "Schutz").forEach { item ->
                Button(
                    onClick = { screen = item },
                    modifier = Modifier.padding(end = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (screen == item) DiaMindRed else Color.DarkGray
                    )
                ) {
                    Text(item, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when (screen) {
            "Dashboard" -> DashboardScreen(context)
            "Chat" -> ChatScreen(context)
            "Essen" -> FoodScreen(context)
            "Diabetes" -> DiabetesScreen(context)
            "Schutz" -> SecurityScreen()
        }
    }
}
