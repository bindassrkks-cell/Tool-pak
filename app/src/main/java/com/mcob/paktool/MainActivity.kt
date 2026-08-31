package com.mcob.paktool

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
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
val BorderCyan = Color(0xFF0E3864)
val NeonCyan = Color(0xFF00E5FF)
val NeonGreen = Color(0xFF00FF66)
val TerminalDark = Color(0xFF021008)
val BtnBlue = Color(0xFF0B5FB5)
val BtnRed = Color(0xFFC62828)

enum class AppScreen { HOME_MENU, PAK_TOOL_SCREEN }
enum class ToolMode { UNPACK, REPACK }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStoragePermission()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = DarkBg, surface = CardBg)) {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    AppNavigation()
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

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf(AppScreen.HOME_MENU) }

    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
        when (screen) {
            AppScreen.HOME_MENU -> HomeMenuScreen(
                onNavigateToPakTool = { currentScreen = AppScreen.PAK_TOOL_SCREEN }
            )
            AppScreen.PAK_TOOL_SCREEN -> PakToolDetailScreen(
                onNavigateBack = { currentScreen = AppScreen.HOME_MENU }
            )
        }
    }
}

// -------------------------------------------------------------
// SCREEN 1: HOME MENU SCREEN (Matching Screenshot 1)
// -------------------------------------------------------------
@Composable
fun HomeMenuScreen(onNavigateToPakTool: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderCyan, RoundedCornerShape(10.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("PAK-OBB TOOL", color = NeonCyan, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("EXPIRY : 31.08.2026 - 03:39 [ KEY ]", color = Color(0xFFA2B4C7), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("MODE   : ADVANCE", color = Color(0xFFA2B4C7), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Brightness2, contentDescription = "Theme", tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF8D6E63))
                            .border(1.dp, NeonCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("OBB-PAK\nTOOL", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 7.sp, lineHeight = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // 4 Main Action Buttons (Screenshot 1)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Button(
                onClick = { onNavigateToPakTool() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                border = BorderStroke(1.dp, BorderCyan)
            ) {
                Text("PAK-TOOL", color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            Button(
                onClick = { Toast.makeText(context, "LUA-TOOL: Demo Mode (Coming in next update)", Toast.LENGTH_SHORT).show() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                border = BorderStroke(1.dp, BorderCyan)
            ) {
                Text("LUA-TOOL", color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            Button(
                onClick = { Toast.makeText(context, "DUMP ANY PAK: Demo Mode", Toast.LENGTH_SHORT).show() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                border = BorderStroke(1.dp, BorderCyan)
            ) {
                Text("DUMP ANY PAK", color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            Button(
                onClick = { Toast.makeText(context, "BUILD 3TIME PAK: Demo Mode", Toast.LENGTH_SHORT).show() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                border = BorderStroke(1.dp, BorderCyan)
            ) {
                Text("BUILD 3TIME PAK", color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        // Footer Section
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF041224)),
                border = BorderStroke(1.dp, BorderCyan),
                enabled = false
            ) {
                Text("BACK TO MAIN MENU", color = Color(0xFF0E3864), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC))
                ) {
                    Text("TELEGRAM", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                Button(
                    onClick = { },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BtnRed)
                ) {
                    Text("YOUTUBE", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }

            Text(
                text = "DEV BY : @Owner_BlackMagicYT || TG : @Black_MagicYT",
                color = Color(0xFFFFD700),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

// -------------------------------------------------------------
// SCREEN 2: PAK TOOL DETAILED SCREEN (Screenshot 2, 3, 4)
// -------------------------------------------------------------
@Composable
fun PakToolDetailScreen(onNavigateBack: () -> Unit) {
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderCyan, RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("PAK-OBB TOOL", color = NeonCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("VERSION : 4.5\nTOOL : $selectedGameType || ${toolMode.name}", color = Color(0xFF88A0B8), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF8D6E63))
                        .border(1.dp, NeonCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("OBB", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                }
            }
        }

        // 4 Category Mode Buttons
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("MINI ZSDIC", "MINI OBB", "GAMEPATCH", "ODPAKS").forEach { type ->
                Button(
                    onClick = { selectedGameType = type },
                    modifier = Modifier.weight(1f).height(30.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedGameType == type) BtnBlue else Color(0xFF092240)),
                    border = BorderStroke(1.dp, BorderCyan),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(type, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                }
            }
        }

        // UNPACK / REPACK TAB SWITCH
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { toolMode = ToolMode.UNPACK },
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (toolMode == ToolMode.UNPACK) BtnBlue else Color(0xFF092240)),
                border = BorderStroke(1.dp, if (toolMode == ToolMode.UNPACK) NeonCyan else BorderCyan)
            ) {
                Text("UNPACK", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            Button(
                onClick = { toolMode = ToolMode.REPACK },
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (toolMode == ToolMode.REPACK) BtnBlue else Color(0xFF092240)),
                border = BorderStroke(1.dp, if (toolMode == ToolMode.REPACK) NeonCyan else BorderCyan)
            ) {
                Text("REPACK", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        // Path Info Box
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderCyan, RoundedCornerShape(6.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF051324)),
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(modifier = Modifier.padding(5.dp)) {
                Text("• Place Pak In [ /sdcard/MCob/input/ ]", color = Color(0xFFA2B4C7), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text("• Select Pak & Choose Unpack Type", color = Color(0xFFA2B4C7), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text("• Assets Saved In [ /sdcard/MCob/unpack/ ]", color = Color(0xFFA2B4C7), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }

        // File List Selector
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
                .border(1.dp, BorderCyan, RoundedCornerShape(6.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(6.dp)
        ) {
            if (detectedPaks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("No .pak in /MCob/input/", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        IconButton(onClick = { scanInputPaks() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonCyan, modifier = Modifier.size(16.dp))
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
                                .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = if (isChecked) NeonGreen else Color.Gray,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = pak, color = if (isChecked) NeonGreen else Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
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
                                .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (selectAll) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = if (selectAll) NeonCyan else Color.Gray,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SELECT ALL", color = NeonCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Terminal Log Box
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
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF032238)).padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("LOG BOX : ENGINE READY || METHOD : $unpackMethod", color = NeonCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(5.dp)) {
                    items(logMessages) { msg ->
                        Text(
                            text = msg,
                            color = if (msg.contains("Error") || msg.contains("Exception")) Color.Red else NeonGreen,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 13.sp
                        )
                    }
                }
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("UNPACKING : ${(progress * 100).toInt()}%", color = NeonCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.CenterHorizontally))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)), color = NeonCyan, trackColor = Color(0xFF05324D))
                }
            }
        }

        // Method Switchers (ALL, SINGLE, MULTI)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("ALL", "SINGLE", "MULTI").forEach { method ->
                Button(
                    onClick = { unpackMethod = method },
                    modifier = Modifier.weight(1f).height(30.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (unpackMethod == method) BtnBlue else Color(0xFF092240)),
                    border = BorderStroke(1.dp, if (unpackMethod == method) NeonCyan else BorderCyan)
                ) {
                    Text(method, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Start Action Button
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
            modifier = Modifier.fillMaxWidth().height(40.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BtnBlue),
            border = BorderStroke(1.dp, NeonCyan),
            enabled = !isProcessing
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
            } else {
                Text(if (toolMode == ToolMode.UNPACK) "START UNPACKING" else "START REPACKING", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
            }
        }

        // Back to Menu Button
        Button(
            onClick = { onNavigateBack() },
            modifier = Modifier.fillMaxWidth().height(36.dp).padding(top = 2.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF041224)),
            border = BorderStroke(1.dp, BorderCyan)
        ) {
            Text("BACK TO MENU", color = NeonCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }

        Text("DEV BY : @Owner_BlackMagicYT || TG : @Black_MagicYT", color = Color(0xFFFFD700), fontSize = 8.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 1.dp))
    }
}
