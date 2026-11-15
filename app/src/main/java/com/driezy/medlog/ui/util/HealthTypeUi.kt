package com.driezy.medlog.ui.util

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

@Composable
fun BloodPressureClassification.tintColor(): Color = when (this) {
    BloodPressureClassification.LOW -> MaterialTheme.colorScheme.tertiary
    BloodPressureClassification.NORMAL -> MaterialTheme.colorScheme.primary
    BloodPressureClassification.ELEVATED -> MaterialTheme.colorScheme.secondary
    BloodPressureClassification.STAGE_1,
    BloodPressureClassification.STAGE_2,
    BloodPressureClassification.CRISIS,
    -> MaterialTheme.colorScheme.error
}

@Composable
fun BloodPressureClassBadge(classification: BloodPressureClassification, modifier: Modifier = Modifier) {
    val badgeColor = classification.tintColor()
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = badgeColor.copy(alpha = 0.15f),
    ) {
        Text(
            text = stringResource(classification.labelRes),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = badgeColor,
        )
    }
}

@get:StringRes
val BmiClassification.labelRes: Int
    get() = when (this) {
        BmiClassification.UNDERWEIGHT -> R.string.health_bmi_underweight
        BmiClassification.NORMAL -> R.string.health_bmi_normal
        BmiClassification.OVERWEIGHT -> R.string.health_bmi_overweight
        BmiClassification.OBESE -> R.string.health_bmi_obese
    }
