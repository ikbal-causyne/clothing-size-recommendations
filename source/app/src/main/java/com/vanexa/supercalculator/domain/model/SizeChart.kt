package com.vanexa.supercalculator.domain.model

import java.io.Serializable

/**
 * Size Chart Domain Models - PRD v2.0 Compliant
 * Section 3-4: User Input Models and Size Chart Entry
 */

// Section 3.1: User Input Models
data class UserBodyInput(
    val height: Double,           // cm (130-230 cm range)
    val weight: Double,            // kg (30-200 kg range)
    val gender: Gender,            // MALE, FEMALE (from Health.kt)
    val region: SizeRegion         // US, EU, UK, ASIA, INDONESIA (from Health.kt)
) : Serializable

// Type alias for PRD compliance (FitRegion = SizeRegion)
typealias FitRegion = SizeRegion

// Section 3.2: Optional Measurements
data class OptionalMeasurements(
    val chestCircumference: Double? = null,    // cm
    val waistCircumference: Double? = null,    // cm
    val hipCircumference: Double? = null,      // cm
    val shoulderWidth: Double? = null,         // cm (for tops)
    val inseamLength: Double? = null           // cm (for pants)
) : Serializable

// Section 4.1: Size Chart Entry
data class SizeChartEntry(
    val sizeLabel: String,                      // "M", "38", "Large", etc.
    val chest: MeasurementRange? = null,        // Upper body
    val waist: MeasurementRange? = null,         // All garments
    val hip: MeasurementRange? = null,          // Lower body, some tops
    val shoulder: MeasurementRange? = null,     // Shirts, jackets
    val length: MeasurementRange? = null,       // Garment length
    val inseam: MeasurementRange? = null,       // Pants only
    val sleeve: MeasurementRange? = null        // Tops with sleeves
) : Serializable

data class MeasurementRange(
    val min: Double,
    val max: Double,
    val unit: MeasurementUnit = MeasurementUnit.CM,
    val confidence: Double = 1.0  // OCR/parsing confidence (0.0-1.0)
) : Serializable

enum class MeasurementUnit {
    CM, INCH
}

// Section 2: Garment Types
enum class GarmentType {
    TSHIRT,      // Upper body
    SHIRT,       // Upper body
    POLO_SHIRT,  // Upper body
    HOODIE,     // Upper body
    SWEATER,    // Upper body
    JACKET,     // Upper body
    BLAZER,     // Upper body
    JEANS,      // Lower body
    TROUSERS,   // Lower body
    CHINOS,     // Lower body
    CARGO_PANTS,// Lower body
    SHORTS,     // Lower body
    JOGGERS;    // Lower body

    /**
     * Ease factors for each dimension (cm of extra room beyond body measurement)
     * These are recommendations for comfortable fit based on garment type
     */
    fun getEaseFactors(): Map<String, Double> {
        return when (this) {
            TSHIRT -> mapOf("chest" to 6.0, "sleeve" to 1.5)
            SHIRT -> mapOf("chest" to 8.0, "shoulder" to 2.0, "sleeve" to 2.0, "waist" to 5.0)
            POLO_SHIRT -> mapOf("chest" to 6.5, "shoulder" to 1.5, "sleeve" to 1.5, "waist" to 4.0)
            HOODIE -> mapOf("chest" to 8.0, "sleeve" to 2.0, "waist" to 6.0)
            SWEATER -> mapOf("chest" to 6.0, "waist" to 4.0, "sleeve" to 2.0)
            JACKET -> mapOf("chest" to 12.0, "shoulder" to 3.0, "sleeve" to 2.0, "waist" to 8.0)
            BLAZER -> mapOf("chest" to 10.0, "shoulder" to 2.5, "sleeve" to 2.0, "waist" to 6.0)
            JEANS -> mapOf("waist" to 2.0, "inseam" to 0.0)
            TROUSERS -> mapOf("waist" to 3.0, "inseam" to 0.0)
            CHINOS -> mapOf("waist" to 3.0, "inseam" to 0.0)
            CARGO_PANTS -> mapOf("waist" to 3.5, "inseam" to 0.0)
            SHORTS -> mapOf("waist" to 3.0, "inseam" to 1.0)
            JOGGERS -> mapOf("waist" to 5.0, "inseam" to 2.0)
        }
    }

    /**
     * Critical dimensions for each garment type
     * These dimensions are deal-breakers for fit quality
     */
    fun getCriticalDimensions(): Set<String> {
        return when (this) {
            TSHIRT, POLO_SHIRT, HOODIE, SWEATER -> setOf("chest")
            SHIRT, JACKET, BLAZER -> setOf("chest", "shoulder")
            JEANS, TROUSERS, CHINOS -> setOf("waist", "inseam")
            CARGO_PANTS -> setOf("waist", "inseam")
            SHORTS -> setOf("waist")
            JOGGERS -> setOf("waist")
        }
    }
}

// Section 5.4: Body Estimation Models
data class EstimatedBodyMeasurements(
    val chest: Double,                           // BUG 7: Should validate > 0 (handled by PreScoringValidator)
    val waist: Double,
    val hip: Double,
    val shoulder: Double,
    val inseam: Double,
    val confidence: ConfidenceScore,
    val estimationMethod: Map<String, EstimationMethod> = emptyMap()  // Track what was estimated vs measured
) : Serializable {
    // BUG 7 FIX: Validation to prevent zeros (unless marked NOT_APPLICABLE)
    // ✅ FIX #1.3: Use getOrDefault to prevent crash when key doesn't exist in map
    init {
        // Allow 0.0 if the measurement is not applicable for the garment type
        // Default to ESTIMATED if key not present (requires validation)
        if (estimationMethod.getOrDefault("chest", EstimationMethod.ESTIMATED) != EstimationMethod.NOT_APPLICABLE) 
            require(chest > 0.0) { "Chest measurement must be > 0" }
            
        if (estimationMethod.getOrDefault("waist", EstimationMethod.ESTIMATED) != EstimationMethod.NOT_APPLICABLE)
            require(waist > 0.0) { "Waist measurement must be > 0" }
            
        if (estimationMethod.getOrDefault("hip", EstimationMethod.ESTIMATED) != EstimationMethod.NOT_APPLICABLE)
            require(hip > 0.0) { "Hip measurement must be > 0" }
            
        if (estimationMethod.getOrDefault("shoulder", EstimationMethod.ESTIMATED) != EstimationMethod.NOT_APPLICABLE)
            require(shoulder > 0.0) { "Shoulder measurement must be > 0" }
            
        if (estimationMethod.getOrDefault("inseam", EstimationMethod.ESTIMATED) != EstimationMethod.NOT_APPLICABLE)
            require(inseam > 0.0) { "Inseam measurement must be > 0" }
    }
}

enum class EstimationMethod {
    MEASURED,        // User provided actual measurement
    ESTIMATED,       // Calculated from height/weight formulas
    ML_ESTIMATED,    // Predicted using machine learning models
    NOT_APPLICABLE   // Not relevant for current garment type
}

data class ConfidenceScore(
    val overall: Double,              // 0.0 to 1.0
    val breakdown: Map<String, MeasurementConfidence> // per measurement with method and variance
) : Serializable

data class MeasurementConfidence(
    val value: Double,           // 0.0 to 1.0
    val method: EstimationMethod,
    val variance: Double         // Expected error margin in cm
) : Serializable

enum class BodyFrame {
    SLIM, AVERAGE, ATHLETIC, PLUS_SIZE
}

// BUG 6 FIX: Replace String dimensions with enum
enum class BodyDimension {
    CHEST, WAIST, HIP, SHOULDER, LENGTH, INSEAM, SLEEVE
}

// ✅ FIX #28: Extension function for consistent dimension name conversion
fun BodyDimension.displayName(): String {
    return when (this) {
        BodyDimension.CHEST -> "chest"
        BodyDimension.WAIST -> "waist"
        BodyDimension.HIP -> "hip"
        BodyDimension.SHOULDER -> "shoulder"
        BodyDimension.INSEAM -> "inseam"
        BodyDimension.LENGTH -> "length"
        BodyDimension.SLEEVE -> "sleeve"
    }
}

// ✅ FIX #6: Extension function for user-friendly garment type labels
fun GarmentType.displayName(): String {
    return when (this) {
        GarmentType.TSHIRT -> "T-Shirt"
        GarmentType.SHIRT -> "Shirt"
        GarmentType.POLO_SHIRT -> "Polo Shirt"
        GarmentType.HOODIE -> "Hoodie"
        GarmentType.SWEATER -> "Sweater"
        GarmentType.JACKET -> "Jacket"
        GarmentType.BLAZER -> "Blazer"
        GarmentType.JEANS -> "Jeans"
        GarmentType.TROUSERS -> "Trousers"
        GarmentType.CHINOS -> "Chinos"
        GarmentType.CARGO_PANTS -> "Cargo Pants"
        GarmentType.SHORTS -> "Shorts"
        GarmentType.JOGGERS -> "Joggers"
    }
}

// Section 7: Fit Scoring Models
data class FitScore(
    val overall: Double,                          // 0.0 to 1.0 (confidence-weighted)
    val rawFitScore: Double,                      // 0.0 to 1.0 (raw score before confidence weighting)
    val dataConfidence: Double,                    // 0.0 to 1.0 (overall data quality confidence)
    val dimensionScores: Map<BodyDimension, DimensionScore>,  // BUG 6 FIX: Use enum instead of String
    val fitQuality: FitQuality,
    val recommendation: String,
    val unreliableDimensions: Map<BodyDimension, Double> = emptyMap()  // BUG 9 FIX: Expose unreliable dimensions
) : Serializable

data class DimensionScore(
    val value: Double,                            // Confidence-adjusted score (BUG 1 FIX: confidence applied per-dimension)
    val rawScore: Double,                         // Raw Gaussian score before confidence weighting
    val confidence: Double,                       // Combined confidence for this dimension
    val status: FitStatus,
    val difference: Double,                      // cm (negative = too small, positive = too big)
    val note: String
) : Serializable

/**
 * Individual dimension fit analysis result
 * Used by Phase 3: DimensionFit scoring engine
 */
data class DimensionFit(
    val dimension: String,                        // "chest", "waist", "hip", "shoulder", "inseam", etc.
    val bodyMeasurement: Double,                  // User's actual body measurement (cm)
    val garmentMeasurement: Double,               // Garment measurement from size chart (cm)
    val recommendedEase: Double,                  // Recommended ease for this dimension (cm)
    val actualEase: Double,                       // Actual ease = garmentMeasurement - bodyMeasurement (cm)
    val fitScore: Double,                         // 0.0-1.0 score (0=bad, 1=perfect)
    val assessment: String,                       // "Perfect", "Fitted", "Relaxed", "Snug", "Too Tight", "Loose", "Too Loose"
    val isCritical: Boolean,                      // True if this is a critical dimension for fit
    val confidence: Double                        // 0.0-1.0 measurement confidence
) : Serializable

enum class FitPreference {
    SLIM,       // Closer to minimum measurements
    REGULAR,    // Middle of range
    RELAXED     // Closer to maximum measurements
}

enum class FitStatus {
    EXCELLENT,
    GOOD,
    ACCEPTABLE,
    TIGHT_OR_LOOSE,
    NOT_RECOMMENDED
}

enum class FitQuality {
    EXCELLENT,
    VERY_GOOD,
    GOOD,
    ACCEPTABLE,
    MARGINAL,
    NOT_RECOMMENDED
}

// Section 7.3: Size Recommendation Models
data class SizeRecommendationResult(
    val primaryRecommendation: ScoredSize,
    val alternativeRecommendations: List<ScoredSize>,
    val betweenSizesInfo: BetweenSizesInfo?,
    val overallConfidence: Double
) : Serializable

data class ScoredSize(
    val sizeEntry: SizeChartEntry,
    val fitScore: FitScore
) : Serializable

data class BetweenSizesInfo(
    val size1: String,
    val size2: String,
    val guidance: String
) : Serializable

// Section 6.2: Regional Normalization
data class RegionalCoefficients(
    val chest: Double,
    val waist: Double,
    val hip: Double,
    val fitStyle: String
) : Serializable

// Section 9: OCR Models
sealed class OCRResult : Serializable {
    data class Success(val sizeChart: List<SizeChartEntry>) : OCRResult()
    data class Error(val message: String) : OCRResult()
}

data class RawSizeData(
    val unit: MeasurementUnit,
    val headers: Map<String, Int>,
    val rows: List<Map<String, String>>,
    val rawText: String
) : Serializable

// Section 13: Validation
sealed class ValidationResult : Serializable {
    object Success : ValidationResult()
    data class Warning(val message: String) : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

// Legacy models for backward compatibility
enum class BodyShape {
    RECTANGLE, TRIANGLE, INVERTED_TRIANGLE, HOURGLASS, ROUND,
    RECTANGULAR, PEAR, APPLE // Additional legacy values
}

data class PredictedDimension(
    val value: Double,
    val confidence: Double = 0.0,
    val min: Double? = null,
    val max: Double? = null,
    val lowerBound: Double = min ?: value,
    val upperBound: Double = max ?: value,
    val uncertainty: Double = 0.0
) : Serializable

data class ConfidenceInterval(
    val lower: Double,
    val upper: Double,
    val lowerBound: Double = lower,
    val upperBound: Double = upper,
    val mean: Double = (lower + upper) / 2.0,
    val confidence: Double = 0.95,
    val confidenceLevel: Double = confidence
) : Serializable

data class PredictedBodyDimensions(
    val chest: PredictedDimension,
    val waist: PredictedDimension,
    val hip: PredictedDimension,
    val hips: PredictedDimension = hip, // Alias for hip
    val shoulder: PredictedDimension? = null,
    val inseam: PredictedDimension? = null,
    val overallConfidence: Double = 0.0
) : Serializable

data class EnhancedSizeRecommendation(
    val primarySize: SizeData? = null,
    val recommendedSize: String = primarySize?.size ?: "",
    val alternativeSizes: List<SizeData> = emptyList(),
    val confidence: Double = 0.0,
    val fitScore: Double = 0.0,
    val confidenceInterval: Double = 0.0,
    val sizeChart: SizeChart? = null,
    val method: String = ""
) : Serializable
data class FabricComposition(
    val cottonPercent: Double = 0.0,
    val polyesterPercent: Double = 0.0,
    val elastanePercent: Double = 0.0
) : Serializable {
    init {
        require(cottonPercent >= 0.0 && cottonPercent <= 100.0) { "Cotton percentage must be 0-100" }
        require(polyesterPercent >= 0.0 && polyesterPercent <= 100.0) { "Polyester percentage must be 0-100" }
        require(elastanePercent >= 0.0 && elastanePercent <= 100.0) { "Elastane percentage must be 0-100" }
    }
    
    val totalPercent: Double
        get() = cottonPercent + polyesterPercent + elastanePercent
}

data class BodyComposition(
    val bmi: Double,
    val bodyFatPercent: Double,
    val leanMass: Double
) : Serializable

// Legacy SizeChart model for DataStore compatibility
enum class SizeClothingType {
    SHIRT, PANTS, UNKNOWN
}

data class SizeData(
    val size: String,
    val chestCm: IntRange? = null,
    val waistCm: IntRange? = null,
    val hipsCm: IntRange? = null,
    val lengthCm: IntRange? = null,
    val heightCmMin: Double = 0.0,
    val weightKgRange: IntRange = 0..200
) : Serializable {
    val hasValidData: Boolean
        get() = chestCm != null || waistCm != null || hipsCm != null || lengthCm != null
}

data class SizeChartMetadata(
    val format: String = "UNKNOWN",
    val confidence: Double = 0.0,
    val parsedFields: Set<String> = emptySet()
) : Serializable

data class SizeChart(
    val id: String = "SC_${System.currentTimeMillis()}",
    val source: String = "Manual Input",
    val region: SizeRegion,
    val clothingType: SizeClothingType,
    val sizes: List<SizeData>,
    val createdAt: Long = System.currentTimeMillis(),
    val metadata: SizeChartMetadata = SizeChartMetadata(),
    val heightCmMin: Double = 0.0,
    val weightKgRange: IntRange = 0..200
) : Serializable
