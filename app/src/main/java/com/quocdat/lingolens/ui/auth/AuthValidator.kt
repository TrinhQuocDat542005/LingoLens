package com.quocdat.lingolens.ui.auth

object AuthValidator {
    private val emailPattern = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

    fun login(email: String, password: String): String? = when {
        !emailPattern.matches(email.trim()) -> "Email không hợp lệ."
        password.length < 8 -> "Mật khẩu phải có ít nhất 8 ký tự."
        else -> null
    }

    fun registration(name: String, email: String, password: String, confirmation: String): String? = when {
        name.trim().isEmpty() -> "Vui lòng nhập họ tên."
        login(email, password) != null -> login(email, password)
        password != confirmation -> "Mật khẩu xác nhận không khớp."
        else -> null
    }
}
