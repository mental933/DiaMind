package de.diamind.ai.ui.screens

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.diamind.ai.ai.DiaMindBrain
import de.diamind.ai.storage.Preferences.loadText
import de.diamind.ai.storage.Preferences.saveText
import de.diamind.ai.ui.components.CardBox

@Composable
fun ChatScreen(context: Context) {
    var input by remember { mutableStateOf("") }
    var chat by remember { mutableStateOf(loadText(context, "chat", "")) }

    Column {
        CardBox(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = chat.ifBlank {
                    "Frag DiaMind etwas.\n\nBeispiele:\nHallo\nHbA1c\nMotivation\nDatenschutz\nEssen\nMerke dir: Lieblingsfarbe = Blau\nLieblingsfarbe"
                },
                color = Color.White,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Nachricht") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val question = input.trim()
                if (question.isNotBlank()) {
                    val answer = DiaMindBrain.answer(context, question)
                    chat += "Du:\n$question\n\nDiaMind:\n$answer\n\n"
                    saveText(context, "chat", chat)
                    input = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Senden")
        }
    }
}
