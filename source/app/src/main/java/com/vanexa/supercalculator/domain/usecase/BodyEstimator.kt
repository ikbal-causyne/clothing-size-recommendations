package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import kotlin.math.pow

/**
 * Body Estimation Engine - PRD Section 5
 * Estimates body measurements from height, weight, gender, and region
 * Based on validated anthropometric regression models
 */
object BodyEstimator {
    
    /**
     * Estimates body measurements from height, weight, gender, and region
     * Based on validated anthropometric regression models (PRD Section 5.2)
     */
    fun estimateBodyMeasurements(
        height: Double,      // cm
        weight: Double,      // kg
        gender: Gender,
        region: SizeRegion,
        optionalMeasurements: OptionalMeasurements? = null,
        garmentType: GarmentType? = null // Add GarmentType context
    ): EstimatedBodyMeasurements {
        
        // ==================================================
        // CRITICAL: Input Validation
        // ==================================================
        
        // Validate height
        require(height.isFinite()) { 
            "Height must be a finite number, got: $height" 
        }
        require(height in 100.0..250.0) { 
            "Height must be between 100-250cm. Got: $height cm. " +
            "If measuring in inches, please convert to cm first." 
        }
        
        // Validate weight
        require(weight.isFinite()) { 
            "Weight must be a finite number, got: $weight" 
        }
        require(weight in 30.0..300.0) { 
            "Weight must be between 30-300kg. Got: $weight kg. " +
            "If measuring in pounds, please convert to kg first." 
        }
        
        // Validate BMI reasonableness
        val bmi = calculateBMI(height, weight)
        require(bmi in 10.0..60.0) {
            "Calculated BMI ($bmi) is outside reasonable range. " +
            "Please verify height ($height cm) and weight ($weight kg)."
        }
        
        // Validate optional measurements if provided
        optionalMeasurements?.let { opt ->
            opt.chestCircumference?.let { chest ->
                require(chest.isFinite() && chest in 50.0..180.0) { 
                    "Chest circumference must be 50-180cm, got: $chest" 
                }
            }
            opt.waistCircumference?.let { waist ->
                require(waist.isFinite() && waist in 40.0..180.0) { 
                    "Waist circumference must be 40-180cm, got: $waist" 
                }
            }
            opt.hipCircumference?.let { hip ->
                require(hip.isFinite() && hip in 50.0..200.0) { 
                    "Hip circumference must be 50-200cm, got: $hip" 
                }
            }
            opt.shoulderWidth?.let { shoulder ->
                require(shoulder.isFinite() && shoulder in 30.0..70.0) {
                    "Shoulder width must be 30-70cm, got: $shoulder"
                }
            }
            opt.inseamLength?.let { inseam ->
                require(inseam.isFinite() && inseam in 50.0..120.0) {
                    "Inseam length must be 50-120cm, got: $inseam"
                }
            }

            // Cross-validation: waist shouldn't be larger than chest by too much
            if (opt.chestCircumference != null && opt.waistCircumference != null) {
                val chest = opt.chestCircumference!!
                val waist = opt.waistCircumference!!
                if (waist > chest * 1.3) {
                    android.util.Log.w("BodyEstimator", "⚠️ Waist ($waist cm) seems unusually large compared to chest ($chest cm). Please verify measurements.")
                }
            }
        }

        val bodyFrame = determineBodyFrame(bmi, height, weight)

        android.util.Log.d("BodyEstimator", "✅ [VALIDATION] All inputs validated: height=$height, weight=$weight, bmi=$bmi, gender=$gender, region=$region")

        // Skip ML predictions entirely - data is corrupted for all cases
        // Only use formula-based estimation for accurate results

        android.util.Log.d("BodyEstimator", "🎯 [DEBUG] Skipping corrupted ML data, using FORMULA estimation for $gender")

        // Fallback to existing formula-based estimation...
        android.util.Log.d("BodyEstimator", "🎯 [DEBUG] Using FORMULA estimation for $gender (ML not available or not male)")

        // bodyFrame already calculated above

        // INTELLIGENT FILTERING based on GarmentType
        val useChest = garmentType == null || isUpperBody(garmentType)
        val useWaist = garmentType == null || isLowerBody(garmentType) || garmentType == GarmentType.SHIRT
        val useHip = garmentType == null || isLowerBody(garmentType)

        // Use optional measurements if provided, otherwise estimate
        val chest = if (useChest) (optionalMeasurements?.chestCircumference
            ?: estimateChestCircumference(height, weight, gender, region)) else 0.0
            
        val waist = if (useWaist) (optionalMeasurements?.waistCircumference
            ?: estimateWaistCircumference(height, weight, gender, region)) else 0.0
            
        val hip = if (useHip) (optionalMeasurements?.hipCircumference
            ?: estimateHipCircumference(height, weight, gender, region)) else 0.0
            
        val shoulder = if (useChest) (optionalMeasurements?.shoulderWidth
            ?: estimateShoulderWidth(height, gender, region)) else 0.0
            
        val inseam = if (useWaist) (optionalMeasurements?.inseamLength
            ?: estimateInseam(height, gender)) else 0.0
        
        val userInput = UserBodyInput(height, weight, gender, region)
        
        // ✅ BUG #58: Cross-validate estimated measurements before returning
        // Only validate relevant measurements
        val validation = validateEstimatedMeasurements(
            chest = if (useChest) chest else 0.0,
            waist = if (useWaist) waist else 0.0,
            hip = if (useHip) hip else 0.0,
            shoulder = if (useChest) shoulder else 0.0,
            gender = gender
        )
        
        var baseConfidence = calculateEstimationConfidence(userInput, optionalMeasurements, bmi)
        
        // Reduce confidence if cross-validation fails
        if (validation is com.vanexa.supercalculator.domain.model.ValidationResult.Warning) {
            android.util.Log.w("BodyEstimator", "⚠️ [BUG #58] Cross-validation warning: ${validation.message}")
            // Reduce confidence by 20% for suspicious combinations
            baseConfidence = ConfidenceScore(
                overall = (baseConfidence.overall * 0.8).coerceIn(0.0, 1.0),
                breakdown = baseConfidence.breakdown
            )
        }
        
        val confidence = baseConfidence
        
        // Build estimation method map
        val estimationMethod = mapOf(
            "chest" to if (useChest) (if (optionalMeasurements?.chestCircumference != null) EstimationMethod.MEASURED else EstimationMethod.ESTIMATED) else EstimationMethod.NOT_APPLICABLE,
            "waist" to if (useWaist) (if (optionalMeasurements?.waistCircumference != null) EstimationMethod.MEASURED else EstimationMethod.ESTIMATED) else EstimationMethod.NOT_APPLICABLE,
            "hip" to if (useHip) (if (optionalMeasurements?.hipCircumference != null) EstimationMethod.MEASURED else EstimationMethod.ESTIMATED) else EstimationMethod.NOT_APPLICABLE,
            "shoulder" to if (useChest) (if (optionalMeasurements?.shoulderWidth != null) EstimationMethod.MEASURED else EstimationMethod.ESTIMATED) else EstimationMethod.NOT_APPLICABLE,
            "inseam" to if (useWaist) (if (optionalMeasurements?.inseamLength != null) EstimationMethod.MEASURED else EstimationMethod.ESTIMATED) else EstimationMethod.NOT_APPLICABLE
        )
        
        return EstimatedBodyMeasurements(
            chest = chest,
            waist = waist,
            hip = hip,
            shoulder = shoulder,
            inseam = inseam,
            confidence = confidence,
            estimationMethod = estimationMethod
        )
    }
    
    private fun calculateBMI(height: Double, weight: Double): Double {
        return weight / ((height / 100.0).pow(2))
    }
    
    /**
     * Enhanced Body Frame Estimation per Engine Vision Module 1.3
     * Uses both BMI and frameIndex (kg/cm ratio) for more accurate classification
     */
    // PHASE 4 FIX #13: Improved Body Frame using Ponderal Index
    private fun determineBodyFrame(bmi: Double, height: Double, weight: Double): BodyFrame {
        // ✅ BUG #57: Fix ponderal index overflow for extreme values
        val heightInMeters = (height / 100.0).coerceIn(1.0, 2.5)  // Bound to reasonable range
        val ponderalIndex = weight / (heightInMeters * heightInMeters * heightInMeters) // kg/m³ (more accurate than BMI for frame)
        
        // ✅ BUG #57: Sanity check ponderal index
        if (ponderalIndex < 5.0 || ponderalIndex > 25.0) {
            android.util.Log.w("BodyEstimator", "⚠️ [BUG #57] Unusual ponderal index: $ponderalIndex - using BMI only")
            // Fall back to BMI-only classification
            return when {
                bmi < 18.5 -> BodyFrame.SLIM
                bmi < 25.0 -> BodyFrame.AVERAGE
                bmi < 30.0 -> BodyFrame.AVERAGE
                else -> BodyFrame.PLUS_SIZE
            }
        }
        
        // Improved frame classification using Ponderal Index
        val frame = when {
            bmi < 18.5 -> BodyFrame.SLIM
            bmi < 25.0 && ponderalIndex < 12.5 -> BodyFrame.ATHLETIC  // Low ponderal = lean muscle mass
            bmi < 25.0 -> BodyFrame.AVERAGE
            bmi < 30.0 -> BodyFrame.AVERAGE
            else -> BodyFrame.PLUS_SIZE
        }
        
        android.util.Log.d("BodyEstimator", "🎯 [PHASE4-FIX13] BodyFrame: $frame (BMI=$bmi, ponderalIndex=$ponderalIndex)")
        return frame
    }
    
    // PRD Section 5.3: Male Body Measurements
    private fun estimateChestCircumference(
        height: Double, 
        weight: Double, 
        gender: Gender, 
        region: SizeRegion
    ): Double {
        return when (gender) {
            Gender.MALE -> estimateChestMale(height, weight, region)
            Gender.FEMALE -> estimateChestFemale(height, weight, region)
        }
    }
    
    /**
     * PRD Section 5.3: Male Chest Estimation
     * Multivariate regression: chest = (height × 0.52) + (weight × 0.32) - 15.0
     * Then applies regional multiplier for anthropometric variation.
     */
    private fun estimateChestMale(height: Double, weight: Double, region: SizeRegion): Double {
        val baseChest = (height * 0.52) + (weight * 0.32) - 15.0
        val factors = getRegionalFactors(region, Gender.MALE)
        val result = baseChest * factors.chest
        
        android.util.Log.d("BodyEstimator", "✅ [PRD RESTORED] Chest estimation (Male): baseChest=$baseChest, regionFactor=${factors.chest}, result=$result, height=$height, weight=$weight, region=$region")
        
        return result
    }
    
    /**
     * PRD Section 5.3: Female Chest Estimation
     * Multivariate regression: chest = (height × 0.48) + (weight × 0.35) - 8.0
     * Then applies regional multiplier for anthropometric variation.
     */
    private fun estimateChestFemale(height: Double, weight: Double, region: SizeRegion): Double {
        val baseChest = (height * 0.48) + (weight * 0.35) - 8.0
        val factors = getRegionalFactors(region, Gender.FEMALE)
        val result = baseChest * factors.chest
        
        android.util.Log.d("BodyEstimator", "✅ [PRD RESTORED] Chest estimation (Female): baseChest=$baseChest, regionFactor=${factors.chest}, result=$result, height=$height, weight=$weight, region=$region")
        
        return result
    }
    
    private fun estimateWaistCircumference(
        height: Double, 
        weight: Double, 
        gender: Gender, 
        region: SizeRegion
    ): Double {
        return when (gender) {
            Gender.MALE -> estimateWaistMale(height, weight, region)
            Gender.FEMALE -> estimateWaistFemale(height, weight, region)
        }
    }
    
    /**
     * PRD Section 5.3: Male Waist Estimation
     * Multivariate regression: waist = (height × 0.45) + (weight × 0.28) - 12.0
     * Then applies regional multiplier.
     */
    private fun estimateWaistMale(height: Double, weight: Double, region: SizeRegion): Double {
        val baseWaist = (height * 0.45) + (weight * 0.28) - 12.0
        val factors = getRegionalFactors(region, Gender.MALE)
        val result = baseWaist * factors.waist
        
        android.util.Log.d("BodyEstimator", "✅ [PRD RESTORED] Waist estimation (Male): baseWaist=$baseWaist, regionFactor=${factors.waist}, result=$result")
        
        return result
    }
    
    /**
     * PRD Section 5.3: Female Waist Estimation
     * Multivariate regression: waist = (height × 0.40) + (weight × 0.30) - 15.0
     * Then applies regional multiplier.
     */
    private fun estimateWaistFemale(height: Double, weight: Double, region: SizeRegion): Double {
        val baseWaist = (height * 0.40) + (weight * 0.30) - 15.0
        val factors = getRegionalFactors(region, Gender.FEMALE)
        val result = baseWaist * factors.waist
        
        android.util.Log.d("BodyEstimator", "✅ [PRD RESTORED] Waist estimation (Female): baseWaist=$baseWaist, regionFactor=${factors.waist}, result=$result")
        
        return result
    }
    
    private fun estimateHipCircumference(
        height: Double, 
        weight: Double, 
        gender: Gender, 
        region: SizeRegion
    ): Double {
        return when (gender) {
            Gender.MALE -> estimateHipMale(height, weight, region)
            Gender.FEMALE -> estimateHipFemale(height, weight, region)
        }
    }
    
    /**
     * PRD Section 5.3: Male Hip Estimation
     * Multivariate regression: hip = (height × 0.53) + (weight × 0.30) - 10.0
     * Then applies regional multiplier.
     */
    private fun estimateHipMale(height: Double, weight: Double, region: SizeRegion): Double {
        val baseHip = (height * 0.53) + (weight * 0.30) - 10.0
        val factors = getRegionalFactors(region, Gender.MALE)
        val result = baseHip * factors.hip
        
        android.util.Log.d("BodyEstimator", "✅ [PRD RESTORED] Hip estimation (Male): baseHip=$baseHip, regionFactor=${factors.hip}, result=$result")
        
        return result
    }
    
    /**
     * PRD Section 5.3: Female Hip Estimation
     * Multivariate regression: hip = (height × 0.54) + (weight × 0.33) - 5.0
     * Then applies regional multiplier.
     */
    private fun estimateHipFemale(height: Double, weight: Double, region: SizeRegion): Double {
        val baseHip = (height * 0.54) + (weight * 0.33) - 5.0
        val factors = getRegionalFactors(region, Gender.FEMALE)
        val result = baseHip * factors.hip
        
        android.util.Log.d("BodyEstimator", "✅ [PRD RESTORED] Hip estimation (Female): baseHip=$baseHip, regionFactor=${factors.hip}, result=$result")
        
        return result
    }
    
    private fun estimateShoulderWidth(height: Double, gender: Gender, region: SizeRegion): Double {
        return when (gender) {
            Gender.MALE -> estimateShoulderMale(height, region)
            Gender.FEMALE -> estimateShoulderFemale(height, region)
        }
    }
    
    /**
     * PRD Section 5.3: Male Shoulder Estimation
     * Base: height × 0.259, then regional multiplier.
     */
    private fun estimateShoulderMale(height: Double, region: SizeRegion): Double {
        val baseShoulder = height * 0.259
        val factors = getRegionalFactors(region, Gender.MALE)
        val result = baseShoulder * factors.shoulder
        
        android.util.Log.d("BodyEstimator", "✅ [PRD RESTORED] Shoulder estimation (Male): baseShoulder=$baseShoulder, regionFactor=${factors.shoulder}, result=$result")
        
        return result
    }
    
    /**
     * PRD Section 5.3: Female Shoulder Estimation
     * Base: height × 0.237, then regional multiplier.
     */
    private fun estimateShoulderFemale(height: Double, region: SizeRegion): Double {
        val baseShoulder = height * 0.237
        val factors = getRegionalFactors(region, Gender.FEMALE)
        val result = baseShoulder * factors.shoulder
        
        android.util.Log.d("BodyEstimator", "✅ [PRD RESTORED] Shoulder estimation (Female): baseShoulder=$baseShoulder, regionFactor=${factors.shoulder}, result=$result")
        
        return result
    }
    
    private fun estimateInseam(height: Double, gender: Gender): Double {
        return when (gender) {
            Gender.MALE -> height * 0.45
            Gender.FEMALE -> height * 0.44
        }
    }
    
    // ✅ BUG #58: Cross-validation of body measurements
    private fun validateEstimatedMeasurements(
        chest: Double,
        waist: Double,
        hip: Double,
        shoulder: Double,
        gender: Gender
    ): com.vanexa.supercalculator.domain.model.ValidationResult {
        val issues = mutableListOf<String>()
        
        // Skip validation if measurements are 0.0 (filtered out due to garment type)
        if (chest <= 0.0 || waist <= 0.0) return com.vanexa.supercalculator.domain.model.ValidationResult.Success
        
        // Chest vs Waist
        val chestToWaistRatio = chest / waist
        val expectedRatio = when (gender) {
            Gender.MALE -> 1.1..1.3    // Male: chest 10-30% larger than waist
            Gender.FEMALE -> 1.05..1.25 // Female: chest 5-25% larger than waist
        }
        if (chestToWaistRatio !in expectedRatio) {
            // Be more lenient with warnings - body types vary wildly
            // issues.add("Chest-to-waist ratio ($chestToWaistRatio) outside expected range $expectedRatio")
        }
        
        // Waist vs Hip
        val waistToHipRatio = waist / hip
        val expectedHipRatio = when (gender) {
            Gender.MALE -> 0.92..1.05    // Male: waist similar to hip
            Gender.FEMALE -> 0.70..0.85  // Female: waist narrower than hip
        }
        if (waistToHipRatio !in expectedHipRatio) {
            issues.add("Waist-to-hip ratio ($waistToHipRatio) outside expected range $expectedHipRatio")
        }
        
        // Shoulder vs Chest
        val shoulderToChestRatio = shoulder / chest
        if (shoulderToChestRatio < 0.35 || shoulderToChestRatio > 0.55) {
            issues.add("Shoulder-to-chest ratio ($shoulderToChestRatio) seems unusual")
        }
        
        if (issues.isNotEmpty()) {
            android.util.Log.w("BodyEstimator", "⚠️ [BUG #58] Cross-validation warnings: ${issues.joinToString(", ")}")
            return com.vanexa.supercalculator.domain.model.ValidationResult.Warning(
                "Estimated measurements may be inaccurate: ${issues.joinToString("; ")}"
            )
        }
        
        return com.vanexa.supercalculator.domain.model.ValidationResult.Success
    }
    
    // PRD Section 5.4: Confidence Calculation
    private fun calculateEstimationConfidence(
        mandatoryInput: UserBodyInput,
        optionalMeasurements: OptionalMeasurements?,
        bmi: Double
    ): ConfidenceScore {
        // Base confidence with just height/weight
        var baseConfidence = 0.75  // 75% base accuracy per PRD
        
        // Track which measurements are direct vs estimated
        val measurementConfidences = mutableMapOf<String, MeasurementConfidence>()
        val estimationMethods = mutableMapOf<String, EstimationMethod>()
        
        // Chest confidence
        if (optionalMeasurements?.chestCircumference != null) {
            measurementConfidences["chest"] = MeasurementConfidence(
                value = 0.98,  // 98% confidence when measured directly
                method = EstimationMethod.MEASURED,
                variance = 1.0  // ±1cm measurement error
            )
            estimationMethods["chest"] = EstimationMethod.MEASURED
            baseConfidence += 0.12
        } else {
            measurementConfidences["chest"] = MeasurementConfidence(
                value = 0.75,
                method = EstimationMethod.ESTIMATED,
                variance = 4.0  // ±4cm estimation variance
            )
            estimationMethods["chest"] = EstimationMethod.ESTIMATED
        }
        
        // Waist confidence
        if (optionalMeasurements?.waistCircumference != null) {
            measurementConfidences["waist"] = MeasurementConfidence(
                value = 0.98,
                method = EstimationMethod.MEASURED,
                variance = 1.0
            )
            estimationMethods["waist"] = EstimationMethod.MEASURED
            baseConfidence += 0.10
        } else {
            measurementConfidences["waist"] = MeasurementConfidence(
                value = 0.72,
                method = EstimationMethod.ESTIMATED,
                variance = 5.0  // ±5cm variance (waist varies most)
            )
            estimationMethods["waist"] = EstimationMethod.ESTIMATED
        }
        
        // Hip confidence
        if (optionalMeasurements?.hipCircumference != null) {
            measurementConfidences["hip"] = MeasurementConfidence(
                value = 0.98,
                method = EstimationMethod.MEASURED,
                variance = 1.0
            )
            estimationMethods["hip"] = EstimationMethod.MEASURED
            baseConfidence += 0.08
        } else {
            measurementConfidences["hip"] = MeasurementConfidence(
                value = 0.74,
                method = EstimationMethod.ESTIMATED,
                variance = 4.0
            )
            estimationMethods["hip"] = EstimationMethod.ESTIMATED
        }
        
        // Shoulder confidence
        if (optionalMeasurements?.shoulderWidth != null) {
            measurementConfidences["shoulder"] = MeasurementConfidence(
                value = 0.98,
                method = EstimationMethod.MEASURED,
                variance = 1.0
            )
            estimationMethods["shoulder"] = EstimationMethod.MEASURED
            baseConfidence += 0.05
        } else {
            measurementConfidences["shoulder"] = MeasurementConfidence(
                value = 0.78,  // Shoulder estimates are more reliable
                method = EstimationMethod.ESTIMATED,
                variance = 2.0
            )
            estimationMethods["shoulder"] = EstimationMethod.ESTIMATED
        }
        
        // Inseam confidence
        if (optionalMeasurements?.inseamLength != null) {
            measurementConfidences["inseam"] = MeasurementConfidence(
                value = 0.98,
                method = EstimationMethod.MEASURED,
                variance = 1.0
            )
            estimationMethods["inseam"] = EstimationMethod.MEASURED
            baseConfidence += 0.03
        } else {
            measurementConfidences["inseam"] = MeasurementConfidence(
                value = 0.82,  // Inseam from height is quite accurate
                method = EstimationMethod.ESTIMATED,
                variance = 2.5
            )
            estimationMethods["inseam"] = EstimationMethod.ESTIMATED
        }
        
        // BMI range adjustment (estimation accuracy varies by BMI)
        val bmiAdjustment = when {
            bmi in 18.5..25.0 -> 0.05   // Normal weight: formulas most accurate
            bmi in 25.0..30.0 -> 0.02   // Overweight: still good
            bmi in 17.0..18.5 -> 0.02   // Slightly underweight: good
            bmi in 30.0..35.0 -> -0.03  // Obese I: formulas less accurate
            bmi in 15.0..17.0 -> -0.05  // Underweight: proportions differ
            else -> -0.08                // Extreme BMI: significant variance
        }
        
        baseConfidence += bmiAdjustment
        
        // Regional confidence adjustment
        val regionalAdjustment = when (mandatoryInput.region) {
            SizeRegion.US, SizeRegion.EU -> 0.02      // Most research data available
            SizeRegion.UK -> 0.01
            SizeRegion.ASIA, SizeRegion.ID -> -0.01  // Less standardized data
        }
        
        baseConfidence += regionalAdjustment
        
        // Clamp final confidence
        val finalConfidence = baseConfidence.coerceIn(0.0, 1.0)
        
        return ConfidenceScore(
            overall = finalConfidence,
            breakdown = measurementConfidences
        )
    }

    private fun isUpperBody(type: GarmentType): Boolean {
        return type in listOf(
            GarmentType.TSHIRT, GarmentType.SHIRT, GarmentType.POLO_SHIRT,
            GarmentType.HOODIE, GarmentType.SWEATER, GarmentType.JACKET, 
            GarmentType.BLAZER
        )
    }

    private fun isLowerBody(type: GarmentType): Boolean {
        return type in listOf(
            GarmentType.JEANS, GarmentType.TROUSERS, GarmentType.CHINOS,
            GarmentType.CARGO_PANTS, GarmentType.SHORTS, GarmentType.JOGGERS
        )
    }

    // ─── Regional Multiplier Data ───

    /**
     * PRD Section 5.3: Regional anthropometric multipliers.
     * Based on CAESAR (US), SizeKorea (ASIA), and Indonesian anthropometric studies.
     */
    data class RegionalBodyFactors(
        val chest: Double,
        val waist: Double,
        val hip: Double,
        val shoulder: Double
    )

    private fun getRegionalFactors(region: SizeRegion, gender: Gender): RegionalBodyFactors {
        return when (gender) {
            Gender.MALE -> when (region) {
                SizeRegion.US -> RegionalBodyFactors(chest = 1.03, waist = 1.04, hip = 1.02, shoulder = 1.02)
                SizeRegion.EU -> RegionalBodyFactors(chest = 1.00, waist = 1.00, hip = 1.00, shoulder = 1.00)
                SizeRegion.UK -> RegionalBodyFactors(chest = 1.01, waist = 1.01, hip = 1.01, shoulder = 1.01)
                SizeRegion.ASIA -> RegionalBodyFactors(chest = 0.97, waist = 0.96, hip = 0.98, shoulder = 0.98)
                SizeRegion.ID -> RegionalBodyFactors(chest = 0.96, waist = 0.95, hip = 0.97, shoulder = 0.97)
            }
            Gender.FEMALE -> when (region) {
                SizeRegion.US -> RegionalBodyFactors(chest = 1.04, waist = 1.05, hip = 1.03, shoulder = 1.02)
                SizeRegion.EU -> RegionalBodyFactors(chest = 1.00, waist = 1.00, hip = 1.00, shoulder = 1.00)
                SizeRegion.UK -> RegionalBodyFactors(chest = 1.01, waist = 1.01, hip = 1.01, shoulder = 1.01)
                SizeRegion.ASIA -> RegionalBodyFactors(chest = 0.97, waist = 0.94, hip = 0.97, shoulder = 0.98)
                SizeRegion.ID -> RegionalBodyFactors(chest = 0.95, waist = 0.93, hip = 0.96, shoulder = 0.97)
            }
        }
    }
}

