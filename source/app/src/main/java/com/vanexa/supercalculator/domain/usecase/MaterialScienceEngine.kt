package com.vanexa.supercalculator.domain.usecase

import android.util.Log
import com.vanexa.supercalculator.domain.model.FabricComposition
import kotlin.math.pow

/**
 * Material Science Engine
 * Implements fabric elasticity modeling, stretch factor calculation, and material composition analysis
 * Based on Physics principles: Hooke's Law, Young's Modulus, Stress-Strain relationships
 * 
 * Scientific Foundation:
 * - Elasticity Theory: ε = ΔL / L₀
 * - Young's Modulus: E = σ / ε
 * - Stretch Factor: SF = 1 + ε_max
 */
class MaterialScienceEngine {
    companion object {
        private const val TAG = "MaterialScienceEngine"
    }
    
    /**
     * Calculate stretch factor based on fabric composition
     * 
     * @param fabricComposition Fabric composition percentages
     * @param elastanePercentage Explicit elastane percentage (overrides composition if provided)
     * @return Stretch factor (1.0 = no stretch, 1.2 = 20% stretch)
     */
    fun calculateStretchFactor(
        fabricComposition: FabricComposition? = null,
        elastanePercentage: Double? = null
    ): Double {
        // Use explicit elastane percentage if provided, otherwise use composition
        val elastane = elastanePercentage ?: fabricComposition?.elastanePercent ?: 0.0
        
        // Base stretch from elastane percentage (empirical data)
        val baseStretch = when {
            elastane > 5.0 -> 1.20  // High stretch (>5% elastane)
            elastane > 2.0 -> 1.10  // Medium stretch (2-5% elastane)
            elastane > 0.5 -> 1.05  // Low stretch (0.5-2% elastane)
            else -> 1.0              // No stretch (<0.5% elastane)
        }
        
        // Material-specific adjustments (from material science research)
        val cottonFactor = (fabricComposition?.cottonPercent ?: 0.0) * 0.0001  // Cotton reduces stretch slightly
        val polyesterFactor = (fabricComposition?.polyesterPercent ?: 0.0) * 0.0005  // Polyester adds minimal stretch
        
        val finalStretch = baseStretch + cottonFactor + polyesterFactor
        
        // Clamp to realistic range (0.95 to 1.30)
        return finalStretch.coerceIn(0.95, 1.30)
    }
    
    /**
     * Adjust dimension for fabric stretch
     * 
     * Formula: Adjusted_Dimension = User_Dimension × Stretch_Factor
     * 
     * @param dimension Original dimension in cm
     * @param stretchFactor Stretch factor (1.0 = no stretch, 1.2 = 20% stretch)
     * @return Adjusted dimension accounting for fabric stretch
     */
    fun adjustDimensionForStretch(
        dimension: Double,
        stretchFactor: Double
    ): Double {
        if (dimension <= 0.0) {
            Log.w(TAG, "Invalid dimension: $dimension, returning original")
            return dimension
        }
        
        if (stretchFactor < 0.95 || stretchFactor > 1.30) {
            Log.w(TAG, "Stretch factor out of range: $stretchFactor, clamping")
            val safeStretch = stretchFactor.coerceIn(0.95, 1.30)
            return dimension * safeStretch
        }
        
        return dimension * stretchFactor
    }
    
    /**
     * Calculate strain (deformation per unit length)
     * 
     * Formula: ε = ΔL / L₀ = (L_final - L_initial) / L_initial
     * 
     * @param initialLength Initial length in cm
     * @param finalLength Final length after stretch in cm
     * @return Strain value (dimensionless)
     */
    fun calculateStrain(
        initialLength: Double,
        finalLength: Double
    ): Double {
        if (initialLength <= 0.0) {
            Log.w(TAG, "Invalid initial length: $initialLength")
            return 0.0
        }
        
        return (finalLength - initialLength) / initialLength
    }
    
    /**
     * Estimate Young's Modulus (material stiffness)
     * 
     * Formula: E = σ / ε
     * 
     * For fabrics, we use empirical values based on material composition
     * 
     * @param fabricComposition Fabric composition
     * @return Estimated Young's Modulus in MPa (MegaPascals)
     */
    fun estimateYoungsModulus(
        fabricComposition: FabricComposition
    ): Double {
        // Empirical values from material science research (in MPa)
        val cottonModulus = 100.0  // Cotton: ~100 MPa
        val polyesterModulus = 1200.0  // Polyester: ~1200 MPa
        val elastaneModulus = 0.5  // Elastane: ~0.5 MPa (very elastic)
        
        // Weighted average based on composition
        val totalPercent = fabricComposition.cottonPercent + 
                          fabricComposition.polyesterPercent + 
                          fabricComposition.elastanePercent
        
        if (totalPercent <= 0.0) {
            return 500.0  // Default value
        }
        
        val weightedModulus = (
            (fabricComposition.cottonPercent / totalPercent) * cottonModulus +
            (fabricComposition.polyesterPercent / totalPercent) * polyesterModulus +
            (fabricComposition.elastanePercent / totalPercent) * elastaneModulus
        )
        
        return weightedModulus.coerceIn(0.1, 2000.0)
    }
    
    /**
     * Calculate thermal expansion adjustment (minimal impact)
     * 
     * Formula: ΔL = α × L₀ × ΔT
     * 
     * @param initialLength Initial length in cm
     * @param temperatureDifference Temperature difference in Celsius
     * @return Length change due to thermal expansion in cm
     */
    fun calculateThermalExpansion(
        initialLength: Double,
        temperatureDifference: Double = 10.0  // Default: 10°C difference
    ): Double {
        // Coefficient of thermal expansion for fabrics: ~10⁻⁵ /°C
        val alpha = 0.00001  // 1/°C
        
        return alpha * initialLength * temperatureDifference
    }
    
    /**
     * Adjust dimensions for material properties (stretch + thermal)
     * 
     * @param dimensions Map of dimension name to value
     * @param stretchFactor Stretch factor
     * @param temperatureDifference Optional temperature difference
     * @return Adjusted dimensions
     */
    fun adjustDimensionsForMaterial(
        dimensions: Map<String, Double>,
        stretchFactor: Double,
        temperatureDifference: Double = 0.0
    ): Map<String, Double> {
        return dimensions.mapValues { (_, value) ->
            var adjusted = adjustDimensionForStretch(value, stretchFactor)
            
            // Apply thermal expansion if significant
            if (temperatureDifference > 5.0) {
                val thermalDelta = calculateThermalExpansion(adjusted, temperatureDifference)
                adjusted += thermalDelta
            }
            
            adjusted
        }
    }
}

