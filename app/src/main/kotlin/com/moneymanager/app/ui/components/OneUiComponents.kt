package com.moneymanager.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moneymanager.app.ui.theme.OneUiTheme
import java.time.LocalDate

val OneUiCardShape = RoundedCornerShape(26.dp)
val OneUiDialogShape = RoundedCornerShape(28.dp)
val OneUiSubCardShape = RoundedCornerShape(18.dp)

/**
 * Samsung One UI Collapsible Scaffold.
 * Features the signature oversized upper viewing zone (Screenshot 5 "Settings")
 * that collapses smoothly into a compact top bar on scroll.
 */
@Composable
fun OneUiCollapsibleScaffold(
    title: String,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    expandedHeight: Dp = 190.dp,
    collapsedHeight: Dp = 56.dp,
    centeredExpandedTitle: Boolean = true,
    floatingBottomBar: @Composable (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { (expandedHeight - collapsedHeight).coerceAtLeast(0.dp).toPx() }
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember(maxOffsetPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (maxOffsetPx <= 0f) return Offset.Zero
                val delta = available.y
                val newOffset = (headerOffsetPx + delta).coerceIn(-maxOffsetPx, 0f)
                val consumed = newOffset - headerOffsetPx
                headerOffsetPx = newOffset
                return Offset(0f, consumed)
            }
        }
    }

    val progress = if (maxOffsetPx > 0f) (-headerOffsetPx / maxOffsetPx).coerceIn(0f, 1f) else 1f
    val currentHeaderHeight = expandedHeight + with(density) { headerOffsetPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OneUiTheme.colors.background)
            .statusBarsPadding()
            .nestedScroll(nestedScrollConnection)
    ) {
        // Scrollable body
        Box(modifier = Modifier.fillMaxSize()) {
            content(PaddingValues(top = currentHeaderHeight))
        }

        // Collapsible One UI Header (pure background, zero borders)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(currentHeaderHeight)
                .background(OneUiTheme.colors.background)
        ) {
            // Expanded Viewing Zone Title (Screenshot 5 "Settings")
            if (progress < 0.9f && maxOffsetPx > 0f) {
                val expandedAlpha = (1f - (progress / 0.75f)).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = collapsedHeight / 2, start = 24.dp, end = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = if (centeredExpandedTitle) Alignment.CenterHorizontally else Alignment.Start
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = OneUiTheme.colors.textPrimary.copy(alpha = expandedAlpha),
                            fontSize = 32.sp
                        ),
                        textAlign = if (centeredExpandedTitle) TextAlign.Center else TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OneUiTheme.colors.textSecondary.copy(alpha = expandedAlpha),
                                fontSize = 13.sp
                            ),
                            textAlign = if (centeredExpandedTitle) TextAlign.Center else TextAlign.Start,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Bottom/Collapsed Action Bar Row (Screenshots 2, 3, 4: "< Title" + actions)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(collapsedHeight)
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (navigationIcon != null) {
                    navigationIcon()
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                }

                val collapsedAlpha = if (maxOffsetPx <= 0f) 1f else ((progress - 0.55f) / 0.45f).coerceIn(0f, 1f)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = OneUiTheme.colors.textPrimary.copy(alpha = collapsedAlpha),
                        fontSize = 20.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )

                actions?.invoke(this)
            }
        }

        // Optional Bottom Floating Element (e.g., Screenshot 5 Floating Search Pill)
        if (floatingBottomBar != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                floatingBottomBar()
            }
        }
    }
}

/**
 * Standard One UI Sub-Screen Top Bar (Screenshots 2, 3, 4: "< Connections", "< Dashboard").
 */
@Composable
fun OneUiSubScreenTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = OneUiTheme.colors.textPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(14.dp))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                color = OneUiTheme.colors.textPrimary,
                fontSize = 20.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp)
        )

        actions?.invoke(this)
    }
}

/**
 * Borderless One UI Squircle Island Container (26.dp radius, #17171A surface on #000000 canvas).
 */
@Composable
fun OneUiCard(
    modifier: Modifier = Modifier,
    shape: Shape = OneUiCardShape,
    containerColor: Color = OneUiTheme.colors.surface,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .clickable(onClick = onClick)
                } else Modifier
            ),
        shape = shape,
        color = containerColor,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
        ) {
            content()
        }
    }
}

/**
 * One UI 9 Gradient Insight Banner Card (Screenshot 4 "Build healthy digital habits").
 */
@Composable
fun OneUiBannerCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val gradientBrush = Brush.horizontalGradient(
        colors = listOf(
            OneUiTheme.colors.secondaryCyan,
            OneUiTheme.colors.primaryAccent
        )
    )

    OneUiCard(
        modifier = modifier,
        containerColor = OneUiTheme.colors.bannerSurface,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = TextStyle(
                    brush = gradientBrush,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center
                )
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = OneUiTheme.colors.textSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}

/**
 * Indented Hairline Divider inside One UI Grouped Cards (Screenshots 2, 4, 5).
 */
@Composable
fun OneUiInsetDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 16.dp,
    endIndent: Dp = 0.dp
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent, end = endIndent),
        color = OneUiTheme.colors.divider,
        thickness = 0.5.dp
    )
}

/**
 * Circular Colored Icon Badge (Screenshot 5: Wi-Fi, Connected devices, Modes, Notifications).
 */
@Composable
fun OneUiIconBadge(
    icon: ImageVector,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Emoji/Text variant of Circular Colored Icon Badge.
 */
@Composable
fun OneUiEmojiBadge(
    emoji: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 17.sp
        )
    }
}

/**
 * One UI Pill Toggle Switch with optional leading vertical divider '|' (Screenshot 4 "Connections").
 */
@Composable
fun OneUiSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showVerticalDivider: Boolean = false
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.5.dp,
        animationSpec = tween(durationMillis = 180),
        label = "OneUiSwitchThumb"
    )
    val trackColor = if (checked) OneUiTheme.colors.primaryAccent else OneUiTheme.colors.switchTrackOff

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showVerticalDivider) {
            Box(
                modifier = Modifier
                    .padding(end = 14.dp)
                    .width(0.8.dp)
                    .height(20.dp)
                    .background(OneUiTheme.colors.divider)
            )
        }

        Box(
            modifier = Modifier
                .width(40.dp)
                .height(22.dp)
                .clip(CircleShape)
                .background(trackColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onCheckedChange(!checked) },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

/**
 * One UI Radio Button.
 */
@Composable
fun OneUiRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ringColor = if (selected) OneUiTheme.colors.primaryAccent else OneUiTheme.colors.textMuted

    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(width = 1.8.dp, color = ringColor, shape = CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(OneUiTheme.colors.primaryAccent)
            )
        }
    }
}

/**
 * Grouped List Item Row matching Screenshot 5 ("Settings") and Screenshot 4 ("Connections").
 */
@Composable
fun OneUiListRow(
    title: String,
    subtitle: String? = null,
    subtitleColor: Color = OneUiTheme.colors.textSecondary,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = OneUiTheme.colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = subtitleColor,
                        fontSize = 12.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailingContent()
        }
    }
}

/**
 * One UI Vertical Colored Accent Bar Row (Screenshot 2 "Most used app categories").
 */
@Composable
fun OneUiVerticalAccentRow(
    title: String,
    subtitle: String? = null,
    valueText: String,
    valueColor: Color = OneUiTheme.colors.textPrimary,
    accentColor: Color,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onClick)
                } else Modifier
            )
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Slender Vertical Colored Pill Bar (3.2.dp wide x 24.dp tall, matching Screenshot 2)
        Box(
            modifier = Modifier
                .width(3.2.dp)
                .height(24.dp)
                .clip(CircleShape)
                .background(accentColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Middle Title & Truncated Subtext
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = OneUiTheme.colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = OneUiTheme.colors.textSecondary,
                        fontSize = 12.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right-aligned Metric / Amount (clean, non-bold font matching Screenshot 2)
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = valueColor,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Normal
            )
        )
    }
}

/**
 * Compact One UI Pill Button (Screenshot 2 "Set timer" capsule button).
 */
@Composable
fun OneUiPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = OneUiTheme.colors.surfaceVariant,
    contentColor: Color = OneUiTheme.colors.textPrimary,
    leadingIcon: ImageVector? = null
) {
    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            )
        }
    }
}

/**
 * One UI Text Action Link (Screenshot 3 "Show categories", Screenshot 2 "View list >").
 */
@Composable
fun OneUiTextActionLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = OneUiTheme.colors.primaryAccent,
    showChevron: Boolean = false
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                color = color,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
        )
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/**
 * One UI Dashboard 7-Day Date Strip (Screenshot 3: "25  26  27  28  (29)  30  31").
 */
@Composable
fun OneUiDateStrip(
    dates: List<LocalDate>,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    leftLabel: String,
    rightActionLabel: String,
    onRightActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            dates.forEach { date ->
                val isSelected = date == selectedDate
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) OneUiTheme.colors.surfaceElevated else Color.Transparent
                        )
                        .clickable { onDateSelected(date) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${date.dayOfMonth}",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = if (isSelected) OneUiTheme.colors.textPrimary else OneUiTheme.colors.textMuted,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 15.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Subheader row ("Today" on left, "Show categories" blue link on right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = leftLabel,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = OneUiTheme.colors.textSecondary,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.5.sp
                )
            )

            OneUiTextActionLink(
                text = rightActionLabel,
                onClick = onRightActionClick
            )
        }
    }
}

/**
 * One UI Floating Search Capsule (Screenshot 1 & Screenshot 5).
 */
@Composable
fun OneUiFloatingSearchPill(
    hint: String = "Search",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = OneUiTheme.colors.surfaceElevated,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = OneUiTheme.colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = OneUiTheme.colors.textSecondary,
                    fontSize = 15.sp
                ),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = OneUiTheme.colors.textSecondary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/**
 * Borderless One UI Capsule Chip (Screenshot 1 "Recently used settings": Auto Blocker, Data saver).
 */
@Composable
fun OneUiCapsuleChip(
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val bgColor = if (selected) OneUiTheme.colors.primaryAccent else OneUiTheme.colors.surfaceElevated
    val textColor = if (selected) Color.White else OneUiTheme.colors.textPrimary

    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = textColor,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 13.5.sp
                )
            )
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(6.dp))
                trailingIcon()
            }
        }
    }
}

/**
 * Section Header inside or above One UI Cards.
 */
@Composable
fun OneUiSectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            color = OneUiTheme.colors.textSecondary,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp
        ),
        modifier = modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
    )
}

/**
 * Borderless One UI Pill Segmented Control.
 */
@Composable
fun <T> OneUiPillSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelProvider: (T) -> String = { it.toString() },
    activeColor: Color = OneUiTheme.colors.primaryAccent
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp),
        shape = CircleShape,
        color = OneUiTheme.colors.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            if (isSelected) OneUiTheme.colors.surfaceElevated else Color.Transparent
                        )
                        .clickable { onItemSelected(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(item),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) activeColor else OneUiTheme.colors.textSecondary,
                            fontSize = 12.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * One UI Progress Track (Screenshot 2 "App timers" thin progress bar).
 */
@Composable
fun OneUiProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.5.dp,
    trackColor: Color = OneUiTheme.colors.surfaceElevated,
    fillColor: Color = OneUiTheme.colors.primaryAccent
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0.02f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "OneUiProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(fillColor)
        )
    }
}

@Deprecated("Use OneUiProgressBar instead", ReplaceWith("OneUiProgressBar(progress, modifier, height, trackColor, fillColor)"))
@Composable
fun OneUiChunkyProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.5.dp,
    trackColor: Color = OneUiTheme.colors.surfaceElevated,
    fillColor: Color = OneUiTheme.colors.primaryAccent
) = OneUiProgressBar(progress, modifier, height, trackColor, fillColor)

/**
 * One UI Tactile Slider.
 */
@Composable
fun OneUiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    activeColor: Color = OneUiTheme.colors.primaryAccent
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val thumbRadius = 9.dp
        val density = LocalDensity.current
        val thumbRadiusPx = with(density) { thumbRadius.toPx() }

        val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
        val normalized = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

        fun updateFromOffset(x: Float) {
            val clamped = (x / widthPx).coerceIn(0f, 1f)
            val computedValue = valueRange.start + clamped * rangeSpan
            onValueChange(computedValue)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(OneUiTheme.colors.surfaceElevated)
                .pointerInput(widthPx) {
                    detectTapGestures { offset -> updateFromOffset(offset.x) }
                }
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        updateFromOffset(change.position.x)
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(normalized)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(activeColor)
            )
        }

        val thumbOffset = ((widthPx - thumbRadiusPx * 2) * normalized).coerceAtLeast(0f)
        Box(
            modifier = Modifier
                .offset(x = with(density) { thumbOffset.toDp() })
                .size(thumbRadius * 2)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.5.dp, activeColor, CircleShape)
        )
    }
}

@Deprecated("Use OneUiSlider instead", ReplaceWith("OneUiSlider(value, onValueChange, modifier, valueRange, activeColor)"))
@Composable
fun OneUiChunkySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    activeColor: Color = OneUiTheme.colors.primaryAccent
) = OneUiSlider(value, onValueChange, modifier, valueRange, activeColor)

/**
 * Borderless Bottom-Aligned One UI 9 Dialog (28.dp rounded corners).
 */
@Composable
fun OneUiBottomDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButtonText: String = "Save",
    onConfirm: () -> Unit,
    dismissButtonText: String = "Cancel",
    confirmButtonColor: Color = OneUiTheme.colors.primaryAccent,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                )
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = OneUiDialogShape,
                color = OneUiTheme.colors.surface,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 19.sp
                            )
                        )
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = OneUiTheme.colors.textSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    content()

                    Spacer(modifier = Modifier.height(20.dp))

                    // One UI Bottom Dialog Pill Buttons (borderless)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onDismissRequest),
                            shape = CircleShape,
                            color = OneUiTheme.colors.surfaceElevated
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = dismissButtonText,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = OneUiTheme.colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp
                                    )
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onConfirm),
                            shape = CircleShape,
                            color = confirmButtonColor
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = confirmButtonText,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
