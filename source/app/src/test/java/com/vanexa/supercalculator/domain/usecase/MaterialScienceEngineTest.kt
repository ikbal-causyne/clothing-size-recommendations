package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.FabricComposition
import org.junit.Assert.*
import org.junit.Test

class MaterialScienceEngineTest {
    private val engine = MaterialScienceEngine()
    
    @Test
    fun `test stretch factor calculation with high elastane`() {
        val composition = FabricComposition(
            cottonPercent = 60.0,
            polyesterPercent = 30.0,
            elastanePercent = 10.0
        )
        
        val stretchFactor = engine.calculateStretchFactor(composition)
        
        // High elastane (>5%) should give stretch factor > 1.15
        assertTrue("Stretch factor should be > 1.15 for high elastane", stretchFactor > 1.15)
        assertTrue("Stretch factor should be <= 1.30", stretchFactor <= 1.30)
    }
    
    @Test
    fun `test stretch factor calculation with low elastane`() {
        val composition = FabricComposition(
            cottonPercent = 95.0,
            polyesterPercent = 5.0,
            elastanePercent = 0.0
        )
        
        val stretchFactor = engine.calculateStretchFactor(composition)
        
        // Low elastane should give stretch factor close to 1.0
        assertTrue("Stretch factor should be close to 1.0 for low elastane", stretchFactor >= 1.0 && stretchFactor <= 1.05)
    }
    
    @Test
    fun `test dimension adjustment for stretch`() {
        val dimension = 100.0
        val stretchFactor = 1.2
        
        val adjusted = engine.adjustDimensionForStretch(dimension, stretchFactor)
        
        assertEquals("Adjusted dimension should be 120.0", 120.0, adjusted, 0.01)
    }
    
    @Test
    fun `test strain calculation`() {
        val initialLength = 100.0
        val finalLength = 110.0
        
        val strain = engine.calculateStrain(initialLength, finalLength)
        
        assertEquals("Strain should be 0.1 (10%)", 0.1, strain, 0.01)
    }
    
    @Test
    fun `test Young's modulus estimation`() {
        val composition = FabricComposition(
            cottonPercent = 50.0,
            polyesterPercent = 45.0,
            elastanePercent = 5.0
        )
        
        val modulus = engine.estimateYoungsModulus(composition)
        
        assertTrue("Young's modulus should be positive", modulus > 0)
        assertTrue("Young's modulus should be reasonable for fabrics", modulus < 2000.0)
    }
}

