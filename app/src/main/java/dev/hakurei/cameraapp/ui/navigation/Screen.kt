package dev.hakurei.cameraapp.ui.navigation

sealed class Screen(val route: String) {
    data object Camera : Screen("camera")

    data object Gallery : Screen("gallery")

    data object Viewer : Screen("viewer/{mediaId}") {
        fun createRoute(mediaId: Long) = "viewer/$mediaId"
    }
}