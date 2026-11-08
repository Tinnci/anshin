package com.driezy.medlog.capability.bpx1

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Runtime permissions needed to scan for and talk to the BPX1 monitor. */
fun requiredBpx1Permissions(): List<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
    )
    else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

fun missingBpx1Permissions(context: Context): List<String> = requiredBpx1Permissions().filter { permission ->
    ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
}
