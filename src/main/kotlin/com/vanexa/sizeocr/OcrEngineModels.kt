package com.vanexa.sizeocr

import java.io.Serializable

/**
 * Data structures for the Size Guide OCR & Table Reconstruction Engine.
 * Author: Ikbal Fadilah (Causyne)
 * License: MIT
 */

/**
 * 2D Bounding Box representation independent of Android platform imports for portable JVM testing.
 */
data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) : Serializable {
    fun centerX(): Int = (left + right) / 2
    fun centerY(): Int = (top + bottom) / 2
    fun width(): Int = right - left
    fun height(): Int = bottom - top
}

/**
 * Stage 1: Raw OCR Token preserving 2D spatial relationships.
 */
data class RawOcrToken(
    val text: String,
    val bbox: BoundingBox,
    val confidence: Double = 0.8
) : Serializable

/**
 * Stage 3: Semantic role classification for a table cell.
 */
enum class CellRole {
    SIZE_LABEL,        // S, M, L, XL, 32, 40, EU 48
    DIMENSION_LABEL,   // Chest, Waist, Hip, Length, etc.
    MEASUREMENT_VALUE, // 96-100, 38-40, 72cm
    UNKNOWN
}

/**
 * Stage 3: Reconstructed Table Cell with spatial grid coordinates.
 */
data class OcrTableCell(
    val row: Int,
    val column: Int,
    val token: RawOcrToken,
    val role: CellRole
) : Serializable

/**
 * Measurement units supported by the engine.
 */
enum class MeasurementUnit {
    CM,
    INCH,
    UNKNOWN
}

/**
 * Stage 5: Bounded numerical measurement range.
 */
data class MeasurementRange(
    val min: Double,
    val max: Double,
    val unit: MeasurementUnit = MeasurementUnit.CM,
    val confidence: Double = 1.0
) : Serializable {
    val midpoint: Double get() = (min + max) / 2.0
}

/**
 * Supported sizing regions.
 */
enum class SizeRegion {
    US,
    EU,
    UK,
    ASIA,
    ID
}

/**
 * Biological gender context for anthropometric normalization.
 */
enum class Gender {
    MALE,
    FEMALE,
    UNISEX
}

/**
 * Normalized output row for a single clothing size.
 */
data class SizeChartEntry(
    val sizeLabel: String,
    val chest: MeasurementRange? = null,
    val waist: MeasurementRange? = null,
    val hip: MeasurementRange? = null,
    val shoulder: MeasurementRange? = null,
    val length: MeasurementRange? = null,
    val inseam: MeasurementRange? = null,
    val sleeve: MeasurementRange? = null
) : Serializable

/**
 * Diagnostic record of an execution stage.
 */
data class ProcessingStage(
    val stage: Int,
    val name: String,
    val confidence: Double,
    val tokenCount: Int,
    val issues: List<String> = emptyList()
) : Serializable

/**
 * Comprehensive metadata regarding the OCR and table reconstruction pipeline.
 */
data class OcrMetadata(
    val rawTokenCount: Int,
    val normalizedTokenCount: Int,
    val detectedRows: Int,
    val detectedColumns: Int,
    val sizeLabelCount: Int,
    val measurementCount: Int,
    val structuralConfidence: Double,
    val semanticConfidence: Double,
    val regionalConsistency: Double,
    val stages: List<ProcessingStage>
) : Serializable

/**
 * Final structured output of the OCR Size Intelligence Engine.
 */
data class ParsedSizeChart(
    val sizes: List<SizeChartEntry>,
    val overallConfidence: Double,
    val warnings: List<String>,
    val metadata: OcrMetadata
) : Serializable

/**
 * Garment Fit Classifications.
 */
enum class FitCategory {
    TIGHT_COMPRESSIVE,
    FITTED_IDEAL,
    RELAXED,
    OVERSIZED_LOOSE
}

/**
 * Output of the Biomechanical Fit Scoring Engine.
 */
data class FitScoreResult(
    val sizeLabel: String,
    val comfortIndex: Double,
    val fitCategory: FitCategory,
    val dimensionEvaluated: String,
    val confidence: Double,
    val notes: String
) : Serializable
