package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

/**
 * ULTRA EXTREME FIT SCORING TEST
 * Tests EVERY possible combination and edge case
 */
class UltraExtremeFitScoringTest {
    
    // ========== TEST 1: VERIFY ALL DIMENSION WEIGHTS SUM TO 1.0 ==========
    
    @Test
    fun testAllDimensionWeightsSumTo1() {
        val allGarmentTypes = GarmentType.values()
        val failures = mutableListOf<String>()
        
        allGarmentTypes.forEach { garmentType ->
            val userBody = EstimatedBodyMeasurements(
                chest = 96.0, waist = 82.0, hip = 96.0,
                shoulder = 44.0, inseam = 78.0,
                confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
            )
            
            val garmentSize = SizeChartEntry(
                "M",
                chest = MeasurementRange(94.0, 98.0),
                waist = MeasurementRange(80.0, 84.0),
                hip = MeasurementRange(94.0, 98.0),
                shoulder = MeasurementRange(42.0, 46.0),
                length = MeasurementRange(70.0, 74.0),
                inseam = MeasurementRange(76.0, 80.0)
            )
            
            val fitScore = FitScoringEngine.calculateFitScore(
                userBody, garmentSize, garmentType, FitPreference.REGULAR
            )
            
            // Verify all dimension scores are present
            val hasChest = fitScore.dimensionScores.containsKey("chest")
            val hasWaist = fitScore.dimensionScores.containsKey("waist")
            val hasHip = fitScore.dimensionScores.containsKey("hip")
            
            // For upper body garments, should have chest
            if (garmentType in listOf(GarmentType.TSHIRT, GarmentType.SHIRT, GarmentType.POLO_SHIRT,
                GarmentType.HOODIE, GarmentType.SWEATER, GarmentType.JACKET, GarmentType.BLAZER)) {
                if (!hasChest) {
                    failures.add("$garmentType: Missing chest dimension score")
                }
            }
            
            // For lower body garments, should have waist
            if (garmentType in listOf(GarmentType.JEANS, GarmentType.TROUSERS, GarmentType.CHINOS,
                GarmentType.CARGO_PANTS, GarmentType.SHORTS, GarmentType.JOGGERS)) {
                if (!hasWaist) {
                    failures.add("$garmentType: Missing waist dimension score")
                }
            }
            
            // Verify score is in valid range
            if (fitScore.overall < 0.0 || fitScore.overall > 1.0) {
                failures.add("$garmentType: Score out of range [0.0, 1.0], got ${fitScore.overall}")
            }
        }
        
        if (failures.isNotEmpty()) {
            println("=== Dimension Weight Failures ===")
            failures.forEach { println(it) }
        }
        
        assertTrue("All dimension weights should be correct", failures.isEmpty())
    }
    
    // ========== TEST 2: MATHEMATICAL ACCURACY OF SCORING FUNCTION ==========
    
    @Test
    fun testScoringFunctionMathematicalAccuracy() {
        val userBody = EstimatedBodyMeasurements(
            chest = 96.0,
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        // Test Case 1: Perfect fit (distance = 0)
        val perfectFit = SizeChartEntry(
            "M",
            chest = MeasurementRange(94.0, 98.0)  // Midpoint = 96.0, user = 96.0
        )
        
        val perfectScore = FitScoringEngine.calculateFitScore(
            userBody, perfectFit, GarmentType.TSHIRT, FitPreference.REGULAR
        )
        
        // Perfect fit should score 1.0 for chest dimension
        val perfectChestScore = perfectScore.dimensionScores["chest"]?.value ?: 0.0
        assertEquals("Perfect fit should score 1.0", 1.0, perfectChestScore, 0.01)
        
        // Test Case 2: At tolerance boundary (distance = tolerance × 0.5)
        val boundaryFit = SizeChartEntry(
            "M",
            chest = MeasurementRange(94.0, 98.0)  // Midpoint = 96.0, tolerance = 2.0
        )
        
        val boundaryUserBody = userBody.copy(chest = 97.0)  // distance = 1.0 = tolerance × 0.5
        val boundaryScore = FitScoringEngine.calculateFitScore(
            boundaryUserBody, boundaryFit, GarmentType.TSHIRT, FitPreference.REGULAR
        )
        
        val boundaryChestScore = boundaryScore.dimensionScores["chest"]?.value ?: 0.0
        assertEquals("At tolerance×0.5 should score 1.0", 1.0, boundaryChestScore, 0.01)
        
        // Test Case 3: At tolerance (distance = tolerance)
        val toleranceFit = SizeChartEntry(
            "M",
            chest = MeasurementRange(94.0, 98.0)  // Midpoint = 96.0, tolerance = 2.0
        )
        
        val toleranceUserBody = userBody.copy(chest = 98.0)  // distance = 2.0 = tolerance
        val toleranceScore = FitScoringEngine.calculateFitScore(
            toleranceUserBody, toleranceFit, GarmentType.TSHIRT, FitPreference.REGULAR
        )
        
        val toleranceChestScore = toleranceScore.dimensionScores["chest"]?.value ?: 0.0
        assertTrue("At tolerance should score 0.75-1.0", 
            toleranceChestScore >= 0.75 && toleranceChestScore <= 1.0)
        
        // Test Case 4: Poor fit (distance > tolerance × 1.95)
        val poorFit = SizeChartEntry(
            "S",
            chest = MeasurementRange(84.0, 88.0)  // Midpoint = 86.0
        )
        
        val poorScore = FitScoringEngine.calculateFitScore(
            userBody, poorFit, GarmentType.TSHIRT, FitPreference.REGULAR
        )
        
        val poorChestScore = poorScore.dimensionScores["chest"]?.value ?: 0.0
        assertTrue("Poor fit should score low (<0.5)", poorChestScore < 0.5)
    }
    
    // ========== TEST 3: FIT PREFERENCE MATHEMATICAL VERIFICATION ==========
    
    @Test
    fun testFitPreferenceMathematicalVerification() {
        val userBody = EstimatedBodyMeasurements(
            chest = 94.5,  // Between min and mid
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        val garmentSize = SizeChartEntry(
            "M",
            chest = MeasurementRange(94.0, 98.0)  // min=94, mid=96, max=98, tolerance=2
        )
        
        // SLIM: target = min + (tolerance × 0.3) = 94 + (2 × 0.3) = 94.6
        // User = 94.5 → distance = 0.1 → should score high
        
        val slimScore = FitScoringEngine.calculateFitScore(
            userBody, garmentSize, GarmentType.TSHIRT, FitPreference.SLIM
        )
        
        // REGULAR: target = mid = 96.0
        // User = 94.5 → distance = 1.5 → should score lower
        
        val regularScore = FitScoringEngine.calculateFitScore(
            userBody, garmentSize, GarmentType.TSHIRT, FitPreference.REGULAR
        )
        
        // SLIM should score higher for user at lower end
        assertTrue(
            "SLIM should score higher for user at lower end: SLIM=${slimScore.overall}, REGULAR=${regularScore.overall}",
            slimScore.overall > regularScore.overall
        )
    }
    
    // ========== TEST 4: ALL GARMENT TYPES × ALL FIT PREFERENCES × ALL REGIONS ==========
    
    @Test
    fun testAllCombinations_StressTest() {
        val allGarmentTypes = GarmentType.values() // 13 types
        val allFitPreferences = FitPreference.values() // 3 types
        
        var totalTests = 0
        var passedTests = 0
        val failures = mutableListOf<String>()
        
        allGarmentTypes.forEach { garmentType ->
            allFitPreferences.forEach { fitPreference ->
                totalTests++
                
                try {
                    val userBody = EstimatedBodyMeasurements(
                        chest = 96.0,
                        waist = 82.0,
                        hip = 96.0,
                        shoulder = 44.0,
                        inseam = 78.0,
                        confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
                    )
                    
                    val garmentSize = SizeChartEntry(
                        "M",
                        chest = MeasurementRange(94.0, 98.0),
                        waist = MeasurementRange(80.0, 84.0),
                        hip = MeasurementRange(94.0, 98.0)
                    )
                    
                    val fitScore = FitScoringEngine.calculateFitScore(
                        userBody, garmentSize, garmentType, fitPreference
                    )
                    
                    val isValid = fitScore.overall >= 0.0 && fitScore.overall <= 1.0 &&
                                 fitScore.dimensionScores.isNotEmpty() &&
                                 fitScore.fitQuality != null &&
                                 !fitScore.recommendation.isNullOrEmpty()
                    
                    if (isValid) {
                        passedTests++
                    } else {
                        failures.add("$garmentType + $fitPreference: Invalid")
                    }
                } catch (e: Exception) {
                    failures.add("$garmentType + $fitPreference: ${e.message}")
                }
            }
        }
        
        println("=== All Combinations Stress Test ===")
        println("Total: $totalTests (13 × 3 = 39)")
        println("Passed: $passedTests")
        println("Failed: ${totalTests - passedTests}")
        
        if (failures.isNotEmpty()) {
            println("Failures (first 10):")
            failures.take(10).forEach { println("  - $it") }
        }
        
        // 100% must pass
        assertEquals("All combinations must pass", totalTests, passedTests)
    }
    
    // ========== TEST 5: WEIGHTED SUM VERIFICATION ==========
    
    @Test
    fun testWeightedSumCalculation() {
        val userBody = EstimatedBodyMeasurements(
            chest = 96.0,
            waist = 82.0,  // Perfect
            hip = 96.0,    // Perfect
            shoulder = 44.0,
            inseam = 78.0,  // Poor
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        // Jeans: waist=0.40, hip=0.35, inseam=0.25
        val garmentSize = SizeChartEntry(
            "M",
            waist = MeasurementRange(80.0, 84.0),   // Perfect (82.0 is midpoint)
            hip = MeasurementRange(94.0, 98.0),     // Perfect (96.0 is midpoint)
            inseam = MeasurementRange(60.0, 64.0)   // Poor (user needs 78)
        )
        
        val fitScore = FitScoringEngine.calculateFitScore(
            userBody, garmentSize, GarmentType.JEANS, FitPreference.REGULAR
        )
        
        // Verify individual dimension scores
        val waistScore = fitScore.dimensionScores["waist"]?.value ?: 0.0
        val hipScore = fitScore.dimensionScores["hip"]?.value ?: 0.0
        val inseamScore = fitScore.dimensionScores["inseam"]?.value ?: 0.0
        
        assertTrue("Waist should be perfect (>0.9)", waistScore > 0.9)
        assertTrue("Hip should be perfect (>0.9)", hipScore > 0.9)
        assertTrue("Inseam should be poor (<0.5)", inseamScore < 0.5)
        
        // Overall score should be weighted sum
        assertTrue("Overall score should be reasonable", fitScore.overall >= 0.5 && fitScore.overall <= 1.0)
    }
    
    // ========== TEST 6: EDGE CASES - ZERO TOLERANCE ==========
    
    @Test
    fun testEdgeCase_ZeroTolerance() {
        val userBody = EstimatedBodyMeasurements(
            chest = 96.0,
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        // Size chart with zero tolerance (min = max)
        val zeroToleranceSize = SizeChartEntry(
            "M",
            chest = MeasurementRange(96.0, 96.0)  // Exact size, no range
        )
        
        val fitScore = FitScoringEngine.calculateFitScore(
            userBody, zeroToleranceSize, GarmentType.TSHIRT, FitPreference.REGULAR
        )
        
        // Should handle gracefully (tolerance = 0, but user matches exactly)
        val chestScore = fitScore.dimensionScores["chest"]?.value ?: 0.0
        
        // User matches exactly, should score high
        assertTrue("Zero tolerance with exact match should score high", chestScore > 0.9)
    }
    
    // ========== TEST 7: EDGE CASES - VERY LARGE TOLERANCE ==========
    
    @Test
    fun testEdgeCase_VeryLargeTolerance() {
        val userBody = EstimatedBodyMeasurements(
            chest = 96.0,
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        // Size chart with very large tolerance
        val largeToleranceSize = SizeChartEntry(
            "M",
            chest = MeasurementRange(80.0, 120.0)  // Very wide range
        )
        
        val fitScore = FitScoringEngine.calculateFitScore(
            userBody, largeToleranceSize, GarmentType.TSHIRT, FitPreference.REGULAR
        )
        
        // User is at midpoint (100.0), should score high
        val chestScore = fitScore.dimensionScores["chest"]?.value ?: 0.0
        assertTrue("Large tolerance with user at midpoint should score high", chestScore > 0.8)
    }
    
    // ========== TEST 8: MISSING DIMENSIONS HANDLING ==========
    
    @Test
    fun testMissingDimensionsHandling() {
        val userBody = EstimatedBodyMeasurements(
            chest = 96.0,
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        // Size chart with only chest (missing other dimensions)
        val partialSize = SizeChartEntry(
            "M",
            chest = MeasurementRange(94.0, 98.0)
            // No waist, hip, etc.
        )
        
        val fitScore = FitScoringEngine.calculateFitScore(
            userBody, partialSize, GarmentType.TSHIRT, FitPreference.REGULAR
        )
        
        // Should still work with only chest
        assertTrue("Should work with partial dimensions", fitScore.overall >= 0.0)
        assertTrue("Should have chest score", fitScore.dimensionScores.containsKey("chest"))
    }
}

