package com.mcob.paktool

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkStoragePermission()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    PakToolScreen()
                }
            }
        }
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }
}

@Composable
fun PakToolScreen() {
    val coroutineScope = rememberCoroutineScope()
    var logText by remember { mutableStateOf("Ready. Storage Base: /sdcard/MCob/") }
    var isLoading by remember { mutableStateOf(false) }

    fun runPythonTask(action: (Python) -> String) {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val py = Python.getInstance()
                val result = action(py)
                withContext(Dispatchers.Main) {
                    logText = result
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    logText = "Error: ${e.localizedMessage}"
                    isLoading = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text("MCOB PAK MODDER", style = MaterialTheme.typography.headlineMedium, color = Color.White)

        Button(
            onClick = {
                runPythonTask { py ->
                    py.getModule("pak_engine").callAttr("init_environment").toString()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("1. Create /sdcard/MCob Folders")
        }

        Button(
            onClick = {
                runPythonTask { py ->
                    py.getModule("pak_engine").callAttr("unpack_pak", "/sdcard/MCob/input.pak").toString()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("2. Unpack (.pak -> /unpack)")
        }

        Button(
            onClick = {
                runPythonTask { py ->
                    py.getModule("pak_engine").callAttr("inject_editor_files").toString()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5))
        ) {
            Text("3. Inject Chams (/editor -> /unpack)")
        }

        Button(
            onClick = {
                runPythonTask { py ->
                    py.getModule("pak_engine").callAttr("repack_pak", "GamePatch_Mod.pak").toString()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) {
            Text("4. Repack -> /repack")
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .padding(top = 10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    Text(text = logText, color = Color.Green, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
