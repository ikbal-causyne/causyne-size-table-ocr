package com.vanexa.sizeocr

import kotlin.math.abs

/**
 * Stage 3: Geometric Table Detector
 * Reconstructs a two-dimensional grid topology from spatial bounding boxes.
 * Author: Ikbal Fadilah (Causyne)
 * License: MIT
 */
object GeometricTableDetector {

    fun detectGrid(tokens: List<RawOcrToken>): List<OcrTableCell> {
        if (tokens.isEmpty()) return emptyList()

        val yPositions = tokens.map { it.bbox.centerY().toFloat() }.sorted()
        val rowThreshold = calculateRowThreshold(tokens)

        val xPositions = tokens.map { it.bbox.centerX().toFloat() }.sorted()
        val columnThreshold = calculateColumnThreshold(tokens)

        // Find unique cluster center bands
        val rowBands = cluster1D(yPositions, rowThreshold)
        val columnBands = cluster1D(xPositions, columnThreshold)

        val cells = mutableListOf<OcrTableCell>()

        for (token in tokens) {
            val yCenter = token.bbox.centerY().toFloat()
            val xCenter = token.bbox.centerX().toFloat()

            val rowIdx = findNearestBandIndex(yCenter, rowBands)
            val colIdx = findNearestBandIndex(xCenter, columnBands)

            val role = classifyRole(token.text, rowIdx, colIdx)

            cells.add(
                OcrTableCell(
                    row = rowIdx,
                    column = colIdx,
                    token = token,
                    role = role
                )
            )
        }

        return cells
    }

    private fun calculateRowThreshold(tokens: List<RawOcrToken>): Float {
        if (tokens.size < 2) return 40f
        val yPositions = tokens.map { it.bbox.centerY().toFloat() }.sorted()
        val gaps = yPositions.zipWithNext().map { (a, b) -> abs(a - b) }
        val significantGaps = gaps.filter { it > 10f }
        return if (significantGaps.isNotEmpty()) {
            significantGaps.average().toFloat() * 0.5f
        } else {
            40f
        }
    }

    private fun calculateColumnThreshold(tokens: List<RawOcrToken>): Float {
        if (tokens.size < 2) return 50f
        val xPositions = tokens.map { it.bbox.centerX().toFloat() }.sorted()
        val gaps = xPositions.zipWithNext().map { (a, b) -> abs(a - b) }
        val significantGaps = gaps.filter { it > 15f }
        return if (significantGaps.isNotEmpty()) {
            significantGaps.average().toFloat() * 0.5f
        } else {
            50f
        }
    }

    private fun cluster1D(values: List<Float>, threshold: Float): List<Float> {
        if (values.isEmpty()) return emptyList()
        val clusters = mutableListOf<Float>()
        var currentClusterSum = values.first()
        var currentClusterCount = 1

        for (i in 1 until values.size) {
            val v = values[i]
            val currentAvg = currentClusterSum / currentClusterCount
            if (abs(v - currentAvg) <= threshold) {
                currentClusterSum += v
                currentClusterCount++
            } else {
                clusters.add(currentClusterSum / currentClusterCount)
                currentClusterSum = v
                currentClusterCount = 1
            }
        }
        clusters.add(currentClusterSum / currentClusterCount)
        return clusters
    }

    private fun findNearestBandIndex(pos: Float, bands: List<Float>): Int {
        var bestIdx = 0
        var bestDist = Float.MAX_VALUE
        for (i in bands.indices) {
            val dist = abs(bands[i] - pos)
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = i
            }
        }
        return bestIdx
    }

    fun classifyRole(text: String, row: Int, column: Int): CellRole {
        val upper = text.uppercase().trim()

        // 1. Check for Dimension Label in header rows/columns
        if (row == 0 || column == 0) {
            val dimensionKeywords = listOf(
                "CHEST", "WAIST", "HIP", "SHOULDER", "LENGTH", "INSEAM", "SLEEVE",
                "BUST", "THIGH", "COLLAR", "DADA", "PINGGANG", "PINGGUL", "PANJANG",
                "BAHU", "LEBAR", "TINGGI"
            )
            if (dimensionKeywords.any { upper.contains(it) }) {
                return CellRole.DIMENSION_LABEL
            }
        }

        // 2. Check for Size Label
        if (matchesSizeLabel(text)) {
            return CellRole.SIZE_LABEL
        }

        // 3. Check for Measurement Value
        if (matchesMeasurementValue(text)) {
            return CellRole.MEASUREMENT_VALUE
        }

        return CellRole.UNKNOWN
    }

    fun matchesSizeLabel(text: String): Boolean {
        val upper = text.uppercase().trim()
        if (upper.matches(Regex("^[X]*[SML]$|^[X]+L$"))) return true
        if (upper.matches(Regex("^(XS|S|M|L|XL|XXL|XXXL|2XL|3XL|4XL|5XL)$"))) return true
        if (upper.matches(Regex("^(US|EU|UK|ASIA|ID)\\s*\\d+$"))) return true
        if (upper.matches(Regex("^\\d{2}$")) && upper.toIntOrNull() in 26..56) return true
        return false
    }

    fun matchesMeasurementValue(text: String): Boolean {
        val s = text.trim()
        if (s.matches(Regex("""\d{1,3}(\.\d)?\s*[-~–]\s*\d{1,3}(\.\d)?"""))) return true
        if (s.matches(Regex("""\d{1,3}(\.\d)?\s*(cm|inch)?""", RegexOption.IGNORE_CASE))) return true
        return false
    }

    fun calculateStructuralConfidence(cells: List<OcrTableCell>): Double {
        if (cells.isEmpty()) return 0.0
        val rows = cells.groupBy { it.row }
        val cols = cells.groupBy { it.column }

        if (rows.size < 2 || cols.size < 2) return 0.3

        val hasHeaders = cells.any { it.role == CellRole.DIMENSION_LABEL || it.role == CellRole.SIZE_LABEL }
        val hasValues = cells.any { it.role == CellRole.MEASUREMENT_VALUE }

        return when {
            hasHeaders && hasValues -> 0.92
            hasValues -> 0.70
            else -> 0.50
        }
    }
}
