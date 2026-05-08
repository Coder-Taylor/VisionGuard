package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.example.myapplication.api.OcrRecordData
import com.example.myapplication.api.OcrResultData
import com.example.myapplication.api.OcrUploadReq
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.ui.gradientBackground
import com.example.myapplication.ui.AlertRed
import com.example.myapplication.ui.CardBackground
import com.example.myapplication.ui.LightBlue
import com.example.myapplication.ui.OfflineGray
import com.example.myapplication.ui.PrimaryBlue
import com.example.myapplication.ui.SoftGreen
import com.example.myapplication.ui.SoftOrange
import com.example.myapplication.ui.SuccessGreen
import com.example.myapplication.ui.TextPrimary
import com.example.myapplication.ui.TextSecondary
import com.example.myapplication.ui.White
import com.example.myapplication.ui.components.CompactTopBar
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.StatusTag
import com.example.myapplication.util.ErrorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
internal fun OcrMedicineScreen(
    onBack: () -> Unit,
    onNavigateToMedicationPlan: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var records by remember { mutableStateOf<List<OcrRecordData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showHardwareHint by remember { mutableStateOf(false) }

    val context = LocalContext.current
    // Camera capture state (launcher defined after loadRecords)
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadMsg by remember { mutableStateOf("") }
    var uploadError by remember { mutableStateOf<String?>(null) }

    // Detail dialog
    var detailTaskId by remember { mutableStateOf<String?>(null) }
    var detailResult by remember { mutableStateOf<OcrResultData?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf<String?>(null) }

    fun fetchDetail(taskId: String) {
        detailTaskId = taskId
        detailResult = null
        detailError = null
        detailLoading = true
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.ocrApi.getOcrResult(taskId)
                }
                if (resp.isSuccessful) {
                    detailResult = resp.body()?.data
                } else {
                    detailError = "获取识别结果失败"
                }
            } catch (e: Exception) {
                detailError = ErrorHelper.userMessage(e, "getOcrResult")
            }
            detailLoading = false
        }
    }

    fun loadRecords() {
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.ocrApi.getOcrRecords(pageSize = 20)
                }
                if (resp.isSuccessful) {
                    records = resp.body()?.data?.list ?: emptyList()
                }
            } catch (e: Exception) {
                errorMsg = ErrorHelper.userMessage(e, "loadOcrRecords")
            }
            isLoading = false
            kotlinx.coroutines.delay(400)
            isRefreshing = false
        }
    }

    // Camera launcher (after loadRecords so it can reference it)
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoUri != null) {
            scope.launch {
                isUploading = true
                uploadMsg = "正在上传识别..."
                uploadError = null
                try {
                    val inputStream = context.contentResolver.openInputStream(photoUri!!)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (bitmap == null) {
                        uploadError = "无法读取照片"
                        isUploading = false
                        return@launch
                    }
                    val maxDim = 1024
                    val w = bitmap.width
                    val h = bitmap.height
                    val scale = if (w > h) maxDim.toFloat() / w else maxDim.toFloat() / h
                    val scaledBitmap = if (scale < 1f) {
                        Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true)
                    } else bitmap

                    val baos = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                    val jpegBytes = baos.toByteArray()
                    baos.close()

                    val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                    val fileUrl = "data:image/jpeg;base64,$base64"

                    uploadMsg = "豆包 AI 识别中..."
                    val resp = withContext(Dispatchers.IO) {
                        RetrofitClient.ocrApi.uploadImageJson(
                            OcrUploadReq(
                                elderId = null,
                                imageCategory = "medicine",
                                fileUrl = fileUrl,
                                fileSize = jpegBytes.size.toLong(),
                                width = scaledBitmap.width,
                                height = scaledBitmap.height,
                            )
                        )
                    }
                    if (resp.isSuccessful) {
                        val taskId = resp.body()?.data?.taskId
                        if (taskId != null) {
                            for (i in 1..10) {
                                delay(2000)
                                uploadMsg = "识别中...(${i * 2}s)"
                                val pollResp = withContext(Dispatchers.IO) {
                                    RetrofitClient.ocrApi.pollOcrTask(taskId)
                                }
                                val status = pollResp.body()?.data?.status
                                if (status == "completed" || status == "failed") {
                                    uploadMsg = if (status == "completed") "识别完成！" else "识别失败"
                                    break
                                }
                            }
                        }
                        uploadMsg = "识别完成"
                        loadRecords()
                    } else {
                        uploadError = "上传失败: ${resp.code()}"
                    }
                } catch (e: Exception) {
                    uploadError = ErrorHelper.userMessage(e, "uploadOcr")
                }
                delay(1500)
                isUploading = false
            }
        }
    }

    fun takePhoto() {
        val file = File(context.cacheDir, "ocr_${System.currentTimeMillis()}.jpg")
        file.parentFile?.mkdirs()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        photoUri = uri
        cameraLauncher.launch(uri)
    }

    LaunchedEffect(Unit) { loadRecords() }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch { loadRecords() }
        },
    )

    if (showHardwareHint) {
        AlertDialog(
            onDismissRequest = { showHardwareHint = false },
            title = { Text("药品识别流程", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowStep("1", "硬件胸牌拍摄药品包装照片")
                    FlowStep("2", "JPEG 图片上传至云端")
                    FlowStep("3", "豆包 doubao-seed-1.6-vision 识别")
                    FlowStep("4", "识别结果返回硬件语音播报")
                    FlowStep("5", "监护人可在此查看识别记录")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHardwareHint = false }) { Text("知道了", color = PrimaryBlue) }
            },
        )
    }

    // Upload progress dialog
    if (isUploading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("拍照识别", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uploadMsg, fontSize = 14.sp, color = TextPrimary)
                    if (uploadError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(uploadError!!, fontSize = 12.sp, color = AlertRed)
                    }
                }
            },
            confirmButton = {},
        )
    }

    // Detail dialog
    if (detailTaskId != null) {
        AlertDialog(
            onDismissRequest = { detailTaskId = null },
            title = { Text("识别结果", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    if (detailLoading) {
                        Text("加载中…", fontSize = 14.sp, color = TextSecondary)
                    } else if (detailError != null) {
                        Text(detailError!!, fontSize = 14.sp, color = AlertRed)
                    } else {
                        detailResult?.let { r ->
                            DetailRow("药品名称", r.medicineName ?: "-")
                            DetailRow("规格", r.medicineSpec ?: "-")
                            DetailRow("用法用量", r.medicineUsage ?: "-")
                            DetailRow("每次剂量", r.medicineDosage ?: "-")
                            DetailRow("禁忌症", r.medicineContraindications.let { if (it.isNullOrBlank()) "无" else it })
                            r.ocrText?.let {
                                DetailRow("OCR 原文", it)
                            }
                            r.confidence?.let {
                                DetailRow("置信度", "${"%.0f".format(it * 100)}%")
                            }
                            r.suggestion?.let {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("用药建议", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(it, fontSize = 13.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailTaskId = null }) { Text("关闭", color = PrimaryBlue) }
            },
        )
    }

    Scaffold(
        modifier = modifier.gradientBackground(),
        topBar = {
            CompactTopBar(title = "用药识别", onBack = onBack) {
                IconButton(onClick = { loadRecords() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新", tint = White)
                }
            }
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pullRefresh(pullRefreshState),
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…", fontSize = 16.sp, color = TextSecondary)
                }
            } else if (errorMsg != null) {
                EmptyState(
                    title = errorMsg!!,
                    message = "请先绑定设备后再使用药品识别",
                    onRetry = { loadRecords() },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                // 功能入口卡片
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(
                            modifier = Modifier.weight(1f).clickable { takePhoto() },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier.size(44.dp).clip(CircleShape).background(LightBlue),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("拍照识别", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("拍摄药品照片", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f).clickable(onClick = onNavigateToMedicationPlan),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier.size(44.dp).clip(CircleShape).background(SoftOrange),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.Medication, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("用药计划", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("管理用药提醒", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                }

                // 识别记录
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Description, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("识别记录", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${records.size} 条", fontSize = 12.sp, color = OfflineGray)
                    }
                }

                if (records.isEmpty()) {
                    item {
                        Text("暂无识别记录，请先拍照识别药品", fontSize = 14.sp, color = TextSecondary)
                    }
                } else {
                    items(records.size) { index ->
                        val r = records[index]
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                r.taskId?.let { fetchDetail(it) }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = r.medicineName ?: "药品识别结果",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                        )
                                        if (!r.ocrText.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(r.ocrText!!, fontSize = 12.sp, color = TextSecondary, maxLines = 2)
                                        }
                                    }
                                    StatusTag(
                                        text = mapOcrStatus(r.status),
                                        color = if (r.status == "completed") SuccessGreen else OfflineGray,
                                        backgroundColor = if (r.status == "completed") SuccessGreen.copy(alpha = 0.1f)
                                        else OfflineGray.copy(alpha = 0.1f),
                                    )
                                }
                                r.createdAt?.let {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(it.take(16).replace("T", " "), fontSize = 10.sp, color = OfflineGray)
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
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

@Composable
private fun FlowStep(num: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(PrimaryBlue),
            contentAlignment = Alignment.Center,
        ) { Text(num, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.width(8.dp))
        Text(desc, fontSize = 14.sp, color = TextPrimary)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 14.sp, color = TextPrimary)
    }
}

private fun mapOcrStatus(status: String?): String = when (status) {
    "pending" -> "等待中"
    "processing" -> "识别中"
    "completed" -> "已完成"
    "failed" -> "失败"
    else -> status ?: "-"
}
