package com.vanexa.supercalculator.domain.usecase

import android.content.Context
import org.json.JSONObject
import kotlin.math.abs

/**
 * On-Device ML Size Engine - ADVANCED VERSION
 * Uses OpenML-trained lookup table with bilinear interpolation
 * 
 * Training Data: 257 samples (OpenML Bodyfat + Vanexa validation)
 * Accuracy: MAE 3.06cm (chest), 3.94cm (waist), 2.25cm (hip)
 * Coverage: 150-200cm height, 50-120kg weight
 */
object MLSizeEngine {
    
    private var lookupTable: List<LookupEntry>? = null
    private var isInitialized = false
    
    data class LookupEntry(
        val height: Double,
        val weight: Double,
        val chest: Double,
        val waist: Double,
        val hip: Double
    )
    
    data class MLPrediction(
        val chest: Double,
        val waist: Double,
        val hip: Double,
        val confidence: Double,
        val method: String
    )
    
    /**
     * Initialize ML engine - call once at app startup
     */
    fun initialize(context: Context) {
        if (isInitialized) {
            android.util.Log.d("MLSizeEngine", "⚠️ Already initialized, skipping")
            return
        }
        
        try {
            val json = context.assets.open("size_engine_lookup.json")
                .bufferedReader()
                .use { it.readText() }
            
            val jsonObject = JSONObject(json)
            val dataArray = jsonObject.getJSONArray("data")
            
            val entries = mutableListOf<LookupEntry>()
            for (i in 0 until dataArray.length()) {
                val entry = dataArray.getJSONObject(i)
                entries.add(
                    LookupEntry(
                        height = entry.getDouble("height"),
                        weight = entry.getDouble("weight"),
                        chest = entry.getDouble("chest"),
                        waist = entry.getDouble("waist"),
                        hip = entry.getDouble("hip")
                    )
                )
            }
            
            lookupTable = entries
            isInitialized = true
            
            android.util.Log.d("MLSizeEngine", "✅ ML Engine initialized: ${entries.size} entries loaded")
            
        } catch (e: Exception) {
            android.util.Log.e("MLSizeEngine", "❌ Failed to initialize: ${e.message}", e)
            isInitialized = false
        }
    }
    
    /**
     * Predict body measurements with bilinear interpolation
     * Returns null if engine not initialized
     */
    fun predict(height: Double, weight: Double): Triple<Double, Double, Double>? {
        if (!isInitialized || lookupTable == null) {
            android.util.Log.w("MLSizeEngine", "⚠️ Not initialized, call initialize() first")
            return null
        }
        
        val prediction = predictAdvanced(height, weight)
        
        if (prediction != null) {
            android.util.Log.d("MLSizeEngine", 
                "✅ Prediction: H=${height}cm W=${weight}kg → " +
                "C=${prediction.chest.format(1)}cm " +
                "W=${prediction.waist.format(1)}cm " +
                "H=${prediction.hip.format(1)}cm " +
                "(${prediction.method}, conf=${prediction.confidence.format(2)})"
            )
        }
        
        return prediction?.let { Triple(it.chest, it.waist, it.hip) }
    }
    
    /**
     * Advanced prediction with bilinear interpolation
     */
    private fun predictAdvanced(height: Double, weight: Double): MLPrediction? {
        val table = lookupTable ?: return null
        
        // Get unique sorted heights and weights from lookup table
        val heights = table.map { it.height }.distinct().sorted()
        val weights = table.map { it.weight }.distinct().sorted()
        
        // Find bounding heights (h1 <= height <= h2)
        val h1 = heights.lastOrNull { it <= height } ?: heights.first()
        val h2 = heights.firstOrNull { it >= height } ?: heights.last()
        
        // Find bounding weights (w1 <= weight <= w2)
        val w1 = weights.lastOrNull { it <= weight } ?: weights.first()
        val w2 = weights.firstOrNull { it >= weight } ?: weights.last()
        
        // Find 4 corner points for bilinear interpolation
        val p11 = table.firstOrNull { it.height == h1 && it.weight == w1 }
        val p12 = table.firstOrNull { it.height == h1 && it.weight == w2 }
        val p21 = table.firstOrNull { it.height == h2 && it.weight == w1 }
        val p22 = table.firstOrNull { it.height == h2 && it.weight == w2 }
        
        // Case 1: Exact match
        if (h1 == h2 && w1 == w2 && p11 != null) {
            return MLPrediction(
                chest = p11.chest,
                waist = p11.waist,
                hip = p11.hip,
                confidence = 0.95,  // Highest confidence for exact match
                method = "exact_match"
            )
        }
        
        // Case 2: Bilinear interpolation (all 4 corners available)
        if (p11 != null && p12 != null && p21 != null && p22 != null) {
            val tHeight = if (h2 != h1) (height - h1) / (h2 - h1) else 0.0
            val tWeight = if (w2 != w1) (weight - w1) / (w2 - w1) else 0.0
            
            val chestInterp = bilinearInterpolate(
                p11.chest, p12.chest, p21.chest, p22.chest, tHeight, tWeight
            )
            val waistInterp = bilinearInterpolate(
                p11.waist, p12.waist, p21.waist, p22.waist, tHeight, tWeight
            )
            val hipInterp = bilinearInterpolate(
                p11.hip, p12.hip, p21.hip, p22.hip, tHeight, tWeight
            )
            
            // Calculate confidence based on distance from grid points
            val distanceFromGrid = kotlin.math.sqrt(tHeight * tHeight + tWeight * tWeight)
            val confidence = (0.92 - distanceFromGrid * 0.1).coerceIn(0.75, 0.92)
            
            return MLPrediction(
                chest = chestInterp,
                waist = waistInterp,
                hip = hipInterp,
                confidence = confidence,
                method = "bilinear_interp"
            )
        }
        
        // Case 3: Linear interpolation (only 2 points available)
        if (p11 != null && p12 != null && h1 == h2) {
            // Interpolate along weight axis
            val t = if (w2 != w1) (weight - w1) / (w2 - w1) else 0.0
            return MLPrediction(
                chest = linearInterpolate(p11.chest, p12.chest, t),
                waist = linearInterpolate(p11.waist, p12.waist, t),
                hip = linearInterpolate(p11.hip, p12.hip, t),
                confidence = 0.88,
                method = "linear_interp_weight"
            )
        }
        
        if (p11 != null && p21 != null && w1 == w2) {
            // Interpolate along height axis
            val t = if (h2 != h1) (height - h1) / (h2 - h1) else 0.0
            return MLPrediction(
                chest = linearInterpolate(p11.chest, p21.chest, t),
                waist = linearInterpolate(p11.waist, p21.waist, t),
                hip = linearInterpolate(p11.hip, p21.hip, t),
                confidence = 0.88,
                method = "linear_interp_height"
            )
        }
        
        // Case 4: Fallback to nearest neighbor
        val nearest = table.minByOrNull {
            val dH = (it.height - height)
            val dW = (it.weight - weight)
            dH * dH + dW * dW  // Euclidean distance squared
        }
        
        return nearest?.let {
            val distance = kotlin.math.sqrt(
                (it.height - height) * (it.height - height) +
                (it.weight - weight) * (it.weight - weight)
            )
            
            // Reduce confidence based on distance
            val confidence = (0.80 - distance * 0.01).coerceIn(0.60, 0.80)
            
            MLPrediction(
                chest = it.chest,
                waist = it.waist,
                hip = it.hip,
                confidence = confidence,
                method = "nearest_neighbor"
            )
        }
    }
    
    /**
     * Bilinear interpolation formula
     * v11, v12, v21, v22 = values at 4 corners
     * tHeight, tWeight = interpolation factors (0.0 to 1.0)
     */
    private fun bilinearInterpolate(
        v11: Double, v12: Double, v21: Double, v22: Double,
        tHeight: Double, tWeight: Double
    ): Double {
        // Interpolate along height first
        val v1 = v11 * (1 - tHeight) + v21 * tHeight
        val v2 = v12 * (1 - tHeight) + v22 * tHeight
        
        // Then interpolate along weight
        return v1 * (1 - tWeight) + v2 * tWeight
    }
    
    /**
     * Linear interpolation between two values
     */
    private fun linearInterpolate(v1: Double, v2: Double, t: Double): Double {
        return v1 * (1 - t) + v2 * t
    }
    
    /**
     * Format double to specified decimal places
     */
    private fun Double.format(decimals: Int): String {
        return "%.${decimals}f".format(this)
    }
}
