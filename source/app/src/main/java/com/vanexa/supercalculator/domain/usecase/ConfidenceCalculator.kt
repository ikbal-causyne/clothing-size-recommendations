package com.vanexa.supercalculator.domain.usecase

import android.util.Log
import com.vanexa.supercalculator.domain.model.*
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Confidence Calculator
 * Implements Bayesian confidence calculation, uncertainty quantification, and Brier score
 * Based on Statistics principles: Bayesian Inference, Confidence Intervals, Calibration
 * 
 * Scientific Foundation:
 * - Bayesian posterior probability: P(correct_size | data)
 * - Uncertainty quantification: Uncertainty = 1 - Confidence
 * - Brier score for calibration: BS = (1/n) × Σ(predicted - actual)²
 * - Confidence intervals: [μ - 1.96×σ, μ + 1.96×σ] (95% CI)
 */
class ConfidenceCalculator {
    companion object {
        private const val TAG = "ConfidenceCalculator"
    }
    
    /**
     * Calculate Bayesian confidence from size scores
     * 
     * Formula: Confidence = max(P(Sᵢ | data)) = max(posterior probabilities)
     * 
     * @param sizeScores List of (SizeData, score) pairs from matching algorithms
     * @return Confidence value (0-100)
     */
    fun calculateBayesianConfidence(
        sizeScores: List<Pair<SizeData, Double>>
    ): Double {
        if (sizeScores.isEmpty()) {
            Log.w(TAG, "Empty size scores, returning 0 confidence")
            return 0.0
        }
        
        // Normalize scores to probabilities (softmax)
        val maxScore = sizeScores.maxOfOrNull { it.second } ?: 0.0
        val normalizedScores = sizeScores.map { (size, score) ->
            Pair(size, kotlin.math.exp(score - maxScore))  // Prevent overflow
        }
        
        val sum = normalizedScores.sumOf { it.second }
        if (sum <= 0.0) {
            return 0.0
        }
        
        // Calculate posterior probabilities
        val probabilities = normalizedScores.map { (size, expScore) ->
            Pair(size, expScore / sum)
        }
        
        // Confidence = maximum posterior probability
        val maxProbability = probabilities.maxOfOrNull { it.second } ?: 0.0
        
        // Convert to percentage (0-100)
        return (maxProbability * 100.0).coerceIn(0.0, 100.0)
    }
    
    /**
     * Calculate uncertainty from confidence
     * 
     * Formula: Uncertainty = 1 - Confidence
     * 
     * @param confidence Confidence value (0-100)
     * @return Uncertainty value (0-100)
     */
    fun calculateUncertainty(confidence: Double): Double {
        return (100.0 - confidence).coerceIn(0.0, 100.0)
    }
    
    /**
     * Calculate confidence interval (95% CI)
     * 
     * Formula: [μ - 1.96×σ, μ + 1.96×σ]
     * 
     * @param mean Mean value
     * @param stdDev Standard deviation
     * @return Pair of (lower bound, upper bound)
     */
    fun calculateConfidenceInterval(
        mean: Double,
        stdDev: Double
    ): Pair<Double, Double> {
        val zScore = 1.96  // 95% confidence level
        val margin = zScore * stdDev
        
        return Pair(
            mean - margin,
            mean + margin
        )
    }
    
    /**
     * Calculate Brier score for calibration
     * 
     * Formula: BS = (1/n) × Σ(predicted - actual)²
     * 
     * Lower is better (0 = perfect calibration, 1 = worst)
     * 
     * @param predictedProbabilities List of predicted probabilities (0-1)
     * @param actualOutcomes List of actual outcomes (true/false)
     * @return Brier score (0-1)
     */
    fun calculateBrierScore(
        predictedProbabilities: List<Double>,
        actualOutcomes: List<Boolean>
    ): Double {
        if (predictedProbabilities.size != actualOutcomes.size) {
            Log.w(TAG, "Mismatched list sizes for Brier score calculation")
            return 1.0  // Worst possible score
        }
        
        if (predictedProbabilities.isEmpty()) {
            return 1.0
        }
        
        val squaredErrors = predictedProbabilities.zip(actualOutcomes).map { (pred, actual) ->
            val actualValue = if (actual) 1.0 else 0.0
            (pred - actualValue).pow(2)
        }
        
        return squaredErrors.average()
    }
    
    /**
     * Calculate ensemble confidence from multiple method scores
     * 
     * Combines scores from different matching algorithms with weights
     * 
     * @param methodScores Map of method name to list of (SizeData, score) pairs
     * @param weights Map of method name to weight (should sum to 1.0)
     * @return Final confidence value (0-100)
     */
    fun calculateEnsembleConfidence(
        methodScores: Map<String, List<Pair<SizeData, Double>>>,
        weights: Map<String, Double> = emptyMap()
    ): Double {
        if (methodScores.isEmpty()) {
            return 0.0
        }
        
        // Default equal weights if not provided
        val defaultWeight = 1.0 / methodScores.size
        val effectiveWeights = methodScores.keys.associateWith { method ->
            weights[method] ?: defaultWeight
        }
        
        // Normalize weights to sum to 1.0
        val weightSum = effectiveWeights.values.sum()
        val normalizedWeights = if (weightSum > 0.0) {
            effectiveWeights.mapValues { it.value / weightSum }
        } else {
            effectiveWeights.mapValues { 1.0 / methodScores.size }
        }
        
        // Calculate weighted average confidence for each size
        val sizeConfidences = mutableMapOf<SizeData, Double>()
        
        methodScores.forEach { (method, scores) ->
            val weight = normalizedWeights[method] ?: 0.0
            val methodConfidence = calculateBayesianConfidence(scores)
            
            scores.forEach { (size, _) ->
                sizeConfidences[size] = (sizeConfidences[size] ?: 0.0) + methodConfidence * weight
            }
        }
        
        // Return maximum confidence
        return sizeConfidences.values.maxOrNull() ?: 0.0
    }
    
    /**
     * Calculate confidence with uncertainty bounds
     * 
     * @param confidence Mean confidence value
     * @param uncertainty Standard deviation of confidence
     * @return ConfidenceInterval with lower and upper bounds
     */
    fun calculateConfidenceWithUncertainty(
        confidence: Double,
        uncertainty: Double
    ): ConfidenceInterval {
        val (lower, upper) = calculateConfidenceInterval(confidence, uncertainty)
        
        return ConfidenceInterval(
            lower = lower.coerceIn(0.0, 100.0),
            upper = upper.coerceIn(0.0, 100.0),
            mean = confidence,
            lowerBound = lower.coerceIn(0.0, 100.0),
            upperBound = upper.coerceIn(0.0, 100.0),
            confidenceLevel = 0.95
        )
    }
}

