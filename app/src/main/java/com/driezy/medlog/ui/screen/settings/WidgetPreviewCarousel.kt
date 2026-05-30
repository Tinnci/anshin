package com.driezy.medlog.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ai.CloudAiEndpointPreset
import com.driezy.medlog.ai.CloudAiEndpointProtocol
import com.driezy.medlog.data.repository.AiUsageSummaryRow
import com.driezy.medlog.data.repository.AppTextScale
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.FontMode
import com.driezy.medlog.data.repository.OpenAiCompatibleCloudAuthMode
import com.driezy.medlog.data.repository.UiDensityScale
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.theme.ThemePalette


internal data class WidgetCarouselItem(
    val previewType: WidgetPreviewType,
    val name: String,
    val description: String,
    val sizes: List<String>,
    val canPin: Boolean,
    val showActions: Boolean = true,
    val onAdd: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetPreviewCarousel(items: List<WidgetCarouselItem>) {
    val carouselState = rememberCarouselState { items.size }

    HorizontalCenteredHeroCarousel(
        state = carouselState,
        modifier = Modifier
            .fillMaxWidth()
            .height(296.dp),
        itemSpacing = MedLogSpacing.Small,
        maxItemWidth = 320.dp,
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) { index ->
        val item = items[index]
        WidgetPickerCard(
            previewType = item.previewType,
            name = item.name,
            description = item.description,
            sizes = item.sizes,
            canPin = item.canPin,
            showActions = item.showActions,
            modifier = Modifier
                .fillMaxHeight()
                .maskClip(RoundedCornerShape(24.dp)),
            onAdd = item.onAdd,
        )
    }
}
