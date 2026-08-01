package com.driezy.medlog.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import com.driezy.medlog.R

/**
 * The canonical Anshin mark, rendered from the same resources as the adaptive
 * launcher icon. This keeps splash and in-app brand moments visually identical.
 */
@Composable
fun AnshinBrandMark(modifier: Modifier = Modifier, contentDescription: String? = null) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 28),
        color = colorResource(R.color.brand_icon_background),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
