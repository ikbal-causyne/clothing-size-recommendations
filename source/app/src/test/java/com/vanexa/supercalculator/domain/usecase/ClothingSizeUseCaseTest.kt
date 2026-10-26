package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ClothingSizeUseCaseTest {
    
    private lateinit var sizeUseCase: ClothingSizeUseCase
    
    @Before
    fun setup() {
        sizeUseCase = ClothingSizeUseCase()
    }
    
    @Test
    fun `test size recommendation for US region`() {
        // Average male: 175 cm, 70 kg, chest 95 cm, waist 80 cm
        val recommendation = sizeUseCase.recommendSize(
            height = 175.0,
            weight = 70.0,
            chest = 95.0,
            waist = 80.0,
            region = SizeRegion.US
        )
        
        assertNotNull(recommendation)
        assertNotNull(recommendation.primarySize)
        assertEquals(SizeRegion.US, recommendation.primarySize.region)
        assertTrue(recommendation.confidence >= 0)
        assertTrue(recommendation.alternativeSizes.isNotEmpty())
    }
    
    @Test
    fun `test size recommendation for EU region`() {
        val recommendation = sizeUseCase.recommendSize(
            height = 175.0,
            weight = 70.0,
            chest = 95.0,
            waist = 80.0,
            region = SizeRegion.EU
        )
        
        assertNotNull(recommendation)
        assertEquals(SizeRegion.EU, recommendation.primarySize.region)
    }
    
    @Test
    fun `test size recommendation for Asia region`() {
        // Asia sizes are typically smaller
        val recommendation = sizeUseCase.recommendSize(
            height = 170.0,
            weight = 65.0,
            chest = 90.0,
            waist = 75.0,
            region = SizeRegion.ASIA
        )
        
        assertNotNull(recommendation)
        assertEquals(SizeRegion.ASIA, recommendation.primarySize.region)
    }
    
    @Test
    fun `test size recommendation without optional measurements`() {
        // Should work with just height and weight
        val recommendation = sizeUseCase.recommendSize(
            height = 175.0,
            weight = 70.0,
            chest = null,
            waist = null,
            region = SizeRegion.US
        )
        
        assertNotNull(recommendation)
        assertNotNull(recommendation.primarySize)
        // Confidence should be lower without chest/waist
        assertTrue(recommendation.confidence >= 0)
    }
    
    @Test
    fun `test size recommendation confidence score`() {
        val recommendation = sizeUseCase.recommendSize(
            height = 175.0,
            weight = 70.0,
            chest = 95.0,
            waist = 80.0,
            region = SizeRegion.US
        )
        
        // Confidence should be between 0 and 100
        assertTrue(recommendation.confidence >= 0)
        assertTrue(recommendation.confidence <= 100)
    }
    
    @Test
    fun `test size recommendation provides alternatives`() {
        val recommendation = sizeUseCase.recommendSize(
            height = 175.0,
            weight = 70.0,
            chest = 95.0,
            waist = 80.0,
            region = SizeRegion.US
        )
        
        // Should provide at least one alternative
        assertTrue(recommendation.alternativeSizes.size >= 1)
    }
}

