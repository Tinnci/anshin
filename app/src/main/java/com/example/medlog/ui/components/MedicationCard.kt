package com.example.medlog.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.medlog.data.model.TimePeriod
import com.example.medlog.ui.screen.home.MedicationWithStatus
import java.text.SimpleDateFormat
import java.util.*

/** 根据药品剂型返回与添加界面一致的 Material Icon */
private fun formIcon(form: String): ImageVector = when (form) {
    "capsule" -> Icons.Rounded.Science
    "liquid"  -> Icons.Rounded.LocalDrink
    "powder"  -> Icons.Rounded.WaterDrop
    else      -> Icons.Rounded.Medication  // tablet + 默认
}

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
) {
    val med = item.medication
    val motionScheme = MaterialTheme.motionScheme

    // 卡片底色：未服 → primaryContainer（需要行动，视觉突出），已服 → surfaceContainerLowest（弱化），跳过 → surfaceContainerHigh
    val containerColor by animateColorAsState(
        targetValue = when {
            item.isTaken   -> MaterialTheme.colorScheme.surfaceContainerLowest
            item.isSkipped -> MaterialTheme.colorScheme.surfaceContainerHigh
            else           -> MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "cardColor",
    )

    // 已服/跳过后整张卡片透明度降低，减弱视觉权重；未服保持完全不透明
    val cardAlpha by animateFloatAsState(
        targetValue = if (item.isTaken || item.isSkipped) 0.60f else 1f,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "cardAlpha",
    )

    // 左侧色带颜色：未服一律显示 primary（强调待服），已服/跳过=outlineVariant（弱化）
    val stripColor by animateColorAsState(
        targetValue = when {
            item.isTaken || item.isSkipped -> MaterialTheme.colorScheme.outlineVariant
            else                           -> MaterialTheme.colorScheme.primary
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "strip",
    )

    val cardShape = if (flatStyle) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp)
    // 高优先级且未完成：error 色描边，在 primaryContainer 背景上清晰可见
    val borderMod = if (med.isHighPriority && !item.isTaken && !item.isSkipped && !flatStyle)
        Modifier.border(2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f), cardShape)
    else Modifier

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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedStatusCircle(isTaken = item.isTaken, isSkipped = item.isSkipped)
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // ── 剂型图标（与添加界面一致）─────────────────
                        Icon(
                            imageVector = formIcon(med.form),
                            contentDescription = med.form,
                            modifier = Modifier.size(15.dp),
                            tint = if (item.isTaken || item.isSkipped)
                                MaterialTheme.colorScheme.outlineVariant
                            else
                                MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = med.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                textDecoration = if (item.isTaken) TextDecoration.LineThrough
                                                 else TextDecoration.None,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (med.isHighPriority) {
                            Icon(
                                Icons.Rounded.PriorityHigh,
                                contentDescription = "高优先级",
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
                                            "中成药",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Rounded.LocalFlorist,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    },
                                    modifier = Modifier.height(22.dp),
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
                                    modifier = Modifier.height(22.dp).widthIn(max = 120.dp),
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val period = TimePeriod.fromKey(med.timePeriod)
                        Icon(
                            period.icon, null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val timeText = if (med.timePeriod == "exact") {
                            med.reminderTimes.split(",").firstOrNull()
                                ?: "%02d:%02d".format(med.reminderHour, med.reminderMinute)
                        } else period.label
                        val doseDisplay = med.doseQuantity.let {
                            if (it == it.toLong().toDouble()) "${it.toLong()} ${med.doseUnit}"
                            else "%.1f ${med.doseUnit}".format(it)
                        }
                        Text(
                            text = "$doseDisplay  ·  $timeText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.isTaken && item.log?.actualTakenTimeMs != null) {
                        Text(
                            text = "✓ 已于 ${SimpleDateFormat("HH:mm", Locale.getDefault())
                                .format(Date(item.log.actualTakenTimeMs))} 服用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (item.isSkipped) {
                        Text(
                            "今日已跳过",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (med.stock != null && med.refillThreshold != null && med.stock <= med.refillThreshold) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Warning, null,
                                Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "库存不足（剩余 ${med.stock.toInt()} ${med.doseUnit}）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                // ── 胶囊形操作按钮组 ──────────────────────────────────
                Spacer(Modifier.width(6.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    // 主操作：服用 / 撤销（pill shape，有图标+文字）
                    FilledTonalButton(
                        onClick = onToggleTaken,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = when {
                                item.isTaken   -> MaterialTheme.colorScheme.tertiaryContainer
                                item.isSkipped -> MaterialTheme.colorScheme.secondaryContainer
                                else           -> MaterialTheme.colorScheme.primaryContainer
                            },
                            contentColor = when {
                                item.isTaken   -> MaterialTheme.colorScheme.onTertiaryContainer
                                item.isSkipped -> MaterialTheme.colorScheme.onSecondaryContainer
                                else           -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Icon(
                            imageVector = if (item.isTaken || item.isSkipped)
                                Icons.AutoMirrored.Rounded.Undo
                            else
                                Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = when {
                                item.isTaken   -> "撤销"
                                item.isSkipped -> "撤销"
                                else           -> "服用"
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    // 跳过按钮（仅待服状态时显示）
                    if (!item.isTaken && !item.isSkipped) {
                        OutlinedButton(
                            onClick = onSkip,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp),
                        ) {
                            Icon(Icons.Rounded.SkipNext, null, Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("跳过", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

/** 带弹性缩放的状态圆圈：标记服用时触发 overshoot bounce */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedStatusCircle(isTaken: Boolean, isSkipped: Boolean) {
    val motionScheme = MaterialTheme.motionScheme
    // 用 Animatable 实现自定义弹性序列：未服 → 0.9，服用 → 超调 1.25 → 稳定 1.0
    val scale = remember { Animatable(if (isTaken) 1f else 0.9f) }
    LaunchedEffect(isTaken) {
        if (isTaken) {
            scale.animateTo(1.25f, animationSpec = spring(dampingRatio = 0.30f, stiffness = 700f))
            scale.animateTo(1.00f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f))
        } else {
            scale.animateTo(0.9f, animationSpec = motionScheme.defaultEffectsSpec())
        }
    }
    val bgColor by animateColorAsState(
        targetValue = when {
            isTaken   -> MaterialTheme.colorScheme.primary
            isSkipped -> MaterialTheme.colorScheme.outlineVariant
            else      -> MaterialTheme.colorScheme.primaryContainer
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
            isTaken   -> Icon(
                Icons.Rounded.Check, null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
            isSkipped -> Icon(
                Icons.Rounded.Remove, null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

