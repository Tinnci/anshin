package com.driezy.medlog.data.model

enum class BloodPressureClassification { LOW, NORMAL, ELEVATED, STAGE_1, STAGE_2, CRISIS }

enum class BmiClassification { UNDERWEIGHT, NORMAL, OVERWEIGHT, OBESE }

/** Pure health metric language; Android labels are presentation mappings. */
enum class HealthType(
    val unit: String,
    val normalMin: Double,
    val normalMax: Double,
    val normalSecMin: Double? = null,
    val normalSecMax: Double? = null,
    val trendThreshold: Double = 0.5,
) {
    BLOOD_PRESSURE("mmHg", 90.0, 120.0, 60.0, 80.0, 3.0),
    BLOOD_GLUCOSE("mmol/L", 3.9, 6.1, trendThreshold = 0.3),
    WEIGHT("kg", 0.0, Double.MAX_VALUE, trendThreshold = 0.5),
    BODY_FAT("%", 5.0, 45.0, trendThreshold = 1.0),
    HEART_RATE("bpm", 60.0, 100.0, trendThreshold = 3.0),
    TEMPERATURE("°C", 36.1, 37.3, trendThreshold = 0.2),
    SPO2("%", 95.0, 100.0, trendThreshold = 1.0),
    ;

    fun isNormal(value: Double): Boolean = value in normalMin..normalMax

    fun formatValue(value: Double, secondaryValue: Double?): String = when (this) {
        BLOOD_PRESSURE -> if (secondaryValue != null) {
            "${value.toInt()}/${secondaryValue.toInt()} $unit"
        } else {
            "${value.toInt()} $unit"
        }
        TEMPERATURE, BLOOD_GLUCOSE, WEIGHT, BODY_FAT -> "%.1f %s".format(value, unit)
        else -> "${value.toInt()} $unit"
    }

    companion object {
        fun fromName(name: String): HealthType = entries.firstOrNull { it.name == name } ?: BLOOD_PRESSURE

        fun classifyBloodPressure(systolic: Double, diastolic: Double): BloodPressureClassification = when {
            systolic < 90 || diastolic < 60 -> BloodPressureClassification.LOW
            systolic < 120 && diastolic < 80 -> BloodPressureClassification.NORMAL
            systolic < 130 && diastolic < 80 -> BloodPressureClassification.ELEVATED
            systolic < 140 || diastolic < 90 -> BloodPressureClassification.STAGE_1
            systolic < 180 || diastolic < 120 -> BloodPressureClassification.STAGE_2
            else -> BloodPressureClassification.CRISIS
        }

        fun calculateBmi(weightKg: Double, heightCm: Double): Double? {
            if (heightCm <= 0) return null
            val heightM = heightCm / 100.0
            return weightKg / (heightM * heightM)
        }

        fun classifyBmi(bmi: Double): BmiClassification = when {
            bmi < 18.5 -> BmiClassification.UNDERWEIGHT
            bmi < 24.0 -> BmiClassification.NORMAL
            bmi < 28.0 -> BmiClassification.OVERWEIGHT
            else -> BmiClassification.OBESE
        }
    }
}
