package dev.hakurei.cameraapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.hakurei.cameraapp.ui.AppViewModelProvider
import dev.hakurei.cameraapp.ui.navigation.Screen
import dev.hakurei.cameraapp.ui.screens.camera.CameraScreen
import dev.hakurei.cameraapp.ui.screens.camera.CameraViewModel
import dev.hakurei.cameraapp.ui.screens.gallery.GalleryScreen
import dev.hakurei.cameraapp.ui.screens.gallery.GalleryViewModel
import dev.hakurei.cameraapp.ui.screens.viewer.ViewerScreen
import dev.hakurei.cameraapp.ui.theme.CameraappTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CameraappTheme {
                val navController = rememberNavController()

                val galleryViewModel: GalleryViewModel = viewModel(factory = AppViewModelProvider.Factory)

                NavHost(
                    navController = navController,
                    startDestination = Screen.Camera.route,
                    modifier = Modifier.fillMaxSize()
                ) {

                    composable(Screen.Camera.route) {
                        val cameraViewModel: CameraViewModel = viewModel(factory = AppViewModelProvider.Factory)
                        CameraScreen(
                            viewModel = cameraViewModel,
                            onGalleryClick = { navController.navigate(Screen.Gallery.route) }
                        )
                    }

                    composable(
                        route = Screen.Gallery.route,
                        enterTransition = { fadeIn(tween(300)) },
                        exitTransition = { fadeOut(tween(300)) }
                    ) {
                        GalleryScreen(
                            viewModel = galleryViewModel,
                            onImageClick = { mediaId ->
                                navController.navigate(Screen.Viewer.createRoute(mediaId))
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = Screen.Viewer.route,
                        arguments = listOf(navArgument("mediaId") { type = NavType.LongType }),
                        enterTransition = {
                            scaleIn(initialScale = 0.8f, animationSpec = tween(300)) +
                                    fadeIn(animationSpec = tween(300))
                        },
                        exitTransition = {
                            scaleOut(targetScale = 0.8f, animationSpec = tween(300)) +
                                    fadeOut(animationSpec = tween(300))
                        }
                    ) { backStackEntry ->
                        val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: 0L
                        ViewerScreen(
                            viewModel = galleryViewModel,
                            initialMediaId = mediaId,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}