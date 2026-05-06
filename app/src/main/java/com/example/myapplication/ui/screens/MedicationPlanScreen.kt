package com.example.myapplication.ui.screens

import android.app.TimePickerDialog
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.api.CreatePlanReq
import com.example.myapplication.api.MedicationPlanData
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.ui.AlertRed
import com.example.myapplication.ui.CardBackground
import com.example.myapplication.ui.OfflineGray
import com.example.myapplication.ui.PrimaryBlue
import com.example.myapplication.ui.SuccessGreen
import com.example.myapplication.ui.TextPrimary
import com.example.myapplication.ui.TextSecondary
import com.example.myapplication.ui.White
import com.example.myapplication.ui.components.AppConfirmDialog
import com.example.myapplication.ui.components.CompactTopBar
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.StatusTag
import com.example.myapplication.ui.gradientBackground
import com.example.myapplication.util.ErrorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
internal fun MedicationPlanScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var plans by remember { mutableStateOf<List<MedicationPlanData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Elder selection
    var elderOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedElderId by remember { mutableStateOf("") }
    var selectedElderName by remember { mutableStateOf("选择老人") }
    var elderDropdownExpanded by remember { mutableStateOf(false) }

    // Create dialog
    var showCreateDialog by remember { mutableStateOf(false) }
    var newDrugName by remember { mutableStateOf("") }
    var newDosage by remember { mutableStateOf("") }
    var newFrequency by remember { mutableStateOf("") }
    var newScheduleTimes by remember { mutableStateOf(listOf<String>()) }
    var newStartDate by remember { mutableStateOf("") }
    var newEndDate by remember { mutableStateOf("") }
    var newNotes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Date picker states (M3 style, same as birthday picker)
    val startDatePickerState = rememberDatePickerState()
    val endDatePickerState = rememberDatePickerState()

    // Delete dialog
    var planToDelete by remember { mutableStateOf<MedicationPlanData?>(null) }

    fun loadElders() {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.elderApi.listMyElders() }
                if (resp.isSuccessful) {
                    val list = resp.body()?.data ?: emptyList()
                    elderOptions = list.mapNotNull { e ->
                        val id = e.elderId ?: return@mapNotNull null
                        id to (e.name ?: id)
                    }
                    if (selectedElderId.isBlank() && elderOptions.isNotEmpty()) {
                        selectedElderId = elderOptions.first().first
                        selectedElderName = elderOptions.first().second
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun loadPlans() {
        if (selectedElderId.isBlank()) return
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.medicationApi.listPlans(selectedElderId)
                }
                if (resp.isSuccessful) {
                    plans = resp.body()?.data?.list ?: emptyList()
                }
            } catch (e: Exception) {
                errorMsg = ErrorHelper.userMessage(e, "loadMedicationPlans")
            }
            isLoading = false
            delay(400)
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) { loadElders() }
    LaunchedEffect(selectedElderId) { if (selectedElderId.isNotBlank()) loadPlans() }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { isRefreshing = true; scope.launch { loadPlans() } },
    )

    // -- Delete dialog --
    planToDelete?.let { plan ->
        AppConfirmDialog(
            title = "删除用药计划",
            message = "确定删除「${plan.drugName}」的用药计划吗？",
            confirmText = "删除",
            cancelText = "取消",
            confirmColor = AlertRed,
            onConfirm = {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            RetrofitClient.medicationApi.deletePlan(plan.planId!!)
                        }
                        plans = plans.filter { it.planId != plan.planId }
                    } catch (_: Exception) {}
                }
                planToDelete = null
            },
            onDismiss = { planToDelete = null },
        )
    }

    // -- TimePickerDialog --
    if (showTimePicker) {
        val timeCtx = androidx.compose.ui.platform.LocalContext.current
        val cal = java.util.Calendar.getInstance()
        TimePickerDialog(
            timeCtx,
            { _, hour, min ->
                val t = "%02d:%02d".format(hour, min)
                if (t !in newScheduleTimes) {
                    newScheduleTimes = (newScheduleTimes + t).sorted()
                }
            },
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            true,
        ).apply {
            setOnDismissListener { showTimePicker = false }
            show()
        }
    }

    // -- M3 Start Date Picker --
    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDatePickerState.selectedDateMillis?.let { millis ->
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
                        newStartDate = "%04d-%02d-%02d".format(
                            cal.get(java.util.Calendar.YEAR),
                            cal.get(java.util.Calendar.MONTH) + 1,
                            cal.get(java.util.Calendar.DAY_OF_MONTH),
                        )
                    }
                    showStartDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = startDatePickerState)
        }
    }

    // -- M3 End Date Picker --
    if (showEndDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDatePickerState.selectedDateMillis?.let { millis ->
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
                        newEndDate = "%04d-%02d-%02d".format(
                            cal.get(java.util.Calendar.YEAR),
                            cal.get(java.util.Calendar.MONTH) + 1,
                            cal.get(java.util.Calendar.DAY_OF_MONTH),
                        )
                    }
                    showEndDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = endDatePickerState)
        }
    }

    // -- Create dialog --
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showCreateDialog = false },
            title = { Text("创建用药计划", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newDrugName,
                        onValueChange = { newDrugName = it },
                        label = { Text("药品名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newDosage,
                            onValueChange = { newDosage = it },
                            label = { Text("剂量") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        OutlinedTextField(
                            value = newFrequency,
                            onValueChange = { newFrequency = it },
                            label = { Text("频次") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }

                    // —— 闹钟式时间选择 ——
                    Text("用药时间", fontSize = 13.sp, color = TextSecondary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        newScheduleTimes.forEach { time ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = PrimaryBlue.copy(alpha = 0.1f),
                                onClick = {
                                    newScheduleTimes = newScheduleTimes - time
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Outlined.AccessTime,
                                        contentDescription = null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        time,
                                        fontSize = 13.sp,
                                        color = PrimaryBlue,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "删除",
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                createError = null
                                showTimePicker = true
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = "添加时间",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    // 日期选择
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newStartDate,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("开始日期") },
                            placeholder = { Text("点击选择") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).clickable {
                                showStartDatePicker = true
                            },
                            shape = RoundedCornerShape(12.dp),
                        )
                        OutlinedTextField(
                            value = newEndDate,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("结束日期(可选)") },
                            placeholder = { Text("点击选择") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).clickable {
                                showEndDatePicker = true
                            },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                    OutlinedTextField(
                        value = newNotes,
                        onValueChange = { newNotes = it },
                        label = { Text("备注") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    if (createError != null) {
                        Text(createError!!, color = AlertRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newDrugName.isBlank()) { createError = "药品名称不能为空"; return@TextButton }
                        if (newScheduleTimes.isEmpty()) { createError = "请至少添加一个用药时间"; return@TextButton }
                        if (selectedElderId.isBlank()) { createError = "请先选择老人"; return@TextButton }
                        isSubmitting = true
                        scope.launch {
                            try {
                                val resp = withContext(Dispatchers.IO) {
                                    RetrofitClient.medicationApi.createPlan(
                                        CreatePlanReq(
                                            elderId = selectedElderId,
                                            drugName = newDrugName.trim(),
                                            dosage = newDosage.trim(),
                                            frequency = newFrequency.trim(),
                                            schedule = newScheduleTimes.joinToString(","),
                                            startDate = newStartDate.trim().ifEmpty { "2026-05-06" },
                                            endDate = newEndDate.trim().ifEmpty { null },
                                            notes = newNotes.trim().ifEmpty { null },
                                        )
                                    )
                                }
                                if (resp.isSuccessful) {
                                    showCreateDialog = false
                                    newDrugName = ""; newDosage = ""; newFrequency = ""
                                    newScheduleTimes = emptyList(); newStartDate = ""; newEndDate = ""; newNotes = ""
                                    loadPlans()
                                } else {
                                    createError = "创建失败，请重试"
                                }
                            } catch (e: Exception) {
                                createError = ErrorHelper.userMessage(e, "createPlan")
                            }
                            isSubmitting = false
                        }
                    },
                    enabled = !isSubmitting,
                ) { Text(if (isSubmitting) "提交中…" else "创建", color = PrimaryBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }, enabled = !isSubmitting) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.gradientBackground(),
        topBar = {
            CompactTopBar(title = "用药计划", onBack = onBack) {
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "创建", tint = White)
                }
            }
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Elder dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedElderName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("选择老人") },
                    trailingIcon = {
                        Text(if (elderDropdownExpanded) "▲" else "▼", fontSize = 12.sp, color = TextSecondary)
                    },
                    modifier = Modifier.fillMaxWidth().clickable { elderDropdownExpanded = true }.height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                )
                DropdownMenu(
                    expanded = elderDropdownExpanded,
                    onDismissRequest = { elderDropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f),
                ) {
                    elderOptions.forEach { (id, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                selectedElderId = id
                                selectedElderName = name
                                elderDropdownExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState),
            ) {
                if (isLoading && plans.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("加载中…", fontSize = 16.sp, color = TextSecondary)
                    }
                } else if (errorMsg != null && plans.isEmpty()) {
                    EmptyState(title = errorMsg!!, message = "请选择老人后重试", onRetry = { loadPlans() }, modifier = Modifier.fillMaxSize())
                } else if (plans.isEmpty()) {
                    EmptyState(title = "暂无用药计划", message = "点击右上角 + 为「$selectedElderName」创建用药计划", modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Text("${plans.size} 个计划", fontSize = 14.sp, color = TextSecondary)
                        }
                        items(plans.size) { index ->
                            val p = plans[index]
                            PlanCard(plan = p, onDelete = { planToDelete = p })
                        }
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
}

@Composable
private fun PlanCard(plan: MedicationPlanData, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Medication, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(plan.drugName ?: "药品", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                StatusTag(
                    text = when (plan.status) { "active" -> "进行中"; "paused" -> "已暂停"; "completed" -> "已完成"; else -> plan.status ?: "-" },
                    color = if (plan.status == "active") SuccessGreen else OfflineGray,
                    backgroundColor = if (plan.status == "active") SuccessGreen.copy(alpha = 0.1f) else OfflineGray.copy(alpha = 0.1f),
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = AlertRed, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${plan.dosage ?: "-"}  ", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                    Text(plan.frequency ?: "", fontSize = 13.sp, color = TextSecondary)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = OfflineGray, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(plan.schedule ?: "-", fontSize = 13.sp, color = TextSecondary)
            }
            if (!plan.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("备注: ${plan.notes}", fontSize = 12.sp, color = OfflineGray)
            }
        }
    }
}
