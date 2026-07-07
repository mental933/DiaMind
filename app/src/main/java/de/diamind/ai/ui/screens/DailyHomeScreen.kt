package de.diamind.ai.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.diamind.ai.ai.DiaMindBrain
import de.diamind.ai.ai.RecommendationFormatter
import de.diamind.ai.data.Statistics
import de.diamind.ai.insulin.InsulinAdvisor
import de.diamind.ai.storage.Preferences.loadLong
import de.diamind.ai.storage.Preferences.loadText
import de.diamind.ai.storage.Preferences.saveText
import de.diamind.ai.ui.components.DiaMindBackground
import de.diamind.ai.ui.components.DiaMindCardDark
import de.diamind.ai.ui.components.DiaMindCyan
import de.diamind.ai.ui.components.DiaMindMuted
import de.diamind.ai.ui.components.DiaMindPink
import de.diamind.ai.ui.components.DiaMindPurple
import de.diamind.ai.ui.components.DiaMindRed
import de.diamind.ai.ui.components.GlassCard
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min

@Composable
fun DailyHomeScreen(context: Context, onOpenSettings: () -> Unit) {
    InsulinAdvisor.initializeDefaults(context)
    var glucose by remember { mutableStateOf(loadText(context, "glucose", "118")) }
    var trend by remember { mutableStateOf(loadText(context, "glucoseTrend", "manual")) }
    var glucoseTime by remember { mutableLongStateOf(loadLong(context, "glucoseTime", 0L)) }
    var assistantBubble by remember { mutableStateOf(loadText(context, "assistantBubble", "Bereit. Kamera antippen oder direkt mit DiaMind sprechen.")) }
    var chat by remember { mutableStateOf(loadText(context, "chat", "")) }
    var input by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var selectedDose by remember { mutableStateOf(loadText(context, "lastRecommendedDose", "0").replace(",", ".").toDoubleOrNull() ?: 0.0) }
    var lastPhoto by remember { mutableStateOf<Bitmap?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val pageScroll = rememberScrollState()

    fun chatStamp(): String {
        val now = Date()
        val day = SimpleDateFormat("dd.MM.", Locale.getDefault()).format(now)
        val today = SimpleDateFormat("dd.MM.", Locale.getDefault()).format(Date())
        val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
        val yesterdayLabel = SimpleDateFormat("dd.MM.", Locale.getDefault()).format(yesterday.time)
        val dayText = when (day) {
            today -> "Heute"
            yesterdayLabel -> "Gestern"
            else -> day
        }
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        return "$dayText $time"
    }

    fun appendUserMessage(message: String) {
        val clean = message.trim()
        if (clean.isBlank()) return
        chat += "Du · ${chatStamp()}:\n$clean\n\n"
        saveText(context, "chat", chat)
    }

    fun appendAssistantMessage(message: String, fromPhoto: Boolean = false) {
        val clean = message.trim()
        if (clean.isBlank()) return
        if (fromPhoto) appendUserMessage("[Foto aufgenommen]")
        chat += "DiaMind · ${chatStamp()}:\n$clean\n\n"
        saveText(context, "chat", chat)
        saveText(context, "assistantBubble", clean)
        assistantBubble = clean
        selectedDose = loadText(context, "lastRecommendedDose", selectedDose.toString()).replace(",", ".").toDoubleOrNull() ?: selectedDose
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            lastPhoto = bitmap
            isThinking = true
            appendUserMessage("[Foto aufgenommen]")
            analyzePhotoForHome(context, bitmap) { message ->
                appendAssistantMessage(message)
                isThinking = false
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null) else Toast.makeText(context, "Kamera-Berechtigung fehlt.", Toast.LENGTH_LONG).show()
    }
    fun openCamera() {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(null)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun sendNow(message: String) {
        val question = message.trim()
        if (question.isBlank()) return
        keyboard?.hide()
        isThinking = true
        appendUserMessage(question)
        val answer = DiaMindBrain.answer(context, question)
        appendAssistantMessage(answer)
        input = ""
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) sendNow(spoken)
        }
    }
    fun startSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Sprich mit DiaMind")
        }
        speechLauncher.launch(intent)
    }

    fun changeSelectedDose(delta: Double) {
        selectedDose = (selectedDose + delta).coerceAtLeast(0.0)
        saveText(context, "lastRecommendedDose", InsulinAdvisor.formatDose(context, selectedDose))
    }

    fun confirmSelectedDose() {
        val carbs = loadText(context, "lastMealCarbs", "").toIntOrNull()
        val food = loadText(context, "lastMealDescription", "Manueller Bolus")
        val glucoseNow = loadText(context, "glucose", "?")
        val trendNow = loadText(context, "glucoseTrend", "manual")
        val slot = InsulinAdvisor.currentSlot()
        val record = if (carbs != null && carbs > 0) {
            val ke = carbs / 10.0
            val factor = InsulinAdvisor.factorForSlot(context, slot)
            val recommended = InsulinAdvisor.doseForKe(context, ke, factor)
            InsulinAdvisor.saveMealAssumption(context, food, carbs, ke, recommended, selectedDose.toString(), slot, glucoseNow, trendNow)
        } else {
            val now = SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(Date())
            saveText(context, "lastActualDose", InsulinAdvisor.formatDose(context, selectedDose))
            saveText(context, "lastBolusTimeMs", System.currentTimeMillis().toString())
            saveText(context, "activeInsulin", InsulinAdvisor.format(InsulinAdvisor.activeInsulin(context)))
            saveText(context, "lastBolusRecord", "$now · Manueller Bolus: ${InsulinAdvisor.formatDose(context, selectedDose)} IE · Glukose $glucoseNow mg/dL · Trend $trendNow")
            "Manueller Bolus gespeichert: ${InsulinAdvisor.formatDose(context, selectedDose)} IE. Ich nutze das später für Restinsulin/Guardian."
        }
        val message = "Gespeichert: ${InsulinAdvisor.formatDose(context, selectedDose)} IE. Ich nutze das fuer den Lernverlauf.\n\nDetails:\n$record"
        appendAssistantMessage(message)
    }

    LaunchedEffect(Unit) {
        while (true) {
            glucose = loadText(context, "glucose", glucose)
            trend = loadText(context, "glucoseTrend", trend)
            glucoseTime = loadLong(context, "glucoseTime", glucoseTime)
            assistantBubble = loadText(context, "assistantBubble", assistantBubble)
            delay(5000)
        }
    }
    LaunchedEffect(isThinking, chat, assistantBubble) {
        if (isThinking) {
            delay(900)
            isThinking = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DiaMindBackground, Color(0xFF050914), Color(0xFF071123))
                )
            )
            .verticalScroll(pageScroll)
            .padding(18.dp)
    ) {
        Header(onOpenSettings)
        Spacer(Modifier.height(4.dp))
        GlucoseHeroCard(context, glucose, trend, glucoseTime)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NewsCard(context = context, modifier = Modifier.weight(1.45f))
            ActionCard(
                title = "Kamera",
                subtitle = "Foto",
                icon = "📷",
                accent = DiaMindPurple,
                modifier = Modifier.weight(0.9f),
                onClick = { openCamera() }
            )
        }
        Spacer(Modifier.height(8.dp))
        AssistantCard(
            bubble = assistantBubble,
            chat = chat,
            isThinking = isThinking,
            input = input,
            lastPhoto = lastPhoto,
            onInput = { input = it },
            onSend = { sendNow(input) },
            onMic = { startSpeech() },
            dose = selectedDose,
            onMinus = { changeSelectedDose(-1.0) },
            onConfirm = { confirmSelectedDose() },
            onPlus = { changeSelectedDose(1.0) }
        )
        Spacer(Modifier.height(28.dp))
    }
}


@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DiamondLogo(modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(10.dp))
        Text("Dia", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
        Text("Mind", color = DiaMindPurple, fontSize = 31.sp, fontWeight = FontWeight.Bold)
        Text(" AI", color = DiaMindMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(DiaMindCyan.copy(alpha = 0.22f))
                .border(1.dp, DiaMindCyan.copy(alpha = 0.55f), CircleShape)
                .clickable { onOpenSettings() },
            contentAlignment = Alignment.Center
        ) { Text("⚙", fontSize = 26.sp, color = Color.White) }
    }
}

@Composable
private fun DiamondLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.02f)
            lineTo(w * 0.96f, h * 0.34f)
            lineTo(w * 0.70f, h * 0.98f)
            lineTo(w * 0.30f, h * 0.98f)
            lineTo(w * 0.04f, h * 0.34f)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFF5E64), Color(0xFFC90020), Color(0xFF5B0011))))
        drawPath(path, Color.White.copy(alpha = 0.35f), style = Stroke(width = 2.2f))
        drawLine(Color.White.copy(alpha = 0.28f), Offset(w * 0.5f, h * 0.02f), Offset(w * 0.30f, h * 0.98f), strokeWidth = 1.8f)
        drawLine(Color.White.copy(alpha = 0.28f), Offset(w * 0.5f, h * 0.02f), Offset(w * 0.70f, h * 0.98f), strokeWidth = 1.8f)
        drawLine(Color.Black.copy(alpha = 0.32f), Offset(w * 0.04f, h * 0.34f), Offset(w * 0.96f, h * 0.34f), strokeWidth = 1.6f)
    }
}

@Composable
private fun GlucoseHeroCard(context: Context, glucose: String, trend: String, glucoseTime: Long) {
    val hba1c = loadText(context, "hba1c", "6.8")
    val lastDose = loadText(context, "lastActualDose", "?")
    val computedIob = InsulinAdvisor.activeInsulin(context)
    val hasRealIob = (loadText(context, "lastBolusTimeMs", "0").toLongOrNull() ?: 0L) > 0L && computedIob > 0.05
    if (hasRealIob) saveText(context, "activeInsulin", InsulinAdvisor.format(computedIob))
    val iob = if (hasRealIob) InsulinAdvisor.format(computedIob) else "—"
    val hba1cPreview = hba1cWindowsText(context)
    GlassCard(
        brush = Brush.linearGradient(
            listOf(Color(0xFF12182E), Color(0xFF080D1D), Color(0xFF151025))
        )
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(glucose, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(5.dp))
            Text("mg/dL", color = DiaMindMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.width(7.dp))
            Text(trendSymbol(trend), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(glucoseSourceText(trend, glucoseTime), color = DiaMindMuted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(4.dp))
        MiniGraph(context = context, modifier = Modifier.fillMaxWidth().height(72.dp), trend = trend, currentGlucose = glucose)
        Spacer(Modifier.height(5.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatBlock("HbA1c", "$hba1c %", DiaMindCyan, Modifier.weight(1f))
            StatBlock("IOB", if (iob == "—") "—" else "$iob IE", DiaMindPurple, Modifier.weight(1f))
            StatBlock("Bolus", if (lastDose == "?" || lastDose == "0") "—" else "$lastDose IE", Color(0xFFFF7A59), Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Text(hba1cPreview, color = DiaMindMuted, fontSize = 10.sp)
    }
}

@Composable
private fun StatBlock(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier.padding(end = 6.dp)) {
        Text(label, color = DiaMindMuted, fontSize = 10.sp)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniGraph(context: Context, modifier: Modifier, trend: String, currentGlucose: String) {
    val history = remember(currentGlucose, trend) { glucoseHistoryTimedPoints(context, currentGlucose) }
    Canvas(modifier = modifier) {
        val minG = 50f
        val maxG = 260f
        for (i in 0..4) {
            val y = size.height * (0.08f + i * 0.21f)
            drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.1f)
        }
        for (i in 0..4) {
            val x = size.width * (i / 4f)
            drawLine(Color.White.copy(alpha = 0.07f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        }
        val targetLowY = size.height * (1f - ((90f - minG) / (maxG - minG))).coerceIn(0f, 1f)
        val targetHighY = size.height * (1f - ((130f - minG) / (maxG - minG))).coerceIn(0f, 1f)
        drawRect(
            color = DiaMindCyan.copy(alpha = 0.16f),
            topLeft = Offset(0f, targetHighY),
            size = Size(size.width, max(2f, targetLowY - targetHighY))
        )
        drawLine(DiaMindCyan.copy(alpha = 0.45f), Offset(0f, targetHighY), Offset(size.width, targetHighY), strokeWidth = 1.2f)
        drawLine(DiaMindCyan.copy(alpha = 0.45f), Offset(0f, targetLowY), Offset(size.width, targetLowY), strokeWidth = 1.2f)
        val path = Path()
        history.forEachIndexed { index, pair ->
            val x = pair.first.coerceIn(0f, 1f) * size.width
            val normalized = ((pair.second - minG) / (maxG - minG)).coerceIn(0f, 1f)
            val y = size.height * (1f - normalized)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (history.isNotEmpty()) drawPath(path, DiaMindRed, style = Stroke(width = 3.2f, cap = StrokeCap.Round))
        val last = history.lastOrNull()
        if (last != null) {
            val lastY = size.height * (1f - ((last.second - minG) / (maxG - minG)).coerceIn(0f, 1f))
            val lastX = last.first.coerceIn(0f, 1f) * size.width
            drawCircle(Color.White, radius = 5f, center = Offset(lastX, lastY))
            drawCircle(DiaMindRed.copy(alpha = 0.35f), radius = 10f, center = Offset(lastX, lastY))
        }
    }
}

@Composable
private fun NewsCard(context: Context, modifier: Modifier) {
    val news = loadText(
        context,
        "newsFeed",
        "Heute · Build 040 aktiv\nChat: klare Bolus-Karten\nFoto: ehrlicher bei Unsicherheit\nGuardian: weniger doppelte Hinweise"
    )
    Column(
        modifier = modifier
            .height(112.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF132239), Color(0xFF0A1227))))
            .border(1.dp, DiaMindCyan.copy(alpha = 0.32f), RoundedCornerShape(22.dp))
            .padding(12.dp)
    ) {
        Text("News", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(news.lineSequence().take(4).joinToString("\n"), color = DiaMindMuted, fontSize = 11.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: String,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(112.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.28f), Color(0xFF10182C))))
            .border(1.dp, accent.copy(alpha = 0.50f), RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center
        ) { Text(icon, fontSize = 21.sp) }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = DiaMindMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AssistantCard(
    bubble: String,
    chat: String,
    isThinking: Boolean,
    input: String,
    lastPhoto: Bitmap?,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    dose: Double,
    onMinus: () -> Unit,
    onConfirm: () -> Unit,
    onPlus: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "assistant-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "pulse"
    )
    val wiggle by transition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(170), RepeatMode.Reverse),
        label = "wiggle"
    )
    val chatScroll = rememberScrollState()
    LaunchedEffect(chat) {
        delay(60)
        chatScroll.animateScrollTo(chatScroll.maxValue)
    }

    GlassCard(
        brush = Brush.linearGradient(listOf(Color(0xFF151B34), Color(0xFF0B1122), Color(0xFF11152A)))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistantFace(modifier = Modifier.size(56.dp).scale(if (isThinking) pulse else 1f))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("DiaMind", color = DiaMindPurple, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("KI", color = DiaMindMuted, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(if (isThinking) "denkt…" else "bereit", color = DiaMindMuted, fontSize = 12.sp)
                }
                Text(if (isThinking) "▌▌▌" else "", color = DiaMindPurple, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(455.dp)
                .offset { IntOffset(0, if (isThinking) wiggle.roundToInt() else 0) }
                .clip(RoundedCornerShape(20.dp))
                .background(DiaMindCardDark)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(chatScroll)
            ) {
                lastPhoto?.let { bitmap ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Essensfoto",
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                ChatTranscript(if (chat.isBlank()) "DiaMind:\n$bubble" else chat, lastPhoto)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                placeholder = { Text("Nachricht eingeben…") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onMic,
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = DiaMindPurple)
            ) { Text("🎤") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSend,
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = DiaMindPurple)
            ) { Text("↑", fontSize = 23.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onMinus,
                modifier = Modifier.weight(1f).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3356), contentColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) { Text("− 1 IE", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1.55f).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DiaMindRed, contentColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) { Text(if (dose > 0.4) "${dose.roundToInt()} IE bestätigen" else "Bolus speichern", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Button(
                onClick = onPlus,
                modifier = Modifier.weight(1f).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3356), contentColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) { Text("+ 1 IE", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}


@Composable
private fun ChatTranscript(raw: String, lastPhoto: Bitmap?) {
    val entries = remember(raw) { parseChatEntries(raw) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.forEach { entry ->
            val isUser = entry.isUser
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(if (isUser) 0.86f else 0.96f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (entry.isRecommendation) Color(0xFF1A1020) else if (isUser) DiaMindPurple.copy(alpha = 0.22f) else Color(0xFF151B34))
                        .border(
                            1.dp,
                            if (entry.isRecommendation) DiaMindRed.copy(alpha = 0.60f) else if (isUser) DiaMindPurple.copy(alpha = 0.45f) else DiaMindCyan.copy(alpha = 0.20f),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entry.author,
                            color = if (isUser) DiaMindCyan else DiaMindPurple,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            entry.stamp,
                            color = DiaMindMuted,
                            fontSize = 11.sp
                        )
                    }
                    if (entry.hasPhoto && lastPhoto != null) {
                        Spacer(Modifier.height(8.dp))
                        Image(
                            bitmap = lastPhoto.asImageBitmap(),
                            contentDescription = "Essensfoto",
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    if (entry.isRecommendation) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(DiaMindRed.copy(alpha = 0.55f))
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        entry.text,
                        color = Color.White,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

private data class ChatEntry(
    val isUser: Boolean,
    val author: String,
    val stamp: String,
    val text: String,
    val isRecommendation: Boolean,
    val hasPhoto: Boolean
)

private fun parseChatEntries(raw: String): List<ChatEntry> {
    val normalized = raw.replace("\r", "").trim()
    if (normalized.isBlank()) return emptyList()
    val result = mutableListOf<ChatEntry>()
    val regex = Regex("""(?m)^(Du|DiaMind)(?: · ([^:]+))?:\s*$""")
    val matches = regex.findAll(normalized).toList()
    if (matches.isEmpty()) return listOf(ChatEntry(false, "DiaMind", "", normalized, normalized.startsWith("🔴 Bolus:"), false))
    var pendingPhoto = false
    matches.forEachIndexed { index, match ->
        val nextStart = matches.getOrNull(index + 1)?.range?.first ?: normalized.length
        val role = match.groupValues[1]
        val stamp = match.groupValues.getOrNull(2).orEmpty().ifBlank { "früher" }
        val body = normalized.substring(match.range.last + 1, nextStart).trim()
        if (body.isNotBlank()) {
            val isPhotoMarker = role == "Du" && body.contains("[Foto aufgenommen]")
            val attachesPhoto = role == "DiaMind" && pendingPhoto
            if (!isPhotoMarker) result += ChatEntry(role == "Du", role, stamp, body, body.startsWith("🔴 Bolus:"), attachesPhoto)
            pendingPhoto = isPhotoMarker || (pendingPhoto && role == "Du")
            if (role == "DiaMind") pendingPhoto = false
        }
    }
    return result.takeLast(18)
}

@Composable
private fun AssistantFace(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCircle(Brush.radialGradient(listOf(DiaMindPink, DiaMindPurple, Color(0xFF15335F))))
        drawCircle(Color.White.copy(alpha = 0.22f), style = Stroke(width = 3f))
        drawCircle(Color(0xFFEFCBFF), radius = size.minDimension * 0.07f, center = Offset(size.width * 0.36f, size.height * 0.42f))
        drawCircle(Color(0xFFEFCBFF), radius = size.minDimension * 0.07f, center = Offset(size.width * 0.64f, size.height * 0.42f))
        drawArc(
            color = Color(0xFFEFCBFF),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(size.width * 0.35f, size.height * 0.45f),
            size = Size(size.width * 0.30f, size.height * 0.25f),
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}

private fun glucoseHistoryTimedPoints(context: Context, currentGlucose: String): List<Pair<Float, Float>> {
    val raw = loadText(context, "glucoseHistory", "")
    val dayStart = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val dayMs = 24f * 60f * 60f * 1000f
    val parsed = raw.split(";")
        .mapNotNull { entry ->
            val ts = entry.substringBefore(":").toLongOrNull() ?: return@mapNotNull null
            val value = entry.substringAfter(":", "").toFloatOrNull() ?: return@mapNotNull null
            if (ts < dayStart) return@mapNotNull null
            ((ts - dayStart).toFloat() / dayMs).coerceIn(0f, 1f) to value
        }
        .sortedBy { it.first }
    if (parsed.size >= 2) return parsed
    val current = currentGlucose.toFloatOrNull() ?: 118f
    val now = ((System.currentTimeMillis() - dayStart).toFloat() / dayMs).coerceIn(0.05f, 1f)
    return listOf(
        (now - 0.18f).coerceAtLeast(0f) to (current - 18f),
        (now - 0.12f).coerceAtLeast(0f) to (current - 10f),
        (now - 0.06f).coerceAtLeast(0f) to (current - 4f),
        now to current
    )
}

private fun hba1cWindowsText(context: Context): String {
    val raw = loadText(context, "glucoseHistory", "")
    val now = System.currentTimeMillis()
    val values = raw.split(";").mapNotNull { entry ->
        val ts = entry.substringBefore(":").toLongOrNull() ?: return@mapNotNull null
        val value = entry.substringAfter(":", "").toIntOrNull() ?: return@mapNotNull null
        ts to value
    }
    fun window(days: Int): String {
        val minTs = now - days * 24L * 60L * 60L * 1000L
        val inWindow = values.filter { it.first >= minTs }.map { it.second }
        if (inWindow.size < 6) return "${daysLabel(days)}: sammelt Daten"
        val avg = inWindow.average().roundToInt()
        return "${daysLabel(days)}: eHbA1c ${String.format(Locale.GERMAN, "%.1f", Statistics.estimatedHba1cFromAverageMgDl(avg))}%"
    }
    return listOf(window(1), window(7), window(30)).joinToString(" · ")
}

private fun daysLabel(days: Int): String = when (days) {
    1 -> "24h"
    7 -> "7T"
    else -> "30T"
}



private fun analyzePhotoForHome(context: Context, bitmap: Bitmap, onDone: (String) -> Unit) {
    val mode = loadText(context, "foodAiMode", "Gemini")
    val geminiKey = loadText(context, "geminiApiKey", "")
    if (mode.equals("Gemini", ignoreCase = true) && geminiKey.isNotBlank()) {
        analyzeHomePhotoWithGemini(geminiKey, bitmap) { estimate, error ->
            val message = if (estimate != null) {
                buildHomeMealMessage(context, estimate, "Gemini Vision")
            } else {
                val fallback = cautiousLocalPlateEstimate()
                buildHomeMealMessage(
                    context,
                    fallback,
                    "Lokaler Fallback: ${error ?: "Gemini nicht erreichbar"}"
                )
            }
            onDone(message)
        }
    } else {
        val estimate = cautiousLocalPlateEstimate()
        onDone(buildHomeMealMessage(context, estimate, "Lokale Vorschaetzung ohne Online-Bild-KI"))
    }
}

private fun cautiousLocalPlateEstimate(): MealEstimate {
    return MealEstimate(
        carbs = 0,
        confidence = "unsicher",
        reason = "Ohne Online-Bildanalyse kann DiaMind das Foto nicht verlaesslich erkennen.",
        primaryFood = "Unklare Mahlzeit",
        seenDescription = "Ich sehe, dass ein Foto aufgenommen wurde, kann lokal aber keine sicheren Lebensmittel erkennen. Bitte beschreibe kurz, was darauf ist, oder nutze Gemini mit API-Key."
    )
}

private fun buildHomeMealMessage(context: Context, estimate: MealEstimate, source: String): String {
    val glucose = loadText(context, "glucose", "?")
    val trend = loadText(context, "glucoseTrend", "manual")
    val slot = InsulinAdvisor.currentSlot()
    val factor = InsulinAdvisor.factorForSlot(context, slot)
    val ke = estimate.carbs / 10.0
    val dose = InsulinAdvisor.doseForKe(context, ke, factor)
    val doseText = InsulinAdvisor.formatDose(context, dose)
    val sea = InsulinAdvisor.preMealTimingAdvice(context, glucose, trend, estimate.carbs)
    val confidenceLine = if (estimate.confidence.isNotBlank()) estimate.confidence else "mittel"

    saveText(context, "lastHomePhotoEstimate", estimate.seenDescription)
    saveText(context, "lastMealDescription", estimate.primaryFood)
    saveText(context, "lastMealCarbs", estimate.carbs.toString())
    saveText(context, "lastMealKe", String.format(Locale.GERMAN, "%.1f", ke))
    saveText(context, "lastRecommendedDose", doseText)
    saveText(context, "newsFeed", "${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())} · Foto: ${estimate.primaryFood} · ${estimate.carbs} g KH\n" + loadText(context, "newsFeed", ""))

    val reason = if (estimate.carbs <= 0) {
        "Ich bin unsicher, deshalb brauche ich eine kurze Beschreibung von dir."
    } else {
        RecommendationFormatter.shortReason(glucose, trend, estimate.carbs)
    }

    return RecommendationFormatter.meal(
        context = context,
        bolusText = doseText,
        timingText = sea,
        reason = reason,
        carbs = estimate.carbs,
        ke = ke,
        glucose = glucose,
        trend = trend,
        factor = factor,
        learning = "Quelle: $source. Sicherheit: $confidenceLine. Gesehen: ${estimate.seenDescription}"
    )
}

private fun actionSeaHome(timing: String): String {
    val lower = timing.lowercase(Locale.getDefault())
    return when {
        lower.contains("niedrig") || lower.contains("fall") || lower.contains("erst essen") -> "erst essen / keinen Vorabstand"
        lower.contains("10") -> "10 Minuten warten"
        lower.contains("5") -> "5 Minuten warten"
        lower.contains("erhöht") || lower.contains("steig") -> "jetzt spritzen, kurz warten"
        lower.contains("direkt") || lower.contains("sofort") -> "jetzt / zum ersten Bissen"
        else -> "jetzt / kurz prüfen"
    }
}

private fun analyzeHomePhotoWithGemini(
    apiKey: String,
    bitmap: Bitmap,
    onDone: (MealEstimate?, String?) -> Unit
) {
    Thread {
        try {
            val imageData = bitmapToBase64Home(bitmap)
            val prompt = """
                Du bist DiaMind, ein vorsichtiger, alltagstauglicher Diabetes-Ernaehrungsassistent.
                Analysiere dieses Essensfoto so, wie ein sehr guter Gemini-Chat es tun wuerde.

                Wichtig:
                - Beschreibe zuerst genau, was du wirklich siehst.
                - Erfinde keine Marken oder Lebensmittel, wenn sie nicht sichtbar sind.
                - Wenn es ein gemischter Teller ist, zerlege ihn in Bestandteile.
                - Schaetze die Kohlenhydrate in Gramm fuer die sichtbare Portion.
                - Fleisch, Kaese, Eier und Fett liefern meistens kaum direkte KH, koennen aber die Wirkung verzoegern.
                - Brot, Kartoffeln, Reis, Nudeln, Püree, Suesswaren, Saucen und Getraenke sind KH-relevant.
                - Antworte nicht als Fliesstext, sondern NUR als JSON.

                JSON-Felder:
                description: detaillierte, kurze Bildbeschreibung in Deutsch
                primary_food: beste kurze Mahlzeitbezeichnung
                carbs_g: geschätzte Kohlenhydrate in Gramm als Zahl
                confidence: niedrig, mittel, hoch oder Prozent
                reason: kurze Begründung der KH-Schaetzung
            """.trimIndent()

            val body = JSONObject()
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray()
                                .put(JSONObject().put("text", prompt))
                                .put(
                                    JSONObject().put(
                                        "inline_data",
                                        JSONObject()
                                            .put("mime_type", "image/jpeg")
                                            .put("data", imageData)
                                    )
                                )
                        )
                    )
                )
                .put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", 0.15)
                        .put("responseMimeType", "application/json")
                )

            val responseText = postJsonNoBearerHome(
                endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey",
                json = body.toString()
            )
            val modelText = extractGeminiTextHome(responseText)
            val jsonText = extractJsonObjectHome(modelText)
            val parsed = JSONObject(jsonText)
            val carbs = parsed.optDouble("carbs_g", 60.0).toInt().coerceIn(0, 250)
            val estimate = MealEstimate(
                carbs = carbs,
                confidence = parsed.optString("confidence", "mittel"),
                reason = parsed.optString("reason", "Gemini Vision Analyse"),
                primaryFood = parsed.optString("primary_food", "Gemini Mahlzeit"),
                seenDescription = parsed.optString("description", modelText)
            )
            Handler(Looper.getMainLooper()).post { onDone(estimate, null) }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post { onDone(null, e.message ?: "Gemini Fehler") }
        }
    }.start()
}

private fun bitmapToBase64Home(bitmap: Bitmap): String {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}

private fun postJsonNoBearerHome(endpoint: String, json: String): String {
    val url = URL(endpoint)
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 30000
        readTimeout = 60000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
    }
    OutputStreamWriter(connection.outputStream).use { it.write(json) }
    val code = connection.responseCode
    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
    val response = stream.bufferedReader().use { it.readText() }
    if (code !in 200..299) error("HTTP $code: $response")
    return response
}

private fun extractGeminiTextHome(response: String): String {
    val root = JSONObject(response)
    val candidates = root.optJSONArray("candidates") ?: return response
    val first = candidates.optJSONObject(0) ?: return response
    val content = first.optJSONObject("content") ?: return response
    val parts = content.optJSONArray("parts") ?: return response
    val builder = StringBuilder()
    for (i in 0 until parts.length()) {
        val part = parts.optJSONObject(i) ?: continue
        val text = part.optString("text", "")
        if (text.isNotBlank()) builder.append(text)
    }
    return builder.toString().ifBlank { response }
}

private fun extractJsonObjectHome(text: String): String {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start >= 0 && end > start) return text.substring(start, end + 1)
    return text
}
