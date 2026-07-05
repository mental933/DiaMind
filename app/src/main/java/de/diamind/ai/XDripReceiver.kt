package de.diamind.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import de.diamind.ai.insulin.InsulinAdvisor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class XDripReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.eveningoutpost.dexdrip.BgEstimate") return

        val glucose = intent.getDoubleExtra(
            "com.eveningoutpost.dexdrip.Extras.BgEstimate",
            -1.0
        )

        val trend = intent.getStringExtra(
            "com.eveningoutpost.dexdrip.Extras.BgSlopeName"
        ) ?: "unknown"

        val time = intent.getLongExtra(
            "com.eveningoutpost.dexdrip.Extras.Time",
            System.currentTimeMillis()
        )

        if (glucose > 0) {
            val value = glucose.toInt().toString()
            val prefs = context.getSharedPreferences("diamind", Context.MODE_PRIVATE)
            val oldHistory = prefs.getString("glucoseHistory", "").orEmpty()
            val newEntry = "$time:$value"
            val newHistory = (oldHistory.split(";") + newEntry)
                .filter { it.contains(":") }
                .takeLast(288) // ca. 24h bei 5-Minuten-Werten
                .joinToString(";")

            prefs.edit()
                .putString("glucose", value)
                .putString("glucoseTrend", trend)
                .putLong("glucoseTime", time)
                .putString("glucoseHistory", newHistory)
                .apply()

            appendSystemNews(context, "xDrip", "$value mg/dL · ${trendLabel(trend)}")
            guardianCheck(context, glucose.toInt(), trend)
        }
    }

    private fun guardianCheck(context: Context, glucose: Int, trend: String) {
        val lower = trend.lowercase(Locale.getDefault())
        val rising = lower.contains("up") || lower.contains("rise")
        val falling = lower.contains("down") || lower.contains("fall")
        val fast = lower.contains("double") || lower.contains("single")
        val target = 110
        val correctionFactor = context.getSharedPreferences("diamind", Context.MODE_PRIVATE)
            .getString("correctionFactor", "40")
            ?.replace(',', '.')
            ?.toDoubleOrNull() ?: 40.0
        val suggestedCorrection = ((glucose - target) / correctionFactor).coerceAtLeast(0.0)
        val rounded = suggestedCorrection.roundToInt()

        val warning = when {
            glucose >= 180 && rising && rounded > 0 -> "Wert steigt: $glucose mg/dL. Prüfe Korrektur nach Plan: ca. $rounded IE, Restinsulin beachten."
            glucose >= 180 -> "Wert erhöht: $glucose mg/dL. Verlauf und Restinsulin prüfen."
            glucose <= 100 && falling -> "Wert fällt: $glucose mg/dL. Früh gegensteuern prüfen, z. B. kleine schnelle KH nach deinem Plan."
            glucose <= 80 -> "Niedriger Wert: $glucose mg/dL. Bitte sofort nach deinem Hypo-Plan handeln."
            else -> null
        }
        if (warning != null) {
            appendSystemNews(context, "Guardian", warning)
            showGuardianNotification(context, warning)
        }
    }

    private fun showGuardianNotification(context: Context, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "diamind_guardian"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "DiaMind Guardian", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val openIntent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            30,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("DiaMind Guardian")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(3001, notification)
    }

    private fun appendSystemNews(context: Context, title: String, body: String) {
        val prefs = context.getSharedPreferences("diamind", Context.MODE_PRIVATE)
        val time = SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(Date())
        val old = prefs.getString("systemNews", "").orEmpty()
        val line = "$time · $title\n$body"
        prefs.edit().putString("systemNews", (line + "\n" + old).lines().take(8).joinToString("\n")).apply()
    }

    private fun trendLabel(trend: String): String {
        val t = trend.lowercase(Locale.getDefault())
        return when {
            t.contains("doubleup") -> "schnell steigend"
            t.contains("up") -> "steigend"
            t.contains("doubledown") -> "schnell fallend"
            t.contains("down") -> "fallend"
            t.contains("flat") -> "stabil"
            else -> trend
        }
    }
}
