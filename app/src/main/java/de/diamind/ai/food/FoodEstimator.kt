package de.diamind.ai.food

import java.util.Locale

data class FoodEstimate(
    val totalCarbs: Int,
    val ke: Double,
    val be: Double,
    val confidence: String,
    val matchedFoods: List<String>,
    val notes: String
) {
    fun toDisplayText(glucose: String, trendText: String): String {
        val foods = if (matchedFoods.isEmpty()) "keine eindeutigen Treffer" else matchedFoods.joinToString(", ")
        val glucoseInfo = if (glucose != "?") {
            "\n\nAktuelle Glukose: $glucose mg/dL\nTrend: $trendText"
        } else {
            "\n\nKeine aktuelle Glukose verfügbar."
        }

        return "KH: ca. $totalCarbs g" +
            "\nKE: ca. ${ formatOne(ke)}" +
            "\nBE: ca. ${formatOne(be)}" +
            "\nSicherheit: $confidence" +
            "\nErkannt: $foods" +
            "\nHinweis: $notes" +
            glucoseInfo +
            "\n\nDiaMind-Hinweis: Bitte Bolus immer nach deinem persönlichen Faktor, deinem Plan und deiner Erfahrung berechnen. Keine automatische Dosierung."
    }
}

data class FoodRule(
    val name: String,
    val keywords: List<String>,
    val carbsNormalPortion: Int,
    val note: String
)

object FoodEstimator {
    private val rules = listOf(
        FoodRule("Pizza", listOf("pizza", "margherita", "salami pizza"), 90, "Pizza schwankt stark je nach Größe und Teig."),
        FoodRule("Pasta/Nudeln", listOf("nudel", "nudeln", "pasta", "spaghetti", "maccheroni", "lasagne"), 75, "Gekochte Nudeln können je nach Portion stark variieren."),
        FoodRule("Reis", listOf("reis", "basmati", "jasminreis", "sushi"), 70, "Reis enthält viele schnelle Kohlenhydrate."),
        FoodRule("Kartoffeln", listOf("kartoffel", "kartoffeln", "püree", "pueree"), 45, "Kartoffelportion geschätzt."),
        FoodRule("Pommes", listOf("pommes", "fritten", "french fries"), 55, "Pommes variieren durch Fett und Portionsgröße."),
        FoodRule("Brot/Brötchen", listOf("brot", "brötchen", "broetchen", "toast", "baguette", "semmel"), 40, "Brot nach typischer Portion geschätzt."),
        FoodRule("Döner", listOf("döner", "doener", "kebab", "dürüm", "dueruem"), 75, "Brotanteil ist meist der größte KH-Faktor."),
        FoodRule("Burger", listOf("burger", "cheeseburger", "hamburger"), 45, "Brötchen und Saucen berücksichtigt."),
        FoodRule("Banane", listOf("banane", "banana"), 25, "Eine normale Banane hat grob 20 bis 30 g KH."),
        FoodRule("Apfel", listOf("apfel", "apple"), 15, "Ein Apfel hat meist 12 bis 20 g KH."),
        FoodRule("Müsli/Hafer", listOf("müsli", "muesli", "hafer", "porridge", "cornflakes"), 55, "Müsli schwankt stark durch Zuckeranteil."),
        FoodRule("Joghurt/Pudding", listOf("joghurt", "yoghurt", "pudding", "protein pudding", "high protein"), 18, "Milchprodukte je nach Zuckeranteil prüfen."),
        FoodRule("Cola/Saft", listOf("cola", "saft", "juice", "limonade", "fanta", "sprite"), 30, "Getränke können sehr schnell wirken."),
        FoodRule("Schokolade", listOf("schokolade", "chocolate", "riegel"), 35, "Fett verzögert die Wirkung teilweise."),
        FoodRule("Kuchen", listOf("kuchen", "torte", "muffin", "donut"), 45, "Süßspeisen sind sehr variabel."),
        FoodRule("Salat", listOf("salat", "gemüse", "gemuese", "gurke", "tomate"), 12, "Ohne Brot/Croutons meist wenige KH."),
        FoodRule("Suppe", listOf("suppe", "eintopf"), 25, "Je nach Nudeln, Kartoffeln oder Reis unterschiedlich."),
        FoodRule("Eis", listOf("eis", "ice cream"), 30, "Zucker und Fett können verzögert wirken.")
    )

    fun estimate(description: String, portion: String): FoodEstimate {
        val text = description.lowercase(Locale.getDefault())
        val matches = rules.filter { rule -> rule.keywords.any { keyword -> text.contains(keyword) } }

        val baseCarbs = when {
            matches.isEmpty() && text.isBlank() -> 40
            matches.isEmpty() -> 50
            else -> matches.sumOf { it.carbsNormalPortion }
        }

        val multiplier = when (portion.lowercase(Locale.getDefault())) {
            "klein" -> 0.7
            "groß" -> 1.35
            "gross" -> 1.35
            else -> 1.0
        }

        val carbs = (baseCarbs * multiplier).toInt().coerceAtLeast(0)
        val confidence = when {
            text.isBlank() -> "niedrig"
            matches.size >= 2 -> "mittel"
            matches.size == 1 -> "mittel"
            else -> "niedrig bis mittel"
        }

        val matchedNames = matches.map { it.name }.distinct()
        val notes = when {
            text.isBlank() -> "Keine Beschreibung; Standardwert verwendet."
            matches.isEmpty() -> "Keine eindeutige Regel erkannt; konservative Standardschätzung verwendet."
            else -> matches.joinToString(" ") { it.note }
        }

        return FoodEstimate(
            totalCarbs = carbs,
            ke = carbs / 10.0,
            be = carbs / 12.0,
            confidence = confidence,
            matchedFoods = matchedNames,
            notes = notes
        )
    }
}

private fun formatOne(value: Double): String {
    return String.format(Locale.GERMAN, "%.1f", value)
}
