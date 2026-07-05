package de.diamind.ai.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.diamind.ai.insulin.InsulinAdvisor
import de.diamind.ai.learning.FoodLearningBridge
import de.diamind.ai.storage.Preferences.loadText
import de.diamind.ai.storage.Preferences.saveText
import de.diamind.ai.ui.components.CardBox
import de.diamind.ai.ui.components.DiaMindMuted
import de.diamind.ai.ui.components.DiaMindRed
import de.diamind.ai.ui.components.DiaMindWarning
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FoodScreen(context: Context) {
    InsulinAdvisor.initializeDefaults(context)

    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var description by remember { mutableStateOf("") }
    var portion by remember { mutableStateOf("normal") }
    var aiMode by remember { mutableStateOf(loadText(context, "foodAiMode", "Lokal")) }
    var geminiApiKey by remember { mutableStateOf(loadText(context, "geminiApiKey", "")) }
    var openAiApiKey by remember { mutableStateOf(loadText(context, "openAiApiKey", "")) }

    var result by remember { mutableStateOf(loadText(context, "lastFoodEstimate", "Noch keine Schätzung")) }
    var insulinResult by remember { mutableStateOf(loadText(context, "lastBolusRecord", "Noch keine Bolusannahme gespeichert")) }
    var isOnlineLoading by remember { mutableStateOf(false) }

    var pendingDescription by remember { mutableStateOf("") }
    var pendingCarbs by remember { mutableStateOf(0) }
    var pendingKe by remember { mutableStateOf(0.0) }
    var pendingDose by remember { mutableStateOf(0.0) }
    var pendingRecommendedDose by remember { mutableStateOf(0.0) }
    var pendingDoseChanged by remember { mutableStateOf(false) }
    var pendingSlot by remember { mutableStateOf(InsulinAdvisor.currentSlot()) }
    var pendingFactor by remember { mutableStateOf(InsulinAdvisor.factorForSlot(context, pendingSlot)) }
    var pendingGlucose by remember { mutableStateOf(loadText(context, "glucose", "?")) }
    var pendingTrend by remember { mutableStateOf(loadText(context, "glucoseTrend", "manual")) }
    var pendingAiMode by remember { mutableStateOf(aiMode) }
    var pendingLocalCarbs by remember { mutableStateOf(0) }
    var pendingOnlineCarbs by remember { mutableStateOf(0) }
    var pendingPrimaryFood by remember { mutableStateOf("") }
    var learningBridgeText by remember { mutableStateOf(FoodLearningBridge.summary(context)) }
    var hasPendingEstimate by remember { mutableStateOf(false) }

    fun preparePendingEstimate(estimate: MealEstimate) {
        val localEstimate = estimateMeal(
            context = context,
            description = description,
            portion = portion,
            bitmap = photo,
            aiMode = "Lokal"
        )
        val glucose = loadText(context, "glucose", "?")
        val trend = loadText(context, "glucoseTrend", "manual")
        val slot = InsulinAdvisor.currentSlot()
        val factor = InsulinAdvisor.factorForSlot(context, slot)
        val ke = estimate.carbs / 10.0
        val dose = InsulinAdvisor.doseForKe(ke, factor)

        pendingDescription = description.ifBlank { estimate.primaryFood }
        pendingPrimaryFood = estimate.primaryFood
        pendingCarbs = estimate.carbs
        pendingKe = ke
        pendingDose = dose
        pendingRecommendedDose = dose
        pendingDoseChanged = false
        pendingSlot = slot
        pendingFactor = factor
        pendingGlucose = glucose
        pendingTrend = trend
        pendingAiMode = aiMode
        pendingLocalCarbs = localEstimate.carbs
        pendingOnlineCarbs = if (aiMode == "Lokal") 0 else estimate.carbs
        hasPendingEstimate = true

        result = estimate.toDisplayText(
            glucose = glucose,
            trend = trend,
            slot = slot,
            factor = factor,
            recommendedDose = dose,
            aiMode = aiMode
        )
        result += "\n\n" + FoodLearningBridge.previewText(
            mode = aiMode,
            localCarbs = pendingLocalCarbs,
            onlineCarbs = pendingOnlineCarbs,
            proposedCarbs = pendingCarbs,
            primaryFood = pendingPrimaryFood.ifBlank { pendingDescription }
        )
        result += "\n\n" + FoodLearningBridge.personalHint(context, pendingPrimaryFood.ifBlank { pendingDescription })
        saveText(context, "lastFoodEstimate", result)
        insulinResult = "Noch nicht bestätigt. Bitte KE/Bolus prüfen und dann übernehmen."
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) photo = bitmap
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Kamera-Berechtigung wurde nicht erlaubt.", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        CardBox {
            Text("Essen & KE-Schätzung", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "Build 022: lokale Fotoanalyse v1 + Online/Gemini als Lehrer + persönliches Lernen aus Bestätigungen. Keine automatische Insulindosierung.",
                color = DiaMindMuted
            )

            Spacer(Modifier.height(12.dp))
            Text("KI-Modus", color = Color.LightGray)
            Row {
                listOf("Lokal", "Gemini", "OpenAI").forEach { item ->
                    Button(
                        onClick = {
                            aiMode = item
                            saveText(context, "foodAiMode", item)
                        },
                        modifier = Modifier.padding(end = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (aiMode == item) DiaMindRed else Color.DarkGray
                        )
                    ) { Text(item) }
                }
            }
            Text(aiModeInfo(aiMode), color = DiaMindWarning)

            if (aiMode == "Gemini") {
                Spacer(Modifier.height(10.dp))
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
                Text(
                    "Im Gemini-Modus wird das Foto an Google Gemini gesendet. Deine Bestätigung trainiert anschließend das lokale DiaMind-Modell.",
                    color = DiaMindWarning
                )
            }

            if (aiMode == "OpenAI") {
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
                Text(
                    "Im OpenAI-Modus wird das Foto an OpenAI gesendet. Deine Bestätigung trainiert anschließend das lokale DiaMind-Modell.",
                    color = DiaMindWarning
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        cameraLauncher.launch(null)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DiaMindRed)
            ) { Text("Foto aufnehmen") }

            photo?.let {
                Spacer(Modifier.height(12.dp))
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Essensfoto",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Was ist auf dem Teller oder auf der Verpackung?") },
                placeholder = { Text("z.B. Ehrmann High Protein Pudding, Pizza, Döner, Nudeln...") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))
            Text("Portion", color = Color.LightGray)
            Row {
                listOf("klein", "normal", "groß").forEach { item ->
                    Button(
                        onClick = { portion = item },
                        modifier = Modifier.padding(end = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (portion == item) DiaMindRed else Color.DarkGray
                        )
                    ) { Text(item) }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val bitmap = photo
                    when (aiMode) {
                        "Gemini" -> {
                            if (geminiApiKey.isBlank()) {
                                val fallback = estimateMeal(context, description, portion, bitmap, "Lokal")
                                preparePendingEstimate(fallback.copy(
                                    reason = fallback.reason + " · Gemini nicht genutzt: API-Key fehlt."
                                ))
                            } else if (bitmap == null) {
                                val fallback = estimateMeal(context, description, portion, null, "Lokal")
                                preparePendingEstimate(fallback.copy(
                                    reason = fallback.reason + " · Gemini braucht ein Foto."
                                ))
                            } else {
                                isOnlineLoading = true
                                result = "Gemini analysiert Foto, Verpackung und Portion..."
                                analyzeFoodWithGemini(
                                    apiKey = geminiApiKey,
                                    bitmap = bitmap,
                                    userDescription = description,
                                    portion = portion,
                                    onDone = { estimate, error ->
                                        isOnlineLoading = false
                                        if (estimate != null) {
                                            preparePendingEstimate(estimate)
                                        } else {
                                            val fallback = estimateMeal(context, description, portion, photo, "Lokal")
                                            preparePendingEstimate(fallback.copy(
                                                reason = fallback.reason + " · Gemini Fehler: ${error ?: "unbekannt"}."
                                            ))
                                        }
                                    }
                                )
                            }
                        }

                        "OpenAI" -> {
                            if (openAiApiKey.isBlank()) {
                                val fallback = estimateMeal(context, description, portion, bitmap, "Lokal")
                                preparePendingEstimate(fallback.copy(
                                    reason = fallback.reason + " · OpenAI nicht genutzt: API-Key fehlt."
                                ))
                            } else if (bitmap == null) {
                                val fallback = estimateMeal(context, description, portion, null, "Lokal")
                                preparePendingEstimate(fallback.copy(
                                    reason = fallback.reason + " · OpenAI braucht ein Foto."
                                ))
                            } else {
                                isOnlineLoading = true
                                result = "OpenAI analysiert Foto, Verpackung und Portion..."
                                analyzeFoodOnline(
                                    apiKey = openAiApiKey,
                                    bitmap = bitmap,
                                    userDescription = description,
                                    portion = portion,
                                    onDone = { estimate, error ->
                                        isOnlineLoading = false
                                        if (estimate != null) {
                                            preparePendingEstimate(estimate)
                                        } else {
                                            val fallback = estimateMeal(context, description, portion, photo, "Lokal")
                                            preparePendingEstimate(fallback.copy(
                                                reason = fallback.reason + " · OpenAI Fehler: ${error ?: "unbekannt"}."
                                            ))
                                        }
                                    }
                                )
                            }
                        }

                        else -> preparePendingEstimate(estimateMeal(context, description, portion, bitmap, "Lokal"))
                    }
                },
                enabled = !isOnlineLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DiaMindRed)
            ) { Text(if (isOnlineLoading) "Analysiere..." else "KE schätzen") }
        }

        Spacer(Modifier.height(14.dp))

        CardBox {
            Text("Schätzung", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(result, color = Color.White)

            if (hasPendingEstimate) {
                Spacer(Modifier.height(14.dp))
                Text("Prüfen & bestätigen", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("KE: ${formatOne(pendingKe)} · Bolusannahme: ${formatOne(pendingDose)} IE", color = Color.White)

                Spacer(Modifier.height(8.dp))
                Text("KE anpassen", color = Color.LightGray)
                Row {
                    AdjustmentButton("-0,5") {
                        pendingKe = (pendingKe - 0.5).coerceAtLeast(0.0)
                        pendingCarbs = (pendingKe * 10.0).roundToInt()
                        pendingDose = InsulinAdvisor.doseForKe(pendingKe, pendingFactor)
                        pendingRecommendedDose = pendingDose
                    }
                    AdjustmentButton("-0,1") {
                        pendingKe = (pendingKe - 0.1).coerceAtLeast(0.0)
                        pendingCarbs = (pendingKe * 10.0).roundToInt()
                        pendingDose = InsulinAdvisor.doseForKe(pendingKe, pendingFactor)
                        pendingRecommendedDose = pendingDose
                    }
                    AdjustmentButton("+0,1") {
                        pendingKe += 0.1
                        pendingCarbs = (pendingKe * 10.0).roundToInt()
                        pendingDose = InsulinAdvisor.doseForKe(pendingKe, pendingFactor)
                        pendingRecommendedDose = pendingDose
                    }
                    AdjustmentButton("+0,5") {
                        pendingKe += 0.5
                        pendingCarbs = (pendingKe * 10.0).roundToInt()
                        pendingDose = InsulinAdvisor.doseForKe(pendingKe, pendingFactor)
                        pendingRecommendedDose = pendingDose
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Bolus anpassen", color = Color.LightGray)
                Row {
                    AdjustmentButton("-0,5") {
                        pendingDose = (pendingDose - 0.5).coerceAtLeast(0.0)
                        pendingDoseChanged = true
                    }
                    AdjustmentButton("-0,1") {
                        pendingDose = (pendingDose - 0.1).coerceAtLeast(0.0)
                        pendingDoseChanged = true
                    }
                    AdjustmentButton("+0,1") {
                        pendingDose += 0.1
                        pendingDoseChanged = true
                    }
                    AdjustmentButton("+0,5") {
                        pendingDose += 0.5
                        pendingDoseChanged = true
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val confirmedResult = buildConfirmedEstimateText(
                            carbs = pendingCarbs,
                            ke = pendingKe,
                            dose = pendingDose,
                            glucose = pendingGlucose,
                            trend = pendingTrend,
                            slot = pendingSlot,
                            factor = pendingFactor,
                            description = pendingDescription
                        )
                        result = confirmedResult
                        saveText(context, "lastFoodEstimate", confirmedResult)

                        val actualDoseInput = if (pendingDoseChanged) formatOne(pendingDose) else ""
                        val bolusRecord = InsulinAdvisor.saveMealAssumption(
                            context = context,
                            description = pendingDescription,
                            carbs = pendingCarbs,
                            ke = pendingKe,
                            recommendedDose = pendingRecommendedDose,
                            actualDoseInput = actualDoseInput,
                            slot = pendingSlot,
                            glucoseBefore = pendingGlucose,
                            trendBefore = pendingTrend
                        )
                        val bridgeRecord = FoodLearningBridge.recordConfirmedMeal(
                            context = context,
                            mode = pendingAiMode,
                            localCarbs = pendingLocalCarbs,
                            onlineCarbs = pendingOnlineCarbs,
                            confirmedCarbs = pendingCarbs,
                            primaryFood = pendingPrimaryFood.ifBlank { pendingDescription },
                            portion = portion,
                            slot = pendingSlot
                        )
                        learningBridgeText = FoodLearningBridge.summary(context)
                        insulinResult = bolusRecord + "\n\n" + bridgeRecord
                        hasPendingEstimate = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DiaMindRed)
                ) { Text("✓ Schätzung übernehmen") }
            }

            Spacer(Modifier.height(12.dp))
            Text("Lernspeicher", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(insulinResult, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Text(
                "Hinweis: DiaMind speichert erst nach deiner Bestätigung. Wenn du nichts änderst, gilt die Empfehlung als ausgeführt.",
                color = DiaMindWarning
            )
        }

        Spacer(Modifier.height(14.dp))

        CardBox {
            Text("Online/Lokal-Lernen", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(learningBridgeText, color = Color.White)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AdjustmentButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.padding(end = 6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
    ) { Text(text) }
}

data class MealEstimate(
    val carbs: Int,
    val confidence: String,
    val reason: String,
    val primaryFood: String,
    val seenDescription: String
) {
    fun toDisplayText(
        glucose: String,
        trend: String,
        slot: String,
        factor: Double,
        recommendedDose: Double,
        aiMode: String
    ): String {
        val ke = carbs / 10.0
        val be = carbs / 12.0
        val glucoseInfo = if (glucose != "?") {
            "\n\nAktuelle Glukose: $glucose mg/dL\nTrend: ${foodTrendText(trend)}"
        } else {
            "\n\nKeine aktuelle Glukose verfügbar."
        }
        return "KI-Modus: $aiMode" +
            "\nIch sehe/verstehe: $seenDescription" +
            "\nKH: ca. $carbs g" +
            "\nKE: ca. ${formatOne(ke)}" +
            "\nBE: ca. ${formatOne(be)}" +
            "\nSicherheit: $confidence" +
            "\nGrundlage: $reason" +
            "\n\nTageszeit: $slot" +
            "\nFaktor: ${formatOne(factor)} IE/KE" +
            "\nBolusannahme vor Bestätigung: ${formatOne(recommendedDose)} IE" +
            glucoseInfo
    }
}

fun estimateMeal(
    context: Context,
    description: String,
    portion: String,
    bitmap: Bitmap?,
    aiMode: String
): MealEstimate {
    val text = description.lowercase(Locale.getDefault())
    val rule = foodRules.firstOrNull { rule -> rule.keywords.any { text.contains(it) } }
    val visual = bitmap?.let { analyzeLocalPhoto(it) }
    val detectedRule = rule ?: visual?.suggestedRule

    val rawBase = detectedRule?.carbsNormalPortion ?: when {
        text.isBlank() && visual != null -> visual.defaultCarbs
        text.isBlank() -> 40
        else -> 50
    }

    val multiplier = when (portion.lowercase(Locale.getDefault())) {
        "klein" -> 0.7
        "groß" -> 1.35
        else -> 1.0
    }

    val primaryFood = detectedRule?.name ?: if (text.isBlank()) visual?.label ?: "unbekannte Mahlzeit" else description
    val rawCarbs = (rawBase * multiplier).roundToInt().coerceAtLeast(0)
    val carbs = FoodLearningBridge.calibratedCarbsForFood(context, primaryFood, rawCarbs)

    val confidence = when {
        rule != null && visual != null -> "mittel bis hoch · Beschreibung und lokale Fotoanalyse vorhanden"
        rule != null -> "mittel · aus deiner Beschreibung erkannt"
        visual != null -> visual.confidence
        else -> "niedrig bis mittel"
    }

    val seen = when {
        rule != null && visual != null ->
            "Beschreibung spricht für ${rule.name}. Lokale Fotoanalyse v1 sieht ${visual.label}."
        rule != null ->
            "Aus deiner Beschreibung erkannt: ${rule.name}."
        visual != null ->
            "Lokale Fotoanalyse v1 sieht ${visual.label}. Das ist eine vorsichtige Offline-Schätzung ohne Cloud."
        text.isNotBlank() ->
            "Aus deiner Beschreibung geschätzt: $description."
        else ->
            "Keine eindeutige Mahlzeit. Standard-Schätzung verwendet."
    }

    val reason = buildString {
        append("Beschreibung '${description.ifBlank { "leer" }}', Portion '$portion', Modus '$aiMode'")
        if (visual != null) append(", lokale Fotoanalyse: ${visual.debug}")
        append(", ${FoodLearningBridge.personalHint(context, primaryFood)}")
    }

    return MealEstimate(
        carbs = carbs,
        confidence = confidence,
        reason = reason,
        primaryFood = primaryFood,
        seenDescription = seen
    )
}

private data class FoodRule(
    val name: String,
    val keywords: List<String>,
    val carbsNormalPortion: Int
)

private data class LocalVisualHint(
    val label: String,
    val suggestedRule: FoodRule?,
    val defaultCarbs: Int,
    val confidence: String,
    val debug: String
)

private val foodRules = listOf(
    FoodRule("Pizza", listOf("pizza", "margherita", "salami pizza"), 90),
    FoodRule("Pasta/Nudeln", listOf("nudel", "nudeln", "pasta", "spaghetti", "penne", "lasagne"), 75),
    FoodRule("Reis", listOf("reis", "basmati", "jasminreis", "sushi"), 70),
    FoodRule("Kartoffeln", listOf("kartoffel", "kartoffeln", "püree", "pueree"), 45),
    FoodRule("Pommes", listOf("pommes", "fritten", "french fries"), 55),
    FoodRule("Brot/Brötchen", listOf("brot", "brötchen", "broetchen", "toast", "semmel", "baguette"), 40),
    FoodRule("Döner", listOf("döner", "doener", "kebab", "dürüm", "dueruem"), 75),
    FoodRule("Burger", listOf("burger", "cheeseburger", "hamburger"), 45),
    FoodRule("Banane", listOf("banane", "banana"), 25),
    FoodRule("Apfel", listOf("apfel", "apple"), 15),
    FoodRule("Müsli/Hafer", listOf("müsli", "muesli", "hafer", "porridge", "cornflakes"), 55),
    FoodRule("Joghurt/Pudding", listOf("joghurt", "yoghurt", "pudding", "high protein", "ehrmann", "protein"), 20),
    FoodRule("Cola/Saft", listOf("cola", "saft", "juice", "limonade", "fanta", "sprite"), 30),
    FoodRule("Schokolade", listOf("schokolade", "chocolate", "riegel", "snickers", "mars", "twix"), 35),
    FoodRule("Kuchen", listOf("kuchen", "torte", "muffin", "donut"), 45),
    FoodRule("Salat", listOf("salat", "gurke", "tomate"), 12)
)

private fun analyzeLocalPhoto(bitmap: Bitmap): LocalVisualHint {
    val width = bitmap.width.coerceAtLeast(1)
    val height = bitmap.height.coerceAtLeast(1)
    val stepX = (width / 24).coerceAtLeast(1)
    val stepY = (height / 24).coerceAtLeast(1)
    var total = 0
    var green = 0
    var yellow = 0
    var brown = 0
    var red = 0
    var white = 0
    var dark = 0

    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val color = bitmap.getPixel(x, y)
            val r = (color shr 16) and 0xff
            val g = (color shr 8) and 0xff
            val b = color and 0xff
            total++
            val brightness = (r + g + b) / 3
            when {
                g > r + 25 && g > b + 20 -> green++
                r > 170 && g > 130 && b < 110 -> yellow++
                r > 95 && g in 45..155 && b < 100 -> brown++
                r > 150 && g < 110 && b < 100 -> red++
                brightness > 205 -> white++
                brightness < 65 -> dark++
            }
            x += stepX
        }
        y += stepY
    }

    fun ratio(value: Int): Double = if (total <= 0) 0.0 else value.toDouble() / total.toDouble()
    val greenR = ratio(green)
    val yellowR = ratio(yellow)
    val brownR = ratio(brown)
    val redR = ratio(red)
    val whiteR = ratio(white)
    val darkR = ratio(dark)

    val rule = when {
        greenR > 0.22 -> foodRules.first { it.name == "Salat" }
        yellowR > 0.20 && brownR > 0.08 -> foodRules.first { it.name == "Pizza" }
        yellowR > 0.24 -> foodRules.first { it.name == "Pasta/Nudeln" }
        brownR > 0.22 -> foodRules.first { it.name == "Brot/Brötchen" }
        redR > 0.18 && yellowR > 0.10 -> foodRules.first { it.name == "Pizza" }
        whiteR > 0.35 -> foodRules.first { it.name == "Reis" }
        darkR > 0.40 -> foodRules.first { it.name == "Schokolade" }
        else -> null
    }

    val label = rule?.name ?: "gemischte oder unklare Mahlzeit"
    val carbs = rule?.carbsNormalPortion ?: 45
    val confidence = if (rule != null) "niedrig bis mittel · lokale Fotoanalyse v1" else "niedrig · lokale Fotoanalyse v1"
    val debug = "grün ${percent(greenR)}, gelb ${percent(yellowR)}, braun ${percent(brownR)}, rot ${percent(redR)}, hell ${percent(whiteR)}"
    return LocalVisualHint(label, rule, carbs, confidence, debug)
}

private fun percent(value: Double): String = "${(value * 100).roundToInt()}%"

fun foodTrendText(trend: String): String {
    return when (trend.lowercase(Locale.getDefault())) {
        "doubleup" -> "schnell steigend"
        "singleup" -> "steigend"
        "fortyfiveup" -> "leicht steigend"
        "flat" -> "stabil"
        "fortyfivedown" -> "leicht fallend"
        "singledown" -> "fallend"
        "doubledown" -> "schnell fallend"
        "manual" -> "manuell"
        else -> "unbekannt"
    }
}

private fun buildConfirmedEstimateText(
    carbs: Int,
    ke: Double,
    dose: Double,
    glucose: String,
    trend: String,
    slot: String,
    factor: Double,
    description: String
): String {
    return "BESTÄTIGT" +
        "\nMahlzeit: ${description.ifBlank { "ohne Beschreibung" }}" +
        "\nKH: ca. $carbs g" +
        "\nKE: ${formatOne(ke)}" +
        "\nBE: ${formatOne(carbs / 12.0)}" +
        "\nTageszeit: $slot" +
        "\nFaktor: ${formatOne(factor)} IE/KE" +
        "\nBestätigter Bolus: ${formatOne(dose)} IE" +
        "\nGlukose vorher: $glucose mg/dL" +
        "\nTrend vorher: ${foodTrendText(trend)}"
}

private fun aiModeInfo(mode: String): String {
    return when (mode) {
        "Gemini" -> "Gemini: Foto wird mit deinem Gemini API-Key an Google Gemini gesendet. Gut für Marken, Verpackungen und gemischte Teller. Deine Bestätigung verbessert zusätzlich die lokale Schätzung."
        "OpenAI" -> "OpenAI: Foto wird mit deinem OpenAI API-Key an OpenAI gesendet. Gut für detaillierte Bildbeschreibung. Deine Bestätigung verbessert zusätzlich die lokale Schätzung."
        else -> "Lokal: keine Datenübertragung. Build 022 nutzt Beschreibung, Portion, einfache Fotoanalyse v1 und deine bestätigten Mahlzeiten."
    }
}

private fun analyzeFoodWithGemini(
    apiKey: String,
    bitmap: Bitmap,
    userDescription: String,
    portion: String,
    onDone: (MealEstimate?, String?) -> Unit
) {
    Thread {
        try {
            val imageData = bitmapToBase64(bitmap)
            val prompt = """
                Du bist ein vorsichtiger Diabetes-Ernährungsassistent.
                Analysiere dieses Essensfoto. Erkenne Lebensmittel, Marken, Verpackungen und sichtbare Nährwertangaben, falls möglich.
                Schätze die sichtbare Portion und die Kohlenhydrate in Gramm.
                Zusatzbeschreibung des Nutzers: ${userDescription.ifBlank { "keine" }}
                Gewählte Portionsgröße: $portion
                Antworte ausschließlich als JSON mit diesen Feldern:
                description, primary_food, carbs_g, confidence, reason
                description: kurze Beschreibung dessen, was du auf dem Bild siehst.
                primary_food: Hauptlebensmittel oder Marke.
                carbs_g: geschätzte Kohlenhydrate in Gramm als Zahl.
                confidence: niedrig, mittel, hoch oder Prozentangabe.
                reason: kurze Begründung der Schätzung.
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
                        .put("temperature", 0.2)
                        .put("responseMimeType", "application/json")
                )

            val responseText = postJsonNoBearer(
                endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey",
                json = body.toString()
            )
            val modelText = extractGeminiText(responseText)
            val jsonText = extractJsonObject(modelText)
            val parsed = JSONObject(jsonText)
            val carbs = parsed.optDouble("carbs_g", 50.0).toInt().coerceAtLeast(0)
            val primary = parsed.optString("primary_food", "Gemini-Erkennung")
            val desc = parsed.optString("description", modelText)
            val confidence = parsed.optString("confidence", "mittel")
            val reason = parsed.optString("reason", "Gemini-Vision-Analyse")
            Handler(Looper.getMainLooper()).post {
                onDone(MealEstimate(carbs, confidence, reason, primary, desc), null)
            }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post { onDone(null, e.message ?: "unbekannt") }
        }
    }.start()
}

private fun analyzeFoodOnline(
    apiKey: String,
    bitmap: Bitmap,
    userDescription: String,
    portion: String,
    onDone: (MealEstimate?, String?) -> Unit
) {
    Thread {
        try {
            val imageData = bitmapToBase64(bitmap)
            val prompt = """
                Analysiere dieses Essensfoto für eine Person mit Diabetes.
                Beschreibe kurz, was du siehst, inklusive Marken/Verpackungen, falls erkennbar.
                Schätze Kohlenhydrate in Gramm für die sichtbare Portion.
                Zusätzliche Nutzerbeschreibung: ${userDescription.ifBlank { "keine" }}
                Gewählte Portion: $portion
                Antworte ausschließlich als JSON mit diesen Feldern:
                description, primary_food, carbs_g, confidence, reason
            """.trimIndent()

            val body = JSONObject()
                .put("model", "gpt-4o-mini")
                .put(
                    "input",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "content",
                                JSONArray()
                                    .put(JSONObject().put("type", "input_text").put("text", prompt))
                                    .put(JSONObject().put("type", "input_image").put("image_url", "data:image/jpeg;base64,$imageData"))
                            )
                    )
                )
                .put("temperature", 0.2)

            val responseText = postJson("https://api.openai.com/v1/responses", apiKey, body.toString())
            val modelText = extractOutputText(responseText)
            val jsonText = extractJsonObject(modelText)
            val parsed = JSONObject(jsonText)
            val carbs = parsed.optDouble("carbs_g", 50.0).toInt().coerceAtLeast(0)
            val primary = parsed.optString("primary_food", "Online-Erkennung")
            val desc = parsed.optString("description", modelText)
            val confidence = parsed.optString("confidence", "mittel")
            val reason = parsed.optString("reason", "Online-Vision-Analyse")
            Handler(Looper.getMainLooper()).post {
                onDone(MealEstimate(carbs, confidence, reason, primary, desc), null)
            }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post { onDone(null, e.message ?: "unbekannt") }
        }
    }.start()
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}

private fun postJson(endpoint: String, apiKey: String, json: String): String {
    val url = URL(endpoint)
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 30000
        readTimeout = 60000
        doOutput = true
        setRequestProperty("Authorization", "Bearer $apiKey")
        setRequestProperty("Content-Type", "application/json")
    }
    OutputStreamWriter(connection.outputStream).use { it.write(json) }
    val code = connection.responseCode
    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
    val response = stream.bufferedReader().use { it.readText() }
    if (code !in 200..299) error("HTTP $code: $response")
    return response
}

private fun postJsonNoBearer(endpoint: String, json: String): String {
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

private fun extractGeminiText(response: String): String {
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

private fun extractOutputText(response: String): String {
    val root = JSONObject(response)
    val output = root.optJSONArray("output") ?: return response
    val builder = StringBuilder()
    for (i in 0 until output.length()) {
        val item = output.optJSONObject(i) ?: continue
        val content = item.optJSONArray("content") ?: continue
        for (j in 0 until content.length()) {
            val part = content.optJSONObject(j) ?: continue
            val text = part.optString("text", "")
            if (text.isNotBlank()) builder.append(text)
        }
    }
    return builder.toString().ifBlank { response }
}

private fun extractJsonObject(text: String): String {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start >= 0 && end > start) return text.substring(start, end + 1)
    return text
}

private fun formatOne(value: Double): String {
    return String.format(Locale.GERMAN, "%.1f", value)
}
