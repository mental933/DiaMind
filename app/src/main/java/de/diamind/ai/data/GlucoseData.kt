package de.diamind.ai.data

data class GlucoseData(
    val value: Int,
    val trend: String,
    val timestamp: Long,
    val source: String
) {
    fun trendSymbol(): String {
        return when (trend.lowercase()) {
            "doubleup" -> "⬆"
            "singleup" -> "↑"
            "fortyfiveup" -> "↗"
            "flat" -> "→"
            "fortyfivedown" -> "↘"
            "singledown" -> "↓"
            "doubledown" -> "⬇"
            else -> ""
        }
    }

    fun trendText(): String {
        return when (trend.lowercase()) {
            "doubleup" -> "schnell steigend"
            "singleup" -> "steigend"
            "fortyfiveup" -> "leicht steigend"
            "flat" -> "stabil"
            "fortyfivedown" -> "leicht fallend"
            "singledown" -> "fallend"
            "doubledown" -> "schnell fallend"
            "manual" -> "manuell"
            else -> "Trend unbekannt"
        }
    }
}
