package com.mcob.paktool

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

val DarkBg = Color(0xFF030D1A)
val CardBg = Color(0xFF06172C)
val BorderCyan = Color(0xFF133A63)
val NeonCyan = Color(0xFF00E5FF)
val NeonGreen = Color(0xFF00FF66)
val TerminalDark = Color(0xFF021008)
val BtnBlue = Color(0xFF0B5FB5)
val BtnRed = Color(0xFFC62828)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStoragePermission()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = DarkBg, surface = CardBg)) {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    ResponsivePakToolScreen()
                }
            }
        }
    }

    private fun requestStoragePermission() {
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

enum class ToolMode { UNPACK, REPACK }

@Composable
fun ResponsivePakToolScreen() {
    var toolMode by remember { mutableStateOf(ToolMode.UNPACK) }
    var selectedGameType by remember { mutableStateOf("GAMEPATCH") }
    var unpackMethod by remember { mutableStateOf("ALL") }

    var detectedPaks by remember { mutableStateOf(listOf<String>()) }
    var selectedPaks by remember { mutableStateOf(setOf<String>()) }
    var selectAll by remember { mutableStateOf(false) }

    var logMessages by remember { mutableStateOf(listOf("> Engine ready...", "> Storage: /sdcard/MCob/")) }
    var progress by remember { mutableStateOf(0.9f) }
    var isProcessing by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun scanInputPaks() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val py = Python.getInstance()
                val jsonStr = py.getModule("pak_engine").callAttr("get_input_pak_files").toString()
                val jsonArr = JSONArray(jsonStr)
                val list = mutableListOf<String>()
                for (i in 0 until jsonArr.length()) {
                    list.add(jsonArr.getString(i))
                }
                withContext(Dispatchers.Main) {
                    detectedPaks = list
                    if (list.isNotEmpty() && selectedPaks.isEmpty()) {
                        selectedPaks = setOf(list[0])
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    logMessages = logMessages + "Scan Error: ${e.localizedMessage}"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        scanInputPaks()
    }

    LaunchedEffect(logMessages.size) {
        if (logMessages.isNotEmpty()) {
            listState.animateScrollToItem(logMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. TOP HEADER
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderCyan, RoundedCornerShape(10.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("PAK-OBB TOOL", color = NeonCyan, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("VERSION : 4.5\nTOOL : $selectedGameType || ${toolMode.name}", color = Color(0xFF88A0B8), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Card(
                        shape = RoundedCornerShape(4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF092240)),
                        border = BorderStroke(1.dp, BorderCyan)
                    ) {
                        Text("LIGHT", color = NeonCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF8D6E63))
                            .border(1.dp, NeonCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("OBB", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    }
                }
            }
        }

        // 2. CATEGORY SWITCHERS
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("MINI ZSDIC", "MINI OBB", "GAMEPATCH", "ODPAKS").forEach { type ->
                Button(
                    onClick = { selectedGameType = type },
                    modifier = Modifier.weight(1f).height(32.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedGameType == type) BtnBlue else Color(0xFF092240)
                    ),
                    border = BorderStroke(1.dp, BorderCyan),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(type, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                }
            }
        }

        // 3. UNPACK / REPACK TABS
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { toolMode = ToolMode.UNPACK },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (toolMode == ToolMode.UNPACK) BtnBlue else Color(0xFF092240)),
                border = BorderStroke(1.dp, if (toolMode == ToolMode.UNPACK) NeonCyan else BorderCyan)
            ) {
                Text("UNPACK", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick = { toolMode = ToolMode.REPACK },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (toolMode == ToolMode.REPACK) BtnBlue else Color(0xFF092240)),
                border = BorderStroke(1.dp, if (toolMode == ToolMode.REPACK) NeonCyan else BorderCyan)
            ) {
                Text("REPACK", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // 4. PATH INFO CARD
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderCyan, RoundedCornerShape(6.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF051324)),
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Text("• Place Pak In [ /storage/emulated/0/MCob/input/ ]", color = Color(0xFFA2B4C7), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text("• Select Pak & Choose Unpack Type", color = Color(0xFFA2B4C7), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text("• Assets Saved In [ /storage/emulated/0/MCob/unpack/ ]", color = Color(0xFFA2B4C7), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }

        // 5. DETECTED FILES LIST
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(85.dp)
                .border(1.dp, BorderCyan, RoundedCornerShape(6.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(6.dp)
        ) {
            if (detectedPaks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("No .pak in /MCob/input/", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        IconButton(onClick = { scanInputPaks() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonCyan, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.padding(4.dp)) {
                    items(detectedPaks) { pak ->
                        val isChecked = selectedPaks.contains(pak)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPaks = if (isChecked) selectedPaks - pak else selectedPaks + pak
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = if (isChecked) NeonGreen else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = pak,
                                color = if (isChecked) NeonGreen else Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectAll = !selectAll
                                    selectedPaks = if (selectAll) detectedPaks.toSet() else emptySet()
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (selectAll) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = if (selectAll) NeonCyan else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SELECT ALL", color = NeonCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 6. TERMINAL LOG BOX
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, BorderCyan, RoundedCornerShape(6.dp)),
            colors = CardDefaults.cardColors(containerColor = TerminalDark),
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF032238)).padding(vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("LOG BOX : ENGINE READY || METHOD : $unpackMethod", color = NeonCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(6.dp)) {
                    items(logMessages) { msg ->
                        Text(
                            text = msg,
                            color = if (msg.contains("Error") || msg.contains("Exception")) Color.Red else NeonGreen,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                }
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("UNPACKING : ${(progress * 100).toInt()}%", color = NeonCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.CenterHorizontally))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = NeonCyan, trackColor = Color(0xFF05324D))
                }
            }
        }

        // 7. UNPACK TYPES (ALL, SINGLE, MULTI)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ALL", "SINGLE", "MULTI").forEach { method ->
                Button(
                    onClick = { unpackMethod = method },
                    modifier = Modifier.weight(1f).height(34.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (unpackMethod == method) BtnBlue else Color(0xFF092240)
                    ),
                    border = BorderStroke(1.dp, if (unpackMethod == method) NeonCyan else BorderCyan)
                ) {
                    Text(method, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // 8. ACTION BUTTON
        Button(
            onClick = {
                if (selectedPaks.isEmpty()) {
                    logMessages = logMessages + "> Error: Please select at least one .pak file!"
                    return@Button
                }
                isProcessing = true
                progress = 0.2f
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val py = Python.getInstance()
                        val module = py.getModule("pak_engine")
                        selectedPaks.forEach { targetPak ->
                            val resJson = if (toolMode == ToolMode.UNPACK) {
                                module.callAttr("unpack_pak_file", targetPak, unpackMethod).toString()
                            } else {
                                module.callAttr("repack_pak_file", targetPak).toString()
                            }
                            val obj = JSONObject(resJson)
                            val logsArr = obj.getJSONArray("logs")
                            val newLogs = mutableListOf<String>()
                            for (i in 0 until logsArr.length()) {
                                newLogs.add(logsArr.getString(i))
                            }
                            withContext(Dispatchers.Main) {
                                logMessages = logMessages + newLogs
                                progress = 1.0f
                            }
                        }
                        withContext(Dispatchers.Main) {
                            isProcessing = false
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            logMessages = logMessages + "> Exception: ${e.localizedMessage}"
                            isProcessing = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(42.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BtnBlue),
            border = BorderStroke(1.dp, NeonCyan),
            enabled = !isProcessing
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
            } else {
                Text(if (toolMode == ToolMode.UNPACK) "START UNPACKING" else "START REPACKING", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
            }
        }

        // 9. FOOTER
        Text("TG : @Black_MagicYT", color = Color.Red, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
    }
}
