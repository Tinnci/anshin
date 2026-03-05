package com.example.medlog.ui.util

/**
 * 将 Double 格式化为剂量显示：整数省略小数点，否则保留 1 位小数。
 * 例：2.0 → "2", 1.5 → "1.5"
 */
fun Double.formatDose(): String =
    if (this == toLong().toDouble()) toLong().toString()
    else "%.1f".format(this)

/**
 * 将 Double 格式化为高精度剂量显示：整数省略小数点，否则保留最多 2 位小数并裁剪尾零。
 * 例：2.0 → "2", 1.5 → "1.5", 0.25 → "0.25"
 */
fun Double.formatDosePrecise(): String =
    if (this == toLong().toDouble()) toLong().toString()
    else "%.2f".format(this).trimEnd('0').trimEnd('.')
