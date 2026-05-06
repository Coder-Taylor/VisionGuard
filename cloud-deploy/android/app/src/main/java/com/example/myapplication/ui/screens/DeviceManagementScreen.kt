package com.example.myapplication.ui.screens

import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.api.InitiateBindingReq
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.ui.gradientBackground
import com.example.myapplication.ui.AlertRed
import com.example.myapplication.ui.CardBackground
import com.example.myapplication.ui.OfflineGray
import com.example.myapplication.ui.PrimaryBlue
import com.example.myapplication.ui.SuccessGreen
import com.example.myapplication.ui.TextPrimary
import com.example.myapplication.ui.TextSecondary
import com.example.myapplication.ui.White
import com.example.myapplication.ui.components.AppPrimaryButton
import com.example.myapplication.ui.components.AppSecondaryButton
import com.example.myapplication.ui.components.CompactTopBar
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.util.ErrorHelper
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
internal fun DeviceManagementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var devices by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    // 绑定方式选择弹窗
    var showMethodPicker by remember { mutableStateOf(false) }
    // 手动输入弹窗
    var showInputDialog by remember { mutableStateOf(false) }
    var inputDeviceId by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    // 扫码启动器
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { scannedId ->
            performBinding(
                scope = scope,
                deviceId = scannedId,
                onError = { errorMsg = it },
                onSuccess = { refreshTrigger++; Toast.makeText(context, "绑定成功", Toast.LENGTH_SHORT).show() },
                onSubmitting = { isSubmitting = it },
            )
        }
    }

    // 从相册选择二维码图片
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                val result = decodeQrFromBitmap(bitmap)
                if (result != null) {
                    performBinding(
                        scope = scope,
                        deviceId = result,
                        onError = { errorMsg = it },
                        onSuccess = { refreshTrigger++; Toast.makeText(context, "绑定成功", Toast.LENGTH_SHORT).show() },
                        onSubmitting = { isSubmitting = it },
                    )
                } else {
                    Toast.makeText(context, "未识别到二维码，请重试", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "读取图片失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 加载已绑定设备列表
    LaunchedEffect(refreshTrigger) {
        scope.launch {
            try {
                // 获取用户监护的老人列表（含设备信息）
                val eldersResp = withContext(Dispatchers.IO) { RetrofitClient.elderApi.listMyElders() }
                if (eldersResp.isSuccessful) {
                    val elders = eldersResp.body()?.data ?: emptyList()
                    val boundDevices = mutableListOf<Map<String, String>>()
                    for (elder in elders) {
                        elder.elderId?.let { eid ->
                            boundDevices.add(
                                mapOf(
                                    "elderId" to eid,
                                    "name" to (elder.name ?: "老人"),
                                    "deviceId" to (elder.deviceId ?: ""),
                                    "deviceOnline" to if (elder.deviceOnline == true) "在线" else "离线",
                                )
                            )
                        }
                    }
                    devices = boundDevices
                }
                isLoading = false
                kotlinx.coroutines.delay(400)
                isRefreshing = false
            } catch (e: Exception) {
                errorMsg = ErrorHelper.userMessage(e, "loadDevices")
                isLoading = false
                kotlinx.coroutines.delay(400)
                isRefreshing = false
            }
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            refreshTrigger++
        },
    )

    // 绑定方式选择弹窗
    if (showMethodPicker) {
        AlertDialog(
            onDismissRequest = { showMethodPicker = false },
            title = { Text("选择绑定方式", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppPrimaryButton(
                        text = "扫码绑定",
                        onClick = {
                            showMethodPicker = false
                            val options = ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("扫描设备上的二维码获取设备 ID")
                                .setBeepEnabled(false)
                                .setOrientationLocked(false)
                            scanLauncher.launch(options)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppSecondaryButton(
                        text = "从相册选择二维码",
                        onClick = { showMethodPicker = false; galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppSecondaryButton(
                        text = "手动输入设备 ID",
                        onClick = { showMethodPicker = false; showInputDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMethodPicker = false }) { Text("取消") } },
        )
    }

    // 手动输入弹窗
    if (showInputDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSubmitting) {
                    showInputDialog = false
                    inputDeviceId = ""
                    inputError = null
                }
            },
            title = { Text("输入设备 ID", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "请输入硬件设备标签或屏幕上的设备 ID",
                        fontSize = 14.sp,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputDeviceId,
                        onValueChange = { inputDeviceId = it.trim(); inputError = null },
                        label = { Text("设备 ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    if (inputError != null) {
                        Text(
                            text = inputError!!,
                            color = androidx.compose.ui.graphics.Color(0xFFF53F3F),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (inputDeviceId.isBlank()) {
                            inputError = "请输入设备 ID"
                            return@TextButton
                        }
                        performBinding(
                            scope = scope,
                            deviceId = inputDeviceId,
                            onError = { inputError = it },
                            onSuccess = {
                                showInputDialog = false
                                inputDeviceId = ""
                                refreshTrigger++
                                Toast.makeText(context, "绑定成功", Toast.LENGTH_SHORT).show()
                            },
                            onSubmitting = { isSubmitting = it },
                        )
                    },
                    enabled = !isSubmitting,
                ) {
                    Text(if (isSubmitting) "绑定中…" else "确定绑定", color = PrimaryBlue)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showInputDialog = false
                        inputDeviceId = ""
                        inputError = null
                    },
                    enabled = !isSubmitting,
                ) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.gradientBackground(),
        topBar = { CompactTopBar(title = "设备绑定与管理", onBack = onBack) },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pullRefresh(pullRefreshState),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            if (isLoading) {
                item {
                    EmptyState(
                        title = "加载中…",
                        message = "正在获取设备列表",
                        onRetry = null,
                    )
                }
            } else if (errorMsg != null) {
                item {
                    EmptyState(
                        title = errorMsg ?: "加载失败",
                        message = "请检查网络后重试",
                        onRetry = { /* TODO */ },
                    )
                }
            } else if (devices.isEmpty()) {
                item {
                    EmptyState(
                        title = "暂无绑定设备",
                        message = "请先创建老人档案，然后通过下方按钮绑定设备",
                        onRetry = null,
                    )
                }
            } else {
                items(devices.size) { index ->
                    val d = devices[index]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = d["name"] ?: "设备",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "老人ID: ${d["elderId"] ?: "-"}",
                                        fontSize = 12.sp,
                                        color = OfflineGray,
                                    )
                                    val did = d["deviceId"] ?: ""
                                    if (did.isNotEmpty()) {
                                        Text(
                                            text = "设备ID: $did",
                                            fontSize = 12.sp,
                                            color = OfflineGray,
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = d["deviceOnline"] ?: "",
                                        fontSize = 12.sp,
                                        color = if (d["deviceOnline"] == "在线") SuccessGreen else OfflineGray,
                                    )
                                }
                            }
                            val elderId = d["elderId"] ?: ""
                            val deviceId = d["deviceId"] ?: ""
                            if (deviceId.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val resp = withContext(Dispatchers.IO) {
                                                    RetrofitClient.deviceApi.unbindDevice(
                                                        com.example.myapplication.api.UnbindReq(deviceId = deviceId, elderId = elderId)
                                                    )
                                                }
                                                if (resp.isSuccessful && resp.body()?.code == 0) {
                                                    Toast.makeText(context, "已解绑", Toast.LENGTH_SHORT).show()
                                                    // 重新加载列表
                                                    val eldersResp = withContext(Dispatchers.IO) { RetrofitClient.elderApi.listMyElders() }
                                                    if (eldersResp.isSuccessful) {
                                                        val elders = eldersResp.body()?.data ?: emptyList()
                                                        devices = elders.mapNotNull { elder ->
                                                            elder.elderId?.let {
                                                                mapOf("elderId" to it, "name" to (elder.name ?: "老人"), "deviceId" to (elder.deviceId ?: ""), "deviceOnline" to if (elder.deviceOnline == true) "在线" else "离线")
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Toast.makeText(context, resp.body()?.message ?: "解绑失败", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, ErrorHelper.userMessage(e, "unbind"), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                ) {
                                    Text("解绑设备", fontSize = 13.sp, color = AlertRed)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                AppPrimaryButton(
                    text = "绑定设备",
                    onClick = { showMethodPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
    }
}

private fun decodeQrFromBitmap(bitmap: android.graphics.Bitmap): String? {
    val intArray = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val source = com.google.zxing.RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
    val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
    return try {
        val result = com.google.zxing.qrcode.QRCodeReader().decode(binaryBitmap)
        result.text
    } catch (_: Exception) {
        null
    }
}

private fun performBinding(
    scope: kotlinx.coroutines.CoroutineScope,
    deviceId: String,
    onError: (String) -> Unit,
    onSuccess: () -> Unit,
    onSubmitting: (Boolean) -> Unit,
) {
    onSubmitting(true)
    scope.launch {
        try {
            // 搜索设备
            val searchResp = withContext(Dispatchers.IO) {
                RetrofitClient.deviceApi.searchDevice(deviceId)
            }
            if (!searchResp.isSuccessful) {
                onError("搜索设备失败，请检查设备 ID")
                onSubmitting(false)
                return@launch
            }
            val searchData = searchResp.body()?.data
            if (searchData?.canBind != true) {
                onError("该设备不可绑定（可能已被绑定或不存在）")
                onSubmitting(false)
                return@launch
            }
            // 获取第一个老人用于绑定
            val eldersResp = withContext(Dispatchers.IO) {
                RetrofitClient.elderApi.listMyElders()
            }
            val elders = eldersResp.body()?.data ?: emptyList()
            val elderId = elders.firstOrNull()?.elderId
            if (elderId == null) {
                onError("请先创建老人档案再绑定设备")
                onSubmitting(false)
                return@launch
            }
            // 发起绑定
            val bindResp = withContext(Dispatchers.IO) {
                RetrofitClient.deviceApi.initiateBinding(
                    InitiateBindingReq(deviceId = deviceId, elderId = elderId)
                )
            }
            if (bindResp.isSuccessful) {
                onSuccess()
            } else {
                onError("绑定失败，请稍后重试")
            }
        } catch (e: Exception) {
            onError(ErrorHelper.userMessage(e, "bind"))
        }
        onSubmitting(false)
    }
}
