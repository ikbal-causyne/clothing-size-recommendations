package com.vanexa.supercalculator.domain.model

import java.io.Serializable

/**
 * Garment measurements from size chart for fit comparison
 * Represents a single size entry from a clothing size chart
 */
data class GarmentMeasurements(
    val sizeLabel: String,                    // "M", "38", "Large", etc.
    val garmentType: GarmentType,             // Determines which dimensions are critical
    val chestCm: Double? = null,              // Upper body circumference
    val waistCm: Double? = null,              // Waist circumference
    val hipCm: Double? = null,                // Hip circumference
    val shoulderCm: Double? = null,           // Shoulder width
    val sleeveCm: Double? = null,             // Sleeve length
    val lengthCm: Double? = null,             // Garment length/inseam
    val ocrConfidence: Double = 0.8,          // Confidence in OCR-extracted measurements (0.0-1.0)
    val source: String = "Manual"             // "OCR", "Manual", "Database"
) : Serializable
