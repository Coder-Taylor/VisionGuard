package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.api.AppContext
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.auth.AuthTokenHolder
import com.example.myapplication.navigation.VisionHubDestination
import com.example.myapplication.ui.CardBackground
import com.example.myapplication.ui.OfflineGray
import com.example.myapplication.ui.PrimaryBlue
import com.example.myapplication.ui.White
import com.example.myapplication.ui.screens.AlertDetailScreen
import com.example.myapplication.ui.screens.AlertHistoryListScreen
import com.example.myapplication.ui.screens.DeviceManagementScreen
import com.example.myapplication.ui.screens.ElderDetailScreen
import com.example.myapplication.ui.screens.ElderManagementScreen
import com.example.myapplication.ui.screens.ForgotPasswordScreen
import com.example.myapplication.ui.screens.HomeScreen
import com.example.myapplication.ui.screens.LocationScreen
import com.example.myapplication.ui.screens.LoginScreen
import com.example.myapplication.ui.screens.MapScreen
import com.example.myapplication.ui.screens.NotificationListScreen
import com.example.myapplication.ui.screens.MedicationPlanScreen
import com.example.myapplication.ui.screens.OcrMedicineScreen
import com.example.myapplication.ui.screens.PositionMedicineScreen
import com.example.myapplication.ui.screens.ProfileScreen
import com.example.myapplication.ui.screens.RegisterScreen
import com.example.myapplication.ui.screens.UserSettingsScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.util.AuthPreference
import com.example.myapplication.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AuthScreen { LOGIN, REGISTER, FORGOT }

// 子页面路由
private sealed interface SubScreen {
    data object None : SubScreen
    data class AlertDetail(val alertId: String) : SubScreen
    data object DeviceManagement : SubScreen
    data object UserSettings : SubScreen
    data object ElderManagement : SubScreen
    data class ElderDetail(val elderId: String) : SubScreen
    data object NotificationCenter : SubScreen
    data object Location : SubScreen
    data object OcrMedicine : SubScreen
    data object MedicationPlan : SubScreen
    data object Map : SubScreen
}

class MainActivity : ComponentActivity() {
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 沉浸式：透明状态栏 + 深色图标（浅色背景）
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            )
        }
        startVisionHubService()
        requestNotificationPermissionIfNeeded()
        AppContext.instance = applicationContext
        NetworkMonitor.init(this)
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val isLoggedIn by VisionDataHub.isLoggedIn.collectAsStateWithLifecycle()
                var authScreen by rememberSaveable { mutableStateOf(AuthScreen.LOGIN) }
                val authScope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    // Token 续期失败时强制回到登录页
                    AuthTokenHolder.onForceLogout = {
                        AuthTokenHolder.token = null
                        AuthTokenHolder.refreshToken = null
                        AuthTokenHolder.userId = ""
                        VisionDataHub.setLoggedIn(false)
                    }
                    val storedToken = withContext(Dispatchers.IO) { AuthPreference.loadJwt(context) }
                    if (storedToken != null) {
                        // 检查 JWT 是否过期：过期且无 refresh_token → 强制重新登录
                        val jwtExpired = try {
                            val payload = storedToken.split(".")[1]
                            val decoded = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE))
                            val exp = com.google.gson.Gson().fromJson(decoded, Map::class.java)["exp"] as? Double
                            exp != null && (exp * 1000).toLong() < System.currentTimeMillis()
                        } catch (_: Exception) { true }
                        val storedRefresh = AuthPreference.loadRefreshToken(context)
                        if (jwtExpired && storedRefresh == null) {
                            withContext(Dispatchers.IO) { AuthPreference.clearAll(context) }
                        } else {
                            AuthTokenHolder.token = storedToken
                            AuthTokenHolder.refreshToken = storedRefresh
                            AuthTokenHolder.displayName = AuthPreference.loadDisplayName(context)
                            AuthTokenHolder.phone = AuthPreference.loadPhone(context)
                            VisionDataHub.setLoggedIn(true)
                        }
                    }
                }

                if (!isLoggedIn) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        when (authScreen) {
                            AuthScreen.LOGIN -> LoginScreen(
                                onLoginSuccess = { token, refreshToken, userId, displayName, phone ->
                                    AuthTokenHolder.token = token
                                    AuthTokenHolder.refreshToken = refreshToken
                                    AuthTokenHolder.userId = userId
                                    authScope.launch {
                                        withContext(Dispatchers.IO) {
                                            AuthPreference.saveJwt(context, token)
                                            AuthPreference.saveRefreshToken(context, refreshToken)
                                            AuthPreference.saveUserInfo(context, displayName, phone)
                                        }
                                    }
                                    VisionDataHub.setLoggedIn(true)
                                },
                                onNavigateToRegister = { authScreen = AuthScreen.REGISTER },
                                onNavigateToReset = { authScreen = AuthScreen.FORGOT },
                                modifier = Modifier.padding(innerPadding),
                            )
                            AuthScreen.REGISTER -> RegisterScreen(
                                onRegisterSuccess = { token, refreshToken, userId, displayName, phone ->
                                    AuthTokenHolder.token = token
                                    AuthTokenHolder.refreshToken = refreshToken
                                    AuthTokenHolder.userId = userId
                                    authScope.launch {
                                        withContext(Dispatchers.IO) {
                                            AuthPreference.saveJwt(context, token)
                                            AuthPreference.saveRefreshToken(context, refreshToken)
                                            AuthPreference.saveUserInfo(context, displayName, phone)
                                        }
                                    }
                                    VisionDataHub.setLoggedIn(true)
                                },
                                onNavigateToLogin = { authScreen = AuthScreen.LOGIN },
                                modifier = Modifier.padding(innerPadding),
                            )
                            AuthScreen.FORGOT -> ForgotPasswordScreen(
                                onResetSuccess = { authScreen = AuthScreen.LOGIN },
                                onNavigateToLogin = { authScreen = AuthScreen.LOGIN },
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
                    }
                } else {
                    VisionGuardMainScreen()
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startVisionHubService() {
        ContextCompat.startForegroundService(this, Intent(this, VisionHubService::class.java))
    }
}

@Composable
internal fun VisionGuardMainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentTab by rememberSaveable { mutableStateOf(VisionHubDestination.HOME) }
    // SubScreen 是 sealed interface 含 data object，不能序列化到 Bundle，用 remember 而非 rememberSaveable
    var subScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }

    // 系统返回键：子页面时返回主页面，不退出 APP
    BackHandler(enabled = subScreen != SubScreen.None) {
        subScreen = SubScreen.None
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CardBackground,
        bottomBar = {
            if (subScreen == SubScreen.None) {
                NavigationBar(
                    containerColor = White,
                    tonalElevation = 2.dp,
                ) {
                    VisionHubDestination.entries.forEach { destination ->
                        val selected = currentTab == destination
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentTab = destination },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label, fontSize = 12.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryBlue,
                                selectedTextColor = PrimaryBlue,
                                indicatorColor = PrimaryBlue.copy(alpha = 0.1f),
                                unselectedIconColor = OfflineGray,
                                unselectedTextColor = OfflineGray,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)

        // 子页面优先
        when (val sub = subScreen) {
            is SubScreen.AlertDetail -> {
                AlertDetailScreen(
                    alertId = sub.alertId,
                    onBack = { subScreen = SubScreen.None },
                    modifier = contentModifier,
                )
            }
            is SubScreen.DeviceManagement -> {
                DeviceManagementScreen(
                    onBack = { subScreen = SubScreen.None },
                    modifier = contentModifier,
                )
            }
            is SubScreen.UserSettings -> {
                UserSettingsScreen(
                    onBack = { subScreen = SubScreen.None },
                    modifier = contentModifier,
                )
            }
            is SubScreen.ElderManagement -> {
                ElderManagementScreen(
                    onBack = { subScreen = SubScreen.None },
                    onNavigateToDeviceBinding = { subScreen = SubScreen.DeviceManagement },
                    onNavigateToElderDetail = { elderId -> subScreen = SubScreen.ElderDetail(elderId) },
                    modifier = contentModifier,
                )
            }
            is SubScreen.ElderDetail -> {
                ElderDetailScreen(
                    elderId = sub.elderId,
                    onBack = { subScreen = SubScreen.None },
                    modifier = contentModifier,
                )
            }
            is SubScreen.NotificationCenter -> {
                NotificationListScreen(
                    onBack = { subScreen = SubScreen.None },
                    modifier = contentModifier,
                )
            }
            is SubScreen.Location -> {
                LocationScreen(
                    onBack = { subScreen = SubScreen.None },
                    onNavigateToMap = { subScreen = SubScreen.Map },
                    modifier = contentModifier,
                )
            }
            is SubScreen.Map -> {
                MapScreen(
                    onBack = { subScreen = SubScreen.None },
                    modifier = contentModifier,
                )
            }
            is SubScreen.OcrMedicine -> {
                OcrMedicineScreen(
                    onBack = { subScreen = SubScreen.None },
                    onNavigateToMedicationPlan = { subScreen = SubScreen.MedicationPlan },
                    modifier = contentModifier,
                )
            }
            is SubScreen.MedicationPlan -> {
                MedicationPlanScreen(
                    onBack = { subScreen = SubScreen.OcrMedicine },
                    modifier = contentModifier,
                )
            }
            SubScreen.None -> {
                when (currentTab) {
                    VisionHubDestination.HOME -> HomeScreen(
                        onAlertClick = { alertId ->
                            subScreen = SubScreen.AlertDetail(alertId)
                        },
                        modifier = contentModifier,
                    )
                    VisionHubDestination.POSITION_MEDICINE -> PositionMedicineScreen(
                        onNavigateToLocation = { subScreen = SubScreen.Location },
                        onNavigateToOcr = { subScreen = SubScreen.OcrMedicine },
                        modifier = contentModifier,
                    )
                    VisionHubDestination.ALERT_HISTORY -> AlertHistoryListScreen(
                        onAlertClick = { alertId ->
                            subScreen = SubScreen.AlertDetail(alertId)
                        },
                        modifier = contentModifier,
                    )
                    VisionHubDestination.PROFILE -> ProfileScreen(
                        onNavigateToDeviceManagement = {
                            subScreen = SubScreen.DeviceManagement
                        },
                        onNavigateToUserSettings = {
                            subScreen = SubScreen.UserSettings
                        },
                        onNavigateToElderManagement = {
                            subScreen = SubScreen.ElderManagement
                        },
                        onNavigateToNotificationCenter = {
                            subScreen = SubScreen.NotificationCenter
                        },
                        onLogout = {
                            coroutineScope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        RetrofitClient.authApi.logout(
                                            com.example.myapplication.api.LogoutReq(refreshToken = AuthTokenHolder.refreshToken ?: "")
                                        )
                                    }
                                } catch (_: Exception) {}
                                withContext(Dispatchers.IO) { AuthPreference.clearAll(context) }
                                AuthTokenHolder.token = null
                                AuthTokenHolder.refreshToken = null
                                AuthTokenHolder.userId = ""
                                VisionDataHub.setLoggedIn(false)
                            }
                        },
                        modifier = contentModifier,
                    )
                }
            }
        }
    }
}
