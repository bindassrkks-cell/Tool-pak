package com.rjtool.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

val DarkBg = Color(0xFF101214)
val CardSurface = Color(0xFF191C1F)
val CardStroke = Color(0xFF262B30)
val AccentTeal = Color(0xFF00BFA5)
val AccentDarkTeal = Color(0xFF004D40)
val TextGray = Color(0xFF8E959E)
val AlertRed = Color(0xFFE53935)

fun hasStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

fun scanLocalFiles(folderName: String, extensions: List<String>): List<String> {
    val targetDir = File("/storage/emulated/0/RJTOOL", folderName)
    if (!targetDir.exists()) targetDir.mkdirs()
    val result = mutableListOf<String>()
    try {
        targetDir.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = f.relativeTo(targetDir).path.replace('\\', '/')
            if (extensions.isEmpty() || extensions.any { rel.endsWith(it, ignoreCase = true) }) {
                result.add(rel)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return result.sorted()
}

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
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
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
    val context = LocalContext.current
    val deviceModel = Build.MODEL
    val androidVer = Build.VERSION.RELEASE
    val hasPerm = remember { mutableStateOf(hasStoragePermission(context)) }

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
                IconButton(onClick = { hasPerm.value = hasStoragePermission(context) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = AccentTeal)
                }
            }
        }

        if (!hasPerm.value) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = AlertRed.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AlertRed)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AlertRed)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Storage Permission Missing", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Tap here to allow All Files Access in Settings", color = TextGray, fontSize = 11.sp)
                        }
                    }
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
                    Text("Fast Native Scanner Active • Python 3.10", color = AccentTeal, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
    onRefresh: () -> Unit,
    onBrowseFile: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = AccentTeal)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (onBrowseFile != null) {
                    Button(
                        onClick = onBrowseFile,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentDarkTeal),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick File from Phone", color = Color.White, fontSize = 12.sp)
                    }
                }
                if (itemsList.isEmpty()) {
                    Text("No files detected in directory.\nPlace file or click 'Pick File from Phone'.", color = TextGray, fontSize = 12.sp)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = AccentTeal) }
        },
        containerColor = CardSurface
    )
}

@Composable
fun PakUnpackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detectedPaks by remember { mutableStateOf(listOf<String>()) }
    var selectedPak by remember { mutableStateOf("") }
    var showChooser by remember { mutableStateOf(false) }
    var decryptLuaOnly by remember { mutableStateOf(false) }
    var decompileLua by remember { mutableStateOf(true) }
    var isRunning by remember { mutableStateOf(false) }
    var logMessage by remember { mutableStateOf("Ready to unpack using index structure.") }

    fun reloadPaks() {
        val files = scanLocalFiles("PAK_ORIGINAL", listOf(".pak", ".obb"))
        detectedPaks = files
        if (files.isNotEmpty() && (selectedPak.isEmpty() || !files.contains(selectedPak))) {
            selectedPak = files[0]
        }
    }

    LaunchedEffect(Unit) {
        reloadPaks()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    var displayName = "game_patch.pak"
                    context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex >= 0) {
                            displayName = cursor.getString(nameIndex)
                        }
                    }
                    val destFile = File("/storage/emulated/0/RJTOOL/PAK_ORIGINAL", displayName)
                    destFile.parentFile?.mkdirs()
                    context.contentResolver.openInputStream(it)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Imported: $displayName", Toast.LENGTH_SHORT).show()
                        reloadPaks()
                        selectedPak = displayName
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Import error: " + e.localizedMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    if (showChooser) {
        ItemChooserDialog(
            title = "Choose PAK File",
            itemsList = detectedPaks,
            selectedItem = selectedPak,
            onSelect = { selectedPak = it },
            onRefresh = { reloadPaks() },
            onBrowseFile = { filePickerLauncher.launch(arrayOf("*/*")) },
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
                    Text(if (selectedPak.isNotEmpty()) selectedPak else "None detected (Click Choose)", color = if (selectedPak.isNotEmpty()) AccentTeal else TextGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Button(onClick = { reloadPaks(); showChooser = true }, colors = ButtonDefaults.buttonColors(containerColor = AccentDarkTeal)) {
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
                if (selectedPak.isEmpty()) {
                    Toast.makeText(context, "Please choose a PAK file first!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
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

    fun reload() {
        detectedPaks = scanLocalFiles("PAK_ORIGINAL", listOf(".pak", ".obb"))
        if (detectedPaks.isNotEmpty() && (selectedPak.isEmpty() || !detectedPaks.contains(selectedPak))) {
            selectedPak = detectedPaks[0]
        }
    }

    LaunchedEffect(Unit) { reload() }

    if (showChooser) {
        ItemChooserDialog(
            title = "Choose Target PAK",
            itemsList = detectedPaks,
            selectedItem = selectedPak,
            onSelect = { selectedPak = it },
            onRefresh = { reload() },
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
                Button(onClick = { reload(); showChooser = true }, colors = ButtonDefaults.buttonColors(containerColor = AccentDarkTeal)) {
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

    fun reload() {
        val folder = if (isDecompile) "LUA_ORIGINAL" else "LUA_UNPACK"
        detectedFiles = scanLocalFiles(folder, listOf(".lua", ".luac"))
        if (detectedFiles.isNotEmpty() && (selectedFile.isEmpty() || !detectedFiles.contains(selectedFile))) {
            selectedFile = detectedFiles[0]
        }
    }

    LaunchedEffect(Unit) { reload() }

    if (showChooser) {
        ItemChooserDialog(
            title = "Choose Lua File",
            itemsList = detectedFiles,
            selectedItem = selectedFile,
            onSelect = { selectedFile = it },
            onRefresh = { reload() },
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
                Button(onClick = { reload(); showChooser = true }, colors = ButtonDefaults.buttonColors(containerColor = AccentDarkTeal)) {
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

    fun reload() {
        detectedPaks = scanLocalFiles("RESULT_PAK", listOf(".pak", ".obb"))
        if (detectedPaks.isNotEmpty() && (selectedPak.isEmpty() || !detectedPaks.contains(selectedPak))) {
            selectedPak = detectedPaks[0]
        }
    }

    LaunchedEffect(Unit) { reload() }

    if (showChooser) {
        ItemChooserDialog(
            title = "Choose PAK to Fix Size",
            itemsList = detectedPaks,
            selectedItem = selectedPak,
            onSelect = { selectedPak = it },
            onRefresh = { reload() },
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
                Button(onClick = { reload(); showChooser = true }, colors = ButtonDefaults.buttonColors(containerColor = AccentDarkTeal)) {
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

    fun reload() {
        detectedAssets = scanLocalFiles("PAK_UNPACK", listOf(".uexp", ".uasset"))
        if (detectedAssets.isNotEmpty() && (targetFile.isEmpty() || !detectedAssets.contains(targetFile))) {
            targetFile = detectedAssets[0]
        }
    }

    LaunchedEffect(Unit) { reload() }

    if (showChooser) {
        ItemChooserDialog(
            title = "Choose Asset File",
            itemsList = detectedAssets,
            selectedItem = targetFile,
            onSelect = { targetFile = it },
            onRefresh = { reload() },
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
                    Button(onClick = { reload(); showChooser = true }, colors = ButtonDefaults.buttonColors(containerColor = AccentDarkTeal)) {
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
