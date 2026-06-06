package moe.reimu.catshare.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ============================================================
// CatShare 卡片组件库
// 支持：elevated / filled / outlined / interactive 四种变体
// ============================================================

/**
 * 卡片样式枚举
 */
enum class CatCardStyle {
    /** 带阴影的浮起卡片 */
    Elevated,
    /** 填充背景色的卡片 */
    Filled,
    /** 带边框的描边卡片 */
    Outlined,
}

/**
 * 通用 CatCard 组件
 *
 * @param title 卡片标题
 * @param subtitle 卡片副标题
 * @param icon 前置图标
 * @param style 卡片样式
 * @param trailing 尾部内容（如 Switch、Chevron 等）
 * @param onClick 点击回调（非 null 时卡片可点击）
 */
@Composable
fun CatCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    style: CatCardStyle = CatCardStyle.Filled,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    var pressed by remember { mutableStateOf(false) }

    val scale by animateDpAsState(
        targetValue = if (pressed) (-2).dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "cardPress"
    )

    val containerColor = when (style) {
        CatCardStyle.Elevated -> MaterialTheme.colorScheme.surface
        CatCardStyle.Filled -> MaterialTheme.colorScheme.surfaceVariant
        CatCardStyle.Outlined -> Color.Transparent
    }

    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        onClick = onClick ?: {},
        enabled = onClick != null && enabled,
        modifier = modifier
            .fillMaxWidth()
            .scale(1f + scale.value / 100f),
        shape = CatCardShape(style),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        elevation = when (style) {
            CatCardStyle.Elevated -> CardDefaults.cardElevation(
                defaultElevation = 2.dp,
                pressedElevation = 6.dp,
            )
            else -> CardElevation(0.dp)
        },
        border = when (style) {
            CatCardStyle.Outlined -> CardDefaults.outlinedCardBorder()
            else -> null
        },
        interactionSource = remember { MutableInteractionSource() }
            .also { if (pressed) it }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 前置图标
            if (icon != null) {
                CatIconContainer(
                    icon = icon,
                    tint = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            // 文本区域
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 尾部内容
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 通用卡片容器（无预设结构，完全自定义内容）
 */
@Composable
fun CatCardCustom(
    modifier: Modifier = Modifier,
    style: CatCardStyle = CatCardStyle.Filled,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = CatCardShape(style),
        colors = CardDefaults.cardColors(
            containerColor = when (style) {
                CatCardStyle.Elevated -> MaterialTheme.colorScheme.surface
                CatCardStyle.Filled -> MaterialTheme.colorScheme.surfaceVariant
                CatCardStyle.Outlined -> Color.Transparent
            }
        ),
        elevation = when (style) {
            CatCardStyle.Elevated -> CardDefaults.cardElevation(defaultElevation = 2.dp)
            else -> CardElevation(0.dp)
        },
        border = when (style) {
            CatCardStyle.Outlined -> CardDefaults.outlinedCardBorder()
            else -> null
        },
        content = content
    )
}

/**
 * 图标容器 — 统一的圆形背景 + 图标
 */
@Composable
fun CatIconContainer(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    size: Int = 48,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(MaterialTheme.shapes.small)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.5).dp)
        )
    }
}

/**
 * 根据卡片样式返回对应形状
 */
@Composable
private fun CatCardShape(style: CatCardStyle): Shape {
    return when (style) {
        CatCardStyle.Elevated -> MaterialTheme.shapes.medium
        CatCardStyle.Filled -> MaterialTheme.shapes.large
        CatCardStyle.Outlined -> MaterialTheme.shapes.medium
    }
}

// ============================================================
// 兼容旧代码的 DefaultCard
// ============================================================

@Composable
fun DefaultCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    CatCardCustom(modifier = modifier, style = CatCardStyle.Filled, content = content)
}

@Composable
fun DefaultCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    CatCardCustom(modifier = modifier, style = CatCardStyle.Filled, onClick = onClick, content = content)
}