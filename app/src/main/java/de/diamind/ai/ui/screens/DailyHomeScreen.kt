package de.diamind.ai.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
    var lastPhoto by remember { mutableStateOf<Bitmap?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val pageScroll = rememberScrollState()

    fun appendAssistantMessage(message: String) {
        val clean = message.trim()
        if (clean.isBlank()) return
        chat += "DiaMind:\n$clean\n\n"
        saveText(context, "chat", chat)
        saveText(context, "assistantBubble", clean)
        assistantBubble = clean
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            lastPhoto = bitmap
            isThinking = true
            val message = quickPhotoAnalysis(context, bitmap)
            appendAssistantMessage(message)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null) else Toast.makeText(context, "Kamera-Berechtigung fehlt.", Toast.LENGTH_LONG).show()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
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
        val answer = DiaMindBrain.answer(context, question)
        chat += "Du:\n$question\n\nDiaMind:\n$answer\n\n"
        saveText(context, "chat", chat)
        saveText(context, "assistantBubble", answer)
        assistantBubble = answer
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

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NewsCard(
                context = context,
                modifier = Modifier.weight(1.35f)
            )
            ActionCard(
                title = "Kamera",
                subtitle = "Foto & Analyse",
                icon = "📷",
                accent = DiaMindPurple,
                modifier = Modifier.weight(1f),
                onClick = { openCamera() }
            )
        }
        lastPhoto?.let { bitmap ->
            Spacer(Modifier.height(12.dp))
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Letztes Foto",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(22.dp))
            )
        }
        Spacer(Modifier.height(8.dp))
        AssistantCard(
            bubble = assistantBubble,
            chat = chat,
            isThinking = isThinking,
            input = input,
            onInput = { input = it },
            onSend = { sendNow(input) },
            onMic = { startSpeech() },
            onMinus = { sendNow("weniger bolus") },
            onConfirm = { sendNow("bestätigen") },
            onPlus = { sendNow("mehr bolus") }
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
        DiamondLogo(modifier = Modifier.size(38.dp))
        Spacer(Modifier.width(10.dp))
        Text("Dia", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
        Text("Mind", color = DiaMindPurple, fontSize = 31.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(DiaMindCyan.copy(alpha = 0.16f))
                .border(1.dp, DiaMindCyan.copy(alpha = 0.35f), CircleShape)
                .clickable { onOpenSettings() },
            contentAlignment = Alignment.Center
        ) {
            Text("⚙", color = Color.White, fontSize = 22.sp)
        }
    }
}

@Composable
private fun DiamondLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w, h * 0.32f)
            lineTo(w * 0.72f, h)
            lineTo(w * 0.28f, h)
            lineTo(0f, h * 0.32f)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFF615F), Color(0xFFB40019))))
        drawPath(path, Color.White.copy(alpha = 0.38f), style = Stroke(width = 2.5f))
        drawLine(Color.White.copy(alpha = 0.3f), Offset(w * 0.5f, 0f), Offset(w * 0.72f, h), strokeWidth = 2f)
        drawLine(Color.White.copy(alpha = 0.3f), Offset(w * 0.5f, 0f), Offset(w * 0.28f, h), strokeWidth = 2f)
    }
}

@Composable
private fun GlucoseHeroCard(context: Context, glucose: String, trend: String, glucoseTime: Long) {
    val hba1c = loadText(context, "hba1c", "6.8")
    val lastDose = loadText(context, "lastActualDose", "?")
    val iob = loadText(context, "activeInsulin", "?")
    GlassCard(
        brush = Brush.linearGradient(
            listOf(Color(0xFF151A32), Color(0xFF080D1D), Color(0xFF141022))
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f)) {
                Text(glucose, color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(5.dp))
                Text("mg/dL", color = DiaMindMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.width(8.dp))
                Text(trendSymbol(trend), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(glucoseSourceText(trend, glucoseTime), color = DiaMindMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(3.dp))
        MiniGraph(context = context, modifier = Modifier.fillMaxWidth().height(82.dp), trend = trend, currentGlucose = glucose)
        Spacer(Modifier.height(3.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CompactStat("HbA1c", "$hba1c %", DiaMindCyan)
            CompactStat("IOB", if (iob == "?") "—" else "$iob IE", DiaMindPurple)
            CompactStat("Bolus", if (lastDose == "?") "—" else "$lastDose IE", Color(0xFFFF7A59))
        }
    }
}

@Composable
private fun CompactStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = DiaMindMuted, fontSize = 10.sp)
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatBlock(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier.padding(end = 8.dp)) {
        Text(label, color = DiaMindMuted, fontSize = 11.sp)
        Text(value, color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

private data class TimedGlucosePoint(val time: Long, val value: Float)

@Composable
private fun MiniGraph(context: Context, modifier: Modifier, trend: String, currentGlucose: String) {
    val points = remember(currentGlucose, trend) { glucoseDayHistoryPoints(context, currentGlucose) }
    val dayBounds = remember(points) { todayBoundsMillis() }
    Canvas(modifier = modifier) {
        val minG = 50f
        val maxG = 260f
        val start = dayBounds.first
        val end = dayBounds.second
        val range = (end - start).toFloat().coerceAtLeast(1f)
        fun yFor(v: Float): Float {
            val normalized = ((v - minG) / (maxG - minG)).coerceIn(0f, 1f)
            return size.height * (1f - normalized)
        }
        fun xFor(t: Long): Float = (((t - start).toFloat() / range).coerceIn(0f, 1f)) * size.width

        // Zielbereich 90-130 mg/dL, wie besprochen.
        val targetTop = yFor(130f)
        val targetBottom = yFor(90f)
        drawRect(
            color = DiaMindCyan.copy(alpha = 0.12f),
            topLeft = Offset(0f, targetTop),
            size = Size(size.width, max(2f, targetBottom - targetTop))
        )
        drawLine(DiaMindCyan.copy(alpha = 0.32f), Offset(0f, targetTop), Offset(size.width, targetTop), strokeWidth = 1.4f)
        drawLine(DiaMindCyan.copy(alpha = 0.32f), Offset(0f, targetBottom), Offset(size.width, targetBottom), strokeWidth = 1.4f)

        // horizontale Hilfslinien
        listOf(70f, 90f, 130f, 180f, 240f).forEach { v ->
            val y = yFor(v)
            drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        // 0, 6, 12, 18, 24 Uhr Markierungen
        for (i in 0..4) {
            val x = size.width * i / 4f
            drawLine(Color.White.copy(alpha = 0.07f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        }

        if (points.size >= 2) {
            val path = Path()
            points.forEachIndexed { index, p ->
                val x = xFor(p.time)
                val y = yFor(p.value)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, DiaMindRed, style = Stroke(width = 4f, cap = StrokeCap.Round))
            val last = points.last()
            val lastX = xFor(last.time)
            val lastY = yFor(last.value)
            drawCircle(Color.White, radius = 5.5f, center = Offset(lastX, lastY))
            drawCircle(DiaMindRed.copy(alpha = 0.35f), radius = 11f, center = Offset(lastX, lastY))
        }
    }
}

@Composable
private fun NewsCard(context: Context, modifier: Modifier) {
    val news = loadText(
        context,
        "systemNews",
        "Heute · Build 030 vorbereitet\nGraph: 00–24 Uhr · Ziel 90–130\nChat: Mahlzeit, Bolus und Korrektur hier."
    )
    Column(
        modifier = modifier
            .height(78.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF122033), Color(0xFF0D1326))))
            .border(1.dp, DiaMindCyan.copy(alpha = 0.32f), RoundedCornerShape(24.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp)
    ) {
        Text("News", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(news.lines().take(2).joinToString("\n"), color = DiaMindMuted, fontSize = 10.sp, lineHeight = 13.sp)
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
            .height(78.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.25f), Color(0xFF10182C))))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) { Text(icon, fontSize = 21.sp) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
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
            AssistantFace(modifier = Modifier.size(62.dp).scale(if (isThinking) pulse else 1f))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("DiaMind", color = DiaMindPurple, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("KI", color = DiaMindMuted, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text(if (isThinking) "denkt…" else "bereit", color = DiaMindMuted, fontSize = 13.sp)
                }
                Text(if (isThinking) "▌▌▌" else "", color = DiaMindPurple, fontSize = 20.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(470.dp)
                .offset { IntOffset(0, if (isThinking) wiggle.roundToInt() else 0) }
                .clip(RoundedCornerShape(20.dp))
                .background(DiaMindCardDark)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            Text(
                text = if (chat.isBlank()) "DiaMind:\n$bubble" else chat,
                color = Color.White,
                fontSize = 17.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(chatScroll)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onMinus,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3356)),
                shape = RoundedCornerShape(16.dp)
            ) { Text("− 1 IE") }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1.35f),
                colors = ButtonDefaults.buttonColors(containerColor = DiaMindRed),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Bestätigen", fontWeight = FontWeight.Bold) }
            Button(
                onClick = onPlus,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3356)),
                shape = RoundedCornerShape(16.dp)
            ) { Text("+ 1 IE") }
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
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = DiaMindPurple)
            ) { Text("🎤") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSend,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = DiaMindPurple)
            ) { Text("↑", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        }
    }
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

private fun glucoseDayHistoryPoints(context: Context, currentGlucose: String): List<TimedGlucosePoint> {
    val raw = loadText(context, "glucoseHistory", "")
    val (start, end) = todayBoundsMillis()
    val parsed = raw.split(";")
        .mapNotNull { entry ->
            val parts = entry.split(":")
            val t = parts.getOrNull(0)?.toLongOrNull()
            val v = parts.getOrNull(1)?.toFloatOrNull()
            if (t != null && v != null && t in start..end) TimedGlucosePoint(t, v) else null
        }
        .sortedBy { it.time }
    if (parsed.size >= 2) return parsed

    val now = System.currentTimeMillis().coerceIn(start, end)
    val current = currentGlucose.toFloatOrNull() ?: 118f
    val span = 3L * 60L * 60L * 1000L
    return listOf(
        TimedGlucosePoint((now - span).coerceAtLeast(start), current - 14f),
        TimedGlucosePoint((now - span / 2).coerceAtLeast(start), current - 5f),
        TimedGlucosePoint(now, current)
    )
}

private fun todayBoundsMillis(): Pair<Long, Long> {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis
    cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
    return start to cal.timeInMillis
}

private fun appendSystemNews(context: Context, title: String, body: String) {
    val time = SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(Date())
    val old = loadText(context, "systemNews", "")
    val line = "$time · $title\n$body"
    saveText(context, "systemNews", (line + "\n" + old).lines().take(8).joinToString("\n"))
}

private fun quickPhotoAnalysis(context: Context, bitmap: Bitmap): String {
    val guess = photoGuess(bitmap)
    val estimate = estimateMeal(guess, "normal", true, loadText(context, "foodAiMode", "Lokal"))
    val glucose = loadText(context, "glucose", "?")
    val trend = loadText(context, "glucoseTrend", "manual")
    val slot = InsulinAdvisor.currentSlot()
    val factor = InsulinAdvisor.factorForSlot(context, slot)
    val ke = estimate.carbs / 10.0
    val dose = InsulinAdvisor.doseForKe(context, ke, factor)
    val text = "Ich habe das Foto aufgenommen. Lokale Schnellansicht: ${estimate.primaryFood}." +
        "\nKH: ca. ${estimate.carbs} g · KE: ${String.format(Locale.GERMAN, "%.1f", ke)}" +
        "\nBolusannahme: ${InsulinAdvisor.formatDose(context, dose)} IE" +
        "\n${InsulinAdvisor.preMealTimingAdvice(context, glucose, trend, estimate.carbs)}" +
        "\n\nDu kannst direkt hier im Chat korrigieren, z. B.: 'Das waren 42 g KH' oder 'Ich habe 9 IE gespritzt'."
    appendSystemNews(context, "Fotoanalyse", "${estimate.primaryFood} · ${estimate.carbs} g KH · ${InsulinAdvisor.formatDose(context, dose)} IE")
    saveText(context, "lastHomePhotoEstimate", text)
    saveText(context, "lastMealDescription", estimate.primaryFood)
    return text
}

private fun photoGuess(bitmap: Bitmap): String {
    val w = bitmap.width.coerceAtLeast(1)
    val h = bitmap.height.coerceAtLeast(1)
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0L
    val stepX = (w / 20).coerceAtLeast(1)
    val stepY = (h / 20).coerceAtLeast(1)
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            val p = bitmap.getPixel(x, y)
            red += android.graphics.Color.red(p)
            green += android.graphics.Color.green(p)
            blue += android.graphics.Color.blue(p)
            count++
            x += stepX
        }
        y += stepY
    }
    val r = red / count
    val g = green / count
    val b = blue / count
    return when {
        b > r + 18 && b > g + 8 -> "Corny Müsliriegel Verpackung"
        g > r + 20 && g > b + 20 -> "Salat Gemüse"
        r > 150 && g > 80 && b < 90 -> "Pizza Pasta Sauce"
        r > 110 && g > 80 && b < 80 -> "Kartoffeln Fleisch Sauce"
        r > 80 && g > 60 && b > 50 -> "Schokolade Riegel"
        else -> "Foto Mahlzeit"
    }
}
