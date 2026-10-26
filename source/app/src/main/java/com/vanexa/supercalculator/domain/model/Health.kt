package com.vanexa.supercalculator.domain.model

enum class Gender {
    MALE, FEMALE
}

enum class BMICategory(val label: String, val colorHex: String) {
    UNDERWEIGHT("Underweight", "#2196F3"),      // Blue
    NORMAL("Normal", "#4CAF50"),                // Green
    OVERWEIGHT("Overweight", "#FF9800"),        // Orange
    OBESE("Obese", "#F44336")                   // Red
}

data class BMIResult(
    val bmi: Double,
    val category: BMICategory,
    val idealWeightRange: Pair<Double, Double>,
    val interpretation: String
)

enum class SizeRegion {
    US, EU, UK, ASIA, ID
}

data class ClothingSize(
    val size: String,
    val region: SizeRegion,
    val chestCm: IntRange,
    val waistCm: IntRange,
    val heightCmMin: Int,
    val weightKgRange: IntRange
)

data class SizeRecommendation(
    val primarySize: ClothingSize,
    val alternativeSizes: List<ClothingSize>,
    val confidence: Double
)

enum class ActivityLevel(val label: String, val multiplier: Double) {
    SEDENTARY("Sedentary", 1.2),
    LIGHT("Light", 1.375),
    MODERATE("Moderate", 1.55),
    ACTIVE("Active", 1.725),
    VERY_ACTIVE("Very Active", 1.9)
}

enum class FitnessGoal {
    LOSE_WEIGHT, MAINTAIN, GAIN_WEIGHT
}

enum class MacroProfile(val label: String) {
    STANDARD("Standard (30/40/30)"),
    LOW_CARB("Low Carb (40/20/40)"),
    HIGH_CARB("High Carb (20/60/20)"),
    KETO("Keto (25/5/70)")
}

data class CalorieResult(
    val bmr: Double,
    val maintenanceCalories: Double,
    val weightLossCalories: Double,
    val weightGainCalories: Double,
    val macros: MacroSplit
)

data class MacroSplit(
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatsGrams: Double,
    val proteinCalories: Double,
    val carbsCalories: Double,
    val fatsCalories: Double
)

data class FoodItem(
    val name: String,
    val servingSize: String,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double
)

