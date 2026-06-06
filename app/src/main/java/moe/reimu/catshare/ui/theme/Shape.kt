package moe.reimu.catshare.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ============================================================
// CatShare 形状系统 — Material 3 Expressive 风格
// 更大胆的圆角，分层次表达不同组件
// ============================================================

val CatShareShapes = Shapes(
    // ── 小号 (extraSmall) — Chip / 小标签 ──
    extraSmall = RoundedCornerShape(6.dp),

    // ── 小号 (small) — Button / TextField / 小卡片 ──
    small = RoundedCornerShape(12.dp),

    // ── 中号 (medium) — Card / Dialog / BottomSheet ──
    medium = RoundedCornerShape(20.dp),

    // ── 大号 (large) — 大卡片 / 全屏容器 ──
    large = RoundedCornerShape(28.dp),

    // ── 超大号 (extraLarge) — Hero 组件 / 首屏主卡片 ──
    extraLarge = RoundedCornerShape(36.dp)
)