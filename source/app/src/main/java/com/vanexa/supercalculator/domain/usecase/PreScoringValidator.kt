package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import android.util.Log

/**
 * PRE-SCORING VALIDATION per Engine Vision Update
 * Validates body measurements sanity, size chart consistency, OCR anomalies
 * This is a skeptical decision system - validates before scoring
 */
object PreScoringValidator {
    private const val TAG = "PreScoringValidator"
    
    /**
     * Validation result with structured signals
     */
    data class ValidationResult(
        val isValid: Boolean,
        val issues: List<ValidationIssue>,
        val confidence: Double  // Overall validation confidence
    )
    
    data class ValidationIssue(
        val code: IssueCode,
        val severity: IssueSeverity,
        val message: String,  // Technical message for logging
        val metric: Double? = null  // Numeric value if applicable
    )
    
    enum class IssueCode {
        BODY_MEASUREMENT_OUT_OF_RANGE,
        BODY_MEASUREMENT_INCONSISTENT,
        CHART_EMPTY,
        CHART_INCONSISTENT_PROGRESSION,
        CHART_UNIT_MISMATCH,
        CHART_INVERTED_RANGE,
        OCR_ANOMALY_DETECTED,
        MISSING_CRITICAL_DIMENSION
    }
    
    enum class IssueSeverity {
        CRITICAL,  // Cannot proceed
        HIGH,      // Proceed with low confidence
        MEDIUM,    // Warning
        LOW        // Info
    }
    
    /**
     * Main validation entry point
     */
    fun validate(
        userBody: EstimatedBodyMeasurements,
        sizeChart: List<SizeChartEntry>,
        garmentType: GarmentType
    ): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        
        // 1. Validate body measurements sanity
        issues.addAll(validateBodyMeasurements(userBody))
        
        // 2. Validate size chart consistency
        issues.addAll(validateSizeChart(sizeChart, garmentType))
        
        // 3. Detect OCR anomalies
        issues.addAll(detectOcrAnomalies(sizeChart))
        
        // 4. Check for missing critical dimensions
        issues.addAll(checkMissingCriticalDimensions(sizeChart, garmentType))
        
        // Calculate overall confidence
        val confidence = calculateValidationConfidence(issues)
        
        val isValid = issues.none { it.severity == IssueSeverity.CRITICAL }
        
        Log.d(TAG, "🎯 [Engine Vision Update] Validation: isValid=$isValid, issues=${issues.size}, confidence=$confidence")
        issues.forEach { issue ->
            Log.w(TAG, "⚠️ [Engine Vision Update] ${issue.severity}: ${issue.code} - ${issue.message}")
        }
        
        return ValidationResult(
            isValid = isValid,
            issues = issues,
            confidence = confidence
        )
    }
    
    /**
     * 1. Validate body measurements sanity
     */
    private fun validateBodyMeasurements(userBody: EstimatedBodyMeasurements): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        
        // Check measurement ranges (reasonable human body ranges)
        val ranges = mapOf(
            "chest" to Pair(50.0, 150.0),
            "waist" to Pair(40.0, 150.0),
            "hip" to Pair(50.0, 150.0),
            "shoulder" to Pair(30.0, 60.0),
            "inseam" to Pair(50.0, 100.0)
        )
        
        val measurements = mapOf(
            "chest" to userBody.chest,
            "waist" to userBody.waist,
            "hip" to userBody.hip,
            "shoulder" to userBody.shoulder,
            "inseam" to userBody.inseam
        )
        
        measurements.forEach { (dimension, value) ->
            // Skip check if value is 0.0 (not applicable for this garment/estimation)
            if (value <= 0.0) return@forEach
            
            val range = ranges[dimension] ?: return@forEach
            if (value < range.first || value > range.second) {
                issues.add(ValidationIssue(
                    code = IssueCode.BODY_MEASUREMENT_OUT_OF_RANGE,
                    severity = IssueSeverity.CRITICAL,
                    message = "$dimension measurement $value cm is outside reasonable range [${range.first}, ${range.second}]",
                    metric = value
                ))
            }
        }
        
        // Check measurement consistency (e.g., waist should be < chest for most people)
        if (userBody.chest > 0 && userBody.waist > 0 && userBody.waist > userBody.chest * 1.1) {
            issues.add(ValidationIssue(
                code = IssueCode.BODY_MEASUREMENT_INCONSISTENT,
                severity = IssueSeverity.HIGH,
                message = "Waist (${userBody.waist}cm) is unusually large compared to chest (${userBody.chest}cm)",
                metric = userBody.waist / userBody.chest
            ))
        }
        
        // Hip should generally be >= waist
        if (userBody.waist > 0 && userBody.hip > 0 && userBody.hip < userBody.waist * 0.9) {
            issues.add(ValidationIssue(
                code = IssueCode.BODY_MEASUREMENT_INCONSISTENT,
                severity = IssueSeverity.MEDIUM,
                message = "Hip (${userBody.hip}cm) is unusually small compared to waist (${userBody.waist}cm)",
                metric = userBody.hip / userBody.waist
            ))
        }
        
        return issues
    }
    
    /**
     * 2. Validate size chart consistency
     */
    private fun validateSizeChart(sizeChart: List<SizeChartEntry>, garmentType: GarmentType): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        
        if (sizeChart.isEmpty()) {
            issues.add(ValidationIssue(
                code = IssueCode.CHART_EMPTY,
                severity = IssueSeverity.CRITICAL,
                message = "Size chart is empty"
            ))
            return issues
        }
        
        // Check progression consistency (sizes should increase monotonically)
        val primaryDimension = when (garmentType) {
            GarmentType.TSHIRT, GarmentType.SHIRT, GarmentType.POLO_SHIRT, 
            GarmentType.HOODIE, GarmentType.SWEATER, GarmentType.JACKET, GarmentType.BLAZER -> "chest"
            else -> "waist"
        }
        
        val measurements = sizeChart.mapNotNull { entry ->
            when (primaryDimension) {
                "chest" -> entry.chest?.let { (it.min + it.max) / 2.0 }
                "waist" -> entry.waist?.let { (it.min + it.max) / 2.0 }
                else -> null
            }
        }
        
        if (measurements.size >= 2) {
            for (i in 1 until measurements.size) {
                val diff = measurements[i] - measurements[i - 1]
                // Expected increment: 4-8cm per size
                if (diff < 2.0 || diff > 12.0) {
                    issues.add(ValidationIssue(
                        code = IssueCode.CHART_INCONSISTENT_PROGRESSION,
                        severity = IssueSeverity.HIGH,
                        message = "Suspicious size progression: ${diff}cm difference in $primaryDimension between sizes",
                        metric = diff
                    ))
                }
            }
        }
        
        return issues
    }
    
    /**
     * 3. Detect OCR anomalies
     */
    private fun detectOcrAnomalies(sizeChart: List<SizeChartEntry>): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        
        sizeChart.forEach { entry ->
            // Check for unit mismatch (mixed CM and INCH)
            val units = listOfNotNull(
                entry.chest?.unit,
                entry.waist?.unit,
                entry.hip?.unit,
                entry.shoulder?.unit,
                entry.length?.unit,
                entry.inseam?.unit
            ).distinct()
            
            if (units.size > 1) {
                issues.add(ValidationIssue(
                    code = IssueCode.CHART_UNIT_MISMATCH,
                    severity = IssueSeverity.HIGH,
                    message = "Size ${entry.sizeLabel} has mixed units: ${units.joinToString(", ")}"
                ))
            }
            
            // Check for inverted ranges (min > max)
            listOfNotNull(
                entry.chest?.let { "chest" to it },
                entry.waist?.let { "waist" to it },
                entry.hip?.let { "hip" to it },
                entry.shoulder?.let { "shoulder" to it },
                entry.length?.let { "length" to it },
                entry.inseam?.let { "inseam" to it }
            ).forEach { (dimension, range) ->
                if (range.min > range.max) {
                    issues.add(ValidationIssue(
                        code = IssueCode.CHART_INVERTED_RANGE,
                        severity = IssueSeverity.CRITICAL,
                        message = "Size ${entry.sizeLabel} has inverted $dimension range: min=${range.min} > max=${range.max}",
                        metric = range.min - range.max
                    ))
                }
            }
            
            // Check for suspiciously small or large ranges (OCR might have misread)
            listOfNotNull(
                entry.chest?.let { "chest" to it },
                entry.waist?.let { "waist" to it },
                entry.hip?.let { "hip" to it }
            ).forEach { (dimension, range) ->
                val rangeWidth = range.max - range.min
                if (rangeWidth < 0.5) {
                    issues.add(ValidationIssue(
                        code = IssueCode.OCR_ANOMALY_DETECTED,
                        severity = IssueSeverity.MEDIUM,
                        message = "Size ${entry.sizeLabel} $dimension range is suspiciously narrow: ${rangeWidth}cm (possible OCR error)",
                        metric = rangeWidth
                    ))
                }
                if (rangeWidth > 15.0) {
                    issues.add(ValidationIssue(
                        code = IssueCode.OCR_ANOMALY_DETECTED,
                        severity = IssueSeverity.MEDIUM,
                        message = "Size ${entry.sizeLabel} $dimension range is suspiciously wide: ${rangeWidth}cm (possible OCR error)",
                        metric = rangeWidth
                    ))
                }
            }
        }
        
        return issues
    }
    
    /**
     * 4. Check for missing critical dimensions
     */
    private fun checkMissingCriticalDimensions(
        sizeChart: List<SizeChartEntry>,
        garmentType: GarmentType
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        
        val requiredDimensions = when (garmentType) {
            GarmentType.TSHIRT, GarmentType.SHIRT, GarmentType.POLO_SHIRT,
            GarmentType.HOODIE, GarmentType.SWEATER, GarmentType.JACKET, GarmentType.BLAZER -> 
                listOf("chest")
            else -> listOf("waist")
        }
        
        sizeChart.forEach { entry ->
            requiredDimensions.forEach { dimension ->
                val hasDimension = when (dimension) {
                    "chest" -> entry.chest != null
                    "waist" -> entry.waist != null
                    else -> false
                }
                
                if (!hasDimension) {
                    issues.add(ValidationIssue(
                        code = IssueCode.MISSING_CRITICAL_DIMENSION,
                        severity = IssueSeverity.HIGH,
                        message = "Size ${entry.sizeLabel} missing critical dimension: $dimension for $garmentType"
                    ))
                }
            }
        }
        
        return issues
    }
    
    /**
     * Calculate validation confidence based on issues
     */
    private fun calculateValidationConfidence(issues: List<ValidationIssue>): Double {
        var confidence = 1.0
        
        issues.forEach { issue ->
            when (issue.severity) {
                IssueSeverity.CRITICAL -> confidence *= 0.0  // Cannot proceed
                IssueSeverity.HIGH -> confidence *= 0.7
                IssueSeverity.MEDIUM -> confidence *= 0.9
                IssueSeverity.LOW -> confidence *= 0.95
            }
        }
        
        return confidence.coerceIn(0.0, 1.0)
    }
}

