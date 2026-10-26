package com.vanexa.supercalculator.domain.usecase

import android.util.Log
import com.vanexa.supercalculator.domain.model.*
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.exp

/**
 * Advanced Size Matcher with Multiple Mathematical Approaches
 * - Statistical Matching with Z-Scores
 * - Fuzzy Logic Matching
 * - Bayesian Inference
 * - K-Nearest Neighbor
 * - Multi-Criteria Decision Analysis
 * Target Accuracy: >85%
 */
class EnhancedSizeMatcher(
    private val dimensionPredictor: BodyDimensionPredictor
) {
    companion object {
        private const val TAG = "EnhancedSizeMatcher"
    }
    
    /**
     * Match size with multiple advanced strategies
     */
    fun matchSize(
        userHeight: Double,
        userWeight: Double,
        userChest: Double?,
        userWaist: Double?,
        userHips: Double?,
        sizeChart: SizeChart,
        region: SizeRegion,
        gender: Gender,
        clothingType: SizeClothingType
    ): EnhancedSizeRecommendation {
        Log.e(TAG, "🔵🔵🔵 matchSize CALLED with NEW INPUTS 🔵🔵🔵")
        Log.e(TAG, "🔵 Height: $userHeight, Weight: $userWeight, Region: $region, Gender: $gender, Clothing: $clothingType")
        Log.e(TAG, "🔵 Chest: $userChest, Waist: $userWaist, Hips: $userHips")
        
        // Validate size chart has valid data
        val validSizes = sizeChart.sizes.filter { it.hasValidData }
        Log.d(TAG, "Valid sizes: ${validSizes.size} out of ${sizeChart.sizes.size}")
        
        // ✅ VALIDATION 1: Check if validSizes is empty
        if (validSizes.isEmpty()) {
            Log.w(TAG, "No valid size data in chart, using fallback")
            return createFallbackRecommendation(userHeight, userWeight, sizeChart, region, gender)
        }
        
        // ✅ VALIDATION 2: Check if all sizes have identical data
        val allSizesHaveSameData = validSizes.size > 1 && run {
            val firstSize = validSizes.first()
            validSizes.all { size ->
                size.chestCm == firstSize.chestCm &&
                size.waistCm == firstSize.waistCm &&
                size.hipsCm == firstSize.hipsCm &&
                size.heightCmMin == firstSize.heightCmMin &&
                size.weightKgRange == firstSize.weightKgRange
            }
        }
        if (allSizesHaveSameData) {
            Log.e(TAG, "❌ CRITICAL: All sizes have IDENTICAL data! This will cause identical scores.")
            Log.e(TAG, "Size chart data: ${validSizes.map { 
                "${it.size}: chest=${it.chestCm}, waist=${it.waistCm}, hips=${it.hipsCm}" 
            }.joinToString(" | ")}")
            Log.e(TAG, "This indicates size chart parsing failed or data is invalid.")
            return createFallbackRecommendation(userHeight, userWeight, sizeChart, region, gender)
        }
        
        // ✅ VALIDATION 3: Check if size chart has meaningful variation
        val chestValues = validSizes.mapNotNull { it.chestCm?.let { r -> (r.first + r.last) / 2.0 } }
        val waistValues = validSizes.mapNotNull { it.waistCm?.let { r -> (r.first + r.last) / 2.0 } }
        val hipsValues = validSizes.mapNotNull { it.hipsCm?.let { r -> (r.first + r.last) / 2.0 } }
        
        val hasVariation = (chestValues.distinct().size > 1) || 
                           (waistValues.distinct().size > 1) || 
                           (hipsValues.distinct().size > 1)
        
        if (!hasVariation && validSizes.size > 1) {
            Log.w(TAG, "⚠️ WARNING: Size chart has no meaningful variation in dimensions.")
            Log.w(TAG, "Chest values: $chestValues, Waist: $waistValues, Hips: $hipsValues")
            // Continue but log warning - may still work if other factors differ
        }
        
        // Step 1: Predict dimensions if no manual input
        val predicted = dimensionPredictor.predictAllDimensions(
            heightCm = userHeight,
            weightKg = userWeight,
            region = region,
            gender = gender
        )
        
        Log.d(TAG, "Predicted dimensions - Chest: ${predicted.chest.value}, Waist: ${predicted.waist.value}, Hips: ${predicted.hips.value}")
        
        // ✅ BUG #5: PREDICTED DIMENSIONS VERIFICATION
        Log.d(TAG, "=== PREDICTED DIMENSIONS DEBUG ===")
        Log.d(TAG, "Input - Height: $userHeight, Weight: $userWeight, Region: $region, Gender: $gender")
        Log.d(TAG, "Predicted - Chest: ${predicted.chest.value}, Waist: ${predicted.waist.value}, Hips: ${predicted.hips.value}")
        Log.d(TAG, "Prediction Confidence: ${predicted.overallConfidence}")
        
        // Verify predictions are reasonable and different
        if (predicted.chest.value == predicted.waist.value && predicted.chest.value == predicted.hips.value) {
            Log.e(TAG, "❌ CRITICAL: All predicted dimensions are IDENTICAL! This is wrong.")
        }
        
        // Step 2: Use manual input if available, otherwise use prediction
        val chest = userChest ?: predicted.chest.value
        val waist = userWaist ?: predicted.waist.value
        val hips = userHips ?: predicted.hips.value
        
        val hasManualInput = userChest != null || userWaist != null || userHips != null
        Log.d(TAG, "Using dimensions - Chest: $chest, Waist: $waist, Hips: $hips (Manual: $hasManualInput)")
        
        // ✅ COMPREHENSIVE LOGGING FOR DEBUGGING
        Log.d(TAG, "=== SIZE MATCHING DEBUG INFO ===")
        Log.d(TAG, "User Input - Height: $userHeight cm, Weight: $userWeight kg")
        Log.d(TAG, "User Dimensions - Chest: $chest cm, Waist: $waist cm, Hips: $hips cm")
        Log.d(TAG, "Region: ${region.name}, Gender: ${gender.name}, Clothing: ${clothingType.name}")
        Log.d(TAG, "Valid sizes count: ${validSizes.size}")
        validSizes.forEachIndexed { idx, size ->
            Log.d(TAG, "Size[$idx] ${size.size}: chest=${size.chestCm}, waist=${size.waistCm}, hips=${size.hipsCm}")
        }
        
        // Step 3: Calculate match scores with multiple advanced methods
        val statisticalScores = calculateStatisticalScores(
            chest, waist, hips, userHeight, userWeight,
            validSizes, clothingType
        )
        
        val fuzzyScores = calculateFuzzyScores(
            chest, waist, hips, userHeight, userWeight,
            validSizes, clothingType
        )
        
        val bayesianScores = calculateBayesianScores(
            chest, waist, hips, userHeight, userWeight,
            validSizes, clothingType
        )
        
        val knnScores = calculateKNNScores(
            chest, waist, hips, userHeight, userWeight,
            validSizes, clothingType
        )
        
        // ✅ ENHANCED: Add Monte Carlo Simulation scores
        val monteCarloScores = runMonteCarloSimulation(
            predictedDimensions = predicted,
            userChest = chest,
            userWaist = waist,
            userHips = hips,
            sizeChart = sizeChart,
            validSizes = validSizes,
            clothingType = clothingType,
            iterations = 1000
        )
        
        // ✅ ENHANCED: Add TOPSIS (MCDA) scores
        val topsisScores = calculateTOPSISScores(
            chest, waist, hips, userHeight, userWeight,
            validSizes, clothingType
        )
        
        val originalWeightedScores = validSizes.map { size ->
            calculateWeightedScore(
                userHeight, userWeight, chest, waist, hips,
                size, clothingType, hasManualInput
            )
        }
        
        // ✅ BUG #2: ARRAY INDEX MISMATCH VALIDATION
        val scoreListSizes = mapOf(
            "statistical" to statisticalScores.size,
            "fuzzy" to fuzzyScores.size,
            "bayesian" to bayesianScores.size,
            "knn" to knnScores.size,
            "weighted" to originalWeightedScores.size,
            "validSizes" to validSizes.size
        )
        Log.d(TAG, "Score list sizes: $scoreListSizes")
        
        // ✅ CRITICAL: Verify all lists have same size
        if (statisticalScores.size != validSizes.size ||
            fuzzyScores.size != validSizes.size ||
            bayesianScores.size != validSizes.size ||
            knnScores.size != validSizes.size ||
            originalWeightedScores.size != validSizes.size) {
            Log.e(TAG, "❌ CRITICAL: Score list size mismatch! This will cause wrong recommendations.")
            Log.e(TAG, "Expected size: ${validSizes.size}, but got: $scoreListSizes")
            // Fallback: Use only weighted scores which should be safe
            val weightedOnlyScores: List<Pair<SizeData, Double>> = validSizes.mapIndexed { index, size ->
                size to (originalWeightedScores.getOrNull(index) ?: 30.0)
            }.sortedWith(
                compareByDescending<Pair<SizeData, Double>> { it.second }
                    .thenBy { it.first.size }
            )
            val primarySize = weightedOnlyScores.first().first
            return EnhancedSizeRecommendation(
                primarySize = primarySize,
                alternativeSizes = weightedOnlyScores.take(3).map { it.first },
                confidence = weightedOnlyScores.first().second.coerceIn(30.0, 100.0),
                confidenceInterval = calculateConfidenceInterval(weightedOnlyScores),
                sizeChart = sizeChart,
                method = "Fallback (Size Mismatch)"
            )
        }
        
        // ✅ FIX: Combine all methods with intelligent weighting and better logging
        // ✅ BUG #2: SAFE ACCESS with getOrNull
        // ✅ ENHANCED: Include Monte Carlo and TOPSIS scores
        val combinedScores = validSizes.mapIndexed { index, size ->
            val statScore = statisticalScores.getOrNull(index) ?: 30.0
            val fuzzyScore = fuzzyScores.getOrNull(index) ?: 30.0
            val bayesScore = bayesianScores.getOrNull(index) ?: 30.0
            val knnScore = knnScores.getOrNull(index) ?: 30.0
            val mcScore = monteCarloScores.getOrNull(index) ?: 30.0
            val topsisScore = topsisScores.getOrNull(index) ?: 30.0
            val weightedScore = originalWeightedScores.getOrNull(index) ?: 30.0
            
            // Weighted combination based on data quality
            val dataQuality = calculateDataQuality(size, clothingType)
            val weights = if (dataQuality > 0.7) {
                // High quality data: trust statistical methods more
                // stat, fuzzy, bayes, knn, monteCarlo, topsis, weighted
                doubleArrayOf(0.20, 0.15, 0.18, 0.12, 0.15, 0.10, 0.10)
            } else {
                // Lower quality: trust simpler methods more
                doubleArrayOf(0.12, 0.12, 0.12, 0.15, 0.10, 0.08, 0.31)
            }
            
            val finalScore = (statScore * weights[0]) + 
                           (fuzzyScore * weights[1]) + 
                           (bayesScore * weights[2]) + 
                           (knnScore * weights[3]) + 
                           (mcScore * weights[4]) +
                           (topsisScore * weights[5]) +
                           (weightedScore * weights[6])
            
            Log.d(TAG, "Size ${size.size}: stat=${String.format("%.2f", statScore)}, fuzzy=${String.format("%.2f", fuzzyScore)}, bayes=${String.format("%.2f", bayesScore)}, knn=${String.format("%.2f", knnScore)}, weighted=${String.format("%.2f", weightedScore)}, final=${String.format("%.2f", finalScore)}")
            
            size to finalScore.coerceIn(0.0, 100.0)
        }.sortedWith(
            compareByDescending<Pair<SizeData, Double>> { it.second }
                .thenBy { it.first.size }  // Secondary sort for consistency
        )
        
        // ✅ FIX: Log all scores for debugging
        Log.d(TAG, "=== ALL SIZE SCORES (sorted) ===")
        combinedScores.forEachIndexed { index, (size, score) ->
            Log.d(TAG, "${index + 1}. Size ${size.size}: Score=${String.format("%.2f", score)}")
        }
        
        // ✅ BUG #6: SCORE CALCULATION VERIFICATION
        val scoreVariation = combinedScores.map { it.second }.distinct().size
        Log.d(TAG, "Score variation: $scoreVariation unique scores out of ${combinedScores.size} sizes")
        
        if (scoreVariation == 1 && combinedScores.size > 1) {
            Log.e(TAG, "❌ CRITICAL: ALL SIZES HAVE IDENTICAL SCORES!")
            Log.e(TAG, "This means the matching algorithm is not differentiating between sizes.")
            Log.e(TAG, "Individual scores breakdown:")
            combinedScores.take(5).forEach { (size, score) ->
                val idx = validSizes.indexOf(size)
                Log.e(TAG, "  ${size.size}: stat=${statisticalScores.getOrNull(idx)}, fuzzy=${fuzzyScores.getOrNull(idx)}, bayes=${bayesianScores.getOrNull(idx)}, knn=${knnScores.getOrNull(idx)}, weighted=${originalWeightedScores.getOrNull(idx)}")
            }
            // Force use of weighted-only method
            val weightedOnlyScores: List<Pair<SizeData, Double>> = validSizes.mapIndexed { index, size ->
                size to (originalWeightedScores.getOrNull(index) ?: 30.0)
            }.sortedWith(
                compareByDescending<Pair<SizeData, Double>> { it.second }
                    .thenBy { it.first.size }
            )
            
            if (weightedOnlyScores.map { it.second }.distinct().size == 1) {
                Log.e(TAG, "❌ CRITICAL: Even weighted scores are identical! Size chart data is invalid.")
                return createFallbackRecommendation(userHeight, userWeight, sizeChart, region, gender)
            }
            
            val primarySize = weightedOnlyScores.first().first
            val confidence = weightedOnlyScores.first().second
            Log.d(TAG, "✅ Using weighted-only method: ${primarySize.size}, Confidence: ${String.format("%.2f", confidence)}%")
            
            return EnhancedSizeRecommendation(
                primarySize = primarySize,
                alternativeSizes = weightedOnlyScores.take(3).map { it.first },
                confidence = confidence.coerceIn(30.0, 100.0),
                confidenceInterval = calculateConfidenceInterval(weightedOnlyScores),
                sizeChart = sizeChart,
                method = "Weighted Only (Identical Combined Scores)"
            )
        }
        
        // ✅ FIX: Verify we have valid scores and not all the same
        val topScore = combinedScores.first().second
        val allSameScore = combinedScores.all { abs(it.second - topScore) < 0.01 }
        
        if (allSameScore && combinedScores.size > 1) {
            Log.w(TAG, "⚠️ WARNING: All sizes have similar scores! Using weighted score method only.")
            // If all scores are the same, use weighted score only (most reliable)
            val weightedOnlyScores: List<Pair<SizeData, Double>> = validSizes.mapIndexed { index, size ->
                size to (originalWeightedScores.getOrNull(index) ?: 30.0)
            }.sortedWith(
                compareByDescending<Pair<SizeData, Double>> { it.second }
                    .thenBy { it.first.size }
            )
            
            Log.d(TAG, "=== WEIGHTED SCORES ONLY ===")
            weightedOnlyScores.forEachIndexed { index, (size, score) ->
                Log.d(TAG, "${index + 1}. Size ${size.size}: Weighted Score=${String.format("%.2f", score)}")
            }
            
            val primarySize = weightedOnlyScores.first().first
            val confidence = weightedOnlyScores.first().second
            
            Log.d(TAG, "✅ Recommendation (weighted only): ${primarySize.size}, Confidence: ${String.format("%.2f", confidence)}%")
            
            return EnhancedSizeRecommendation(
                primarySize = primarySize,
                alternativeSizes = weightedOnlyScores.take(3).map { it.first },
                confidence = confidence.coerceIn(30.0, 100.0),
                confidenceInterval = calculateConfidenceInterval(weightedOnlyScores),
                sizeChart = sizeChart,
                method = if (hasManualInput) "Manual Input (Weighted Only)" else "Predicted (Weighted Only)"
            )
        }
        
        val primarySize = combinedScores.first().first
        val confidence = combinedScores.first().second
        
        // ✅ FIX: More dynamic and accurate confidence calculation
        val finalConfidence = when {
            confidence < 20.0 && validSizes.isNotEmpty() -> {
                // Very low confidence - scale more conservatively
                20.0 + (confidence * 0.5)
            }
            confidence < 30.0 && validSizes.isNotEmpty() -> {
                // Low confidence - moderate scaling
                30.0 + (confidence * 0.6)
            }
            else -> confidence.coerceIn(0.0, 100.0)
        }
        
        Log.e(TAG, "🟢🟢🟢 FINAL RECOMMENDATION 🟢🟢🟢")
        Log.e(TAG, "🟢 PRIMARY SIZE: ${primarySize.size}, Confidence: ${String.format("%.2f", finalConfidence)}%")
        Log.e(TAG, "🟢 Top 3 sizes: ${combinedScores.take(3).joinToString { "${it.first.size}(${String.format("%.1f", it.second)})" }}")
        Log.e(TAG, "🟢 ALL SCORES: ${combinedScores.joinToString { "${it.first.size}=${String.format("%.2f", it.second)}" }}")
        Log.e(TAG, "🟢 Input was: Height=$userHeight, Weight=$userWeight, Region=$region, Gender=$gender")
        
        val result = EnhancedSizeRecommendation(
            primarySize = primarySize,
            alternativeSizes = combinedScores.take(3).map { it.first },
            confidence = finalConfidence.coerceIn(30.0, 100.0),
            confidenceInterval = calculateConfidenceInterval(combinedScores),
            sizeChart = sizeChart,
            method = if (hasManualInput) "Manual Input" else "Predicted"
        )
        
        Log.e(TAG, "🟢 RETURNING: ${result.primarySize?.size ?: "N/A"}")
        return result
    }
    
    /**
     * Statistical Matching with Z-Scores and Standard Deviation
     */
    private fun calculateStatisticalScores(
        chest: Double, waist: Double, hips: Double,
        height: Double, weight: Double,
        sizes: List<SizeData>,
        clothingType: SizeClothingType
    ): List<Double> {
        // Calculate mean and std dev for each dimension from size chart
        val chestValues = sizes.mapNotNull { it.chestCm?.let { r -> (r.first + r.last) / 2.0 } }
        val waistValues = sizes.mapNotNull { it.waistCm?.let { r -> (r.first + r.last) / 2.0 } }
        val hipsValues = sizes.mapNotNull { it.hipsCm?.let { r -> (r.first + r.last) / 2.0 } }
        
        val chestMean = if (chestValues.isNotEmpty()) chestValues.average() else chest
        val chestStd = if (chestValues.size > 1) {
            sqrt(chestValues.map { (it - chestMean).pow(2) }.average()).coerceAtLeast(5.0)
        } else 10.0
        
        val waistMean = if (waistValues.isNotEmpty()) waistValues.average() else waist
        val waistStd = if (waistValues.size > 1) {
            sqrt(waistValues.map { (it - waistMean).pow(2) }.average()).coerceAtLeast(5.0)
        } else 10.0
        
        val hipsMean = if (hipsValues.isNotEmpty()) hipsValues.average() else hips
        val hipsStd = if (hipsValues.size > 1) {
            sqrt(hipsValues.map { (it - hipsMean).pow(2) }.average()).coerceAtLeast(5.0)
        } else 10.0
        
        return sizes.map { size ->
            var score = 0.0
            var weight = 0.0
            
            // Chest z-score matching
            size.chestCm?.let { range ->
                val rangeCenter = (range.first + range.last) / 2.0
                val zScore = abs((chest - rangeCenter) / chestStd)
                val matchScore = exp(-zScore * zScore / 2.0) // Gaussian probability
                val importance = if (clothingType == SizeClothingType.SHIRT) 0.40 else 0.15
                score += matchScore * importance
                weight += importance
            }
            
            // Waist z-score matching
            size.waistCm?.let { range ->
                val rangeCenter = (range.first + range.last) / 2.0
                val zScore = abs((waist - rangeCenter) / waistStd)
                val matchScore = exp(-zScore * zScore / 2.0)
                val importance = if (clothingType == SizeClothingType.PANTS) 0.50 else 0.20
                score += matchScore * importance
                weight += importance
            }
            
            // Hips z-score matching
            size.hipsCm?.let { range ->
                val rangeCenter = (range.first + range.last) / 2.0
                val zScore = abs((hips - rangeCenter) / hipsStd)
                val matchScore = exp(-zScore * zScore / 2.0)
                score += matchScore * 0.15
                weight += 0.15
            }
            
            // ✅ BUG #1: CRITICAL FIX - Ensure proper return value
            // In Kotlin lambda, last expression is automatically returned
            // But we need to ensure the expression is actually the last one
            if (weight > 0) {
                val normalizedScore = (score / weight) * 100.0
                // ✅ FIX: Last expression = return value (coerceIn returns Double)
                normalizedScore.coerceIn(0.0, 100.0)
            } else {
                // No data - return lower score, not default 50%
                30.0
            }
        }
    }
    
    /**
     * Fuzzy Logic Matching with Membership Functions
     */
    private fun calculateFuzzyScores(
        chest: Double, waist: Double, hips: Double,
        height: Double, weight: Double,
        sizes: List<SizeData>,
        clothingType: SizeClothingType
    ): List<Double> {
        return sizes.map { size ->
            var score = 0.0
            var weight = 0.0
            
            // ✅ FIX: Fuzzy membership function - more intelligent and never returns 0
            fun fuzzyMembership(value: Double, range: IntRange): Double {
                val center = (range.first + range.last) / 2.0
                val width = max(5.0, (range.last - range.first) / 2.0) // Minimum width 5cm
                val distance = abs(value - center)
                
                return when {
                    distance <= width -> {
                        // Perfect match zone - score based on proximity to center
                        0.9 + (0.1 * (1.0 - (distance / width)))
                    }
                    distance <= width * 2 -> {
                        // Good match zone
                        0.7 - ((distance - width) / width) * 0.3
                    }
                    distance <= width * 4 -> {
                        // Acceptable match zone - never return 0
                        max(0.2, 0.4 - ((distance - width * 2) / (width * 2)) * 0.2)
                    }
                    else -> {
                        // Far but still consider - minimum score for comparison
                        max(0.1, 0.2 - ((distance - width * 4) / (width * 10)))
                    }
                }
            }
            
            size.chestCm?.let { range ->
                val fuzzyScore = fuzzyMembership(chest, range)
                val importance = if (clothingType == SizeClothingType.SHIRT) 0.40 else 0.15
                score += fuzzyScore * importance
                weight += importance
            }
            
            size.waistCm?.let { range ->
                val fuzzyScore = fuzzyMembership(waist, range)
                val importance = if (clothingType == SizeClothingType.PANTS) 0.50 else 0.20
                score += fuzzyScore * importance
                weight += importance
            }
            
            size.hipsCm?.let { range ->
                val fuzzyScore = fuzzyMembership(hips, range)
                score += fuzzyScore * 0.15
                weight += 0.15
            }
            
            // ✅ FIX: Better default score - don't give same score to all
            if (weight > 0) {
                (score / weight) * 100.0
            } else {
                30.0 // Lower score if no data, not default 50%
            }
        }
    }
    
    /**
     * Bayesian Inference for Probability-Based Matching
     */
    private fun calculateBayesianScores(
        chest: Double, waist: Double, hips: Double,
        height: Double, weight: Double,
        sizes: List<SizeData>,
        clothingType: SizeClothingType
    ): List<Double> {
        // Prior probability: uniform distribution
        val prior = 1.0 / sizes.size
        
        return sizes.map { size ->
            var likelihood = 1.0
            
            // Calculate likelihood for each dimension
            size.chestCm?.let { range ->
                val probability = calculateRangeProbability(chest, range)
                likelihood *= probability
            }
            
            size.waistCm?.let { range ->
                val probability = calculateRangeProbability(waist, range)
                likelihood *= probability
            }
            
            size.hipsCm?.let { range ->
                val probability = calculateRangeProbability(hips, range)
                likelihood *= probability
            }
            
            // Normalize likelihood
            val totalLikelihood = sizes.sumOf { s ->
                var l = 1.0
                s.chestCm?.let { r -> l *= calculateRangeProbability(chest, r) }
                s.waistCm?.let { r -> l *= calculateRangeProbability(waist, r) }
                s.hipsCm?.let { r -> l *= calculateRangeProbability(hips, r) }
                l
            }.coerceAtLeast(0.0001)
            
            // ✅ FIX: Correct Bayesian posterior probability formula
            // P(size|data) = (P(data|size) * P(size)) / P(data)
            // where P(data) = sum of all P(data|size) * P(size)
            val posterior = (likelihood * prior) / totalLikelihood
            posterior * 100.0
        }
    }
    
    // ✅ FIX: Improved range probability - never returns 0, better for outliers
    private fun calculateRangeProbability(value: Double, range: IntRange): Double {
        val center = (range.first + range.last) / 2.0
        val rangeWidth = (range.last - range.first).toDouble()
        val std = max(5.0, rangeWidth / 4.0) // Minimum std dev 5cm
        val zScore = abs((value - center) / std)
        
        // ✅ FIX: Use more lenient Gaussian with minimum probability
        val gaussianProb = exp(-zScore * zScore / 2.0)
        // Ensure minimum probability so all sizes are considered
        return max(0.01, gaussianProb)
    }
    
    /**
     * K-Nearest Neighbor with Weighted Distance
     */
    private fun calculateKNNScores(
        chest: Double, waist: Double, hips: Double,
        height: Double, weight: Double,
        sizes: List<SizeData>,
        clothingType: SizeClothingType
    ): List<Double> {
        val userVector = doubleArrayOf(chest, waist, hips, height, weight)
        
        return sizes.map { size ->
            val sizeVector = doubleArrayOf(
                size.chestCm?.let { (it.first + it.last) / 2.0 } ?: chest,
                size.waistCm?.let { (it.first + it.last) / 2.0 } ?: waist,
                size.hipsCm?.let { (it.first + it.last) / 2.0 } ?: hips,
                size.heightCmMin?.toDouble() ?: height,
                size.weightKgRange?.let { (it.first + it.last) / 2.0 } ?: weight
            )
            
            // Weighted Euclidean distance
            val weights = doubleArrayOf(
                if (clothingType == SizeClothingType.SHIRT) 0.3 else 0.1, // chest
                if (clothingType == SizeClothingType.PANTS) 0.3 else 0.1, // waist
                0.1, // hips
                0.2, // height
                0.2  // weight
            )
            
            var distanceSum = 0.0
            for (i in userVector.indices) {
                val diff = userVector[i] - sizeVector[i]
                distanceSum += weights[i] * diff * diff
            }
            val distance = sqrt(distanceSum)
            
            // Convert distance to score (inverse relationship)
            max(30.0, 100.0 - (distance / 5.0))
        }
    }
    
    /**
     * Calculate data quality score for a size
     */
    private fun calculateDataQuality(size: SizeData, clothingType: SizeClothingType): Double {
        var quality = 0.0
        var maxQuality = 0.0
        
        if (clothingType == SizeClothingType.SHIRT) {
            if (size.chestCm != null) quality += 0.5
            maxQuality += 0.5
            if (size.waistCm != null) quality += 0.2
            maxQuality += 0.2
        } else {
            if (size.waistCm != null) quality += 0.5
            maxQuality += 0.5
            if (size.chestCm != null) quality += 0.2
            maxQuality += 0.2
        }
        
        if (size.hipsCm != null) quality += 0.15
        maxQuality += 0.15
        
        if (size.heightCmMin != null) quality += 0.1
        maxQuality += 0.1
        
        if (size.weightKgRange != null) quality += 0.05
        maxQuality += 0.05
        
        return if (maxQuality > 0) quality / maxQuality else 0.5
    }
    
    /**
     * Fallback recommendation when size chart has no valid data
     */
    private fun createFallbackRecommendation(
        height: Double,
        weight: Double,
        sizeChart: SizeChart,
        region: SizeRegion,
        gender: Gender
    ): EnhancedSizeRecommendation {
        Log.w(TAG, "Creating fallback recommendation - no valid size data")
        
        // Use simple height/weight based matching
        val bmi = weight / ((height / 100).pow(2))
        
        // Estimate size based on BMI and available sizes
        val sortedSizes = sizeChart.sizes.sortedBy { it.size }
        val estimatedSize = when {
            bmi < 18.5 -> sortedSizes.firstOrNull()?.size ?: "S"
            bmi < 23 -> sortedSizes.getOrNull(sortedSizes.size / 3)?.size ?: "M"
            bmi < 27 -> sortedSizes.getOrNull(sortedSizes.size * 2 / 3)?.size ?: "L"
            else -> sortedSizes.lastOrNull()?.size ?: "XL"
        }
        
        val fallbackSize = sizeChart.sizes.find { it.size == estimatedSize }
            ?: sizeChart.sizes.firstOrNull()
            ?: SizeData(size = estimatedSize)
        
        return EnhancedSizeRecommendation(
            primarySize = fallbackSize,
            alternativeSizes = sizeChart.sizes.take(2),
            confidence = 60.0, // Lower confidence for fallback
            confidenceInterval = 0.20,
            sizeChart = sizeChart,
            method = "Fallback (Height/Weight Only)"
        )
    }
    
    private fun calculateConfidenceInterval(scores: List<Pair<SizeData, Double>>): Double {
        if (scores.size < 2) return 0.15
        
        val topScore = scores.first().second
        val secondScore = scores.getOrNull(1)?.second ?: 0.0
        val scoreDiff = topScore - secondScore
        
        // Higher confidence if top score is significantly higher
        return when {
            scoreDiff > 20 -> 0.05  // Very confident
            scoreDiff > 10 -> 0.10  // Confident
            scoreDiff > 5 -> 0.15   // Moderate
            else -> 0.20            // Low confidence
        }
    }
    
    /**
     * ✅ ENHANCED: Run Monte Carlo Simulation for uncertainty propagation
     * 
     * Samples from predicted distribution and calculates fit scores
     * 
     * @param predictedDimensions Predicted body dimensions with uncertainty
     * @param userChest User chest measurement (or predicted)
     * @param userWaist User waist measurement (or predicted)
     * @param userHips User hips measurement (or predicted)
     * @param sizeChart Size chart to match against
     * @param validSizes Valid sizes from chart
     * @param clothingType Clothing type
     * @param iterations Number of Monte Carlo iterations (default 1000)
     * @return List of scores for each size
     */
    private fun runMonteCarloSimulation(
        predictedDimensions: PredictedBodyDimensions,
        userChest: Double?,
        userWaist: Double?,
        userHips: Double?,
        sizeChart: SizeChart,
        validSizes: List<SizeData>,
        clothingType: SizeClothingType,
        iterations: Int = 1000
    ): List<Double> {
        // Estimate covariance matrix from prediction uncertainties
        val chestStd = (predictedDimensions.chest.upperBound - predictedDimensions.chest.lowerBound) / 4.0
        val waistStd = (predictedDimensions.waist.upperBound - predictedDimensions.waist.lowerBound) / 4.0
        val hipsStd = (predictedDimensions.hips.upperBound - predictedDimensions.hips.lowerBound) / 4.0
        
        // Note: Correlation coefficients available for future multivariate sampling enhancement
        // val chestWaistCorr = 0.6
        // val chestHipsCorr = 0.5
        // val waistHipsCorr = 0.7
        
        // Run Monte Carlo simulation
        val sizeScores = validSizes.map { size ->
            var totalScore = 0.0
            
            repeat(iterations) {
                // Sample from multivariate normal distribution (simplified)
                val chestSample = sampleNormal(predictedDimensions.chest.value, chestStd)
                val waistSample = sampleNormal(predictedDimensions.waist.value, waistStd)
                val hipsSample = sampleNormal(predictedDimensions.hips.value, hipsStd)
                
                // Calculate fit score for this sample
                val sampleScore = calculateFitScoreForSample(
                    chestSample, waistSample, hipsSample,
                    size, clothingType
                )
                
                totalScore += sampleScore
            }
            
            // Return mean score
            (totalScore / iterations) * 100.0
        }
        
        return sizeScores
    }
    
    /**
     * Sample from normal distribution using Box-Muller transform
     */
    private fun sampleNormal(mean: Double, stdDev: Double): Double {
        val u1 = kotlin.random.Random.nextDouble()
        val u2 = kotlin.random.Random.nextDouble()
        val z = sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * kotlin.math.PI * u2)
        return mean + z * stdDev
    }
    
    /**
     * Calculate fit score for a single sample
     */
    private fun calculateFitScoreForSample(
        chest: Double,
        waist: Double,
        hips: Double,
        size: SizeData,
        clothingType: SizeClothingType
    ): Double {
        var score = 0.0
        var weight = 0.0
        
        size.chestCm?.let { range ->
            val rangeCenter = (range.first + range.last) / 2.0
            val distance = abs(chest - rangeCenter)
            val rangeWidth = (range.last - range.first) / 2.0
            val matchScore = exp(-(distance / rangeWidth).pow(2))
            val importance = if (clothingType == SizeClothingType.SHIRT) 0.40 else 0.15
            score += matchScore * importance
            weight += importance
        }
        
        size.waistCm?.let { range ->
            val rangeCenter = (range.first + range.last) / 2.0
            val distance = abs(waist - rangeCenter)
            val rangeWidth = (range.last - range.first) / 2.0
            val matchScore = exp(-(distance / rangeWidth).pow(2))
            val importance = if (clothingType == SizeClothingType.PANTS) 0.50 else 0.20
            score += matchScore * importance
            weight += importance
        }
        
        size.hipsCm?.let { range ->
            val rangeCenter = (range.first + range.last) / 2.0
            val distance = abs(hips - rangeCenter)
            val rangeWidth = (range.last - range.first) / 2.0
            val matchScore = exp(-(distance / rangeWidth).pow(2))
            score += matchScore * 0.15
            weight += 0.15
        }
        
        return if (weight > 0) score / weight else 0.3
    }
    
    /**
     * ✅ ENHANCED: Calculate TOPSIS (Multi-Criteria Decision Analysis) scores
     * 
     * TOPSIS: Technique for Order Preference by Similarity to Ideal Solution
     * 
     * @param chest User chest measurement
     * @param waist User waist measurement
     * @param hips User hips measurement
     * @param height User height
     * @param weight User weight
     * @param sizes List of sizes to evaluate
     * @param clothingType Clothing type
     * @return List of TOPSIS scores (0-100)
     */
    private fun calculateTOPSISScores(
        chest: Double,
        waist: Double,
        hips: Double,
        height: Double,  // Reserved for future height-based criteria
        weight: Double,  // Reserved for future weight-based criteria
        sizes: List<SizeData>,
        clothingType: SizeClothingType
    ): List<Double> {
        // Criteria weights: chest (0.35), waist (0.40), hips (0.25)
        val chestWeight = if (clothingType == SizeClothingType.SHIRT) 0.40 else 0.30
        val waistWeight = if (clothingType == SizeClothingType.PANTS) 0.50 else 0.35
        val hipsWeight = 0.25
        
        // Normalize weights to sum to 1.0
        val totalWeight = chestWeight + waistWeight + hipsWeight
        val normalizedChestWeight = chestWeight / totalWeight
        val normalizedWaistWeight = waistWeight / totalWeight
        val normalizedHipsWeight = hipsWeight / totalWeight
        
        // Extract dimension values for all sizes
        val chestCenters = sizes.mapNotNull { it.chestCm?.let { r -> (r.first + r.last) / 2.0 } }
        val waistCenters = sizes.mapNotNull { it.waistCm?.let { r -> (r.first + r.last) / 2.0 } }
        val hipsCenters = sizes.mapNotNull { it.hipsCm?.let { r -> (r.first + r.last) / 2.0 } }
        
        // Calculate positive and negative ideal solutions
        val chestIdealPos = chestCenters.maxOrNull() ?: chest
        val chestIdealNeg = chestCenters.minOrNull() ?: chest
        val waistIdealPos = waistCenters.maxOrNull() ?: waist
        val waistIdealNeg = waistCenters.minOrNull() ?: waist
        val hipsIdealPos = hipsCenters.maxOrNull() ?: hips
        val hipsIdealNeg = hipsCenters.minOrNull() ?: hips
        
        return sizes.map { size ->
            val chestCenter = size.chestCm?.let { (it.first + it.last) / 2.0 } ?: chest
            val waistCenter = size.waistCm?.let { (it.first + it.last) / 2.0 } ?: waist
            val hipsCenter = size.hipsCm?.let { (it.first + it.last) / 2.0 } ?: hips
            
            // Distance to positive ideal (we want to minimize this)
            val dPos = sqrt(
                normalizedChestWeight * (chestCenter - chestIdealPos).pow(2) +
                normalizedWaistWeight * (waistCenter - waistIdealPos).pow(2) +
                normalizedHipsWeight * (hipsCenter - hipsIdealPos).pow(2)
            )
            
            // Distance to negative ideal (we want to maximize this)
            val dNeg = sqrt(
                normalizedChestWeight * (chestCenter - chestIdealNeg).pow(2) +
                normalizedWaistWeight * (waistCenter - waistIdealNeg).pow(2) +
                normalizedHipsWeight * (hipsCenter - hipsIdealNeg).pow(2)
            )
            
            // TOPSIS score: dNeg / (dPos + dNeg)
            val topsisScore = if (dPos + dNeg > 0) {
                dNeg / (dPos + dNeg)
            } else {
                0.5
            }
            
            topsisScore * 100.0
        }
    }
    
    /**
     * Calculate weighted score with importance weighting
     */
    private fun calculateWeightedScore(
        userHeight: Double,
        userWeight: Double,
        chest: Double,
        waist: Double,
        hips: Double,
        size: SizeData,
        clothingType: SizeClothingType,
        hasManualInput: Boolean
    ): Double {
        var totalScore = 0.0
        var totalWeight = 0.0
        
        // Weight matching (10% weight)
        size.weightKgRange?.let { range ->
            val weightScore = if (userWeight in range.first.toDouble()..range.last.toDouble()) {
                1.0
            } else {
                val distance = calculateNormalizedDistance(userWeight, range)
                1.0 - distance
            }
            totalScore += weightScore * 0.10
            totalWeight += 0.10
        }
        
        // Height matching (10% weight)
        size.heightCmMin?.let { minHeight ->
            val heightScore = if (userHeight >= minHeight) {
                1.0
            } else {
                max(0.0, 1.0 - ((minHeight - userHeight) / 30.0))
            }
            totalScore += heightScore * 0.10
            totalWeight += 0.10
        }
        
        // Chest matching (critical for shirt - 40% weight)
        if (clothingType == SizeClothingType.SHIRT) {
            size.chestCm?.let { range ->
                val chestScore = calculateRangeMatchScore(chest, range)
                val boost = if (hasManualInput) 1.15 else 1.0
                totalScore += chestScore * 0.40 * boost
                totalWeight += 0.40
            }
        }
        
        // Waist matching (critical for pants - 50% weight)
        if (clothingType == SizeClothingType.PANTS) {
            size.waistCm?.let { range ->
                val waistScore = calculateRangeMatchScore(waist, range)
                val boost = if (hasManualInput) 1.15 else 1.0
                totalScore += waistScore * 0.50 * boost
                totalWeight += 0.50
            }
            // ✅ ENHANCED: Length matching for pants (15% weight)
            size.lengthCm?.let { range ->
                // For pants, length is important but we don't have user length input
                // So we use height as proxy (pants length ≈ height * 0.6 for full length)
                val estimatedLength = userHeight * 0.6
                val lengthScore = calculateRangeMatchScore(estimatedLength, range)
                totalScore += lengthScore * 0.15
                totalWeight += 0.15
            }
        } else {
            // Waist for shirt (15% weight)
            size.waistCm?.let { range ->
                val waistScore = calculateRangeMatchScore(waist, range)
                totalScore += waistScore * 0.15
                totalWeight += 0.15
            }
        }
        
        // Hips matching (10% weight)
        size.hipsCm?.let { range ->
            val hipsScore = calculateRangeMatchScore(hips, range)
            totalScore += hipsScore * 0.10
            totalWeight += 0.10
        }
        
        // Normalize score - ensure minimum 30% if we have any data
        return if (totalWeight > 0) {
            val normalized = (totalScore / totalWeight) * 100.0
            // Ensure minimum confidence if we have any matching data
            max(30.0, normalized)
        } else {
            // No matching data - return baseline score
            30.0
        }
    }
    
    /**
     * Calculate how well a value matches a range
     * ✅ FIX: Improved penalty calculation - more intelligent and accurate matching
     */
    private fun calculateRangeMatchScore(value: Double, range: IntRange): Double {
        val rangeCenter = (range.first + range.last) / 2.0
        val rangeWidth = (range.last - range.first).toDouble()
        
        return when {
            value in range.first.toDouble()..range.last.toDouble() -> {
                // ✅ FIX: Perfect match - score based on how close to center (better differentiation)
                val distanceFromCenter = abs(value - rangeCenter)
                val maxDistance = rangeWidth / 2.0
                if (maxDistance > 0) {
                    // Closer to center = higher score (1.0 at center, ~0.8 at edges)
                    0.8 + (0.2 * (1.0 - (distanceFromCenter / maxDistance)))
                } else {
                    1.0 // Single value range
                }
            }
            value < range.first -> {
                val diff = range.first - value
                // ✅ FIX: Adaptive penalty - more lenient, never return 0
                val penaltyFactor = max(10.0, rangeWidth * 0.5) // At least 10cm, or 50% of range width
                val penalty = diff / penaltyFactor
                // ✅ CRITICAL: Never return 0 - minimum score for comparison
                max(0.15, 1.0 - penalty)
            }
            else -> {
                val diff = value - range.last
                val penaltyFactor = max(10.0, rangeWidth * 0.5)
                val penalty = diff / penaltyFactor
                // ✅ CRITICAL: Never return 0 - minimum score for comparison
                max(0.15, 1.0 - penalty)
            }
        }
    }
    
    /**
     * Calculate normalized distance (0-1)
     */
    private fun calculateNormalizedDistance(value: Double, range: IntRange): Double {
        return when {
            value in range.first.toDouble()..range.last.toDouble() -> 0.0
            value < range.first -> (range.first - value) / 20.0
            else -> (value - range.last) / 20.0
        }.coerceIn(0.0, 1.0)
    }
}

