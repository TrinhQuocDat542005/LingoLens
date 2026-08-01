package com.quocdat.lingolens.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("LingoLens", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp)); CircularProgressIndicator()
        }
    }
}

@Composable
fun LoginScreen(viewModel: AuthViewModel, onRegister: () -> Unit) {
    val form by viewModel.form.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    AuthLayout("Chào mừng trở lại", "Đăng nhập để đồng bộ hành trình học của bạn") {
        OutlinedTextField(email, { email = it; viewModel.clearError() }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        PasswordField(password, { password = it; viewModel.clearError() }, visible, { visible = !visible })
        form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button({ viewModel.login(email, password) }, Modifier.fillMaxWidth(), enabled = !form.loading) {
            if (form.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Đăng nhập")
        }
        TextButton(onRegister, Modifier.align(Alignment.CenterHorizontally)) { Text("Chưa có tài khoản? Đăng ký") }
    }
}

@Composable
fun RegisterScreen(viewModel: AuthViewModel, onBack: () -> Unit) {
    val form by viewModel.form.collectAsState()
    var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }; var confirmation by remember { mutableStateOf("") }
    var accepted by remember { mutableStateOf(false) }
    LaunchedEffect(form.registrationComplete) { if (form.registrationComplete) { viewModel.consumeRegistration(); onBack() } }
    AuthLayout("Tạo tài khoản", "Bắt đầu học tiếng Anh từ những vật thể quanh bạn") {
        OutlinedTextField(name, { name = it; viewModel.clearError() }, Modifier.fillMaxWidth(), label = { Text("Họ và tên") }, singleLine = true)
        OutlinedTextField(email, { email = it; viewModel.clearError() }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        PasswordField(password, { password = it; viewModel.clearError() }, false, {})
        OutlinedTextField(confirmation, { confirmation = it; viewModel.clearError() }, Modifier.fillMaxWidth(), label = { Text("Xác nhận mật khẩu") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(accepted, { accepted = it }); Text("Tôi đồng ý với điều khoản và chính sách riêng tư") }
        form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button({ viewModel.register(name, email, password, confirmation) }, Modifier.fillMaxWidth(), enabled = accepted && !form.loading) {
            if (form.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Đăng ký")
        }
        TextButton(onBack, Modifier.align(Alignment.CenterHorizontally)) { Text("Đã có tài khoản? Đăng nhập") }
    }
}

@Composable
private fun AuthLayout(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("LingoLens", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp)); Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp)); Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, visible: Boolean, toggle: () -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text("Mật khẩu") }, singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(toggle) { Text(if (visible) "Ẩn" else "Hiện") } })
}
