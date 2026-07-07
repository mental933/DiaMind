package de.diamind.ai

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import de.diamind.ai.insulin.InsulinAdvisor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                .takeLast(288)
                .joinToString(";")

            prefs.edit()
                .putString("glucose", value)
                .putString("glucoseTrend", trend)
                .putLong("glucoseTime", time)
                .putString("glucoseHistory", newHistory)
                .apply()

            guardianCheck(context, glucose.toInt(), trend)
        }
    }

    private fun guardianCheck(context: Context, glucose: Int, trend: String) {
        val lower = trend.lowercase(Locale.getDefault())
        val rising = lower.contains("up")
        val falling = lower.contains("down")
        val iob = InsulinAdvisor.activeInsulin(context)
        val state = when {
            glucose >= 180 && rising && iob < 1.0 -> "HIGH_RISING_LOW_IOB"
            glucose >= 180 -> "HIGH"
            glucose <= 100 && falling -> "LOW_SOON"
            glucose <= 80 -> "LOW"
            else -> "OK"
        }
        val prefs = context.getSharedPreferences("diamind", Context.MODE_PRIVATE)
        val lastState = prefs.getString("lastGuardianState", "")
        val eventId = "$state:${glucose / 10}:${if (rising) "up" else if (falling) "down" else "flat"}"
        val lastEventId = prefs.getString("lastGuardianEventId", "")
        val nowMs = System.currentTimeMillis()
        val lastGuardianTime = prefs.getLong("lastGuardianNotificationTimeMs", 0L)
        val minGapMs = 45L * 60L * 1000L

        if (state == "OK") {
            prefs.edit()
                .putString("lastGuardianState", "OK")
                .putString("lastGuardianEventId", "")
                .putString("lastGuardianSuggestion", "")
                .putString("activeInsulin", InsulinAdvisor.format(InsulinAdvisor.activeInsulin(context)))
                .apply()
            return
        }
        if (eventId == lastEventId) return
        if (state == lastState && nowMs - lastGuardianTime < minGapMs) return

        val correctionText = InsulinAdvisor.correctionSuggestion(context, glucose.toString(), trend)
        val message = when (state) {
            "HIGH_RISING_LOW_IOB" -> "Wert $glucose mg/dL und steigend. $correctionText"
            "HIGH" -> "Wert erhöht: $glucose mg/dL. $correctionText"
            "LOW_SOON" -> "Wert $glucose mg/dL und fallend. Prüfe schnelle KH nach deinem Plan. Bolus jetzt eher vermeiden."
            "LOW" -> "Wert niedrig: $glucose mg/dL. Bitte sofort prüfen und nach deinem Hypo-Plan handeln."
            else -> return
        }
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val oldNews = prefs.getString("newsFeed", "").orEmpty()
        prefs.edit()
            .putString("lastGuardianState", state)
            .putString("lastGuardianEventId", eventId)
            .putLong("lastGuardianNotificationTimeMs", nowMs)
            .putString("lastGuardianSuggestion", message)
            .putString("assistantBubble", message)
            .putString("activeInsulin", InsulinAdvisor.format(InsulinAdvisor.activeInsulin(context)))
            .putString("newsFeed", "$time · Guardian: $message\n$oldNews")
            .apply()
        showGuardianNotification(context, message)
    }

    private fun showGuardianNotification(context: Context, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "diamind_guardian"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "DiaMind Guardian", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
        val openIntent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            1001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("DiaMind Guardian")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(301, notification)
    }
}
