package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*

class ClothingSizeUseCase {
    
    private val mensShirts = listOf(
        // US Sizes
        ClothingSize("S", SizeRegion.US, 86..91, 71..76, 165, 54..64),
        ClothingSize("M", SizeRegion.US, 96..101, 81..86, 170, 64..73),
        ClothingSize("L", SizeRegion.US, 106..111, 91..96, 175, 73..82),
        ClothingSize("XL", SizeRegion.US, 116..121, 101..106, 180, 82..91),
        ClothingSize("XXL", SizeRegion.US, 126..132, 111..117, 185, 91..100),
        ClothingSize("XXXL", SizeRegion.US, 137..142, 122..127, 188, 100..109),
        
        // EU Sizes
        ClothingSize("S", SizeRegion.EU, 88..93, 72..77, 165, 54..64),
        ClothingSize("M", SizeRegion.EU, 98..103, 82..87, 170, 64..73),
        ClothingSize("L", SizeRegion.EU, 108..113, 92..97, 175, 73..82),
        ClothingSize("XL", SizeRegion.EU, 118..123, 102..107, 180, 82..91),
        ClothingSize("XXL", SizeRegion.EU, 128..134, 112..118, 185, 91..100),
        ClothingSize("XXXL", SizeRegion.EU, 139..144, 123..128, 188, 100..109),
        
        // UK Sizes
        ClothingSize("S", SizeRegion.UK, 86..91, 71..76, 165, 54..64),
        ClothingSize("M", SizeRegion.UK, 96..101, 81..86, 170, 64..73),
        ClothingSize("L", SizeRegion.UK, 106..111, 91..96, 175, 73..82),
        ClothingSize("XL", SizeRegion.UK, 116..121, 101..106, 180, 82..91),
        ClothingSize("XXL", SizeRegion.UK, 126..132, 111..117, 185, 91..100),
        ClothingSize("XXXL", SizeRegion.UK, 137..142, 122..127, 188, 100..109),
        
        // Asia Sizes (typically smaller)
        ClothingSize("S", SizeRegion.ASIA, 84..89, 69..74, 160, 50..60),
        ClothingSize("M", SizeRegion.ASIA, 94..99, 79..84, 165, 60..70),
        ClothingSize("L", SizeRegion.ASIA, 104..109, 89..94, 170, 70..80),
        ClothingSize("XL", SizeRegion.ASIA, 114..119, 99..104, 175, 80..90),
        ClothingSize("XXL", SizeRegion.ASIA, 124..130, 109..115, 180, 90..100),
        ClothingSize("XXXL", SizeRegion.ASIA, 135..140, 120..125, 183, 100..110),
        
        // Indonesia Sizes
        ClothingSize("S", SizeRegion.ID, 86..91, 71..76, 165, 54..64),
        ClothingSize("M", SizeRegion.ID, 96..101, 81..86, 170, 64..73),
        ClothingSize("L", SizeRegion.ID, 106..111, 91..96, 175, 73..82),
        ClothingSize("XL", SizeRegion.ID, 116..121, 101..106, 180, 82..91),
        ClothingSize("XXL", SizeRegion.ID, 126..132, 111..117, 185, 91..100),
        ClothingSize("XXXL", SizeRegion.ID, 137..142, 122..127, 188, 100..109)
    )
    
    fun recommendSize(
        height: Double,
        weight: Double,
        chest: Double?,
        waist: Double?,
        region: SizeRegion
    ): SizeRecommendation {
        val scores = mensShirts
            .filter { it.region == region }
            .map { size ->
                var score = 0.0
                val maxScore = 100.0
                
                if (height >= size.heightCmMin) score += 25
                if (weight.toInt() in size.weightKgRange) score += 25
                chest?.let { if (it.toInt() in size.chestCm) score += 25 }
                waist?.let { if (it.toInt() in size.waistCm) score += 25 }
                
                size to score
            }
            .sortedByDescending { it.second }
        
        return SizeRecommendation(
            primarySize = scores.first().first,
            alternativeSizes = scores.take(3).map { it.first },
            confidence = scores.first().second
        )
    }
}

