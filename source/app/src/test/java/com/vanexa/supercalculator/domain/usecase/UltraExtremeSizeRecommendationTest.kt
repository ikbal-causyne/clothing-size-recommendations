package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

/**
 * ULTRA EXTREME SIZE RECOMMENDATION TEST
 * Tests recommendation engine with extreme scenarios
 */
class UltraExtremeSizeRecommendationTest {
    
    // ========== TEST 1: SORTING ACCURACY VERIFICATION ==========
    
    @Test
    fun testSortingAccuracy() {
        val userBody = EstimatedBodyMeasurements(
            chest = 96.0,
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        val sizeChart = listOf(
            SizeChartEntry("XS", chest = MeasurementRange(80.0, 84.0)),  // Score ~0.3
            SizeChartEntry("S", chest = MeasurementRange(88.0, 92.0)),   // Score ~0.5
            SizeChartEntry("M", chest = MeasurementRange(94.0, 98.0)),  // Score ~0.95 (best)
            SizeChartEntry("L", chest = MeasurementRange(100.0, 104.0)), // Score ~0.7
            SizeChartEntry("XL", chest = MeasurementRange(106.0, 110.0)) // Score ~0.3
        )
        
        val recommendation = SizeRecommendationEngine.recommendSizes(
            userBody, sizeChart, GarmentType.TSHIRT
        )
        
        // Primary should be M (highest score)
        assertEquals("Primary should be M", "M", recommendation.primaryRecommendation.sizeEntry.sizeLabel)
        
        // Verify primary has highest score
        val primaryScore = recommendation.primaryRecommendation.fitScore.overall
        
        // All alternatives should have score <= primary
        recommendation.alternativeRecommendations.forEach { alt ->
            assertTrue(
                "Alternative score should be <= primary: alt=${alt.fitScore.overall}, primary=$primaryScore",
                alt.fitScore.overall <= primaryScore
            )
        }
    }
    
    // ========== TEST 2: ALTERNATIVES LOGIC MATHEMATICAL VERIFICATION ==========
    
    @Test
    fun testAlternativesLogicMathematicalVerification() {
        val userBody = EstimatedBodyMeasurements(
            chest = 96.0,
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        val sizeChart = listOf(
            SizeChartEntry("M", chest = MeasurementRange(94.0, 98.0)),  // Score = 0.95
            SizeChartEntry("L", chest = MeasurementRange(96.0, 100.0)), // Score = 0.90 (within 0.15)
            SizeChartEntry("S", chest = MeasurementRange(90.0, 94.0)),  // Score = 0.75 (within 0.15)
            SizeChartEntry("XL", chest = MeasurementRange(102.0, 106.0)) // Score = 0.60 (outside 0.15)
        )
        
        val recommendation = SizeRecommendationEngine.recommendSizes(
            userBody, sizeChart, GarmentType.TSHIRT
        )
        
        // Primary: M (score 0.95)
        assertEquals("Primary should be M", "M", recommendation.primaryRecommendation.sizeEntry.sizeLabel)
        
        // Alternatives: Should include L (0.90 >= 0.95 - 0.15 = 0.80)
        val alternatives = recommendation.alternativeRecommendations
        val alternativeSizes = alternatives.map { it.sizeEntry.sizeLabel }
        
        // L should be in alternatives (0.90 >= 0.80)
        assertTrue("L should be alternative", alternativeSizes.contains("L"))
        
        // S might be in alternatives if score >= 0.80
        // XL should NOT be in alternatives (0.60 < 0.80)
        assertFalse("XL should NOT be alternative", alternativeSizes.contains("XL"))
    }
    
    // ========== TEST 3: BETWEEN-SIZES DETECTION MATHEMATICAL VERIFICATION ==========
    
    @Test
    fun testBetweenSizesDetectionMathematicalVerification() {
        val userBody = EstimatedBodyMeasurements(
            chest = 99.0,  // Exactly between M and L
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        val sizeChart = listOf(
            SizeChartEntry("M", chest = MeasurementRange(94.0, 98.0)),  // Score ~0.85
            SizeChartEntry("L", chest = MeasurementRange(100.0, 104.0)) // Score ~0.82 (diff=0.03)
        )
        
        val recommendation = SizeRecommendationEngine.recommendSizes(
            userBody, sizeChart, GarmentType.TSHIRT
        )
        
        // Score difference should be <= 0.05, so should detect between sizes
        val primaryScore = recommendation.primaryRecommendation.fitScore.overall
        val secondaryScore = recommendation.alternativeRecommendations.firstOrNull()?.fitScore?.overall ?: 0.0
        val scoreDiff = abs(primaryScore - secondaryScore)
        
        if (scoreDiff <= 0.05) {
            assertNotNull("Should detect between sizes when diff <= 0.05", recommendation.betweenSizesInfo)
        }
    }
    
    // ========== TEST 4: CONFIDENCE CALCULATION MATHEMATICAL VERIFICATION ==========
    
    @Test
    fun testConfidenceCalculationMathematicalVerification() {
        val highConfidenceBody = EstimatedBodyMeasurements(
            chest = 96.0,
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.90, emptyMap<String, MeasurementConfidence>()) // High confidence
        )
        
        val perfectFitSize = SizeChartEntry(
            "M",
            chest = MeasurementRange(94.0, 98.0)  // Perfect fit
        )
        
        val sizeChart = listOf(perfectFitSize)
        
        val recommendation = SizeRecommendationEngine.recommendSizes(
            highConfidenceBody, sizeChart, GarmentType.TSHIRT
        )
        
        // Expected confidence calculation:
        // Base: 0.90
        // Boost: + (fit_score × 0.2) = + (0.95 × 0.2) = +0.19
        // Total: 0.90 + 0.19 = 1.09 → clamped to 1.0
        
        val expectedConfidence = 1.0 // Clamped
        assertTrue("Confidence should be <= 1.0", recommendation.overallConfidence <= 1.0)
        assertTrue("Confidence should be >= 0.9", recommendation.overallConfidence >= 0.9)
    }
    
    // ========== TEST 5: CROSS-STORE VARIATION ULTRA TEST ==========
    
    @Test
    fun testCrossStoreVariation_UltraExtreme() {
        val userBody = EstimatedBodyMeasurements(
            chest = 98.0,
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        // Store A: US Brand (larger sizes)
        val storeA = listOf(
            SizeChartEntry("S", chest = MeasurementRange(88.0, 92.0)),
            SizeChartEntry("M", chest = MeasurementRange(96.0, 100.0)),  // User fits here
            SizeChartEntry("L", chest = MeasurementRange(104.0, 108.0)),
            SizeChartEntry("XL", chest = MeasurementRange(112.0, 116.0))
        )
        
        // Store B: Asian Brand (smaller sizes)
        val storeB = listOf(
            SizeChartEntry("S", chest = MeasurementRange(84.0, 88.0)),
            SizeChartEntry("M", chest = MeasurementRange(90.0, 94.0)),
            SizeChartEntry("L", chest = MeasurementRange(96.0, 100.0)),  // User fits here
            SizeChartEntry("XL", chest = MeasurementRange(102.0, 106.0))
        )
        
        val recA = SizeRecommendationEngine.recommendSizes(
            userBody, storeA, GarmentType.TSHIRT
        )
        val recB = SizeRecommendationEngine.recommendSizes(
            userBody, storeB, GarmentType.TSHIRT
        )
        
        val sizeA = recA.primaryRecommendation.sizeEntry.sizeLabel
        val sizeB = recB.primaryRecommendation.sizeEntry.sizeLabel
        
        // Should get different recommendations
        assertEquals("Store A should recommend M", "M", sizeA)
        assertEquals("Store B should recommend L", "L", sizeB)
        
        // Verify all are different
        assertNotEquals("Store A and B should differ", sizeA, sizeB)
    }
    
    // ========== TEST 6: PERFORMANCE STRESS TEST ==========
    
    @Test
    fun testPerformance_1000Sizes() {
        val userBody = EstimatedBodyMeasurements(
            chest = 96.0,
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        // Create size chart with 1000 sizes
        val largeSizeChart = (1..1000).map { i ->
            SizeChartEntry(
                "Size$i",
                chest = MeasurementRange(80.0 + i * 0.1, 84.0 + i * 0.1)
            )
        }
        
        val startTime = System.currentTimeMillis()
        val recommendation = SizeRecommendationEngine.recommendSizes(
            userBody, largeSizeChart, GarmentType.TSHIRT
        )
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        println("=== Performance Test: 1000 Sizes ===")
        println("Duration: ${duration}ms")
        println("Average per size: ${duration.toDouble() / 1000}ms")
        
        // Should complete in <5 seconds (5ms per size)
        assertTrue(
            "1000 sizes should complete in <5000ms, got ${duration}ms",
            duration < 5000
        )
        
        // Should still produce valid recommendation
        assertNotNull("Should have recommendation", recommendation.primaryRecommendation)
    }
    
    // ========== TEST 7: IDENTICAL SCORES HANDLING ==========
    
    @Test
    fun testIdenticalScoresHandling() {
        val userBody = EstimatedBodyMeasurements(
            chest = 96.0,
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        // Size chart where multiple sizes have identical measurements
        val sizeChart = listOf(
            SizeChartEntry("M1", chest = MeasurementRange(94.0, 98.0)),
            SizeChartEntry("M2", chest = MeasurementRange(94.0, 98.0)),  // Identical
            SizeChartEntry("M3", chest = MeasurementRange(94.0, 98.0))   // Identical
        )
        
        val recommendation = SizeRecommendationEngine.recommendSizes(
            userBody, sizeChart, GarmentType.TSHIRT
        )
        
        // Should handle gracefully - pick first one
        assertNotNull("Should have recommendation", recommendation.primaryRecommendation)
        
        // All should have same score
        val primaryScore = recommendation.primaryRecommendation.fitScore.overall
        recommendation.alternativeRecommendations.forEach { alt ->
            assertEquals(
                "Identical sizes should have same score",
                primaryScore,
                alt.fitScore.overall,
                0.01
            )
        }
    }
    
    // ========== TEST 8: EXTREME SCORE DIFFERENCES ==========
    
    @Test
    fun testExtremeScoreDifferences() {
        val userBody = EstimatedBodyMeasurements(
            chest = 96.0,
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap())
        )
        
        val sizeChart = listOf(
            SizeChartEntry("XS", chest = MeasurementRange(70.0, 74.0)),  // Very poor (score ~0.0)
            SizeChartEntry("S", chest = MeasurementRange(80.0, 84.0)),   // Poor (score ~0.2)
            SizeChartEntry("M", chest = MeasurementRange(94.0, 98.0)),   // Perfect (score ~0.95)
            SizeChartEntry("L", chest = MeasurementRange(110.0, 114.0)), // Very poor (score ~0.0)
            SizeChartEntry("XL", chest = MeasurementRange(120.0, 124.0))  // Very poor (score ~0.0)
        )
        
        val recommendation = SizeRecommendationEngine.recommendSizes(
            userBody, sizeChart, GarmentType.TSHIRT
        )
        
        // Should clearly recommend M
        assertEquals("Should recommend M", "M", recommendation.primaryRecommendation.sizeEntry.sizeLabel)
        
        // Should have very high confidence (clear winner)
        assertTrue("Should have high confidence", recommendation.overallConfidence > 0.85)
    }
    
    // ========== TEST 9: ALL GARMENT TYPES RECOMMENDATION ==========
    
    @Test
    fun testAllGarmentTypes_RecommendationAccuracy() {
        val userBody = EstimatedBodyMeasurements(
            chest = 96.0,
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap())
        )
        
        val sizeChart = listOf(
            SizeChartEntry("S", chest = MeasurementRange(88.0, 92.0)),
            SizeChartEntry("M", chest = MeasurementRange(94.0, 98.0)),
            SizeChartEntry("L", chest = MeasurementRange(100.0, 104.0))
        )
        
        val allGarmentTypes = GarmentType.values()
        var allAccurate = true
        val failures = mutableListOf<String>()
        
        allGarmentTypes.forEach { garmentType ->
            try {
                val recommendation = SizeRecommendationEngine.recommendSizes(
                    userBody, sizeChart, garmentType
                )
                
                val isValid = recommendation.primaryRecommendation != null &&
                             recommendation.overallConfidence >= 0.0 &&
                             recommendation.overallConfidence <= 1.0 &&
                             recommendation.primaryRecommendation.fitScore.overall >= 0.0 &&
                             recommendation.primaryRecommendation.fitScore.overall <= 1.0
                
                if (!isValid) {
                    allAccurate = false
                    failures.add("$garmentType: Invalid recommendation")
                }
            } catch (e: Exception) {
                allAccurate = false
                failures.add("$garmentType: ${e.message}")
            }
        }
        
        if (failures.isNotEmpty()) {
            println("=== Garment Type Failures ===")
            failures.forEach { println(it) }
        }
        
        assertTrue("All garment types should work", allAccurate)
    }
}

