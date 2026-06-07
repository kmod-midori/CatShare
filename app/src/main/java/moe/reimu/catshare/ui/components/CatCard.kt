package moe.reimu.catshare.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import moe.reimu.catshare.ui.theme.CatShareShapes

enum class CatCardVariant { Elevated, Filled, Outlined, Clickable }

@Composable
fun CatCard(
    modifier: Modifier = Modifier,
    variant: CatCardVariant = CatCardVariant.Filled,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickMod = if (onClick != null) {
        Modifier.clickable(
            enabled = enabled,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    when (variant) {
        CatCardVariant.Elevated -> {
            ElevatedCard(
                modifier = modifier.fillMaxWidth().then(clickMod),
                shape = CatShareShapes.medium,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
                    content()
                }
            }
        }

        CatCardVariant.Filled -> {
            Card(
                modifier = modifier.fillMaxWidth().then(clickMod),
                shape = CatShareShapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
                    content()
                }
            }
        }

        CatCardVariant.Outlined -> {
            OutlinedCard(
                modifier = modifier.fillMaxWidth().then(clickMod),
                shape = CatShareShapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
                    content()
                }
            }
        }

        CatCardVariant.Clickable -> {
            var pressed by remember { mutableStateOf(false) }
            val targetContainer: Color by animateColorAsState(
                if (pressed) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "card_color",
            )
            Card(
                modifier = modifier.fillMaxWidth().clickable(
                    enabled = enabled,
                    onClick = {
                        pressed = true
                        onClick?.invoke()
                    },
                ),
                shape = CatShareShapes.medium,
                colors = CardDefaults.cardColors(containerColor = targetContainer),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(contentPadding)
                        .animateContentSize(
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                        ),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun CatHeroCard(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ElevatedCard(
        modifier = modifier.fillMaxWidth().then(clickMod),
        shape = CatShareShapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = contentColorFor(MaterialTheme.colorScheme.primaryContainer),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            if (icon != null) {
                Surface(
                    shape = CatShareShapes.medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        icon()
                    }
                }
            }
            androidx.compose.material3.Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (subtitle != null) {
                androidx.compose.material3.Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
