package com.vanexa.sizeocr

/**
 * Stage 2: Token Normalizer
 * Cleans character OCR substitutions and standardizes numerical and unit tokens.
 * Author: Ikbal Fadilah (Causyne)
 * License: MIT
 */
object TokenNormalizer {

    private const val MIN_OCR_CONFIDENCE = 0.25

    fun normalize(tokens: List<RawOcrToken>): List<RawOcrToken> {
        return tokens.mapNotNull { token ->
            var text = token.text.trim()

            // Discard tokens with low optical confidence
            if (token.confidence < MIN_OCR_CONFIDENCE) {
                return@mapNotNull null
            }

            // Normalize range hyphens and tildes
            text = text.replace(Regex("[-~–—]"), "-")

            // Standardize units
            text = text.replace(Regex("[\"']"), "inch")
                .replace(Regex("(?i)\\b(inch|inches|in)\\b"), "inch")
                .replace(Regex("(?i)\\b(centimeter|centimeters|cm)\\b"), "cm")

            // Correct common optical substitution errors in digit strings
            if (looksLikeNumericMeasurement(text)) {
                text = correctDigitSubstitutions(text)
            }

            // Keep measurement-unit spelling intact; only label tokens need
            // the label normalization map.
            if (!looksLikeNumericMeasurement(text)) {
                text = normalizeSizeLabelString(text)
            }

            // Prune standalone invalid single characters
            if (text.length == 1 && !text.matches(Regex("[SMLX0-9]"))) {
                return@mapNotNull null
            }

            token.copy(text = text)
        }
    }

    private fun looksLikeNumericMeasurement(s: String): Boolean {
        // Contains digit or range-like pattern
        return s.matches(Regex(".*\\d.*")) || s.contains("-")
    }

    private fun correctDigitSubstitutions(s: String): String {
        // Limit substitutions to a leading numeric/range span so dimension
        // words and size labels elsewhere in the token remain untouched.
        val span = Regex("^\\s*[0-9OoIl|BS]+(?:\\s*-\\s*[0-9OoIl|BS]+)?").find(s)
            ?: return s
        val corrected = span.value.map { character ->
            when (character) {
                'O', 'o' -> '0'
                'I', 'l', '|' -> '1'
                'B' -> '8'
                'S' -> '5'
                else -> character
            }
        }.joinToString("")
        return s.replaceRange(span.range, corrected)
    }

    fun normalizeSizeLabelString(s: String): String {
        val upper = s.uppercase().trim()
        return when {
            upper.matches(Regex("^(EXTRA SMALL|X-SMALL|X SMALL)$")) -> "XS"
            upper.matches(Regex("^(SMALL)$")) -> "S"
            upper.matches(Regex("^(MEDIUM)$")) -> "M"
            upper.matches(Regex("^(LARGE)$")) -> "L"
            upper.matches(Regex("^(EXTRA LARGE|X-LARGE|X LARGE)$")) -> "XL"
            upper.matches(Regex("^(DOUBLE XL|XX LARGE|2X LARGE|2XL)$")) -> "XXL"
            upper.matches(Regex("^(TRIPLE XL|XXX LARGE|3X LARGE|3XL)$")) -> "XXXL"
            else -> upper
        }
    }
}
