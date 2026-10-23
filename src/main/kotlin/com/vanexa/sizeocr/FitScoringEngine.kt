package com.vanexa.sizeocr

/**
 * Biomechanical Fit Scoring Engine
 * Experimental heuristic scoring of garment and body measurements.
 * Coefficients in this class have not been clinically or anthropometrically validated.
 * Author: Ikbal Fadilah (Causyne)
 * License: MIT
 */
class FitScoringEngine {

    companion object {
        const val EASE_SKINNY = 0.98
        const val EASE_REGULAR = 1.05
        const val EASE_RELAXED = 1.10
        const val EASE_OVERSIZED = 1.18

        const val STRETCH_RIGID = 0.02      // 100% cotton / denim
        const val STRETCH_NATURAL = 0.05    // Wool, linen, knit
        const val STRETCH_MEDIUM = 0.15     // Cotton + 3% elastane
        const val STRETCH_HIGH = 0.35       // Spandex / activewear
    }

    /**
     * Calculates the Comfort Index (CI) for a garment dimension against user measurement:
     *
     * CI = [ D_garment * (1 + stretch) ] / [ M_body * ease ]
     */
    fun evaluateFit(
        sizeLabel: String,
        garmentMeasurementCm: Double,
        userBodyMeasurementCm: Double,
        dimensionName: String = "Chest",
        easeAllowance: Double = EASE_REGULAR,
        stretchFactor: Double = STRETCH_NATURAL
    ): FitScoreResult {
        if (userBodyMeasurementCm <= 0.0 || garmentMeasurementCm <= 0.0) {
            return FitScoreResult(
                sizeLabel = sizeLabel,
                comfortIndex = 0.0,
                fitCategory = FitCategory.FITTED_IDEAL,
                dimensionEvaluated = dimensionName,
                confidence = 0.0,
                notes = "Invalid measurement input"
            )
        }

        val effectiveGarment = garmentMeasurementCm * (1.0 + stretchFactor)
        val targetBody = userBodyMeasurementCm * easeAllowance
        val comfortIndex = effectiveGarment / targetBody

        val category = when {
            comfortIndex < 0.96 -> FitCategory.TIGHT_COMPRESSIVE
            comfortIndex in 0.96..1.05 -> FitCategory.FITTED_IDEAL
            comfortIndex in 1.0501..1.14 -> FitCategory.RELAXED
            else -> FitCategory.OVERSIZED_LOOSE
        }

        val confidence = when {
            comfortIndex in 0.97..1.03 -> 0.96
            comfortIndex in 0.93..1.07 -> 0.88
            else -> 0.75
        }

        val notes = when (category) {
            FitCategory.TIGHT_COMPRESSIVE -> "Garment will fit tightly with noticeable fabric compression."
            FitCategory.FITTED_IDEAL -> "Optimal anatomical fit aligned with standard ease allowance."
            FitCategory.RELAXED -> "Comfortable, relaxed fit with room for motion."
            FitCategory.OVERSIZED_LOOSE -> "Substantial drape and excess volume; consider sizing down."
        }

        return FitScoreResult(
            sizeLabel = sizeLabel,
            comfortIndex = comfortIndex,
            fitCategory = category,
            dimensionEvaluated = dimensionName,
            confidence = confidence,
            notes = notes
        )
    }
}
