package com.driezy.medlog.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.TextUnit
import com.driezy.medlog.R

/**
 * A Composable that displays a Material Symbol from the local variable font,
 * supporting fluid variations in weight and fill.
 *
 * @param iconHex The hexadecimal unicode of the symbol (e.g. "f60a" for qr_code_scanner).
 * @param modifier The modifier to be applied to the text.
 * @param weight The font weight variation axis (typically 100f to 700f).
 * @param fill The fill variation axis (0f for outline, 1f for filled).
 * @param color The tint color of the symbol.
 * @param size The size of the symbol text.
 */
@Composable
fun MaterialSymbolIcon(
    iconHex: String,
    modifier: Modifier = Modifier,
    weight: Float = 400f,
    fill: Float = 0f,
    color: Color = Color.Unspecified,
    size: TextUnit = TextUnit.Unspecified,
) {
    val fontFamily = remember(weight, fill) {
        FontFamily(
            Font(
                resId = R.font.material_symbols_rounded,
                variationSettings = FontVariation.Settings(
                    FontVariation.Setting("wght", weight),
                    FontVariation.Setting("FILL", fill),
                ),
            ),
        )
    }

    val iconText = remember(iconHex) {
        val codePoint = iconHex.toInt(16)
        String(Character.toChars(codePoint))
    }

    Text(
        text = iconText,
        fontFamily = fontFamily,
        color = color,
        fontSize = size,
        modifier = modifier,
    )
}
