package com.vanexa.supercalculator.domain.model

import android.graphics.Rect
import java.io.Serializable

/**
 * OCR Engine Data Models - OCR_ENGINE_MASTER_PROMPT.md Compliant
 * 
 * This file contains all data models required for the 8-stage OCR pipeline:
 * - Stage 1: RawOcrToken (raw OCR extraction with spatial relationships)
 * - Stage 3: OcrTableCell (structural detection with cell roles)
 * - Stage 5: MeasurementRange (measurement extraction with confidence)
 * - Stage 8: ParsedSizeChart (final output with warnings)
 */

/**
 * STAGE 1: Raw OCR Token
 * Preserves spatial relationships from ML Kit OCR output
 */
data class RawOcrToken(
    val text: String,
    val bbox: Rect,  // Bounding box from ML Kit
    val confidence: Double  // OCR confidence from ML Kit (0.0-1.0)
) : Serializable

/**
 * STAGE 3: OCR Table Cell
 * Represents a cell in the inferred table structure
 */
data class OcrTableCell(
    val row: Int,
    val column: Int,
    val token: RawOcrToken,
    val role: CellRole
) : Serializable

/**
 * STAGE 3: Cell Role Classification
 * Determines what type of data each cell contains
 */
enum class CellRole {
    SIZE_LABEL,        // Size labels: S, M, L, XL, 42, EU 50, etc.
    DIMENSION_LABEL,   // Dimension headers: Chest, Waist, Hip, etc.
    MEASUREMENT_VALUE, // Measurement values: 96-100, 38-40, etc.
    UNKNOWN            // Unclassified token
}

/**
 * STAGE 5: Enhanced Measurement Range
 * Includes confidence from OCR and parsing stages
 */
data class MeasurementRangeWithConfidence(
    val min: Double,
    val max: Double,
    val unit: MeasurementUnit,
    val confidence: Double,  // Combined OCR + parsing confidence (0.0-1.0)
    val ocrConfidence: Double,  // Original OCR confidence
    val parsingConfidence: Double  // Confidence from parsing/validation
) : Serializable {
    /**
     * Convert to standard MeasurementRange for compatibility
     */
    fun toMeasurementRange(): MeasurementRange {
        return MeasurementRange(
            min = min,
            max = max,
            unit = unit,
            confidence = confidence
        )
    }
}

/**
 * STAGE 8: Parsed Size Chart Output
 * Final structured output from OCR engine with warnings
 */
data class ParsedSizeChart(
    val sizes: List<SizeChartEntry>,
    val overallConfidence: Double,  // Compounded confidence from all stages
    val warnings: List<String>,  // User-facing warnings about data quality
    val ocrMetadata: OcrMetadata  // Metadata about OCR processing
) : Serializable

/**
 * OCR Processing Metadata
 * Tracks processing stages and quality metrics
 */
data class OcrMetadata(
    val rawTokenCount: Int,
    val normalizedTokenCount: Int,
    val detectedRows: Int,
    val detectedColumns: Int,
    val sizeLabelCount: Int,
    val measurementCount: Int,
    val structuralConfidence: Double,  // Confidence in table structure detection
    val semanticConfidence: Double,  // Confidence in size label classification
    val regionalConsistency: Double,  // Confidence in regional sizing consistency
    val processingStages: List<ProcessingStage>
) : Serializable

/**
 * Processing Stage Tracking
 */
data class ProcessingStage(
    val stage: Int,  // 1-8
    val name: String,
    val confidence: Double,
    val tokenCount: Int,
    val issues: List<String>
) : Serializable

