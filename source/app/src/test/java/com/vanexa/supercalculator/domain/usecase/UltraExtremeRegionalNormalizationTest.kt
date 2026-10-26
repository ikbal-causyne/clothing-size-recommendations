package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

/**
 * ULTRA EXTREME REGIONAL NORMALIZATION TEST
 * Tests regional normalization with mathematical precision
 */
class UltraExtremeRegionalNormalizationTest {
    
    // ========== TEST 1: NORMALIZATION MATHEMATICAL VERIFICATION ==========
    
    @Test
    fun testNormalizationMathematicalVerification() {
        val sizeChart = SizeChartEntry(
            "M",
            chest = MeasurementRange(94.0, 98.0),
            waist = MeasurementRange(80.0, 84.0),
            hip = MeasurementRange(96.0, 100.0)
        )
        
        // Test US normalization (divide by 1.03, 1.04, 1.02)
        val usNormalized = RegionalFitNormalizer.normalizeGarmentMeasurements(sizeChart, SizeRegion.US)
        
        assertEquals("US chest min", 94.0 / 1.03, usNormalized.chest!!.min, 0.01)
        assertEquals("US chest max", 98.0 / 1.03, usNormalized.chest!!.max, 0.01)
        assertEquals("US waist min", 80.0 / 1.04, usNormalized.waist!!.min, 0.01)
        assertEquals("US waist max", 84.0 / 1.04, usNormalized.waist!!.max, 0.01)
        assertEquals("US hip min", 96.0 / 1.02, usNormalized.hip!!.min, 0.01)
        assertEquals("US hip max", 100.0 / 1.02, usNormalized.hip!!.max, 0.01)
        
        // Test ASIA normalization (divide by 0.97, 0.95, 0.97)
        val asiaNormalized = RegionalFitNormalizer.normalizeGarmentMeasurements(sizeChart, SizeRegion.ASIA)
        
        assertEquals("ASIA chest min", 94.0 / 0.97, asiaNormalized.chest!!.min, 0.01)
        assertEquals("ASIA chest max", 98.0 / 0.97, asiaNormalized.chest!!.max, 0.01)
        assertEquals("ASIA waist min", 80.0 / 0.95, asiaNormalized.waist!!.min, 0.01)
        assertEquals("ASIA waist max", 84.0 / 0.95, asiaNormalized.waist!!.max, 0.01)
        assertEquals("ASIA hip min", 96.0 / 0.97, asiaNormalized.hip!!.min, 0.01)
        assertEquals("ASIA hip max", 100.0 / 0.97, asiaNormalized.hip!!.max, 0.01)
        
        // Verify US makes smaller (for comparison), ASIA makes larger
        assertTrue("US should make smaller", usNormalized.chest!!.min < sizeChart.chest!!.min)
        assertTrue("ASIA should make larger", asiaNormalized.chest!!.min > sizeChart.chest!!.min)
    }
    
    // ========== TEST 2: NORMALIZATION IMPACT ON RECOMMENDATION ==========
    
    @Test
    fun testNormalizationImpactOnRecommendation() {
        val userBody = EstimatedBodyMeasurements(
            chest = 98.0,
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
                confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        val baseSizeChart = listOf(
            SizeChartEntry("M", chest = MeasurementRange(96.0, 100.0))
        )
        
        // Test with US normalization
        val usNormalized = RegionalFitNormalizer.normalizeSizeChart(baseSizeChart, SizeRegion.US)
        val usRec = SizeRecommendationEngine.recommendSizes(
            userBody, usNormalized, GarmentType.TSHIRT
        )
        
        // Test with ASIA normalization
        val asiaNormalized = RegionalFitNormalizer.normalizeSizeChart(baseSizeChart, SizeRegion.ASIA)
        val asiaRec = SizeRecommendationEngine.recommendSizes(
            userBody, asiaNormalized, GarmentType.TSHIRT
        )
        
        // US normalization makes chart smaller → user fits better → higher score
        // ASIA normalization makes chart larger → user fits worse → lower score
        
        val usScore = usRec.primaryRecommendation.fitScore.overall
        val asiaScore = asiaRec.primaryRecommendation.fitScore.overall
        
        // US should score higher (normalized chart is smaller, user fits better)
        assertTrue(
            "US normalization should produce higher score: US=$usScore, ASIA=$asiaScore",
            usScore >= asiaScore
        )
    }
    
    // ========== TEST 3: ALL REGIONS NORMALIZATION ==========
    
    @Test
    fun testAllRegionsNormalization() {
        val sizeChart = SizeChartEntry(
            "M",
            chest = MeasurementRange(100.0, 104.0)
        )
        
        val allRegions = SizeRegion.values()
        val normalizedResults = mutableMapOf<SizeRegion, Double>()
        
        allRegions.forEach { region ->
            val normalized = RegionalFitNormalizer.normalizeGarmentMeasurements(sizeChart, region)
            normalizedResults[region] = normalized.chest!!.min
        }
        
        // Verify EU is baseline (1.00 = no change)
        val euResult = normalizedResults[SizeRegion.EU]!!
        assertEquals("EU should be baseline", 100.0, euResult, 0.01)
        
        // Verify US is smaller (divide by 1.03)
        val usResult = normalizedResults[SizeRegion.US]!!
        assertTrue("US should be smaller than EU", usResult < euResult)
        
        // Verify ASIA is larger (divide by 0.97)
        val asiaResult = normalizedResults[SizeRegion.ASIA]!!
        assertTrue("ASIA should be larger than EU", asiaResult > euResult)
        
        // Verify INDONESIA is larger than ASIA (divide by 0.96 vs 0.97)
        val indoResult = normalizedResults[SizeRegion.INDONESIA]!!
        assertTrue("INDONESIA should be larger than ASIA", indoResult > asiaResult)
    }
}

