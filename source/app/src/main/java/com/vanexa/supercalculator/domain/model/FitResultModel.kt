package com.vanexa.supercalculator.domain.model

import com.vanexa.supercalculator.domain.usecase.FitCategoryEngine
import java.io.Serializable

/**
 * Complete fit analysis result
 * Contains dimension-level scores and user-friendly recommendations
 * This is the output of Phase 5: calculateFitScore orchestration
 */
data class FitResult(
    val overallFitCategory: FitCategoryEngine.FitCategory,  // User-friendly category
    val overallScore: Double,                 // 0.0-1.0 weighted fit score
    val dimensionFits: List<DimensionFit>,    // Individual dimension analysis
    val recommendation: String,               // "This size is Perfect", "This is tight", etc.
    val dimensionAnalysis: String,            // "Chest: Fitted (0.9cm extra), Waist: Perfect"
    val nextSteps: List<String>               // ["Try size L", "Check shoulder fit"]
) : Serializable
