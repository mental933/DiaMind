package de.diamind.ai.xdrip

import android.content.Context
import de.diamind.ai.data.GlucoseData
import de.diamind.ai.storage.Preferences.loadLong
import de.diamind.ai.storage.Preferences.loadText

object XDripReader {
    fun current(context: Context): GlucoseData {
        val value = loadText(context, "glucose", "118").toIntOrNull() ?: 118
        val trend = loadText(context, "glucoseTrend", "manual")
        val time = loadLong(context, "glucoseTime", 0L)
        val source = if (trend == "manual") "manuell" else "xDrip"
        return GlucoseData(value, trend, time, source)
    }
}
