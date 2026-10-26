package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import android.util.Log

/**
 * DYNAMIC THRESHOLD CALCULATOR per Engine Vision Update
 * Replaces static thresholds with dynamic thresholds based on:
 * - GarmentType
 * - Dimension sensitivity
 * - Fabric behavior (future)
 */
object DynamicThresholdCalculator {
    private const val TAG = "DynamicThresholdCalculator"
    
    /**
     * Dynamic thresholds configuration
     */
    data class DynamicThresholds(
        val excellentThreshold: Double,      // Score threshold for EXCELLENT
        val veryGoodThreshold: Double,       // Score threshold for VERY_GOOD
        val goodThreshold: Double,           // Score threshold for GOOD
        val acceptableThreshold: Double,     // Score threshold for ACCEPTABLE
        val marginalThreshold: Double,       // Score threshold for MARGINAL
        val alternativeTolerance: Double,    // Score difference for alternatives
        val betweenSizesTolerance: Double,   // Score difference for "between sizes"
        val dimensionSensitivity: Map<String, Double>  // Per-dimension sensitivity multipliers
    )
    
    /**
     * Calculate dynamic thresholds for given garment type
     */
    fun calculateThresholds(garmentType: GarmentType): DynamicThresholds {
        // Base thresholds (more lenient for forgiving garments, stricter for fitted)
        val baseThresholds = when (garmentType) {
            // Upper body - fitted garments (stricter)
            GarmentType.SHIRT, GarmentType.BLAZER -> DynamicThresholds(
                excellentThreshold = 0.92,
                veryGoodThreshold = 0.82,
                goodThreshold = 0.72,
                acceptableThreshold = 0.62,
                marginalThreshold = 0.52,
                alternativeTolerance = 0.12,
                betweenSizesTolerance = 0.04,
                dimensionSensitivity = mapOf(
                    "chest" to 1.2,      // More sensitive
                    "shoulder" to 1.1,
                    "waist" to 0.8,
                    "length" to 0.9
                )
            )
            
            // Upper body - relaxed garments (more lenient)
            GarmentType.HOODIE, GarmentType.SWEATER -> DynamicThresholds(
                excellentThreshold = 0.88,
                veryGoodThreshold = 0.78,
                goodThreshold = 0.68,
                acceptableThreshold = 0.58,
                marginalThreshold = 0.48,
                alternativeTolerance = 0.18,
                betweenSizesTolerance = 0.06,
                dimensionSensitivity = mapOf(
                    "chest" to 1.0,
                    "length" to 0.9,
                    "waist" to 0.7
                )
            )
            
            // Lower body - fitted (stricter)
            GarmentType.JEANS, GarmentType.CHINOS -> DynamicThresholds(
                excellentThreshold = 0.90,
                veryGoodThreshold = 0.80,
                goodThreshold = 0.70,
                acceptableThreshold = 0.60,
                marginalThreshold = 0.50,
                alternativeTolerance = 0.15,
                betweenSizesTolerance = 0.05,
                dimensionSensitivity = mapOf(
                    "waist" to 1.3,      // Most critical
                    "hip" to 1.1,
                    "inseam" to 0.9
                )
            )
            
            // Lower body - relaxed
            GarmentType.JOGGERS, GarmentType.CARGO_PANTS -> DynamicThresholds(
                excellentThreshold = 0.86,
                veryGoodThreshold = 0.76,
                goodThreshold = 0.66,
                acceptableThreshold = 0.56,
                marginalThreshold = 0.46,
                alternativeTolerance = 0.20,
                betweenSizesTolerance = 0.07,
                dimensionSensitivity = mapOf(
                    "waist" to 1.1,
                    "hip" to 1.0,
                    "inseam" to 0.8
                )
            )
            
            // Default (T-shirt, Polo, etc.)
            else -> DynamicThresholds(
                excellentThreshold = 0.90,
                veryGoodThreshold = 0.80,
                goodThreshold = 0.70,
                acceptableThreshold = 0.60,
                marginalThreshold = 0.50,
                alternativeTolerance = 0.15,
                betweenSizesTolerance = 0.05,
                dimensionSensitivity = mapOf(
                    "chest" to 1.0,
                    "length" to 0.9,
                    "waist" to 0.8
                )
            )
        }
        
        Log.d(TAG, "🎯 [Engine Vision Update] Dynamic thresholds for $garmentType: excellent=${baseThresholds.excellentThreshold}, good=${baseThresholds.goodThreshold}")
        
        return baseThresholds
    }
    
    /**
     * Determine fit quality using dynamic thresholds
     */
    fun determineFitQuality(score: Double, thresholds: DynamicThresholds): FitQuality {
        return when {
            score >= thresholds.excellentThreshold -> FitQuality.EXCELLENT
            score >= thresholds.veryGoodThreshold -> FitQuality.VERY_GOOD
            score >= thresholds.goodThreshold -> FitQuality.GOOD
            score >= thresholds.acceptableThreshold -> FitQuality.ACCEPTABLE
            score >= thresholds.marginalThreshold -> FitQuality.MARGINAL
            else -> FitQuality.NOT_RECOMMENDED
        }
    }
    
    /**
     * Determine fit status for dimension using dynamic sensitivity
     */
    fun determineDimensionStatus(
        score: Double,
        dimension: String,
        thresholds: DynamicThresholds
    ): FitStatus {
        // Adjust thresholds based on dimension sensitivity
        val sensitivity = thresholds.dimensionSensitivity[dimension] ?: 1.0
        val adjustedExcellent = thresholds.excellentThreshold * sensitivity.coerceIn(0.8, 1.2)
        val adjustedGood = thresholds.goodThreshold * sensitivity.coerceIn(0.8, 1.2)
        val adjustedAcceptable = thresholds.acceptableThreshold * sensitivity.coerceIn(0.8, 1.2)
        
        return when {
            score >= adjustedExcellent -> FitStatus.EXCELLENT
            score >= adjustedGood -> FitStatus.GOOD
            score >= adjustedAcceptable -> FitStatus.ACCEPTABLE
            score >= thresholds.marginalThreshold -> FitStatus.TIGHT_OR_LOOSE
            else -> FitStatus.NOT_RECOMMENDED
        }
    }
}

