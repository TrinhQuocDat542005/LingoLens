package com.quocdat.lingolens

import com.quocdat.lingolens.ui.auth.AuthValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidatorTest {
    @Test fun validLoginPasses() = assertNull(AuthValidator.login("dat@example.com", "Secure123"))
    @Test fun invalidEmailFails() = assertEquals("Email không hợp lệ.", AuthValidator.login("bad-email", "Secure123"))
    @Test fun shortPasswordFails() = assertEquals("Mật khẩu phải có ít nhất 8 ký tự.", AuthValidator.login("dat@example.com", "short"))
    @Test fun confirmationMustMatch() = assertEquals(
        "Mật khẩu xác nhận không khớp.",
        AuthValidator.registration("Đạt", "dat@example.com", "Secure123", "Secure456")
    )
}
