package com.driezy.medlog.ui.util

import androidx.annotation.StringRes
import com.driezy.medlog.R
import com.driezy.medlog.data.model.BloodPressureClassification
import com.driezy.medlog.data.model.BmiClassification
import com.driezy.medlog.data.model.HealthType

@get:StringRes
val HealthType.labelRes: Int
    get() = when (this) {
        HealthType.BLOOD_PRESSURE -> R.string.health_type_label_blood_pressure
        HealthType.BLOOD_GLUCOSE -> R.string.health_type_label_blood_glucose
        HealthType.WEIGHT -> R.string.health_type_label_weight
        HealthType.BODY_FAT -> R.string.health_type_label_body_fat
        HealthType.HEART_RATE -> R.string.health_type_label_heart_rate
        HealthType.TEMPERATURE -> R.string.health_type_label_temperature
        HealthType.SPO2 -> R.string.health_type_label_spo2
    }

@get:StringRes
val BloodPressureClassification.labelRes: Int
    get() = when (this) {
        BloodPressureClassification.LOW -> R.string.health_bp_class_low
        BloodPressureClassification.NORMAL -> R.string.health_bp_class_normal
        BloodPressureClassification.ELEVATED -> R.string.health_bp_class_elevated
        BloodPressureClassification.STAGE_1 -> R.string.health_bp_class_stage1
        BloodPressureClassification.STAGE_2 -> R.string.health_bp_class_stage2
        BloodPressureClassification.CRISIS -> R.string.health_bp_class_crisis
    }

@get:StringRes
val BmiClassification.labelRes: Int
    get() = when (this) {
        BmiClassification.UNDERWEIGHT -> R.string.health_bmi_underweight
        BmiClassification.NORMAL -> R.string.health_bmi_normal
        BmiClassification.OVERWEIGHT -> R.string.health_bmi_overweight
        BmiClassification.OBESE -> R.string.health_bmi_obese
    }
