package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import android.util.Log

/**
 * GARMENT PHYSICS LAYER per Engine Vision Update
 * Handles ease allowance rules, stretch vs rigid behavior, upper-body vs lower-body logic
 */
object GarmentPhysicsEngine {
    private const val TAG = "GarmentPhysicsEngine"
    
    /**
     * Garment physics configuration
     */
    data class GarmentPhysics(
        val easeAllowance: EaseAllowance,      // How much extra room garment has
        val stretchBehavior: StretchBehavior,  // How much fabric stretches
        val bodyCategory: BodyCategory         // Upper vs lower body logic
    )
    
    data class EaseAllowance(
        val chest: Double,      // cm of ease for chest
        val waist: Double,      // cm of ease for waist
        val hip: Double,       // cm of ease for hip
        val shoulder: Double,   // cm of ease for shoulder
        val length: Double     // cm of ease for length
    )
    
    enum class StretchBehavior {
        RIGID,          // No stretch (e.g., denim, canvas)
        MINIMAL_STRETCH, // 5-10% stretch (e.g., cotton)
        MODERATE_STRETCH, // 10-20% stretch (e.g., jersey, knit)
        HIGH_STRETCH     // 20%+ stretch (e.g., spandex, elastane)
    }
    
    enum class BodyCategory {
        UPPER_BODY,     // Tops, shirts, jackets
        LOWER_BODY,     // Pants, shorts
        FULL_BODY       // Dresses, jumpsuits (future)
    }
    
    /**
     * Get garment physics for garment type
     */
    fun getGarmentPhysics(garmentType: GarmentType): GarmentPhysics {
        val physics = when (garmentType) {
            // Upper body - fitted
            GarmentType.SHIRT, GarmentType.BLAZER -> GarmentPhysics(
                easeAllowance = EaseAllowance(
                    chest = 8.0,    // Fitted: 6-10cm ease
                    waist = 6.0,
                    hip = 0.0,
                    shoulder = 2.0,
                    length = 0.0
                ),
                stretchBehavior = StretchBehavior.RIGID,
                bodyCategory = BodyCategory.UPPER_BODY
            )
            
            // Upper body - relaxed
            GarmentType.HOODIE, GarmentType.SWEATER -> GarmentPhysics(
                easeAllowance = EaseAllowance(
                    chest = 15.0,   // Relaxed: 12-18cm ease
                    waist = 12.0,
                    hip = 0.0,
                    shoulder = 4.0,
                    length = 0.0
                ),
                stretchBehavior = StretchBehavior.MODERATE_STRETCH,
                bodyCategory = BodyCategory.UPPER_BODY
            )
            
            // Upper body - standard
            GarmentType.TSHIRT, GarmentType.POLO_SHIRT -> GarmentPhysics(
                easeAllowance = EaseAllowance(
                    chest = 10.0,   // Standard: 8-12cm ease
                    waist = 8.0,
                    hip = 0.0,
                    shoulder = 3.0,
                    length = 0.0
                ),
                stretchBehavior = StretchBehavior.MINIMAL_STRETCH,
                bodyCategory = BodyCategory.UPPER_BODY
            )
            
            // Lower body - fitted
            GarmentType.JEANS, GarmentType.CHINOS -> GarmentPhysics(
                easeAllowance = EaseAllowance(
                    chest = 0.0,
                    waist = 2.0,    // Fitted pants: 0-4cm ease
                    hip = 4.0,
                    shoulder = 0.0,
                    length = 0.0
                ),
                stretchBehavior = StretchBehavior.MINIMAL_STRETCH,
                bodyCategory = BodyCategory.LOWER_BODY
            )
            
            // Lower body - relaxed
            GarmentType.JOGGERS, GarmentType.CARGO_PANTS -> GarmentPhysics(
                easeAllowance = EaseAllowance(
                    chest = 0.0,
                    waist = 8.0,    // Relaxed pants: 6-10cm ease
                    hip = 10.0,
                    shoulder = 0.0,
                    length = 0.0
                ),
                stretchBehavior = StretchBehavior.MODERATE_STRETCH,
                bodyCategory = BodyCategory.LOWER_BODY
            )
            
            // Lower body - standard
            GarmentType.TROUSERS, GarmentType.SHORTS -> GarmentPhysics(
                easeAllowance = EaseAllowance(
                    chest = 0.0,
                    waist = 4.0,    // Standard: 2-6cm ease
                    hip = 6.0,
                    shoulder = 0.0,
                    length = 0.0
                ),
                stretchBehavior = StretchBehavior.MINIMAL_STRETCH,
                bodyCategory = BodyCategory.LOWER_BODY
            )
            
            // Default
            else -> GarmentPhysics(
                easeAllowance = EaseAllowance(
                    chest = 10.0,
                    waist = 6.0,
                    hip = 6.0,
                    shoulder = 3.0,
                    length = 0.0
                ),
                stretchBehavior = StretchBehavior.MINIMAL_STRETCH,
                bodyCategory = BodyCategory.UPPER_BODY
            )
        }
        
        Log.d(TAG, "🎯 [Engine Vision Update] Garment physics for $garmentType: easeAllowance=${physics.easeAllowance.chest}cm chest, stretch=${physics.stretchBehavior}")
        
        return physics
    }
    
    /**
     * Adjust measurement range based on garment physics
     * Accounts for ease allowance and stretch behavior
     */
    fun adjustRangeForPhysics(
        range: MeasurementRange,
        dimension: String,
        physics: GarmentPhysics
    ): MeasurementRange {
        val ease = when (dimension) {
            "chest" -> physics.easeAllowance.chest
            "waist" -> physics.easeAllowance.waist
            "hip" -> physics.easeAllowance.hip
            "shoulder" -> physics.easeAllowance.shoulder
            "length" -> physics.easeAllowance.length
            else -> 0.0
        }
        
        // Stretch behavior affects how forgiving the range is
        val stretchFactor = when (physics.stretchBehavior) {
            StretchBehavior.RIGID -> 0.0           // No forgiveness
            StretchBehavior.MINIMAL_STRETCH -> 0.05 // 5% forgiveness
            StretchBehavior.MODERATE_STRETCH -> 0.15 // 15% forgiveness
            StretchBehavior.HIGH_STRETCH -> 0.25    // 25% forgiveness
        }
        
        // Adjusted range accounts for ease and stretch
        val adjustedMin = range.min - ease * (1.0 - stretchFactor)
        val adjustedMax = range.max + ease * stretchFactor
        
        return MeasurementRange(
            min = adjustedMin.coerceAtLeast(0.0),
            max = adjustedMax,
            unit = range.unit,
            confidence = range.confidence
        )
    }
    
    /**
     * Check if measurement fits considering garment physics
     */
    fun checkFitWithPhysics(
        userMeasurement: Double,
        garmentRange: MeasurementRange,
        dimension: String,
        physics: GarmentPhysics
    ): Boolean {
        val adjustedRange = adjustRangeForPhysics(garmentRange, dimension, physics)
        return userMeasurement >= adjustedRange.min && userMeasurement <= adjustedRange.max
    }
}

