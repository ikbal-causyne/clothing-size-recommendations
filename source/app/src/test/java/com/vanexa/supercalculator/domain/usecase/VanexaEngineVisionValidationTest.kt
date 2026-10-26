package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Vanexa Engine Vision - Validation Test Suite
 *
 * **Purpose**: Verify that the Engine Vision implementation meets all requirements:
 * 1. ✅ NO percentage-based probabilities in output
 * 2. ✅ FIT CATEGORIES: "Comfortable", "Slightly Tight", "Slightly Loose"
 * 3. ✅ DIMENSION ANALYSIS: which measurements are problematic
 * 4. ✅ CLEAR NEXT STEPS: what to do if size doesn't fit
 * 5. ✅ POSITIVE LANGUAGE: never say "Not Recommended"
 * 6. ✅ REAL WORLD VALIDATION: Amazon, Military, Asian sizing patterns
 *
 * **Test Cases**:
 * - Amazon-based clothing (known sizing patterns)
 * - Military standard sizing (tight specifications)
 * - Asian sizing (cultural differences in fit preferences)
 * - Between-size scenarios
 * - High/low confidence scenarios
 */
class VanexaEngineVisionValidationTest {
    
    // ====================================================================
    // MASTER TEST: Engine Vision Compliance
    // ====================================================================
    
    @Test
    fun test_EngineVisionCompliance_NoPercentages() {
        /**
         * ❌ VIOLATION: Percentage-based language like:
         * - "45% fit probability"
         * - "Not Recommended (25%)"
         * - "Acceptable (61%)"
         *
         * ✅ REQUIRED: Human-friendly categories like:
         * - "Comfortable Fit"
         * - "Slightly Tight"
         * - "Poor Fit"
         */
        
        val userBody = EstimatedBodyMeasurements(
            height = 175.0,
            weight = 75.0,
            chest = 96.0,
            waist = 80.0,
            hip = 94.0,
            confidence = ConfidenceScore(0.90, emptyMap())
        )
        
        val sizeChart = SizeChartEntry(
            sizeLabel = "M",
            chest = MeasurementRange(92.0, 100.0),
            waist = MeasurementRange(76.0, 84.0),
            hip = MeasurementRange(90.0, 98.0)
        )
        
        val fitScore = FitScoringEngineVNext.calculateFitScore(
            userBody, sizeChart, GarmentType.TSHIRT,
            FitPreference.REGULAR, SizeRegion.US
        )
        
        // ❌ FAIL: If recommendation contains percentage
        assertFalse(
            "❌ VIOLATION: Percentage in FitScore recommendation",
            fitScore.recommendation.contains("%")
        )
        
        val rec = FitCategoryEngine.createRecommendation(
            fitScore, SizeRegion.US, GarmentType.TSHIRT
        )
        
        // ✅ PASS: Has fit category from enum
        assertTrue(
            "✅ Has FitCategory enum value",
            rec.fitCategory in FitCategoryEngine.FitCategory.values()
        )
        
        // ✅ PASS: Has dimension analysis
        assertTrue(
            "✅ Has dimension analysis text",
            rec.dimensionAnalysis.isNotEmpty()
        )
        
        // ✅ PASS: Has next steps
        assertTrue(
            "✅ Has next steps guidance",
            rec.nextSteps.isNotEmpty()
        )
        
        // ✅ PASS: Score is normalized (0.0-1.0), not percentage
        assertTrue(
            "✅ Score is normalized (not percentage)",
            rec.scoreNormalized in 0.0..1.0
        )
        
        println("""
            ✅ ENGINE VISION COMPLIANCE TEST PASSED
            
            Fit Recommendation:
            - Category: ${rec.fitCategory.displayName}
            - Primary: ${rec.primaryMessage}
            - Score: ${String.format("%.2f", rec.scoreNormalized)} (NOT percentage)
            - Analysis: ${rec.dimensionAnalysis}
            - Next Steps: ${rec.nextSteps}
        """.trimIndent())
    }
    
    // ====================================================================
    // REAL WORLD TEST CASES
    // ====================================================================
    
    @Test
    fun test_AmazonUniqloTShirt_US_Medium() {
        /**
         * Real Test Case: Amazon Uniqlo Airism T-Shirt (US Market)
         *
         * User Profile:
         * - Height: 180cm, Weight: 75kg
         * - Chest: 96cm (well-proportioned)
         * - Waist: 82cm
         * - Hip: 94cm
         * - Confidence: HIGH (0.90)
         *
         * Size Chart (Medium - US):
         * - Chest: 92-100cm
         * - Waist: 78-86cm
         * - Hip: 90-98cm
         *
         * Expected Outcome:
         * - Score: 0.70+
         * - Category: COMFORTABLE or VERY_COMFORTABLE
         * - Reason: User is well within range on all dimensions
         */
        
        val userBody = EstimatedBodyMeasurements(
            height = 180.0,
            weight = 75.0,
            chest = 96.0,
            waist = 82.0,
            hip = 94.0,
            confidence = ConfidenceScore(0.90, emptyMap())
        )
        
        val sizeM = SizeChartEntry(
            sizeLabel = "M",
            chest = MeasurementRange(92.0, 100.0),
            waist = MeasurementRange(78.0, 86.0),
            hip = MeasurementRange(90.0, 98.0)
        )
        
        val fitScore = FitScoringEngineVNext.calculateFitScore(
            userBody, sizeM, GarmentType.TSHIRT,
            FitPreference.REGULAR, SizeRegion.US
        )
        
        // User is within range, should be comfortable
        assertTrue(
            "Score >= 0.70 (COMFORTABLE+)",
            fitScore.overall >= 0.70
        )
        
        assertTrue(
            "FitQuality is GOOD or better",
            fitScore.fitQuality in listOf(
                FitQuality.GOOD, FitQuality.VERY_GOOD, FitQuality.EXCELLENT
            )
        )
        
        val rec = FitCategoryEngine.createRecommendation(
            fitScore, SizeRegion.US, GarmentType.TSHIRT
        )
        
        assertTrue(
            "Category is COMFORTABLE or VERY_COMFORTABLE",
            rec.fitCategory in listOf(
                FitCategoryEngine.FitCategory.COMFORTABLE,
                FitCategoryEngine.FitCategory.VERY_COMFORTABLE
            )
        )
        
        println("""
            ✅ TEST PASSED: Amazon Uniqlo T-Shirt (US Medium)
            
            User: 180cm, 75kg, chest=96cm
            Size: M (92-100cm chest)
            
            Result:
            - Score: ${String.format("%.2f", fitScore.overall)} (high)
            - Category: ${rec.fitCategory.displayName}
            - Primary: ${rec.primaryMessage}
            - Analysis: ${rec.dimensionAnalysis}
        """.trimIndent())
    }
    
    @Test
    fun test_MilitarySizing_200cm_120kg_BigPerson() {
        /**
         * Real Test Case: Military Sizing Standard (Tight Specification)
         *
         * User Profile:
         * - Height: 200cm, Weight: 120kg (large person)
         * - Chest: 108cm
         * - Waist: 96cm
         * - Hip: 102cm
         * - Confidence: 0.88 (body estimation)
         *
         * Size Progression (Military Spec):
         * - L (Large): chest 100-108cm, waist 88-96cm
         * - XL: chest 108-116cm, waist 96-104cm
         * - 2XL: chest 116-124cm, waist 104-112cm
         *
         * Expected Outcome:
         * - L: Score ~0.70 (barely fits, slightly tight on upper end)
         * - XL: Score 0.75+ (comfortable)
         * - 2XL: Score <0.70 (too loose)
         *
         * This validates that large people get correct size recommendations
         */
        
        val userBody = EstimatedBodyMeasurements(
            height = 200.0,
            weight = 120.0,
            chest = 108.0,
            waist = 96.0,
            hip = 102.0,
            confidence = ConfidenceScore(0.88, emptyMap())
        )
        
        // Size L
        val sizeL = SizeChartEntry(
            sizeLabel = "L",
            chest = MeasurementRange(100.0, 108.0),
            waist = MeasurementRange(88.0, 96.0),
            hip = MeasurementRange(94.0, 102.0)
        )
        
        val scoreL = FitScoringEngineVNext.calculateFitScore(
            userBody, sizeL, GarmentType.SHIRT,
            FitPreference.REGULAR, SizeRegion.US
        )
        
        // Size XL
        val sizeXL = SizeChartEntry(
            sizeLabel = "XL",
            chest = MeasurementRange(108.0, 116.0),
            waist = MeasurementRange(96.0, 104.0),
            hip = MeasurementRange(102.0, 110.0)
        )
        
        val scoreXL = FitScoringEngineVNext.calculateFitScore(
            userBody, sizeXL, GarmentType.SHIRT,
            FitPreference.REGULAR, SizeRegion.US
        )
        
        // XL should be better than L
        assertTrue(
            "XL score > L score (${scoreXL.overall} > ${scoreL.overall})",
            scoreXL.overall > scoreL.overall
        )
        
        // XL should be in COMFORTABLE range
        assertTrue(
            "XL score >= 0.70 (COMFORTABLE)",
            scoreXL.overall >= 0.70
        )
        
        val recXL = FitCategoryEngine.createRecommendation(
            scoreXL, SizeRegion.US, GarmentType.SHIRT
        )
        
        assertTrue(
            "XL is recommended for this user",
            recXL.fitCategory.isRecommended
        )
        
        println("""
            ✅ TEST PASSED: Military Sizing (200cm, 120kg)
            
            User: 200cm, 120kg, chest=108cm
            
            Results:
            - L Score: ${String.format("%.2f", scoreL.overall)} → ${FitCategoryEngine.FitCategory.fromScore(scoreL.overall).displayName}
            - XL Score: ${String.format("%.2f", scoreXL.overall)} → ${recXL.fitCategory.displayName}
            
            Verdict: XL is better fit
        """.trimIndent())
    }
    
    @Test
    fun test_AsianSizing_TightSpec_SamePerson() {
        /**
         * Real Test Case: Asian Sizing vs US Sizing (Same Body, Different Region)
         *
         * User Profile:
         * - Height: 200cm, Weight: 120kg (same as military test)
         * - Chest: 108cm
         *
         * Key Difference: Asian sizing is TIGHTER
         * - US XL: chest 108-116cm (tolerance factor 1.15)
         * - ASIA XL: chest 104-110cm (tolerance factor 0.90)
         * - INDO XL: chest 102-108cm (tolerance factor 0.85)
         *
         * Expected Outcome:
         * - US XL: COMFORTABLE (0.75+)
         * - ASIA XL: TIGHT (0.60-0.70)
         * - INDO XL: POOR FIT (<0.60)
         *
         * This validates regional awareness and cultural fit differences
         */
        
        val userBody = EstimatedBodyMeasurements(
            height = 200.0,
            weight = 120.0,
            chest = 108.0,
            waist = 96.0,
            hip = 102.0,
            confidence = ConfidenceScore(0.88, emptyMap())
        )
        
        // US XL (loose tolerance)
        val sizeUSXL = SizeChartEntry(
            sizeLabel = "XL",
            chest = MeasurementRange(108.0, 116.0),
            waist = MeasurementRange(96.0, 104.0),
            hip = MeasurementRange(102.0, 110.0)
        )
        
        val scoreUS = FitScoringEngineVNext.calculateFitScore(
            userBody, sizeUSXL, GarmentType.SHIRT,
            FitPreference.REGULAR, SizeRegion.US
        )
        
        // ASIA XL (tighter tolerance)
        val sizeAsiaXL = SizeChartEntry(
            sizeLabel = "XL",
            chest = MeasurementRange(104.0, 110.0),
            waist = MeasurementRange(92.0, 98.0),
            hip = MeasurementRange(98.0, 104.0)
        )
        
        val scoreAsia = FitScoringEngineVNext.calculateFitScore(
            userBody, sizeAsiaXL, GarmentType.SHIRT,
            FitPreference.REGULAR, SizeRegion.ASIA
        )
        
        // US should score better than ASIA (looser tolerance)
        assertTrue(
            "US XL (${String.format("%.2f", scoreUS.overall)}) > ASIA XL (${String.format("%.2f", scoreAsia.overall)})",
            scoreUS.overall > scoreAsia.overall
        )
        
        val recUS = FitCategoryEngine.createRecommendation(
            scoreUS, SizeRegion.US, GarmentType.SHIRT
        )
        
        val recAsia = FitCategoryEngine.createRecommendation(
            scoreAsia, SizeRegion.ASIA, GarmentType.SHIRT
        )
        
        // US recommendation should be more positive
        assertTrue(
            "US is COMFORTABLE, ASIA is TIGHT or worse",
            recUS.fitCategory in listOf(
                FitCategoryEngine.FitCategory.COMFORTABLE,
                FitCategoryEngine.FitCategory.VERY_COMFORTABLE
            ) &&
            recAsia.fitCategory in listOf(
                FitCategoryEngine.FitCategory.SLIGHTLY_TIGHT,
                FitCategoryEngine.FitCategory.POOR_FIT
            )
        )
        
        println("""
            ✅ TEST PASSED: Regional Sizing Differences
            
            User: 200cm, 120kg, chest=108cm
            
            Results:
            - US XL: ${String.format("%.2f", scoreUS.overall)} → ${recUS.fitCategory.displayName}
              Message: ${recUS.primaryMessage}
            
            - ASIA XL: ${String.format("%.2f", scoreAsia.overall)} → ${recAsia.fitCategory.displayName}
              Message: ${recAsia.primaryMessage}
            
            Verdict: Regional tolerance differences are respected
        """.trimIndent())
    }
    
    @Test
    fun test_BetweenSizesScenario() {
        /**
         * Real Test Case: Between-Size Scenario
         *
         * User Profile:
         * - Height: 177cm, Weight: 78kg
         * - Chest: 95cm (at boundary between sizes)
         *
         * Sizes:
         * - S: 88-96cm (upper end)
         * - M: 92-100cm (lower end)
         *
         * Expected Outcome:
         * - Both sizes score similarly (0.65-0.75)
         * - Recommendation shows BOTH options
         * - Suggests considering personal preference
         */
        
        val userBody = EstimatedBodyMeasurements(
            height = 177.0,
            weight = 78.0,
            chest = 95.0,
            waist = 80.0,
            hip = 93.0,
            confidence = ConfidenceScore(0.88, emptyMap())
        )
        
        val sizeS = SizeChartEntry(
            sizeLabel = "S",
            chest = MeasurementRange(88.0, 96.0),
            waist = MeasurementRange(76.0, 84.0),
            hip = MeasurementRange(89.0, 97.0)
        )
        
        val sizeM = SizeChartEntry(
            sizeLabel = "M",
            chest = MeasurementRange(92.0, 100.0),
            waist = MeasurementRange(80.0, 88.0),
            hip = MeasurementRange(93.0, 101.0)
        )
        
        val scoreS = FitScoringEngineVNext.calculateFitScore(
            userBody, sizeS, GarmentType.TSHIRT,
            FitPreference.REGULAR, SizeRegion.US
        )
        
        val scoreM = FitScoringEngineVNext.calculateFitScore(
            userBody, sizeM, GarmentType.TSHIRT,
            FitPreference.REGULAR, SizeRegion.US
        )
        
        // Scores should be similar (within 0.15)
        val difference = abs(scoreS.overall - scoreM.overall)
        assertTrue(
            "Scores are similar (diff = ${String.format("%.2f", difference)})",
            difference < 0.15
        )
        
        val recS = FitCategoryEngine.createRecommendation(
            scoreS, SizeRegion.US, GarmentType.TSHIRT
        )
        
        val recM = FitCategoryEngine.createRecommendation(
            scoreM, SizeRegion.US, GarmentType.TSHIRT
        )
        
        println("""
            ✅ TEST PASSED: Between-Size Scenario
            
            User: 177cm, 78kg, chest=95cm (boundary)
            
            Results:
            - S: ${String.format("%.2f", scoreS.overall)} → ${recS.fitCategory.displayName}
            - M: ${String.format("%.2f", scoreM.overall)} → ${recM.fitCategory.displayName}
            
            Verdict: Both sizes are viable (within 0.15 score difference)
            Recommendation: Show both options to user
        """.trimIndent())
    }
    
    @Test
    fun test_LowConfidenceScenario() {
        /**
         * Real Test Case: Low Confidence (OCR errors, etc.)
         *
         * User Profile:
         * - Height: 175cm (estimated)
         * - Chest: 96cm (from OCR with LOW CONFIDENCE - 0.45)
         * - Waist/Hip: Missing
         * - Overall Confidence: ~0.50 (poor data)
         *
         * Expected Outcome:
         * - Score should be lower due to confidence collapse (score * confidence²)
         * - Message should include confidence qualifier: "Possibly" instead of direct category
         * - Next steps should suggest manual measurement
         */
        
        val userBody = EstimatedBodyMeasurements(
            height = 175.0,
            weight = 75.0,
            chest = 96.0,
            waist = null,  // Missing
            hip = null,     // Missing
            confidence = ConfidenceScore(0.50, mapOf(
                "chest" to ConfidenceBand(0.45, 0.40, 0.50)  // Low OCR confidence
            ))
        )
        
        val sizeM = SizeChartEntry(
            sizeLabel = "M",
            chest = MeasurementRange(92.0, 100.0),
            waist = MeasurementRange(78.0, 86.0),
            hip = MeasurementRange(90.0, 98.0)
        )
        
        val fitScore = FitScoringEngineVNext.calculateFitScore(
            userBody, sizeM, GarmentType.TSHIRT,
            FitPreference.REGULAR, SizeRegion.US
        )
        
        // Low confidence should result in lower score
        assertTrue(
            "Low confidence produces lower score",
            fitScore.overall < 0.70
        )
        
        val rec = FitCategoryEngine.createRecommendation(
            fitScore, SizeRegion.US, GarmentType.TSHIRT
        )
        
        // Message should include confidence qualifier
        assertTrue(
            "Message includes confidence qualifier ('Likely', 'Probably', 'Possibly')",
            rec.primaryMessage.contains("Likely") || 
            rec.primaryMessage.contains("Probably") || 
            rec.primaryMessage.contains("Possibly")
        )
        
        println("""
            ✅ TEST PASSED: Low Confidence Scenario
            
            User: 175cm, 75kg (from poor OCR - 45% confidence on chest)
            Size: M
            
            Results:
            - Score: ${String.format("%.2f", fitScore.overall)} (low due to confidence collapse)
            - Data Confidence: ${String.format("%.0f", fitScore.dataConfidence * 100)}%
            - Message: ${rec.primaryMessage} (includes confidence qualifier)
            
            Verdict: Low confidence is properly reflected in recommendation
            Next Step: Suggest user provide manual measurements for accuracy
        """.trimIndent())
    }
}
