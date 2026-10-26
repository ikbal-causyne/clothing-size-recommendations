package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import android.util.Log

/**
 * CONFIDENCE VECTOR CALCULATOR per Engine Vision Update
 * Uses per-dimension confidence vectors
 * Overall confidence is weighted, not absolute
 */
object ConfidenceVectorCalculator {
    private const val TAG = "ConfidenceVectorCalculator"
    
    /**
     * Per-dimension confidence vector
     */
    data class ConfidenceVector(
        val dimension: String,
        val userConfidence: Double,      // Confidence in user measurement
        val chartConfidence: Double,     // Confidence in chart measurement (from OCR/parsing)
        val combinedConfidence: Double,  // Combined confidence
        val weight: Double               // Dimension importance weight
    )
    
    /**
     * Overall confidence structure
     */
    data class WeightedConfidence(
        val overall: Double,                          // Weighted overall confidence
        val perDimension: Map<String, ConfidenceVector>, // Per-dimension confidence vectors
        val confidenceBreakdown: Map<String, Double>  // Breakdown by source
    )
    
    /**
     * Calculate weighted confidence from body measurements and size chart
     */
    fun calculateWeightedConfidence(
        userBody: EstimatedBodyMeasurements,
        garmentSize: SizeChartEntry,
        garmentType: GarmentType,
        dimensionWeights: DimensionWeights
    ): WeightedConfidence {
        val confidenceVectors = mutableMapOf<String, ConfidenceVector>()
        
        // Get user confidence per dimension
        val userConfidences = mapOf(
            "chest" to (userBody.confidence.breakdown["chest"]?.value ?: 1.0),
            "waist" to (userBody.confidence.breakdown["waist"]?.value ?: 1.0),
            "hip" to (userBody.confidence.breakdown["hip"]?.value ?: 1.0),
            "shoulder" to (userBody.confidence.breakdown["shoulder"]?.value ?: 1.0),
            "inseam" to (userBody.confidence.breakdown["inseam"]?.value ?: 1.0)
        )
        
        // Chart confidence from OCR/parsing (BUG 9 FIX: Use actual confidence from MeasurementRange)
        val chartConfidences = mapOf(
            "chest" to (garmentSize.chest?.confidence ?: 0.0),
            "waist" to (garmentSize.waist?.confidence ?: 0.0),
            "hip" to (garmentSize.hip?.confidence ?: 0.0),
            "shoulder" to (garmentSize.shoulder?.confidence ?: 0.0),
            "inseam" to (garmentSize.inseam?.confidence ?: 0.0)
        )
        
        // Build confidence vectors
        val weights = mapOf(
            "chest" to dimensionWeights.chest,
            "waist" to dimensionWeights.waist,
            "hip" to dimensionWeights.hip,
            "shoulder" to dimensionWeights.shoulder,
            "inseam" to dimensionWeights.inseam
        )
        
        weights.forEach { (dimension, weight) ->
            if (weight > 0) {
                val userConf = userConfidences[dimension] ?: 1.0
                val chartConf = chartConfidences[dimension] ?: 0.0
                val combinedConf = userConf * chartConf  // Combined confidence
                
                confidenceVectors[dimension] = ConfidenceVector(
                    dimension = dimension,
                    userConfidence = userConf,
                    chartConfidence = chartConf,
                    combinedConfidence = combinedConf,
                    weight = weight
                )
            }
        }
        
        // Calculate weighted overall confidence
        val totalWeightedConfidence = confidenceVectors.values.sumOf { it.combinedConfidence * it.weight }
        val totalWeight = confidenceVectors.values.sumOf { it.weight }
        val overallConfidence = if (totalWeight > 0) totalWeightedConfidence / totalWeight else 0.0
        
        Log.d(TAG, "🎯 [Engine Vision Update] Weighted confidence: overall=$overallConfidence")
        confidenceVectors.forEach { (dimension, vector) ->
            Log.d(TAG, "  $dimension: userConf=${vector.userConfidence}, chartConf=${vector.chartConfidence}, combined=${vector.combinedConfidence}, weight=${vector.weight}")
        }
        
        return WeightedConfidence(
            overall = overallConfidence.coerceIn(0.0, 1.0),
            perDimension = confidenceVectors,
            confidenceBreakdown = mapOf(
                "user" to userBody.confidence.overall,
                "chart" to chartConfidences.values.average(),
                "combined" to overallConfidence
            )
        )
    }
}

