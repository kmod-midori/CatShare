package moe.reimu.catshare.ui.navigation

import kotlinx.serialization.Serializable

/**
 * CatShare 类型安全导航路由
 * 使用 Jetpack Navigation Compose 2.9+ 的 @Serializable 路由
 */

@Serializable
object MainRoute

@Serializable
data class ShareRoute(
    val fileUris: List<String> = emptyList(),
    val fileNames: List<String> = emptyList(),
    val fileSizes: List<Long> = emptyList(),
)

@Serializable
object SettingsRoute