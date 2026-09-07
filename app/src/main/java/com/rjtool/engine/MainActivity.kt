package com.rjtool.engine

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

val DarkBg = Color(0xFF101214)
val CardSurface = Color(0xFF191C1F)
val CardStroke = Color(0xFF262B30)
val AccentTeal = Color(0xFF00BFA5)
val AccentDarkTeal = Color(0xFF004D40)
val TextGray = Color(0xFF8E959E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStoragePermission()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = DarkBg, surface = CardSurface)) {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    RJToolApp()
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

enum class Screen { HOME, PAK_UNPACK, PAK_REPACK, LUA_DECOMPILE, LUA_COMPILE, SIZE_FIXER, HEX_EDITOR }

@Composable
fun RJToolApp() {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                Python.getInstance().getModule("rj_engine").callAttr("init_workspace")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Crossfade(targetState = currentScreen, label = "ScreenNav") { screen ->
        when (screen) {
            Screen.HOME -> HomeScreen(
                onNavigate = { currentScreen = it },
                onExecuteFix = {
                    try {
                        val py = Python.getInstance().getModule("rj_engine")
                        py.callAttr("init_workspace")
                        Toast.makeText(context, "Workspace Synchronized!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: " + e.localizedMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            )
            Screen.PAK_UNPACK -> PakUnpackScreen(onBack = { currentScreen = Screen.HOME })
            Screen.PAK_REPACK -> PakRepackScreen(onBack = { currentScreen = Screen.HOME })
            Screen.LUA_DECOMPILE -> LuaToolScreen(isDecompile = true, onBack = { currentScreen = Screen.HOME })
            Screen.LUA_COMPILE -> LuaToolScreen(isDecompile = false, onBack = { currentScreen = Screen.HOME })
            Screen.SIZE_FIXER -> SizeFixerScreen(onBack = { currentScreen = Screen.HOME })
            Screen.HEX_EDITOR -> HexEditorScreen(onBack = { currentScreen = Screen.HOME })
        }
    }
}

@Composable
fun HomeScreen(onNavigate: (Screen) -> Unit, onExecuteFix: () -> Unit) {
    val deviceModel = Build.MODEL
    val androidVer = Build.VERSION.RELEASE

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(CardSurface).border(1.dp, CardStroke, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("RJTOOL v1.0.59", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("TG @byrj6", color = TextGray, fontSize = 12.sp)
                    }
                }
                IconButton(onClick = { }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = AccentTeal)
                }
            }
        }

        item {
            Text(deviceModel + " • Android " + androidVer, color = TextGray, fontSize = 13.sp)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CardStroke)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Fixed workspace", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("/storage/emulated/0/RJTOOL", color = TextGray, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Real UE4 Engine Active • Python 3.10", color = AccentTeal, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onExecuteFix() },
                colors = CardDefaults.cardColors(containerColor = AccentDarkTeal.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AccentTeal.copy(alpha = 0.3f))
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Execute", color = AccentTeal, fontSize = 12.sp)
                        Text("fix.mainactivity.py", color = AccentTeal, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { ToolCard(Icons.Default.ArrowDownward, "PAK Unpack", "Real Unpack with Exact Hierarchy") { onNavigate(Screen.PAK_UNPACK) } }
        item { ToolCard(Icons.Default.ArrowUpward, "PAK Repack", "EDITTED -> Real UE4 PAK") { onNavigate(Screen.PAK_REPACK) } }
        item { ToolCard(Icons.Default.DataObject, "LUA Decompile", "Real Bytecode Disassembly") { onNavigate(Screen.LUA_DECOMPILE) } }
        item { ToolCard(Icons.Default.Code, "LUA Compile", "Lua source -> Binary bytecode") { onNavigate(Screen.LUA_COMPILE) } }
        item { ToolCard(Icons.Default.CropFree, "Size Fixer", "Auto detect & pad RESULT_PAK") { onNavigate(Screen.SIZE_FIXER) } }
        item { ToolCard(Icons.Default.Edit, "Hex Editor", "Edit .uexp / .uasset & Headshot") { onNavigate(Screen.HEX_EDITOR) } }
    }
}

@Composable
fun ToolCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CardStroke)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(AccentDarkTeal.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = TextGray, fontSize = 12.sp)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGray)
        }
    }
}

@Composable
fun ItemChooserDialog(
    title: String,
    itemsList: List<String>,
    selectedItem: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            if (itemsList.isEmpty()) {
                Text("No files detected in directory.", color = TextGray)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                    items(itemsList) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item); onDismiss() }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (item == selectedItem),
                                onClick = { onSelect(item); onDismiss() },
                                colors = RadioButtonDefaults.colors(selectedColor = AccentTeal)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(item, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Confirm", color = AccentTeal) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextGray) }
        },
        containerColor = CardSurface
    )
}

@Composable
fun PakUnpackScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var detectedPaks by remember { mutableStateOf(listOf<String>()) }
    var selectedPak by remember { mutableStateOf("") }
    var showChooser by remember { mutableStateOf(false) }
    var decryptLuaOnly by remember { mutableStateOf(false) }
    var decompileLua by remember { mutableStateOf(true) }
    var isRunning by remember { mutableStateOf(false) }
    var logMessage by remember { mutableStateOf("Ready to unpack using index structure.") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val py = Python.getInstance().getModule("rj_engine")
                val listJson = py.callAttr("get_folder_files", "PAK_ORIGINAL", arrayOf(".pak", ".obb")).toString()
                val arr = JSONArray(listJson)
                val paks = mutableListOf<String>()
                for (i in 0 until arr.length()) paks.add(arr.getString(i))
                withContext(Dispatchers.Main) {
                    detectedPaks = paks
                    if (paks.isNotEmpty()) selectedPak = paks[0]
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (showChooser) {
        ItemChooserDialog(
            title = "Choose PAK from PAK_ORIGINAL",
            itemsList = detectedPaks,
            selectedItem = selectedPak,
            onSelect = { selectedPak = it },
            onDismiss = { showChooser = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeaderNav("PAK Unpack", "Real Unreal Extraction Engine", onBack)

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("PAK_ORIGINAL", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(if (selectedPak.isNotEmpty()) selectedPak else "None detected", color = TextGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Button(onClick = { showChooser = true }, colors = ButtonDefaults.buttonColors(containerColor = AccentDarkTeal)) {
                    Text("Choose", color = Color.White, fontSize = 11.sp)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Output Directory", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("/storage/emulated/0/RJTOOL/PAK_UNPACK", color = TextGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = decryptLuaOnly, onCheckedChange = { decryptLuaOnly = it }, colors = CheckboxDefaults.colors(checkedColor = AccentTeal))
                    Text("Decrypt .lua files only", color = Color.White, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = decompileLua, onCheckedChange = { decompileLua = it }, colors = CheckboxDefaults.colors(checkedColor = AccentTeal))
                    Text("Disassemble Lua bytecode into real code", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        Button(
            onClick = {
                if (selectedPak.isEmpty()) return@Button
                isRunning = true
                logMessage = "Unpacking real PAK chunks & index hierarchy..."
                scope.launch(Dispatchers.IO) {
                    try {
                        val py = Python.getInstance().getModule("rj_engine")
                        val res = py.callAttr("unpack_pak", selectedPak, decryptLuaOnly, decompileLua).toString()
                        val obj = JSONObject(res)
                        withContext(Dispatchers.Main) {
                            if (obj.getString("status") == "success") {
                                val count = obj.getInt("count")
                                val bytes = obj.getLong("total_bytes")
                                logMessage = "✅ Successfully Unpacked Real Files!\nTotal Files: " + count + "\nTotal Size: " + (bytes / 1024) + " KB\nTarget: " + obj.getString("target_dir")
                            } else {
                                logMessage = "Error: " + obj.getString("message")
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { logMessage = "Error: " + e.localizedMessage }
                    } finally {
                        withContext(Dispatchers.Main) { isRunning = false }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) CardStroke else AccentDarkTeal),
            enabled = !isRunning && selectedPak.isNotEmpty()
        ) {
            Text(if (isRunning) "Unpacking..." else "Unpack PAK", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        TerminalBox(logMessage)
    }
}

@Composable
fun PakRepackScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var detectedPaks by remember { mutableStateOf(listOf<String>()) }
    var selectedPak by remember { mutableStateOf("") }
    var showChooser by remember { mutableStateOf(false) }
    var statusLog by remember { mutableStateOf("Ready to repack modified tree into authentic UE4 PAK") }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val py = Python.getInstance().getModule("rj_engine")
                val listJson = py.callAttr("get_folder_files", "PAK_ORIGINAL", arrayOf(".pak", ".obb")).toString()
                val arr = JSONArray(listJson)
                val paks = mutableListOf<String>()
                for (i in 0 until arr.length()) paks.add(arr.getString(i))
                withContext(Dispatchers.Main) {
                    detectedPaks = paks
                    if (paks.isNotEmpty()) selectedPak = paks[0]
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (showChooser) {
        ItemChooserDialog(
            title = "Choose PAK to Repack",
            itemsList = detectedPaks,
            selectedItem = selectedPak,
            onSelect = { selectedPak = it },
            onDismiss = { showChooser = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeaderNav("PAK Repack", "Authentic UE4 Archive Builder", onBack)

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Target PAK", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (selectedPak.isNotEmpty()) selectedPak else "None detected", color = AccentTeal, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Button(onClick = { showChooser = true }, colors = ButtonDefaults.buttonColors(containerColor = AccentDarkTeal)) {
                    Text("Choose", color = Color.White, fontSize = 11.sp)
                }
            }
        }

        Button(
            onClick = {
                if (selectedPak.isEmpty()) return@Button
                isProcessing = true
                statusLog = "Repacking into real UE4 PAK format..."
                scope.launch(Dispatchers.IO) {
                    try {
                        val py = Python.getInstance().getModule("rj_engine")
                        val res = py.callAttr("repack_pak", selectedPak).toString()
                        val obj = JSONObject(res)
                        withContext(Dispatchers.Main) {
                            statusLog = "✅ Successfully Repacked!\nPacked Files: " + obj.getInt("packed_files") + "\nSize: " + obj.getLong("size_bytes") + " bytes\nOutput: " + obj.getString("output")
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { statusLog = "Error: " + e.localizedMessage }
                    } finally {
                        withContext(Dispatchers.Main) { isProcessing = false }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentDarkTeal),
            enabled = !isProcessing && selectedPak.isNotEmpty()
        ) {
            Text("Start Repack", color = Color.White, fontWeight = FontWeight.Bold)
        }

        TerminalBox(statusLog)
    }
}

@Composable
fun LuaToolScreen(isDecompile: Boolean, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var detectedFiles by remember { mutableStateOf(listOf<String>()) }
    var selectedFile by remember { mutableStateOf("") }
    var showChooser by remember { mutableStateOf(false) }
    var statusLog by remember { mutableStateOf(if (isDecompile) "Disassembles raw bytecode into real function logic." else "Compiles Lua code into binary bytecode container.") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val py = Python.getInstance().getModule("rj_engine")
                val folder = if (isDecompile) "LUA_ORIGINAL" else "LUA_UNPACK"
                val listJson = py.callAttr("get_folder_files", folder, arrayOf(".lua", ".luac")).toString()
                val arr = JSONArray(listJson)
                val files = mutableListOf<String>()
                for (i in 0 until arr.length()) files.add(arr.getString(i))
                withContext(Dispatchers.Main) {
                    detectedFiles = files
                    if (files.isNotEmpty()) selectedFile = files[0]
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (showChooser) {
        ItemChooserDialog(
            title = "Choose Lua File",
            itemsList = detectedFiles,
            selectedItem = selectedFile,
            onSelect = { selectedFile = it },
            onDismiss = { showChooser = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeaderNav(if (isDecompile) "LUA Decompile" else "LUA Compile", if (isDecompile) "Real Bytecode Disassembly" else "Lua Source -> Bytecode", onBack)

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Target File", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (selectedFile.isNotEmpty()) selectedFile else "None detected", color = AccentTeal, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Button(onClick = { showChooser = true }, colors = ButtonDefaults.buttonColors(containerColor = AccentDarkTeal)) {
                    Text("Choose", color = Color.White, fontSize = 11.sp)
                }
            }
        }

        Button(
            onClick = {
                if (selectedFile.isEmpty()) return@Button
                scope.launch(Dispatchers.IO) {
                    try {
                        val py = Python.getInstance().getModule("rj_engine")
                        val res = if (isDecompile) py.callAttr("lua_decompile", selectedFile).toString() else py.callAttr("lua_compile", selectedFile).toString()
                        withContext(Dispatchers.Main) { statusLog = res }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { statusLog = "Error: " + e.localizedMessage }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentDarkTeal),
            enabled = selectedFile.isNotEmpty()
        ) {
            Text(if (isDecompile) "Disassemble to /LUA_UNPACK/" else "Compile to /EDITTED/", color = Color.White, fontWeight = FontWeight.Bold)
        }

        TerminalBox(statusLog)
    }
}

@Composable
fun SizeFixerScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var detectedPaks by remember { mutableStateOf(listOf<String>()) }
    var selectedPak by remember { mutableStateOf("") }
    var showChooser by remember { mutableStateOf(false) }
    var resultLog by remember { mutableStateOf("Matches RESULT_PAK byte size with PAK_ORIGINAL to prevent game crash.") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val py = Python.getInstance().getModule("rj_engine")
                val listJson = py.callAttr("get_folder_files", "RESULT_PAK", arrayOf(".pak", ".obb")).toString()
                val arr = JSONArray(listJson)
                val paks = mutableListOf<String>()
                for (i in 0 until arr.length()) paks.add(arr.getString(i))
                withContext(Dispatchers.Main) {
                    detectedPaks = paks
                    if (paks.isNotEmpty()) selectedPak = paks[0]
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (showChooser) {
        ItemChooserDialog(
            title = "Choose PAK to Fix Size",
            itemsList = detectedPaks,
            selectedItem = selectedPak,
            onSelect = { selectedPak = it },
            onDismiss = { showChooser = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeaderNav("Size Fixer", "Auto detect & pad RESULT_PAK", onBack)

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Target PAK", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (selectedPak.isNotEmpty()) selectedPak else "None detected", color = AccentTeal, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Button(onClick = { showChooser = true }, colors = ButtonDefaults.buttonColors(containerColor = AccentDarkTeal)) {
                    Text("Choose", color = Color.White, fontSize = 11.sp)
                }
            }
        }

        Button(
            onClick = {
                if (selectedPak.isEmpty()) return@Button
                scope.launch(Dispatchers.IO) {
                    try {
                        val py = Python.getInstance().getModule("rj_engine")
                        val res = py.callAttr("smart_size_fix", selectedPak).toString()
                        withContext(Dispatchers.Main) { resultLog = res }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { resultLog = "Error: " + e.localizedMessage }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentDarkTeal),
            enabled = selectedPak.isNotEmpty()
        ) {
            Text("Auto Pad & Match Exact Size", color = Color.White, fontWeight = FontWeight.Bold)
        }

        TerminalBox(resultLog)
    }
}

@Composable
fun HexEditorScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var detectedAssets by remember { mutableStateOf(listOf<String>()) }
    var targetFile by remember { mutableStateOf("BP_PlayerPawn.uexp") }
    var showChooser by remember { mutableStateOf(false) }
    var multiplier by remember { mutableStateOf("2.5") }
    var statusLog by remember { mutableStateOf("Injects IEEE-754 float headshot multiplier into asset binaries.") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val py = Python.getInstance().getModule("rj_engine")
                val listJson = py.callAttr("get_folder_files", "PAK_UNPACK", arrayOf(".uexp", ".uasset")).toString()
                val arr = JSONArray(listJson)
                val assets = mutableListOf<String>()
                for (i in 0 until arr.length()) assets.add(arr.getString(i))
                withContext(Dispatchers.Main) {
                    detectedAssets = assets
                    if (assets.isNotEmpty()) targetFile = assets[0]
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (showChooser) {
        ItemChooserDialog(
            title = "Choose Asset File",
            itemsList = detectedAssets,
            selectedItem = targetFile,
            onSelect = { targetFile = it },
            onDismiss = { showChooser = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeaderNav("Hex Editor", "Binary Asset Modifier", onBack)

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Target Asset", color = Color.White, fontWeight = FontWeight.Bold)
                    Button(onClick = { showChooser = true }, colors = ButtonDefaults.buttonColors(containerColor = AccentDarkTeal)) {
                        Text("Choose", color = Color.White, fontSize = 11.sp)
                    }
                }
                Text(targetFile, color = AccentTeal, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = multiplier,
                    onValueChange = { multiplier = it },
                    label = { Text("Headshot Multiplier (Float)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentTeal,
                        focusedLabelColor = AccentTeal,
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White
                    )
                )
            }
        }

        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    try {
                        val py = Python.getInstance().getModule("rj_engine")
                        val res = py.callAttr("hex_patch_headshot", targetFile, multiplier.toDoubleOrNull() ?: 2.5).toString()
                        withContext(Dispatchers.Main) { statusLog = "✅ Successfully Patched:\n" + res }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { statusLog = "Error: " + e.localizedMessage }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentDarkTeal)
        ) {
            Text("Apply Headshot Patch", color = Color.White, fontWeight = FontWeight.Bold)
        }

        TerminalBox(statusLog)
    }
}

@Composable
fun HeaderNav(title: String, subtitle: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.clickable { onBack() }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = AccentTeal)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Back", color = AccentTeal, fontSize = 14.sp)
        }
        Text("RJTOOL v1.0.59", color = Color.White, fontWeight = FontWeight.Bold)
    }
    Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text(subtitle, color = TextGray, fontSize = 12.sp)
}

@Composable
fun TerminalBox(text: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardSurface), border = BorderStroke(1.dp, CardStroke)) {
        Text(text, color = AccentTeal, modifier = Modifier.padding(12.dp), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}
