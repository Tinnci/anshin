package com.driezy.medlog.feature.medications.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.theme.emphasizedTypography
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── 低库存警告 banner ─────────────────────────────────────────────────────────

private const val LOW_STOCK_VISIBLE_LIMIT = 3

internal data class LowStockItemPresentation(val name: String, val stock: Double, val unit: String)

internal data class LowStockPresentation(val visibleItems: List<LowStockItemPresentation>, val hiddenCount: Int) {
    companion object {
        fun from(
            medications: List<Pair<String, Pair<Double, String>>>,
            visibleLimit: Int = LOW_STOCK_VISIBLE_LIMIT,
        ): LowStockPresentation {
            val collapsed = medications
                .groupBy { (name, stockPair) -> name.trim() to stockPair.second }
                .map { (key, entries) ->
                    val lowestStock = entries.minOf { it.second.first }
                    LowStockItemPresentation(
                        name = key.first,
                        stock = lowestStock,
                        unit = key.second,
                    )
                }
                .sortedWith(compareBy<LowStockItemPresentation> { it.stock }.thenBy { it.name })
            return LowStockPresentation(
                visibleItems = collapsed.take(visibleLimit),
                hiddenCount = (collapsed.size - visibleLimit).coerceAtLeast(0),
            )
        }
    }
}

@Composable
internal fun LowStockBanner(medications: List<Pair<String, Pair<Double, String>>>, modifier: Modifier = Modifier) {
    val presentation = remember(medications) { LowStockPresentation.from(medications) }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MedLogSpacing.Large, vertical = MedLogSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                MedLogIcon(
                    icon = MedLogIcons.Warning,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_low_stock_title),
                    style = MaterialTheme.emphasizedTypography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(4.dp))
                presentation.visibleItems.forEach { item ->
                    Text(
                        text = stringResource(
                            R.string.home_low_stock_item,
                            item.name,
                            item.stock.toString(),
                            item.unit,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.90f),
                    )
                }
                if (presentation.hiddenCount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.home_low_stock_more,
                            presentation.hiddenCount,
                            presentation.hiddenCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.78f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun todayDateString(): String {
    val pattern = stringResource(R.string.date_format_day_label)
    return remember(pattern) { DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(LocalDate.now()) }
}
