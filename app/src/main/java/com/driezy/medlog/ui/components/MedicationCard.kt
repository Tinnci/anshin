package com.driezy.medlog.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.data.model.TimePeriod
import com.driezy.medlog.feature.medications.home.MedicationWithStatus
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.util.displayName
import com.driezy.medlog.ui.util.formIcon
import com.driezy.medlog.ui.util.formatDose
import com.driezy.medlog.ui.util.icon
import com.driezy.medlog.ui.util.labelRes
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedicationCard(
    item: MedicationWithStatus,
    onToggleTaken: () -> Unit,
    onSkip: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** true = 内嵌在父卡片中，去掉外圆角并使用透明背景 */
    flatStyle: Boolean = false,
    /** 部分服用回调；传入实际剂量, null 表示不展示该功能 */
    onPartialTake: ((Double) -> Unit)? = null,
) {
    val med = item.medication
    val medDisplayName = remember(med.name) { med.displayName() }
    val motionScheme = MaterialTheme.motionScheme

    // 部分服用对话框状态
    var showPartialDialog by remember { mutableStateOf(false) }
    var partialInput by remember { mutableStateOf("") }

    // 卡片底色：未服 → primaryContainer（需要行动，视觉突出），已服 → surfaceContainerLowest（弱化），跳过 → surfaceContainerHigh，部分 → secondaryContainer
    val containerColor by animateColorAsState(
        targetValue = when {
            item.isTaken -> MaterialTheme.colorScheme.surfaceContainerLowest
            item.isSkipped -> MaterialTheme.colorScheme.surfaceContainerHigh
            item.isPartial -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "cardColor",
    )

    // 已服/跳过/部分服用后整张卡片透明度降低，减弱视觉权重；未服保持完全不透明
    val cardAlpha by animateFloatAsState(
        targetValue = if (item.isHandled) 0.60f else 1f,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "cardAlpha",
    )

    // 左侧色带颜色：未服一律显示 primary（强调待服），已服/跳过=outlineVariant（弱化），部分=secondary
    val stripColor by animateColorAsState(
        targetValue = when {
            item.isTaken || item.isSkipped -> MaterialTheme.colorScheme.outlineVariant
            item.isPartial -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "strip",
    )

    val cardShape = if (flatStyle) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp)
    // 高优先级且未完成：error 色描边，在 primaryContainer 背景上清晰可见
    val borderMod = if (med.isHighPriority && !item.isHandled && !flatStyle) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f), cardShape)
    } else {
        Modifier
    }

    // 扁平卡片（elevation = 0），flatStyle 下背景透明继承父卡片
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(borderMod)
            .graphicsLayer { alpha = cardAlpha }
            .clickable(onClick = onClick),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (flatStyle) Color.Transparent else containerColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        // IntrinsicSize.Min 使左侧色带高度与主内容对齐
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // ── 左侧色带 ─────────────────────────────────────
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .fillMaxHeight()
                    .background(stripColor),
            )

            // ── 主内容区 ──────────────────────────────────────
            Row(
                modifier = Modifier.padding(horizontal = MedLogSpacing.Large, vertical = MedLogSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedStatusCircle(
                    isTaken = item.isTaken,
                    isSkipped = item.isSkipped,
                    isPartial = item.isPartial,
                )
                Spacer(Modifier.width(MedLogSpacing.Medium))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    ) {
                        // ── 剂型图标（与添加界面一致）─────────────────
                        MedLogIcon(
                            icon = formIcon(med.form),
                            contentDescription = med.form,
                            modifier = Modifier.size(16.dp),
                            tint = if (item.isTaken || item.isSkipped) {
                                MaterialTheme.colorScheme.outlineVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                        Text(
                            text = medDisplayName,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleMedium.copy(
                                textDecoration = if (item.isTaken) {
                                    TextDecoration.LineThrough
                                } else {
                                    TextDecoration.None
                                },
                            ),
                            maxLines = 3,
                            overflow = TextOverflow.Clip,
                        )
                        if (med.isHighPriority) {
                            MedLogIcon(
                                MedLogIcons.PriorityHigh,
                                contentDescription = stringResource(R.string.med_card_high_priority_cd),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        if (med.isPRN) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text("PRN", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    // ── 分类 & 中成药标签行 ─────────────────────────────
                    if (med.category.isNotBlank() || med.isTcm) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 2.dp),
                        ) {
                            if (med.isTcm) {
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            stringResource(R.string.med_card_tcm_label),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                    icon = {
                                        MedLogIcon(
                                            MedLogIcons.LocalFlorist,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    },
                                    modifier = Modifier.height(24.dp),
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        iconContentColor = MaterialTheme.colorScheme.tertiary,
                                    ),
                                )
                            } else if (med.category.isNotBlank()) {
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            med.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    modifier = Modifier.height(24.dp).widthIn(max = 120.dp),
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val period = TimePeriod.fromKey(med.timePeriod)
                        MedLogIcon(
                            period.icon,
                            null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val timeText = if (med.timePeriod == "exact") {
                            med.reminderTimes.split(",").firstOrNull()
                                ?: "%02d:%02d".format(med.reminderHour, med.reminderMinute)
                        } else {
                            stringResource(period.labelRes)
                        }
                        val doseDisplay = "${med.doseQuantity.formatDose()} ${med.doseUnit}"
                        Text(
                            text = "$doseDisplay  ·  $timeText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val actualTakenTimeMs = item.log?.actualTakenTimeMs
                    if (item.isTaken && actualTakenTimeMs != null) {
                        val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                        Text(
                            text = stringResource(
                                R.string.med_card_taken_at,
                                timeFmt.format(Date(actualTakenTimeMs)),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (item.isPartial) {
                        val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                        val qty = item.log?.actualDoseQuantity
                        val timeStr = item.log?.actualTakenTimeMs?.let { timeFmt.format(Date(it)) }
                        Text(
                            text = stringResource(
                                R.string.med_card_partial_taken,
                                qty?.let { it.formatDose() } ?: "",
                                med.doseUnit,
                                timeStr ?: "",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (item.isSkipped) {
                        Text(
                            stringResource(R.string.med_card_skipped_today),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    val stock = med.stock
                    val refillThreshold = med.refillThreshold
                    if (stock != null && refillThreshold != null && stock <= refillThreshold) {
                        Spacer(Modifier.height(MedLogSpacing.Tiny))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                MedLogIcon(
                                    MedLogIcons.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    stringResource(
                                        R.string.med_card_low_stock,
                                        stock.toInt().toString(),
                                        med.doseUnit,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }

                // ── 胶囊形操作按钮组 ──────────────────────────────────
                Spacer(Modifier.width(8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    // 主操作：服用 / 撤销（pill shape，有图标+文字）
                    FilledTonalButton(
                        onClick = onToggleTaken,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = when {
                                item.isTaken -> MaterialTheme.colorScheme.tertiaryContainer
                                item.isSkipped -> MaterialTheme.colorScheme.secondaryContainer
                                item.isPartial -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                            contentColor = when {
                                item.isTaken -> MaterialTheme.colorScheme.onTertiaryContainer
                                item.isSkipped -> MaterialTheme.colorScheme.onSecondaryContainer
                                item.isPartial -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) {
                        MedLogIcon(
                            icon = if (item.isHandled) {
                                MedLogIcons.Undo
                            } else {
                                MedLogIcons.Check
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = when {
                                item.isHandled -> stringResource(R.string.home_snackbar_undo)
                                else -> stringResource(R.string.med_card_btn_take)
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    // 跳过按钮（仅待服状态时显示）
                    if (!item.isHandled) {
                        OutlinedButton(
                            onClick = onSkip,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 32.dp),
                        ) {
                            MedLogIcon(MedLogIcons.SkipNext, null, Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                stringResource(R.string.notif_action_skip),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }

                    // 部分服用按钮（仅待服状态且相应回调已提供时显示）
                    if (!item.isHandled && onPartialTake != null) {
                        OutlinedButton(
                            onClick = {
                                partialInput = ""
                                showPartialDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 32.dp),
                        ) {
                            MedLogIcon(MedLogIcons.Adjust, null, Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                stringResource(R.string.med_card_btn_partial),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }

    // 部分服用对话框
    if (showPartialDialog && onPartialTake != null) {
        AlertDialog(
            onDismissRequest = { showPartialDialog = false },
            title = { Text(stringResource(R.string.med_card_btn_partial)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(
                            R.string.med_card_partial_input_hint,
                            med.doseQuantity.formatDose(),
                            med.doseUnit,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = partialInput,
                        onValueChange = { partialInput = it },
                        singleLine = true,
                        suffix = { Text(med.doseUnit) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val qty = partialInput.toDoubleOrNull()
                        if (qty != null && qty > 0) {
                            onPartialTake(qty.coerceAtMost(med.doseQuantity))
                            showPartialDialog = false
                        }
                    },
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPartialDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedStatusCircle(isTaken: Boolean, isSkipped: Boolean, isPartial: Boolean = false) {
    val motionScheme = MaterialTheme.motionScheme
    // Keep the confirmation pulse short and contained; the color/status carries the meaning.
    val scale = remember { Animatable(if (isTaken) 1f else 0.9f) }
    LaunchedEffect(isTaken, isPartial) {
        if (isTaken || isPartial) {
            scale.animateTo(1.10f, animationSpec = motionScheme.fastSpatialSpec())
            scale.animateTo(1.00f, animationSpec = motionScheme.defaultSpatialSpec())
        } else {
            scale.animateTo(0.9f, animationSpec = motionScheme.defaultSpatialSpec())
        }
    }
    val bgColor by animateColorAsState(
        targetValue = when {
            isTaken -> MaterialTheme.colorScheme.primary
            isPartial -> MaterialTheme.colorScheme.secondary
            isSkipped -> MaterialTheme.colorScheme.outlineVariant
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "circleBg",
    )
    Box(
        modifier = Modifier
            .size(36.dp)
            .scale(scale.value)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isTaken -> MedLogIcon(
                MedLogIcons.Check,
                null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
            isPartial -> MedLogIcon(
                MedLogIcons.Adjust,
                null,
                tint = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.size(20.dp),
            )
            isSkipped -> MedLogIcon(
                MedLogIcons.Remove,
                null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
