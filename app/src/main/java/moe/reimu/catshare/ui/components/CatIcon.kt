package moe.reimu.catshare.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class CatIconSize(val size: Dp, val padding: Dp) {
    Small(40.dp, 8.dp),
    Medium(48.dp, 10.dp),
    Large(56.dp, 14.dp),
}

@Composable
fun CatIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: CatIconSize = CatIconSize.Medium,
    containerColor: Color? = null,
    contentColor: Color? = null,
) {
    val actualContainerColor = containerColor ?: MaterialTheme.colorScheme.primaryContainer
    val actualContentColor = contentColor ?: contentColorFor(actualContainerColor)

    Surface(
        modifier = modifier.size(size.size),
        shape = CircleShape,
        color = actualContainerColor,
        contentColor = actualContentColor,
    ) {
        Box(modifier = Modifier.padding(size.padding)) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(size.size - size.padding * 2),
            )
        }
    }
}
