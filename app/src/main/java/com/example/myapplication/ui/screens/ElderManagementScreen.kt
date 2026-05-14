package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.api.CreateElderReq
import com.example.myapplication.api.ElderData
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
import com.example.myapplication.ui.components.AppConfirmDialog
import com.example.myapplication.ui.components.AppPrimaryButton
import com.example.myapplication.ui.components.AppSecondaryButton
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.CompactTopBar
import com.example.myapplication.ui.components.StatusTag
import com.example.myapplication.util.ErrorHelper
import com.example.myapplication.util.LunarCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.Color

private val lunarMonths = listOf("正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "冬月", "腊月")
private val lunarDays = listOf(
    "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
    "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
    "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十",
)
private val tianGan = listOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
private val diZhi = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")

private fun getYearName(year: Int): String = "${tianGan[(year - 4) % 10]}${diZhi[(year - 4) % 12]}"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
internal fun ElderManagementScreen(
    onBack: () -> Unit,
    onNavigateToDeviceBinding: () -> Unit,
    onNavigateToElderDetail: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var elders by remember { mutableStateOf<List<ElderData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // 创建老人弹窗
    var showCreateDialog by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var createGender by remember { mutableStateOf("") }
    var createBirthDate by remember { mutableStateOf("") } // 最终存储的日期 yyyy-MM-dd
    var createBirthDisplay by remember { mutableStateOf("") } // 显示文本
    var isLunarDate by remember { mutableStateOf(false) }
    var createBloodType by remember { mutableStateOf("") }
    var createAllergy by remember { mutableStateOf("") }
    var createMedicalHistory by remember { mutableStateOf("") }
    var createEmergencyName by remember { mutableStateOf("") }
    var createEmergencyPhone by remember { mutableStateOf("") }
    var createError by remember { mutableStateOf<String?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    // 日期选择器
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    var showLunarDatePicker by remember { mutableStateOf(false) }
    var selectedLunarYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedLunarMonth by remember { mutableStateOf(1) }
    var selectedLunarDay by remember { mutableStateOf(1) }
    var genderExpanded by remember { mutableStateOf(false) }
    var bloodTypeExpanded by remember { mutableStateOf(false) }
    val genderOptions = listOf("男", "女")
    val bloodTypeOptions = listOf("A", "B", "AB", "O")

    // 删除确认
    var deleteTarget by remember { mutableStateOf<ElderData?>(null) }

    fun loadElders() {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.elderApi.listMyElders() }
                if (resp.isSuccessful) {
                    elders = resp.body()?.data ?: emptyList()
                } else {
                    errorMsg = "加载失败"
                }
            } catch (e: Exception) {
                errorMsg = ErrorHelper.userMessage(e, "loadElders")
            }
            isLoading = false
            kotlinx.coroutines.delay(400)
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) { loadElders() }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch { loadElders() }
        },
    )

    // 阳历日期选择器弹窗
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val cal = Calendar.getInstance().apply { timeInMillis = millis }
                        val y = cal.get(Calendar.YEAR)
                        val m = cal.get(Calendar.MONTH) + 1
                        val d = cal.get(Calendar.DAY_OF_MONTH)
                        createBirthDate = "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
                        // 显示公历 + 对应农历
                        val lunar = LunarCalendar.solarToLunar(y, m, d)
                        if (lunar != null) {
                            val (lunarY, lunarM, lunarD, isLeap) = lunar
                            val leapTag = if (isLeap) "闰" else ""
                            createBirthDisplay = "${y}年${m}月${d}日 / ${getYearName(lunarY)}年$leapTag${lunarMonths[lunarM - 1]}${lunarDays[lunarD - 1]}"
                        } else {
                            createBirthDisplay = "${y}年${m}月${d}日"
                        }
                    }
                    showDatePicker = false
                }) { Text("确定", color = PrimaryBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 农历日期选择器弹窗
    if (showLunarDatePicker) {
        val leapMonth = LunarCalendar.leapMonthOf(selectedLunarYear)
        AlertDialog(
            onDismissRequest = { showLunarDatePicker = false },
            title = { Text("选择农历日期", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 年份选择，显示天干地支
                    val yearName = getYearName(selectedLunarYear)
                    Text(
                        text = "${yearName}年（${selectedLunarYear}）",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            if (selectedLunarYear > 1900) selectedLunarYear--
                        }) { Text("−", fontSize = 18.sp) }
                        Spacer(modifier = Modifier.width(24.dp))
                        OutlinedButton(onClick = {
                            if (selectedLunarYear < 2100) selectedLunarYear++
                        }) { Text("+", fontSize = 18.sp) }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("选择月份", fontSize = 14.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))

                    // 月份网格 4列×3行
                    val monthRows = lunarMonths.chunked(4)
                    monthRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            row.forEach { monthName ->
                                val mIdx = lunarMonths.indexOf(monthName) + 1
                                val selected = selectedLunarMonth == mIdx
                                val isLeap = leapMonth == mIdx
                                TextButton(
                                    onClick = { selectedLunarMonth = mIdx },
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (selected) PrimaryBlue else White,
                                            shape = RoundedCornerShape(8.dp),
                                        ),
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = monthName,
                                            fontSize = 13.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) White else TextPrimary,
                                        )
                                        if (isLeap) {
                                            Text(
                                                text = "闰",
                                                fontSize = 10.sp,
                                                color = if (selected) White else PrimaryBlue,
                                            )
                                        }
                                    }
                                }
                            }
                            // fill remaining columns
                            repeat(4 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("选择日期", fontSize = 14.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))

                    // 日期网格 5列×6行
                    val dayRows = lunarDays.chunked(5)
                    dayRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            row.forEach { dayName ->
                                val dIdx = lunarDays.indexOf(dayName) + 1
                                val selected = selectedLunarDay == dIdx
                                TextButton(
                                    onClick = { selectedLunarDay = dIdx },
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (selected) PrimaryBlue else White,
                                            shape = RoundedCornerShape(8.dp),
                                        ),
                                ) {
                                    Text(
                                        text = dayName,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) White else TextPrimary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            repeat(5 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    // 预览转换结果
                    Spacer(modifier = Modifier.height(12.dp))
                    val solar = LunarCalendar.lunarToSolar(selectedLunarYear, selectedLunarMonth, selectedLunarDay)
                    if (solar != null) {
                        Text(
                            text = "→ ${solar.first}-${solar.second.toString().padStart(2, '0')}-${solar.third.toString().padStart(2, '0')}",
                            fontSize = 13.sp,
                            color = SuccessGreen,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val solar = LunarCalendar.lunarToSolar(selectedLunarYear, selectedLunarMonth, selectedLunarDay)
                    if (solar != null) {
                        createBirthDate = "${solar.first}-${solar.second.toString().padStart(2, '0')}-${solar.third.toString().padStart(2, '0')}"
                        createBirthDisplay = "$createBirthDate / ${getYearName(selectedLunarYear)}年${lunarMonths[selectedLunarMonth - 1]}${lunarDays[selectedLunarDay - 1]}"
                    } else {
                        createBirthDate = ""
                        createBirthDisplay = "${getYearName(selectedLunarYear)}年${lunarMonths[selectedLunarMonth - 1]}${lunarDays[selectedLunarDay - 1]}"
                    }
                    showLunarDatePicker = false
                }) { Text("确定", color = PrimaryBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showLunarDatePicker = false }) { Text("取消") }
            },
        )
    }

    // 删除确认弹窗
    if (deleteTarget != null) {
        AppConfirmDialog(
            title = "删除老人档案",
            message = "确定删除「${deleteTarget?.name}」的档案？此操作不可撤销，将解绑所有已绑定设备。",
            confirmText = "确认删除",
            cancelText = "取消",
            confirmColor = AlertRed,
            onConfirm = {
                val target = deleteTarget ?: return@AppConfirmDialog
                deleteTarget = null
                scope.launch {
                    try {
                        val resp = withContext(Dispatchers.IO) {
                            RetrofitClient.elderApi.deleteElder(target.elderId ?: "")
                        }
                        if (resp.isSuccessful && resp.body()?.code == 0) {
                            loadElders()
                            android.widget.Toast.makeText(context, "已删除", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, resp.body()?.message ?: "删除失败", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, ErrorHelper.userMessage(e, "deleteElder"), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }

    // 创建老人弹窗
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isCreating) { showCreateDialog = false; createError = null }
            },
            title = { Text("创建老人档案", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        OutlinedTextField(
                            value = createName, onValueChange = { createName = it; createError = null },
                            label = { Text("姓名 *") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        )
                    }
                    // 性别下拉
                    item {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = createGender,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("性别") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = if (genderExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                        contentDescription = null,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )
                            // 透明遮罩捕获点击
                            Box(modifier = Modifier
                                .matchParentSize()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { genderExpanded = true }
                            )
                            DropdownMenu(
                                expanded = genderExpanded,
                                onDismissRequest = { genderExpanded = false },
                            ) {
                                genderOptions.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt) },
                                        onClick = { createGender = opt; genderExpanded = false },
                                    )
                                }
                            }
                        }
                    }
                    // 出生日期
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = createBirthDisplay,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("出生日期") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = {
                                isLunarDate = false
                                showDatePicker = true
                            }) {
                                Text("阳历", color = PrimaryBlue, fontSize = 13.sp)
                            }
                            TextButton(onClick = {
                                isLunarDate = true
                                showLunarDatePicker = true
                            }) {
                                Text("农历", color = PrimaryBlue, fontSize = 13.sp)
                            }
                        }
                    }
                    // 血型下拉
                    item {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = createBloodType,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("血型") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = if (bloodTypeExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                        contentDescription = null,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )
                            // 透明遮罩捕获点击
                            Box(modifier = Modifier
                                .matchParentSize()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { bloodTypeExpanded = true }
                            )
                            DropdownMenu(
                                expanded = bloodTypeExpanded,
                                onDismissRequest = { bloodTypeExpanded = false },
                            ) {
                                bloodTypeOptions.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt) },
                                        onClick = { createBloodType = opt; bloodTypeExpanded = false },
                                    )
                                }
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = createAllergy, onValueChange = { createAllergy = it },
                            label = { Text("过敏史") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = createMedicalHistory, onValueChange = { createMedicalHistory = it },
                            label = { Text("既往病史") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = createEmergencyName, onValueChange = { createEmergencyName = it },
                            label = { Text("紧急联系人姓名") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = createEmergencyPhone, onValueChange = { createEmergencyPhone = it },
                            label = { Text("紧急联系人电话") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        )
                    }
                    if (createError != null) {
                        item {
                            Text(text = createError!!, color = androidx.compose.ui.graphics.Color(0xFFF53F3F), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (createName.isBlank()) { createError = "请填写姓名"; return@TextButton }
                        isCreating = true
                        scope.launch {
                            try {
                                val resp = withContext(Dispatchers.IO) {
                                    RetrofitClient.elderApi.createElder(
                                        CreateElderReq(
                                            name = createName.trim(),
                                            gender = createGender.ifEmpty { null },
                                            birthDate = createBirthDate.ifEmpty { null },
                                            bloodType = createBloodType.ifEmpty { null },
                                            allergy = createAllergy.trim().ifEmpty { null },
                                            medicalHistory = createMedicalHistory.trim().ifEmpty { null },
                                            emergencyContactName = createEmergencyName.trim().ifEmpty { null },
                                            emergencyContactPhone = createEmergencyPhone.trim().ifEmpty { null },
                                        )
                                    )
                                }
                                if (resp.isSuccessful && resp.body()?.code == 0) {
                                    showCreateDialog = false
                                    createName = ""; createGender = ""; createBirthDate = ""; createBirthDisplay = ""
                                    createBloodType = ""; createAllergy = ""; createMedicalHistory = ""
                                    createEmergencyName = ""; createEmergencyPhone = ""
                                    createError = null
                                    loadElders()
                                } else {
                                    val errBody = try { resp.errorBody()?.string() } catch (_: Exception) { null }
                                    createError = resp.body()?.message
                                        ?: errBody
                                        ?: "创建失败(${resp.code()})"
                                }
                            } catch (e: Exception) {
                                createError = ErrorHelper.userMessage(e, "createElder") + "(${e.message})"
                            }
                            isCreating = false
                        }
                    },
                    enabled = !isCreating,
                ) { Text(if (isCreating) "创建中…" else "创建", color = PrimaryBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; createError = null }, enabled = !isCreating) { Text("取消") }
            },
        )
    }

    Scaffold(
        modifier = modifier.gradientBackground(),
        topBar = { CompactTopBar(title = "我的老人", onBack = onBack) },
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
                item { EmptyState(title = "加载中…", message = "正在获取老人列表", onRetry = null) }
            } else if (errorMsg != null) {
                item {
                    EmptyState(
                        title = errorMsg ?: "加载失败",
                        message = "请检查网络后重试",
                        onRetry = { loadElders() },
                    )
                }
            } else if (elders.isEmpty()) {
                item {
                    EmptyState(
                        title = "暂无老人档案",
                        message = "请点击下方按钮创建老人档案，然后绑定设备",
                        onRetry = null,
                    )
                }
            } else {
                items(elders.size) { index ->
                    val elder = elders[index]
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            elder.elderId?.let { onNavigateToElderDetail(it) }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = elder.name ?: "未命名",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        if (elder.gender != null) {
                                            Text(elder.gender ?: "", fontSize = 12.sp, color = TextSecondary)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("ID: ${elder.elderId ?: "-"}", fontSize = 12.sp, color = OfflineGray)
                                }
                                StatusTag(
                                    text = if (elder.deviceOnline == true) "设备在线" else "设备离线",
                                    color = if (elder.deviceOnline == true) SuccessGreen else OfflineGray,
                                    backgroundColor = if (elder.deviceOnline == true) SuccessGreen.copy(alpha = 0.1f)
                                    else OfflineGray.copy(alpha = 0.1f),
                                )
                            }
                            if (elder.bloodType != null || elder.allergy != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    elder.bloodType?.let {
                                        Text("血型: $it", fontSize = 12.sp, color = TextSecondary)
                                    }
                                    elder.allergy?.let {
                                        Text("过敏: $it", fontSize = 12.sp, color = TextSecondary)
                                    }
                                }
                            }
                            // 删除按钮
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { deleteTarget = elder },
                                colors = ButtonDefaults.textButtonColors(contentColor = AlertRed),
                            ) {
                                Text("删除档案", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                AppPrimaryButton(
                    text = "创建老人档案",
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                AppSecondaryButton(
                    text = "设备绑定与管理",
                    onClick = onNavigateToDeviceBinding,
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
