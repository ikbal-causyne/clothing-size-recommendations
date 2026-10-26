package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

/**
 * Regional Normalizer Validation Test - Extreme Testing Phase 1
 * Validates regional normalization coefficients per PRD Section 6.2
 */
class RegionalNormalizerValidationTest {
    
    @Test
    fun testUSNormalization() {
        val sizeChart = SizeChartEntry(
            "M",
            chest = MeasurementRange(94.0, 98.0),
            waist = MeasurementRange(80.0, 84.0),
            hip = MeasurementRange(96.0, 100.0)
        )
        
        val normalized = RegionalFitNormalizer.normalizeGarmentMeasurements(
            sizeChart, SizeRegion.US
        )
        
        // US factors: chest=1.03, waist=1.04, hip=1.02 (PRD Section 6.2)
        // IMPORTANT: Normalization uses DIVISION (inverted logic)
        assertEquals("US chest min should be 94.0 / 1.03", 94.0 / 1.03, normalized.chest!!.min, 0.1)
        assertEquals("US chest max should be 98.0 / 1.03", 98.0 / 1.03, normalized.chest!!.max, 0.1)
        assertEquals("US waist min should be 80.0 / 1.04", 80.0 / 1.04, normalized.waist!!.min, 0.1)
        assertEquals("US waist max should be 84.0 / 1.04", 84.0 / 1.04, normalized.waist!!.max, 0.1)
        assertEquals("US hip min should be 96.0 / 1.02", 96.0 / 1.02, normalized.hip!!.min, 0.1)
        assertEquals("US hip max should be 100.0 / 1.02", 100.0 / 1.02, normalized.hip!!.max, 0.1)
    }
    
    @Test
    fun testASIANormalization() {
        val sizeChart = SizeChartEntry(
            "M",
            chest = MeasurementRange(94.0, 98.0),
            waist = MeasurementRange(80.0, 84.0),
            hip = MeasurementRange(96.0, 100.0)
        )
        
        val normalized = RegionalFitNormalizer.normalizeGarmentMeasurements(
            sizeChart, SizeRegion.ASIA
        )
        
        // ASIA factors: chest=0.97, waist=0.95, hip=0.97 (PRD Section 6.2)
        // IMPORTANT: Normalization uses DIVISION (inverted logic)
        assertEquals("ASIA chest min should be 94.0 / 0.97", 94.0 / 0.97, normalized.chest!!.min, 0.1)
        assertEquals("ASIA chest max should be 98.0 / 0.97", 98.0 / 0.97, normalized.chest!!.max, 0.1)
        assertEquals("ASIA waist min should be 80.0 / 0.95", 80.0 / 0.95, normalized.waist!!.min, 0.1)
        assertEquals("ASIA waist max should be 84.0 / 0.95", 84.0 / 0.95, normalized.waist!!.max, 0.1)
        assertEquals("ASIA hip min should be 96.0 / 0.97", 96.0 / 0.97, normalized.hip!!.min, 0.1)
        assertEquals("ASIA hip max should be 100.0 / 0.97", 100.0 / 0.97, normalized.hip!!.max, 0.1)
    }
    
    @Test
    fun testIDNormalization() {
        val sizeChart = SizeChartEntry(
            "M",
            chest = MeasurementRange(94.0, 98.0)
        )
        
        val normalized = RegionalFitNormalizer.normalizeGarmentMeasurements(
            sizeChart, SizeRegion.ID
        )
        
        // ID factors: chest=0.96, waist=0.94, hip=0.96 (PRD Section 6.2)
        // IMPORTANT: Normalization uses DIVISION (inverted logic)
        assertEquals("ID chest min should be 94.0 / 0.96", 94.0 / 0.96, normalized.chest!!.min, 0.1)
        assertEquals("ID chest max should be 98.0 / 0.96", 98.0 / 0.96, normalized.chest!!.max, 0.1)
    }
    
    @Test
    fun testEUNormalization() {
        val sizeChart = SizeChartEntry(
            "M",
            chest = MeasurementRange(94.0, 98.0)
        )
        
        val normalized = RegionalFitNormalizer.normalizeGarmentMeasurements(
            sizeChart, SizeRegion.EU
        )
        
        // EU factors: chest=1.00, waist=1.00, hip=1.00 (baseline)
        // IMPORTANT: Normalization uses DIVISION (inverted logic)
        assertEquals("EU chest min should be unchanged (1.00)", 94.0 / 1.00, normalized.chest!!.min, 0.1)
        assertEquals("EU chest max should be unchanged (1.00)", 98.0 / 1.00, normalized.chest!!.max, 0.1)
    }
    
    @Test
    fun testUKNormalization() {
        val sizeChart = SizeChartEntry(
            "M",
            chest = MeasurementRange(94.0, 98.0)
        )
        
        val normalized = RegionalFitNormalizer.normalizeGarmentMeasurements(
            sizeChart, SizeRegion.UK
        )
        
        // UK factors: chest=1.01, waist=1.01, hip=1.00 (PRD Section 6.2)
        // IMPORTANT: Normalization uses DIVISION (inverted logic)
        assertEquals("UK chest min should be 94.0 / 1.01", 94.0 / 1.01, normalized.chest!!.min, 0.1)
        assertEquals("UK chest max should be 98.0 / 1.01", 98.0 / 1.01, normalized.chest!!.max, 0.1)
    }
}

