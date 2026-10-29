package com.vanexa.data.models 

import kotlinx.serialization.Serializable

@Serializable
data class SizeChartEntry(
    val sizeLabel: String, // "XS", "S", "M", "L", etc.
    val chestCm: Double? = null,
    val chestInch: Double? = null,
    val waistCm: Double? = null,
    val waistInch: Double? = null,
    val hipCm: Double? = null,
    val hipInch: Double? = null,
    val shoulderCm: Double? = null,
    val shoulderInch: Double? = null,
    val sleeveCm: Double? = null,
    val sleeveInch: Double? = null,
    val lengthCm: Double? = null,
    val lengthInch: Double? = null,
    val inseamCm: Double? = null,
    val inseamInch: Double? = null,

    // Pants fields
    val riseCm: Double? = null,
    val riseInch: Double? = null,
    val thighCm: Double? = null,
    val thighInch: Double? = null,
    val openingCm: Double? = null, // Leg opening
    val openingInch: Double? = null,
    val outseamCm: Double? = null,
    val outseamInch: Double? = null
) {
    fun toCm(unit: MeasurementUnit): Map<String, Double> {
        fun convert(cm: Double?, inch: Double?): Double? =
            cm ?: inch?.times(2.54)

        return buildMap {
            convert(chestCm, chestInch)?.let { put("chest", it) }
            convert(waistCm, waistInch)?.let { put("waist", it) }
            convert(hipCm, hipInch)?.let { put("hip", it) }
            convert(shoulderCm, shoulderInch)?.let { put("shoulder", it) }
            convert(sleeveCm, sleeveInch)?.let { put("sleeve", it) }
            convert(lengthCm, lengthInch)?.let { put("length", it) }
            convert(inseamCm, inseamInch)?.let { put("inseam", it) }
            // Pants fields
            convert(riseCm, riseInch)?.let { put("rise", it) }
            convert(thighCm, thighInch)?.let { put("thigh", it) }
            convert(openingCm, openingInch)?.let { put("opening", it) }
            convert(outseamCm, outseamInch)?.let { put("outseam", it) }
        }
    }
}

@Serializable
data class ManualSizeChart(
    val garmentType: String,
    val unit: MeasurementUnit,
    val entries: List<SizeChartEntry>,
    val createdAt: Long = System.currentTimeMillis()
)

enum class MeasurementUnit {
    CM, INCH;

    fun toggle() = if (this == CM) INCH else CM
}

// Standard size labels by garment type
object SizeLabels {
    // EXISTING - DO NOT TOUCH
    val SHIRT = listOf("XS", "S", "M", "L", "XL", "2XL", "3XL", "4XL", "5XL", "6XL")
    val DRESS = listOf("XS", "S", "M", "L", "XL", "XXL", "XXXL")
    val JACKET = listOf("XS", "S", "M", "L", "XL", "2XL", "3XL", "4XL")
    
    // NEW - PANTS/BOTTOMS
    val PANTS_WAIST = listOf("26", "28", "29", "30", "31", "32", "33", "34", "36", "38", "40", "42", "44", "46", "48", "50")
    val PANTS_LENGTH = listOf("28", "30", "32", "34", "36", "38")
    val SHORTS = listOf("26", "28", "30", "32", "34", "36", "38", "40", "42", "44", "46", "48") // Waist only
    
    // Generate waist x length combinations
    fun generatePantsSizes(): List<String> {
        val sizes = mutableListOf<String>()
        // Common combinations
        listOf(28, 30, 32, 34, 36, 38, 40, 42).forEach { waist ->
            listOf(28, 30, 32, 34, 36).forEach { length ->
                sizes.add("${waist}x${length}")
            }
        }
        return sizes
    }

    fun forGarmentType(type: String): List<String> {
        return when (type.uppercase()) {
            "SHIRT", "T-SHIRT", "POLO SHIRT", "HOODIE" -> SHIRT
            "JEANS", "CHINOS", "CARGO PANTS", "TROUSERS" -> generatePantsSizes()
            "SHORTS", "JOGGERS" -> SHORTS
            "PANTS" -> generatePantsSizes()
            "DRESS" -> DRESS
            "JACKET", "COAT", "BLAZER" -> JACKET
            else -> SHIRT
        }
    }

    fun isPantsType(type: String): Boolean {
        return type.uppercase() in listOf("JEANS", "CHINOS", "CARGO PANTS", "TROUSERS", "PANTS")
    }

    fun isShortsType(type: String): Boolean {
        return type.uppercase() in listOf("SHORTS", "JOGGERS")
    }
}

