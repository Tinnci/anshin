package com.example.medlog.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

/** 剂型 key → Material Icon（共享工具函数，消除各 Screen 重复定义） */
fun formIcon(form: String): ImageVector = when (form) {
    "capsule" -> Icons.Rounded.Science
    "liquid"  -> Icons.Rounded.LocalDrink
    "powder"  -> Icons.Rounded.WaterDrop
    else      -> Icons.Rounded.Medication  // tablet + 默认
}
