package de.diamind.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import de.diamind.ai.ui.components.DiaMindBackground
import de.diamind.ai.ui.screens.DailyHomeScreen
import de.diamind.ai.ui.screens.SettingsHubScreen

@Composable
fun DiaMindApp() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf("Home") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DiaMindBackground)
    ) {
        when (screen) {
            "Settings" -> SettingsHubScreen(context = context, onBack = { screen = "Home" })
            else -> DailyHomeScreen(context = context, onOpenSettings = { screen = "Settings" })
        }
    }
}
