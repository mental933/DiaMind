package de.diamind.ai.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
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
import de.diamind.ai.ui.components.DiaMindWarning

@Composable
fun AiSettingsScreen(context: Context) {
    var foodAiMode by remember { mutableStateOf(loadText(context, "foodAiMode", "Lokal")) }
    var chatAiMode by remember { mutableStateOf(loadText(context, "chatAiMode", "Lokal")) }
    var geminiApiKey by remember { mutableStateOf(loadText(context, "geminiApiKey", "")) }
    var openAiApiKey by remember { mutableStateOf(loadText(context, "openAiApiKey", "")) }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        CardBox {
            Text("KI-Einstellungen", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "Hier legst du fest, ob DiaMind lokal bleibt oder Online-KI für Essen und Verpackungen nutzen darf.",
                color = DiaMindMuted
            )

            Spacer(Modifier.height(18.dp))
            Text("Essensanalyse", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row {
                listOf("Lokal", "Gemini", "OpenAI").forEach { mode ->
                    Button(
                        onClick = {
                            foodAiMode = mode
                            saveText(context, "foodAiMode", mode)
                        },
                        modifier = Modifier.padding(end = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (foodAiMode == mode) DiaMindRed else Color.DarkGray
                        )
                    ) { Text(mode) }
                }
            }
            Text(modeDescription(foodAiMode, "Essensanalyse"), color = DiaMindWarning)

            Spacer(Modifier.height(18.dp))
            Text("Chat", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row {
                listOf("Lokal", "Gemini", "OpenAI").forEach { mode ->
                    Button(
                        onClick = {
                            chatAiMode = mode
                            saveText(context, "chatAiMode", mode)
                        },
                        modifier = Modifier.padding(end = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (chatAiMode == mode) DiaMindRed else Color.DarkGray
                        )
                    ) { Text(mode) }
                }
            }
            Text(modeDescription(chatAiMode, "Chat"), color = DiaMindWarning)
        }

        Spacer(Modifier.height(14.dp))

        CardBox {
            Text("API-Schlüssel", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "Die Schlüssel werden nur lokal in der App gespeichert. Wenn du Online-KI nutzt, werden Fotos oder Texte an den gewählten Anbieter gesendet.",
                color = DiaMindWarning
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = geminiApiKey,
                onValueChange = {
                    geminiApiKey = it.trim()
                    saveText(context, "geminiApiKey", geminiApiKey)
                },
                label = { Text("Gemini API-Key") },
                placeholder = { Text("AIza...") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = openAiApiKey,
                onValueChange = {
                    openAiApiKey = it.trim()
                    saveText(context, "openAiApiKey", openAiApiKey)
                },
                label = { Text("OpenAI API-Key") },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(14.dp))

        CardBox {
            Text("Status", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Essensanalyse: $foodAiMode", color = Color.White)
            Text("Chat: $chatAiMode", color = Color.White)
            Text("Gemini-Key: ${if (geminiApiKey.isBlank()) "nicht gesetzt" else "gesetzt"}", color = Color.LightGray)
            Text("OpenAI-Key: ${if (openAiApiKey.isBlank()) "nicht gesetzt" else "gesetzt"}", color = Color.LightGray)
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun modeDescription(mode: String, area: String): String {
    return when (mode) {
        "Gemini" -> "$area nutzt Gemini Vision, wenn ein API-Key gesetzt ist. Gut für Marken, Verpackungen, Nährwerttabellen und Portionsschätzung."
        "OpenAI" -> "$area nutzt OpenAI, wenn ein API-Key gesetzt ist. Gut für genaue Bild- und Textbeschreibung."
        else -> "$area bleibt lokal. Keine Datenübertragung, aber weniger smarte Erkennung."
    }
}
