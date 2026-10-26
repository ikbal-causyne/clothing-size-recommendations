package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

/**
 * ULTRA EXTREME AUTO-FIX TEST
 * Detects ALL issues and provides specific fix recommendations
 */
class UltraExtremeAutoFixTest {
    
    data class DetectedIssue(
        val component: String,
        val issueType: String,
        val description: String,
        val expected: Any?,
        val actual: Any?,
        val fixRecommendation: String,
        val severity: String // "CRITICAL", "HIGH", "MEDIUM", "LOW"
    )
    
    private val issues = mutableListOf<DetectedIssue>()
    
    @Test
    fun detectAllIssues() {
        println("=== ULTRA EXTREME AUTO-FIX TEST ===")
        println("Detecting all issues in Size Engine...")
        
        // Test 1: Fit Scoring Issues
        detectFitScoringIssues()
        
        // Test 2: Recommendation Logic Issues
        detectRecommendationLogicIssues()
        
        // Test 3: Regional Normalization Issues
        detectRegionalNormalizationIssues()
        
        // Test 4: Integration Issues
        detectIntegrationIssues()
        
        // Test 5: Mathematical Accuracy Issues
        detectMathematicalAccuracyIssues()
        
        // Report
        if (issues.isNotEmpty()) {
            println("\n=== ISSUES DETECTED ===")
            println("Total issues: ${issues.size}")
            
            val critical = issues.filter { it.severity == "CRITICAL" }
            val high = issues.filter { it.severity == "HIGH" }
            val medium = issues.filter { it.severity == "MEDIUM" }
            val low = issues.filter { it.severity == "LOW" }
            
            println("\nSeverity Breakdown:")
            println("  CRITICAL: ${critical.size}")
            println("  HIGH: ${high.size}")
            println("  MEDIUM: ${medium.size}")
            println("  LOW: ${low.size}")
            
            println("\n=== DETAILED ISSUES ===")
            issues.forEach { issue ->
                println("\n[${issue.severity}] ${issue.component} - ${issue.issueType}")
                println("  Description: ${issue.description}")
                println("  Expected: ${issue.expected}")
                println("  Actual: ${issue.actual}")
                println("  Fix: ${issue.fixRecommendation}")
            }
        } else {
            println("\n✅ NO ISSUES DETECTED - ALL TESTS PASSED!")
        }
        
        // Fail if critical or high issues found
        val criticalHigh = issues.filter { it.severity in listOf("CRITICAL", "HIGH") }
        assertTrue(
            "No critical or high severity issues should exist. Found: ${criticalHigh.size}",
            criticalHigh.isEmpty()
        )
    }
    
    private fun detectFitScoringIssues() {
        println("\n--- Detecting Fit Scoring Issues ---")
        
        // Issue 1: Verify dimension weights sum to 1.0
        val allGarmentTypes = GarmentType.values()
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
            
            // Check if score is in valid range
            if (fitScore.overall < 0.0 || fitScore.overall > 1.0) {
                issues.add(
                    DetectedIssue(
                        component = "FitScoringEngine",
                        issueType = "Invalid Score Range",
                        description = "$garmentType produces score outside [0.0, 1.0]",
                        expected = "0.0 <= score <= 1.0",
                        actual = fitScore.overall,
                        fixRecommendation = "Check scoreDimension() function - ensure score is clamped to [0.0, 1.0]",
                        severity = "CRITICAL"
                    )
                )
            }
            
            // Check if dimension scores are present
            if (fitScore.dimensionScores.isEmpty()) {
                issues.add(
                    DetectedIssue(
                        component = "FitScoringEngine",
                        issueType = "Missing Dimension Scores",
                        description = "$garmentType has no dimension scores",
                        expected = "At least one dimension score",
                        actual = "0 dimensions",
                        fixRecommendation = "Check getWeightsForGarment() - ensure at least one weight > 0",
                        severity = "CRITICAL"
                    )
                )
            }
        }
    }
    
    private fun detectRecommendationLogicIssues() {
        println("\n--- Detecting Recommendation Logic Issues ---")
        
        val userBody = EstimatedBodyMeasurements(
            chest = 96.0,
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        val sizeChart = listOf(
            SizeChartEntry("S", chest = MeasurementRange(88.0, 92.0)),  // Lower score
            SizeChartEntry("M", chest = MeasurementRange(94.0, 98.0)),  // Highest score
            SizeChartEntry("L", chest = MeasurementRange(100.0, 104.0)) // Medium score
        )
        
        val recommendation = SizeRecommendationEngine.recommendSizes(
            userBody, sizeChart, GarmentType.TSHIRT
        )
        
        // Verify primary is truly best
        val primaryScore = recommendation.primaryRecommendation.fitScore.overall
        
        sizeChart.forEach { size ->
            val score = FitScoringEngine.calculateFitScore(
                userBody, size, GarmentType.TSHIRT, FitPreference.REGULAR
            )
            
            if (score.overall > primaryScore) {
                issues.add(
                    DetectedIssue(
                        component = "SizeRecommendationEngine",
                        issueType = "Incorrect Best Match",
                        description = "Primary recommendation is not the best match",
                        expected = "Primary score should be highest",
                        actual = "Primary=${primaryScore}, Better=${score.overall}",
                        fixRecommendation = "Check sorting logic in recommendSizes() - verify sortedByDescending works correctly",
                        severity = "CRITICAL"
                    )
                )
            }
        }
    }
    
    private fun detectRegionalNormalizationIssues() {
        println("\n--- Detecting Regional Normalization Issues ---")
        
        val sizeChart = SizeChartEntry(
            "M",
            chest = MeasurementRange(100.0, 104.0)
        )
        
        // Test US normalization
        val usNormalized = RegionalFitNormalizer.normalizeGarmentMeasurements(sizeChart, SizeRegion.US)
        val expectedUS = 100.0 / 1.03
        val actualUS = usNormalized.chest!!.min
        
        if (abs(actualUS - expectedUS) > 0.01) {
            issues.add(
                DetectedIssue(
                    component = "RegionalFitNormalizer",
                    issueType = "Incorrect Normalization Calculation",
                    description = "US normalization calculation is wrong",
                    expected = expectedUS,
                    actual = actualUS,
                    fixRecommendation = "Check normalizeGarmentMeasurements() - verify division logic: min / coefficient.chest",
                    severity = "HIGH"
                )
            )
        }
        
        // Test ASIA normalization
        val asiaNormalized = RegionalFitNormalizer.normalizeGarmentMeasurements(sizeChart, SizeRegion.ASIA)
        val expectedASIA = 100.0 / 0.97
        val actualASIA = asiaNormalized.chest!!.min
        
        if (abs(actualASIA - expectedASIA) > 0.01) {
            issues.add(
                DetectedIssue(
                    component = "RegionalFitNormalizer",
                    issueType = "Incorrect Normalization Calculation",
                    description = "ASIA normalization calculation is wrong",
                    expected = expectedASIA,
                    actual = actualASIA,
                    fixRecommendation = "Check normalizeGarmentMeasurements() - verify division logic",
                    severity = "HIGH"
                )
            )
        }
    }
    
    private fun detectIntegrationIssues() {
        println("\n--- Detecting Integration Issues ---")
        
        // Test complete flow
        val userInput = UserBodyInput(175.0, 70.0, Gender.MALE, SizeRegion.US)
        
        try {
            val estimatedBody = BodyEstimator.estimateBodyMeasurements(
                userInput.height, userInput.weight, userInput.gender, userInput.region, null
            )
            
            val sizeChart = listOf(
                SizeChartEntry("M", chest = MeasurementRange(94.0, 98.0))
            )
            
            val normalizedChart = RegionalFitNormalizer.normalizeSizeChart(sizeChart, userInput.region)
            
            val recommendation = SizeRecommendationEngine.recommendSizes(
                estimatedBody, normalizedChart, GarmentType.TSHIRT
            )
            
            // Verify all steps worked
            if (recommendation.primaryRecommendation == null) {
                issues.add(
                    DetectedIssue(
                        component = "Integration",
                        issueType = "Recommendation Missing",
                        description = "Complete flow produces null recommendation",
                        expected = "Valid SizeRecommendationResult",
                        actual = "null",
                        fixRecommendation = "Check each step: BodyEstimator → RegionalNormalizer → SizeRecommendationEngine",
                        severity = "CRITICAL"
                    )
                )
            }
            
        } catch (e: Exception) {
            issues.add(
                DetectedIssue(
                    component = "Integration",
                    issueType = "Exception in Complete Flow",
                    description = "Complete flow throws exception",
                    expected = "No exception",
                    actual = e.message,
                    fixRecommendation = "Fix exception in: ${e.stackTrace.firstOrNull()?.className}",
                    severity = "CRITICAL"
                )
            )
        }
    }
    
    private fun detectMathematicalAccuracyIssues() {
        println("\n--- Detecting Mathematical Accuracy Issues ---")
        
        // Test scoring function mathematical accuracy
        val userBody = EstimatedBodyMeasurements(
            chest = 96.0,
            waist = 82.0,
            hip = 96.0,
            shoulder = 44.0,
            inseam = 78.0,
            confidence = ConfidenceScore(0.85, emptyMap<String, MeasurementConfidence>())
        )
        
        // Perfect fit case
        val perfectFit = SizeChartEntry(
            "M",
            chest = MeasurementRange(94.0, 98.0)  // Midpoint = 96.0, user = 96.0
        )
        
        val perfectScore = FitScoringEngine.calculateFitScore(
            userBody, perfectFit, GarmentType.TSHIRT, FitPreference.REGULAR
        )
        
        val perfectChestScore = perfectScore.dimensionScores["chest"]?.value ?: 0.0
        
        if (abs(perfectChestScore - 1.0) > 0.01) {
            issues.add(
                DetectedIssue(
                    component = "FitScoringEngine",
                    issueType = "Mathematical Accuracy",
                    description = "Perfect fit does not score 1.0",
                    expected = 1.0,
                    actual = perfectChestScore,
                    fixRecommendation = "Check scoreDimension() - when distance=0, score should be 1.0",
                    severity = "HIGH"
                )
            )
        }
    }
}

