package de.diamind.ai.data

object Statistics {
    fun estimatedHba1cFromAverageMgDl(avg: Int): Double {
        return ((avg + 46.7) / 28.7).coerceIn(4.0, 14.0)
    }

    fun tirLabel(value: Int): String = when {
        value < 70 -> "Niedrig"
        value <= 180 -> "Im Zielbereich"
        value <= 250 -> "Erhöht"
        else -> "Deutlich erhöht"
    }
}
