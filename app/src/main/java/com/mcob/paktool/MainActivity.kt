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
    var detectedPaks by remember { mutableStateOf(listOf<String>()) }
    var selectedPak by remember { mutableStateOf("") }
    var logMessages by remember { mutableStateOf(listOf("> Engine ready...", "> Storage: /sdcard/MCob/")) }
    var progress by remember { mutableStateOf(1f) }
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
                    if (list.isNotEmpty() && (selectedPak.isEmpty() || !list.contains(selectedPak))) {
                        selectedPak = list[0]
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
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // HEADER
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
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF8D6E63))
                        .border(1.dp, NeonCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("OBB", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
            }
        }

        // CATEGORY ROW
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("MINI ZSDIC", "MINI OBB", "GAMEPATCH", "ODPAKS").forEach { type ->
                Button(
                    onClick = { selectedGameType = type },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedGameType == type) BtnBlue else Color(0xFF092240)
                    ),
                    border = BorderStroke(1.dp, BorderCyan),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    Text(type, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.White, maxLines = 1)
                }
            }
        }

        // UNPACK / REPACK SWITCH
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { toolMode = ToolMode.UNPACK },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (toolMode == ToolMode.UNPACK) BtnBlue else Color(0xFF092240)),
                border = BorderStroke(1.dp, if (toolMode == ToolMode.UNPACK) NeonCyan else BorderCyan)
            ) {
                Text("UNPACK", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick = { toolMode = ToolMode.REPACK },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (toolMode == ToolMode.REPACK) BtnBlue else Color(0xFF092240)),
                border = BorderStroke(1.dp, if (toolMode == ToolMode.REPACK) NeonCyan else BorderCyan)
            ) {
                Text("REPACK", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // STORAGE INSTRUCTION CARD
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderCyan, RoundedCornerShape(6.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF061426)),
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("• Input  : /sdcard/MCob/input/ (Place .pak here)", color = Color(0xFFA2B4C7), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("• Editor : /sdcard/MCob/editor/ (Place modded .uasset/.uexp)", color = Color(0xFFA2B4C7), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("• Output : /sdcard/MCob/repack/ (Exact input name)", color = Color(0xFFA2B4C7), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }

        // FILE SELECTOR
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(95.dp)
                .padding(vertical = 2.dp)
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
                LazyColumn(modifier = Modifier.padding(6.dp)) {
                    items(detectedPaks) { pak ->
                        val isSelected = selectedPak == pak
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPak = pak }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFFFFD700) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = pak,
                                color = if (isSelected) Color(0xFFFFD700) else Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // TERMINAL LOG BOX
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
                    Text("LOG BOX : ENGINE READY || METHOD : ${toolMode.name}", color = NeonCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
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
                    Text("DONE : ${(progress * 100).toInt()}%", color = NeonCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.CenterHorizontally))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)), color = NeonCyan, trackColor = Color(0xFF05324D))
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ACTION BUTTON
        Button(
            onClick = {
                if (selectedPak.isEmpty()) {
                    logMessages = logMessages + "> Error: Please select a .pak file from input!"
                    return@Button
                }
                isProcessing = true
                progress = 0.3f
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val py = Python.getInstance()
                        val module = py.getModule("pak_engine")
                        val resJson = if (toolMode == ToolMode.UNPACK) {
                            module.callAttr("unpack_pak_file", selectedPak).toString()
                        } else {
                            module.callAttr("repack_pak_file", selectedPak).toString()
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
            modifier = Modifier.fillMaxWidth().height(44.dp),
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

        Spacer(modifier = Modifier.height(4.dp))

        // SOCIAL FOOTER
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { /* Telegram */ },
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC))
            ) {
                Text("TELEGRAM", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Button(
                onClick = { /* YouTube */ },
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BtnRed)
            ) {
                Text("YOUTUBE", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        Text("DEV BY : @Owner_BlackMagicYT || TG : @Black_MagicYT", color = Color(0xFFFFD700), fontSize = 8.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
    }
}
