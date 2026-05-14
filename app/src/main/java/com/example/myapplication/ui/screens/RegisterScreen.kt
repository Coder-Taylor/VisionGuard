package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myapplication.api.LoginReq
import com.example.myapplication.api.RegisterReq
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.ui.gradientBackground
import com.example.myapplication.ui.White
import com.example.myapplication.util.ErrorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RegisterScreen(
    onRegisterSuccess: (token: String, refreshToken: String, userId: String, displayName: String, phone: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var username by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .gradientBackground()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "创建账号",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "注册 VisionGuard 监护人账号",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
        )

        // 用户名（必填）— 对齐后端 RegisterReq.username
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; errorMessage = null },
            label = { Text("用户名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        // 手机号（可选）
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it; errorMessage = null },
            label = { Text("手机号（可选）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        // 密码
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        // 确认密码
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; errorMessage = null },
            label = { Text("确认密码") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                when {
                    username.isBlank() -> errorMessage = "请填写用户名"
                    password.length < 6 -> errorMessage = "密码至少6位"
                    password != confirmPassword -> errorMessage = "两次密码不一致"
                    else -> {
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            try {
                                // 1. 注册 — 后端返回 {code:0, message:"register success"} 无 token
                                val regResponse = withContext(Dispatchers.IO) {
                                    RetrofitClient.authApi.register(
                                        RegisterReq(
                                            username = username.trim(),
                                            password = password,
                                            phone = phone.trim().ifEmpty { null },
                                            email = null,
                                        )
                                    )
                                }
                                val regBody = regResponse.body()
                                if (!regResponse.isSuccessful || regBody?.code != 0) {
                                    errorMessage = regBody?.message ?: "注册失败，请稍后重试"
                                    isLoading = false
                                    return@launch
                                }
                                // 2. 注册成功后自动登录获取 token
                                val loginResponse = withContext(Dispatchers.IO) {
                                    RetrofitClient.authApi.login(
                                        LoginReq(username.trim(), password)
                                    )
                                }
                                val loginBody = loginResponse.body()
                                if (loginResponse.isSuccessful && loginBody?.access_token != null) {
                                    val regDisplayName = loginBody.display_name?.takeIf { it.isNotBlank() } ?: username.trim()
                                    val regPhone = loginBody.phone?.takeIf { it.isNotBlank() } ?: phone.trim()
                                    com.example.myapplication.auth.AuthTokenHolder.displayName = regDisplayName
                                    com.example.myapplication.auth.AuthTokenHolder.phone = regPhone
                                    val uid = try {
                                        val payload = loginBody.access_token!!.split(".")[1]
                                        val decoded = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE))
                                        com.google.gson.Gson().fromJson(decoded, Map::class.java)["userId"]?.toString() ?: ""
                                    } catch (_: Exception) { "" }
                                    onRegisterSuccess(
                                        loginBody.access_token!!,
                                        loginBody.refresh_token ?: "",
                                        uid,
                                        regDisplayName,
                                        regPhone,
                                    )
                                } else {
                                    errorMessage = "注册成功但登录失败，请手动登录"
                                }
                            } catch (e: Exception) {
                                errorMessage = ErrorHelper.userMessage(e, "register")
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (isLoading) "注册中…" else "注册")
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onNavigateToLogin) { Text("已有账号？去登录") }
    }
}
