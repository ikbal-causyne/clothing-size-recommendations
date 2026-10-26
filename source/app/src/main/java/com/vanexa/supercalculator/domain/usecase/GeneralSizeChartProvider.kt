package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*

/**
 * GeneralSizeChartProvider
 *
 * Provides standard international size charts for general size recommendations
 * when no brand-specific size chart is available.
 *
 * Data sourced from ISO 8559 / EN 13402 international sizing standards
 * and major market sizing conventions (US, EU, UK, Asia, Indonesia).
 *
 * Usage: When the user requests a recommendation WITHOUT providing a size chart,
 * the engine uses these standard charts as fallback input — the rest of the
 * pipeline (BodyEstimator → FitScoring → SizeRecommendation) runs identically.
 */
object GeneralSizeChartProvider {

    /**
     * Returns a standard size chart for the given region, garment type, and gender.
     * All measurements are in CM.
     */
    fun getStandardChart(
        region: SizeRegion,
        garmentType: GarmentType,
        gender: Gender
    ): List<SizeChartEntry> {
        return if (garmentType.isTopGarment()) {
            getTopChart(region, gender)
        } else {
            getBottomChart(region, gender)
        }
    }

    // ─── Top Garments (T-Shirt, Shirt, Polo, Hoodie, Sweater, Jacket, Blazer) ───

    private fun getTopChart(region: SizeRegion, gender: Gender): List<SizeChartEntry> {
        return when (gender) {
            Gender.MALE -> getMaleTopChart(region)
            Gender.FEMALE -> getFemaleTopChart(region)
        }
    }

    private fun getMaleTopChart(region: SizeRegion): List<SizeChartEntry> {
        // Standard male top sizing per region (chest / waist / hip in cm)
        return when (region) {
            SizeRegion.US -> listOf(
                topEntry("XS", chest = 82.0..87.0, waist = 68.0..73.0, hip = 82.0..87.0),
                topEntry("S",  chest = 87.0..92.0, waist = 73.0..78.0, hip = 87.0..92.0),
                topEntry("M",  chest = 92.0..100.0, waist = 78.0..86.0, hip = 92.0..100.0),
                topEntry("L",  chest = 100.0..108.0, waist = 86.0..94.0, hip = 100.0..108.0),
                topEntry("XL", chest = 108.0..116.0, waist = 94.0..102.0, hip = 108.0..116.0),
                topEntry("2XL", chest = 116.0..124.0, waist = 102.0..110.0, hip = 116.0..124.0),
                topEntry("3XL", chest = 124.0..132.0, waist = 110.0..118.0, hip = 124.0..132.0)
            )
            SizeRegion.EU -> listOf(
                topEntry("XS", chest = 80.0..85.0, waist = 66.0..71.0, hip = 80.0..85.0),
                topEntry("S",  chest = 85.0..90.0, waist = 71.0..76.0, hip = 85.0..90.0),
                topEntry("M",  chest = 90.0..98.0, waist = 76.0..84.0, hip = 90.0..98.0),
                topEntry("L",  chest = 98.0..106.0, waist = 84.0..92.0, hip = 98.0..106.0),
                topEntry("XL", chest = 106.0..114.0, waist = 92.0..100.0, hip = 106.0..114.0),
                topEntry("2XL", chest = 114.0..122.0, waist = 100.0..108.0, hip = 114.0..122.0),
                topEntry("3XL", chest = 122.0..130.0, waist = 108.0..116.0, hip = 122.0..130.0)
            )
            SizeRegion.UK -> listOf(
                topEntry("XS", chest = 82.0..87.0, waist = 68.0..73.0, hip = 82.0..87.0),
                topEntry("S",  chest = 87.0..92.0, waist = 73.0..78.0, hip = 87.0..92.0),
                topEntry("M",  chest = 92.0..100.0, waist = 78.0..86.0, hip = 92.0..100.0),
                topEntry("L",  chest = 100.0..108.0, waist = 86.0..94.0, hip = 100.0..108.0),
                topEntry("XL", chest = 108.0..116.0, waist = 94.0..102.0, hip = 108.0..116.0),
                topEntry("2XL", chest = 116.0..124.0, waist = 102.0..110.0, hip = 116.0..124.0),
                topEntry("3XL", chest = 124.0..132.0, waist = 110.0..118.0, hip = 124.0..132.0)
            )
            SizeRegion.ASIA -> listOf(
                topEntry("XS", chest = 78.0..83.0, waist = 64.0..69.0, hip = 78.0..83.0),
                topEntry("S",  chest = 83.0..88.0, waist = 69.0..74.0, hip = 83.0..88.0),
                topEntry("M",  chest = 88.0..96.0, waist = 74.0..82.0, hip = 88.0..96.0),
                topEntry("L",  chest = 96.0..104.0, waist = 82.0..90.0, hip = 96.0..104.0),
                topEntry("XL", chest = 104.0..112.0, waist = 90.0..98.0, hip = 104.0..112.0),
                topEntry("2XL", chest = 112.0..120.0, waist = 98.0..106.0, hip = 112.0..120.0),
                topEntry("3XL", chest = 120.0..128.0, waist = 106.0..114.0, hip = 120.0..128.0)
            )
            SizeRegion.ID -> listOf(
                topEntry("XS", chest = 76.0..82.0, waist = 62.0..68.0, hip = 76.0..82.0),
                topEntry("S",  chest = 82.0..88.0, waist = 68.0..74.0, hip = 82.0..88.0),
                topEntry("M",  chest = 88.0..95.0, waist = 74.0..81.0, hip = 88.0..95.0),
                topEntry("L",  chest = 95.0..103.0, waist = 81.0..89.0, hip = 95.0..103.0),
                topEntry("XL", chest = 103.0..111.0, waist = 89.0..97.0, hip = 103.0..111.0),
                topEntry("2XL", chest = 111.0..119.0, waist = 97.0..105.0, hip = 111.0..119.0),
                topEntry("3XL", chest = 119.0..127.0, waist = 105.0..113.0, hip = 119.0..127.0)
            )
        }
    }

    private fun getFemaleTopChart(region: SizeRegion): List<SizeChartEntry> {
        return when (region) {
            SizeRegion.US -> listOf(
                topEntry("XS", chest = 76.0..81.0, waist = 58.0..63.0, hip = 82.0..87.0),
                topEntry("S",  chest = 81.0..86.0, waist = 63.0..68.0, hip = 87.0..92.0),
                topEntry("M",  chest = 86.0..94.0, waist = 68.0..76.0, hip = 92.0..100.0),
                topEntry("L",  chest = 94.0..102.0, waist = 76.0..84.0, hip = 100.0..108.0),
                topEntry("XL", chest = 102.0..110.0, waist = 84.0..92.0, hip = 108.0..116.0),
                topEntry("2XL", chest = 110.0..118.0, waist = 92.0..100.0, hip = 116.0..124.0),
                topEntry("3XL", chest = 118.0..126.0, waist = 100.0..108.0, hip = 124.0..132.0)
            )
            SizeRegion.EU -> listOf(
                topEntry("XS", chest = 74.0..79.0, waist = 56.0..61.0, hip = 80.0..85.0),
                topEntry("S",  chest = 79.0..84.0, waist = 61.0..66.0, hip = 85.0..90.0),
                topEntry("M",  chest = 84.0..92.0, waist = 66.0..74.0, hip = 90.0..98.0),
                topEntry("L",  chest = 92.0..100.0, waist = 74.0..82.0, hip = 98.0..106.0),
                topEntry("XL", chest = 100.0..108.0, waist = 82.0..90.0, hip = 106.0..114.0),
                topEntry("2XL", chest = 108.0..116.0, waist = 90.0..98.0, hip = 114.0..122.0),
                topEntry("3XL", chest = 116.0..124.0, waist = 98.0..106.0, hip = 122.0..130.0)
            )
            SizeRegion.UK -> listOf(
                topEntry("XS", chest = 76.0..81.0, waist = 58.0..63.0, hip = 82.0..87.0),
                topEntry("S",  chest = 81.0..86.0, waist = 63.0..68.0, hip = 87.0..92.0),
                topEntry("M",  chest = 86.0..94.0, waist = 68.0..76.0, hip = 92.0..100.0),
                topEntry("L",  chest = 94.0..102.0, waist = 76.0..84.0, hip = 100.0..108.0),
                topEntry("XL", chest = 102.0..110.0, waist = 84.0..92.0, hip = 108.0..116.0),
                topEntry("2XL", chest = 110.0..118.0, waist = 92.0..100.0, hip = 116.0..124.0),
                topEntry("3XL", chest = 118.0..126.0, waist = 100.0..108.0, hip = 124.0..132.0)
            )
            SizeRegion.ASIA -> listOf(
                topEntry("XS", chest = 72.0..77.0, waist = 54.0..59.0, hip = 78.0..83.0),
                topEntry("S",  chest = 77.0..82.0, waist = 59.0..64.0, hip = 83.0..88.0),
                topEntry("M",  chest = 82.0..90.0, waist = 64.0..72.0, hip = 88.0..96.0),
                topEntry("L",  chest = 90.0..98.0, waist = 72.0..80.0, hip = 96.0..104.0),
                topEntry("XL", chest = 98.0..106.0, waist = 80.0..88.0, hip = 104.0..112.0),
                topEntry("2XL", chest = 106.0..114.0, waist = 88.0..96.0, hip = 112.0..120.0),
                topEntry("3XL", chest = 114.0..122.0, waist = 96.0..104.0, hip = 120.0..128.0)
            )
            SizeRegion.ID -> listOf(
                topEntry("XS", chest = 70.0..76.0, waist = 52.0..58.0, hip = 76.0..82.0),
                topEntry("S",  chest = 76.0..82.0, waist = 58.0..64.0, hip = 82.0..88.0),
                topEntry("M",  chest = 82.0..89.0, waist = 64.0..71.0, hip = 88.0..95.0),
                topEntry("L",  chest = 89.0..97.0, waist = 71.0..79.0, hip = 95.0..103.0),
                topEntry("XL", chest = 97.0..105.0, waist = 79.0..87.0, hip = 103.0..111.0),
                topEntry("2XL", chest = 105.0..113.0, waist = 87.0..95.0, hip = 111.0..119.0),
                topEntry("3XL", chest = 113.0..121.0, waist = 95.0..103.0, hip = 119.0..127.0)
            )
        }
    }

    // ─── Bottom Garments (Jeans, Trousers, Chinos, Cargo, Shorts, Joggers) ───

    private fun getBottomChart(region: SizeRegion, gender: Gender): List<SizeChartEntry> {
        return when (gender) {
            Gender.MALE -> getMaleBottomChart(region)
            Gender.FEMALE -> getFemaleBottomChart(region)
        }
    }

    private fun getMaleBottomChart(region: SizeRegion): List<SizeChartEntry> {
        return when (region) {
            SizeRegion.US -> listOf(
                bottomEntry("XS", waist = 68.0..73.0, hip = 82.0..87.0, inseam = 76.0..78.0),
                bottomEntry("S",  waist = 73.0..78.0, hip = 87.0..92.0, inseam = 78.0..80.0),
                bottomEntry("M",  waist = 78.0..86.0, hip = 92.0..100.0, inseam = 80.0..82.0),
                bottomEntry("L",  waist = 86.0..94.0, hip = 100.0..108.0, inseam = 80.0..82.0),
                bottomEntry("XL", waist = 94.0..102.0, hip = 108.0..116.0, inseam = 80.0..82.0),
                bottomEntry("2XL", waist = 102.0..110.0, hip = 116.0..124.0, inseam = 80.0..82.0),
                bottomEntry("3XL", waist = 110.0..118.0, hip = 124.0..132.0, inseam = 80.0..82.0)
            )
            SizeRegion.EU -> listOf(
                bottomEntry("XS", waist = 66.0..71.0, hip = 80.0..85.0, inseam = 76.0..78.0),
                bottomEntry("S",  waist = 71.0..76.0, hip = 85.0..90.0, inseam = 78.0..80.0),
                bottomEntry("M",  waist = 76.0..84.0, hip = 90.0..98.0, inseam = 80.0..82.0),
                bottomEntry("L",  waist = 84.0..92.0, hip = 98.0..106.0, inseam = 80.0..82.0),
                bottomEntry("XL", waist = 92.0..100.0, hip = 106.0..114.0, inseam = 80.0..82.0),
                bottomEntry("2XL", waist = 100.0..108.0, hip = 114.0..122.0, inseam = 80.0..82.0),
                bottomEntry("3XL", waist = 108.0..116.0, hip = 122.0..130.0, inseam = 80.0..82.0)
            )
            SizeRegion.UK -> listOf(
                bottomEntry("XS", waist = 68.0..73.0, hip = 82.0..87.0, inseam = 76.0..78.0),
                bottomEntry("S",  waist = 73.0..78.0, hip = 87.0..92.0, inseam = 78.0..80.0),
                bottomEntry("M",  waist = 78.0..86.0, hip = 92.0..100.0, inseam = 80.0..82.0),
                bottomEntry("L",  waist = 86.0..94.0, hip = 100.0..108.0, inseam = 80.0..82.0),
                bottomEntry("XL", waist = 94.0..102.0, hip = 108.0..116.0, inseam = 80.0..82.0),
                bottomEntry("2XL", waist = 102.0..110.0, hip = 116.0..124.0, inseam = 80.0..82.0),
                bottomEntry("3XL", waist = 110.0..118.0, hip = 124.0..132.0, inseam = 80.0..82.0)
            )
            SizeRegion.ASIA -> listOf(
                bottomEntry("XS", waist = 64.0..69.0, hip = 78.0..83.0, inseam = 74.0..76.0),
                bottomEntry("S",  waist = 69.0..74.0, hip = 83.0..88.0, inseam = 76.0..78.0),
                bottomEntry("M",  waist = 74.0..82.0, hip = 88.0..96.0, inseam = 78.0..80.0),
                bottomEntry("L",  waist = 82.0..90.0, hip = 96.0..104.0, inseam = 78.0..80.0),
                bottomEntry("XL", waist = 90.0..98.0, hip = 104.0..112.0, inseam = 78.0..80.0),
                bottomEntry("2XL", waist = 98.0..106.0, hip = 112.0..120.0, inseam = 78.0..80.0),
                bottomEntry("3XL", waist = 106.0..114.0, hip = 120.0..128.0, inseam = 78.0..80.0)
            )
            SizeRegion.ID -> listOf(
                bottomEntry("XS", waist = 62.0..68.0, hip = 76.0..82.0, inseam = 72.0..74.0),
                bottomEntry("S",  waist = 68.0..74.0, hip = 82.0..88.0, inseam = 74.0..76.0),
                bottomEntry("M",  waist = 74.0..81.0, hip = 88.0..95.0, inseam = 76.0..78.0),
                bottomEntry("L",  waist = 81.0..89.0, hip = 95.0..103.0, inseam = 76.0..78.0),
                bottomEntry("XL", waist = 89.0..97.0, hip = 103.0..111.0, inseam = 76.0..78.0),
                bottomEntry("2XL", waist = 97.0..105.0, hip = 111.0..119.0, inseam = 76.0..78.0),
                bottomEntry("3XL", waist = 105.0..113.0, hip = 119.0..127.0, inseam = 76.0..78.0)
            )
        }
    }

    private fun getFemaleBottomChart(region: SizeRegion): List<SizeChartEntry> {
        return when (region) {
            SizeRegion.US -> listOf(
                bottomEntry("XS", waist = 58.0..63.0, hip = 82.0..87.0, inseam = 74.0..76.0),
                bottomEntry("S",  waist = 63.0..68.0, hip = 87.0..92.0, inseam = 76.0..78.0),
                bottomEntry("M",  waist = 68.0..76.0, hip = 92.0..100.0, inseam = 78.0..80.0),
                bottomEntry("L",  waist = 76.0..84.0, hip = 100.0..108.0, inseam = 78.0..80.0),
                bottomEntry("XL", waist = 84.0..92.0, hip = 108.0..116.0, inseam = 78.0..80.0),
                bottomEntry("2XL", waist = 92.0..100.0, hip = 116.0..124.0, inseam = 78.0..80.0),
                bottomEntry("3XL", waist = 100.0..108.0, hip = 124.0..132.0, inseam = 78.0..80.0)
            )
            SizeRegion.EU -> listOf(
                bottomEntry("XS", waist = 56.0..61.0, hip = 80.0..85.0, inseam = 74.0..76.0),
                bottomEntry("S",  waist = 61.0..66.0, hip = 85.0..90.0, inseam = 76.0..78.0),
                bottomEntry("M",  waist = 66.0..74.0, hip = 90.0..98.0, inseam = 78.0..80.0),
                bottomEntry("L",  waist = 74.0..82.0, hip = 98.0..106.0, inseam = 78.0..80.0),
                bottomEntry("XL", waist = 82.0..90.0, hip = 106.0..114.0, inseam = 78.0..80.0),
                bottomEntry("2XL", waist = 90.0..98.0, hip = 114.0..122.0, inseam = 78.0..80.0),
                bottomEntry("3XL", waist = 98.0..106.0, hip = 122.0..130.0, inseam = 78.0..80.0)
            )
            SizeRegion.UK -> listOf(
                bottomEntry("XS", waist = 58.0..63.0, hip = 82.0..87.0, inseam = 74.0..76.0),
                bottomEntry("S",  waist = 63.0..68.0, hip = 87.0..92.0, inseam = 76.0..78.0),
                bottomEntry("M",  waist = 68.0..76.0, hip = 92.0..100.0, inseam = 78.0..80.0),
                bottomEntry("L",  waist = 76.0..84.0, hip = 100.0..108.0, inseam = 78.0..80.0),
                bottomEntry("XL", waist = 84.0..92.0, hip = 108.0..116.0, inseam = 78.0..80.0),
                bottomEntry("2XL", waist = 92.0..100.0, hip = 116.0..124.0, inseam = 78.0..80.0),
                bottomEntry("3XL", waist = 100.0..108.0, hip = 124.0..132.0, inseam = 78.0..80.0)
            )
            SizeRegion.ASIA -> listOf(
                bottomEntry("XS", waist = 54.0..59.0, hip = 78.0..83.0, inseam = 72.0..74.0),
                bottomEntry("S",  waist = 59.0..64.0, hip = 83.0..88.0, inseam = 74.0..76.0),
                bottomEntry("M",  waist = 64.0..72.0, hip = 88.0..96.0, inseam = 76.0..78.0),
                bottomEntry("L",  waist = 72.0..80.0, hip = 96.0..104.0, inseam = 76.0..78.0),
                bottomEntry("XL", waist = 80.0..88.0, hip = 104.0..112.0, inseam = 76.0..78.0),
                bottomEntry("2XL", waist = 88.0..96.0, hip = 112.0..120.0, inseam = 76.0..78.0),
                bottomEntry("3XL", waist = 96.0..104.0, hip = 120.0..128.0, inseam = 76.0..78.0)
            )
            SizeRegion.ID -> listOf(
                bottomEntry("XS", waist = 52.0..58.0, hip = 76.0..82.0, inseam = 70.0..72.0),
                bottomEntry("S",  waist = 58.0..64.0, hip = 82.0..88.0, inseam = 72.0..74.0),
                bottomEntry("M",  waist = 64.0..71.0, hip = 88.0..95.0, inseam = 74.0..76.0),
                bottomEntry("L",  waist = 71.0..79.0, hip = 95.0..103.0, inseam = 74.0..76.0),
                bottomEntry("XL", waist = 79.0..87.0, hip = 103.0..111.0, inseam = 74.0..76.0),
                bottomEntry("2XL", waist = 87.0..95.0, hip = 111.0..119.0, inseam = 74.0..76.0),
                bottomEntry("3XL", waist = 95.0..103.0, hip = 119.0..127.0, inseam = 74.0..76.0)
            )
        }
    }

    // ─── Helper Functions ───

    private fun topEntry(
        label: String,
        chest: ClosedFloatingPointRange<Double>,
        waist: ClosedFloatingPointRange<Double>,
        hip: ClosedFloatingPointRange<Double>
    ): SizeChartEntry = SizeChartEntry(
        sizeLabel = label,
        chest = MeasurementRange(min = chest.start, max = chest.endInclusive, unit = MeasurementUnit.CM),
        waist = MeasurementRange(min = waist.start, max = waist.endInclusive, unit = MeasurementUnit.CM),
        hip = MeasurementRange(min = hip.start, max = hip.endInclusive, unit = MeasurementUnit.CM)
    )

    private fun bottomEntry(
        label: String,
        waist: ClosedFloatingPointRange<Double>,
        hip: ClosedFloatingPointRange<Double>,
        inseam: ClosedFloatingPointRange<Double>
    ): SizeChartEntry = SizeChartEntry(
        sizeLabel = label,
        waist = MeasurementRange(min = waist.start, max = waist.endInclusive, unit = MeasurementUnit.CM),
        hip = MeasurementRange(min = hip.start, max = hip.endInclusive, unit = MeasurementUnit.CM),
        inseam = MeasurementRange(min = inseam.start, max = inseam.endInclusive, unit = MeasurementUnit.CM)
    )

    /**
     * Extension to determine if a garment type is a top or bottom garment.
     */
    private fun GarmentType.isTopGarment(): Boolean = when (this) {
        GarmentType.TSHIRT, GarmentType.SHIRT, GarmentType.POLO_SHIRT,
        GarmentType.HOODIE, GarmentType.SWEATER, GarmentType.JACKET,
        GarmentType.BLAZER -> true
        GarmentType.JEANS, GarmentType.TROUSERS, GarmentType.CHINOS,
        GarmentType.CARGO_PANTS, GarmentType.SHORTS, GarmentType.JOGGERS -> false
    }
}
