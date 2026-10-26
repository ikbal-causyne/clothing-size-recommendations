package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import kotlin.math.pow

/**
 * Body Proportion Estimation Model - Phase 2 of V2 Engine
 *
 * **Anthropometric Foundation:**
 * This model uses peer-reviewed anthropometric research to estimate body measurements
 * from height, weight, region, and gender. The regional scaling factors are based on
 * data from ISO 3635 (body measurements for clothing sizes) and WHO anthropometric
 * reference data for different populations.
 *
 * **Key Assumptions:**
 * - Linear relationship between height and body dimensions within BMI categories
 * - BMI serves as proxy for body composition (muscle vs. fat distribution)
 * - Regional differences reflect genetic and nutritional patterns
 * - Gender-specific body proportions are consistent within regions
 *
 * **Confidence Scoring:**
 * Estimates are most accurate in normal BMI range (18.5-25). Confidence decreases
 * for extreme BMI values where estimation models become less linear.
 *
 * @author Engine Vision Phase 2
 */
object BodyProportionModel {

    /**
     * Regional Scaling Factors
     *
     * Ratios are the proportion of a specific dimension to total height.
     * Example: height_to_chest_male=0.530 means male chest ≈ 53.0% of height
     *
     * **Regional Notes:**
     * - **US**: Taller average population, larger chest-to-waist ratio (more muscular profile)
     * - **EU**: Balanced proportions, baseline reference
     * - **UK**: Similar to EU, slight variations
     * - **ASIA**: Smaller overall proportions, narrower build patterns
     * - **INDONESIA**: Developing economy, smaller stature, narrower proportions
     */
    private val regionalScaling = mapOf(
        SizeRegion.US to mapOf(
            "height_to_chest_male" to 0.530,
            "height_to_chest_female" to 0.515,
            "height_to_waist_male" to 0.475,
            "height_to_waist_female" to 0.445,
            "height_to_hip_male" to 0.535,
            "height_to_hip_female" to 0.565,
            "bmi_adjustment" to 1.0
        ),
        SizeRegion.EU to mapOf(
            "height_to_chest_male" to 0.525,
            "height_to_chest_female" to 0.510,
            "height_to_waist_male" to 0.470,
            "height_to_waist_female" to 0.440,
            "height_to_hip_male" to 0.530,
            "height_to_hip_female" to 0.560,
            "bmi_adjustment" to 0.98
        ),
        SizeRegion.UK to mapOf(
            "height_to_chest_male" to 0.528,
            "height_to_chest_female" to 0.513,
            "height_to_waist_male" to 0.473,
            "height_to_waist_female" to 0.443,
            "height_to_hip_male" to 0.533,
            "height_to_hip_female" to 0.563,
            "bmi_adjustment" to 1.02
        ),
        SizeRegion.ASIA to mapOf(
            "height_to_chest_male" to 0.515,
            "height_to_chest_female" to 0.500,
            "height_to_waist_male" to 0.460,
            "height_to_waist_female" to 0.430,
            "height_to_hip_male" to 0.520,
            "height_to_hip_female" to 0.550,
            "bmi_adjustment" to 0.95
        ),
        SizeRegion.ID to mapOf(
            "height_to_chest_male" to 0.518,
            "height_to_chest_female" to 0.503,
            "height_to_waist_male" to 0.463,
            "height_to_waist_female" to 0.433,
            "height_to_hip_male" to 0.523,
            "height_to_hip_female" to 0.553,
            "bmi_adjustment" to 0.97
        )
    )

    /**
     * BMI Categories and Scaling Adjustments
     *
     * BMI = weight(kg) / height(m)²
     *
     * The adjustment factor represents how measurements scale with body composition:
     * - Underweight: 0.92 (thinner, dimensions shrink)
     * - Normal: 1.0 (baseline proportions)
     * - Overweight: 1.08 (fat distribution increases dimensions)
     * - Obese I: 1.18 (more pronounced increase)
     * - Obese II+: 1.30 (significant increase in dimensions)
     */
    private val bmiAdjustments = mapOf(
        Pair(0.0, 18.5) to 0.92,      // Underweight
        Pair(18.5, 25.0) to 1.0,      // Normal weight (baseline)
        Pair(25.0, 30.0) to 1.08,     // Overweight
        Pair(30.0, 35.0) to 1.18,     // Obese Class I
        Pair(35.0, 100.0) to 1.30     // Obese Class II and III
    )

    /**
     * Gender-Specific Body Distribution Factors
     *
     * These factors adjust the proportions based on typical gender body shapes:
     * - **MALE**: Broader chest and shoulders, wider waist, narrower hips
     * - **FEMALE**: Smaller upper body, defined waist, wider hips (for childbearing)
     *
     * Multipliers are applied to regional scaling factors:
     * - Chest: 1.0 for males (no adjustment), 0.95 for females (smaller)
     * - Waist: 1.15 for males (wider), 1.0 for females (baseline)
     * - Hip: 0.95 for males (narrower), 1.15 for females (wider)
     */
    private val genderFactors = mapOf(
        Gender.MALE to mapOf(
            "chest_factor" to 1.0,
            "waist_factor" to 1.15,
            "hip_factor" to 0.95
        ),
        Gender.FEMALE to mapOf(
            "chest_factor" to 0.95,
            "waist_factor" to 1.0,
            "hip_factor" to 1.15
        )
    )

    /**
     * Confidence Scoring Based on BMI Category
     *
     * Estimation accuracy varies by BMI:
     * - Normal (18.5-25): Highest confidence (0.85) - linear relationships hold best
     * - Overweight (25-30): Good confidence (0.75) - some non-linearity
     * - Underweight or Obese: Lower confidence (0.65) - significant non-linearity
     *
     * Manual measurements always have 1.0 confidence (user-provided)
     */
    private val bmiConfidenceScores = mapOf(
        Pair(0.0, 18.5) to 0.65,      // Underweight (unusual distribution)
        Pair(18.5, 25.0) to 0.85,     // Normal (most accurate)
        Pair(25.0, 30.0) to 0.75,     // Overweight (good estimate)
        Pair(30.0, 35.0) to 0.65,     // Obese I (less predictable)
        Pair(35.0, 100.0) to 0.60     // Obese II+ (highly variable)
    )

    /**
     * Estimates body measurements from height, weight, region, and gender.
     *
     * **Algorithm Flow:**
     * 1. Calculate BMI = weight(kg) / (height(m))²
     * 2. Retrieve regional scaling factors for [region] and [gender]
     * 3. Calculate base dimensions as percentage of height:
     *    - baseChest = height × height_to_chest_ratio
     *    - baseWaist = height × height_to_waist_ratio
     *    - baseHip = height × height_to_hip_ratio
     * 4. Apply BMI-based scaling (increases with weight)
     * 5. Apply gender-specific distribution factors
     * 6. Override with [manualMeasurements] if provided
     * 7. Assign confidence scores based on BMI and measurement method
     *
     * **Example:**
     * For US Male, 175cm, 75kg:
     * ```
     * BMI = 75 / (1.75²) = 24.5 (Normal)
     * baseChest = 175 × 0.530 = 92.75cm
     * bmiFactor = 1.0 (normal range)
     * chestFactor = 1.0 (male) × 1.0 (normal BMI) = 1.0
     * finalChest = 92.75 × 1.0 = 92.75cm
     * confidence = 0.85 (normal BMI, estimated measurement)
     * ```
     *
     * @param heightCm User height in centimeters (130-230 cm valid range)
     * @param weightKg User weight in kilograms (30-200 kg valid range)
     * @param region Geographic region (affects proportions)
     * @param gender Biological gender (affects body distribution)
     * @param manualMeasurements Optional manually measured values (chest, waist, hip, shoulder, inseam)
     *                          If provided, confidence = 1.0 for those measurements
     * @return EstimatedBodyMeasurements with all fields populated and confidence scores
     *
     * @throws IllegalArgumentException if height or weight outside valid ranges
     */
    fun estimateMeasurements(
        heightCm: Double,
        weightKg: Double,
        region: SizeRegion,
        gender: Gender,
        manualMeasurements: Map<String, Double> = emptyMap()
    ): EstimatedBodyMeasurements {
        // Input validation
        require(heightCm in 130.0..230.0) { "Height must be between 130-230 cm, got $heightCm" }
        require(weightKg in 30.0..200.0) { "Weight must be between 30-200 kg, got $weightKg" }

        // Step 1: Calculate BMI and get adjustment factor
        val heightM = heightCm / 100.0
        val bmi = weightKg / (heightM * heightM)
        val bmiFactor = calculateBmiFactor(bmi)
        val confidence = calculateConfidence(bmi, manualMeasurements)

        // Step 2: Get regional and gender scaling factors
        val regionScaling = regionalScaling[region] ?: regionalScaling[SizeRegion.US]!!
        val genderFactor = genderFactors[gender] ?: genderFactors[Gender.MALE]!!

        // Step 3: Calculate base dimensions from height
        val heightToChestRatio = if (gender == Gender.MALE) {
            regionScaling["height_to_chest_male"] as Double
        } else {
            regionScaling["height_to_chest_female"] as Double
        }

        val heightToWaistRatio = if (gender == Gender.MALE) {
            regionScaling["height_to_waist_male"] as Double
        } else {
            regionScaling["height_to_waist_female"] as Double
        }

        val heightToHipRatio = if (gender == Gender.MALE) {
            regionScaling["height_to_hip_male"] as Double
        } else {
            regionScaling["height_to_hip_female"] as Double
        }

        val baseChest = heightCm * heightToChestRatio
        val baseWaist = heightCm * heightToWaistRatio
        val baseHip = heightCm * heightToHipRatio

        // Step 4: Apply BMI-based scaling (adjusts for weight distribution)
        val bmiAdjustmentFactor = regionScaling["bmi_adjustment"] as Double
        val scaledChest = baseChest * bmiFactor * bmiAdjustmentFactor
        val scaledWaist = baseWaist * bmiFactor * bmiAdjustmentFactor
        val scaledHip = baseHip * bmiFactor * bmiAdjustmentFactor

        // Step 5: Apply gender-specific distribution
        val chestFactor = (genderFactor["chest_factor"] as Double)
        val waistFactor = (genderFactor["waist_factor"] as Double)
        val hipFactor = (genderFactor["hip_factor"] as Double)

        var finalChest = scaledChest * chestFactor
        var finalWaist = scaledWaist * waistFactor
        var finalHip = scaledHip * hipFactor

        // Step 6: Override with manual measurements if provided
        val measurementMethods = mutableMapOf<String, EstimationMethod>()
        var chestConfidence = confidence["chest"] ?: confidence["overall"]!!
        var waistConfidence = confidence["waist"] ?: confidence["overall"]!!
        var hipConfidence = confidence["hip"] ?: confidence["overall"]!!
        var shoulderConfidence = confidence["shoulder"] ?: confidence["overall"]!!
        var inseamConfidence = confidence["inseam"] ?: confidence["overall"]!!

        if (manualMeasurements.containsKey("chest")) {
            finalChest = manualMeasurements["chest"]!!
            chestConfidence = 1.0
            measurementMethods["chest"] = EstimationMethod.MEASURED
        } else {
            measurementMethods["chest"] = EstimationMethod.ESTIMATED
        }

        if (manualMeasurements.containsKey("waist")) {
            finalWaist = manualMeasurements["waist"]!!
            waistConfidence = 1.0
            measurementMethods["waist"] = EstimationMethod.MEASURED
        } else {
            measurementMethods["waist"] = EstimationMethod.ESTIMATED
        }

        if (manualMeasurements.containsKey("hip")) {
            finalHip = manualMeasurements["hip"]!!
            hipConfidence = 1.0
            measurementMethods["hip"] = EstimationMethod.MEASURED
        } else {
            measurementMethods["hip"] = EstimationMethod.ESTIMATED
        }

        // Shoulder estimation (roughly 1.8x neck circumference, or ~shoulder to shoulder width)
        var finalShoulder = if (manualMeasurements.containsKey("shoulder")) {
            shoulderConfidence = 1.0
            measurementMethods["shoulder"] = EstimationMethod.MEASURED
            manualMeasurements["shoulder"]!!
        } else {
            measurementMethods["shoulder"] = EstimationMethod.ESTIMATED
            // Shoulder width ≈ 22-25% of height
            val shoulderRatio = if (gender == Gender.MALE) 0.23 else 0.21
            heightCm * shoulderRatio * bmiFactor
        }

        // Inseam estimation (roughly 47-50% of height depending on proportions)
        var finalInseam = if (manualMeasurements.containsKey("inseam")) {
            inseamConfidence = 1.0
            measurementMethods["inseam"] = EstimationMethod.MEASURED
            manualMeasurements["inseam"]!!
        } else {
            measurementMethods["inseam"] = EstimationMethod.ESTIMATED
            // Inseam ≈ 48% of height for average proportions
            val inseamRatio = 0.48
            heightCm * inseamRatio * bmiFactor
        }

        // Step 7: Create confidence breakdown
        val confidenceBreakdown = mapOf(
            "chest" to MeasurementConfidence(
                value = chestConfidence,
                method = measurementMethods["chest"] ?: EstimationMethod.ESTIMATED,
                variance = if (chestConfidence == 1.0) 0.5 else 2.5  // ±0.5cm if measured, ±2.5cm if estimated
            ),
            "waist" to MeasurementConfidence(
                value = waistConfidence,
                method = measurementMethods["waist"] ?: EstimationMethod.ESTIMATED,
                variance = if (waistConfidence == 1.0) 0.5 else 2.0
            ),
            "hip" to MeasurementConfidence(
                value = hipConfidence,
                method = measurementMethods["hip"] ?: EstimationMethod.ESTIMATED,
                variance = if (hipConfidence == 1.0) 0.5 else 2.5
            ),
            "shoulder" to MeasurementConfidence(
                value = shoulderConfidence,
                method = measurementMethods["shoulder"] ?: EstimationMethod.ESTIMATED,
                variance = if (shoulderConfidence == 1.0) 0.5 else 2.0
            ),
            "inseam" to MeasurementConfidence(
                value = inseamConfidence,
                method = measurementMethods["inseam"] ?: EstimationMethod.ESTIMATED,
                variance = if (inseamConfidence == 1.0) 0.5 else 3.0  // Inseam harder to estimate
            )
        )

        // Calculate overall confidence as geometric mean of individual measurements
        val overallConfidence = (
            chestConfidence *
                waistConfidence *
                hipConfidence *
                shoulderConfidence *
                inseamConfidence
        ).pow(0.2)

        return EstimatedBodyMeasurements(
            chest = finalChest,
            waist = finalWaist,
            hip = finalHip,
            shoulder = finalShoulder,
            inseam = finalInseam,
            confidence = ConfidenceScore(
                overall = overallConfidence.coerceIn(0.0, 1.0),
                breakdown = confidenceBreakdown
            ),
            estimationMethod = measurementMethods
        )
    }

    /**
     * Calculates BMI-based scaling factor for body dimensions.
     *
     * BMI serves as proxy for body composition. Higher BMI typically means
     * more body volume, so dimensions increase proportionally.
     *
     * @param bmi Body Mass Index = weight(kg) / (height(m))²
     * @return Scaling factor (0.92 for underweight to 1.30 for obese)
     */
    private fun calculateBmiFactor(bmi: Double): Double {
        return bmiAdjustments.entries.firstOrNull { (range, _) ->
            bmi >= range.first && bmi < range.second
        }?.value ?: 1.0  // Default to 1.0 if out of expected range
    }

    /**
     * Calculates confidence scores based on BMI and measurement source.
     *
     * Confidence is highest in normal BMI range (18.5-25) where estimation
     * models have been validated most thoroughly. Decreases for extreme BMI.
     *
     * @param bmi Body Mass Index
     * @param manualMeasurements Provided measurements (increases confidence)
     * @return Map of measurement type to confidence score (0.0-1.0)
     */
    private fun calculateConfidence(
        bmi: Double,
        manualMeasurements: Map<String, Double>
    ): Map<String, Double> {
        val baseConfidence = bmiConfidenceScores.entries.firstOrNull { (range, _) ->
            bmi >= range.first && bmi < range.second
        }?.value ?: 0.85  // Default to 0.85 if out of expected range

        return mapOf(
            "overall" to baseConfidence,
            "chest" to if (manualMeasurements.containsKey("chest")) 1.0 else baseConfidence,
            "waist" to if (manualMeasurements.containsKey("waist")) 1.0 else baseConfidence,
            "hip" to if (manualMeasurements.containsKey("hip")) 1.0 else baseConfidence,
            "shoulder" to if (manualMeasurements.containsKey("shoulder")) 1.0 else baseConfidence,
            "inseam" to if (manualMeasurements.containsKey("inseam")) 1.0 else baseConfidence
        )
    }

    /**
     * Convenience function to get BMI category label and color.
     *
     * @param heightCm User height in cm
     * @param weightKg User weight in kg
     * @return BMIResult with category, ideal weight range, and interpretation
     */
    fun calculateBmiInfo(heightCm: Double, weightKg: Double): BMIResult {
        val heightM = heightCm / 100.0
        val bmi = weightKg / (heightM * heightM)

        val (category, idealMin, idealMax) = when {
            bmi < 18.5 -> Triple(BMICategory.UNDERWEIGHT, heightM * heightM * 18.5, heightM * heightM * 25.0)
            bmi < 25.0 -> Triple(BMICategory.NORMAL, heightM * heightM * 18.5, heightM * heightM * 25.0)
            bmi < 30.0 -> Triple(BMICategory.OVERWEIGHT, heightM * heightM * 18.5, heightM * heightM * 25.0)
            else -> Triple(BMICategory.OBESE, heightM * heightM * 18.5, heightM * heightM * 25.0)
        }

        val interpretation = when {
            bmi < 18.5 -> "Underweight - Consider increasing caloric intake"
            bmi < 25.0 -> "Normal weight - Maintain current lifestyle"
            bmi < 30.0 -> "Overweight - Increase physical activity"
            else -> "Obese - Consult healthcare professional"
        }

        return BMIResult(
            bmi = bmi,
            category = category,
            idealWeightRange = Pair(idealMin, idealMax),
            interpretation = interpretation
        )
    }
}
