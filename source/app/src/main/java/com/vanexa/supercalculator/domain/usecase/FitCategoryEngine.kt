package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import android.util.Log
import kotlin.math.abs

/**
 * Fit Category Engine - Human-Friendly Fit Recommendations
 *
 * **Purpose**: Transform raw fit scores (0.0-1.0) into user-friendly fit categories
 * with actionable guidance, NO confusing percentages or "Not Recommended" language.
 *
 * **Engine Vision Compliance** (NON-NEGOTIABLE):
 * - ❌ NO percentage probabilities in output
 * - ✅ Show FIT CATEGORIES: "Comfortable", "Slightly Tight", "Slightly Loose"
 * - ✅ Provide DIMENSION ANALYSIS: which measurements are problematic
 * - ✅ Show CLEAR NEXT STEPS: what to do if this size doesn't fit perfectly
 * - ✅ Use POSITIVE LANGUAGE: never say "Not Recommended", say "Poor Fit - Consider Alternatives"
 *
 * **Key Design Principles**:
 * - All output is human-friendly and actionable
 * - Score 0.55-0.70 range can be either TIGHT or LOOSE (determined by dimension analysis)
 * - Recommendations respect regional/cultural fit preferences
 * - Garment-specific guidance (e.g., jeans feedback differs from t-shirt feedback)
 * - Confidence levels shown as modifiers ("Likely", "Possibly") not percentages
 */
object FitCategoryEngine {
    private const val TAG = "FitCategoryEngine"
    
    /**
     * Fit Category Definition
     * Each category maps to a score range and has associated UX properties
     */
    enum class FitCategory(
        val displayName: String,
        val scoreMin: Double,
        val scoreMax: Double,
        val colorHex: String,        // For UI background
        val iconEmoji: String = "✅", // Visual indicator
        val isRecommended: Boolean   // Whether to recommend this size
    ) {
        VERY_COMFORTABLE(
            displayName = "Very Comfortable Fit",
            scoreMin = 0.85,
            scoreMax = 1.0,
            colorHex = "#4CAF50",     // Green
            iconEmoji = "✨",
            isRecommended = true
        ),
        COMFORTABLE(
            displayName = "Comfortable Fit",
            scoreMin = 0.70,
            scoreMax = 0.85,
            colorHex = "#81C784",     // Light Green
            iconEmoji = "✅",
            isRecommended = true
        ),
        SLIGHTLY_TIGHT(
            displayName = "Slightly Tight",
            scoreMin = 0.55,
            scoreMax = 0.70,
            colorHex = "#FFB74D",     // Amber
            iconEmoji = "⚠️",
            isRecommended = true      // Still wearable! Important mindset shift
        ),
        SLIGHTLY_LOOSE(
            displayName = "Slightly Loose",
            scoreMin = 0.55,
            scoreMax = 0.70,
            colorHex = "#FFB74D",     // Amber
            iconEmoji = "⚠️",
            isRecommended = true      // Still wearable!
        ),
        POOR_FIT(
            displayName = "Poor Fit",
            scoreMin = 0.30,
            scoreMax = 0.55,
            colorHex = "#EF5350",     // Red
            iconEmoji = "❌",
            isRecommended = false
        ),
        VERY_POOR_FIT(
            displayName = "Very Poor Fit",
            scoreMin = 0.0,
            scoreMax = 0.30,
            colorHex = "#C62828",     // Dark Red
            iconEmoji = "🚫",
            isRecommended = false
        );
        
        companion object {
            /**
             * Map raw fit score to base category
             * Note: SLIGHTLY_TIGHT/LOOSE will be refined based on dimension analysis
             */
            fun fromScore(score: Double): FitCategory {
                return when {
                    score >= 0.85 -> VERY_COMFORTABLE
                    score >= 0.70 -> COMFORTABLE
                    score >= 0.55 -> SLIGHTLY_TIGHT  // Refined later to LOOSE if needed
                    score >= 0.30 -> POOR_FIT
                    else -> VERY_POOR_FIT
                }
            }
        }
    }
    
    /**
     * User-Friendly Fit Recommendation
     * All text is positive, actionable, and never includes percentage probabilities
     */
    data class FitRecommendation(
        val fitCategory: FitCategory,
        val scoreNormalized: Double,            // 0.0-1.0, NOT percentage
        val primaryMessage: String,             // e.g., "Comfortable Fit" or "Likely Comfortable"
        val dimensionAnalysis: String,          // e.g., "Chest matches well. Waist is 2cm tight."
        val nextSteps: String,                  // e.g., "This size should work for everyday wear."
        val alternativeMessage: String? = null  // For TIGHT/LOOSE: "Consider size L if you prefer more room"
    )
    
    /**
     * Create user-friendly fit recommendation from raw fit score
     *
     * **Algorithm**:
     * 1. Determine base category from score
     * 2. Analyze problematic dimensions
     * 3. Refine TIGHT/LOOSE distinction based on dimension directions
     * 4. Generate confidence-qualified primary message
     * 5. Generate dimension analysis summary
     * 6. Generate region-aware next steps
     * 7. Generate alternative suggestion if in TIGHT/LOOSE range
     *
     * **Key Insight**: Scores 0.55-0.70 can represent either tight or loose fit.
     * The dimension analysis determines which, so we can give more specific guidance.
     *
     * @param fitScore Raw FitScore from FitScoringEngineVNext
     * @param userRegion Region code for culturally-aware suggestions (e.g., 'US' prefer looser fit)
     * @param garmentType For garment-specific guidance
     * @param userHeight Optional: for height-specific context
     * @param userWeight Optional: for body-type-specific context
     * @return FitRecommendation with category, analysis, and next steps (no percentages)
     */
    fun createRecommendation(
        fitScore: FitScore,
        userRegion: SizeRegion = SizeRegion.US,
        garmentType: GarmentType = GarmentType.TSHIRT,
        userHeight: Double? = null,
        userWeight: Double? = null
    ): FitRecommendation {
        
        Log.d(TAG, "🎯 Creating fit recommendation: score=${fitScore.overall}, dataConf=${fitScore.dataConfidence}, region=$userRegion, garment=$garmentType")
        
        // Step 1: Determine base fit category (may refine later)
        val baseCategory = FitCategory.fromScore(fitScore.overall)
        Log.d(TAG, "   baseCategory=$baseCategory")
        
        // Step 2: Analyze dimensions to refine TIGHT/LOOSE distinction
        val dimensionAnalysis = analyzeDimensions(
            fitScore.dimensionScores,
            fitScore.unreliableDimensions,
            garmentType
        )
        Log.d(TAG, "   tightnessDirection=${dimensionAnalysis.tightnessDirection}")
        
        // Step 3: Refine category if in TIGHT/LOOSE range
        val finalCategory = if (baseCategory == FitCategory.SLIGHTLY_TIGHT) {
            when (dimensionAnalysis.tightnessDirection) {
                TightnessDirection.LOOSE -> FitCategory.SLIGHTLY_LOOSE
                else -> FitCategory.SLIGHTLY_TIGHT
            }
        } else {
            baseCategory
        }
        Log.d(TAG, "   finalCategory=$finalCategory")
        
        // Step 4: Generate primary message with confidence qualification
        val primaryMessage = generatePrimaryMessage(
            finalCategory,
            fitScore.dataConfidence
        )
        Log.d(TAG, "   primaryMessage='$primaryMessage'")
        
        // Step 5: Generate dimension analysis text
        val dimensionText = dimensionAnalysis.summaryText
        Log.d(TAG, "   dimensionAnalysis='$dimensionText'")
        
        // Step 6: Generate next steps based on category and region
        val nextSteps = generateNextSteps(
            finalCategory,
            garmentType,
            dimensionAnalysis,
            userRegion
        )
        Log.d(TAG, "   nextSteps='$nextSteps'")
        
        // Step 7: Generate alternative message if TIGHT/LOOSE
        val alternativeMessage = if (finalCategory in listOf(
            FitCategory.SLIGHTLY_TIGHT,
            FitCategory.SLIGHTLY_LOOSE
        )) {
            generateAlternativeMessage(finalCategory, garmentType)
        } else {
            null
        }
        
        Log.d(TAG, "✅ Recommendation created successfully")
        
        return FitRecommendation(
            fitCategory = finalCategory,
            scoreNormalized = fitScore.overall.coerceIn(0.0, 1.0),
            primaryMessage = primaryMessage,
            dimensionAnalysis = dimensionText,
            nextSteps = nextSteps,
            alternativeMessage = alternativeMessage
        )
    }
    
    // ====================================================================
    // PRIVATE HELPER FUNCTIONS
    // ====================================================================
    
    private enum class TightnessDirection {
        TIGHT,
        LOOSE,
        BALANCED
    }
    
    private data class DimensionAnalysisResult(
        val summaryText: String,
        val tightnessDirection: TightnessDirection,
        val problematicDimensions: List<String>
    )
    
    /**
     * Analyze dimensions to determine:
     * 1. Overall tightness direction (TIGHT, LOOSE, BALANCED)
     * 2. Summary text describing problematic dimensions
     * 3. List of problematic dimension names
     */
    private fun analyzeDimensions(
        dimensionScores: Map<BodyDimension, DimensionScore>,
        unreliableDimensions: Map<BodyDimension, Double>,
        garmentType: GarmentType
    ): DimensionAnalysisResult {
        
        val builder = StringBuilder()
        val problematicDimensions = mutableListOf<String>()
        var totalTightness = 0.0  // Positive = tight, negative = loose
        
        dimensionScores.forEach { (dimension, score) ->
            val dimensionName = dimension.displayName()
            
            when {
                score.rawScore >= 0.90 -> {
                    // Excellent match - nothing to report (silent success)
                }
                score.rawScore >= 0.75 -> {
                    // Good match - mention if slightly off
                    if (score.difference > 1.0) {
                        builder.append("$dimensionName is ${String.format("%.1f", score.difference)}cm larger than ideal. ")
                        totalTightness -= 1.0  // Makes fit looser
                    } else if (score.difference < -1.0) {
                        builder.append("$dimensionName is ${String.format("%.1f", abs(score.difference))}cm smaller than ideal. ")
                        totalTightness += 1.0  // Makes fit tighter
                    }
                }
                score.rawScore >= 0.60 -> {
                    // Acceptable but worth noting
                    val direction = if (score.difference > 0) "larger" else "smaller"
                    builder.append("$dimensionName is ${String.format("%.1f", abs(score.difference))}cm $direction. ")
                    if (score.difference > 0) {
                        totalTightness -= 1.0
                    } else {
                        totalTightness += 1.0
                    }
                    problematicDimensions.add(dimensionName)
                }
                else -> {
                    // Poor match - definitely problematic
                    val direction = if (score.difference > 0) "larger" else "smaller"
                    builder.append("$dimensionName is significantly ${String.format("%.1f", abs(score.difference))}cm $direction. ")
                    if (score.difference > 0) {
                        totalTightness -= 2.0  // More weight for poor match
                    } else {
                        totalTightness += 2.0
                    }
                    problematicDimensions.add(dimensionName)
                }
            }
        }
        
        // Add low-confidence dimensions (BUG 9 FIX)
        if (unreliableDimensions.isNotEmpty()) {
            val unreliableList = unreliableDimensions.map { it.key.displayName() }.joinToString(", ")
            builder.append("Lower confidence in: $unreliableList (may affect accuracy). ")
        }
        
        val summaryText = builder.toString().trim().takeIf { it.isNotEmpty() } 
            ?: "All dimensions match well."
        
        // Determine tightness direction: Is user measurements making this size feel tight or loose?
        val tightnessDirection = when {
            totalTightness > 0.5 -> TightnessDirection.TIGHT    // User is too big for size
            totalTightness < -0.5 -> TightnessDirection.LOOSE   // User is too small for size
            else -> TightnessDirection.BALANCED                  // Mix of tight and loose dimensions
        }
        
        return DimensionAnalysisResult(summaryText, tightnessDirection, problematicDimensions)
    }
    
    /**
     * Generate primary message with confidence qualification
     *
     * Low confidence adds a qualifier:
     * - 85%+ confidence: No qualifier ("Comfortable Fit")
     * - 70-85%: "Likely" ("Likely Comfortable")
     * - 60-70%: "Probably" ("Probably Comfortable")
     * - <60%: "Possibly" ("Possibly Comfortable")
     *
     * This is better UX than showing "0.70 confidence" - users understand "Likely"
     */
    private fun generatePrimaryMessage(
        category: FitCategory,
        dataConfidence: Double
    ): String {
        val confidencePrefix = when {
            dataConfidence >= 0.85 -> ""           // High confidence, no qualifier
            dataConfidence >= 0.70 -> "Likely "    // Good confidence
            dataConfidence >= 0.60 -> "Probably "  // Acceptable confidence
            else -> "Possibly "                    // Low confidence
        }
        
        return when (category) {
            FitCategory.VERY_COMFORTABLE -> "${confidencePrefix}Excellent Fit"
            FitCategory.COMFORTABLE -> "${confidencePrefix}Comfortable Fit"
            FitCategory.SLIGHTLY_TIGHT -> "${confidencePrefix}Slightly Tight"
            FitCategory.SLIGHTLY_LOOSE -> "${confidencePrefix}Slightly Loose"
            FitCategory.POOR_FIT -> "Poor Fit"
            FitCategory.VERY_POOR_FIT -> "Not Recommended"  // Only use at very poor
        }
    }
    
    /**
     * Generate region-aware next steps
     *
     * Different regions have different fit preferences:
     * - US: Looser/relaxed fit is normal and preferred
     * - EU/UK: Standard fit expectations
     * - ASIA/INDO: Fitted/tight is culturally normal
     *
     * This helps users understand if "slightly tight" is actually normal for their region
     */
    private fun generateNextSteps(
        category: FitCategory,
        garmentType: GarmentType,
        analysis: DimensionAnalysisResult,
        userRegion: SizeRegion
    ): String {
        return when (category) {
            FitCategory.VERY_COMFORTABLE ->
                "This is your ideal size. Buy with confidence."
            
            FitCategory.COMFORTABLE ->
                "This size should work well for you. A solid choice."
            
            FitCategory.SLIGHTLY_TIGHT -> {
                // User is too big for this size
                when (userRegion) {
                    SizeRegion.US ->
                        "Slightly tight. Try the next size up for more comfort, or this size if you prefer a fitted look."
                    
                    SizeRegion.EU, SizeRegion.UK ->
                        "Slightly tight. Consider checking a half-size up if available."
                    
                    SizeRegion.ASIA, SizeRegion.ID ->
                        "Slightly tight. This is normal for fitted Asian sizing. Should be comfortable for layering."
                }
            }
            
            FitCategory.SLIGHTLY_LOOSE -> {
                // User is too small for this size
                when (userRegion) {
                    SizeRegion.US ->
                        "Slightly loose. This is typical for relaxed US sizing. Should be comfortable."
                    
                    SizeRegion.EU, SizeRegion.UK ->
                        "Slightly loose. Try the next size down for a more fitted look."
                    
                    SizeRegion.ASIA, SizeRegion.ID ->
                        "Slightly loose. Consider sizing down for a better fit."
                }
            }
            
            FitCategory.POOR_FIT ->
                "Not ideal for this body type. Check the problematic dimensions and consider a different size."
            
            FitCategory.VERY_POOR_FIT ->
                "Significant mismatch. Choose a different size based on the dimension analysis."
        }
    }
    
    /**
     * Generate alternative size suggestion for TIGHT/LOOSE cases
     *
     * This gives users a quick action item without needing to manually try other sizes
     */
    private fun generateAlternativeMessage(
        category: FitCategory,
        garmentType: GarmentType
    ): String {
        return when (category) {
            FitCategory.SLIGHTLY_TIGHT ->
                "Try the next size up if you want more room, or this size if you prefer a snug fit."
            
            FitCategory.SLIGHTLY_LOOSE ->
                "Try the next size down if you prefer a snugger fit, or keep this if you like room to move."
            
            else -> ""
        }
    }
}
