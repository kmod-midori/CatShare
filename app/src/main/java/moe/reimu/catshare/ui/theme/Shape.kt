package moe.reimu.catshare.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val CatShareShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

val PillShape = RoundedCornerShape(percent = 50)
val TopLargeShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
