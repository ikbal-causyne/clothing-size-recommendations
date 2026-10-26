package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.domain.model.*

/**
 * Regional Size Chart Interpreter - PRD Section 6
 * 
 * ✅ ENGINE VISION FIX: Region affects SIZE CHART INTERPRETATION, not BODY ESTIMATION
 * 
 * CRITICAL BUG FIXED:
 * OLD: BodyEstimator multiplied by region → User body changed per region (WRONG)
 *      RegionalFitNormalizer divided by region → Chart changed per region (WRONG)
 *      Result: Non-commutative, unpredictable recommendations
 * 
 * NEW: BodyEstimator returns GLOBAL body (no region factor)
 *      RegionalFitNormalizer interprets size charts with region context
 *      Result: Same user → consistent primary size + regional guidance
 * 
 * HOW REGION WORKS NOW:
 * 1. User body is GLOBAL (170cm/65kg male always ~88cm chest)
 * 2. Size charts are REGIONAL (different garment grading per region)
 * 3. Region coefficients inform INTERPRETATION:
 *    - US M typically runs 3% larger
 *    - Indonesia M typically runs 4% smaller
 *    - Use this to guide between-size decisions and fit expectations
 * 4. NO chart modification - only add confidence/tolerance based on region
 */
object RegionalFitNormalizer {
    
    /**
     * Regional fit context based on market research and anthropometric studies (2004-2024)
     * ✅ FIX: These coefficients are NOW METADATA ONLY - they inform tolerance and UX,
     *         but do NOT modify body or chart
     */
    private val regionContext = mapOf(
        SizeRegion.US to RegionalContext(
            label = "US (Relaxed/Loose)",
            garmentSizeMultiplier = 1.03,  // US garments typically 3% larger
            fitStyle = "Relaxed/Loose",
            toleranceFactor = 1.15,  // More tolerance for fit variation
            betweenSizeGuidance = "Size up if between sizes (Americans prefer roomier fit)"
        ),
        SizeRegion.EU to RegionalContext(
            label = "EU (Standard)",
            garmentSizeMultiplier = 1.00,
            fitStyle = "Standard",
            toleranceFactor = 1.0,
            betweenSizeGuidance = "Follow standard recommendations"
        ),
        SizeRegion.UK to RegionalContext(
            label = "UK (Standard-Relaxed)",
            garmentSizeMultiplier = 1.01,
            fitStyle = "Standard-Relaxed",
            toleranceFactor = 1.08,
            betweenSizeGuidance = "Slightly more room than EU"
        ),
        SizeRegion.ASIA to RegionalContext(
            label = "ASIA (Slim/Fitted)",
            garmentSizeMultiplier = 0.97,  // Asian garments typically 3% smaller
            fitStyle = "Slim/Fitted",
            toleranceFactor = 0.90,  // Tighter tolerance for fit
            betweenSizeGuidance = "Size up if prefer comfort (Asian cuts run slim)"
        ),
        SizeRegion.ID to RegionalContext(
            label = "INDONESIA (Slim)",
            garmentSizeMultiplier = 0.96,  // Indonesian garments typically 4% smaller
            fitStyle = "Slim",
            toleranceFactor = 0.85,  // Tighter tolerance
            betweenSizeGuidance = "Size up for comfort (Indonesian cuts run slim)"
        )
    )
    
    /**
     * Get regional context for UX and tolerance guidance
     * ✅ FIX: Returns metadata only - does NOT modify body or chart
     */
    fun getRegionalContext(userRegion: SizeRegion): RegionalContext {
        return regionContext[userRegion] ?: regionContext[SizeRegion.EU]!!
    }
    
    /**
     * Calculate tolerance factor based on region and garment type
     * ✅ FIX: Region affects HOW MUCH TOLERANCE we allow, not the measurements themselves
     * 
     * This is used by FitScoringEngine to adjust Gaussian decay width
     * Example:
     * - User chest: 88cm (GLOBAL, same everywhere)
     * - US fit curve: wider tolerance (1.15x) → more accepting of slight mismatch
     * - Indonesia fit curve: narrower tolerance (0.85x) → strict fit requirements
     */
    fun getToleranceFactor(userRegion: SizeRegion, garmentType: GarmentType): Double {
        val regionTolerance = regionContext[userRegion]?.toleranceFactor ?: 1.0
        
        // Garment-specific tolerance adjustments (independent of region)
        val garmentTolerance = when (garmentType) {
            GarmentType.TSHIRT -> 1.2    // T-shirts have more natural variation
            GarmentType.JEANS -> 0.8     // Jeans need tighter fit
            GarmentType.HOODIE -> 1.3    // Hoodies worn loose
            else -> 1.0
        }
        
        return regionTolerance * garmentTolerance
    }
    
    /**
     * ✅ FIX: NO chart modification anymore!
     * Return size chart UNCHANGED - use it as ground truth
     * Region effects are applied in FitScoringEngine via tolerance factors
     */
    fun getRegionalSizeChart(
        sizeChart: List<SizeChartEntry>,
        userRegion: SizeRegion
    ): List<SizeChartEntry> {
        android.util.Log.d("RegionalFitNormalizer", "✅ [ENGINE VISION FIX] Returning SIZE CHART UNMODIFIED for region: $userRegion")
        android.util.Log.d("RegionalFitNormalizer", "   Region ONLY affects: tolerance factors, between-size guidance, UX messaging")
        
        // ✅ NO chart modification - return as-is
        // Regional interpretation happens in FitScoringEngine via tolerance factors
        return sizeChart
    }
    
    /**
     * Get between-size guidance based on region
     * ✅ FIX: This is METADATA for UX - inform user about regional fit expectations
     */
    fun getBetweenSizeGuidance(userRegion: SizeRegion): String {
        return regionContext[userRegion]?.betweenSizeGuidance
            ?: "Use your fit preference to decide between sizes"
    }
    
    /**
     * Kept for backward compatibility - delegates to new logic
     * ✅ FIX: Now a no-op (returns unchanged measurement)
     * 
     * DEPRECATED: This was applying regionFactors incorrectly
     * Use getRegionalSizeChart() instead
     */
    @Deprecated("Use getRegionalSizeChart() instead. This no longer modifies measurements.")
    fun normalizeGarmentMeasurements(
        garmentMeasurements: SizeChartEntry,
        userRegion: SizeRegion
    ): SizeChartEntry {
        android.util.Log.w("RegionalFitNormalizer", "⚠️ DEPRECATED: normalizeGarmentMeasurements() - charts are no longer modified per region")
        // Return unchanged - region effects are now applied via tolerance factors
        return garmentMeasurements
    }
    
    /**
     * Kept for backward compatibility
     * ✅ FIX: Now a no-op (returns unchanged chart)
     * 
     * DEPRECATED: Use getRegionalSizeChart() instead
     */
    @Deprecated("Use getRegionalSizeChart() instead")
    fun normalizeSizeChart(
        sizeChart: List<SizeChartEntry>,
        userRegion: SizeRegion
    ): List<SizeChartEntry> {
        android.util.Log.w("RegionalFitNormalizer", "⚠️ DEPRECATED: normalizeSizeChart() - charts are no longer modified per region")
        return sizeChart
    }
}

/**
 * ✅ FIX: New data class for regional context (metadata only)
 */
data class RegionalContext(
    val label: String,
    val garmentSizeMultiplier: Double,  // For reference/logging only
    val fitStyle: String,
    val toleranceFactor: Double,  // Applied in FitScoringEngine
    val betweenSizeGuidance: String  // For UX messaging
)

