package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*
import kotlin.math.abs

/**
 * Data class representing the fit analysis result for a single dimension.
 *
 * @param dimensionName The name of the dimension (e.g., "chest", "waist").
 * @param bodyMeasurement The user's body measurement for this dimension in cm.
 * @param garmentMeasurement The garment's measurement for this dimension in cm.
 * @param actualEase The calculated ease (garmentMeasurement - bodyMeasurement).
 * @param recommendedEase The ideal or recommended ease for this dimension in cm.
 * @param minEase The minimum acceptable ease for this dimension in cm.
 * @param maxEase The maximum acceptable ease for this dimension in cm.
 * @param fitScore A score from 0.0 to 1.0 indicating how well this dimension fits (1.0 is perfect).
 * @param assessment A human-readable string describing the fit (e.g., "Perfect", "Too Tight").
 * @param isCritical True if this dimension is considered critical for overall fit.
 * @param confidence The confidence score of the body measurement (0.0-1.0).
 */
data class DimensionFitDetail(
    val dimensionName: String,
    val bodyMeasurement: Double,
    val garmentMeasurement: Double,
    val actualEase: Double,
    val recommendedEase: Double,
    val minEase: Double,
    val maxEase: Double,
    val fitScore: Double,
    val assessment: String,
    val isCritical: Boolean,
    val confidence: Double
)

/**
 * Fit Scoring Engine V2 - Phase 3: Individual Dimension Analysis
 *
 * **Purpose:**
 * Analyzes how well individual dimensions (chest, waist, hip, etc.) fit between
 * user body measurements and garment measurements. Provides dimension-level insight
 * into fit quality before calculating overall fit scores.
 *
 * **Dimension-Specific Tolerances:**
 * Different dimensions have different acceptable ranges based on garment type and
 * human body mechanics:
 *
 * - **Chest**: Large tolerance (±4 to ±8cm) - allows for layering and movement
 * - **Waist**: Tighter tolerance (±2 to ±4cm) - critical for waistband comfort
 * - **Hip**: Moderate tolerance (±3 to ±6cm) - affects silhouette but allows movement
 * - **Shoulder**: Minimal tolerance (±1 to ±2cm) - sleeve alignment critical
 * - **Inseam**: Strict tolerance (-2 to +2cm) - length must be exact
 * - **Other**: Standard tolerance (±3 to ±5cm)
 *
 * **Scoring Philosophy:**
 * - Penalize tight fits more heavily than loose fits (tight causes discomfort)
 * - Prefer slightly loose over slightly tight
 * - Optimal fit point varies by dimension (chest prefers tighter ease than hip)
 * - Dimension-level scores feed into Phase 4 weighted overall scoring
 *
 * @author Engine Vision Phase 3
 */
object FitScoringEngineV2 {

    /**
     * Analyzes fit for a single dimension (chest, waist, hip, etc.).
     *
     * **Algorithm Flow:**
     *
     * 1. **Calculate Ease**: Difference between garment and body measurements
     *    - Positive ease = loose (garment > body)
     *    - Negative ease = tight (garment < body)
     *
     * 2. **Determine Acceptable Range**: Varies by dimension type
     *    - Different dimensions have different comfort tolerances
     *    - Ranges are defined as (minEase, maxEase)
     *
     * 3. **Calculate Fit Score**: 0.0-1.0 based on ease position
     *    - Too tight: Linear penalty from minimum
     *    - Too loose: Linear penalty from maximum
     *    - Within range: Gaussian preference for optimal ease
     *
     * 4. **Generate Assessment**: Human-readable fit description
     *    - "Perfect" = ideal range and near-optimal ease
     *    - "Fitted" = slightly tighter than recommended
     *    - "Relaxed" = slightly looser than recommended
     *    - "Snug" / "Loose" = approaching limits
     *    - "Too Tight" / "Too Loose" = outside acceptable range
     *
     * 5. **Return DimensionFitDetail**: Complete dimension analysis with score and assessment
     *
     * @param dimension Dimension name: "chest", "waist", "hip", "shoulder", "inseam", "sleeve", etc.
     * @param bodyMeasurement User's body measurement in cm (e.g., 93cm chest)
     * @param garmentMeasurement Garment measurement from size chart in cm (e.g., 101cm chest)
     * @param recommendedEase Recommended ease for this dimension in cm (e.g., 8cm for chest)
     * @param isCritical True if this dimension is critical for overall fit (e.g., inseam, shoulder)
     * @param confidence Measurement confidence 0.0-1.0 (1.0 = manually measured, 0.7-0.9 = estimated)
     *
     * @return DimensionFitDetail with score, assessment, and detailed metrics
     *
     * **Example:**
     * ```kotlin
     * val result = analyzeDimension(
     *     dimension = "chest",
     *     bodyMeasurement = 93.0,
     *     garmentMeasurement = 101.0,
     *     recommendedEase = 8.0,
     *     isCritical = true,
     *     confidence = 0.85
     * )
     * // Result: FitScore=0.986, Assessment="Perfect"
     * ```
     */
    fun analyzeDimension(
        dimension: String,
        bodyMeasurement: Double,
        garmentMeasurement: Double,
        recommendedEase: Double,
        isCritical: Boolean,
        confidence: Double
    ): DimensionFitDetail {
        // STEP 1: Calculate actual ease
        val actualEase = garmentMeasurement - bodyMeasurement

        // STEP 2: Determine acceptable ease range for this dimension
        // Different dimensions have different comfort tolerances based on garment mechanics
        val (minEase, maxEase) = when (dimension.lowercase()) {
            // CHEST: Upper body garments allow large ease for layering/movement
            // Typical: 6-12cm for tops, narrower for fitted shirts
            "chest" -> {
                val min = recommendedEase - 4.0  // 4cm below recommended
                val max = recommendedEase + 8.0   // 8cm above recommended
                Pair(min, max)
            }

            // WAIST: Critical dimension - tight waistbands uncomfortable, loose = sagging
            // Typical: 2-8cm for bottoms
            "waist" -> {
                val min = recommendedEase - 2.0   // 2cm below recommended
                val max = recommendedEase + 4.0   // 4cm above recommended
                Pair(min, max)
            }

            // HIP: Affects silhouette and movement - moderate tolerance
            // Typical: 5-10cm for fitted bottoms
            "hip" -> {
                val min = recommendedEase - 3.0   // 3cm below recommended
                val max = recommendedEase + 6.0   // 6cm above recommended
                Pair(min, max)
            }

            // SHOULDER: Sleeve alignment critical - narrow tolerance
            // Typical: 1-4cm (seam placement must align with natural shoulder point)
            "shoulder" -> {
                val min = recommendedEase - 1.0   // 1cm below recommended
                val max = recommendedEase + 2.0   // 2cm above recommended
                Pair(min, max)
            }

            // INSEAM: Length must be near-exact - strictest tolerance
            // Typical: -2cm (slightly short is OK, avoids drag) to +2cm (slight hem possible)
            // Negative ease common here (garment slightly shorter than user inseam)
            "inseam" -> {
                val min = -2.0   // 2cm short is acceptable
                val max = 2.0    // 2cm long is acceptable
                Pair(min, max)
            }

            // DEFAULT: Standard moderate tolerance
            "sleeve", "length" -> {
                val min = recommendedEase - 3.0
                val max = recommendedEase + 5.0
                Pair(min, max)
            }

            else -> {
                // Fallback for unknown dimensions
                val min = recommendedEase - 3.0
                val max = recommendedEase + 5.0
                Pair(min, max)
            }
        }

        // STEP 3: Calculate fit score (0.0-1.0)
        val fitScore = when {
            // CASE 1: TOO TIGHT - Penalize heavily
            // Each 10cm too tight = complete failure (score=0)
            // Tight fits cause discomfort and restrict movement
            actualEase < minEase -> {
                val deficit = minEase - actualEase  // How much too tight
                val penalty = deficit / 10.0        // Harsh penalty: 10cm = 1.0 score loss
                (1.0 - penalty).coerceIn(0.0, 1.0)
            }

            // CASE 2: TOO LOOSE - Penalize moderately
            // Each 15cm too loose = complete failure (score=0)
            // Loose fits look sloppy but are wearable
            actualEase > maxEase -> {
                val excess = actualEase - maxEase   // How much too loose
                val penalty = excess / 15.0         // Softer penalty: 15cm = 1.0 score loss
                (1.0 - penalty).coerceIn(0.0, 1.0)
            }

            // CASE 3: WITHIN ACCEPTABLE RANGE - Prefer slightly tight side
            // Position 0.0 = minimum ease (tight side)
            // Position 1.0 = maximum ease (loose side)
            // Optimal = 0.4 (40% of range toward loose = slightly fitted)
            else -> {
                val rangeWidth = maxEase - minEase
                if (rangeWidth <= 0.0) {
                    1.0  // Zero-width range = perfect fit
                } else {
                    val rangePosition = (actualEase - minEase) / rangeWidth
                    val optimalPosition = 0.4  // Prefer 40% into the range (slightly fitted)

                    // Gaussian penalty based on distance from optimal
                    // -0.2 factor means ±0.5 position = 0.8 score, ±1.0 = 0.6 score
                    val penalty = abs(rangePosition - optimalPosition) * 0.2
                    (1.0 - penalty).coerceIn(0.8, 1.0)
                }
            }
        }

        // STEP 4: Determine assessment string
        // Human-readable description of fit quality
        val assessment = when {
            // EXTREMELY TIGHT - More than 5cm below minimum
            actualEase < minEase - 5.0 -> "Too Tight"

            // TIGHT - Below minimum but not extreme
            actualEase < minEase -> "Snug"

            // EXTREMELY LOOSE - More than 5cm above maximum
            actualEase > maxEase + 5.0 -> "Too Loose"

            // LOOSE - Above maximum but not extreme
            actualEase > maxEase -> "Loose"

            // IDEAL FIT - Near recommended ease
            abs(actualEase - recommendedEase) < 2.0 -> "Perfect"

            // FITTED - Slightly tighter than recommended, but still within range
            actualEase < recommendedEase -> "Fitted"

            // RELAXED - Slightly looser than recommended, but still within range
            else -> "Relaxed"
        }

        // STEP 5: Return DimensionFitDetail with all fields populated
        return DimensionFitDetail(
            dimensionName = dimension,
            bodyMeasurement = bodyMeasurement,
            garmentMeasurement = garmentMeasurement,
            actualEase = actualEase,
            recommendedEase = recommendedEase,
            minEase = minEase,
            maxEase = maxEase,
            fitScore = fitScore,
            assessment = assessment,
            isCritical = isCritical,
            confidence = confidence
        )
    }

    /**
     * Analyzes multiple dimensions at once.
     *
     * Convenience function that applies [analyzeDimension] to multiple dimensions
     * and returns a list of DimensionFitDetail results for overall fit analysis.
     *
     * @param dimensions Map of dimension name to (body, garment, ease) tuple
     * @param easeRecommendations Map of dimension name to recommended ease
     * @param criticalDimensions Set of critical dimension names
     * @param confidenceScores Map of dimension name to confidence (0.0-1.0)
     * @return List of DimensionFitDetail results
     */
    fun analyzeDimensions(
        dimensions: Map<String, Pair<Double, Double>>,  // dimension -> (body, garment)
        easeRecommendations: Map<String, Double>,        // dimension -> recommendedEase
        criticalDimensions: Set<String> = emptySet(),
        confidenceScores: Map<String, Double> = emptyMap()
    ): List<DimensionFitDetail> {
        return dimensions.map { (dimensionName, measurements) ->
            val (bodyMeasurement, garmentMeasurement) = measurements
            val recommendedEase = easeRecommendations[dimensionName] ?: 0.0
            val isCritical = dimensionName in criticalDimensions
            val confidence = confidenceScores[dimensionName] ?: 0.85

            analyzeDimension(
                dimension = dimensionName,
                bodyMeasurement = bodyMeasurement,
                garmentMeasurement = garmentMeasurement,
                recommendedEase = recommendedEase,
                isCritical = isCritical,
                confidence = confidence
            )
        }
    }

    /**
     * Calculates overall fit score from dimension-level scores.
     *
     * **Weighting Strategy:**
     * - Critical dimensions (inseam, shoulder): 100% weight
     * - Normal dimensions (chest, waist, hip): 85% weight
     * - Secondary dimensions: 70% weight
     *
     * Uses weighted geometric mean to heavily penalize poor critical fits.
     *
     * @param dimensionFits List of analyzed dimensions
     * @return Overall fit score (0.0-1.0)
     */
    fun calculateOverallFitScore(dimensionFits: List<DimensionFitDetail>): Double {
        if (dimensionFits.isEmpty()) return 0.5

        var weightedSum = 0.0
        var totalWeight = 0.0

        for (fit in dimensionFits) {
            // Assign weights based on dimension importance
            val weight = when {
                fit.isCritical -> 1.0       // 100% weight for critical
                fit.dimensionName in listOf("chest", "waist", "hip") -> 0.85  // 85% for main
                else -> 0.70                 // 70% for secondary
            }

            weightedSum += fit.fitScore * weight
            totalWeight += weight
        }

        return if (totalWeight > 0.0) {
            (weightedSum / totalWeight).coerceIn(0.0, 1.0)
        } else {
            0.5
        }
    }

    /**
     * Calculates confidence-weighted overall fit score from dimension-level scores.
     *
     * **Weighting Strategy:**
     * This function combines individual dimension fit scores while respecting confidence levels.
     * Measurements with higher confidence (manually measured vs. estimated) contribute more to
     * the final score. Critical dimensions (those that directly affect garment fit) are weighted
     * at 2.0x, while normal dimensions are weighted at 1.0x.
     *
     * **Why Double-Weight Critical Dimensions:**
     * Some dimensions are deal-breakers for fit. If inseam is 5cm too short or chest is too
     * tight, the overall size is wrong regardless of other dimensions. By doubling the weight
     * of critical dimensions, we ensure fit failures in critical areas dominate the score.
     *
     * **Confidence Weighting:**
     * A perfectly measured critical dimension (confidence=1.0) has weight 2.0×1.0=2.0.
     * An estimated secondary dimension (confidence=0.7) has weight 1.0×0.7=0.7.
     * This ensures uncertain measurements don't pull the overall score away from what we
     * know with high confidence.
     *
     * **Formula:**
     * ```
     * weightedSum = Σ(fitScore × baseWeight × confidence)
     * totalWeight = Σ(baseWeight × confidence)
     * overallScore = weightedSum / totalWeight
     * ```
     *
     * @param dimensions List of analyzed DimensionFitDetail results
     * @return Confidence-weighted fit score (0.0-1.0)
     *
     * **Example:**
     * ```
     * Dimension 1 (chest, critical, score=0.9, confidence=1.0):
     *   baseWeight = 2.0, confidenceWeight = 2.0×1.0 = 2.0
     *   contribution = 0.9 × 2.0 = 1.8
     *
     * Dimension 2 (waist, normal, score=0.8, confidence=0.7):
     *   baseWeight = 1.0, confidenceWeight = 1.0×0.7 = 0.7
     *   contribution = 0.8 × 0.7 = 0.56
     *
     * Result: (1.8 + 0.56) / (2.0 + 0.7) = 2.36 / 2.7 = 0.874
     * ```
     */
    fun calculateWeightedScore(dimensions: List<DimensionFitDetail>): Double {
        if (dimensions.isEmpty()) return 0.5

        var weightedSum = 0.0
        var totalWeight = 0.0

        for (dimension in dimensions) {
            // STEP 1: Assign base weight based on dimension criticality
            // Critical dimensions like chest/waist/inseam are deal-breakers
            val baseWeight = if (dimension.isCritical) 2.0 else 1.0

            // STEP 2: Apply confidence weighting
            // A measured dimension (confidence=1.0) contributes fully
            // An estimated dimension (confidence=0.75) contributes 75%
            val confidenceWeight = baseWeight * dimension.confidence

            // STEP 3: Add this dimension's contribution to weighted sum
            // fitScore=0.9 with weight=2.0 contributes 1.8 to numerator
            weightedSum += dimension.fitScore * confidenceWeight

            // STEP 4: Track total weight for normalization
            totalWeight += confidenceWeight
        }

        // STEP 5: Return normalized weighted average, or 0.5 if no dimensions
        return if (totalWeight > 0.0) {
            (weightedSum / totalWeight).coerceIn(0.0, 1.0)
        } else {
            0.5  // Default if no dimensions analyzed
        }
    }

    /**
     * Converts numeric fit score to user-friendly FitCategory.
     *
     * **Algorithm:**
     * This function determines not just the quality level (good/poor) but also the specific
     * failure mode (too tight vs. too loose) by analyzing critical dimensions. A size can have
     * an acceptable overall score but fail on a critical dimension—we need to surface that
     * to the user immediately.
     *
     * **Two-Stage Process:**
     * 1. **Critical Dimension Check** (fail-fast): If any critical dimension fails badly,
     *    determine if it's a tight or loose issue and return immediately. These are deal-breakers.
     * 2. **Score-Based Category** (fallback): If no critical failures, use the overall score
     *    to determine category, with ease analysis to distinguish tight from loose variants.
     *
     * **Critical Dimension Deal-Breakers:**
     * - inseam too short/long: Major issue (can't adjust easily)
     * - shoulder too tight/loose: Major issue (sleeve placement critical)
     * - chest way too tight: Major issue (breathing/movement restricted)
     * These override a good overall score.
     *
     * **Threshold Rationale:**
     * - 0.90+: Excellent fit (rare—most garments score 0.70-0.85)
     * - 0.75+: Comfortable fit (good enough to recommend)
     * - 0.60-0.75: Borderline (recommend if user prefers that style)
     * - 0.40-0.60: Poor fit (only if user really likes the look)
     * - <0.40: Very poor fit (avoid)
     *
     * @param score Overall confidence-weighted fit score (0.0-1.0)
     * @param dimensions List of analyzed dimension fits
     * @return FitCategory describing fit quality and mode (tight/loose/perfect)
     */
    fun scoreToCategory(score: Double, dimensions: List<DimensionFitDetail>): FitCategoryEngine.FitCategory {
        // STEP 1: Check for critical dimension failures
        // These are deal-breakers that override overall score
        val criticalDimensions = dimensions.filter { it.isCritical }

        for (critical in criticalDimensions) {
            // If a critical dimension has very poor fit (score < 0.4), it fails
            if (critical.fitScore < 0.4) {
                // Determine if it's too tight or too loose
                val isTooTight = critical.actualEase < 0.0  // Garment < body measurement
                val isTooLoose = critical.actualEase > 0.0  // Garment > body measurement

                // Return specific category for critical failure
                return when {
                    isTooTight -> FitCategoryEngine.FitCategory.SLIGHTLY_TIGHT
                    isTooLoose -> FitCategoryEngine.FitCategory.SLIGHTLY_LOOSE
                    else -> FitCategoryEngine.FitCategory.POOR_FIT
                }
            }
        }

        // STEP 2: No critical failures—use overall score with ease analysis
        // Calculate average ease across all dimensions to distinguish tight from loose
        val averageEase = if (dimensions.isNotEmpty()) {
            dimensions.map { it.actualEase }.average()
        } else {
            0.0
        }

        return when {
            // EXCELLENT: Score >= 0.90 means all dimensions are very close to target
            // Rare in practice but possible with perfect measurements
            score >= 0.90 -> FitCategoryEngine.FitCategory.VERY_COMFORTABLE

            // COMFORTABLE: Score >= 0.75 means good fit with minor variations
            // Most sizes in this range are recommendable
            score >= 0.75 -> FitCategoryEngine.FitCategory.COMFORTABLE

            // BORDERLINE: Score 0.60-0.75 means acceptable but not ideal
            // Distinguish between slightly tight vs. slightly loose based on ease
            // Tight (avg ease < 3cm) suggests garment is snug
            // Loose (avg ease >= 3cm) suggests garment is relaxed
            score >= 0.60 -> {
                if (averageEase < 3.0) {
                    FitCategoryEngine.FitCategory.SLIGHTLY_TIGHT
                } else {
                    FitCategoryEngine.FitCategory.SLIGHTLY_LOOSE
                }
            }

            // POOR: Score 0.40-0.60 means significant issues but potentially wearable
            // Again distinguish tight vs. loose to guide user choice
            score >= 0.40 -> {
                if (averageEase < 3.0) {
                    FitCategoryEngine.FitCategory.SLIGHTLY_TIGHT  // Or could be POOR_FIT
                } else {
                    FitCategoryEngine.FitCategory.SLIGHTLY_LOOSE  // Or could be POOR_FIT
                }
            }

            // VERY POOR: Score < 0.40 means garment is likely too tight
            // Default to tight as this is more restrictive and uncomfortable
            else -> FitCategoryEngine.FitCategory.VERY_POOR_FIT
        }
    }

    /**
     * Main entry point: Orchestrates complete fit analysis process.
     *
     * **Purpose:**
     * This is the top-level function that coordinates all phases of fit analysis:
     * 1. Extracts garment specifications (critical dimensions, ease factors)
     * 2. Analyzes each available dimension individually (Phase 3)
     * 3. Calculates overall fit score with confidence weighting (Phase 4)
     * 4. Maps score to user-friendly category
     * 5. Generates human-readable guidance text
     * 6. Returns complete FitResult with all analysis
     *
     * **Algorithm Flow:**
     *
     * **Step 1: Extract Garment Specifications**
     * - Retrieve ease factors from garment type (e.g., chest=8cm for shirt)
     * - Retrieve critical dimensions (e.g., "chest", "shoulder" for shirt)
     *
     * **Step 2: Build Dimension Analysis List**
     * - For each possible dimension (chest, waist, hip, shoulder, sleeve, inseam):
     *   a) Check if both body and garment have this measurement
     *   b) If yes, analyze the dimension:
     *      - Call analyzeDimension() with measurements and specs
     *      - Add result to list
     *   c) If no, skip (dimension not applicable to this garment)
     * - Confidence for each dimension = body confidence × garment OCR confidence
     *
     * **Step 3: Handle Insufficient Data**
     * - If no dimensions analyzed, return error FitResult
     * - Provide helpful message to user (add measurements, upload chart)
     *
     * **Step 4: Calculate Overall Fit**
     * - Use calculateWeightedScore() with confidence and criticality weighting
     * - Map score to category using scoreToCategory()
     *
     * **Step 5: Generate User-Facing Text**
     * - generateRecommendation(): "This size is Perfect!", etc.
     * - generateDimensionAnalysis(): Summarize each dimension's fit
     * - generateNextSteps(): Suggest alternatives or fixes
     *
     * **Step 6: Return Complete Result**
     * - FitResult with all fields for UI display and further processing
     *
     * @param body User's body measurements (from BodyProportionModel or manual input)
     * @param garment Garment specifications (from size chart or manual entry)
     * @return FitResult with complete analysis and recommendations
     *
     * **Example:**
     * ```kotlin
     * val body = BodyProportionModel.estimateMeasurements(175.0, 75.0, SizeRegion.US, Gender.MALE)
     * val garment = GarmentMeasurements(
     *     sizeLabel = "M",
     *     garmentType = GarmentType.SHIRT,
     *     chestCm = 100.0,
     *     waistCm = 92.0,
     *     shoulderCm = 42.0,
     *     ocrConfidence = 0.9
     * )
     * val result = calculateFitScore(body, garment)
     * println(result.recommendation)  // "This size is Comfortable!"
     * ```
     */
    fun calculateFitScore(
        body: EstimatedBodyMeasurements,
        garment: GarmentMeasurements
    ): FitResult {
        // STEP 1: Extract garment specifications
        val easeFactors = garment.garmentType.getEaseFactors()
        val criticalDimensions = garment.garmentType.getCriticalDimensions()

        // STEP 2: Build list of dimension analyses
        val dimensionFits = mutableListOf<DimensionFitDetail>()

        // Helper function to add dimension if both measurements exist
        fun analyzeDimensionIfPresent(
            dimensionName: String,
            bodyMeasurement: Double?,
            garmentMeasurement: Double?,
            bodyConfidence: Double
        ) {
            if (bodyMeasurement != null && garmentMeasurement != null) {
                val recommendedEase = easeFactors[dimensionName] ?: 0.0
                val isCritical = dimensionName in criticalDimensions
                val combinedConfidence = bodyConfidence * garment.ocrConfidence

                dimensionFits.add(
                    analyzeDimension(
                        dimension = dimensionName,
                        bodyMeasurement = bodyMeasurement,
                        garmentMeasurement = garmentMeasurement,
                        recommendedEase = recommendedEase,
                        isCritical = isCritical,
                        confidence = combinedConfidence
                    )
                )
            }
        }

        // Analyze chest dimension
        analyzeDimensionIfPresent(
            "chest",
            body.chest,
            garment.chestCm,
            body.confidence.breakdown["chest"]?.value ?: body.confidence.overall
        )

        // Analyze waist dimension
        analyzeDimensionIfPresent(
            "waist",
            body.waist,
            garment.waistCm,
            body.confidence.breakdown["waist"]?.value ?: body.confidence.overall
        )

        // Analyze hip dimension
        analyzeDimensionIfPresent(
            "hip",
            body.hip,
            garment.hipCm,
            body.confidence.breakdown["hip"]?.value ?: body.confidence.overall
        )

        // Analyze shoulder dimension
        analyzeDimensionIfPresent(
            "shoulder",
            body.shoulder,
            garment.shoulderCm,
            body.confidence.breakdown["shoulder"]?.value ?: body.confidence.overall
        )

        // Analyze sleeve dimension
        analyzeDimensionIfPresent(
            "sleeve",
            body.inseam,  // Use inseam as proxy for sleeve (both are length measurements)
            garment.sleeveCm,
            body.confidence.breakdown["inseam"]?.value ?: body.confidence.overall
        )

        // Analyze inseam dimension (critical for pants)
        analyzeDimensionIfPresent(
            "inseam",
            body.inseam,
            garment.lengthCm,
            body.confidence.breakdown["inseam"]?.value ?: body.confidence.overall
        )

        // STEP 3: Handle insufficient data
        if (dimensionFits.isEmpty()) {
            return FitResult(
                overallFitCategory = FitCategoryEngine.FitCategory.VERY_POOR_FIT,
                overallScore = 0.0,
                dimensionFits = emptyList(),
                recommendation = "Unable to determine fit: insufficient measurement data.",
                dimensionAnalysis = "Please provide more body measurements for a better estimate.",
                nextSteps = listOf(
                    "Try another garment type to compare",
                    "Verify your height and weight inputs"
                )
            )
        }

        // STEP 4: Calculate overall fit score and category
        val overallScore = calculateWeightedScore(dimensionFits)
        val fitCategory = scoreToCategory(overallScore, dimensionFits)

        // STEP 5: Generate user-facing text
        val recommendation = generateRecommendation(fitCategory, dimensionFits, garment.sizeLabel)
        val dimensionAnalysis = generateDimensionAnalysis(dimensionFits)
        val nextSteps = generateNextSteps(fitCategory, dimensionFits)

        // STEP 6: Return complete analysis
        return FitResult(
            overallFitCategory = fitCategory,
            overallScore = overallScore,
            dimensionFits = dimensionFits.map { it.toDomainModel() },
            recommendation = recommendation,
            dimensionAnalysis = dimensionAnalysis,
            nextSteps = nextSteps
        )
    }

    /**
     * Generates the main user-facing recommendation message.
     *
     * **Purpose:**
     * Creates a single, clear statement about whether the size fits. This is the primary
     * message shown to the user. Text is always positive and emphasizes what will work,
     * not what's wrong.
     *
     * **Text Structure:**
     * - Size label (e.g., "Size M") for clarity
     * - Category-specific advice about wearability
     * - Reference to category description for consistency
     *
     * **Philosophy:**
     * - Lead with size label for context ("Size M is...")
     * - Use "will be" for future wearability
     * - "excellent match" vs "work well" vs "wearable" vs "too..."
     * - Never say "NOT recommended" or negative statements
     * - Always include category.description for category-level detail
     *
     * @param category Overall fit category from scoreToCategory()
     * @param dimensionFits Dimension analysis (unused here but available for future enhancement)
     * @param sizeLabel Size label like "M", "38", "Large", etc.
     * @return Human-readable recommendation suitable for display to user
     *
     * **Example Outputs:**
     * - "Size M is an excellent match for your measurements! This size should fit you perfectly."
     * - "Size L should work well for you. This size should fit you comfortably."
     * - "Size XL will be wearable but may feel a bit roomy. Consider sizing down for a closer fit."
     */
    private fun generateRecommendation(
        category: FitCategoryEngine.FitCategory,
        @Suppress("UNUSED_PARAMETER") dimensionFits: List<DimensionFitDetail>,
        sizeLabel: String
    ): String {
        // Build size label text: "Size M" or just use category if no label
        val sizeText = if (sizeLabel.isNotBlank()) "Size $sizeLabel" else "This size"

        // Use when() to map category to specific recommendation text
        // Each branch includes:
        // 1. Main recommendation about fit
        // 2. Category description (detail about what that category means)
        return when (category) {
            // PERFECT: Rare case, all dimensions ideal
            FitCategoryEngine.FitCategory.VERY_COMFORTABLE ->
                "$sizeText is an excellent match for your measurements! ${category.displayName.lowercase()}."

            // GOOD: Most dimensions ideal, recommended
            FitCategoryEngine.FitCategory.COMFORTABLE ->
                "$sizeText should work well for you. ${category.displayName.lowercase()}."

            // TIGHT: Wearable but snug
            FitCategoryEngine.FitCategory.SLIGHTLY_TIGHT ->
                "$sizeText will be wearable but may feel a bit fitted. ${category.displayName.lowercase()}."

            // LOOSE: Wearable but roomy
            FitCategoryEngine.FitCategory.SLIGHTLY_LOOSE ->
                "$sizeText will be wearable but may feel a bit roomy. ${category.displayName.lowercase()}."

            // POOR: Significant issues
            FitCategoryEngine.FitCategory.POOR_FIT ->
                "$sizeText may not fit well. ${category.displayName.lowercase()}."

            // VERY POOR: Major fit issues
            FitCategoryEngine.FitCategory.VERY_POOR_FIT ->
                "$sizeText doesn't match your measurements. ${category.displayName.lowercase()}."

            // UNKNOWN: Fallback
            else -> "Unable to determine fit. Please verify garment measurements."
        }
    }

    /**
     * Generates detailed dimension-by-dimension analysis breakdown.
     *
     * **Purpose:**
     * Shows the user exactly how each dimension fits. This helps them understand
     * which measurements are the limiting factor (e.g., "chest is tight but waist is perfect").
     *
     * **Text Format:**
     * ```
     * • Chest: Perfect (garment is +8.2cm vs your body)
     * • Waist: Fitted (garment is +3.1cm vs your body)
     * • Shoulder: Snug (garment is +1.2cm vs your body)
     * ```
     *
     * **Ease Interpretation:**
     * - Positive ease (+8.2cm): Garment is larger than body (looser fit)
     * - Negative ease (-2.0cm): Garment is smaller than body (tighter fit, common in tight jeans)
     * - Zero ease (0.0cm): Garment equals body measurement (perfect fit, rare)
     *
     * **Why Show Ease:**
     * - Shows how much extra room or tightness user will experience
     * - Helps expert users understand fit mechanics
     * - Educational for future size selection
     *
     * @param dimensionFits Individual dimension fit analysis results
     * @return Formatted string with bullet points, one per dimension
     *
     * **Example:**
     * ```
     * • Chest: Perfect (garment is +8.2cm vs your body)
     * • Waist: Fitted (garment is +2.1cm vs your body)
     * • Inseam: Too Long (garment is +1.5cm vs your body)
     * ```
     */
    private fun generateDimensionAnalysis(dimensionFits: List<DimensionFitDetail>): String {
        // Handle empty case gracefully
        if (dimensionFits.isEmpty()) return "No dimension data available."

        // Build bullet point for each dimension
        val dimensionStrings = dimensionFits.map { fit ->
            // Capitalize first letter of dimension name
            val dimensionName = fit.dimensionName.replaceFirstChar { it.uppercaseChar() }

            // Format ease with sign: +8.2cm or -2.0cm or 0.0cm
            val easeSign = when {
                fit.actualEase > 0.0 -> "+"
                fit.actualEase < 0.0 -> ""  // Negative sign already in number
                else -> ""
            }
            val easeFormatted = "%.1f".format(fit.actualEase)

            // Build bullet: "• Chest: Perfect (garment is +8.2cm vs your body)"
            "• $dimensionName: ${fit.assessment} (garment is $easeSign$easeFormatted cm vs your body)"
        }

        // Join with newlines for multi-line display
        return dimensionStrings.joinToString("\n")
    }

    /**
     * Generates actionable next steps for the user.
     *
     * **Purpose:**
     * Tells the user exactly what to do next. This is critical for conversion:
     * "Try size L" is more actionable than "This is too small."
     *
     * **Strategy:**
     * 1. **Size alternatives**: Recommend specific next size to try (up/down/different)
     * 2. **Key issues**: Identify which dimension is the limiting factor
     * 3. **Confidence warning**: Alert user to low-confidence measurements that need verification
     *
     * **Low Confidence Threshold:** 0.7
     * - < 0.7: Estimated or low OCR confidence (risky to rely on)
     * - >= 0.7: Either manually measured or high OCR confidence (reliable)
     *
     * @param category Overall fit category from scoreToCategory()
     * @param dimensionFits Dimension-level analysis with assessments and confidence
     * @return List of action items in priority order
     *
     * **Example Output:**
     * ```
     * ["Try the next size up",
     *  "Key issue: chest (garment is too tight)",
     *  "⚠️ Manually measure: shoulder for better accuracy"]
     * ```
     */
    private fun generateNextSteps(
        category: FitCategoryEngine.FitCategory,
        dimensionFits: List<DimensionFitDetail>
    ): List<String> {
        val steps = mutableListOf<String>()

        // STEP 1: Recommend size change based on category
        when (category) {
            // TOO TIGHT: Recommend sizing up
            FitCategoryEngine.FitCategory.SLIGHTLY_TIGHT -> {
                steps.add("Try the next size up")

                // Find which dimensions are tight (for user education)
                val tightDimensions = dimensionFits
                    .filter { it.assessment in listOf("Too Tight", "Snug") }
                    .map { it.dimensionName.replaceFirstChar { c -> c.uppercaseChar() } }

                if (tightDimensions.isNotEmpty()) {
                    steps.add("Key issue: ${tightDimensions.joinToString(", ")} (too snug)")
                }
            }

            // TOO LOOSE: Recommend sizing down
            FitCategoryEngine.FitCategory.SLIGHTLY_LOOSE -> {
                steps.add("Try the next size down")

                // Find which dimensions are loose
                val looseDimensions = dimensionFits
                    .filter { it.assessment in listOf("Too Loose", "Loose") }
                    .map { it.dimensionName.replaceFirstChar { c -> c.uppercaseChar() } }

                if (looseDimensions.isNotEmpty()) {
                    steps.add("Key issue: ${looseDimensions.joinToString(", ")} (too roomy)")
                }
            }

            // POOR: Multiple issues, may need bigger change
            FitCategoryEngine.FitCategory.POOR_FIT -> {
                // Determine if tight or loose dominates
                val tightCount = dimensionFits.count { it.assessment in listOf("Too Tight", "Snug") }
                val looseCount = dimensionFits.count { it.assessment in listOf("Too Loose", "Loose") }

                when {
                    tightCount > looseCount -> steps.add("Try 2+ sizes up")
                    looseCount > tightCount -> steps.add("Try 2+ sizes down")
                    else -> steps.add("Try a different size or brand")
                }
            }

            // VERY POOR: Major misfit, significant size change needed
            FitCategoryEngine.FitCategory.VERY_POOR_FIT -> {
                val tightCount = dimensionFits.count { it.assessment in listOf("Too Tight", "Snug") }
                val looseCount = dimensionFits.count { it.assessment in listOf("Too Loose", "Loose") }

                when {
                    tightCount > looseCount -> steps.add("Try several sizes larger")
                    looseCount > tightCount -> steps.add("Try several sizes smaller")
                    else -> steps.add("This style may not fit your proportions")
                }
            }

            // GOOD/EXCELLENT: No size change needed
            FitCategoryEngine.FitCategory.VERY_COMFORTABLE,
            FitCategoryEngine.FitCategory.COMFORTABLE -> {
                steps.add("This size should work great for you!")
            }

            // UNKNOWN: Ask for more data
            else -> {
                steps.add("Add more measurements for better recommendations")
            }
        }

        // STEP 2: Check for low-confidence measurements
        // These need manual verification since estimates may be wrong
        val lowConfidenceDimensions = dimensionFits
            .filter { it.confidence < 0.7 }
            .map { it.dimensionName.replaceFirstChar { c -> c.uppercaseChar() } }

        if (lowConfidenceDimensions.isNotEmpty()) {
            steps.add("⚠️ Manually measure: ${lowConfidenceDimensions.joinToString(", ")} for better accuracy")
        }

        return steps
    }

    private fun DimensionFitDetail.toDomainModel(): com.vanexa.supercalculator.domain.model.DimensionFit {
        return com.vanexa.supercalculator.domain.model.DimensionFit(
            dimension = dimensionName,
            bodyMeasurement = bodyMeasurement,
            garmentMeasurement = garmentMeasurement,
            recommendedEase = recommendedEase,
            actualEase = actualEase,
            fitScore = fitScore,
            assessment = assessment,
            isCritical = isCritical,
            confidence = confidence
        )
    }
}
