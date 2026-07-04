package de.diamind.ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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
            context.getSharedPreferences("diamind", Context.MODE_PRIVATE)
                .edit()
                .putString("glucose", glucose.toInt().toString())
                .putString("glucoseTrend", trend)
                .putLong("glucoseTime", time)
                .apply()
        }
    }
}
