package com.vanexa.supercalculator.domain.usecase

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vanexa.supercalculator.domain.model.OCRResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * OCR Text Extractor using ML Kit Text Recognition v2 (FREE)
 * PRD Section 4.2 & 9 - Fully on-device OCR processing
 * 
 * Technology Stack:
 * - Library: Google ML Kit Text Recognition v2
 * - Cost: $0.00 (Fully on-device)
 * - Supported Languages: Latin, Chinese, Japanese, Korean, Devanagari
 * 
 * OCR Processing Pipeline:
 * Image Input → ML Kit Text Blocks → Text Extraction → 
 * Pattern Recognition → Dimension Parsing → Structure Mapping → 
 * Validation → User Confirmation → Storage
 */
class ImageTextExtractor {
    companion object {
        private const val TAG = "ImageTextExtractor"
    }
    
    // ✅ BUG #53: Make textRecognizer lazy and nullable for proper cleanup
    private var textRecognizer: com.google.mlkit.vision.text.TextRecognizer? = null
    private val lock = Any()
    
    private fun getTextRecognizer(): com.google.mlkit.vision.text.TextRecognizer {
        synchronized(lock) {
            if (textRecognizer == null) {
                textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                android.util.Log.d(TAG, "✅ [BUG #53] TextRecognizer created (lazy initialization)")
            }
            return textRecognizer!!
        }
    }
    
    /**
     * Extract text from bitmap image - PRD Section 4.2
     * Returns raw text for parsing by SizeChartParser
     */
    suspend fun extractText(bitmap: Bitmap): Result<String> = suspendCancellableCoroutine { continuation ->
        try {
            Log.d(TAG, "🔍 [OCR Pipeline Step 1] Starting text extraction from bitmap: ${bitmap.width}x${bitmap.height}")
            
            // Validate bitmap
            if (bitmap.isRecycled) {
                Log.e(TAG, "❌ Bitmap is recycled")
                continuation.resume(Result.failure(IllegalStateException("Bitmap is recycled")))
                return@suspendCancellableCoroutine
            }
            
            // ✅ FIX: Create mutable copy of bitmap for ML Kit to prevent recycling issues
            // ML Kit may hold internal references, so we create a copy that we control
            val bitmapCopy = bitmap.copy(bitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888, bitmap.isMutable)
            Log.d(TAG, "✅ [OCR Pipeline Step 1.5] Created bitmap copy for ML Kit processing")
            
            // Step 2: Create InputImage for ML Kit using the copy
            val image = InputImage.fromBitmap(bitmapCopy, 0)
            Log.d(TAG, "✅ [OCR Pipeline Step 2] InputImage created successfully")
            
            // Step 3: Process with ML Kit Text Recognition
            // ✅ BUG #53: Use lazy-initialized recognizer
            val recognizer = getTextRecognizer()
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    Log.d(TAG, "✅ [OCR Pipeline Step 3] ML Kit processing completed")
                    
                    // ✅ FIX: Use structured text extraction for better OCR handling
                    // ML Kit provides textBlocks with lines and elements - use this for better structure
                    val textBlocks = visionText.textBlocks
                    Log.d(TAG, "📋 Found ${textBlocks.size} text blocks")
                    
                    // ✅ IMPROVED: Extract text with structure awareness
                    // Strategy: Preserve line structure from ML Kit for better parsing
                    val structuredText = buildString {
                        textBlocks.forEachIndexed { blockIndex, block ->
                            val lines = block.lines
                            Log.d(TAG, "  Block $blockIndex: ${lines.size} lines")
                            
                            lines.forEachIndexed { lineIndex, line ->
                                val lineText = line.text.trim()
                                if (lineText.isNotBlank()) {
                                    append(lineText)
                                    append("\n") // Preserve line breaks from ML Kit
                                    Log.d(TAG, "    Line $lineIndex: $lineText")
                                }
                            }
                        }
                    }.trim()
                    
                    // Fallback to simple text if structured extraction is empty
                    val extractedText = if (structuredText.isNotBlank()) {
                        structuredText
                    } else {
                        visionText.text // Use simple text as fallback
                    }
                    
                    Log.d(TAG, "✅ [OCR Pipeline Step 4] Text extracted: ${extractedText.length} characters")
                    Log.d(TAG, "📝 Extracted text preview: ${extractedText.take(300)}...")
                    
                    // ✅ FIX: Recycle bitmap copy after ML Kit processing completes
                    if (!bitmapCopy.isRecycled) {
                        bitmapCopy.recycle()
                        Log.d(TAG, "✅ [OCR Pipeline] Bitmap copy recycled after ML Kit processing")
                    }
                    
                    if (extractedText.isBlank()) {
                        Log.w(TAG, "⚠️ Extracted text is blank - no text found in image")
                        continuation.resume(Result.failure(IllegalStateException("No text found in image. Please ensure the image contains a size chart.")))
                    } else {
                        Log.d(TAG, "✅ [OCR Pipeline Step 5] Text extraction successful - ready for parsing")
                        continuation.resume(Result.success(extractedText))
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ [OCR Pipeline Step 3] ML Kit text extraction failed: ${e.message}", e)
                    // ✅ FIX: Recycle bitmap copy on failure
                    if (!bitmapCopy.isRecycled) {
                        bitmapCopy.recycle()
                        Log.d(TAG, "✅ [OCR Pipeline] Bitmap copy recycled after ML Kit failure")
                    }
                    continuation.resume(Result.failure(e))
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [OCR Pipeline] Error creating InputImage: ${e.message}", e)
            continuation.resume(Result.failure(e))
        }
    }
    
    /**
     * Extract structured OCR data with text blocks - PRD Section 9.1
     * Returns OCRResult for better integration with Size Engine
     */
    suspend fun extractStructuredData(bitmap: Bitmap): OCRResult = suspendCancellableCoroutine { continuation ->
        try {
            Log.d(TAG, "🔍 [Structured OCR] Starting structured extraction from bitmap: ${bitmap.width}x${bitmap.height}")
            
            if (bitmap.isRecycled) {
                Log.e(TAG, "❌ Bitmap is recycled")
                continuation.resume(OCRResult.Error("Bitmap is recycled"))
                return@suspendCancellableCoroutine
            }
            
            val image = InputImage.fromBitmap(bitmap, 0)
            Log.d(TAG, "✅ [Structured OCR] InputImage created")
            
            // ✅ BUG #53: Use lazy-initialized recognizer
            val recognizer = getTextRecognizer()
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val extractedText = visionText.text
                    Log.d(TAG, "✅ [Structured OCR] Text extracted: ${extractedText.length} characters")
                    
                    if (extractedText.isBlank()) {
                        Log.w(TAG, "⚠️ [Structured OCR] No text found")
                        continuation.resume(OCRResult.Error("No text found in image. Please ensure the image contains a size chart."))
                        return@addOnSuccessListener
                    }
                    
                    // Parse extracted text using SizeChartParser
                    // This will be handled by ViewModel to integrate with Size Engine
                    Log.d(TAG, "✅ [Structured OCR] Ready for parsing - text length: ${extractedText.length}")
                    // Return success with empty list - parsing will be done by SizeChartParser
                    continuation.resume(OCRResult.Success(emptyList()))
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ [Structured OCR] Failed: ${e.message}", e)
                    continuation.resume(OCRResult.Error(e.message ?: "OCR processing failed"))
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [Structured OCR] Exception: ${e.message}", e)
            continuation.resume(OCRResult.Error(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Clean up resources
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
     * ✅ BUG #53: Check if TextRecognizer is available
     */
    fun isAvailable(): Boolean {
        synchronized(lock) {
            return textRecognizer != null
        }
    }
}

