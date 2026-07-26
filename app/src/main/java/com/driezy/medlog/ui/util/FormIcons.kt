package com.driezy.medlog.ui.util

import com.driezy.medlog.ui.icons.MedLogIcons

/** 剂型 key → Material Icon（共享工具函数，消除各 Screen 重复定义） */
fun formIcon(form: String): Int = when (form) {
    "capsule" -> MedLogIcons.Science
    "liquid" -> MedLogIcons.LocalDrink
    "powder" -> MedLogIcons.WaterDrop
    else -> MedLogIcons.Medication // tablet + 默认
}
