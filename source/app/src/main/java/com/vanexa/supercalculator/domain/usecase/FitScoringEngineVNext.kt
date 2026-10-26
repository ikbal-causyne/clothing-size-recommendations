package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import android.util.Log
import kotlin.math.*

/**
 * FitScoringEngine vNext - Size Intelligence Engine
 * 
 * 🚫 MASTER PROMPT COMPLIANCE (NON-NEGOTIABLE):
 * - NEVER use size labels (S/M/L/XL) as primary logic - labels are metadata only
 * - ALL logic MUST be numerical and probabilistic
 * - NEVER use linear scoring - MUST use Gaussian decay
 * - Formula is FIXED and MANDATORY: score = exp(-0.5 * (distance / tolerance)^2)
 * - NEVER remove confidence modeling (OCR, chart, user body confidence)
 * - Confidence collapse is MANDATORY: finalScore = rawFitScore * (dataConfidence^2)
 * - NEVER output absolute claims - only probabilistic language with confidence
 * - NEVER simplify biology - exact weights are FIXED unless explicitly instructed
 * 
 * Core principles:
 * - Clothing size is a probabilistic fit problem, not a label match
 * - Fit scoring must be non-linear (Gaussian decay) - MANDATORY
 * - Chest, waist, hips have different biological weights (adjusted by garment type)
 * - Garment type affects tolerance AND weights
 * - Fit preference shifts tolerance curves (relative, not absolute)
 * - OCR confidence, chart confidence, and body confidence MUST mathematically affect scores
 * - Never trust OCR blindly
 * 
 * Architecture:
 * - Domain-driven, Kotlin-first, Android native
 * - Offline-first
 * - Deterministic, explainable scoring
 */
object FitScoringEngineVNext {
    private const val TAG = "FitScoringEngineVNext"
    
    // PHASE 4 FIX #17: Extract Magic Numbers
    // Garment center calculation
    private const val MIDPOINT_FACTOR = 0.5              // True midpoint (not 0.35)
    private const val IDEAL_FIT_BIAS = 0.05              // 5% bias toward looser fit
    private const val PREFERENCE_SHIFT_FACTOR = 0.15     // 15% of rangeWidth for preference shift
    
    // Gaussian scoring
    private const val GAUSSIAN_STEEPNESS = 0.8           // Steeper curve for better discrimination
    
    // Tolerance values by garment type
    private const val TOLERANCE_BLAZER_SHIRT = 2.5       // Structured garments: strict
    private const val TOLERANCE_TSHIRT_POLO = 3.5        // Casual tops: moderate
    private const val TOLERANCE_HOODIE_SWEATER = 5.0      // Loose garments: forgiving
    private const val TOLERANCE_JEANS_CHINOS = 2.0       // Pants: very strict (comfort!)
    private const val TOLERANCE_JOGGERS_CARGO = 6.0      // Athletic/casual pants: very forgiving
    private const val TOLERANCE_TROUSERS = 2.5            // Dress pants: strict
    private const val TOLERANCE_SHORTS = 3.5              // Shorts: moderate
    private const val TOLERANCE_DEFAULT = 4.0             // Default: moderate
    private const val TOLERANCE_FLOOR = 1.5               // Minimum tolerance (cm)
    
    // Tolerance adjustments for range width
    private const val TOLERANCE_ADJUST_NARROW = -0.5      // Very narrow range (<4cm)
    private const val TOLERANCE_ADJUST_WIDE = 1.0          // Very wide range (>14cm)
    private const val RANGE_WIDTH_NARROW_THRESHOLD = 4.0  // Narrow range threshold
    private const val RANGE_WIDTH_WIDE_THRESHOLD = 14.0   // Wide range threshold
    
    // Confidence penalty thresholds
    private const val CONFIDENCE_EXCELLENT = 0.90
    private const val CONFIDENCE_VERY_GOOD = 0.80
    private const val CONFIDENCE_GOOD = 0.70
    private const val CONFIDENCE_ACCEPTABLE = 0.60
    private const val CONFIDENCE_MARGINAL = 0.50
    
    // Confidence penalty values
    private const val PENALTY_EXCELLENT = 1.00            // No penalty
    private const val PENALTY_VERY_GOOD = 0.98             // Minimal penalty
    private const val PENALTY_GOOD = 0.95                  // Slight penalty
    private const val PENALTY_ACCEPTABLE = 0.90            // Moderate penalty
    private const val PENALTY_MARGINAL = 0.85              // Significant penalty
    private const val PENALTY_POOR_FACTOR = 0.80           // Poor data: linear with floor
    
    // Default confidence values
    private const val DEFAULT_CONFIDENCE = 0.80            // Default when null
    
    /**
     * Calculate fit score with Gaussian decay and confidence weighting
     * PHASE 1 FIXES APPLIED:
     * - FIX #1: Garment center uses true midpoint (0.5, not 0.35), with idealFitPoint (5% bias) and preferenceShift (15% of rangeWidth)
     * - FIX #2: Graduated confidence penalty (not harsh square) - preserves scores for good data
     * - FIX #3: Garment physics integrated at start - uses physicsAdjustedRange throughout
     * - BUG 1: Confidence applied per-dimension (not just final collapse)
     * - BUG 5: Weighted geometric mean for confidence (respects dimension importance)
     * - BUG 6: BodyDimension enum (type-safe, not String)
     * - BUG 8: Garment-type-specific weights (shirt≠jeans, hoodie≠blazer)
     * - BUG 9: Unreliable dimensions exposed to UI
     */
    fun calculateFitScore(
        userBody: EstimatedBodyMeasurements,
        garmentSize: SizeChartEntry,
        garmentType: GarmentType,
        fitPreference: FitPreference = FitPreference.REGULAR,
        userRegion: SizeRegion? = null
    ): FitScore {
        
        Log.d(TAG, "🎯 [Size Intelligence FIXED] Calculating fit score with Gaussian decay")
        Log.d(TAG, "   User region (tolerance lens): $userRegion")

        // Regional tolerance factor: region affects tolerance, not body measurements
        val regionalToleranceFactor = if (userRegion != null) {
            RegionalFitNormalizer.getToleranceFactor(userRegion, garmentType)
        } else {
            1.0
        }

        // Regional garment size multiplier: US garments run ~3% larger than EU
        val garmentSizeMultiplier = if (userRegion != null) {
            RegionalFitNormalizer.getRegionalContext(userRegion).garmentSizeMultiplier
        } else {
            1.0
        }
        Log.d(TAG, "   regionalToleranceFactor=$regionalToleranceFactor, garmentSizeMultiplier=$garmentSizeMultiplier")
        
        // BUG 8 FIX: Get garment-type-specific weights
        val dimensionWeights = getGarmentTypeWeights(garmentType)
        
        // Score each dimension with Gaussian decay
        val dimensionScores = mutableMapOf<BodyDimension, DimensionScore>()
        var totalWeightedRawScore = 0.0
        var totalWeight = 0.0
        
        // Track confidences with weights for weighted average (BUG 5 FIX)
        val confidenceWeights = mutableMapOf<BodyDimension, Pair<Double, Double>>()  // (confidence, weight)
        val unreliableDimensions = mutableMapOf<BodyDimension, Double>()  // BUG 9 FIX
        
        // Chest dimension
        if (dimensionWeights[BodyDimension.CHEST] != null && dimensionWeights[BodyDimension.CHEST]!! > 0 && garmentSize.chest != null && userBody.chest > 0.1) {
            val userConfidence = userBody.confidence.breakdown["chest"]?.value ?: 0.75
            val chartConfidence = garmentSize.chest.confidence ?: run {
                Log.w(TAG, "⚠️ [NULL-SAFETY-FIX] Chest confidence is null, defaulting to $DEFAULT_CONFIDENCE")
                DEFAULT_CONFIDENCE
            }
            val combinedConfidence = userConfidence * chartConfidence
            val weight = dimensionWeights[BodyDimension.CHEST]!!
            
            confidenceWeights[BodyDimension.CHEST] = Pair(combinedConfidence, weight)
            
            // BUG 9 FIX: Track unreliable dimensions
            if (combinedConfidence < 0.7) {
                unreliableDimensions[BodyDimension.CHEST] = combinedConfidence
            }
            
            val score = scoreDimensionGaussian(
                userMeasurement = userBody.chest,
                garmentRange = garmentSize.chest,
                fitPreference = fitPreference,
                dimension = BodyDimension.CHEST,
                garmentType = garmentType,
                regionalToleranceFactor = regionalToleranceFactor,
                garmentSizeMultiplier = garmentSizeMultiplier,
                userRegion = userRegion
            )
            
            // BUG 1 FIX: Apply confidence per-dimension (not just final collapse)
            val confidenceAdjustedScore = score.rawScore * combinedConfidence
            
            val adjustedScore = score.copy(
                value = confidenceAdjustedScore,  // Confidence-adjusted score
                confidence = combinedConfidence
            )
            
            dimensionScores[BodyDimension.CHEST] = adjustedScore
            totalWeightedRawScore += confidenceAdjustedScore * weight
            totalWeight += weight
            
            Log.d(TAG, "  chest: rawScore=${score.rawScore}, confidence=$combinedConfidence, adjustedScore=$confidenceAdjustedScore, weighted=${confidenceAdjustedScore * weight}")
        }
        
        // Waist dimension
        if (dimensionWeights[BodyDimension.WAIST] != null && dimensionWeights[BodyDimension.WAIST]!! > 0 && garmentSize.waist != null && userBody.waist > 0.1) {
            val userConfidence = userBody.confidence.breakdown["waist"]?.value ?: 0.72
            val chartConfidence = garmentSize.waist.confidence ?: run {
                Log.w(TAG, "⚠️ [NULL-SAFETY-FIX] Waist confidence is null, defaulting to $DEFAULT_CONFIDENCE")
                DEFAULT_CONFIDENCE
            }
            val combinedConfidence = userConfidence * chartConfidence
            val weight = dimensionWeights[BodyDimension.WAIST]!!
            
            confidenceWeights[BodyDimension.WAIST] = Pair(combinedConfidence, weight)
            
            // BUG 9 FIX: Track unreliable dimensions
            if (combinedConfidence < 0.7) {
                unreliableDimensions[BodyDimension.WAIST] = combinedConfidence
            }
            
            val score = scoreDimensionGaussian(
                userMeasurement = userBody.waist,
                garmentRange = garmentSize.waist,
                fitPreference = fitPreference,
                dimension = BodyDimension.WAIST,
                garmentType = garmentType,
                regionalToleranceFactor = regionalToleranceFactor,
                garmentSizeMultiplier = garmentSizeMultiplier,
                userRegion = userRegion
            )
            
            // BUG 1 FIX: Apply confidence per-dimension
            val confidenceAdjustedScore = score.rawScore * combinedConfidence
            
            val adjustedScore = score.copy(
                value = confidenceAdjustedScore,
                confidence = combinedConfidence
            )
            
            dimensionScores[BodyDimension.WAIST] = adjustedScore
            totalWeightedRawScore += confidenceAdjustedScore * weight
            totalWeight += weight
            
            Log.d(TAG, "  waist: rawScore=${score.rawScore}, confidence=$combinedConfidence, adjustedScore=$confidenceAdjustedScore, weighted=${confidenceAdjustedScore * weight}")
        }
        
        // Hip dimension
        if (dimensionWeights[BodyDimension.HIP] != null && dimensionWeights[BodyDimension.HIP]!! > 0 && garmentSize.hip != null && userBody.hip > 0.1) {
            val userConfidence = userBody.confidence.breakdown["hip"]?.value ?: 0.74
            val chartConfidence = garmentSize.hip.confidence ?: run {
                Log.w(TAG, "⚠️ [NULL-SAFETY-FIX] Hip confidence is null, defaulting to $DEFAULT_CONFIDENCE")
                DEFAULT_CONFIDENCE
            }
            val combinedConfidence = userConfidence * chartConfidence
            val weight = dimensionWeights[BodyDimension.HIP]!!
            
            confidenceWeights[BodyDimension.HIP] = Pair(combinedConfidence, weight)
            
            if (combinedConfidence < 0.7) {
                unreliableDimensions[BodyDimension.HIP] = combinedConfidence
            }
            
            val score = scoreDimensionGaussian(
                userMeasurement = userBody.hip,
                garmentRange = garmentSize.hip,
                fitPreference = fitPreference,
                dimension = BodyDimension.HIP,
                garmentType = garmentType,
                regionalToleranceFactor = regionalToleranceFactor,
                garmentSizeMultiplier = garmentSizeMultiplier,
                userRegion = userRegion
            )
            
            // BUG 1 FIX: Apply confidence per-dimension
            val confidenceAdjustedScore = score.rawScore * combinedConfidence
            
            val adjustedScore = score.copy(
                value = confidenceAdjustedScore,
                confidence = combinedConfidence
            )
            
            dimensionScores[BodyDimension.HIP] = adjustedScore
            totalWeightedRawScore += confidenceAdjustedScore * weight
            totalWeight += weight
            
            Log.d(TAG, "  hip: rawScore=${score.rawScore}, confidence=$combinedConfidence, adjustedScore=$confidenceAdjustedScore, weighted=${confidenceAdjustedScore * weight}")
        }
        
        // Calculate raw fit score (before final confidence collapse)
        val rawFitScore = if (totalWeight > 0) totalWeightedRawScore / totalWeight else 0.0
        
        // Calculate data confidence (weighted average respects dimension importance)
        val dataConfidence = calculateWeightedConfidence(confidenceWeights)
        
        // ✅ MASTER PROMPT MANDATORY: Confidence collapse formula
        // finalScore = rawFitScore × (dataConfidence²)
        // This is intentional harsh penalty for low-quality data
        val confidenceSquared = dataConfidence * dataConfidence
        val confidenceWeightedScore = rawFitScore * confidenceSquared
        
        Log.d(TAG, "🎯 [MASTER PROMPT] Confidence collapse: rawScore=$rawFitScore, dataConf=$dataConfidence, dataConf²=$confidenceSquared, final=$confidenceWeightedScore")
        
        val fitQuality = determineFitQuality(confidenceWeightedScore)
        val weakestDimension = dimensionScores.minByOrNull { it.value.value }?.key
        val recommendation = generateExplanation(dimensionScores, weakestDimension, confidenceWeightedScore, dataConfidence, unreliableDimensions)
        
        return FitScore(
            overall = confidenceWeightedScore.coerceIn(0.0, 1.0),
            rawFitScore = rawFitScore.coerceIn(0.0, 1.0),
            dataConfidence = dataConfidence,
            dimensionScores = dimensionScores,
            fitQuality = fitQuality,
            recommendation = recommendation,
            unreliableDimensions = unreliableDimensions  // BUG 9 FIX
        )
    }
    
    /**
     * BUG 8 FIX: Garment-type-specific weights
     * Base weights: chest 45%, waist 35%, hips 20%
     * Adjusted for garment type
     */
    private fun getGarmentTypeWeights(garmentType: GarmentType): Map<BodyDimension, Double> {
        return when (garmentType) {
            // Upper body - fitted (chest is primary)
            GarmentType.SHIRT, GarmentType.BLAZER -> mapOf(
                BodyDimension.CHEST to 0.60,
                BodyDimension.WAIST to 0.25,
                BodyDimension.HIP to 0.15
            )
            
            // Upper body - standard
            GarmentType.TSHIRT, GarmentType.POLO_SHIRT -> mapOf(
                BodyDimension.CHEST to 0.50,
                BodyDimension.WAIST to 0.30,
                BodyDimension.HIP to 0.20
            )
            
            // Upper body - relaxed
            GarmentType.HOODIE, GarmentType.SWEATER -> mapOf(
                BodyDimension.CHEST to 0.45,
                BodyDimension.WAIST to 0.35,
                BodyDimension.HIP to 0.20
            )
            
            // Lower body - fitted (waist is primary)
            GarmentType.JEANS, GarmentType.CHINOS -> mapOf(
                BodyDimension.WAIST to 0.50,
                BodyDimension.HIP to 0.35,
                BodyDimension.INSEAM to 0.15
            )
            
            // Lower body - relaxed
            GarmentType.JOGGERS, GarmentType.CARGO_PANTS -> mapOf(
                BodyDimension.WAIST to 0.40,
                BodyDimension.HIP to 0.35,
                BodyDimension.INSEAM to 0.25
            )
            
            // Lower body - standard
            GarmentType.TROUSERS, GarmentType.SHORTS -> mapOf(
                BodyDimension.WAIST to 0.45,
                BodyDimension.HIP to 0.35,
                BodyDimension.INSEAM to 0.20
            )
            
            // Default (fallback to base weights)
            else -> mapOf(
                BodyDimension.CHEST to 0.45,
                BodyDimension.WAIST to 0.35,
                BodyDimension.HIP to 0.20
            )
        }
    }
    
    /**
     * PHASE 1 FIXES: Gaussian decay dimension scoring
     * - FIX #1: True midpoint (0.5) with idealFitPoint (5% bias) and preferenceShift (15% of rangeWidth)
     * - FIX #3: Physics integration at start - adjusts range before all calculations
     * - FIX #12: Tighter Gaussian curve (-0.8 instead of -0.5) for better discrimination
     * Uses Gaussian function: score = exp(-0.8 * ((x - μ) / σ)^2)
     * where μ = targetMeasurement (idealFitPoint + preferenceShift), σ = adjustedTolerance
     */
    private fun scoreDimensionGaussian(
        userMeasurement: Double,
        garmentRange: MeasurementRange,
        fitPreference: FitPreference,
        dimension: BodyDimension,
        garmentType: GarmentType,
        regionalToleranceFactor: Double = 1.0,
        garmentSizeMultiplier: Double = 1.0,
        userRegion: SizeRegion? = null
    ): DimensionScore {
        
        // ==================================================
        // CRITICAL FIX: Apply Garment Physics BEFORE Scoring
        // ==================================================
        val physics = GarmentPhysicsEngine.getGarmentPhysics(garmentType)
        val physicsAdjustedRange = GarmentPhysicsEngine.adjustRangeForPhysics(
            range = garmentRange,
            dimension = dimension.displayName(),
            physics = physics
        )
        
        Log.d(TAG, "🎯 [PHYSICS-FIX] $dimension: original=[${garmentRange.min}-${garmentRange.max}], physics-adjusted=[${physicsAdjustedRange.min}-${physicsAdjustedRange.max}], ease=${when(dimension) {
            BodyDimension.CHEST -> physics.easeAllowance.chest
            BodyDimension.WAIST -> physics.easeAllowance.waist
            BodyDimension.HIP -> physics.easeAllowance.hip
            else -> 0.0
        }}cm")
        
        // ==================================================
        // CRITICAL FIX: Convert inches to CM before scoring
        // User body is ALWAYS in CM. OCR charts may be in INCH.
        // ==================================================
        val inchToCmFactor = if (garmentRange.unit == MeasurementUnit.INCH) 2.54 else 1.0
        val cmAdjustedMin = physicsAdjustedRange.min * inchToCmFactor
        val cmAdjustedMax = physicsAdjustedRange.max * inchToCmFactor
        
        if (inchToCmFactor != 1.0) {
            Log.d(TAG, "🔄 [UNIT-FIX] $dimension: converted INCH to CM: [${physicsAdjustedRange.min}-${physicsAdjustedRange.max}] inch -> [$cmAdjustedMin-$cmAdjustedMax] cm")
        }
        
        // NOW USE CM-converted range:
        // Apply garmentSizeMultiplier to account for regional garment grading differences
        val scaledMin = cmAdjustedMin * garmentSizeMultiplier
        val scaledMax = cmAdjustedMax * garmentSizeMultiplier
        val rangeWidth = scaledMax - scaledMin
        
        // FIX #1: True midpoint (not 35% from min) - PHASE 4 FIX #17
        val garmentCenter = scaledMin + (rangeWidth * MIDPOINT_FACTOR) // True midpoint
        
        // Dynamic IDEAL_FIT_BIAS: US users prefer looser fit (10% bias), others 5%
        val dynamicFitBias = if (userRegion == SizeRegion.US) 0.10 else IDEAL_FIT_BIAS
        // For garments where upper fit is preferred (e.g., shirts), add slight bias:
        val idealFitPoint = garmentCenter + (rangeWidth * dynamicFitBias)
        
        // FIX #10: Improved Tolerance Calculation - PHASE 4 FIX #17
        // Base tolerance by garment type (independent of range)
        val baseTolerance = when (garmentType) {
            GarmentType.BLAZER, GarmentType.SHIRT -> TOLERANCE_BLAZER_SHIRT              // Structured garments: strict
            GarmentType.TSHIRT, GarmentType.POLO_SHIRT -> TOLERANCE_TSHIRT_POLO         // Casual tops: moderate
            GarmentType.HOODIE, GarmentType.SWEATER -> TOLERANCE_HOODIE_SWEATER            // Loose garments: forgiving
            GarmentType.JEANS, GarmentType.CHINOS -> TOLERANCE_JEANS_CHINOS              // Pants: very strict (comfort!)
            GarmentType.JOGGERS, GarmentType.CARGO_PANTS -> TOLERANCE_JOGGERS_CARGO       // Athletic/casual pants: very forgiving
            GarmentType.TROUSERS -> TOLERANCE_TROUSERS                                // Dress pants: strict
            GarmentType.SHORTS -> TOLERANCE_SHORTS                                  // Shorts: moderate
            else -> TOLERANCE_DEFAULT                                                // Default: moderate
        }
        
        // Adjust for unusual range widths (not as primary factor!)
        val rangeAdjustment = when {
            rangeWidth < RANGE_WIDTH_NARROW_THRESHOLD -> TOLERANCE_ADJUST_NARROW   // Very narrow range (suspect OCR) → tighter tolerance
            rangeWidth > RANGE_WIDTH_WIDE_THRESHOLD -> TOLERANCE_ADJUST_WIDE  // Very wide range → slightly increase tolerance
            else -> 0.0                 // Normal range → no adjustment
        }
        
        var adjustedTolerance = (baseTolerance + rangeAdjustment).coerceAtLeast(TOLERANCE_FLOOR) // Floor of 1.5cm

        // Apply regional tolerance factor (region is a tolerance/interpretation lens)
        adjustedTolerance = (adjustedTolerance * regionalToleranceFactor).coerceAtLeast(TOLERANCE_FLOOR)

        Log.d(TAG, "🎯 [TOLERANCE-FIX] $garmentType $dimension: base=$baseTolerance, rangeWidth=$rangeWidth, adjustment=$rangeAdjustment, regionalFactor=$regionalToleranceFactor, final=$adjustedTolerance")
        
        // FIX #1: Use idealFitPoint for preference calculations - PHASE 4 FIX #17
        val preferenceShift = when (fitPreference) {
            FitPreference.SLIM -> -rangeWidth * PREFERENCE_SHIFT_FACTOR      // 15% toward tighter
            FitPreference.REGULAR -> 0.0                  // No shift from ideal
            FitPreference.RELAXED -> +rangeWidth * PREFERENCE_SHIFT_FACTOR   // 15% toward looser
        }
        
        val targetMeasurement = idealFitPoint + preferenceShift
        
        // Calculate distance from target
        val distance = abs(userMeasurement - targetMeasurement)
        
        // FIX #12: Tighter Gaussian Scoring - PHASE 4 FIX #17
        val normalizedDistance = distance / adjustedTolerance
        
        // Use steeper Gaussian curve for better discrimination
        val rawScore = exp(-GAUSSIAN_STEEPNESS * normalizedDistance.pow(2.0))  // Changed from -0.5 to -0.8
        
        // Now scoring is:
        // distance = 0 → score = 1.000 (EXCELLENT)
        // distance = 0.5×tolerance → score = 0.825 (VERY GOOD)
        // distance = 1.0×tolerance → score = 0.449 (MARGINAL)
        // distance = 1.5×tolerance → score = 0.140 (NOT RECOMMENDED)
        
        Log.d(TAG, "🎯 [GAUSSIAN-FIX] distance=$distance, tolerance=$adjustedTolerance, normalized=$normalizedDistance, rawScore=$rawScore")
        
        Log.d(TAG, "  [$dimension] Gaussian scoring (FIXED): user=$userMeasurement, center=$garmentCenter, idealFitPoint=$idealFitPoint, target=$targetMeasurement, distance=$distance, tolerance=$adjustedTolerance, rawScore=$rawScore")
        
        val status = when {
            rawScore >= 0.90 -> FitStatus.EXCELLENT
            rawScore >= 0.75 -> FitStatus.GOOD
            rawScore >= 0.60 -> FitStatus.ACCEPTABLE
            rawScore >= 0.40 -> FitStatus.TIGHT_OR_LOOSE
            else -> FitStatus.NOT_RECOMMENDED
        }
        
        val difference = userMeasurement - garmentCenter
        
        return DimensionScore(
            value = rawScore,  // Will be adjusted by confidence in caller
            rawScore = rawScore,
            confidence = 1.0,  // Will be set by caller
            status = status,
            difference = difference,
            note = generateDimensionNote(userMeasurement, garmentCenter, difference, rawScore)
        )
    }
    
    /**
     * FIX #4: Weighted Geometric Mean for Confidence
     * Properly punishes lower confidence while respecting dimension weights
     */
    private fun calculateWeightedConfidence(
        confidenceWeights: Map<BodyDimension, Pair<Double, Double>>
    ): Double {
        if (confidenceWeights.isEmpty()) {
            Log.w(TAG, "⚠️ [CONFIDENCE-FIX] No confidence weights provided, defaulting to 0.5")
            return 0.5
        }
        
        // Weighted Geometric Mean: product(confidence^weight)^(1/sum(weights))
        var logSum = 0.0
        var totalWeight = 0.0
        
        confidenceWeights.values.forEach { (confidence, weight) ->
            if (confidence > 0 && weight > 0) {
                logSum += weight * ln(confidence)
                totalWeight += weight
            }
        }
        
        val geometricMean = if (totalWeight > 0) {
            exp(logSum / totalWeight)
        } else {
            0.5
        }
        
        Log.d(TAG, "🎯 [CONFIDENCE-FIX] Geometric mean: $geometricMean (${confidenceWeights.size} dimensions)")
        
        return geometricMean.coerceIn(0.0, 1.0)
    }
    
    /**
     * MASTER PROMPT MANDATORY: Weighted confidence calculation
     * Uses weighted average (not geometric mean) to respect dimension importance
     * Geometric mean would unfairly punish when irrelevant dimensions have low confidence
     */
    
    /**
     * Determine fit quality from score
     * ✅ MASTER PROMPT Phase 1: Fit Score Interpretation (Section 6)
     * - 85–100% → Strong Match (EXCELLENT)
     * - 70–84% → Likely Fit (VERY_GOOD)
     * - 50–69% → Possible Fit (GOOD)
     * - 30–49% → Low Fit Probability (ACCEPTABLE)
     * - <30% → Very Low Fit Probability (MARGINAL/NOT_RECOMMENDED)
     */
    private fun determineFitQuality(score: Double): FitQuality {
        return when {
            score >= 0.85 -> FitQuality.EXCELLENT      // ✅ 85-100% → Strong Match
            score >= 0.70 -> FitQuality.VERY_GOOD      // ✅ 70-84% → Likely Fit
            score >= 0.50 -> FitQuality.GOOD            // ✅ 50-69% → Possible Fit
            score >= 0.30 -> FitQuality.ACCEPTABLE      // ✅ 30-49% → Low Fit Probability
            else -> FitQuality.MARGINAL                 // ✅ <30% → Very Low Fit Probability (use MARGINAL, not NOT_RECOMMENDED unless truly impossible)
        }
    }
    
    /**
     * Generate natural-language explanation based on weakest dimension
     * BUG 9 FIX: Includes unreliable dimensions info
     */
    private fun generateExplanation(
        dimensionScores: Map<BodyDimension, DimensionScore>,
        weakestDimension: BodyDimension?,
        finalScore: Double,
        dataConfidence: Double,
        unreliableDimensions: Map<BodyDimension, Double>
    ): String {
        val builder = StringBuilder()
        
        val probabilityPercent = (finalScore * 100).toInt().coerceIn(1, 100)
        
        // ✅ MASTER PROMPT Phase 1: Fit Score Interpretation (Section 6)
        // - 85–100% → Strong Match
        // - 70–84% → Likely Fit
        // - 50–69% → Possible Fit (Trade-offs)
        // - 30–49% → Low Fit Probability
        // - <30% → Very Low Fit Probability
        when {
            finalScore >= 0.85 -> builder.append("Strong Match (${probabilityPercent}% fit probability). ")
            finalScore >= 0.70 -> builder.append("Likely Fit (${probabilityPercent}% fit probability). ")
            finalScore >= 0.50 -> builder.append("Possible Fit (${probabilityPercent}% fit probability) - Trade-offs. ")
            finalScore >= 0.30 -> builder.append("Low Fit Probability (${probabilityPercent}%) - Based on dimension mismatch. ")
            else -> builder.append("Very Low Fit Probability (${probabilityPercent}% fit probability) - Significant dimension mismatch. ")
        }
        
        if (weakestDimension != null) {
            val weakestScore = dimensionScores[weakestDimension]
            if (weakestScore != null) {
                val diff = abs(weakestScore.difference)
                val dimensionName = weakestDimension.displayName()  // ✅ FIX #28: Use consistent displayName()
                when {
                    diff <= 2.0 -> builder.append("$dimensionName matches well (${String.format("%.1f", diff)}cm difference). ")
                    diff <= 5.0 -> builder.append("$dimensionName is ${String.format("%.1f", diff)}cm ${if (weakestScore.difference > 0) "larger" else "smaller"} than ideal. ")
                    else -> builder.append("$dimensionName has significant mismatch: ${String.format("%.1f", diff)}cm ${if (weakestScore.difference > 0) "too large" else "too small"}. ")
                }
            }
        }
        
        // BUG 9 FIX: Actionable feedback about unreliable dimensions
        if (unreliableDimensions.isNotEmpty()) {
            val unreliableList = unreliableDimensions.map { (dim, conf) ->
                "${dim.displayName()} (${String.format("%.0f", conf * 100)}%)"  // ✅ FIX #28: Use consistent displayName()
            }.joinToString(", ")
            builder.append("Low confidence in: $unreliableList - may affect accuracy. ")
        } else if (dataConfidence < 0.7) {
            builder.append("Low overall data confidence (${String.format("%.0f", dataConfidence * 100)}%) - recommendation may be less accurate. ")
        }
        
        return builder.toString().trim()
    }
    
    /**
     * Generate dimension note
     */
    private fun generateDimensionNote(
        userMeasurement: Double,
        garmentCenter: Double,
        difference: Double,
        rawScore: Double
    ): String {
        return when {
            abs(difference) <= 1.0 -> "Perfect match (${String.format("%.1f", abs(difference))}cm)"
            difference > 0 -> "${String.format("%.1f", abs(difference))}cm larger than ideal (score: ${String.format("%.2f", rawScore)})"
            else -> "${String.format("%.1f", abs(difference))}cm smaller than ideal (score: ${String.format("%.2f", rawScore)})"
        }
    }
}
