package com.vanexa.supercalculator.domain.usecase

import android.util.Log
import com.vanexa.supercalculator.domain.model.*
import kotlin.math.pow

/**
 * Biomechanics Engine
 * Implements body composition analysis, anthropometric ratios, and body shape classification
 * Based on Biology principles: Anthropometry, Physiology, Body Composition Analysis
 * 
 * Scientific Foundation:
 * - BMI calculation: BMI = W / (H/100)²
 * - Deurenberg equation for body fat estimation
 * - Anthropometric ratios (WHR, CWR)
 * - Body shape classification based on ratios
 */
class BiomechanicsEngine {
    companion object {
        private const val TAG = "BiomechanicsEngine"
    }
    
    /**
     * Calculate body composition (BMI, body fat %, lean mass)
     * 
     * @param heightCm Height in centimeters
     * @param weightKg Weight in kilograms
     * @param age Optional age (improves body fat estimation)
     * @param gender Gender (affects body fat calculation)
     * @param region Optional region (affects body fat estimation)
     * @return BodyComposition with BMI, body fat %, and lean mass
     */
    fun calculateBodyComposition(
        heightCm: Double,
        weightKg: Double,
        age: Int? = null,
        gender: Gender,
        region: SizeRegion? = null
    ): BodyComposition {
        // Safety checks
        val safeHeight = heightCm.coerceIn(100.0, 250.0)
        val safeWeight = weightKg.coerceIn(30.0, 200.0)
        val safeAge = age?.coerceIn(15, 100) ?: 30  // Default age if not provided
        
        // Calculate BMI: BMI = W / (H/100)²
        val bmi = safeWeight / ((safeHeight / 100.0).pow(2))
        
        // Deurenberg equation for body fat estimation (regional variants)
        val bodyFatPercent = when (gender) {
            Gender.MALE -> {
                // Male: BF% = 1.20 × BMI + 0.23 × age - 16.2
                val base = 1.20 * bmi + 0.23 * safeAge - 16.2
                // Regional adjustment
                when (region) {
                    SizeRegion.ASIA, SizeRegion.ID -> base - 2.0  // Asian populations typically have lower BF%
                    else -> base
                }
            }
            Gender.FEMALE -> {
                // Female: BF% = 1.20 × BMI + 0.23 × age - 5.4
                val base = 1.20 * bmi + 0.23 * safeAge - 5.4
                // Regional adjustment
                when (region) {
                    SizeRegion.ASIA, SizeRegion.ID -> base - 1.5
                    else -> base
                }
            }
        }.coerceIn(5.0, 50.0)  // Clamp to realistic range
        
        // Calculate lean mass: Lean Mass = W × (1 - BF%/100)
        val leanMass = safeWeight * (1 - bodyFatPercent / 100.0)
        
        return BodyComposition(
            bmi = bmi,
            bodyFatPercent = bodyFatPercent,
            leanMass = leanMass
        )
    }
    
    /**
     * Calculate anthropometric ratios
     * 
     * @param chest Chest circumference in cm
     * @param waist Waist circumference in cm
     * @param hips Hips circumference in cm
     * @return Map of ratio names to values
     */
    fun calculateAnthropometricRatios(
        chest: Double?,
        waist: Double?,
        hips: Double?
    ): Map<String, Double> {
        val ratios = mutableMapOf<String, Double>()
        
        // Waist-to-Hip Ratio (WHR)
        if (waist != null && hips != null && hips > 0.0) {
            ratios["WHR"] = waist / hips
        }
        
        // Chest-to-Waist Ratio (CWR)
        if (chest != null && waist != null && waist > 0.0) {
            ratios["CWR"] = chest / waist
        }
        
        // Waist-to-Height Ratio (WHtR)
        // Note: Height would need to be passed separately if needed
        
        return ratios
    }
    
    /**
     * Classify body shape based on anthropometric ratios
     * 
     * Classification rules:
     * - PEAR: WHR < 0.85 and CWR > 1.1 (narrow waist, wider hips)
     * - APPLE: WHR > 0.95 and CWR < 1.0 (wider waist, narrower chest)
     * - HOURGLASS: WHR in 0.85-0.95 and CWR in 1.0-1.1 (balanced proportions)
     * - RECTANGULAR: Other combinations (straight proportions)
     * 
     * @param chest Chest circumference in cm
     * @param waist Waist circumference in cm
     * @param hips Hips circumference in cm
     * @return BodyShape classification
     */
    fun classifyBodyShape(
        chest: Double?,
        waist: Double?,
        hips: Double?
    ): BodyShape {
        if (chest == null || waist == null || hips == null) {
            Log.w(TAG, "Missing dimensions for body shape classification, defaulting to RECTANGULAR")
            return BodyShape.RECTANGULAR
        }
        
        if (waist <= 0.0 || hips <= 0.0 || chest <= 0.0) {
            Log.w(TAG, "Invalid dimensions for body shape classification, defaulting to RECTANGULAR")
            return BodyShape.RECTANGULAR
        }
        
        val whr = waist / hips
        val cwr = chest / waist
        
        return when {
            // PEAR shape: Narrow waist, wider hips
            whr < 0.85 && cwr > 1.1 -> BodyShape.PEAR
            
            // APPLE shape: Wider waist, narrower chest
            whr > 0.95 && cwr < 1.0 -> BodyShape.APPLE
            
            // HOURGLASS shape: Balanced proportions
            whr in 0.85..0.95 && cwr in 1.0..1.1 -> BodyShape.HOURGLASS
            
            // RECTANGULAR shape: Other combinations
            else -> BodyShape.RECTANGULAR
        }
    }
    
    /**
     * Calculate postural variation adjustments
     * 
     * Sitting measurements differ from standing measurements:
     * - Waist: ~5% increase when sitting
     * - Chest: Minimal change
     * - Hips: Minimal change
     * 
     * @param standingWaist Standing waist measurement in cm
     * @return Sitting waist measurement in cm
     */
    fun adjustForSittingPosture(standingWaist: Double): Double {
        return standingWaist * 1.05  // 5% increase
    }
    
    /**
     * Get fit preference adjustment based on body shape
     * 
     * Different body shapes may prefer different fit styles:
     * - PEAR: May prefer looser fit on hips
     * - APPLE: May prefer looser fit on waist
     * - HOURGLASS: Can wear fitted styles
     * - RECTANGULAR: May prefer structured fits
     * 
     * @param bodyShape Body shape classification
     * @param dimension Dimension name ("chest", "waist", "hips")
     * @return Adjustment factor (1.0 = no adjustment, >1.0 = looser, <1.0 = tighter)
     */
    fun getFitPreferenceAdjustment(
        bodyShape: BodyShape,
        dimension: String
    ): Double {
        return when (bodyShape) {
            BodyShape.PEAR -> when (dimension) {
                "hips" -> 1.05  // Slightly looser on hips
                else -> 1.0
            }
            BodyShape.RECTANGLE, BodyShape.RECTANGULAR -> 1.0
            BodyShape.TRIANGLE -> 1.0
            BodyShape.INVERTED_TRIANGLE -> 1.0
            BodyShape.HOURGLASS -> 1.0
            BodyShape.ROUND -> 1.0
            BodyShape.APPLE -> 1.0
            BodyShape.APPLE -> when (dimension) {
                "waist" -> 1.05  // Slightly looser on waist
                else -> 1.0
            }
            BodyShape.HOURGLASS -> 1.0  // No adjustment needed
            BodyShape.RECTANGULAR -> 1.0  // No adjustment needed
        }
    }
}

