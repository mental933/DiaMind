package de.diamind.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import de.diamind.ai.ui.DiaMindApp
import de.diamind.ai.ui.theme.DiaMindTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DiaMindTheme {
                DiaMindApp()
            }
        }
    }
}
