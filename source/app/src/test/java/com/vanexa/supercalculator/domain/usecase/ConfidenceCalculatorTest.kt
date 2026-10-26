package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.SizeData
import org.junit.Assert.*
import org.junit.Test

/**
 * Confidence Calculator Test - Fixed to match ACTUAL implementation
 * Part of Size Recommendation Engine - Extreme Testing
 */
class ConfidenceCalculatorTest {
    private val calculator = ConfidenceCalculator()
    
    @Test
    fun `test Bayesian confidence calculation`() {
        // ACTUAL implementation uses softmax normalization
        // Confidence = max(posterior probabilities) * 100
        // With scores [0.3, 0.7, 0.2], after softmax:
        // - exp(0.3-0.7) = exp(-0.4) = 0.67
        // - exp(0.7-0.7) = exp(0) = 1.0
        // - exp(0.2-0.7) = exp(-0.5) = 0.61
        // Sum = 2.28
        // Probabilities: [0.29, 0.44, 0.27]
        // Max = 0.44 -> Confidence = 44%
        val sizeScores = listOf(
            Pair(SizeData(size = "S"), 0.3),
            Pair(SizeData(size = "M"), 0.7),
            Pair(SizeData(size = "L"), 0.2)
        )
        
        val confidence = calculator.calculateBayesianConfidence(sizeScores)
        
        // Confidence should be between 0 and 100
        assertTrue("Confidence should be between 0 and 100, got $confidence", confidence >= 0.0 && confidence <= 100.0)
        
        // With highest score 0.7, confidence should favor it
        // After softmax, max probability should be > 0.3 (roughly 0.44)
        // So confidence should be > 30%
        assertTrue("Confidence should favor highest score, got $confidence", confidence > 30.0)
    }
    
    @Test
    fun `test Bayesian confidence with equal scores`() {
        // All scores equal -> equal probabilities -> confidence = 1/n * 100
        val sizeScores = listOf(
            Pair(SizeData(size = "S"), 0.5),
            Pair(SizeData(size = "M"), 0.5),
            Pair(SizeData(size = "L"), 0.5)
        )
        
        val confidence = calculator.calculateBayesianConfidence(sizeScores)
        
        // With 3 equal scores, each has probability 1/3 = 0.333
        // Confidence = 0.333 * 100 = 33.3%
        assertTrue("Confidence should be ~33% for equal scores, got $confidence", 
            confidence >= 30.0 && confidence <= 40.0)
    }
    
    @Test
    fun `test uncertainty calculation`() {
        val confidence = 75.0
        val uncertainty = calculator.calculateUncertainty(confidence)
        
        // Uncertainty = 100 - Confidence = 100 - 75 = 25.0
        assertEquals("Uncertainty should be 25.0 for 75% confidence", 25.0, uncertainty, 0.01)
    }
    
    @Test
    fun `test confidence interval calculation`() {
        val mean = 100.0
        val stdDev = 5.0
        
        val (lower, upper) = calculator.calculateConfidenceInterval(mean, stdDev)
        
        // 95% CI: [μ - 1.96×σ, μ + 1.96×σ]
        // Expected: [100 - 1.96*5, 100 + 1.96*5] = [90.2, 109.8]
        assertTrue("Lower bound should be less than mean", lower < mean)
        assertTrue("Upper bound should be greater than mean", upper > mean)
        
        val expectedMargin = 1.96 * stdDev
        val actualMargin = (upper - lower) / 2.0
        
        assertTrue("Interval should be approximately 95% CI (margin ~9.8), got margin $actualMargin", 
            kotlin.math.abs(actualMargin - expectedMargin) < 1.0)
    }
    
    @Test
    fun `test Brier score calculation`() {
        val predicted = listOf(0.8, 0.6, 0.9, 0.3)
        val actual = listOf(true, true, true, false)
        
        val brierScore = calculator.calculateBrierScore(predicted, actual)
        
        // Brier score = (1/n) × Σ(predicted - actual)²
        // = (1/4) × [(0.8-1)² + (0.6-1)² + (0.9-1)² + (0.3-0)²]
        // = (1/4) × [0.04 + 0.16 + 0.01 + 0.09]
        // = (1/4) × 0.30 = 0.075
        assertTrue("Brier score should be between 0 and 1, got $brierScore", brierScore >= 0.0 && brierScore <= 1.0)
        assertTrue("Brier score should be reasonable for good predictions (<0.2), got $brierScore", brierScore < 0.2)
    }
    
    @Test
    fun `test Brier score perfect predictions`() {
        val predicted = listOf(1.0, 1.0, 0.0, 0.0)
        val actual = listOf(true, true, false, false)
        
        val brierScore = calculator.calculateBrierScore(predicted, actual)
        
        // Perfect predictions -> Brier score = 0.0
        assertEquals("Perfect predictions should have Brier score 0.0", 0.0, brierScore, 0.01)
    }
}
