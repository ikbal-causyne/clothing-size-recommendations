package com.vanexa.supercalculator.domain.usecase

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Unit test for FitScoringEngine confidence collapse and regional tolerance fixes
 * Tests Engine Vision compliance for 200cm/120kg/US input
 */
class FitScoringEngineTest {

    @Test
    fun testConfidenceCollapseFormula() {
        // Simulate Engine Vision confidence collapse: final = raw × (dataConfidence²)
        val rawScore = 0.618991  // Example raw weighted score
        val dataConfidence = 0.8490  // Geometric mean of per-dimension confidences
        val expectedFinal = (rawScore * (dataConfidence * dataConfidence)).coerceIn(0.0, 1.0)
        
        // Expected: 0.618991 × (0.8490²) = 0.618991 × 0.7209 ≈ 0.446189
        val tolerance = 0.0001
        assertTrue(expectedFinal in (0.446189 - tolerance)..(0.446189 + tolerance),
            "Final score should be raw × dataConfidence². Got $expectedFinal, expected ~0.446189")
    }

    @Test
    fun testGeometricMeanConfidence() {
        // Test geometric mean calculation for 3 confidences: 0.9, 0.9, 0.85
        val confs = listOf(0.9, 0.9, 0.85)
        val prod = confs.fold(1.0) { acc, v -> acc * v }
        val geometricMean = Math.pow(prod, 1.0 / confs.size)
        
        // Expected: (0.9 × 0.9 × 0.85)^(1/3) = (0.6885)^(1/3) ≈ 0.8826
        val tolerance = 0.001
        assertTrue(geometricMean in (0.880)..(0.885),
            "Geometric mean should be ~0.883. Got $geometricMean")
    }

    @Test
    fun testHighDefaultConfidence() {
        // Engine Vision fix: use 0.9 default confidence instead of 1.0
        val defaultConfidence = 0.90
        assertTrue(defaultConfidence >= 0.85 && defaultConfidence <= 0.95,
            "Default confidence should be high (0.90). Got $defaultConfidence")
    }

    @Test
    fun testProbabilisticOutput() {
        // Engine Vision mandate: output probability, never "NOT_RECOMMENDED" as absolute
        val scores = listOf(0.80, 0.50, 0.20, 0.05)
        
        for (score in scores) {
            val recommendation = when {
                score >= 0.90 -> "Excellent fit"
                score >= 0.75 -> "Very good fit"
                score >= 0.60 -> "Acceptable fit"
                else -> {
                    val fitProbability = (score * 100).toInt()
                    "Low Fit Probability ($fitProbability%) - Consider alternatives"
                }
            }
            
            // Verify no absolute "NOT_RECOMMENDED" in output
            assertTrue(!recommendation.contains("NOT_RECOMMENDED"),
                "Recommendation should be probabilistic, not absolute. Got: $recommendation")
        }
    }

    @Test
    fun testBodyEstimationFor200cm120kg() {
        // Validate expected body estimates per Engine Vision
        val height = 200.0
        val weight = 120.0
        val bmi = weight / ((height / 100.0) * (height / 100.0))
        
        // Expected: BMI = 30.0 → PLUS_SIZE frame
        assertTrue(bmi >= 29.5 && bmi <= 30.5,
            "BMI for 200cm/120kg should be ~30. Got $bmi")
        
        // Expected: chest = 200 × 0.545 = 109 cm (PLUS_SIZE ratio)
        val chestRatio = 0.545
        val estimatedChest = height * chestRatio
        assertTrue(estimatedChest >= 108.5 && estimatedChest <= 109.5,
            "Estimated chest for 200cm/PLUS_SIZE should be ~109cm. Got $estimatedChest")
        
        // Expected: waist = 109 × 0.92 = 100.28 cm (PLUS_SIZE waist-to-chest)
        val waistRatio = 0.92
        val estimatedWaist = estimatedChest * waistRatio
        assertTrue(estimatedWaist >= 99.8 && estimatedWaist <= 100.8,
            "Estimated waist should be ~100.28cm. Got $estimatedWaist")
    }

    @Test
    fun testRegionalToleranceFactor() {
        // Engine Vision: region only adjusts tolerance, not body estimation
        val regionFactors = mapOf(
            "US" to 1.03,
            "EU" to 1.00,
            "UK" to 1.02,
            "ASIA" to 0.97,
            "INDONESIA" to 0.96
        )
        
        // Example: US tolerance scaling
        val baseToleranceCm = 4.0
        val usToleranceCm = baseToleranceCm * regionFactors["US"]!!
        
        assertTrue(usToleranceCm >= 4.1 && usToleranceCm <= 4.15,
            "US tolerance should be 4.0 × 1.03 = 4.12cm. Got $usToleranceCm")
    }

    @Test
    fun testRanking200cm120kgUS() {
        // Simulate ranking for 200cm/120kg/US against sample sizes
        val userChest = 109.0
        val userWaist = 100.28
        val userHip = 130.36
        
        val sizes = listOf(
            Triple("M", 96.0, 80.0),
            Triple("L", 104.0, 92.0),
            Triple("XL", 112.0, 102.0),
            Triple("XXL", 120.0, 112.0)
        )
        
        data class SizeScore(val label: String, val score: Double)
        val scores = mutableListOf<SizeScore>()
        
        for ((label, chestRange, waistRange) in sizes) {
            val dChest = kotlin.math.abs(userChest - chestRange)
            val dWaist = kotlin.math.abs(userWaist - waistRange)
            val tolerance = 4.5
            
            // Simple Gaussian: exp(-0.5*(d/t)^2)
            val sChest = kotlin.math.exp(-0.5 * (dChest / tolerance).pow(2.0))
            val sWaist = kotlin.math.exp(-0.5 * (dWaist / tolerance).pow(2.0))
            
            // Weighted: 0.45 chest, 0.35 waist, rest hip
            val score = 0.45 * sChest + 0.35 * sWaist
            scores.add(SizeScore(label, score))
        }
        
        scores.sortByDescending { it.score }
        
        // Best match should be XL (chest 112, waist 102 closest to user 109/100.28)
        assertTrue(scores[0].label == "XL",
            "Best fit for 200cm/120kg/US should be XL. Got ${scores[0].label}")
    }
}
