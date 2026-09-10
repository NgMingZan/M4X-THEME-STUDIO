package com.m4x.themestudio.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m4x.themestudio.data.ThemeInfo
import com.m4x.themestudio.data.TranslationOptions
import java.io.File

private enum class Screen { Home, Themes, Translate, Create }

@Composable
fun M4XThemeStudioApp(vm: ThemeViewModel) {
    val themes by vm.themes.collectAsState()
    val busy by vm.busy.collectAsState()
    val progress by vm.progress.collectAsState()
    var screen by remember { mutableStateOf(Screen.Home) }
    var selected by remember { mutableStateOf<ThemeInfo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importTheme(uri) { r ->
            message = r.fold({ "Đã nhập ${it.name}" }, { it.message ?: "Mở Theme/Lockscreen thất bại" })
            if (r.isSuccess) screen = Screen.Themes
        }
    }

    val bakLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.convertBak(uri) { r ->
            message = r.fold({ "Đã chuyển BAK/ZIP thành chủ đề ${it.name}" }, { it.message ?: "Chuyển đổi thất bại" })
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(remember { SnackbarHostState() })
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.Home -> HomeScreen(
                    themeCount = themes.size,
                    onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    onBak = { bakLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    onThemes = { screen = Screen.Themes },
                    onCreate = { screen = Screen.Create }
                )
                Screen.Themes -> ThemesScreen(
                    themes = themes,
                    onBack = { screen = Screen.Home },
                    onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    onTranslate = { selected = it; screen = Screen.Translate },
                    onDelete = vm::delete,
                    onExport = { t -> vm.export(t) { r -> message = r.fold({ "Đã lưu vào Download/M4XThemeStudio • Mở Theme Manager để nhập thủ công" }, { it.message ?: "Xuất tệp thất bại" }) } },
                    onApply = { message = "Bản No-Root: hãy Việt hóa → Xuất MTZ → mở Theme Manager và nhập thủ công" }
                )
                Screen.Translate -> TranslateScreen(
                    theme = selected,
                    busy = busy,
                    progress = progress,
                    onBack = { screen = Screen.Themes },
                    onRun = { options ->
                        selected?.let { t ->
                            vm.translate(t, options) { r ->
                                message = r.fold(
                                    { "Xong: ${it.changedFiles} tệp đổi • XML/ảnh ${it.replacements} lượt • OCR ${it.ocrImages} ảnh/${it.ocrTranslatedLines} dòng" },
                                    { it.message ?: "Việt hóa thất bại" }
                                )
                            }
                        }
                    },
                    onExport = {
                        selected?.let { old ->
                            val fresh = themes.firstOrNull { it.id == old.id } ?: old
                            vm.export(fresh) { r -> message = r.fold({ "Đã lưu vào Download/M4XThemeStudio" }, { it.message ?: "Xuất tệp thất bại" }) }
                        }
                    }
                )
                Screen.Create -> CreateScreen(onBack = { screen = Screen.Home }, onChooseBase = {
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                })
            }

            if (busy && screen != Screen.Translate) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 8.dp
                ) {
                    Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(14.dp))
                        Text("Đang xử lý chủ đề…")
                    }
                }
            }

            message?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(3000)
                    message = null
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.inverseSurface
                ) {
                    Text(msg, Modifier.padding(horizontal = 18.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.inverseOnSurface)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(themeCount: Int, onImport: () -> Unit, onBak: () -> Unit, onThemes: () -> Unit, onCreate: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        contentPadding = PaddingValues(top = 36.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Menu, null, Modifier.size(34.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("M4X", color = MaterialTheme.colorScheme.primary, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text("THEME STUDIO", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(34.dp))
            }
        }
        item {
            BigMenuCard(Icons.Default.Add, "Mở Theme / Lockscreen", "Chọn MTZ, ZIP hoặc tệp lockscreen không có đuôi", MaterialTheme.colorScheme.secondary, onImport)
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text("✓ Chế độ NO ROOT: đọc, OCR, dịch, sửa và xuất MTZ hoàn toàn bằng quyền tệp chuẩn Android. Chỉ bước áp dụng theme vào Xiaomi Theme Manager được thực hiện thủ công.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        item {
            BigMenuCard(Icons.Default.Restore, "BAK Converter", "Mở BAK/ZIP của Xiaomi và chuyển thành MTZ để xử lý", MaterialTheme.colorScheme.tertiary, onBak)
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column {
                    SmallMenuRow(Icons.Default.Palette, "Chủ đề", "$themeCount chủ đề đã nhập", onThemes)
                    HorizontalDivider(Modifier.padding(horizontal = 28.dp))
                    SmallMenuRow(Icons.Default.Tune, "Tạo bản Việt hóa", "Ghép và chỉnh các thành phần chủ đề", onCreate)
                }
            }
        }
        item {
            Text("M4X Theme Studio V2.2 NO ROOT • MTZ + XML + OCR ảnh + Lockscreen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BigMenuCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(20.dp), color = tint) {
                Icon(icon, null, Modifier.padding(18.dp).size(30.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.KeyboardArrowDown, null)
        }
    }
}

@Composable
private fun SmallMenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(26.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, Modifier.padding(13.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight, null)
    }
}

@Composable
private fun ThemesScreen(
    themes: List<ThemeInfo>,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onTranslate: (ThemeInfo) -> Unit,
    onDelete: (ThemeInfo) -> Unit,
    onExport: (ThemeInfo) -> Unit,
    onApply: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { TopBar("Chủ đề", onBack) }
        item {
            Surface(Modifier.padding(18.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(22.dp)) {
                    Text("Nhập MTZ / Lockscreen từ bộ nhớ", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Không cần root. Chọn trực tiếp MTZ/ZIP/lockscreen bằng trình chọn tệp Android; tệp lockscreen không có đuôi vẫn được nhận diện bằng chữ ký ZIP.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onImport, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Chọn các chủ đề để nhập")
                    }
                }
            }
        }
        if (themes.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inventory2, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Chưa có chủ đề", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Nhập một tệp .mtz để bắt đầu Việt hóa")
                }
            }
        } else {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(themes, key = { it.id }) { t ->
                        ThemeCard(t, onTranslate, onDelete, onExport, onApply)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(theme: ThemeInfo, onTranslate: (ThemeInfo) -> Unit, onDelete: (ThemeInfo) -> Unit, onExport: (ThemeInfo) -> Unit, onApply: () -> Unit) {
    Card(Modifier.width(220.dp), shape = RoundedCornerShape(22.dp)) {
        Column {
            val bitmap = remember(theme.previewPath) { theme.previewPath?.let { p -> runCatching { BitmapFactory.decodeFile(p) }.getOrNull() } }
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), null, Modifier.fillMaxWidth().height(260.dp), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxWidth().height(260.dp).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Palette, null, Modifier.size(70.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.padding(14.dp)) {
                Text(theme.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${theme.xmlCount} XML • ${theme.imageCount} ảnh • ${formatSize(theme.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { if (theme.translatedPath != null) onExport(theme) else onApply() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (theme.translatedPath != null) "Xuất bản Việt hóa" else "Việt hóa trước")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onTranslate(theme) }) { Text("Dịch") }
                    TextButton(onClick = { onDelete(theme) }) { Text("Xóa", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun TranslateScreen(theme: ThemeInfo?, busy: Boolean, progress: Int, onBack: () -> Unit, onRun: (TranslationOptions) -> Unit, onExport: () -> Unit) {
    var xml by remember { mutableStateOf(true) }
    var locale by remember { mutableStateOf(true) }
    var manifest by remember { mutableStateOf(true) }
    var images by remember { mutableStateOf(true) }
    var custom by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { TopBar("Việt hóa Theme", onBack) }
        item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(theme?.name ?: "Chưa chọn chủ đề", fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text("Engine V2 tự mở MTZ/lockscreen, quét ZIP lồng nhau, nhận diện chữ trong XML/MAML và OCR chữ Trung/Anh trong ảnh rồi chuyển sang tiếng Việt.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                OptionRow("Việt hóa XML", "Dịch chuỗi tiếng Trung/Anh phổ biến trong lockscreen, statusbar…", xml) { xml = it }
                OptionRow("Chuẩn hóa locale", "Đổi zh_CN / zh-CN sang vi_VN / vi-VN", locale) { locale = it }
                OptionRow("Manifest / description", "Việt hóa mô tả và metadata dạng văn bản", manifest) { manifest = it }
                OptionRow("OCR ảnh có chữ", "Tự nhận diện chữ Trung/Anh trong PNG/JPG/WebP rồi ghi chữ Việt trực tiếp lên ảnh", images) { images = it }
                Spacer(Modifier.height(8.dp))
                Text("Không cần Gemini API: bản V2 dùng ML Kit trên máy. Lần đầu có thể cần tải model ngôn ngữ.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(18.dp))
                Text("Từ thay thế riêng", fontWeight = FontWeight.Bold)
                Text("Mỗi dòng: chữ gốc=tiếng Việt. Ví dụ: 解锁=Vuốt để mở khóa", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    placeholder = { Text("原文=Tiếng Việt\nCustom=Tuỳ chỉnh") }
                )
                Spacer(Modifier.height(20.dp))
                if (busy) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("Đang xử lý $progress%")
                }
                Button(
                    onClick = {
                        onRun(
                            TranslationOptions(
                                translateXml = xml,
                                normalizeVietnameseLocale = locale,
                                translateManifest = manifest,
                                translateImages = images,
                                customPairs = parsePairs(custom)
                            )
                        )
                    },
                    enabled = theme != null && !busy,
                    modifier = Modifier.fillMaxWidth().height(58.dp)
                ) {
                    Icon(Icons.Default.Translate, null); Spacer(Modifier.width(8.dp)); Text("Bắt đầu Việt hóa")
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onExport, enabled = theme != null && !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Lưu Theme / Lockscreen ra Downloads")
                }
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked, onChecked)
        }
    }
}

@Composable
private fun CreateScreen(onBack: () -> Unit, onChooseBase: () -> Unit) {
    val modules = listOf(
        Icons.Default.GridView to "Biểu tượng",
        Icons.Default.Lock to "Kiểu màn hình khóa",
        Icons.Default.Notifications to "Thanh trạng thái",
        Icons.Default.Phone to "Điện thoại & danh bạ",
        Icons.Default.Message to "SMS và tin nhắn",
        Icons.Default.Home to "Màn hình chính",
        Icons.Default.Nightlight to "Màn hình luôn bật",
        Icons.Default.FontDownload to "Phông chữ"
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { TopBar("Tạo bản Việt hóa", onBack) }
        item {
            Surface(Modifier.padding(24.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(22.dp)) {
                    Text("Chủ đề gốc", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Chủ đề gốc được giữ nguyên; V1 chỉ thay các tệp văn bản khi bạn chạy Việt hóa.")
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onChooseBase, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Chọn chủ đề gốc") }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                modules.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (icon, label) ->
                            Surface(Modifier.weight(1f).height(120.dp).padding(vertical = 6.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                                    Text(label, fontWeight = FontWeight.Bold, maxLines = 2)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, Modifier.size(32.dp)) }
        Spacer(Modifier.weight(1f))
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(48.dp))
    }
}

private fun parsePairs(text: String): Map<String, String> = buildMap {
    text.lineSequence().forEach { line ->
        val i = line.indexOf('=')
        if (i > 0 && i < line.lastIndex) put(line.substring(0, i).trim(), line.substring(i + 1).trim())
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024f)
    else -> "$bytes B"
}
