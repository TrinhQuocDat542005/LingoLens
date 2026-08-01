package com.quocdat.lingolens.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object Camera : Screen("camera")
    object Result : Screen("result/{word}") {
        fun createRoute(word: String) = "result/$word"
    }
    object MyWords : Screen("my_words")
    object Stats : Screen("stats")
    object Settings : Screen("settings")
}
