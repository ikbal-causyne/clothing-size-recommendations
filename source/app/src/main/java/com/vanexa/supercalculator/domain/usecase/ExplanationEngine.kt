package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import android.util.Log
import kotlin.math.abs

/**
 * EXPLANATION ENGINE per Engine Vision Update
 * Generates structured explanations with numeric differences (cm-based)
 * Domain logic returns structured signals, not UX copy
 */
object ExplanationEngine {
    private const val TAG = "ExplanationEngine"
    
    /**
     * Structured explanation signal (domain logic)
     */
    data class FitExplanation(
        val primarySize: String,
        val fitScore: Double,
        val fitQuality: FitQuality,
        val dimensionDeltas: Map<BodyDimension, DimensionDelta>,  // BUG 6 FIX: Use enum instead of String
        val biomechanicalDeltas: BiomechanicalDeltas,      // Overall fit deltas
        val riskFactors: List<RiskFactor>,                  // Structured risk signals
        val recommendationCode: RecommendationCode         // Structured recommendation
    )
    
    data class DimensionDelta(
        val dimension: BodyDimension,  // BUG 6 FIX: Use enum
        val userMeasurement: Double,
        val garmentMidpoint: Double,
        val differenceCm: Double,          // user - garment (positive = too large, negative = too small)
        val withinRange: Boolean,
        val rangeMin: Double,
        val rangeMax: Double,
        val fitStatus: FitStatus
    )
    
    data class BiomechanicalDeltas(
        val chestDelta: Double?,
        val waistDelta: Double?,
        val hipDelta: Double?,
        val shoulderDelta: Double?,
        val inseamDelta: Double?,
        val overallTightness: Double,       // Negative = tight, positive = loose, 0 = perfect
        val criticalMismatch: BodyDimension?  // BUG 6 FIX: Use enum
    )
    
    data class RiskFactor(
        val code: RiskCode,
        val severity: RiskSeverity,
        val dimension: String?,
        val metric: Double?,
        val message: String  // Technical message
    )
    
    enum class RiskCode {
        MEASUREMENT_TOO_SMALL,
        MEASUREMENT_TOO_LARGE,
        MULTIPLE_DIMENSIONS_MISMATCH,
        LOW_CONFIDENCE_ESTIMATION,
        BETWEEN_SIZES,
        CHART_QUALITY_LOW
    }
    
    enum class RiskSeverity {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW
    }
    
    enum class RecommendationCode {
        STRONG_RECOMMEND,
        RECOMMEND,
        CONDITIONAL_RECOMMEND,
        NOT_RECOMMEND,
        BETWEEN_SIZES
    }
    
    /**
     * Generate structured explanation from fit score
     */
    fun generateExplanation(
        fitScore: FitScore,
        userBody: EstimatedBodyMeasurements,
        garmentSize: SizeChartEntry,
        garmentType: GarmentType
    ): FitExplanation {
        // Calculate dimension deltas (numeric differences)
        val dimensionDeltas = calculateDimensionDeltas(userBody, garmentSize, fitScore.dimensionScores)
        
        // Calculate biomechanical deltas
        val biomechanicalDeltas = calculateBiomechanicalDeltas(dimensionDeltas)
        
        // Identify risk factors
        val riskFactors = identifyRiskFactors(dimensionDeltas, userBody, fitScore)
        
        // Determine recommendation code
        val recommendationCode = determineRecommendationCode(fitScore, riskFactors, biomechanicalDeltas)
        
        Log.d(TAG, "🎯 [Engine Vision Update] Explanation: primarySize=${garmentSize.sizeLabel}, fitScore=${fitScore.overall}, recommendationCode=$recommendationCode")
        dimensionDeltas.forEach { (dimension, delta) ->
            Log.d(TAG, "  $dimension: user=${delta.userMeasurement}cm, garment=${delta.garmentMidpoint}cm, diff=${delta.differenceCm}cm, status=${delta.fitStatus}")
        }
        
        return FitExplanation(
            primarySize = garmentSize.sizeLabel,
            fitScore = fitScore.overall,
            fitQuality = fitScore.fitQuality,
            dimensionDeltas = dimensionDeltas,
            biomechanicalDeltas = biomechanicalDeltas,
            riskFactors = riskFactors,
            recommendationCode = recommendationCode
        )
    }
    
    /**
     * Calculate numeric differences per dimension
     */
    private fun calculateDimensionDeltas(
        userBody: EstimatedBodyMeasurements,
        garmentSize: SizeChartEntry,
        dimensionScores: Map<BodyDimension, DimensionScore>  // BUG 6 FIX: Use enum
    ): Map<BodyDimension, DimensionDelta> {
        val deltas = mutableMapOf<BodyDimension, DimensionDelta>()
        
        // Chest
        garmentSize.chest?.let { range ->
            val userValue = userBody.chest
            // Skip if user measurement is 0.0 (not applicable for garment type)
            if (userValue > 0.1) {
                val garmentMid = (range.min + range.max) / 2.0
                val diff = userValue - garmentMid
                val score = dimensionScores[BodyDimension.CHEST]
                deltas[BodyDimension.CHEST] = DimensionDelta(
                    dimension = BodyDimension.CHEST,
                    userMeasurement = userValue,
                    garmentMidpoint = garmentMid,
                    differenceCm = diff,
                    withinRange = userValue >= range.min && userValue <= range.max,
                    rangeMin = range.min,
                    rangeMax = range.max,
                    fitStatus = score?.status ?: FitStatus.ACCEPTABLE
                )
            }
        }
        
        // Waist
        garmentSize.waist?.let { range ->
            val userValue = userBody.waist
            // Skip if user measurement is 0.0 (not applicable for garment type)
            if (userValue > 0.1) {
                val garmentMid = (range.min + range.max) / 2.0
                val diff = userValue - garmentMid
                val score = dimensionScores[BodyDimension.WAIST]
                deltas[BodyDimension.WAIST] = DimensionDelta(
                    dimension = BodyDimension.WAIST,
                    userMeasurement = userValue,
                    garmentMidpoint = garmentMid,
                    differenceCm = diff,
                    withinRange = userValue >= range.min && userValue <= range.max,
                    rangeMin = range.min,
                    rangeMax = range.max,
                    fitStatus = score?.status ?: FitStatus.ACCEPTABLE
                )
            }
        }
        
        // Hip
        garmentSize.hip?.let { range ->
            val userValue = userBody.hip
            // Skip if user measurement is 0.0 (not applicable for garment type)
            if (userValue > 0.1) {
                val garmentMid = (range.min + range.max) / 2.0
                val diff = userValue - garmentMid
                val score = dimensionScores[BodyDimension.HIP]
                deltas[BodyDimension.HIP] = DimensionDelta(
                    dimension = BodyDimension.HIP,
                    userMeasurement = userValue,
                    garmentMidpoint = garmentMid,
                    differenceCm = diff,
                    withinRange = userValue >= range.min && userValue <= range.max,
                    rangeMin = range.min,
                    rangeMax = range.max,
                    fitStatus = score?.status ?: FitStatus.ACCEPTABLE
                )
            }
        }
        
        // Shoulder
        garmentSize.shoulder?.let { range ->
            val userValue = userBody.shoulder
            val garmentMid = (range.min + range.max) / 2.0
            val diff = userValue - garmentMid
            val score = dimensionScores[BodyDimension.SHOULDER]
            deltas[BodyDimension.SHOULDER] = DimensionDelta(
                dimension = BodyDimension.SHOULDER,
                userMeasurement = userValue,
                garmentMidpoint = garmentMid,
                differenceCm = diff,
                withinRange = userValue >= range.min && userValue <= range.max,
                rangeMin = range.min,
                rangeMax = range.max,
                fitStatus = score?.status ?: FitStatus.ACCEPTABLE
            )
        }
        
        // Inseam
        garmentSize.inseam?.let { range ->
            val userValue = userBody.inseam
            val garmentMid = (range.min + range.max) / 2.0
            val diff = userValue - garmentMid
            val score = dimensionScores[BodyDimension.INSEAM]
            deltas[BodyDimension.INSEAM] = DimensionDelta(
                dimension = BodyDimension.INSEAM,
                userMeasurement = userValue,
                garmentMidpoint = garmentMid,
                differenceCm = diff,
                withinRange = userValue >= range.min && userValue <= range.max,
                rangeMin = range.min,
                rangeMax = range.max,
                fitStatus = score?.status ?: FitStatus.ACCEPTABLE
            )
        }
        
        return deltas
    }
    
    /**
     * Calculate biomechanical deltas (overall fit characteristics)
     */
    private fun calculateBiomechanicalDeltas(
        dimensionDeltas: Map<BodyDimension, DimensionDelta>  // BUG 6 FIX: Use enum
    ): BiomechanicalDeltas {
        val chestDelta = dimensionDeltas[BodyDimension.CHEST]?.differenceCm
        val waistDelta = dimensionDeltas[BodyDimension.WAIST]?.differenceCm
        val hipDelta = dimensionDeltas[BodyDimension.HIP]?.differenceCm
        val shoulderDelta = dimensionDeltas[BodyDimension.SHOULDER]?.differenceCm
        val inseamDelta = dimensionDeltas[BodyDimension.INSEAM]?.differenceCm
        
        // Calculate overall tightness (weighted average of deltas)
        val deltas = listOfNotNull(chestDelta, waistDelta, hipDelta, shoulderDelta, inseamDelta)
        val overallTightness = if (deltas.isNotEmpty()) {
            deltas.average()
        } else {
            0.0
        }
        
        // Find critical mismatch (worst dimension)
        val criticalMismatch = dimensionDeltas.maxByOrNull { abs(it.value.differenceCm) }?.key  // Already BodyDimension enum
        
        return BiomechanicalDeltas(
            chestDelta = chestDelta,
            waistDelta = waistDelta,
            hipDelta = hipDelta,
            shoulderDelta = shoulderDelta,
            inseamDelta = inseamDelta,
            overallTightness = overallTightness,
            criticalMismatch = criticalMismatch
        )
    }
    
    /**
     * Identify risk factors
     */
    private fun identifyRiskFactors(
        dimensionDeltas: Map<BodyDimension, DimensionDelta>,  // BUG 6 FIX: Use enum
        userBody: EstimatedBodyMeasurements,
        fitScore: FitScore
    ): List<RiskFactor> {
        val risks = mutableListOf<RiskFactor>()
        
        // FIX #11: Aggregate small/large dimensions FIRST
        val tooSmallDimensions = mutableListOf<String>()
        val tooLargeDimensions = mutableListOf<String>()
        val criticalMismatches = mutableListOf<String>()
        
        dimensionDeltas.forEach { (dimension, delta) ->
            when {
                delta.differenceCm < -10.0 -> {
                    criticalMismatches.add("${dimension.displayName()} (${String.format("%.1f", abs(delta.differenceCm))}cm too small)")  // ✅ FIX #28
                }
                delta.differenceCm < -5.0 -> {
                    tooSmallDimensions.add(dimension.displayName())  // ✅ FIX #28
                }
                delta.differenceCm > 10.0 -> {
                    criticalMismatches.add("${dimension.displayName()} (${String.format("%.1f", delta.differenceCm)}cm too large)")  // ✅ FIX #28
                }
                delta.differenceCm > 5.0 -> {
                    tooLargeDimensions.add(dimension.displayName())  // ✅ FIX #28
                }
            }
        }
        
        // Add aggregated risks
        if (criticalMismatches.isNotEmpty()) {
            risks.add(RiskFactor(
                code = RiskCode.MULTIPLE_DIMENSIONS_MISMATCH,
                severity = RiskSeverity.CRITICAL,
                dimension = null,
                metric = criticalMismatches.size.toDouble(),
                message = "Severe mismatch in: ${criticalMismatches.joinToString(", ")}"
            ))
        }
        
        if (tooSmallDimensions.isNotEmpty()) {
            risks.add(RiskFactor(
                code = RiskCode.MEASUREMENT_TOO_SMALL,
                severity = if (tooSmallDimensions.size > 1) RiskSeverity.HIGH else RiskSeverity.MEDIUM,
                dimension = tooSmallDimensions.joinToString(", "),
                metric = null,
                message = "${tooSmallDimensions.joinToString(", ")} may be tight"
            ))
        }
        
        if (tooLargeDimensions.isNotEmpty()) {
            risks.add(RiskFactor(
                code = RiskCode.MEASUREMENT_TOO_LARGE,
                severity = if (tooLargeDimensions.size > 1) RiskSeverity.HIGH else RiskSeverity.MEDIUM,
                dimension = tooLargeDimensions.joinToString(", "),
                metric = null,
                message = "${tooLargeDimensions.joinToString(", ")} may be loose"
            ))
        }
        
        // Multiple dimensions mismatch (aggregated check)
        val mismatchedDimensions = dimensionDeltas.values.count { abs(it.differenceCm) > 3.0 }
        if (mismatchedDimensions >= 2 && criticalMismatches.isEmpty()) {
            risks.add(RiskFactor(
                code = RiskCode.MULTIPLE_DIMENSIONS_MISMATCH,
                severity = RiskSeverity.MEDIUM,
                dimension = null,
                metric = mismatchedDimensions.toDouble(),
                message = "$mismatchedDimensions dimensions have significant mismatch (>3cm)"
            ))
        }
        
        // Low confidence estimation
        if (userBody.confidence.overall < 0.7) {
            risks.add(RiskFactor(
                code = RiskCode.LOW_CONFIDENCE_ESTIMATION,
                severity = RiskSeverity.MEDIUM,
                dimension = null,
                metric = userBody.confidence.overall,
                message = "Body measurements have low confidence (${String.format("%.1f", userBody.confidence.overall * 100)}%)"
            ))
        }
        
        return risks
    }
    
    /**
     * Determine recommendation code from fit score and risks
     */
    private fun determineRecommendationCode(
        fitScore: FitScore,
        riskFactors: List<RiskFactor>,
        biomechanicalDeltas: BiomechanicalDeltas
    ): RecommendationCode {
        // Critical risks = not recommend
        if (riskFactors.any { it.severity == RiskSeverity.CRITICAL }) {
            return RecommendationCode.NOT_RECOMMEND
        }
        
        // High score + low risks = strong recommend
        if (fitScore.overall >= 0.90 && riskFactors.isEmpty()) {
            return RecommendationCode.STRONG_RECOMMEND
        }
        
        // Good score = recommend
        if (fitScore.overall >= 0.75) {
            return RecommendationCode.RECOMMEND
        }
        
        // Acceptable score with risks = conditional
        if (fitScore.overall >= 0.60) {
            return RecommendationCode.CONDITIONAL_RECOMMEND
        }
        
        // Low score = not recommend
        return RecommendationCode.NOT_RECOMMEND
    }
}

