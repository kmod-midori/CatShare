package moe.reimu.catshare.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import moe.reimu.catshare.models.FileInfo
import moe.reimu.catshare.ui.screens.MainScreen
import moe.reimu.catshare.ui.screens.SettingsScreen
import moe.reimu.catshare.ui.screens.ShareScreen

object Routes {
    const val MAIN = "main"
    const val SHARE = "share"
    const val SETTINGS = "settings"
}

@Composable
fun CatNavHost(
    modifier: Modifier = Modifier,
    onPickFiles: () -> Unit,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.MAIN,
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(220),
                )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(180)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(180),
                )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(220),
                )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(180)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(180),
                )
        },
    ) {
        composable(Routes.MAIN) {
            MainScreen(
                onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
                onPickFiles = onPickFiles,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        // Share 路由通过 Intent 直接触发 Activity，不纳入 NavHost
        // 因为 ShareActivity 接收 ACTION_SEND / SEND_MULTIPLE
    }
}
