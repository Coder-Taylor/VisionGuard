package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.ElderlyProfile
import com.example.myapplication.VisionDataHub
import com.example.myapplication.api.ElderlyProfileRequest
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.ui.PrimaryText
import com.example.myapplication.ui.ScreenBackground
import com.example.myapplication.ui.SecondaryText
import com.example.myapplication.util.ElderlyPreference
import com.example.myapplication.util.ErrorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ElderlyProfileScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by VisionDataHub.elderlyProfile.collectAsStateWithLifecycle()
    val userProfile by VisionDataHub.userProfile.collectAsStateWithLifecycle()

    var name by rememberSaveable { mutableStateOf(saved.name) }
    var age by rememberSaveable { mutableStateOf(if (saved.age > 0) saved.age.toString() else "") }
    var gender by rememberSaveable { mutableStateOf(saved.gender) }
    var phone by rememberSaveable { mutableStateOf(saved.phone) }
    var medicalNotes by rememberSaveable { mutableStateOf(saved.medicalNotes) }
    var isSaving by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .then(Modifier),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = "老人信息",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = PrimaryText,
                )
            }
        }
        item {
            Text(
                text = "填写被监护老人的基本信息，方便紧急时联系和救助。",
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryText,
            )
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("老人姓名") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = age,
                onValueChange = { if (it.all(Char::isDigit) && it.length <= 3) age = it },
                label = { Text("年龄") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Column {
                Text(
                    text = "性别",
                    style = MaterialTheme.typography.labelLarge,
                    color = SecondaryText,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("男" to Icons.Filled.Male, "女" to Icons.Filled.Female, "其他" to Icons.Filled.Person).forEach { (label, icon) ->
                        val isSelected = gender == label
                        TextButton(
                            onClick = { gender = label },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(icon, contentDescription = null)
                            Text(
                                text = label,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else SecondaryText,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("老人电话") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = medicalNotes,
                onValueChange = { medicalNotes = it },
                label = { Text("医疗备注（过敏史、慢性病等）") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "请填写老人姓名", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSaving = true
                    val profile = ElderlyProfile(
                        name = name.trim(),
                        age = age.toIntOrNull() ?: 0,
                        gender = gender,
                        phone = phone.trim(),
                        medicalNotes = medicalNotes.trim(),
                    )
                    VisionDataHub.updateElderlyProfile(profile)
                    scope.launch {
                        withContext(Dispatchers.IO) { ElderlyPreference.save(context, profile) }
                        try {
                            RetrofitClient.elderlyApi.saveElderlyProfile(
                                userId = userProfile.userId,
                                profile = ElderlyProfileRequest(
                                    name = profile.name,
                                    age = profile.age,
                                    gender = profile.gender,
                                    phone = profile.phone,
                                    medicalNotes = profile.medicalNotes,
                                ),
                            )
                        } catch (e: Exception) {
                            val msg = ErrorHelper.userMessage(e, "saveElderly")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                            isSaving = false
                            return@launch
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        }
                        isSaving = false
                        onBack()
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (isSaving) "保存中…" else "保存老人信息")
            }
        }
    }
}
