package com.quocdat.lingolens.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object Camera : Screen("camera")
    object Result : Screen("result?word={word}&imageUri={imageUri}") {
        fun createRoute(word: String = "cat", imageUri: String? = null): String = buildString {
            append("result?word=")
            append(Uri.encode(word))
            imageUri?.let {
                append("&imageUri=")
                append(Uri.encode(it))
            }
        }
    }
    object MyWords : Screen("my_words")
    object Stats : Screen("stats")
    object Settings : Screen("settings")
}
