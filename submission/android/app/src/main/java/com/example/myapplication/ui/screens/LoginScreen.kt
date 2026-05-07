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
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.ui.gradientBackground
import com.example.myapplication.ui.White
import com.example.myapplication.util.ErrorHelper
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LoginScreen(
    onLoginSuccess: (token: String, refreshToken: String, userId: String, displayName: String, phone: String) -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
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
            text = "VisionGuard",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "视障与老人智能守护平台",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp, bottom = 40.dp),
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it; errorMessage = null },
            label = { Text("用户名或手机号") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
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
                if (username.isBlank() || password.isBlank()) {
                    errorMessage = "请填写用户名和密码"
                    return@Button
                }
                isLoading = true
                errorMessage = null
                scope.launch {
                    try {
                        val response = withContext(Dispatchers.IO) {
                            RetrofitClient.authApi.login(LoginReq(username.trim(), password))
                        }
                        val body = response.body()
                        if (response.isSuccessful && body?.access_token != null) {
                            // 后端返回 flat JSON: {access_token, refresh_token, expires_in}
                            // 解码 JWT payload 提取 userId
                            val uid = try {
                                val payload = body.access_token!!.split(".")[1]
                                val decoded = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE))
                                com.google.gson.Gson().fromJson(decoded, Map::class.java)["userId"]?.toString() ?: ""
                            } catch (_: Exception) { "" }
                            val backendDisplayName = body.display_name?.takeIf { it.isNotBlank() } ?: username.trim()
                            val backendPhone = body.phone?.takeIf { it.isNotBlank() } ?: ""
                            com.example.myapplication.auth.AuthTokenHolder.displayName = backendDisplayName
                            com.example.myapplication.auth.AuthTokenHolder.phone = backendPhone
                            onLoginSuccess(
                                body.access_token!!,
                                body.refresh_token ?: "",
                                uid,
                                backendDisplayName,
                                backendPhone,
                            )
                        } else {
                            // 尝试从 errorBody 提取后端的错误消息 {"code":400,"message":"..."}
                            val errMsg = try {
                                val errJson = response.errorBody()?.string()
                                if (!errJson.isNullOrBlank()) {
                                    Gson().fromJson(errJson, Map::class.java)["message"] as? String
                                } else null
                            } catch (_: Exception) { null }
                            errorMessage = errMsg ?: "登录失败，请检查用户名和密码"
                        }
                    } catch (e: Exception) {
                        errorMessage = ErrorHelper.userMessage(e, "login")
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (isLoading) "登录中…" else "登录")
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onNavigateToRegister) { Text("还没有账号？立即注册") }
        TextButton(onClick = onNavigateToReset) { Text("忘记密码") }
    }
}
