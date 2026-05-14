package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.api.ElderData
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.api.UpdateElderReq
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
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.CompactTopBar
import com.example.myapplication.ui.components.StatusTag
import com.example.myapplication.util.ErrorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ElderDetailScreen(
    elderId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var elder by remember { mutableStateOf<ElderData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // 编辑模式
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editGender by remember { mutableStateOf("") }
    var editBloodType by remember { mutableStateOf("") }
    var editAllergy by remember { mutableStateOf("") }
    var editMedicalHistory by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    fun loadElder() {
        scope.launch {
            isLoading = true
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.elderApi.getElder(elderId) }
                if (resp.isSuccessful && resp.body()?.data != null) {
                    elder = resp.body()!!.data
                } else {
                    errorMsg = resp.body()?.message ?: "加载失败"
                }
            } catch (e: Exception) {
                errorMsg = ErrorHelper.userMessage(e, "loadElder")
            }
            isLoading = false
        }
    }

    LaunchedEffect(elderId) { loadElder() }

    Scaffold(
        modifier = modifier.gradientBackground(),
        topBar = {
            val topTitle = elder?.name ?: "老人详情"
            CompactTopBar(title = topTitle, onBack = onBack) {
                TextButton(onClick = {
                        if (isEditing) {
                            // 保存
                            isSaving = true
                            scope.launch {
                                try {
                                    val resp = withContext(Dispatchers.IO) {
                                        RetrofitClient.elderApi.updateElder(
                                            elderId,
                                            UpdateElderReq(
                                                name = editName.trim().ifEmpty { null },
                                                gender = editGender.ifEmpty { null },
                                                bloodType = editBloodType.ifEmpty { null },
                                                allergy = editAllergy.trim().ifEmpty { null },
                                                medicalHistory = editMedicalHistory.trim().ifEmpty { null },
                                                birthDate = null,
                                            )
                                        )
                                    }
                                    if (resp.isSuccessful && resp.body()?.code == 0) {
                                        isEditing = false
                                        loadElder()
                                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, resp.body()?.message ?: "保存失败", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, ErrorHelper.userMessage(e, "updateElder"), Toast.LENGTH_SHORT).show()
                                }
                                isSaving = false
                            }
                        } else {
                            elder?.let { e ->
                                editName = e.name ?: ""
                                editGender = e.gender ?: ""
                                editBloodType = e.bloodType ?: ""
                                editAllergy = e.allergy ?: ""
                                editMedicalHistory = e.medicalHistory ?: ""
                            }
                            isEditing = true
                        }
                    }) {
                        Text(
                            text = if (isEditing) if (isSaving) "保存中…" else "保存" else "编辑",
                            color = White,
                            fontSize = 14.sp,
                        )
                    }
            }
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isLoading) {
                item { EmptyState(title = "加载中…", message = "正在获取档案信息", onRetry = null) }
            } else if (errorMsg != null) {
                item { EmptyState(title = errorMsg!!, message = "请返回重试", onRetry = { loadElder() }) }
            } else if (elder != null) {
                val e = elder!!

                // 基本信息卡片
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(e.name ?: "未命名", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("ID: ${e.elderId ?: "-"}", fontSize = 12.sp, color = OfflineGray)
                                }
                                StatusTag(
                                    text = if (e.deviceOnline == true) "设备在线" else "设备离线",
                                    color = if (e.deviceOnline == true) SuccessGreen else OfflineGray,
                                    backgroundColor = if (e.deviceOnline == true) SuccessGreen.copy(alpha = 0.1f)
                                    else OfflineGray.copy(alpha = 0.1f),
                                )
                            }
                        }
                    }
                }

                // 详细信息卡片
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("详细信息", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                            if (isEditing) {
                                OutlinedTextField(
                                    value = editName, onValueChange = { editName = it },
                                    label = { Text("姓名") }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                )
                                OutlinedTextField(
                                    value = editGender, onValueChange = { editGender = it },
                                    label = { Text("性别") }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                )
                                OutlinedTextField(
                                    value = editBloodType, onValueChange = { editBloodType = it },
                                    label = { Text("血型") }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                )
                                OutlinedTextField(
                                    value = editAllergy, onValueChange = { editAllergy = it },
                                    label = { Text("过敏史") }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                )
                                OutlinedTextField(
                                    value = editMedicalHistory, onValueChange = { editMedicalHistory = it },
                                    label = { Text("既往病史") }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                )
                            } else {
                                DetailRow("姓名", e.name)
                                DetailRow("性别", e.gender)
                                DetailRow("出生日期", e.birthDate)
                                DetailRow("血型", e.bloodType)
                                DetailRow("过敏史", e.allergy)
                                DetailRow("既往病史", e.medicalHistory)
                                DetailRow("状态", e.status)
                                DetailRow("设备ID", e.deviceId)
                                DetailRow("创建时间", e.createdAt?.take(10))
                            }
                        }
                    }
                }

                // 监护人卡片
                val guardians = e.guardians
                if (!guardians.isNullOrEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("监护人列表", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Spacer(modifier = Modifier.height(8.dp))
                                guardians.forEach { g ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text(g.nickname ?: g.userId ?: "-", fontSize = 14.sp, color = TextPrimary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val roleTag = when (g.role) { "primary" -> "主监护人" else -> "协作监护人" }
                                        StatusTag(text = roleTag, color = PrimaryBlue, backgroundColor = PrimaryBlue.copy(alpha = 0.1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 14.sp, color = TextSecondary, modifier = Modifier.width(80.dp))
        Text(value?.ifEmpty { "-" } ?: "-", fontSize = 14.sp, color = TextPrimary)
    }
}
