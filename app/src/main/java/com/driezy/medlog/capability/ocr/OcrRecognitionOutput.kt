package com.driezy.medlog.capability.ocr

data class OcrRecognitionOutput(val groups: List<OcrResultGroup> = emptyList()) {
    val mergedTexts: List<String> = mergeGroups(groups)

    companion object {
        val Empty = OcrRecognitionOutput()

        private fun mergeGroups(groups: List<OcrResultGroup>): List<String> {
            val seen = mutableSetOf<String>()
            return groups
                .flatMap { it.texts }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .filter { seen.add(it.replace("\\s+".toRegex(), " ")) }
        }
    }
}

data class OcrResultGroup(val source: OcrResultSource, val texts: List<String>)

enum class OcrResultSource {
    ML_KIT_ORIGINAL,
    PREPROCESSED_VARIANTS,
    SEVEN_SEGMENT_MODEL,
    LCD_CROP_MODEL,
}
