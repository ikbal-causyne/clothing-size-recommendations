package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*

/**
 * Input Validation - PRD Section 13.1
 * Validates user inputs and size charts
 */
object InputValidator {
    
    fun validateHeight(height: Double): ValidationResult {
        return when {
            height < 130 -> ValidationResult.Error("Height too low. Must be at least 130cm")
            height > 230 -> ValidationResult.Error("Height too high. Must be at most 230cm")
            else -> ValidationResult.Success
        }
    }
    
    fun validateWeight(weight: Double): ValidationResult {
        return when {
            weight < 30 -> ValidationResult.Error("Weight too low. Must be at least 30kg")
            weight > 200 -> ValidationResult.Error("Weight too high. Must be at most 200kg")
            else -> ValidationResult.Success
        }
    }
    
    fun validateSizeChart(sizeChart: List<SizeChartEntry>): ValidationResult {
        return when {
            sizeChart.isEmpty() -> 
                ValidationResult.Error("Size chart is empty")
            sizeChart.size < 2 -> 
                ValidationResult.Warning("Size chart has only one size. Add more for better recommendations")
            else -> {
                // Engine Vision: Never reject - use probabilistic approach
                // Count sizes with measurements vs without
                val sizesWithMeasurements = sizeChart.count { 
                    it.chest != null || it.waist != null || it.hip != null 
                }
                val sizesWithoutMeasurements = sizeChart.size - sizesWithMeasurements
                
                // If most sizes have measurements, it's acceptable
                if (sizesWithoutMeasurements > 0 && sizesWithoutMeasurements <= sizeChart.size / 2) {
                    ValidationResult.Warning(
                        "Some sizes (${sizesWithoutMeasurements}/${sizeChart.size}) have incomplete measurements. " +
                        "Recommendations will use available data with reduced confidence."
                    )
                } else if (sizesWithoutMeasurements > sizeChart.size / 2) {
                    ValidationResult.Warning(
                        "Many sizes (${sizesWithoutMeasurements}/${sizeChart.size}) are missing measurements. " +
                        "Consider adding more measurement data for better accuracy."
                    )
                } else {
                    ValidationResult.Success
                }
            }
        }
    }
    
    fun validateUserInput(userInput: UserBodyInput): ValidationResult {
        val heightResult = validateHeight(userInput.height)
        if (heightResult is ValidationResult.Error) return heightResult
        
        val weightResult = validateWeight(userInput.weight)
        if (weightResult is ValidationResult.Error) return weightResult
        
        return ValidationResult.Success
    }
    
    /**
     * ✅ FIX #24: Validate optional measurements against height/weight/gender
     * Clears invalid measurements when mandatory inputs change
     */
    fun validateOptionalMeasurements(
        optional: OptionalMeasurements,
        height: Double,
        weight: Double,
        gender: Gender
    ): ValidationResult {
        // Reasonable ranges (in cm) - based on anthropometric data
        val chestRange = when (gender) {
            Gender.MALE -> {
                // Male: typically 90-130cm for normal builds
                val baseChest = height * 0.5 + weight * 0.3
                (baseChest * 0.7)..(baseChest * 1.3)
            }
            Gender.FEMALE -> {
                // Female: typically 80-120cm for normal builds
                val baseChest = height * 0.45 + weight * 0.25
                (baseChest * 0.7)..(baseChest * 1.3)
            }
        }
        
        val waistRange = when (gender) {
            Gender.MALE -> {
                // Male: typically 70-120cm
                val baseWaist = height * 0.35 + weight * 0.4
                (baseWaist * 0.7)..(baseWaist * 1.4)
            }
            Gender.FEMALE -> {
                // Female: typically 60-110cm
                val baseWaist = height * 0.30 + weight * 0.35
                (baseWaist * 0.7)..(baseWaist * 1.4)
            }
        }
        
        val hipRange = when (gender) {
            Gender.MALE -> {
                // Male: typically 85-115cm
                val baseHip = height * 0.40 + weight * 0.35
                (baseHip * 0.75)..(baseHip * 1.3)
            }
            Gender.FEMALE -> {
                // Female: typically 85-120cm
                val baseHip = height * 0.42 + weight * 0.38
                (baseHip * 0.75)..(baseHip * 1.3)
            }
        }
        
        val shoulderRange = when (gender) {
            Gender.MALE -> {
                // Male: typically 38-50cm
                val baseShoulder = height * 0.22
                (baseShoulder * 0.85)..(baseShoulder * 1.15)
            }
            Gender.FEMALE -> {
                // Female: typically 35-45cm
                val baseShoulder = height * 0.20
                (baseShoulder * 0.85)..(baseShoulder * 1.15)
            }
        }
        
        val inseamRange = when (gender) {
            Gender.MALE -> {
                // Male: typically 70-90cm (inseam is ~45% of height)
                val baseInseam = height * 0.45
                (baseInseam * 0.85)..(baseInseam * 1.15)
            }
            Gender.FEMALE -> {
                // Female: typically 65-85cm
                val baseInseam = height * 0.43
                (baseInseam * 0.85)..(baseInseam * 1.15)
            }
        }
        
        // Validate each provided measurement
        optional.chestCircumference?.let { chest ->
            if (chest !in chestRange) {
                return ValidationResult.Error(
                    "Chest measurement (${chest}cm) is not reasonable for height ${height}cm, weight ${weight}kg. " +
                    "Expected range: ${chestRange.start.toInt()}-${chestRange.endInclusive.toInt()}cm"
                )
            }
        }
        
        optional.waistCircumference?.let { waist ->
            if (waist !in waistRange) {
                return ValidationResult.Error(
                    "Waist measurement (${waist}cm) is not reasonable for height ${height}cm, weight ${weight}kg. " +
                    "Expected range: ${waistRange.start.toInt()}-${waistRange.endInclusive.toInt()}cm"
                )
            }
        }
        
        optional.hipCircumference?.let { hip ->
            if (hip !in hipRange) {
                return ValidationResult.Error(
                    "Hip measurement (${hip}cm) is not reasonable for height ${height}cm, weight ${weight}kg. " +
                    "Expected range: ${hipRange.start.toInt()}-${hipRange.endInclusive.toInt()}cm"
                )
            }
        }
        
        optional.shoulderWidth?.let { shoulder ->
            if (shoulder !in shoulderRange) {
                return ValidationResult.Error(
                    "Shoulder measurement (${shoulder}cm) is not reasonable for height ${height}cm. " +
                    "Expected range: ${shoulderRange.start.toInt()}-${shoulderRange.endInclusive.toInt()}cm"
                )
            }
        }
        
        optional.inseamLength?.let { inseam ->
            if (inseam !in inseamRange) {
                return ValidationResult.Error(
                    "Inseam measurement (${inseam}cm) is not reasonable for height ${height}cm. " +
                    "Expected range: ${inseamRange.start.toInt()}-${inseamRange.endInclusive.toInt()}cm"
                )
            }
        }
        
        return ValidationResult.Success
    }
}

