package com.gluconote.ui.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.io.InputStreamReader

@Composable
fun ProfileScreen(navController: NavController) {
    val vm = viewModel<ProfileViewModel>()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = vm.exportText()
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(text.toByteArray(Charsets.UTF_8))
            }
            snackbar.showSnackbar("已生成 CSV，可分享或发送给医生")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.use { InputStreamReader(it).readText() } ?: ""
                val n = vm.importText(text)
                snackbar.showSnackbar("导入完成：新增 $n 条")
            } catch (e: Exception) {
                snackbar.showSnackbar("导入失败，请检查文件格式")
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("我的", style = MaterialTheme.typography.headlineSmall)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("目标血糖范围", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.padding(8.dp))
                    NumberField("空腹下限", ui.fastingMinText, vm::setFastingMin)
                    NumberField("空腹上限", ui.fastingMaxText, vm::setFastingMax)
                    NumberField("餐后下限", ui.postMinText, vm::setPostMin)
                    NumberField("餐后上限", ui.postMaxText, vm::setPostMax)
                    Spacer(Modifier.padding(8.dp))
                    Button(
                        onClick = { vm.save { scope.launch { snackbar.showSnackbar("已保存") } } },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("保存目标范围") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("数据备份", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.padding(8.dp))
                    Button(
                        onClick = { exportLauncher.launch("gluconote_export.csv") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("导出 CSV") }
                    Spacer(Modifier.padding(8.dp))
                    Button(
                        onClick = { importLauncher.launch(arrayOf("text/csv", "*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("导入备份") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("关于与免责声明", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.padding(8.dp))
                    Text(
                        "本应用数据仅供个人日常管理参考，不构成医疗建议，不能替代专业医生的诊断与治疗。如有不适请及时就医。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true
    )
}
