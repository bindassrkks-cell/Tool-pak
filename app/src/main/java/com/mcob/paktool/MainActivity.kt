package com.mcob.paktool

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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

// Screenshot Colors (Cyberpunk Dark Navy Theme)
val DarkBg = Color(0xFF040D1A)
val CardBg = Color(0xFF07182E)
val BorderCyan = Color(0xFF133A63)
val NeonCyan = Color(0xFF00E5FF)
val NeonGreen = Color(0xFF00FF66)
val TerminalDark = Color(0xFF021008)
val BtnBlue = Color(0xFF0B5FB5)
val BtnRed = Color(0xFFC62828)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAllFilesPermission()

        setContent {
            PakAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBg
                ) {
                    MainScreen()
                }
            }
        }
    }

    private fun requestAllFilesPermission() {
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
fun PakAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DarkBg,
            surface = CardBg,
            primary = NeonCyan
        ),
        content = content
    )
}

enum class ScreenState { MENU, PAK_TOOL, LUA_TOOL }
enum class ToolMode { UNPACK, REPACK }

@Composable
fun MainScreen() {
    var currentScreen by remember { mutableStateOf(ScreenState.PAK_TOOL) }
    var toolMode by remember { mutableStateOf(ToolMode.REPACK) }
    var selectedGameType by remember { mutableStateOf("GAMEPATCH") }

    var detectedPaks by remember { mutableStateOf(listOf<String>()) }
    var selectedPak by remember { mutableStateOf("") }
    var logMessages by remember { mutableStateOf(listOf("> Engine ready...", "> Storage initialized: /sdcard/MCob/")) }
    var progress by remember { mutableStateOf(1f) }
    var isProcessing by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun scanPaks() {
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
                    if (list.isNotEmpty() && (selectedPak.isEmpty() || !list.contains(selectedPak))) {
                        selectedPak = list[0]
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    logMessages = logMessages + "Error scanning: ${e.localizedMessage}"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        scanPaks()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. TOP HEADER (Matching Screenshots)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderCyan, RoundedCornerShape(10.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PAK-OBB TOOL",
                        color = NeonCyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "VERSION : 4.5\nTOOL : $selectedGameType || ${toolMode.name}",
                        color = Color(0xFF88A0B8),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { /* Toggle theme */ }) {
                        Icon(Icons.Default.Brightness2, contentDescription = "Theme", tint = Color.White)
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF8D6E63))
                            .border(1.dp, NeonCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("OBB", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. CATEGORY SELECTORS (MINI ZSDIC, MINI OBB, GAMEPATCH, ODPAKS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("MINI ZSDIC", "MINI OBB").forEach { type ->
                Button(
                    onClick = { selectedGameType = type },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedGameType == type) BtnBlue else Color(0xFF092240)
                    ),
                    border = BorderStroke(1.dp, BorderCyan),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text(type, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("GAMEPATCH", "ODPAKS").forEach { type ->
                Button(
                    onClick = { selectedGameType = type },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedGameType == type) BtnBlue else Color(0xFF092240)
                    ),
                    border = BorderStroke(1.dp, BorderCyan),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text(type, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                }
            }
        }

        // 3. UNPACK / REPACK MODE SWITCH (Screenshot 3 & 4)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { toolMode = ToolMode.UNPACK },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (toolMode == ToolMode.UNPACK) BtnBlue else Color(0xFF092240)
                ),
                border = BorderStroke(1.dp, if (toolMode == ToolMode.UNPACK) NeonCyan else BorderCyan)
            ) {
                Text("UNPACK", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { toolMode = ToolMode.REPACK },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (toolMode == ToolMode.REPACK) BtnBlue else Color(0xFF092240)
                ),
                border = BorderStroke(1.dp, if (toolMode == ToolMode.REPACK) NeonCyan else BorderCyan)
            ) {
                Text("REPACK", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }

        // 4. STORAGE INSTRUCTIONS CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderCyan, RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF061426)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(NeonGreen))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Place Pak In [ /sdcard/MCob/input/ ]",
                        color = Color(0xFFA2B4C7),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(NeonGreen))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Place Edited In [ /sdcard/MCob/editor/ ]",
                        color = Color(0xFFA2B4C7),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(NeonGreen))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Output Saved In [ /sdcard/MCob/repack/ ]",
                        color = Color(0xFFA2B4C7),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 5. DETECTED PAK FILES (Radio Selector)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .padding(vertical = 4.dp)
                .border(1.dp, BorderCyan, RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (detectedPaks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "No .pak files found in /MCob/input/",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        IconButton(onClick = { scanPaks() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonCyan)
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(detectedPaks) { pak ->
                        val isSelected = selectedPak == pak
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPak = pak }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFFFFD700) else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = pak,
                                color = if (isSelected) Color(0xFFFFD700) else Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // 6. TERMINAL LOG BOX & PROGRESS (Screenshots 2 & 3)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, BorderCyan, RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = TerminalDark),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Log Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF032238))
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "LOG BOX : ENGINE READY || METHOD : ${toolMode.name}",
                        color = NeonCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Logs Text
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                ) {
                    items(logMessages) { msg ->
                        Text(
                            text = msg,
                            color = if (msg.contains("Error")) Color.Red else NeonGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Progress Bar
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(
                        text = "DONE : ${(progress * 100).toInt()}%",
                        color = NeonCyan,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = NeonCyan,
                        trackColor = Color(0xFF05324D),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 7. ACTION BUTTON (START UNPACK / START REPACK)
        Button(
            onClick = {
                if (selectedPak.isEmpty()) {
                    logMessages = logMessages + "> Error: Please select a .pak file from input!"
                    return@Button
                }
                isProcessing = true
                progress = 0.2f
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val py = Python.getInstance()
                        val module = py.getModule("pak_engine")
                        val resJson = if (toolMode == ToolMode.UNPACK) {
                            module.callAttr("unpack_pak_file", selectedPak).toString()
                        } else {
                            module.callAttr("repack_pak_file", selectedPak, "GamePatch_Mod.pak").toString()
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
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BtnBlue),
            border = BorderStroke(1.dp, NeonCyan),
            enabled = !isProcessing
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            } else {
                Text(
                    text = if (toolMode == ToolMode.UNPACK) "START UNPACKING" else "START REPACKING",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 8. SOCIAL FOOTER (Telegram / Youtube from Screenshot 1)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { /* Open Telegram */ },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC))
            ) {
                Text("TELEGRAM", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            Button(
                onClick = { /* Open YouTube */ },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BtnRed)
            ) {
                Text("YOUTUBE", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        // Dev tag
        Text(
            text = "DEV BY : @Owner_BlackMagicYT || TG : @Black_MagicYT",
            color = Color(0xFFFFD700),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
        )
    }
}
