package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import org.junit.Test
import org.junit.Assert.*

/**
 * Size Recommendation Validation Test - Extreme Testing Phase 1
 * Validates size recommendation engine per PRD Section 7.3
 */
class SizeRecommendationValidationTest {
    
    // Test Set 5.1: Store A (US Brand) - Loose Fit
    @Test
    fun testStoreA_USBrand() {
        val userBody = EstimatedBodyMeasurements(
            chest = 98.0,  // User's chest
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        val sizeChart = listOf(
            SizeChartEntry("S", chest = MeasurementRange(88.0, 92.0)),
            SizeChartEntry("M", chest = MeasurementRange(96.0, 100.0)),
            SizeChartEntry("L", chest = MeasurementRange(104.0, 108.0)),
            SizeChartEntry("XL", chest = MeasurementRange(112.0, 116.0))
        )
        
        val recommendation = SizeRecommendationEngine.recommendSizes(
            userBody, sizeChart, GarmentType.TSHIRT, FitPreference.REGULAR
        )
        
        // Expected: Should recommend M (98.0 is in 96-100 range)
        assertEquals(
            "Should recommend M for chest 98.0 in range 96-100",
            "M",
            recommendation.primaryRecommendation.sizeEntry.sizeLabel
        )
        
        // Expected: Confidence should be high (>80%)
        assertTrue(
            "Confidence should be >80%, got ${recommendation.overallConfidence}",
            recommendation.overallConfidence > 0.80
        )
    }
    
    // Test Set 5.2: Store B (Asian Brand) - Slim Fit
    @Test
    fun testStoreB_AsianBrand() {
        val userBody = EstimatedBodyMeasurements(
            chest = 98.0,  // Same user
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        // Asian brand has smaller sizes
        val sizeChart = listOf(
            SizeChartEntry("S", chest = MeasurementRange(84.0, 88.0)),
            SizeChartEntry("M", chest = MeasurementRange(90.0, 94.0)),
            SizeChartEntry("L", chest = MeasurementRange(96.0, 100.0)),
            SizeChartEntry("XL", chest = MeasurementRange(102.0, 106.0))
        )
        
        val recommendation = SizeRecommendationEngine.recommendSizes(
            userBody, sizeChart, GarmentType.TSHIRT, FitPreference.REGULAR
        )
        
        // Expected: Should recommend L (98.0 is in 96-100 range for L in Asian brand)
        assertEquals(
            "Should recommend L for chest 98.0 in Asian brand (cross-store variation)",
            "L",
            recommendation.primaryRecommendation.sizeEntry.sizeLabel
        )
        
        // This demonstrates cross-store variation handling
    }
    
    // Test Set 5.3: Between Sizes Scenario
    @Test
    fun testBetweenSizesDetection() {
        val userBody = EstimatedBodyMeasurements(
            chest = 99.0,  // Between M (94-98) and L (100-104)
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        val sizeChart = listOf(
            SizeChartEntry("M", chest = MeasurementRange(94.0, 98.0)),
            SizeChartEntry("L", chest = MeasurementRange(100.0, 104.0))
        )
        
        val recommendation = SizeRecommendationEngine.recommendSizes(
            userBody, sizeChart, GarmentType.TSHIRT, FitPreference.REGULAR
        )
        
        // Should detect between sizes
        assertNotNull(
            "Should detect between sizes scenario",
            recommendation.betweenSizesInfo
        )
        
        // Should provide guidance
        assertTrue(
            "Should provide guidance for between sizes",
            recommendation.betweenSizesInfo!!.guidance.isNotEmpty()
        )
        
        // Should show both sizes as alternatives
        assertTrue(
            "Should have alternative recommendations",
            recommendation.alternativeRecommendations.isNotEmpty()
        )
    }
    
    @Test
    fun testCrossStoreVariation() {
        val userBody = EstimatedBodyMeasurements(
            chest = 98.0,
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        // Store A (US brand - larger sizes)
        val storeA = listOf(
            SizeChartEntry("M", chest = MeasurementRange(96.0, 100.0)),
            SizeChartEntry("L", chest = MeasurementRange(104.0, 108.0))
        )
        val recommendationA = SizeRecommendationEngine.recommendSizes(
            userBody, storeA, GarmentType.TSHIRT
        )
        
        // Store B (Asian brand - smaller sizes)
        val storeB = listOf(
            SizeChartEntry("M", chest = MeasurementRange(90.0, 94.0)),
            SizeChartEntry("L", chest = MeasurementRange(96.0, 100.0))
        )
        val recommendationB = SizeRecommendationEngine.recommendSizes(
            userBody, storeB, GarmentType.TSHIRT
        )
        
        // Store A should recommend M, Store B should recommend L (for same user)
        assertEquals("Store A should recommend M", "M", recommendationA.primaryRecommendation.sizeEntry.sizeLabel)
        assertEquals("Store B should recommend L", "L", recommendationB.primaryRecommendation.sizeEntry.sizeLabel)
        
        // This proves cross-store variation is handled correctly
    }
}

