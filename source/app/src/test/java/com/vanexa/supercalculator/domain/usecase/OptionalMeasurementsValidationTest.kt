package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

/**
 * Optional Measurements Validation Test - Extreme Testing
 * Tests ALL optional measurement fields: Chest, Waist, Hip, Shoulder, Inseam
 * Verifies each field individually and in combination
 */
class OptionalMeasurementsValidationTest {
    
    // Test 1: Chest Only
    @Test
    fun testChestOnly() {
        val height = 175.0
        val weight = 70.0
        val gender = Gender.MALE
        val region = SizeRegion.US
        
        val optional = OptionalMeasurements(
            chestCircumference = 98.0,
            waistCircumference = null,
            hipCircumference = null,
            shoulderWidth = null,
            inseamLength = null
        )
        
        val result = BodyEstimator.estimateBodyMeasurements(
            height, weight, gender, region, optional
        )
        
        // Should use provided chest
        assertEquals("Should use provided chest", 98.0, result.chest, 0.1)
        
        // Others should be estimated
        assertTrue("Waist should be estimated", result.waist > 0)
        assertTrue("Hip should be estimated", result.hip > 0)
        assertTrue("Shoulder should be estimated", result.shoulder > 0)
        assertTrue("Inseam should be estimated", result.inseam > 0)
        
        // Confidence should be higher with chest measurement
        assertTrue("Confidence should be >=0.85 with chest measurement, got ${result.confidence.overall}",
            result.confidence.overall >= 0.85)
        
        // Chest confidence should be high (measured)
        val chestConfidence = result.confidence.breakdown["chest"]?.value ?: 0.0
        assertTrue("Measured chest should have >=98% confidence, got $chestConfidence",
            chestConfidence >= 0.98)
    }
    
    // Test 2: Waist Only
    @Test
    fun testWaistOnly() {
        val height = 175.0
        val weight = 70.0
        val gender = Gender.MALE
        val region = SizeRegion.US
        
        val optional = OptionalMeasurements(
            chestCircumference = null,
            waistCircumference = 82.0,
            hipCircumference = null,
            shoulderWidth = null,
            inseamLength = null
        )
        
        val result = BodyEstimator.estimateBodyMeasurements(
            height, weight, gender, region, optional
        )
        
        // Should use provided waist
        assertEquals("Should use provided waist", 82.0, result.waist, 0.1)
        
        // Others should be estimated
        assertTrue("Chest should be estimated", result.chest > 0)
        assertTrue("Hip should be estimated", result.hip > 0)
        
        // Waist confidence should be high (measured)
        val waistConfidence = result.confidence.breakdown["waist"]?.value ?: 0.0
        assertTrue("Measured waist should have >=98% confidence, got $waistConfidence",
            waistConfidence >= 0.98)
    }
    
    // Test 3: Hip Only
    @Test
    fun testHipOnly() {
        val height = 175.0
        val weight = 70.0
        val gender = Gender.MALE
        val region = SizeRegion.US
        
        val optional = OptionalMeasurements(
            chestCircumference = null,
            waistCircumference = null,
            hipCircumference = 96.0,
            shoulderWidth = null,
            inseamLength = null
        )
        
        val result = BodyEstimator.estimateBodyMeasurements(
            height, weight, gender, region, optional
        )
        
        // Should use provided hip
        assertEquals("Should use provided hip", 96.0, result.hip, 0.1)
        
        // Hip confidence should be high (measured)
        val hipConfidence = result.confidence.breakdown["hip"]?.value ?: 0.0
        assertTrue("Measured hip should have >=98% confidence, got $hipConfidence",
            hipConfidence >= 0.98)
    }
    
    // Test 4: Shoulder Only
    @Test
    fun testShoulderOnly() {
        val height = 175.0
        val weight = 70.0
        val gender = Gender.MALE
        val region = SizeRegion.US
        
        val optional = OptionalMeasurements(
            chestCircumference = null,
            waistCircumference = null,
            hipCircumference = null,
            shoulderWidth = 44.0,
            inseamLength = null
        )
        
        val result = BodyEstimator.estimateBodyMeasurements(
            height, weight, gender, region, optional
        )
        
        // Should use provided shoulder
        assertEquals("Should use provided shoulder", 44.0, result.shoulder, 0.1)
        
        // Shoulder confidence should be high (measured)
        val shoulderConfidence = result.confidence.breakdown["shoulder"]?.value ?: 0.0
        assertTrue("Measured shoulder should have >=98% confidence, got $shoulderConfidence",
            shoulderConfidence >= 0.98)
    }
    
    // Test 5: Inseam Only
    @Test
    fun testInseamOnly() {
        val height = 175.0
        val weight = 70.0
        val gender = Gender.MALE
        val region = SizeRegion.US
        
        val optional = OptionalMeasurements(
            chestCircumference = null,
            waistCircumference = null,
            hipCircumference = null,
            shoulderWidth = null,
            inseamLength = 78.0
        )
        
        val result = BodyEstimator.estimateBodyMeasurements(
            height, weight, gender, region, optional
        )
        
        // Should use provided inseam
        assertEquals("Should use provided inseam", 78.0, result.inseam, 0.1)
        
        // Inseam confidence should be high (measured)
        val inseamConfidence = result.confidence.breakdown["inseam"]?.value ?: 0.0
        assertTrue("Measured inseam should have >=98% confidence, got $inseamConfidence",
            inseamConfidence >= 0.98)
    }
    
    // Test 6: All Optional Measurements
    @Test
    fun testAllOptionalMeasurements() {
        val height = 175.0
        val weight = 70.0
        val gender = Gender.MALE
        val region = SizeRegion.US
        
        val optional = OptionalMeasurements(
            chestCircumference = 98.0,
            waistCircumference = 82.0,
            hipCircumference = 96.0,
            shoulderWidth = 44.0,
            inseamLength = 78.0
        )
        
        val result = BodyEstimator.estimateBodyMeasurements(
            height, weight, gender, region, optional
        )
        
        // All should use provided values
        assertEquals("Should use provided chest", 98.0, result.chest, 0.1)
        assertEquals("Should use provided waist", 82.0, result.waist, 0.1)
        assertEquals("Should use provided hip", 96.0, result.hip, 0.1)
        assertEquals("Should use provided shoulder", 44.0, result.shoulder, 0.1)
        assertEquals("Should use provided inseam", 78.0, result.inseam, 0.1)
        
        // Confidence should be very high (all measured)
        assertTrue("With all optional measurements, confidence should be >=90%, got ${result.confidence.overall}",
            result.confidence.overall >= 0.90)
        
        // All measurements should have high confidence
        result.confidence.breakdown.forEach { (dimension, confidence) ->
            assertTrue("$dimension should have >=98% confidence when measured, got ${confidence.value}",
                confidence.value >= 0.98)
            assertEquals("$dimension should be marked as MEASURED", 
                EstimationMethod.MEASURED, confidence.method)
        }
    }
    
    // Test 7: Partial Combinations - Chest + Waist
    @Test
    fun testChestAndWaist() {
        val height = 175.0
        val weight = 70.0
        val gender = Gender.MALE
        val region = SizeRegion.US
        
        val optional = OptionalMeasurements(
            chestCircumference = 98.0,
            waistCircumference = 82.0,
            hipCircumference = null,
            shoulderWidth = null,
            inseamLength = null
        )
        
        val result = BodyEstimator.estimateBodyMeasurements(
            height, weight, gender, region, optional
        )
        
        assertEquals("Should use provided chest", 98.0, result.chest, 0.1)
        assertEquals("Should use provided waist", 82.0, result.waist, 0.1)
        
        // Confidence should be higher than base
        assertTrue("Confidence should be >=0.85 with chest+waist, got ${result.confidence.overall}",
            result.confidence.overall >= 0.85)
    }
    
    // Test 8: Partial Combinations - Hip + Inseam
    @Test
    fun testHipAndInseam() {
        val height = 175.0
        val weight = 70.0
        val gender = Gender.MALE
        val region = SizeRegion.US
        
        val optional = OptionalMeasurements(
            chestCircumference = null,
            waistCircumference = null,
            hipCircumference = 96.0,
            shoulderWidth = null,
            inseamLength = 78.0
        )
        
        val result = BodyEstimator.estimateBodyMeasurements(
            height, weight, gender, region, optional
        )
        
        assertEquals("Should use provided hip", 96.0, result.hip, 0.1)
        assertEquals("Should use provided inseam", 78.0, result.inseam, 0.1)
    }
    
    // Test 9: Female with All Optional Measurements
    @Test
    fun testFemaleAllOptionalMeasurements() {
        val height = 165.0
        val weight = 60.0
        val gender = Gender.FEMALE
        val region = SizeRegion.US
        
        val optional = OptionalMeasurements(
            chestCircumference = 88.0,
            waistCircumference = 72.0,
            hipCircumference = 92.0,
            shoulderWidth = 38.0,
            inseamLength = 72.0
        )
        
        val result = BodyEstimator.estimateBodyMeasurements(
            height, weight, gender, region, optional
        )
        
        // All should use provided values
        assertEquals("Should use provided chest", 88.0, result.chest, 0.1)
        assertEquals("Should use provided waist", 72.0, result.waist, 0.1)
        assertEquals("Should use provided hip", 92.0, result.hip, 0.1)
        assertEquals("Should use provided shoulder", 38.0, result.shoulder, 0.1)
        assertEquals("Should use provided inseam", 72.0, result.inseam, 0.1)
        
        // Confidence should be very high
        assertTrue("Female with all measurements should have >=90% confidence, got ${result.confidence.overall}",
            result.confidence.overall >= 0.90)
    }
    
    // Test 10: Asian Male with Optional Measurements
    @Test
    fun testAsianMaleWithOptionalMeasurements() {
        val height = 170.0
        val weight = 65.0
        val gender = Gender.MALE
        val region = SizeRegion.ASIA
        
        val optional = OptionalMeasurements(
            chestCircumference = 92.0,
            waistCircumference = 76.0,
            hipCircumference = 90.0,
            shoulderWidth = 42.0,
            inseamLength = 75.0
        )
        
        val result = BodyEstimator.estimateBodyMeasurements(
            height, weight, gender, region, optional
        )
        
        // All should use provided values (regional normalization doesn't affect provided measurements)
        assertEquals("Should use provided chest", 92.0, result.chest, 0.1)
        assertEquals("Should use provided waist", 76.0, result.waist, 0.1)
        assertEquals("Should use provided hip", 90.0, result.hip, 0.1)
        assertEquals("Should use provided shoulder", 42.0, result.shoulder, 0.1)
        assertEquals("Should use provided inseam", 75.0, result.inseam, 0.1)
    }
}

