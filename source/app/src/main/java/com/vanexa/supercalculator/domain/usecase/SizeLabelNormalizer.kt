package com.vanexa.supercalculator.domain.usecase

/**
 * Size Label Normalization - PRD Section 8
 * Normalizes size labels from multiple languages and formats
 */
object SizeLabelNormalizer {
    
    /**
     * Comprehensive multi-language size label mapping
     * PRD Section 8.1
     */
    private val sizeMappings = mapOf(
        // English
        "EXTRA SMALL" to "XS",
        "SMALL" to "S",
        "MEDIUM" to "M",
        "LARGE" to "L",
        "EXTRA LARGE" to "XL",
        "DOUBLE XL" to "XXL",
        "TRIPLE XL" to "XXXL",
        
        // Indonesian
        "EKSTRA KECIL" to "XS",
        "KECIL" to "S",
        "SEDANG" to "M",
        "BESAR" to "L",
        "EKSTRA BESAR" to "XL",
        
        // Japanese (Romaji)
        "ESUSU" to "SS",
        "ESU" to "S",
        "EMU" to "M",
        "ERU" to "L",
        "EKUSERU" to "XL",
        
        // Korean (Romaji)
        "SO" to "S",
        "JUNG" to "M",
        "DAE" to "L",
        
        // Chinese (Pinyin)
        "XIAO" to "S",
        "ZHONG" to "M",
        "DA" to "L",
        
        // Spanish
        "PEQUEÑO" to "S",
        "MEDIANO" to "M",
        "GRANDE" to "L",
        
        // French
        "PETIT" to "S",
        "MOYEN" to "M",
        "GRAND" to "L",
        
        // German
        "KLEIN" to "S",
        "MITTEL" to "M",
        "GROSS" to "L"
    )
    
    /**
     * Numeric size standardization
     * PRD Section 8.1
     */
    private val numericStandards = mapOf(
        // EU numeric to standard
        44 to "XS", 46 to "S", 48 to "M", 50 to "L", 
        52 to "XL", 54 to "XXL", 56 to "XXXL",
        
        // UK numeric to standard
        6 to "XS", 8 to "S", 10 to "M", 12 to "L",
        14 to "XL", 16 to "XXL", 18 to "XXXL",
        
        // US numeric to standard
        2 to "XS", 4 to "S", 6 to "S", 8 to "M",
        10 to "M", 12 to "L", 14 to "L", 16 to "XL"
    )
    
    /**
     * Pants/jeans waist size to general size mapping
     * PRD Section 8.1
     */
    fun normalizePantsSize(waistInches: Int): String {
        return when (waistInches) {
            in 26..27 -> "XS"
            in 28..29 -> "S"
            in 30..32 -> "M"
            in 33..34 -> "L"
            in 36..38 -> "XL"
            in 40..42 -> "XXL"
            else -> "CUSTOM"
        }
    }
    
    /**
     * Normalize any size label to standard format
     * PRD Section 8.1
     * PHASE 4 FIX #14: Improved normalization with OCR fixes
     */
    fun normalize(rawLabel: String): String {
        // PHASE 4 FIX #14: Enhanced cleaning with OCR error correction
        val cleaned = rawLabel
            .uppercase()
            .trim()
            .replace(Regex("\\s+"), " ")  // Normalize whitespace
            .replace("-", " ")            // Replace hyphens with spaces
            .replace("XI", "XL")          // OCR fix: XI often misread as XL
            .replace("IVI", "M")          // OCR fix: IVI often misread as M
        
        // Direct match first (PHASE 4 FIX #14)
        if (cleaned in listOf("XS", "S", "M", "L", "XL", "XXL", "XXXL", "4XL", "5XL")) {
            return cleaned
        }
        
        // Extended sizes (4XL, 5XL, etc.)
        val extendedSizePattern = Regex("^(\\d+)XL$")
        extendedSizePattern.find(cleaned)?.let { match ->
            val number = match.groupValues[1].toIntOrNull()
            if (number != null && number >= 4) {
                return cleaned // Keep extended sizes as-is (4XL, 5XL, etc.)
            }
        }
        
        // Check mapping dictionary
        sizeMappings[cleaned]?.let { return it }
        
        // Check if numeric
        cleaned.toIntOrNull()?.let { number ->
            // Pants size (26-42 range)
            if (number in 26..42) {
                return normalizePantsSize(number)
            }
            // EU/UK/US size
            numericStandards[number]?.let { return it }
        }
        
        // Handle "SIZE" prefix patterns (Size8, Size9, etc.) - convert to numeric XL
        val sizePrefixPattern = Regex("^SIZE(\\d+)$")
        sizePrefixPattern.find(cleaned)?.let { match ->
            val number = match.groupValues[1].toIntOrNull()
            if (number != null) {
                // Convert Size8 -> 8XL, Size9 -> 9XL, etc.
                if (number >= 4) {
                    return "${number}XL"
                } else {
                    // For Size1-7, map to standard sizes
                    val standardSizes = listOf("XS", "S", "M", "L", "XL", "XXL", "XXXL")
                    if (number <= standardSizes.size) {
                        return standardSizes[number - 1]
                    }
                }
            }
        }
        
        // Fallback: return as-is
        return cleaned
    }
}

