package de.diamind.ai.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import de.diamind.ai.ai.DiaMindBrain
import de.diamind.ai.storage.Preferences.loadText
import de.diamind.ai.storage.Preferences.saveText
import de.diamind.ai.ui.components.CardBox
import de.diamind.ai.ui.components.DiaMindRed

@Composable
fun ChatScreen(context: Context) {
    var input by remember { mutableStateOf("") }
    var chat by remember { mutableStateOf(loadText(context, "chat", "")) }

    fun sendNow(message: String) {
        val question = message.trim()
        if (question.isNotBlank()) {
            val answer = DiaMindBrain.answer(context, question)
            chat += "Du:\n$question\n\nDiaMind:\n$answer\n\n"
            saveText(context, "chat", chat)
            saveText(context, "assistantBubble", answer)
            input = ""
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) sendNow(spoken)
        }
    }

    Column {
        CardBox(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.ifBlank {
                    "Frag DiaMind etwas.\n\nBeispiele:\nIch nehme Lyumjev und Tresiba 26 IE abends.\nMerke: Kamera muss schneller werden.\nWie ist mein Spritz-Ess-Abstand?\nListe Roadmap."
                },
                color = Color.White,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Nachricht oder Therapieänderung") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { sendNow(input) }),
            singleLine = false
        )

        Row {
            Button(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Sprich mit DiaMind")
                    }
                    try {
                        speechLauncher.launch(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Spracheingabe ist auf diesem Gerät nicht verfügbar.", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.weight(1f).padding(end = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) { Text("🎤 Sprechen") }

            Button(
                onClick = { sendNow(input) },
                modifier = Modifier.weight(1f).padding(start = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DiaMindRed)
            ) { Text("Senden") }
        }
    }
}
