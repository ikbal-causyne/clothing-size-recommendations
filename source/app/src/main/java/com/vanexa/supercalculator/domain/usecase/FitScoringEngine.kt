package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * Fit Scoring Engine - PRD Section 7
 * Calculates fit scores between user body and garment sizes
 */
object FitScoringEngine {

    private fun determineFitQuality(score: Double): FitQuality {
        return when {
            score >= 0.90 -> FitQuality.EXCELLENT
            score >= 0.80 -> FitQuality.VERY_GOOD
            score >= 0.70 -> FitQuality.GOOD
            score >= 0.60 -> FitQuality.ACCEPTABLE
            score >= 0.50 -> FitQuality.MARGINAL
            else -> FitQuality.MARGINAL
        }
    }

    /**
     * Enhanced Probabilistic Dimension Scoring per Engine Vision Update
     */
    private fun scoreDimension(
        userMeasurement: Double,
        garmentRange: MeasurementRange,
        fitPreference: FitPreference,
        userConfidence: Double = 1.0,
        dimension: String = "unknown",
        thresholds: DynamicThresholdCalculator.DynamicThresholds
    ): DimensionScore {

        val garmentMid = (garmentRange.min + garmentRange.max) / 2.0
        val garmentTolerance = (garmentRange.max - garmentRange.min) / 2.0

        val preferenceAdjustment = when (fitPreference) {
            FitPreference.SLIM -> -2.0
            FitPreference.REGULAR -> 0.0
            FitPreference.RELAXED -> +2.0
        }

        val adjustedTarget = garmentMid + preferenceAdjustment

        val distance = abs(userMeasurement - adjustedTarget)
        val score = exp(-0.5 * (distance / garmentTolerance).pow(2.0))

        val finalScore = score.coerceIn(0.0, 1.0) * userConfidence
        val dimensionStatus = DynamicThresholdCalculator.determineDimensionStatus(finalScore, dimension, thresholds)

        return DimensionScore(
            value = finalScore,
            rawScore = score,
            confidence = userConfidence,
            status = dimensionStatus,
            difference = userMeasurement - adjustedTarget,
            note = generateDimensionNote(userMeasurement, garmentRange, finalScore)
        )
    }

// ... existing code ...
    /**
     * Dimension importance weights per garment type
     * PRD Section 7.1
     * Made public for use by ConfidenceVectorCalculator
     */
    fun getWeightsForGarment(garmentType: GarmentType): DimensionWeights {
        return when (garmentType) {
            GarmentType.TSHIRT -> DimensionWeights(chest = 0.70, length = 0.30)
            GarmentType.SHIRT -> DimensionWeights(chest = 0.50, shoulder = 0.30, length = 0.20)
            GarmentType.POLO_SHIRT -> DimensionWeights(chest = 0.55, shoulder = 0.25, length = 0.20)
            GarmentType.HOODIE -> DimensionWeights(chest = 0.65, length = 0.35)
            GarmentType.SWEATER -> DimensionWeights(chest = 0.60, length = 0.40)
            GarmentType.JACKET -> DimensionWeights(chest = 0.45, shoulder = 0.35, length = 0.20)
            GarmentType.BLAZER -> DimensionWeights(chest = 0.40, shoulder = 0.40, length = 0.20)
            GarmentType.JEANS -> DimensionWeights(waist = 0.40, hip = 0.35, inseam = 0.25)
            GarmentType.TROUSERS -> DimensionWeights(waist = 0.40, hip = 0.30, inseam = 0.30)
            GarmentType.CHINOS -> DimensionWeights(waist = 0.45, hip = 0.30, inseam = 0.25)
            GarmentType.CARGO_PANTS -> DimensionWeights(waist = 0.35, hip = 0.35, inseam = 0.30)
            GarmentType.SHORTS -> DimensionWeights(waist = 0.55, hip = 0.45)
            GarmentType.JOGGERS -> DimensionWeights(waist = 0.35, hip = 0.30, inseam = 0.35)
        }
    }
    
    /**
     * Calculates fit score between user body and garment size
     * Returns 0.0 (worst) to 1.0 (perfect)
     * PRD Section 7.2
     * Enhanced with dynamic thresholds and garment physics per Engine Vision Update
     */
    fun calculateFitScore(
        userBody: EstimatedBodyMeasurements,
        garmentSize: SizeChartEntry,
        garmentType: GarmentType,
        fitPreference: FitPreference = FitPreference.REGULAR,
        dynamicThresholds: DynamicThresholdCalculator.DynamicThresholds? = null
    ): FitScore {
        
        val weights = getWeightsForGarment(garmentType)
        val thresholds = dynamicThresholds ?: DynamicThresholdCalculator.calculateThresholds(garmentType)
        val garmentPhysics = GarmentPhysicsEngine.getGarmentPhysics(garmentType)
        
        val scores = mutableMapOf<String, DimensionScore>()
        var totalWeightedScore = 0.0
        
        // Get confidence for chest measurement (default high-confidence if missing)
        val chestConfidence = userBody.confidence.breakdown["chest"]?.value ?: 0.90
        
        // Score each dimension with confidence weighting and garment physics
        if (weights.chest > 0 && garmentSize.chest != null) {
            // Apply garment physics adjustment
            val adjustedRange = GarmentPhysicsEngine.adjustRangeForPhysics(
                garmentSize.chest, "chest", garmentPhysics
            )
            val score = scoreDimension(
                userMeasurement = userBody.chest,
                garmentRange = adjustedRange,
                fitPreference = fitPreference,
                userConfidence = chestConfidence,
                dimension = "chest",
                thresholds = thresholds
            )
            scores["chest"] = score
            totalWeightedScore += score.value * weights.chest * chestConfidence
        }
        
        // Get confidence for each measurement
        val waistConfidence = userBody.confidence.breakdown["waist"]?.value ?: 0.90
        val hipConfidence = userBody.confidence.breakdown["hip"]?.value ?: 0.90
        val shoulderConfidence = userBody.confidence.breakdown["shoulder"]?.value ?: 0.90
        val inseamConfidence = userBody.confidence.breakdown["inseam"]?.value ?: 0.90
        
        if (weights.waist > 0 && garmentSize.waist != null) {
            val adjustedRange = GarmentPhysicsEngine.adjustRangeForPhysics(
                garmentSize.waist, "waist", garmentPhysics
            )
            val score = scoreDimension(
                userMeasurement = userBody.waist,
                garmentRange = adjustedRange,
                fitPreference = fitPreference,
                userConfidence = waistConfidence,
                dimension = "waist",
                thresholds = thresholds
            )
            scores["waist"] = score
            totalWeightedScore += score.value * weights.waist * waistConfidence
        }
        
        if (weights.hip > 0 && garmentSize.hip != null) {
            val adjustedRange = GarmentPhysicsEngine.adjustRangeForPhysics(
                garmentSize.hip, "hip", garmentPhysics
            )
            val score = scoreDimension(
                userMeasurement = userBody.hip,
                garmentRange = adjustedRange,
                fitPreference = fitPreference,
                userConfidence = hipConfidence,
                dimension = "hip",
                thresholds = thresholds
            )
            scores["hip"] = score
            totalWeightedScore += score.value * weights.hip * hipConfidence
        }
        
        if (weights.shoulder > 0 && garmentSize.shoulder != null) {
            val adjustedRange = GarmentPhysicsEngine.adjustRangeForPhysics(
                garmentSize.shoulder, "shoulder", garmentPhysics
            )
            val score = scoreDimension(
                userMeasurement = userBody.shoulder,
                garmentRange = adjustedRange,
                fitPreference = fitPreference,
                userConfidence = shoulderConfidence,
                dimension = "shoulder",
                thresholds = thresholds
            )
            scores["shoulder"] = score
            totalWeightedScore += score.value * weights.shoulder * shoulderConfidence
        }
        
        if (weights.length > 0 && garmentSize.length != null) {
            val adjustedRange = GarmentPhysicsEngine.adjustRangeForPhysics(
                garmentSize.length, "length", garmentPhysics
            )
            val score = scoreDimension(
                userMeasurement = userBody.inseam, // Approximate
                garmentRange = adjustedRange,
                fitPreference = fitPreference,
                userConfidence = inseamConfidence,
                dimension = "length",
                thresholds = thresholds
            )
            scores["length"] = score
            totalWeightedScore += score.value * weights.length * inseamConfidence
        }
        
        if (weights.inseam > 0 && garmentSize.inseam != null) {
            val adjustedRange = GarmentPhysicsEngine.adjustRangeForPhysics(
                garmentSize.inseam, "inseam", garmentPhysics
            )
            val score = scoreDimension(
                userMeasurement = userBody.inseam,
                garmentRange = adjustedRange,
                fitPreference = fitPreference,
                userConfidence = inseamConfidence,
                dimension = "inseam",
                thresholds = thresholds
            )
            scores["inseam"] = score
            totalWeightedScore += score.value * weights.inseam * inseamConfidence
        }
        
        // Weighted aggregate per Engine Vision Module 3.4
        // Formula: totalScore += score * weight * confidence, totalWeight += weight * confidence, totalConfidence *= confidence
        var totalConfidence = 1.0
        val totalWeight = listOfNotNull(
            if (weights.chest > 0 && garmentSize.chest != null) weights.chest * chestConfidence else null,
            if (weights.waist > 0 && garmentSize.waist != null) weights.waist * waistConfidence else null,
            if (weights.hip > 0 && garmentSize.hip != null) weights.hip * hipConfidence else null,
            if (weights.shoulder > 0 && garmentSize.shoulder != null) weights.shoulder * shoulderConfidence else null,
            if (weights.length > 0 && garmentSize.length != null) weights.length * inseamConfidence else null,
            if (weights.inseam > 0 && garmentSize.inseam != null) weights.inseam * inseamConfidence else null
        ).sum()
        
        // Confidence propagation per Engine Vision: compute geometric mean of present confidences
        val presentConfs = listOfNotNull(
            if (weights.chest > 0 && garmentSize.chest != null) chestConfidence else null,
            if (weights.waist > 0 && garmentSize.waist != null) waistConfidence else null,
            if (weights.hip > 0 && garmentSize.hip != null) hipConfidence else null,
            if (weights.shoulder > 0 && garmentSize.shoulder != null) shoulderConfidence else null,
            if (weights.length > 0 && garmentSize.length != null) inseamConfidence else null,
            if (weights.inseam > 0 && garmentSize.inseam != null) inseamConfidence else null
        )

        totalConfidence = if (presentConfs.isEmpty()) {
            0.90
        } else {
            // geometric mean
            val prod = presentConfs.fold(1.0) { acc, v -> acc * v }
            Math.pow(prod, 1.0 / presentConfs.size)
        }

        val rawScore = if (totalWeight > 0) totalWeightedScore / totalWeight else 0.0

        // Apply Engine Vision final score collapse: final = raw × (dataConfidence²)
        val finalScore = (rawScore * (totalConfidence * totalConfidence)).coerceIn(0.0, 1.0)

        android.util.Log.d("FitScoringEngine", "🎯 [Engine Vision Update] Weighted aggregate: totalScore=$totalWeightedScore, totalWeight=$totalWeight, rawScore=$rawScore, dataConfidence=$totalConfidence, finalScore=$finalScore")
        
        // Use dynamic thresholds for fit quality determination
        val fitQuality = DynamicThresholdCalculator.determineFitQuality(finalScore, thresholds)
        
        // Convert String keys to BodyDimension enum for compatibility
        val dimensionScoresEnum = scores.mapKeys { (key, _) ->
            when (key) {
                "chest" -> BodyDimension.CHEST
                "waist" -> BodyDimension.WAIST
                "hip" -> BodyDimension.HIP
                "shoulder" -> BodyDimension.SHOULDER
                "length" -> BodyDimension.LENGTH
                "inseam" -> BodyDimension.INSEAM
                else -> throw IllegalArgumentException("Unknown dimension: $key")
            }
        }
        
        return FitScore(
            overall = finalScore,
            rawFitScore = rawScore.coerceIn(0.0, 1.0),
            dataConfidence = totalConfidence.coerceIn(0.0, 1.0),
            dimensionScores = dimensionScoresEnum,
            fitQuality = fitQuality,
            recommendation = generateRecommendation(finalScore, scores)
        )
    }
    

    
    private fun generateRecommendation(score: Double, @Suppress("UNUSED_PARAMETER") scores: Map<String, DimensionScore>): String {
        return when {
            score >= 0.90 -> "Excellent fit! This size should fit you perfectly."
            score >= 0.75 -> "Very good fit. This size should work well for you."
            score >= 0.60 -> "Acceptable fit. This size may work, but consider alternatives."
            else -> {
                val fitProbability = (score * 100).toInt().coerceIn(1, 100)
                "Low Fit Probability ($fitProbability%) - Significant dimension mismatch. This size may not fit well."
            }
        }
    }
    
    private fun generateDimensionNote(
        userMeasurement: Double,
        garmentRange: MeasurementRange,
        @Suppress("UNUSED_PARAMETER") score: Double
    ): String {
        val garmentMid = (garmentRange.min + garmentRange.max) / 2.0
        val difference = userMeasurement - garmentMid
        
        return when {
            abs(difference) <= 2.0 -> "Perfect match"
            difference > 0 -> "You are ${String.format("%.1f", abs(difference))}cm larger than ideal"
            else -> "You are ${String.format("%.1f", abs(difference))}cm smaller than ideal"
        }
    }
}

data class DimensionWeights(
    val chest: Double = 0.0,
    val waist: Double = 0.0,
    val hip: Double = 0.0,
    val shoulder: Double = 0.0,
    val length: Double = 0.0,
    val inseam: Double = 0.0
)

