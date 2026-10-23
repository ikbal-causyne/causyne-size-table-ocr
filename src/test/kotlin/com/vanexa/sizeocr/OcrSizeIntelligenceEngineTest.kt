package com.vanexa.sizeocr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit Tests for the Size Guide OCR & Table Reconstruction Engine.
 * Author: Ikbal Fadilah (Causyne)
 * License: MIT
 */
class OcrSizeIntelligenceEngineTest {

    @Test
    fun testTokenNormalization() {
        val rawTokens = listOf(
            RawOcrToken("CHEST", BoundingBox(10, 10, 60, 30), 0.95),
            RawOcrToken("9O-95cm", BoundingBox(10, 40, 70, 60), 0.85), // 'O' should become '0'
            RawOcrToken("IlO-115", BoundingBox(10, 70, 70, 90), 0.80),  // 'Il' should become '11'
            RawOcrToken("z", BoundingBox(10, 100, 15, 110), 0.10)       // Low confidence, should be pruned
        )

        val normalized = TokenNormalizer.normalize(rawTokens)
        assertEquals(3, normalized.size)
        assertEquals("CHEST", normalized[0].text)
        assertTrue(normalized[1].text.contains("90-95cm"))
        assertTrue(normalized[2].text.contains("110-115"))
    }

    @Test
    fun testTableGridReconstruction() {
        // Construct a synthetic 2x3 table
        // Headers: [Size, Chest, Waist]
        // Row 1:   [S,    92cm,  76cm]
        // Row 2:   [M,    98cm,  82cm]
        val tokens = listOf(
            // Header Row (Y: 20)
            RawOcrToken("Size", BoundingBox(20, 15, 50, 30), 0.95),
            RawOcrToken("Chest", BoundingBox(80, 15, 120, 30), 0.95),
            RawOcrToken("Waist", BoundingBox(150, 15, 190, 30), 0.95),

            // Row 1 (Y: 60)
            RawOcrToken("S", BoundingBox(20, 55, 35, 70), 0.90),
            RawOcrToken("92cm", BoundingBox(80, 55, 115, 70), 0.90),
            RawOcrToken("76cm", BoundingBox(150, 55, 185, 70), 0.90),

            // Row 2 (Y: 100)
            RawOcrToken("M", BoundingBox(20, 95, 35, 110), 0.92),
            RawOcrToken("98cm", BoundingBox(80, 95, 115, 110), 0.92),
            RawOcrToken("82cm", BoundingBox(150, 95, 185, 110), 0.92)
        )

        val engine = OcrSizeIntelligenceEngine()
        val result = engine.processTokens(tokens)

        assertEquals(2, result.sizes.size)
        assertEquals("S", result.sizes[0].sizeLabel)
        assertNotNull(result.sizes[0].chest)
        assertEquals(92.0, result.sizes[0].chest?.min)

        assertEquals("M", result.sizes[1].sizeLabel)
        assertNotNull(result.sizes[1].chest)
        assertEquals(98.0, result.sizes[1].chest?.min)

        assertTrue(result.overallConfidence > 0.70)
    }

    @Test
    fun testFitScoringBiomechanicalEngine() {
        val engine = FitScoringEngine()

        // 1. Exact match test
        val fittedResult = engine.evaluateFit(
            sizeLabel = "M",
            garmentMeasurementCm = 100.0,
            userBodyMeasurementCm = 95.2, // 95.2 * 1.05 = ~100.0
            dimensionName = "Chest",
            easeAllowance = FitScoringEngine.EASE_REGULAR,
            stretchFactor = 0.0
        )
        assertEquals(FitCategory.FITTED_IDEAL, fittedResult.fitCategory)
        assertTrue(fittedResult.confidence >= 0.90)

        // 2. Compressive fit test
        val tightResult = engine.evaluateFit(
            sizeLabel = "S",
            garmentMeasurementCm = 85.0,
            userBodyMeasurementCm = 95.0,
            dimensionName = "Chest",
            easeAllowance = FitScoringEngine.EASE_REGULAR,
            stretchFactor = 0.0
        )
        assertEquals(FitCategory.TIGHT_COMPRESSIVE, tightResult.fitCategory)

    }
}
