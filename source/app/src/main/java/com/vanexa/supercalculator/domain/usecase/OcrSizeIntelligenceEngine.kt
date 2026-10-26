package com.vanexa.supercalculator.domain.usecase

import com.vanexa.supercalculator.data.network.VanexaAiApiService

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vanexa.supercalculator.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * OCR Size Intelligence Engine - OCR_ENGINE_MASTER_PROMPT.md Implementation
 * 
 * This is NOT a generic OCR. This is a reasoning system on top of OCR output.
 * 
 * Core Philosophy:
 * 1. OCR is unreliable by default - OCR output is noisy input, NOT truth
 * 2. Sizes are NOT labels - measurements (cm/inch) are the only ground truth
 * 3. Never assume intent - context decides meaning, NOT position alone
 * 4. Everything has confidence - OCR, parsing, structural, semantic, final
 * 
 * 8-Stage Pipeline:
 * 1. Raw OCR Extraction (preserve spatial relationships)
 * 2. Token Normalization (OCR error correction)
 * 3. Structural Detection (table structure inference)
 * 4. Size Label Intelligence (context-aware classification)
 * 5. Measurement Extraction (with confidence)
 * 6. Size Count Validation (anti-hallucination)
 * 7. Region & Gender Context Injection
 * 8. Confidence Collapse (compounded confidence)
 */
class OcrSizeIntelligenceEngine {
    companion object {
        private const val TAG = "OcrSizeIntelligence"
        private const val MIN_OCR_CONFIDENCE = 0.25  // ✅ FIX: Lower threshold to reduce false rejections (was 0.35)
    }
    
    // ✅ BUG #53 FIX: Lazy initialization and proper cleanup for TextRecognizer
    private var textRecognizer: com.google.mlkit.vision.text.TextRecognizer? = null
    private val lock = Any()
    
    private fun getTextRecognizer(): com.google.mlkit.vision.text.TextRecognizer {
        synchronized(lock) {
            if (textRecognizer == null) {
                textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                Log.d(TAG, "✅ [BUG #53] TextRecognizer created (lazy initialization)")
            }
            return textRecognizer!!
        }
    }
    
    /**
     * Clean up resources - must be called to prevent memory leak
     * ✅ BUG #53: Properly close TextRecognizer to prevent resource leak
     */
    fun close() {
        synchronized(lock) {
            textRecognizer?.let {
                it.close()
                textRecognizer = null
                Log.d(TAG, "✅ [BUG #53] OCR resources closed - TextRecognizer released")
            } ?: run {
                Log.d(TAG, "✅ [BUG #53] TextRecognizer already closed or never initialized")
            }
        }
    }
    
    /**
     * Main entry point: Process image through 8-stage pipeline
     * ✅ FIX: Comprehensive error handling to prevent crashes
     */
    suspend fun processImage(
        bitmap: Bitmap,
        userRegion: SizeRegion,
        userGender: Gender,
        apiService: VanexaAiApiService? = null,
        tier: String? = null,
        deviceHash: String? = null,
        purchaseToken: String? = null,
        sessionToken: String? = null,
        productId: String? = null
    ): ParsedSizeChart = withContext(Dispatchers.Default) {
        
        val processingStages = mutableListOf<ProcessingStage>()
        
        try {
            Log.d(TAG, "🔍 [OCR Engine] Starting 8-stage pipeline")
            Log.d(TAG, "📊 [OCR Engine] Image: ${bitmap.width}x${bitmap.height}, Region: $userRegion, Gender: $userGender")
            
            // STAGE 1: Raw OCR Extraction
            Log.d(TAG, "📋 [Stage 1] Raw OCR Extraction, bitmap=${bitmap.width}x${bitmap.height}, config=${bitmap.config}")
            val rawTokens = try {
                extractRawOcrTokens(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Stage 1] Error in OCR extraction: ${e.message}", e)
                return@withContext createErrorResult("OCR extraction failed: ${e.message}", processingStages)
            }
            processingStages.add(ProcessingStage(
                stage = 1,
                name = "Raw OCR Extraction",
                confidence = try { rawTokens.map { it.confidence }.average() } catch (e: Exception) { 0.5 },
                tokenCount = rawTokens.size,
                issues = if (rawTokens.isEmpty()) listOf("No text detected in image") else emptyList()
            ))
            Log.d(TAG, "✅ [Stage 1] Extracted ${rawTokens.size} raw tokens")

            // STAGE 2: AI-Assisted Parsing (Priority Stage)
            // If AI is available and successful, we bypass legacy stages 2-6
            Log.d(TAG, "🤖 [AI Stage] Starting AI-assisted parsing")
            val aiEntries = if (apiService != null && tier != null && deviceHash != null) {
                try {
                    val ocrTextForAi = rawTokens.joinToString("\n") { it.text }
                    apiService.parseSizeChartWithAi(tier, deviceHash, purchaseToken, sessionToken, productId, ocrTextForAi)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ [AI Stage] AI Parsing failed, falling back to legacy: ${e.message}")
                    null
                }
            } else {
                null
            }

            if (aiEntries != null && aiEntries.isNotEmpty()) {
                Log.d(TAG, "✅ [AI Stage] AI Parsing successful, returning structured AI result")
                processingStages.add(ProcessingStage(
                    stage = 2,
                    name = "AI Assisted Parsing",
                    confidence = 0.95,
                    tokenCount = aiEntries.size,
                    issues = emptyList()
                ))
                
                return@withContext ParsedSizeChart(
                    sizes = aiEntries,
                    overallConfidence = 0.95,
                    warnings = emptyList(),
                    ocrMetadata = OcrMetadata(
                        rawTokenCount = rawTokens.size,
                        normalizedTokenCount = rawTokens.size,
                        detectedRows = 0,
                        detectedColumns = 0,
                        sizeLabelCount = aiEntries.size,
                        measurementCount = aiEntries.size * 3, // rough estimate of extracted fields
                        structuralConfidence = 0.95,
                        semanticConfidence = 0.95,
                        regionalConsistency = 1.0,
                        processingStages = processingStages
                    )
                )
            }
            
            if (rawTokens.isEmpty()) {
                return@withContext ParsedSizeChart(
                sizes = emptyList(),
                overallConfidence = 0.0,
                warnings = emptyList(), // Silenced user-facing warning: "No text found in image"
                ocrMetadata = OcrMetadata(
                    rawTokenCount = 0,
                    normalizedTokenCount = 0,
                    detectedRows = 0,
                    detectedColumns = 0,
                    sizeLabelCount = 0,
                    measurementCount = 0,
                    structuralConfidence = 0.0,
                    semanticConfidence = 0.0,
                    regionalConsistency = 0.0,
                    processingStages = processingStages
                )
            )
        }
        
            // STAGE 2: Token Normalization
            Log.d(TAG, "🔧 [Stage 2] Token Normalization")
            val normalizedTokens = try {
                normalizeTokens(rawTokens)
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Stage 2] Error in token normalization: ${e.message}", e)
                // Continue with raw tokens if normalization fails
                rawTokens
            }
            processingStages.add(ProcessingStage(
                stage = 2,
                name = "Token Normalization",
                confidence = try { normalizedTokens.map { it.confidence }.average() } catch (e: Exception) { 0.5 },
                tokenCount = normalizedTokens.size,
                issues = if (normalizedTokens.size < rawTokens.size) 
                    listOf("${rawTokens.size - normalizedTokens.size} tokens rejected due to low confidence") 
                else emptyList()
            ))
            Log.d(TAG, "✅ [Stage 2] Normalized ${normalizedTokens.size} tokens (rejected ${rawTokens.size - normalizedTokens.size})")
        
            // STAGE 3: Structural Detection
            Log.d(TAG, "📊 [Stage 3] Structural Detection")
            val tableCells = try {
                detectTableStructure(normalizedTokens)
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Stage 3] Error in structural detection: ${e.message}", e)
                // Create cells from tokens as fallback
                normalizedTokens.mapIndexed { index, token ->
                    OcrTableCell(
                        row = index / 10,  // Simple row assignment
                        column = index % 10,  // Simple column assignment
                        token = token,
                        role = CellRole.UNKNOWN
                    )
                }
            }
            val structuralConfidence = try {
                calculateStructuralConfidence(tableCells)
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Stage 3] Error calculating structural confidence: ${e.message}", e)
                0.5  // Default moderate confidence
            }
            processingStages.add(ProcessingStage(
                stage = 3,
                name = "Structural Detection",
                confidence = structuralConfidence,
                tokenCount = tableCells.size,
                issues = if (tableCells.isEmpty()) listOf("Could not detect table structure") else emptyList()
            ))
            Log.d(TAG, "✅ [Stage 3] Detected ${tableCells.size} table cells, ${try { tableCells.groupBy { it.row }.size } catch (e: Exception) { 0 }} rows, ${try { tableCells.groupBy { it.column }.size } catch (e: Exception) { 0 }} columns")
        
            // STAGE 4: Size Label Intelligence
            Log.d(TAG, "🏷️ [Stage 4] Size Label Intelligence")
            val sizeLabelCells = try {
                classifySizeLabels(tableCells)
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Stage 4] Error in size label classification: ${e.message}", e)
                // Fallback: find cells that match size patterns
                tableCells.filter { matchesSizeLabelPattern(it.token.text) }
            }
            val semanticConfidence = try {
                calculateSemanticConfidence(sizeLabelCells, tableCells)
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Stage 4] Error calculating semantic confidence: ${e.message}", e)
                0.5  // Default moderate confidence
            }
            processingStages.add(ProcessingStage(
                stage = 4,
                name = "Size Label Intelligence",
                confidence = semanticConfidence,
                tokenCount = sizeLabelCells.size,
                issues = if (sizeLabelCells.isEmpty()) listOf("No size labels detected") else emptyList()
            ))
            Log.d(TAG, "✅ [Stage 4] Classified ${sizeLabelCells.size} size labels")
        
            // STAGE 5: Measurement Extraction
            Log.d(TAG, "📏 [Stage 5] Measurement Extraction")
            val measurements = try {
                extractMeasurements(tableCells, sizeLabelCells)
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Stage 5] Error in measurement extraction: ${e.message}", e)
                emptyMap()  // Continue with empty measurements
            }
            processingStages.add(ProcessingStage(
                stage = 5,
                name = "Measurement Extraction",
                confidence = try { measurements.values.map { it.confidence }.average() } catch (e: Exception) { 0.5 },
                tokenCount = measurements.size,
                issues = if (measurements.isEmpty()) listOf("No measurements extracted") else emptyList()
            ))
            Log.d(TAG, "✅ [Stage 5] Extracted ${measurements.size} measurements")
        
            // STAGE 6: Size Count Validation
            Log.d(TAG, "✅ [Stage 6] Size Count Validation")
            val validatedSizes = try {
                validateSizeCount(sizeLabelCells, measurements)
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Stage 6] Error in size count validation: ${e.message}", e)
                sizeLabelCells  // Use all size labels if validation fails
            }
            processingStages.add(ProcessingStage(
                stage = 6,
                name = "Size Count Validation",
                confidence = if (validatedSizes.size == sizeLabelCells.size) 1.0 else 0.7,
                tokenCount = validatedSizes.size,
                issues = if (validatedSizes.size != sizeLabelCells.size) 
                    listOf("Size count mismatch: detected ${sizeLabelCells.size} labels but validated ${validatedSizes.size}") 
                else emptyList()
            ))
            Log.d(TAG, "✅ [Stage 6] Validated ${validatedSizes.size} sizes")
        
            // STAGE 7: Region & Gender Context Injection
            Log.d(TAG, "🌍 [Stage 7] Region & Gender Context Injection")
            val contextualizedSizes = try {
                injectContext(validatedSizes, measurements, tableCells, userRegion, userGender)
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Stage 7] Error in context injection: ${e.message}", e)
                // Fallback: create basic size entries
                validatedSizes.map { sizeCell ->
                    SizeChartEntry(
                        sizeLabel = SizeLabelNormalizer.normalize(sizeCell.token.text),
                        chest = null,
                        waist = null,
                        hip = null,
                        shoulder = null,
                        length = null,
                        inseam = null,
                        sleeve = null
                    )
                }
            }
            val regionalConsistency = try {
                calculateRegionalConsistency(contextualizedSizes, userRegion)
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Stage 7] Error calculating regional consistency: ${e.message}", e)
                0.8  // Default moderate consistency
            }
            processingStages.add(ProcessingStage(
                stage = 7,
                name = "Region & Gender Context",
                confidence = regionalConsistency,
                tokenCount = contextualizedSizes.size,
                issues = if (regionalConsistency < 0.7) listOf("Regional sizing inconsistency detected") else emptyList()
            ))
            Log.d(TAG, "✅ [Stage 7] Contextualized ${contextualizedSizes.size} sizes")
        
            // STAGE 8: Confidence Collapse & Final Output
            Log.d(TAG, "🎯 [Stage 8] Confidence Collapse")
            val ocrConfidence = try {
                rawTokens.map { it.confidence }.average()
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Stage 8] Error calculating OCR confidence: ${e.message}", e)
                0.5  // Default moderate confidence
            }
            
            val finalConfidence = try {
                collapseConfidence(
                    ocrConfidence = ocrConfidence,
                    structuralConfidence = structuralConfidence,
                    semanticConfidence = semanticConfidence,
                    regionalConsistency = regionalConsistency
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Stage 8] Error in confidence collapse: ${e.message}", e)
                0.5  // Default moderate confidence
            }
            
            val warnings = try {
                generateWarnings(processingStages, finalConfidence)
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Stage 8] Error generating warnings: ${e.message}", e)
                listOf("Error generating warnings: ${e.message}")
            }
            
            processingStages.add(ProcessingStage(
                stage = 8,
                name = "Confidence Collapse",
                confidence = finalConfidence,
                tokenCount = contextualizedSizes.size,
                issues = warnings
            ))
            Log.d(TAG, "✅ [Stage 8] Final confidence: $finalConfidence, Warnings: ${warnings.size}")
            
            val ocrMetadata = try {
                OcrMetadata(
                    rawTokenCount = rawTokens.size,
                    normalizedTokenCount = normalizedTokens.size,
                    detectedRows = tableCells.groupBy { it.row }.size,
                    detectedColumns = tableCells.groupBy { it.column }.size,
                    sizeLabelCount = sizeLabelCells.size,
                    measurementCount = measurements.size,
                    structuralConfidence = structuralConfidence,
                    semanticConfidence = semanticConfidence,
                    regionalConsistency = regionalConsistency,
                    processingStages = processingStages
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Stage 8] Error creating metadata: ${e.message}", e)
                OcrMetadata(
                    rawTokenCount = rawTokens.size,
                    normalizedTokenCount = normalizedTokens.size,
                    detectedRows = 0,
                    detectedColumns = 0,
                    sizeLabelCount = sizeLabelCells.size,
                    measurementCount = measurements.size,
                    structuralConfidence = structuralConfidence,
                    semanticConfidence = semanticConfidence,
                    regionalConsistency = regionalConsistency,
                    processingStages = processingStages
                )
            }
            
            return@withContext ParsedSizeChart(
                sizes = contextualizedSizes,
                overallConfidence = finalConfidence,
                warnings = warnings,
                ocrMetadata = ocrMetadata
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ [OCR Engine] Fatal error in pipeline: ${e.message}", e)
            return@withContext createErrorResult("Fatal error: ${e.message}", processingStages)
        }
    }
    
    /**
     * ✅ FIX: Helper to create error result when pipeline fails
     */
    private fun createErrorResult(
        errorMessage: String,
        processingStages: MutableList<ProcessingStage>
    ): ParsedSizeChart {
        processingStages.add(ProcessingStage(
            stage = 0,
            name = "Error",
            confidence = 0.0,
            tokenCount = 0,
            issues = listOf(errorMessage)
        ))
        
        return ParsedSizeChart(
            sizes = emptyList(),
            overallConfidence = 0.0,
            warnings = listOf(errorMessage),
            ocrMetadata = OcrMetadata(
                rawTokenCount = 0,
                normalizedTokenCount = 0,
                detectedRows = 0,
                detectedColumns = 0,
                sizeLabelCount = 0,
                measurementCount = 0,
                structuralConfidence = 0.0,
                semanticConfidence = 0.0,
                regionalConsistency = 0.0,
                processingStages = processingStages
            )
        )
    }
    
    /**
     * STAGE 1: Raw OCR Extraction
     * Extract text blocks with spatial relationships from ML Kit
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun extractRawOcrTokens(bitmap: Bitmap): List<RawOcrToken> = withContext(Dispatchers.IO) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        
        return@withContext suspendCancellableCoroutine { continuation ->
            getTextRecognizer().process(inputImage)
                .addOnSuccessListener { visionText ->
                    val tokens = mutableListOf<RawOcrToken>()
                    
                    visionText.textBlocks.forEach { block ->
                        block.lines.forEach { line ->
                            line.elements.forEach elementLoop@{ element ->
                                val bbox = element.boundingBox ?: return@elementLoop
                                val text = element.text.trim()
                                if (text.isNotBlank()) {
                                    tokens.add(RawOcrToken(
                                        text = text,
                                        bbox = bbox,
                                        confidence = 0.8  // ML Kit doesn't provide per-element confidence, use default
                                    ))
                                }
                            }
                        }
                    }
                    
                    continuation.resume(tokens) {}
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ [Stage 1] OCR extraction failed: ${e.message}", e)
                    continuation.resume(emptyList()) {}
                }
        }
    }
    
    /**
     * STAGE 2: Token Normalization
     * Normalize OCR noise BEFORE reasoning
     */
    private fun normalizeTokens(tokens: List<RawOcrToken>): List<RawOcrToken> {
        return tokens.mapNotNull { token ->
            // Normalize common OCR errors
            var normalizedText = token.text
                .replace(Regex("[Oo]"), "0")  // O → 0
                .replace(Regex("[Il|]"), "1")  // I, l, | → 1
                .replace(Regex("B"), "8")      // B → 8 (in numbers)
                .replace(Regex("S"), "5")      // S → 5 (in numbers)
                .replace(Regex("G"), "6")      // G → 6
            
            // Normalize separators
            normalizedText = normalizedText
                .replace(Regex("[-~–]"), "-")  // Normalize range separators
            
            // Normalize units
            normalizedText = normalizedText
                .replace(Regex("[\"']", RegexOption.IGNORE_CASE), "inch")
                .replace(Regex("inch", RegexOption.IGNORE_CASE), "inch")
                .replace(Regex("cm|centimeter", RegexOption.IGNORE_CASE), "cm")
            
            // Reject tokens with low confidence
            if (token.confidence < MIN_OCR_CONFIDENCE) {
                Log.d(TAG, "⚠️ [Normalization] Rejected token '$normalizedText' (confidence: ${token.confidence})")
                return@mapNotNull null
            }
            
            // Reject isolated numbers without context (will be validated in Stage 4)
            if (normalizedText.matches(Regex("^\\d+$")) && normalizedText.length < 2) {
                // Single digit numbers are suspicious without context
                Log.d(TAG, "⚠️ [Normalization] Rejected isolated single digit: '$normalizedText'")
                return@mapNotNull null
            }
            
            token.copy(text = normalizedText)
        }
    }
    
    /**
     * STAGE 3: Structural Detection
     * Infer table structure from spatial relationships
     */
    private fun detectTableStructure(tokens: List<RawOcrToken>): List<OcrTableCell> {
        if (tokens.isEmpty()) return emptyList()
        
        // Cluster tokens by Y position (rows) - convert Int to Float
        val yPositions = tokens.map { it.bbox.centerY().toFloat() }.distinct().sorted()
        val rowThreshold = calculateRowThreshold(tokens)
        
        // Cluster tokens by X position (columns) - convert Int to Float
        val xPositions = tokens.map { it.bbox.centerX().toFloat() }.distinct().sorted()
        val columnThreshold = calculateColumnThreshold(tokens)
        
        val cells = mutableListOf<OcrTableCell>()
        
        tokens.forEach { token ->
            val row = findRowIndex(token.bbox.centerY().toFloat(), yPositions, rowThreshold)
            val column = findColumnIndex(token.bbox.centerX().toFloat(), xPositions, columnThreshold)
            
            // Initial role classification (will be refined in Stage 4)
            val role = classifyCellRole(token.text, row, column)
            
            cells.add(OcrTableCell(
                row = row,
                column = column,
                token = token,
                role = role
            ))
        }
        
        return cells
    }
    
    private fun calculateRowThreshold(tokens: List<RawOcrToken>): Float {
        if (tokens.size < 2) return 50f
        val yPositions = tokens.map { it.bbox.centerY().toFloat() }.sorted()
        val gaps = yPositions.zipWithNext().map { (a, b) -> abs(a - b) }
        return gaps.filter { it > 10f }.average().toFloat() * 0.5f
    }
    
    private fun calculateColumnThreshold(tokens: List<RawOcrToken>): Float {
        if (tokens.size < 2) return 50f
        val xPositions = tokens.map { it.bbox.centerX().toFloat() }.sorted()
        val gaps = xPositions.zipWithNext().map { (a, b) -> abs(a - b) }
        return gaps.filter { it > 10f }.average().toFloat() * 0.5f
    }
    
    private fun findRowIndex(y: Float, yPositions: List<Float>, threshold: Float): Int {
        return yPositions.indexOfFirst { abs(it - y) <= threshold }.takeIf { it >= 0 } 
            ?: yPositions.size
    }
    
    private fun findColumnIndex(x: Float, xPositions: List<Float>, threshold: Float): Int {
        return xPositions.indexOfFirst { abs(it - x) <= threshold }.takeIf { it >= 0 } 
            ?: xPositions.size
    }
    
    private fun classifyCellRole(text: String, row: Int, column: Int): CellRole {
        val upper = text.uppercase()
        
        // Dimension labels (usually in first row or first column)
        if (row == 0 || column == 0) {
            val dimensionKeywords = listOf("CHEST", "WAIST", "HIP", "SHOULDER", "LENGTH", "INSEAM", "SLEEVE",
                "DADA", "PINGGANG", "PINGGUL", "BAHU", "PANJANG", "LENGAN")
            if (dimensionKeywords.any { upper.contains(it) }) {
                return CellRole.DIMENSION_LABEL
            }
        }
        
        // Size labels (usually in first row or first column)
        if (row == 0 || column == 0) {
            if (matchesSizeLabelPattern(text)) {
                return CellRole.SIZE_LABEL
            }
        }
        
        // Measurement values (numeric ranges or single numbers)
        if (matchesMeasurementPattern(text)) {
            return CellRole.MEASUREMENT_VALUE
        }
        
        return CellRole.UNKNOWN
    }
    
    private fun matchesSizeLabelPattern(text: String): Boolean {
        val upper = text.uppercase().trim()
        // Alphabetic sizes
        if (upper.matches(Regex("^[X]*[SML]$|^[X]+L$"))) return true
        // Numeric sizes (2-3 digits)
        if (upper.matches(Regex("^\\d{2,3}$"))) return true
        // Regional sizes
        if (upper.matches(Regex("^(US|EU|UK|ASIA|ID)\\s*\\d+$"))) return true
        return false
    }
    
    private fun matchesMeasurementPattern(text: String): Boolean {
        // Range pattern: "96-100", "96~100", "96–100"
        if (text.matches(Regex("""\d{2,3}\s*[-~–]\s*\d{2,3}"""))) return true
        // Single value with unit: "96cm", "38inch"
        if (text.matches(Regex("""\d{2,3}\s*(cm|inch|"|')"""))) return true
        // Single value: "96" (will be validated in Stage 5)
        if (text.matches(Regex("""^\d{2,3}$"""))) return true
        return false
    }
    
    /**
     * STAGE 4: Size Label Intelligence
     * Context-aware size label classification
     */
    private fun classifySizeLabels(cells: List<OcrTableCell>): List<OcrTableCell> {
        return cells.filter { cell ->
            when (cell.role) {
                CellRole.SIZE_LABEL -> {
                    // Validate it's actually a size label (not a measurement)
                    val text = cell.token.text.uppercase()
                    val isInHeaderPosition = cell.row == 0 || cell.column == 0
                    val isNotMeasurement = !text.contains("CM") && !text.contains("INCH")
                    val matchesSizePattern = matchesSizeLabelPattern(text)
                    
                    isInHeaderPosition && isNotMeasurement && matchesSizePattern
                }
                else -> false
            }
        }
    }
    
    /**
     * STAGE 5: Measurement Extraction
     * Extract measurements with confidence scoring
     * ✅ FIX: Improved extraction with fallback strategies
     */
    private fun extractMeasurements(
        cells: List<OcrTableCell>,
        sizeLabelCells: List<OcrTableCell>
    ): Map<String, MeasurementRangeWithConfidence> {
        val measurements = mutableMapOf<String, MeasurementRangeWithConfidence>()
        
        // Group cells by row to find measurements associated with size labels
        val cellsByRow = cells.groupBy { it.row }
        
        sizeLabelCells.forEach { sizeCell ->
            val row = sizeCell.row
            val rowCells = cellsByRow[row] ?: return@forEach
            
            // Strategy 1: Find measurement cells in the same row (explicit MEASUREMENT_VALUE role)
            rowCells.filter { it.role == CellRole.MEASUREMENT_VALUE }.forEach { measurementCell ->
                val parsed = parseMeasurement(measurementCell.token.text, measurementCell.token.confidence)
                if (parsed != null) {
                    val key = "${sizeCell.token.text}_${measurementCell.column}"
                    measurements[key] = parsed
                    Log.d(TAG, "✅ [Stage 5] Extracted measurement: $key = ${measurementCell.token.text}")
                }
            }
            
            // Strategy 2: ✅ FIX: Also check UNKNOWN cells that might be measurements (if no explicit measurements found)
            if (rowCells.none { it.role == CellRole.MEASUREMENT_VALUE }) {
                Log.d(TAG, "⚠️ [Stage 5] No explicit measurements in row, checking UNKNOWN cells")
                rowCells.filter { it.role == CellRole.UNKNOWN }.forEach { unknownCell ->
                    val parsed = parseMeasurement(unknownCell.token.text, unknownCell.token.confidence)
                    if (parsed != null) {
                        val key = "${sizeCell.token.text}_${unknownCell.column}"
                        measurements[key] = parsed
                        Log.d(TAG, "✅ [Stage 5] Extracted from UNKNOWN cell: $key = ${unknownCell.token.text}")
                    }
                }
            }
        }
        
        Log.d(TAG, "📊 [Stage 5] Total measurements extracted: ${measurements.size}")
        return measurements
    }
    
    private fun parseMeasurement(text: String, ocrConfidence: Double): MeasurementRangeWithConfidence? {
        // Pattern 1: Range "96-100", "96~100"
        val rangePattern = Regex("""(\d{2,3})\s*[-~–]\s*(\d{2,3})""")
        rangePattern.find(text)?.let { match ->
            val min = match.groupValues[1].toDouble()
            val max = match.groupValues[2].toDouble()
            val unit = detectUnit(text)
            val parsingConfidence = if (min < max && max - min < 20) 0.9 else 0.6
            
            return MeasurementRangeWithConfidence(
                min = min,
                max = max,
                unit = unit,
                confidence = ocrConfidence * parsingConfidence,
                ocrConfidence = ocrConfidence,
                parsingConfidence = parsingConfidence
            )
        }
        
        // Pattern 2: Single value "96cm", "38inch"
        val singlePattern = Regex("""(\d{2,3})\s*(cm|inch|"|')""")
        singlePattern.find(text)?.let { match ->
            val value = match.groupValues[1].toDouble()
            val unit = if (match.groupValues[2].contains("inch", ignoreCase = true) || 
                          match.groupValues[2] == "\"" || match.groupValues[2] == "'") {
                MeasurementUnit.INCH
            } else {
                MeasurementUnit.CM
            }
            
            return MeasurementRangeWithConfidence(
                min = value - 2,
                max = value + 2,
                unit = unit,
                confidence = ocrConfidence * 0.8,
                ocrConfidence = ocrConfidence,
                parsingConfidence = 0.8
            )
        }
        
        // Pattern 3: Single number (assume cm, lower confidence)
        // ✅ FIX: More lenient range for single numbers
        val numberPattern = Regex("""^(\d{1,3})$""")
        numberPattern.find(text)?.let { match ->
            val value = match.groupValues[1].toDouble()
            // ✅ FIX: Expanded range to include smaller measurements (e.g., 26, 28 for waist)
            if (value in 15.0..250.0) {  // Reasonable measurement range (expanded from 20-200)
                return MeasurementRangeWithConfidence(
                    min = value - 2,
                    max = value + 2,
                    unit = MeasurementUnit.CM,
                    confidence = ocrConfidence * 0.6,  // ✅ FIX: Slightly higher confidence (was 0.5)
                    ocrConfidence = ocrConfidence,
                    parsingConfidence = 0.6  // ✅ FIX: Increased from 0.5
                )
            }
        }
        
        return null
    }
    
    private fun detectUnit(text: String): MeasurementUnit {
        val upper = text.uppercase()
        return when {
            upper.contains("INCH") || upper.contains("\"") || upper.contains("'") -> MeasurementUnit.INCH
            else -> MeasurementUnit.CM
        }
    }
    
    /**
     * STAGE 6: Size Count Validation
     * Anti-hallucination: ensure detected sizes match measurements
     */
    private fun validateSizeCount(
        sizeLabels: List<OcrTableCell>,
        measurements: Map<String, MeasurementRangeWithConfidence>
    ): List<OcrTableCell> {
        // Count unique size labels
        val uniqueSizes = sizeLabels.map { it.token.text }.distinct()
        
        // Count measurement groups (grouped by size label prefix)
        val measurementGroups = measurements.keys.map { it.split("_").first() }.distinct()
        
        // If mismatch, prefer fewer sizes (never hallucinate)
        return if (uniqueSizes.size > measurementGroups.size) {
            Log.w(TAG, "⚠️ [Validation] Size count mismatch: ${uniqueSizes.size} labels vs ${measurementGroups.size} measurement groups")
            // Return sizes that have measurements
            sizeLabels.filter { sizeLabel ->
                measurementGroups.contains(sizeLabel.token.text)
            }
        } else {
            sizeLabels
        }
    }
    
    /**
     * STAGE 7: Region & Gender Context Injection
     * Apply regional sizing context and map measurements to sizes
     * ✅ FIX: Improved measurement mapping with fallback strategies
     */
    private fun injectContext(
        sizeLabels: List<OcrTableCell>,
        measurements: Map<String, MeasurementRangeWithConfidence>,
        allCells: List<OcrTableCell>,
        @Suppress("UNUSED_PARAMETER") region: SizeRegion,
        @Suppress("UNUSED_PARAMETER") gender: Gender
    ): List<SizeChartEntry> {
        val cellsByRow = allCells.groupBy { it.row }
        val dimensionHeaders = allCells.filter { it.role == CellRole.DIMENSION_LABEL }
        
        // ✅ FIX: Also check header row (row 0) for dimension labels
        val headerRowCells = cellsByRow[0] ?: emptyList()
        val headerDimensionMap = headerRowCells
            .filter { it.role == CellRole.DIMENSION_LABEL }
            .associate { it.column to mapDimensionName(it.token.text) }
        
        return sizeLabels.map { sizeCell ->
            val row = sizeCell.row
            val rowCells = cellsByRow[row] ?: emptyList()
            
            // Find dimension labels to map measurements
            val dimensionMap = mutableMapOf<String, MeasurementRangeWithConfidence?>()
            
            // Strategy 1: Map measurements by column position with dimension headers
            rowCells.filter { it.role == CellRole.MEASUREMENT_VALUE }.forEach { measurementCell ->
                val column = measurementCell.column
                
                // Try to find dimension header in same column (from header row or dimension labels)
                val dimensionName = headerDimensionMap[column] 
                    ?: dimensionHeaders.firstOrNull { it.column == column }?.let { mapDimensionName(it.token.text) }
                    ?: inferDimensionFromPosition(column, rowCells.size)  // ✅ FIX: Fallback inference
                
                if (dimensionName != "unknown") {
                    val parsed = parseMeasurement(measurementCell.token.text, measurementCell.token.confidence)
                    if (parsed != null) {
                        dimensionMap[dimensionName] = parsed
                        Log.d(TAG, "✅ [Stage 7] Mapped measurement: column=$column, dimension=$dimensionName, value=${measurementCell.token.text}")
                    }
                }
            }
            
            // Strategy 2: Use measurements map if available (from Stage 5)
            val sizeLabelText = sizeCell.token.text
            measurements.filter { it.key.startsWith("${sizeLabelText}_") }.forEach { (key, measurement) ->
                // Try to infer dimension from column in key
                val columnFromKey = key.split("_").getOrNull(1)?.toIntOrNull()
                if (columnFromKey != null) {
                    val dimensionName = headerDimensionMap[columnFromKey] 
                        ?: inferDimensionFromPosition(columnFromKey, rowCells.size)
                    if (dimensionName != "unknown" && !dimensionMap.containsKey(dimensionName)) {
                        dimensionMap[dimensionName] = measurement
                        Log.d(TAG, "✅ [Stage 7] Mapped from measurements map: $dimensionName")
                    }
                }
            }
            
            // Strategy 3: If no measurements found, try to parse all numeric values in row as measurements
            if (dimensionMap.isEmpty()) {
                Log.w(TAG, "⚠️ [Stage 7] No measurements mapped for size ${sizeLabelText}, trying fallback parsing")
                rowCells.filter { it.role == CellRole.MEASUREMENT_VALUE || it.role == CellRole.UNKNOWN }
                    .forEachIndexed { index, cell ->
                        val parsed = parseMeasurement(cell.token.text, cell.token.confidence)
                        if (parsed != null) {
                            // Infer dimension by position (common order: chest, waist, hip)
                            val inferredDimension = when (index) {
                                0 -> "chest"
                                1 -> "waist"
                                2 -> "hip"
                                3 -> "shoulder"
                                4 -> "length"
                                else -> null
                            }
                            if (inferredDimension != null && !dimensionMap.containsKey(inferredDimension)) {
                                dimensionMap[inferredDimension] = parsed
                                Log.d(TAG, "✅ [Stage 7] Fallback mapped: $inferredDimension = ${cell.token.text}")
                            }
                        }
                    }
            }
            
            // Convert to SizeChartEntry
            SizeChartEntry(
                sizeLabel = SizeLabelNormalizer.normalize(sizeCell.token.text),
                chest = dimensionMap["chest"]?.toMeasurementRange(),
                waist = dimensionMap["waist"]?.toMeasurementRange(),
                hip = dimensionMap["hip"]?.toMeasurementRange(),
                shoulder = dimensionMap["shoulder"]?.toMeasurementRange(),
                length = dimensionMap["length"]?.toMeasurementRange(),
                inseam = dimensionMap["inseam"]?.toMeasurementRange(),
                sleeve = dimensionMap["sleeve"]?.toMeasurementRange()
            )
        }
    }
    
    /**
     * ✅ FIX: Infer dimension from column position (common table layouts)
     */
    private fun inferDimensionFromPosition(column: Int, totalColumns: Int): String {
        // Common order in size charts: Size | Chest | Waist | Hip | Length | ...
        return when (column) {
            0 -> "unknown"  // Usually size label
            1 -> "chest"    // Most common first measurement
            2 -> "waist"    // Second most common
            3 -> "hip"      // Third
            4 -> "length"   // Fourth
            5 -> "shoulder" // Fifth
            else -> if (column < totalColumns / 2) "chest" else "waist"  // Fallback
        }
    }
    
    private fun mapDimensionName(text: String): String {
        val upper = text.uppercase()
        return when {
            upper.contains("CHEST") || upper.contains("BUST") || upper.contains("DADA") -> "chest"
            upper.contains("WAIST") || upper.contains("PINGGANG") -> "waist"
            upper.contains("HIP") || upper.contains("PINGGUL") -> "hip"
            upper.contains("SHOULDER") || upper.contains("BAHU") -> "shoulder"
            upper.contains("LENGTH") || upper.contains("PANJANG") -> "length"
            upper.contains("INSEAM") -> "inseam"
            upper.contains("SLEEVE") || upper.contains("LENGAN") -> "sleeve"
            else -> "unknown"
        }
    }
    
    /**
     * STAGE 8: Confidence Collapse
     * Compounded confidence calculation
     */
    private fun collapseConfidence(
        ocrConfidence: Double,
        structuralConfidence: Double,
        semanticConfidence: Double,
        regionalConsistency: Double
    ): Double {
        // Multiplicative collapse: if ANY factor is low, final confidence drops significantly
        val collapsed = ocrConfidence * structuralConfidence * semanticConfidence * regionalConsistency
        
        // Apply harsh penalty if any factor < 0.5
        val penalty = if (listOf(ocrConfidence, structuralConfidence, semanticConfidence, regionalConsistency)
                .any { it < 0.5 }) {
            0.5  // 50% penalty
        } else {
            1.0
        }
        
        return (collapsed * penalty).coerceIn(0.0, 1.0)
    }
    
    // Helper functions
    private fun calculateStructuralConfidence(cells: List<OcrTableCell>): Double {
        if (cells.isEmpty()) return 0.0
        val rows = cells.groupBy { it.row }.size
        val columns = cells.groupBy { it.column }.size
        val sizeLabels = cells.count { it.role == CellRole.SIZE_LABEL }
        val measurements = cells.count { it.role == CellRole.MEASUREMENT_VALUE }
        
        // ✅ FIX: More lenient confidence calculation
        // Even with 2 rows (header + 1 data row) and 2 columns, we can have valid structure
        return when {
            rows >= 3 && columns >= 3 && sizeLabels > 0 && measurements > 0 -> 0.9
            rows >= 2 && columns >= 2 && sizeLabels > 0 && measurements > 0 -> 0.75  // ✅ FIX: Increased from 0.7
            rows >= 2 && (sizeLabels > 0 || measurements > 0) -> 0.6  // ✅ FIX: Acceptable even with minimal structure
            else -> 0.4  // ✅ FIX: Lower minimum instead of 0.5
        }
    }
    
    private fun calculateSemanticConfidence(
        sizeLabels: List<OcrTableCell>,
        allCells: List<OcrTableCell>
    ): Double {
        if (sizeLabels.isEmpty()) return 0.0
        val totalCells = allCells.size
        val labelRatio = sizeLabels.size.toDouble() / totalCells
        return when {
            labelRatio > 0.2 -> 0.9  // Good ratio of labels
            labelRatio > 0.1 -> 0.7
            else -> 0.5
        }
    }
    
    private fun calculateRegionalConsistency(
        @Suppress("UNUSED_PARAMETER") sizes: List<SizeChartEntry>,
        @Suppress("UNUSED_PARAMETER") region: SizeRegion
    ): Double {
        // Simplified: check if size labels match regional patterns
        // Full implementation would validate against known regional sizing systems
        return 0.8  // Default moderate confidence
    }
    
    private fun generateWarnings(
        stages: List<ProcessingStage>,
        finalConfidence: Double
    ): List<String> {
        val warnings = mutableListOf<String>()
        
        // Silenced user-facing warnings for low confidence or incomplete structure
        // Issues are still logged in ProcessingStage for internal debugging
        
        return warnings.distinct()
    }
}


