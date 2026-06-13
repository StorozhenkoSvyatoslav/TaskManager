
package ru.storozhenko.taskmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val BluePrimary = Color(0xFF1976D2)
private val BlueDark    = Color(0xFF1565C0)
private val PageBg      = Color(0xFFF5F5F5)

@Composable
fun AuthScreen(onAuthenticated: (String) -> Unit) {
    val scope       = rememberCoroutineScope()
    val authService = remember { AuthService() }

    var selectedTab         by remember { mutableIntStateOf(0) }
    var email               by remember { mutableStateOf("") }
    var password            by remember { mutableStateOf("") }
    var confirmPassword     by remember { mutableStateOf("") }
    var showPassword        by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var error               by remember { mutableStateOf<String?>(null) }
    var isLoading           by remember { mutableStateOf(false) }

    val onSignIn: () -> Unit = {
        when {
            email.isBlank() || password.isBlank() -> error = "Please fill in all fields"
            else -> scope.launch {
                isLoading = true
                error = null
                authService.login(email.trim(), password)
                    .onSuccess { token -> onAuthenticated(token) }
                    .onFailure { e -> error = e.message ?: "Login failed" }
                isLoading = false
            }
        }
    }

    val onCreateAccount: () -> Unit = {
        when {
            email.isBlank() || password.isBlank() || confirmPassword.isBlank() ->
                error = "Please fill in all fields"
            !email.contains("@") ->
                error = "Please enter a valid email address"
            password.length < 8 ->
                error = "Password must be at least 8 characters"
            password != confirmPassword ->
                error = "Passwords do not match"
            else -> scope.launch {
                isLoading = true
                error = null
                authService.register(email.trim(), password)
                    .onSuccess {
                        selectedTab = 0
                        password = ""
                        confirmPassword = ""
                    }
                    .onFailure { e -> error = e.message ?: "Registration failed" }
                isLoading = false
            }
        }
    }

    val onCancel: () -> Unit = {
        email = ""; password = ""; confirmPassword = ""; error = null
    }

    Box(
        modifier = Modifier.fillMaxSize().background(PageBg),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints {
            val isNarrow = maxWidth < 600.dp

            if (isNarrow) {
                // Mobile: scrollable full-screen form, no branding panel, no fixed height
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(BluePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Task Manager",
                            color = BluePrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                    AuthFormContent(
                        selectedTab = selectedTab,
                        onTabChange = { selectedTab = it; error = null },
                        error = error,
                        email = email, onEmailChange = { email = it },
                        password = password, onPasswordChange = { password = it },
                        confirmPassword = confirmPassword, onConfirmPasswordChange = { confirmPassword = it },
                        showPassword = showPassword, onTogglePassword = { showPassword = !showPassword },
                        showConfirmPassword = showConfirmPassword,
                        onToggleConfirmPassword = { showConfirmPassword = !showConfirmPassword },
                        isLoading = isLoading,
                        onSignIn = onSignIn,
                        onCreateAccount = onCreateAccount,
                        onCancel = onCancel
                    )
                }
            } else {
                // Wide: existing 16:9 split card with branding panel
                val cardWidth  = minOf(maxWidth - 32.dp, 1400.dp)
                val cardHeight = cardWidth * 9f / 16f

                Card(
                    modifier  = Modifier.width(cardWidth).height(cardHeight),
                    shape     = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        BrandingPanel(modifier = Modifier.weight(0.45f).fillMaxHeight())

                        Column(
                            modifier = Modifier
                                .weight(0.55f)
                                .fillMaxHeight()
                                .background(Color.White)
                                .padding(horizontal = 48.dp, vertical = 40.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            AuthFormContent(
                                selectedTab = selectedTab,
                                onTabChange = { selectedTab = it; error = null },
                                error = error,
                                email = email, onEmailChange = { email = it },
                                password = password, onPasswordChange = { password = it },
                                confirmPassword = confirmPassword, onConfirmPasswordChange = { confirmPassword = it },
                                showPassword = showPassword, onTogglePassword = { showPassword = !showPassword },
                                showConfirmPassword = showConfirmPassword,
                                onToggleConfirmPassword = { showConfirmPassword = !showConfirmPassword },
                                isLoading = isLoading,
                                onSignIn = onSignIn,
                                onCreateAccount = onCreateAccount,
                                onCancel = onCancel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthFormContent(
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    error: String?,
    email: String, onEmailChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit,
    confirmPassword: String, onConfirmPasswordChange: (String) -> Unit,
    showPassword: Boolean, onTogglePassword: () -> Unit,
    showConfirmPassword: Boolean, onToggleConfirmPassword: () -> Unit,
    isLoading: Boolean,
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onCancel: () -> Unit
) {
    Text(
        "Welcome Back",
        color = BluePrimary,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.W600
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Please enter your details to continue",
        color = Color(0xFF666666),
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(24.dp))

    PrimaryTabRow(
        selectedTabIndex = selectedTab,
        containerColor = Color.Transparent,
        contentColor = BluePrimary
    ) {
        listOf("Login", "Register").forEachIndexed { index, title ->
            Tab(
                selected = selectedTab == index,
                onClick = { onTabChange(index) },
                text = {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = if (selectedTab == index) FontWeight.Medium else FontWeight.Normal
                    )
                }
            )
        }
    }
    Spacer(Modifier.height(20.dp))

    if (error != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
            shape    = RoundedCornerShape(4.dp)
        ) {
            Text(
                error,
                color    = Color(0xFFD32F2F),
                modifier = Modifier.padding(12.dp),
                style    = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (selectedTab == 0) {
        LoginForm(
            email = email, onEmailChange = onEmailChange,
            password = password, onPasswordChange = onPasswordChange,
            showPassword = showPassword, onTogglePassword = onTogglePassword,
            isLoading = isLoading,
            onSignIn = onSignIn
        )
    } else {
        RegisterForm(
            email = email, onEmailChange = onEmailChange,
            password = password, onPasswordChange = onPasswordChange,
            confirmPassword = confirmPassword, onConfirmPasswordChange = onConfirmPasswordChange,
            showPassword = showPassword, onTogglePassword = onTogglePassword,
            showConfirmPassword = showConfirmPassword, onToggleConfirmPassword = onToggleConfirmPassword,
            isLoading = isLoading,
            onCreateAccount = onCreateAccount,
            onCancel = onCancel
        )
    }
}

@Composable
private fun BrandingPanel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(BluePrimary, BlueDark),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        )
    ) {
        // Decorative circle — large, top-right
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = (-80).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
        )
        // Decorative circle — small, bottom-left
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 60.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Task Manager",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Organize your tasks, streamline your workflow",
                color = Color.White.copy(alpha = 0.95f),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(40.dp))

            FeatureItem("Intuitive task management")
            Spacer(Modifier.height(20.dp))
            FeatureItem("Collaborate with your team")
            Spacer(Modifier.height(20.dp))
            FeatureItem("Track progress in real-time")
        }
    }
}

@Composable
private fun FeatureItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LoginForm(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    isLoading: Boolean,
    onSignIn: () -> Unit
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email Address") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(8.dp)
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Password") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = onTogglePassword) {
                Text(if (showPassword) "Hide" else "Show", color = BluePrimary)
            }
        }
    )
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onSignIn,
            modifier = Modifier.weight(1f).height(48.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Sign In")
            }
        }
        OutlinedButton(
            onClick = {},
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BluePrimary),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Forgot Password?")
        }
    }
}

@Composable
private fun RegisterForm(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    showConfirmPassword: Boolean,
    onToggleConfirmPassword: () -> Unit,
    isLoading: Boolean,
    onCreateAccount: () -> Unit,
    onCancel: () -> Unit
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email Address") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(8.dp)
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Password") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = onTogglePassword) {
                Text(if (showPassword) "Hide" else "Show", color = BluePrimary)
            }
        },
        supportingText = { Text("Must be at least 8 characters", color = Color(0xFF666666)) }
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = confirmPassword,
        onValueChange = onConfirmPasswordChange,
        label = { Text("Confirm Password") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = onToggleConfirmPassword) {
                Text(if (showConfirmPassword) "Hide" else "Show", color = BluePrimary)
            }
        }
    )
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onCreateAccount,
            modifier = Modifier.weight(1f).height(48.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Create Account")
            }
        }
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BluePrimary),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Cancel")
        }
    }
}
