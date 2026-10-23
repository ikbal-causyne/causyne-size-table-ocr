package com.vanexa.sizeocr

import kotlin.math.abs
import kotlin.math.pow

/**
 * OCR Size Intelligence Engine
 * 
 * An autonomous, purely offline 8-stage table reconstruction pipeline
 * designed for mobile edge runtimes.
 *
 * Core Philosophy:
 * 1. OCR is noisy input, NOT truth.
 * 2. Sizes are geometric intervals, NOT arbitrary labels.
 * 3. Spatial context dictates cell semantic role.
 * 4. Compounded confidence collapse filters optical anomalies.
 *
 * Author: Ikbal Fadilah (Causyne)
 * License: MIT
 */
class OcrSizeIntelligenceEngine {

    /**
     * Executes the 8-stage table reconstruction pipeline on extracted spatial tokens.
     * Operates completely offline with zero external network dependencies.
     */
    fun processTokens(
        rawTokens: List<RawOcrToken>,
        userRegion: SizeRegion = SizeRegion.US,
        userGender: Gender = Gender.UNISEX
    ): ParsedSizeChart {
        val stages = mutableListOf<ProcessingStage>()

        // Stage 1: Raw Token Ingestion
        val stage1Confidence = if (rawTokens.isNotEmpty()) {
            rawTokens.map { it.confidence }.average()
        } else {
            0.0
        }
        stages.add(
            ProcessingStage(
                stage = 1,
                name = "Raw OCR Extraction",
                confidence = stage1Confidence,
                tokenCount = rawTokens.size,
                issues = if (rawTokens.isEmpty()) listOf("Zero tokens extracted") else emptyList()
            )
        )

        if (rawTokens.isEmpty()) {
            return createEmptyResult(stages)
        }

        // Stage 2: Token Normalization
        val normalizedTokens = TokenNormalizer.normalize(rawTokens)
        val stage2Confidence = if (normalizedTokens.isNotEmpty()) {
            normalizedTokens.map { it.confidence }.average()
        } else {
            0.0
        }
        stages.add(
            ProcessingStage(
                stage = 2,
                name = "Token Normalization",
                confidence = stage2Confidence,
                tokenCount = normalizedTokens.size,
                issues = if (normalizedTokens.size < rawTokens.size) {
                    listOf("${rawTokens.size - normalizedTokens.size} low-confidence tokens pruned")
                } else emptyList()
            )
        )

        // Stage 3: Structural Detection & Grid Topology Inference
        val tableCells = GeometricTableDetector.detectGrid(normalizedTokens)
        val structuralConfidence = GeometricTableDetector.calculateStructuralConfidence(tableCells)
        stages.add(
            ProcessingStage(
                stage = 3,
                name = "Structural Grid Detection",
                confidence = structuralConfidence,
                tokenCount = tableCells.size,
                issues = if (tableCells.isEmpty()) listOf("Failed to cluster table grid") else emptyList()
            )
        )

        // Stage 4: Size Label Intelligence
        val sizeLabelCells = tableCells.filter { it.role == CellRole.SIZE_LABEL }
        val semanticConfidence = if (sizeLabelCells.isNotEmpty()) 0.90 else 0.40
        stages.add(
            ProcessingStage(
                stage = 4,
                name = "Size Label Reasoning",
                confidence = semanticConfidence,
                tokenCount = sizeLabelCells.size,
                issues = if (sizeLabelCells.isEmpty()) listOf("No distinct size labels identified") else emptyList()
            )
        )

        // Stage 5: Measurement Range Extraction
        val measurementsByCell = extractMeasurements(tableCells)
        val measurementConfidence = if (measurementsByCell.isNotEmpty()) 0.88 else 0.45
        stages.add(
            ProcessingStage(
                stage = 5,
                name = "Measurement Extraction",
                confidence = measurementConfidence,
                tokenCount = measurementsByCell.size,
                issues = if (measurementsByCell.isEmpty()) listOf("No numerical measurements parsed") else emptyList()
            )
        )

        // Stage 6: Structural Anti-Hallucination & Monotonicity Validation
        val sizeEntries = assembleSizeEntries(tableCells, sizeLabelCells, measurementsByCell)
        val monotonicityIssues = validateMonotonicity(sizeEntries)
        val stage6Confidence = if (monotonicityIssues.isEmpty()) 0.95 else 0.70
        stages.add(
            ProcessingStage(
                stage = 6,
                name = "Structural Validation",
                confidence = stage6Confidence,
                tokenCount = sizeEntries.size,
                issues = monotonicityIssues
            )
        )

        // Stage 7: Regional & Gender Context Injection
        val regionalConsistency = evaluateRegionalConsistency(sizeEntries, userRegion)
        stages.add(
            ProcessingStage(
                stage = 7,
                name = "Regional Normalization",
                confidence = regionalConsistency,
                tokenCount = sizeEntries.size,
                issues = if (regionalConsistency < 0.75) listOf("Slight deviation from regional standard") else emptyList()
            )
        )

        // Stage 8: Compounded Confidence Collapse
        val finalConfidence = collapseConfidence(
            ocrConf = stage1Confidence,
            structConf = structuralConfidence,
            semConf = semanticConfidence,
            regConf = regionalConsistency
        )

        val warnings = mutableListOf<String>()
        stages.forEach { warnings.addAll(it.issues) }

        stages.add(
            ProcessingStage(
                stage = 8,
                name = "Confidence Collapse",
                confidence = finalConfidence,
                tokenCount = sizeEntries.size,
                issues = warnings
            )
        )

        val metadata = OcrMetadata(
            rawTokenCount = rawTokens.size,
            normalizedTokenCount = normalizedTokens.size,
            detectedRows = tableCells.groupBy { it.row }.size,
            detectedColumns = tableCells.groupBy { it.column }.size,
            sizeLabelCount = sizeLabelCells.size,
            measurementCount = measurementsByCell.size,
            structuralConfidence = structuralConfidence,
            semanticConfidence = semanticConfidence,
            regionalConsistency = regionalConsistency,
            stages = stages
        )

        return ParsedSizeChart(
            sizes = sizeEntries,
            overallConfidence = finalConfidence,
            warnings = warnings,
            metadata = metadata
        )
    }

    private fun extractMeasurements(cells: List<OcrTableCell>): Map<Pair<Int, Int>, MeasurementRange> {
        val result = mutableMapOf<Pair<Int, Int>, MeasurementRange>()

        for (cell in cells) {
            val text = cell.token.text.trim()
            val unit = when {
                text.contains("inch", ignoreCase = true) -> MeasurementUnit.INCH
                text.contains("cm", ignoreCase = true) -> MeasurementUnit.CM
                else -> MeasurementUnit.CM
            }

            // Pattern: range like 96-102
            val rangeMatch = Regex("""(\d{1,3}(?:\.\d)?)\s*-\s*(\d{1,3}(?:\.\d)?)""").find(text)
            if (rangeMatch != null) {
                val minVal = rangeMatch.groupValues[1].toDoubleOrNull()
                val maxVal = rangeMatch.groupValues[2].toDoubleOrNull()
                if (minVal != null && maxVal != null) {
                    result[Pair(cell.row, cell.column)] = MeasurementRange(
                        min = minVal,
                        max = maxVal,
                        unit = unit,
                        confidence = 0.90
                    )
                    continue
                }
            }

            // Pattern: single number like 98 or 98cm
            val singleMatch = Regex("""(\d{1,3}(?:\.\d)?)""").find(text)
            if (singleMatch != null) {
                val num = singleMatch.groupValues[1].toDoubleOrNull()
                if (num != null && num in 20.0..250.0) {
                    result[Pair(cell.row, cell.column)] = MeasurementRange(
                        min = num,
                        max = num,
                        unit = unit,
                        confidence = 0.85
                    )
                }
            }
        }
        return result
    }

    private fun assembleSizeEntries(
        allCells: List<OcrTableCell>,
        sizeLabelCells: List<OcrTableCell>,
        measurements: Map<Pair<Int, Int>, MeasurementRange>
    ): List<SizeChartEntry> {
        if (sizeLabelCells.isEmpty()) return emptyList()

        // Determine if sizes are arranged vertically (rows) or horizontally (columns)
        val uniqueRows = sizeLabelCells.map { it.row }.distinct()
        val uniqueCols = sizeLabelCells.map { it.column }.distinct()

        val isVerticalLayout = uniqueRows.size >= uniqueCols.size
        val entries = mutableListOf<SizeChartEntry>()

        if (isVerticalLayout) {
            // Each size label defines a row
            val dimensionHeaders = allCells.filter { it.role == CellRole.DIMENSION_LABEL }
            val colToDim = dimensionHeaders.associate { it.column to it.token.text.uppercase() }

            for (labelCell in sizeLabelCells) {
                var chest: MeasurementRange? = null
                var waist: MeasurementRange? = null
                var hip: MeasurementRange? = null
                var length: MeasurementRange? = null

                val rowMeasurements = measurements.filter { it.key.first == labelCell.row }
                for ((coord, range) in rowMeasurements) {
                    val colIdx = coord.second
                    val dim = colToDim[colIdx] ?: ""
                    when {
                        dim.contains("CHEST") || dim.contains("DADA") || dim.contains("BUST") -> chest = range
                        dim.contains("WAIST") || dim.contains("PINGGANG") -> waist = range
                        dim.contains("HIP") || dim.contains("PINGGUL") -> hip = range
                        dim.contains("LENGTH") || dim.contains("PANJANG") -> length = range
                        else -> {
                            // Assign based on column order if headers are missing
                            if (chest == null) chest = range
                            else if (waist == null) waist = range
                            else if (hip == null) hip = range
                            else if (length == null) length = range
                        }
                    }
                }

                entries.add(
                    SizeChartEntry(
                        sizeLabel = TokenNormalizer.normalizeSizeLabelString(labelCell.token.text),
                        chest = chest,
                        waist = waist,
                        hip = hip,
                        length = length
                    )
                )
            }
        } else {
            // Horizontal layout: each size label defines a column
            for (labelCell in sizeLabelCells) {
                val colMeasurements = measurements.filter { it.key.second == labelCell.column }
                var chest: MeasurementRange? = null
                var waist: MeasurementRange? = null

                for ((_, range) in colMeasurements) {
                    if (chest == null) chest = range
                    else if (waist == null) waist = range
                }

                entries.add(
                    SizeChartEntry(
                        sizeLabel = TokenNormalizer.normalizeSizeLabelString(labelCell.token.text),
                        chest = chest,
                        waist = waist
                    )
                )
            }
        }

        return entries
    }

    private fun validateMonotonicity(entries: List<SizeChartEntry>): List<String> {
        val issues = mutableListOf<String>()
        val chestValues = entries.mapNotNull { it.chest?.midpoint }
        for (i in 0 until chestValues.size - 1) {
            if (chestValues[i] > chestValues[i + 1]) {
                issues.add("Monotonicity violation between size ${entries[i].sizeLabel} and ${entries[i + 1].sizeLabel}")
            }
        }
        return issues
    }

    private fun evaluateRegionalConsistency(entries: List<SizeChartEntry>, region: SizeRegion): Double {
        if (entries.isEmpty()) return 0.5
        val labels = entries.map { it.sizeLabel }
        val hasStandardAlpha = labels.any { it in listOf("S", "M", "L", "XL") }
        val hasRegionalPrefix = labels.any { it.startsWith(region.name) }
        return if (hasStandardAlpha || hasRegionalPrefix) 0.95 else 0.80
    }

    /**
     * Mathematical Compound Confidence Collapse:
     * C_final = (C_ocr)^w1 * (C_struct)^w2 * (C_sem)^w3 * (C_reg)^w4
     */
    fun collapseConfidence(
        ocrConf: Double,
        structConf: Double,
        semConf: Double,
        regConf: Double
    ): Double {
        val w1 = 0.25
        val w2 = 0.35
        val w3 = 0.25
        val w4 = 0.15

        val c1 = ocrConf.coerceIn(0.1, 1.0).pow(w1)
        val c2 = structConf.coerceIn(0.1, 1.0).pow(w2)
        val c3 = semConf.coerceIn(0.1, 1.0).pow(w3)
        val c4 = regConf.coerceIn(0.1, 1.0).pow(w4)

        return (c1 * c2 * c3 * c4).coerceIn(0.0, 1.0)
    }

    private fun createEmptyResult(stages: List<ProcessingStage>): ParsedSizeChart {
        return ParsedSizeChart(
            sizes = emptyList(),
            overallConfidence = 0.0,
            warnings = listOf("No text or table detected in image"),
            metadata = OcrMetadata(
                rawTokenCount = 0,
                normalizedTokenCount = 0,
                detectedRows = 0,
                detectedColumns = 0,
                sizeLabelCount = 0,
                measurementCount = 0,
                structuralConfidence = 0.0,
                semanticConfidence = 0.0,
                regionalConsistency = 0.0,
                stages = stages
            )
        )
    }
}
