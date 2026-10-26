package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.Gender
import com.vanexa.supercalculator.domain.model.SizeRegion
import org.junit.Assert.*
import org.junit.Test

/**
 * Biomechanics Engine Test - Fixed to match ACTUAL implementation
 * Part of Size Recommendation Engine - Extreme Testing
 */
class BiomechanicsEngineTest {
    private val engine = BiomechanicsEngine()
    
    @Test
    fun `test body composition calculation for male`() {
        val composition = engine.calculateBodyComposition(
            heightCm = 175.0,
            weightKg = 75.0,
            age = 30,
            gender = Gender.MALE,
            region = SizeRegion.US
        )
        
        assertTrue("BMI should be reasonable", composition.bmi > 20.0 && composition.bmi < 30.0)
        assertTrue("Body fat % should be in valid range", composition.bodyFatPercent >= 5.0 && composition.bodyFatPercent <= 50.0)
        assertTrue("Lean mass should be positive", composition.leanMass > 0)
    }
    
    @Test
    fun `test body composition calculation for female`() {
        val composition = engine.calculateBodyComposition(
            heightCm = 165.0,
            weightKg = 60.0,
            age = 25,
            gender = Gender.FEMALE,
            region = SizeRegion.US
        )
        
        assertTrue("BMI should be reasonable", composition.bmi > 18.0 && composition.bmi < 25.0)
        assertTrue("Body fat % should be in valid range", composition.bodyFatPercent >= 5.0 && composition.bodyFatPercent <= 50.0)
        assertTrue("Lean mass should be positive", composition.leanMass > 0)
    }
    
    @Test
    fun `test body shape classification - hourglass`() {
        // HOURGLASS: WHR in 0.85..0.95 AND CWR in 1.0..1.1
        // Example: chest=95, waist=90, hips=100
        // WHR = 90/100 = 0.90 (in range 0.85..0.95)
        // CWR = 95/90 = 1.056 (in range 1.0..1.1)
        val shape = engine.classifyBodyShape(
            chest = 95.0,
            waist = 90.0,
            hips = 100.0
        )
        
        assertEquals("Should classify as HOURGLASS", com.vanexa.supercalculator.domain.model.BodyShape.HOURGLASS, shape)
    }
    
    @Test
    fun `test body shape classification - pear`() {
        // PEAR: WHR < 0.85 AND CWR > 1.1
        // Example: chest=95, waist=70, hips=100
        // WHR = 70/100 = 0.70 (< 0.85)
        // CWR = 95/70 = 1.357 (> 1.1)
        val shape = engine.classifyBodyShape(
            chest = 95.0,
            waist = 70.0,
            hips = 100.0
        )
        
        assertEquals("Should classify as PEAR", com.vanexa.supercalculator.domain.model.BodyShape.PEAR, shape)
    }
    
    @Test
    fun `test body shape classification - apple`() {
        // APPLE: WHR > 0.95 AND CWR < 1.0
        // Example: chest=90, waist=100, hips=100
        // WHR = 100/100 = 1.0 (> 0.95)
        // CWR = 90/100 = 0.90 (< 1.0)
        val shape = engine.classifyBodyShape(
            chest = 90.0,
            waist = 100.0,
            hips = 100.0
        )
        
        assertEquals("Should classify as APPLE", com.vanexa.supercalculator.domain.model.BodyShape.APPLE, shape)
    }
    
    @Test
    fun `test body shape classification - rectangular`() {
        // RECTANGULAR: Other combinations (not PEAR, APPLE, or HOURGLASS)
        // Example: chest=90, waist=85, hips=90
        // WHR = 85/90 = 0.944 (in 0.85..0.95) BUT
        // CWR = 90/85 = 1.059 (in 1.0..1.1) - This would be HOURGLASS!
        
        // Better example for RECTANGULAR:
        // chest=90, waist=85, hips=90
        // WHR = 85/90 = 0.944 (in 0.85..0.95) BUT
        // CWR = 90/85 = 1.059 (in 1.0..1.1) - Still HOURGLASS!
        
        // Even better: chest=95, waist=85, hips=95
        // WHR = 85/95 = 0.895 (in 0.85..0.95) BUT
        // CWR = 95/85 = 1.118 (> 1.1) - NOT in 1.0..1.1
        // So: WHR in range BUT CWR NOT in range -> RECTANGULAR
        
        val shape = engine.classifyBodyShape(
            chest = 95.0,
            waist = 85.0,
            hips = 95.0
        )
        
        assertEquals("Should classify as RECTANGULAR", com.vanexa.supercalculator.domain.model.BodyShape.RECTANGULAR, shape)
    }
    
    @Test
    fun `test anthropometric ratios calculation`() {
        val ratios = engine.calculateAnthropometricRatios(
            chest = 95.0,
            waist = 80.0,
            hips = 100.0
        )
        
        assertTrue("Should have WHR", ratios.containsKey("WHR"))
        assertTrue("Should have CWR", ratios.containsKey("CWR"))
        assertEquals("WHR should be 0.8", 0.8, ratios["WHR"] ?: 0.0, 0.01)
        assertEquals("CWR should be 1.1875", 1.1875, ratios["CWR"] ?: 0.0, 0.01)
    }
}
