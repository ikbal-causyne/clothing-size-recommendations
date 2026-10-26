package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import org.junit.Test
import org.junit.Assert.*

/**
 * ULTRA EXTREME INTEGRATION TEST
 * Tests complete flow with extreme scenarios
 */
class UltraExtremeIntegrationTest {
    
    // ========== TEST 1: COMPLETE FLOW - ALL COMBINATIONS ==========
    
    @Test
    fun testCompleteFlow_AllCombinations() {
        val heights = listOf(150.0, 160.0, 170.0, 175.0, 180.0, 190.0)
        val weights = listOf(50.0, 60.0, 70.0, 80.0, 90.0, 100.0)
        val genders = listOf(Gender.MALE, Gender.FEMALE)
        val regions = SizeRegion.values()
        val garmentTypes = GarmentType.values()
        
        var totalTests = 0
        var passedTests = 0
        val failures = mutableListOf<String>()
        
        heights.forEach { height ->
            weights.forEach { weight ->
                genders.forEach { gender ->
                    regions.forEach { region ->
                        garmentTypes.forEach { garmentType ->
                            totalTests++
                            
                            try {
                                // Step 1: Estimate body
                                val estimatedBody = BodyEstimator.estimateBodyMeasurements(
                                    height, weight, gender, region, null
                                )
                                
                                // Step 2: Size chart
                                val sizeChart = listOf(
                                    SizeChartEntry("M", chest = MeasurementRange(90.0, 100.0))
                                )
                                
                                // Step 3: Normalize
                                val normalizedChart = RegionalFitNormalizer.normalizeSizeChart(sizeChart, region)
                                
                                // Step 4: Get recommendation
                                val recommendation = SizeRecommendationEngine.recommendSizes(
                                    estimatedBody,
                                    normalizedChart,
                                    garmentType
                                )
                                
                                // Step 5: Verify
                                val isValid = recommendation.primaryRecommendation != null &&
                                             recommendation.overallConfidence >= 0.0 &&
                                             recommendation.overallConfidence <= 1.0
                                
                                if (isValid) {
                                    passedTests++
                                } else {
                                    failures.add("H=$height, W=$weight, G=$gender, R=$region, GT=$garmentType")
                                }
                            } catch (e: Exception) {
                                failures.add("H=$height, W=$weight, G=$gender, R=$region, GT=$garmentType: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
        
        println("=== Complete Flow Stress Test ===")
        println("Total: $totalTests (6×6×2×5×13 = 4,680)")
        println("Passed: $passedTests")
        println("Failed: ${totalTests - passedTests}")
        
        if (failures.isNotEmpty() && failures.size <= 10) {
            println("Failures:")
            failures.forEach { println("  - $it") }
        }
        
        // 99%+ must pass
        val passRate = (passedTests.toDouble() / totalTests) * 100.0
        assertTrue(
            "Pass rate should be >=99%, got ${String.format("%.2f", passRate)}%",
            passRate >= 99.0
        )
    }
    
    // ========== TEST 2: REAL-WORLD SCENARIOS ==========
    
    @Test
    fun testRealWorldScenarios() {
        val scenarios = listOf(
            // Scenario 1: Average US Male buying T-Shirt
            Triple(
                UserBodyInput(175.0, 70.0, Gender.MALE, SizeRegion.US),
                listOf(
                    SizeChartEntry("S", chest = MeasurementRange(88.0, 92.0)),
                    SizeChartEntry("M", chest = MeasurementRange(96.0, 100.0)),
                    SizeChartEntry("L", chest = MeasurementRange(104.0, 108.0))
                ),
                GarmentType.TSHIRT
            ),
            // Scenario 2: Average Asian Female buying Jeans
            Triple(
                UserBodyInput(160.0, 55.0, Gender.FEMALE, SizeRegion.ASIA),
                listOf(
                    SizeChartEntry("S", waist = MeasurementRange(68.0, 72.0), hip = MeasurementRange(88.0, 92.0)),
                    SizeChartEntry("M", waist = MeasurementRange(74.0, 78.0), hip = MeasurementRange(94.0, 98.0)),
                    SizeChartEntry("L", waist = MeasurementRange(80.0, 84.0), hip = MeasurementRange(100.0, 104.0))
                ),
                GarmentType.JEANS
            ),
            // Scenario 3: Indonesian Male buying Shirt
            Triple(
                UserBodyInput(168.0, 65.0, Gender.MALE, SizeRegion.INDONESIA),
                listOf(
                    SizeChartEntry("M", chest = MeasurementRange(92.0, 96.0), shoulder = MeasurementRange(42.0, 46.0)),
                    SizeChartEntry("L", chest = MeasurementRange(98.0, 102.0), shoulder = MeasurementRange(44.0, 48.0))
                ),
                GarmentType.SHIRT
            )
        )
        
        scenarios.forEachIndexed { index, (userInput, sizeChart, garmentType) ->
            try {
                // Complete flow
                val estimatedBody = BodyEstimator.estimateBodyMeasurements(
                    userInput.height,
                    userInput.weight,
                    userInput.gender,
                    userInput.region,
                    null
                )
                
                val normalizedChart = RegionalFitNormalizer.normalizeSizeChart(sizeChart, userInput.region)
                
                val recommendation = SizeRecommendationEngine.recommendSizes(
                    estimatedBody,
                    normalizedChart,
                    garmentType
                )
                
                // Verify
                assertNotNull("Scenario ${index + 1}: Should have recommendation", recommendation.primaryRecommendation)
                assertTrue("Scenario ${index + 1}: Confidence should be valid", 
                    recommendation.overallConfidence >= 0.0 && recommendation.overallConfidence <= 1.0)
                
            } catch (e: Exception) {
                fail("Scenario ${index + 1} failed: ${e.message}")
            }
        }
    }
    
    // ========== TEST 3: OPTIONAL MEASUREMENTS IMPACT ==========
    
    @Test
    fun testOptionalMeasurementsImpactOnRecommendation() {
        val userInput = UserBodyInput(175.0, 70.0, Gender.MALE, SizeRegion.US)
        
        val sizeChart = listOf(
            SizeChartEntry("S", chest = MeasurementRange(88.0, 92.0)),
            SizeChartEntry("M", chest = MeasurementRange(94.0, 98.0)),
            SizeChartEntry("L", chest = MeasurementRange(100.0, 104.0))
        )
        
        // Without optional measurements
        val withoutOptional = BodyEstimator.estimateBodyMeasurements(
            userInput.height, userInput.weight, userInput.gender, userInput.region, null
        )
        val normalizedChart = RegionalFitNormalizer.normalizeSizeChart(sizeChart, userInput.region)
        val recWithout = SizeRecommendationEngine.recommendSizes(
            withoutOptional, normalizedChart, GarmentType.TSHIRT
        )
        
        // With optional measurements
        val withOptional = OptionalMeasurements(chestCircumference = 98.0)
        val withOptionalBody = BodyEstimator.estimateBodyMeasurements(
            userInput.height, userInput.weight, userInput.gender, userInput.region, withOptional
        )
        val recWith = SizeRecommendationEngine.recommendSizes(
            withOptionalBody, normalizedChart, GarmentType.TSHIRT
        )
        
        // With optional measurements, confidence should be higher
        assertTrue(
            "With optional measurements, confidence should be higher",
            recWith.overallConfidence >= recWithout.overallConfidence
        )
        
        // With optional measurements, recommendation should be more accurate
        val withSize = recWith.primaryRecommendation.sizeEntry.sizeLabel
        assertTrue("With optional, should recommend M or L", withSize in listOf("M", "L"))
    }
}

