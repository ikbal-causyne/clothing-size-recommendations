package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import kotlin.math.abs
import kotlin.math.max

/**
 * Size Recommendation Engine - PRD Section 7.3
 * Analyzes all available sizes and returns ranked recommendations
 */
object SizeRecommendationEngine {
    
    /**
     * Enhanced Probabilistic Recommendation Engine per Engine Vision Update
     * Skeptical decision system with pre-scoring validation, dynamic thresholds, and structured explanations
     * 
     * ✅ ENGINE VISION FIX: Region now affects SIZE CHART INTERPRETATION, not BODY ESTIMATION
     * - User body is GLOBAL (same everywhere)
     * - Region affects: tolerance factors, between-size guidance, UX messaging
     * - Size charts are returned UNCHANGED
     */
    fun recommendSizes(
        userBody: EstimatedBodyMeasurements,
        sizeChart: List<SizeChartEntry>,
        garmentType: GarmentType,
        fitPreference: FitPreference = FitPreference.REGULAR,
        userRegion: SizeRegion? = null  // ✅ ENGINE VISION: Regional awareness
    ): SizeRecommendationResult {
        
        android.util.Log.d("SizeRecommendationEngine", "✅ [ENGINE VISION FIX] Starting recommendation")
        android.util.Log.d("SizeRecommendationEngine", "  User body: GLOBAL (region-independent)")
        android.util.Log.d("SizeRecommendationEngine", "  User region: $userRegion (affects tolerance, not body)")
        
        // ✅ ENGINE VISION FIX: Size chart stays UNCHANGED
        // Region only affects: tolerance factors in FitScoringEngine, between-size guidance, UX
        val interpretedSizeChart = if (userRegion != null) {
            android.util.Log.d("SizeRecommendationEngine", "🌍 [ENGINE VISION FIX] Getting regional size chart for: $userRegion (unchanged)")
            RegionalFitNormalizer.getRegionalSizeChart(sizeChart, userRegion)
        } else {
            android.util.Log.w("SizeRecommendationEngine", "⚠️ No user region provided - using default (EU) tolerance")
            sizeChart
        }
        
        // STEP 1: PRE-SCORING VALIDATION
        val validation = PreScoringValidator.validate(userBody, interpretedSizeChart, garmentType)
        if (!validation.isValid) {
            android.util.Log.e("SizeRecommendationEngine", "❌ Validation failed: ${validation.issues.size} critical issues")
            throw IllegalArgumentException("Pre-scoring validation failed: ${validation.issues.joinToString(", ") { it.message }}")
        }
        
        android.util.Log.d("SizeRecommendationEngine", "✅ Validation passed: confidence=${validation.confidence}, issues=${validation.issues.size}")
        
        // STEP 2: Get dynamic thresholds per Engine Vision
        val dynamicThresholds = DynamicThresholdCalculator.calculateThresholds(garmentType)
        
        // STEP 3: Score all sizes with Gaussian decay and confidence weighting (vNext)
        // ✅ ENGINE VISION FIX: Use size chart UNCHANGED
        val scoredSizes = interpretedSizeChart.map { size ->
            ScoredSize(
                sizeEntry = size,
                fitScore = FitScoringEngineVNext.calculateFitScore(
                    userBody, size, garmentType, fitPreference, userRegion = userRegion
                )
            )
        }.sortedByDescending { it.fitScore.overall }
        
        // Validate we have at least one scored size
        if (scoredSizes.isEmpty()) {
            throw IllegalArgumentException("Size chart is empty or no valid sizes found")
        }
        
        // STEP 4: Select best match
        val bestMatch = scoredSizes.first()
        
        // STEP 5: Find alternatives
        // ✅ MASTER PROMPT Phase 2: Alternatives ALWAYS shown if score gap < 0.2 (Section 5)
        // Use max of 0.2 (master prompt requirement) or dynamic threshold (better UX)
        val alternativeThreshold = max(0.2, dynamicThresholds.alternativeTolerance)
        val alternatives = scoredSizes.drop(1)
            .filter { 
                val scoreGap = bestMatch.fitScore.overall - it.fitScore.overall
                scoreGap < alternativeThreshold
            }
            .take(2)
        
        android.util.Log.d("SizeRecommendationEngine", "🎯 [MASTER PROMPT Phase 2] Alternative threshold: $alternativeThreshold (master prompt: 0.2, dynamic: ${dynamicThresholds.alternativeTolerance})")
        
        // STEP 6: Analyze if user is between sizes (using dynamic threshold)
        val betweenSizesAnalysis = analyzeBetweenSizes(scoredSizes, garmentType, dynamicThresholds)
        
        // STEP 7: Generate structured explanation (domain logic, not UX copy)
        val explanation = ExplanationEngine.generateExplanation(
            bestMatch.fitScore, userBody, bestMatch.sizeEntry, garmentType
        )
        
        android.util.Log.d("SizeRecommendationEngine", "🎯 [Engine Vision Update] Explanation: recommendationCode=${explanation.recommendationCode}, fitScore=${explanation.fitScore}")
        explanation.dimensionDeltas.forEach { (dimension, delta) ->
            android.util.Log.d("SizeRecommendationEngine", "  ${dimension.name}: diff=${delta.differenceCm}cm, withinRange=${delta.withinRange}")
        }
        
        // STEP 8: Calculate weighted confidence (using vNext weights: chest 45%, waist 35%, hips 20%)
        val dimensionWeights = DimensionWeights(chest = 0.45, waist = 0.35, hip = 0.20)
        val weightedConfidence = ConfidenceVectorCalculator.calculateWeightedConfidence(
            userBody, bestMatch.sizeEntry, garmentType, dimensionWeights
        )
        
        // ✅ MASTER PROMPT Phase 3: Single-size answers when confidence < 0.8 (Section 5)
        // If confidence is low, we should still show alternatives but mark them as less confident
        val finalAlternatives = if (weightedConfidence.overall < 0.8) {
            // Low confidence: ensure we show alternatives even if score gap is larger
            val expandedAlternatives = scoredSizes.drop(1)
                .filter { 
                    val scoreGap = bestMatch.fitScore.overall - it.fitScore.overall
                    scoreGap < 0.3  // More lenient when confidence is low
                }
                .take(3)  // Show more alternatives when confidence is low
            android.util.Log.d("SizeRecommendationEngine", "⚠️ [MASTER PROMPT Phase 3] Low confidence (${weightedConfidence.overall}), showing ${expandedAlternatives.size} alternatives with expanded threshold")
            expandedAlternatives
        } else {
            alternatives
        }
        
        // STEP 9: Generate warnings (structured, not marketing copy)
        val warnings = collectWarnings(scoredSizes, userBody, explanation)
        
        return SizeRecommendationResult(
            primaryRecommendation = bestMatch,
            alternativeRecommendations = finalAlternatives,
            betweenSizesInfo = betweenSizesAnalysis,
            overallConfidence = weightedConfidence.overall
        )
    }
    
    /**
     * Natural Language Reason Generation per Engine Vision Update
     * Uses structured explanation with numeric differences (cm-based)
     * This is domain logic - UI layer will format with emojis/language
     */
    private fun generateFitReason(
        fitScore: FitScore,
        userBody: EstimatedBodyMeasurements,
        explanation: ExplanationEngine.FitExplanation
    ): String {
        val score = fitScore.overall
        val fitQuality = fitScore.fitQuality
        val deltas = explanation.dimensionDeltas
        
        android.util.Log.d("SizeRecommendationEngine", "🎯 [Engine Vision Update] Generating reason: score=$score, fitQuality=$fitQuality")
        
        // Build reason with numeric differences (cm-based)
        val builder = StringBuilder()
        
        when (explanation.recommendationCode) {
            ExplanationEngine.RecommendationCode.STRONG_RECOMMEND -> {
                builder.append("Excellent fit. ")
                val bestDimension = deltas.maxByOrNull { it.value.fitStatus.ordinal }?.key
                if (bestDimension != null) {
                    val delta = deltas[bestDimension]!!
                    builder.append("${bestDimension.displayName()} matches within ${String.format("%.1f", abs(delta.differenceCm))}cm. ")  // ✅ FIX #28
                }
            }
            ExplanationEngine.RecommendationCode.RECOMMEND -> {
                builder.append("Good fit. ")
                val worstDimension = deltas.minByOrNull { it.value.fitStatus.ordinal }?.key
                if (worstDimension != null) {
                    val delta = deltas[worstDimension]!!
                    if (abs(delta.differenceCm) > 2.0) {
                        builder.append("${worstDimension.displayName()} is ${String.format("%.1f", abs(delta.differenceCm))}cm ${if (delta.differenceCm > 0) "larger" else "smaller"} than ideal. ")  // ✅ FIX #28
                    }
                }
            }
            ExplanationEngine.RecommendationCode.CONDITIONAL_RECOMMEND -> {
                builder.append("Acceptable fit with concerns. ")
                val mismatches = deltas.filter { abs(it.value.differenceCm) > 3.0 }
                if (mismatches.isNotEmpty()) {
                    val mismatchList = mismatches.map { "${it.key.displayName()} (${String.format("%.1f", abs(it.value.differenceCm))}cm ${if (it.value.differenceCm > 0) "larger" else "smaller"})" }  // ✅ FIX #28
                    builder.append("Mismatches: ${mismatchList.joinToString(", ")}. ")
                }
            }
            ExplanationEngine.RecommendationCode.NOT_RECOMMEND -> {
                // ✅ MASTER PROMPT: Amazon psychology - probability framing, not rejection
                val fitProbability = (fitScore.overall * 100).toInt().coerceIn(1, 100)
                builder.append("Low Fit Probability ($fitProbability%). ")
                val criticalMismatch = explanation.biomechanicalDeltas.criticalMismatch
                if (criticalMismatch != null) {
                    val delta = deltas[criticalMismatch]!!
                    builder.append("Risk: ${criticalMismatch.displayName()} mismatch (${String.format("%.1f", abs(delta.differenceCm))}cm ${if (delta.differenceCm > 0) "larger" else "smaller"}). ")  // ✅ FIX #28
                }
            }
            ExplanationEngine.RecommendationCode.BETWEEN_SIZES -> {
                builder.append("Between sizes. ")
                deltas.forEach { (dimension, delta) ->
                    if (abs(delta.differenceCm) > 2.0) {
                        builder.append("${dimension.displayName()}: ${String.format("%.1f", delta.differenceCm)}cm ${if (delta.differenceCm > 0) "larger" else "smaller"}. ")  // ✅ FIX #28
                    }
                }
            }
        }
        
        return builder.toString().trim()
    }
    
    /**
     * Warning System per Engine Vision Update
     * Returns structured warnings based on explanation and validation
     */
    private fun collectWarnings(
        scoredSizes: List<ScoredSize>,
        userBody: EstimatedBodyMeasurements,
        explanation: ExplanationEngine.FitExplanation
    ): List<String> {
        val warnings = mutableListOf<String>()
        
        // Convert structured risk factors to warnings
        explanation.riskFactors.forEach { risk ->
            when (risk.severity) {
                ExplanationEngine.RiskSeverity.CRITICAL -> {
                    warnings.add("⚠️ CRITICAL: ${risk.message}")
                }
                ExplanationEngine.RiskSeverity.HIGH -> {
                    warnings.add("⚠️ ${risk.message}")
                }
                ExplanationEngine.RiskSeverity.MEDIUM -> {
                    warnings.add("💡 ${risk.message}")
                }
                ExplanationEngine.RiskSeverity.LOW -> {
                    warnings.add("ℹ️ ${risk.message}")
                }
            }
        }
        
        // Low profile confidence
        if (userBody.confidence.overall < 0.7) {
            warnings.add(
                "⚠️ Limited measurements provided. Recommendations based on estimated body profile. Consider adding more measurements for accuracy."
            )
        }
        
        // Poor chart quality - check data confidence
        val avgDataConfidence = scoredSizes.map { it.fitScore.dataConfidence }.average()
        if (avgDataConfidence < 0.7) {
            warnings.add(
                "⚠️ Size chart data quality may be low (confidence: ${String.format("%.0f", avgDataConfidence * 100)}%). OCR may have misread some values. Double-check with product page."
            )
        }
        
        // No clear winner (score difference < 0.05)
        if (scoredSizes.size >= 2) {
            val topScore = scoredSizes[0].fitScore.overall
            val secondScore = scoredSizes[1].fitScore.overall
            if (topScore - secondScore < 0.05) {
                warnings.add(
                    "💡 Two sizes are very close (${scoredSizes[0].sizeEntry.sizeLabel} vs ${scoredSizes[1].sizeEntry.sizeLabel}). Your fit preference influenced this choice."
                )
            }
        }
        
        // Size between standards
        val userChest = userBody.chest
        if (userChest > 0 && userChest < 70) {
            warnings.add(
                "📏 Your measurements are very small for standard sizing. Consider petite or children's sizes."
            )
        }
        if (userChest > 130) {
            warnings.add(
                "📏 Your measurements are large for standard sizing. Consider plus-size or extended range options."
            )
        }
        
        return warnings
    }
    
    private fun analyzeBetweenSizes(
        scoredSizes: List<ScoredSize>,
        garmentType: GarmentType,
        dynamicThresholds: DynamicThresholdCalculator.DynamicThresholds
    ): BetweenSizesInfo? {
        
        if (scoredSizes.size < 2) return null
        
        val top1 = scoredSizes[0]
        val top2 = scoredSizes[1]
        
        // FIX #8: Use dynamic threshold instead of hardcoded 0.05
        if (abs(top1.fitScore.overall - top2.fitScore.overall) <= dynamicThresholds.betweenSizesTolerance) {
            android.util.Log.d("SizeRecommendationEngine", "🎯 [BETWEEN-SIZES-FIX] Close scores detected (${top1.fitScore.overall} vs ${top2.fitScore.overall}), threshold=${dynamicThresholds.betweenSizesTolerance}")
            return BetweenSizesInfo(
                size1 = top1.sizeEntry.sizeLabel,
                size2 = top2.sizeEntry.sizeLabel,
                guidance = generateBetweenSizesGuidance(top1, top2)
            )
        }
        
        return null
    }
    
    /**
     * Enhanced Between Sizes Guidance per Engine Vision Module 4.2
     * Natural language reason generation
     */
    private fun generateBetweenSizesGuidance(
        size1: ScoredSize,
        size2: ScoredSize
    ): String {
        val builder = StringBuilder()
        builder.append("You're between sizes ${size1.sizeEntry.sizeLabel} and ${size2.sizeEntry.sizeLabel}. ")
        
        // Analyze which dimensions favor which size
        val size1Better = mutableListOf<String>()
        val size2Better = mutableListOf<String>()
        
        size1.fitScore.dimensionScores.forEach { (dimension, score1) ->
            val score2 = size2.fitScore.dimensionScores[dimension]
            if (score2 != null) {
                if (score1.value > score2.value + 0.05) {
                    size1Better.add(dimension.displayName())  // ✅ FIX #28: Use consistent displayName()
                } else if (score2.value > score1.value + 0.05) {
                    size2Better.add(dimension.displayName())  // ✅ FIX #28: Use consistent displayName()
                }
            }
        }
        
        // Generate natural language guidance per Engine Vision
        if (size1Better.isNotEmpty()) {
            builder.append("Choose ${size1.sizeEntry.sizeLabel} for better fit in ${size1Better.joinToString(", ")}. ")
        }
        if (size2Better.isNotEmpty()) {
            builder.append("Choose ${size2.sizeEntry.sizeLabel} for better fit in ${size2Better.joinToString(", ")}.")
        }
        
        // Add preference-based guidance
        val scoreDiff = size1.fitScore.overall - size2.fitScore.overall
        if (abs(scoreDiff) < 0.05) {
            builder.append(" Both sizes are very close - your fit preference influenced this recommendation.")
        }
        
        return builder.toString()
    }
    
    private fun calculateRecommendationConfidence(
        bestMatch: ScoredSize,
        alternatives: List<ScoredSize>,
        bodyConfidence: ConfidenceScore
    ): Double {
        // Base confidence from body estimation
        var confidence = bodyConfidence.overall
        
        // Boost if best match score is high
        confidence += bestMatch.fitScore.overall * 0.2
        
        // Reduce if alternatives are very close
        if (alternatives.isNotEmpty()) {
            val firstAlternative = alternatives.firstOrNull()
            if (firstAlternative != null) {
                val scoreDiff = bestMatch.fitScore.overall - firstAlternative.fitScore.overall
                if (scoreDiff < 0.1) {
                    confidence -= 0.1
                }
            }
        }
        
        return confidence.coerceIn(0.0, 1.0)
    }
}

