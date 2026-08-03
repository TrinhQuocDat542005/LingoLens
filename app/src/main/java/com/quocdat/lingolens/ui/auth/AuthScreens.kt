package com.quocdat.lingolens.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SplashScreen() {
    AuthBackground {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark()
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Đang khôi phục phiên học…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LoginScreen(viewModel: AuthViewModel, onRegister: () -> Unit) {
    val form by viewModel.form.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AuthLayout(
        eyebrow = "WELCOME BACK",
        title = "Chào mừng trở lại",
        subtitle = "Tiếp tục hành trình biến mọi vật xung quanh thành từ vựng tiếng Anh."
    ) {
        AuthTextField(
            value = email,
            onValueChange = { email = it; viewModel.clearError() },
            label = "Email",
            icon = Icons.Default.Email,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        )
        PasswordField(
            value = password,
            onChange = { password = it; viewModel.clearError() },
            visible = visible,
            toggle = { visible = !visible },
            label = "Mật khẩu"
        )
        form.error?.let { AuthError(it) }
        Button(
            onClick = { viewModel.login(email, password) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !form.loading,
            shape = RoundedCornerShape(16.dp)
        ) {
            if (form.loading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Đang đăng nhập…")
            } else Text("Đăng nhập", fontWeight = FontWeight.Bold)
        }
        AuthDivider("hoặc")
        TextButton(onClick = onRegister, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Chưa có tài khoản? ")
            Text("Đăng ký ngay", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RegisterScreen(viewModel: AuthViewModel, onBack: () -> Unit) {
    val form by viewModel.form.collectAsState()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmationVisible by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }

    LaunchedEffect(form.registrationComplete) {
        if (form.registrationComplete) {
            viewModel.consumeRegistration()
            onBack()
        }
    }

    AuthLayout(
        eyebrow = "CREATE YOUR ACCOUNT",
        title = "Bắt đầu cùng LingoLens",
        subtitle = "Tạo tài khoản để đồng bộ từ đã học và tiến độ trên mọi thiết bị."
    ) {
        AuthTextField(name, { name = it; viewModel.clearError() }, "Họ và tên", Icons.Default.Person)
        AuthTextField(
            email, { email = it; viewModel.clearError() }, "Email", Icons.Default.Email,
            KeyboardType.Email, ImeAction.Next
        )
        PasswordField(password, { password = it; viewModel.clearError() }, passwordVisible,
            { passwordVisible = !passwordVisible }, "Mật khẩu")
        PasswordField(confirmation, { confirmation = it; viewModel.clearError() }, confirmationVisible,
            { confirmationVisible = !confirmationVisible }, "Xác nhận mật khẩu", ImeAction.Done)

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
        ) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                Text(
                    "Tôi đồng ý với Điều khoản sử dụng và Chính sách riêng tư.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        form.error?.let { AuthError(it) }
        Button(
            onClick = { viewModel.register(name, email, password, confirmation) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = accepted && !form.loading,
            shape = RoundedCornerShape(16.dp)
        ) {
            if (form.loading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Đang tạo tài khoản…")
            } else Text("Tạo tài khoản", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Đã có tài khoản? ")
            Text("Đăng nhập", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AuthBackground(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.primary.copy(alpha = .14f),
                    MaterialTheme.colorScheme.background
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(260.dp).align(Alignment.TopEnd)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = .07f), CircleShape)
        )
        content()
    }
}

@Composable
private fun AuthLayout(
    eyebrow: String,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    AuthBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            BrandMark()
            Spacer(Modifier.height(22.dp))
            Text(eyebrow, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                fontSize = 11.sp, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 440.dp))
            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("Camera • AI Offline • Từ vựng theo ngữ cảnh", color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(17.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
                .border(1.dp, Color.White.copy(alpha = .2f), RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Search, "LingoLens", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("LingoLens", fontWeight = FontWeight.Black, fontSize = 24.sp)
            Text("SEE IT. LEARN IT.", color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.2.sp)
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        colors = authTextFieldColors()
    )
}

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    visible: Boolean,
    toggle: () -> Unit,
    label: String,
    imeAction: ImeAction = ImeAction.Next
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Lock, null) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = toggle) { Text(if (visible) "Ẩn" else "Hiện") } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        colors = authTextFieldColors()
    )
}

@Composable
private fun AuthError(message: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth().padding(14.dp), fontSize = 13.sp)
    }
}

@Composable
private fun AuthDivider(label: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text(label, Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp)
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .34f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .22f),
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = .55f),
    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
)
