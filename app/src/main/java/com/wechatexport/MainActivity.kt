package com.wechatexport

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.wechatexport.engine.DbExtractor
import com.wechatexport.engine.DbMomentsParser
import com.wechatexport.engine.MonthlyExporter
import com.wechatexport.engine.MomentsLoader
import com.wechatexport.engine.Moment
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }

    @Composable
    private fun App() {
        var status by remember { mutableStateOf("一键把 Moments 导出为可读的 Markdown / HTML") }
        var busy by remember { mutableStateOf(false) }

        val importLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            busy = true
            status = "正在解析并转换…"
            Thread {
                try {
                    val (file, head) = copyUriToTemp(uri)
                    val moments: List<Moment> = if (isSqlite(head)) {
                        // User picked the raw WeChat database — parse it directly
                        // instead of feeding a binary file into the JSON loader
                        // (which previously produced a cryptic "unexpected json token" error).
                        val result = DbMomentsParser.parse(file)
                        if (result.moments.isEmpty()) {
                            throw IllegalStateException(
                                "数据库解析到 0 条朋友圈，可能当前微信版本的序列化/protobuf 暂不支持。\n" +
                                result.warnings.joinToString("\n")
                            )
                        }
                        result.moments
                    } else {
                        val text = file.readText(Charsets.UTF_8)
                        try {
                            MomentsLoader.load(text)
                        } catch (e: Exception) {
                            throw IllegalStateException(
                                "所选文件不是有效的 Moments JSON。\n" +
                                "请选择 snskit.py / 桌面 convert 导出的 moments.json，或本机微信数据库 SnsMicroMsg.db。\n" +
                                "原始错误: ${e.message ?: e.javaClass.simpleName}",
                                e
                            )
                        }
                    }
                    file.delete()
                    val summary = MonthlyExporter.exportAll(
                        moments, File(getExternalFilesDir(null), "WechatExport")
                    )
                    runOnUiThread {
                        status = "已导出 ${summary.total} 条（时间倒序，含每月文件）→\n" +
                            "${summary.json.absolutePath}\n" +
                            "${summary.md.absolutePath}\n" +
                            "${summary.html.absolutePath}"
                        busy = false
                        try {
                            shareResults(this@MainActivity, summary.md, summary.html)
                        } catch (e: Exception) {
                            status = "已导出 ${summary.total} 条，但打开分享面板失败: ${e.message ?: e.javaClass.simpleName}"
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        status = "转换失败: ${e.message ?: e.javaClass.simpleName}"
                        busy = false
                    }
                }
            }.start()
        }

        MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF0D47A1))) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(48.dp))
                Text("微信朋友圈导出", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                    enabled = !busy
                ) { Text("① 选择 Moments JSON / 数据库并导出") }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        busy = true
                        status = "检测 ROOT…"
                        Thread {
                            try {
                                if (!DbExtractor.hasRoot()) {
                                    runOnUiThread { status = "未获取 ROOT，无法读取本机数据库"; busy = false }
                                    return@Thread
                                }
                                val dirs = DbExtractor.listHashDirs()
                                val hashDir = dirs.firstOrNull { it.isNotBlank() }
                                if (hashDir == null) {
                                    runOnUiThread { status = "未找到包含 SnsMicroMsg.db 的微信账号目录"; busy = false }
                                    return@Thread
                                }

                                val out = File(getExternalFilesDir(null), "WechatExportDb")
                                val db = DbExtractor.copyDb(hashDir, out)
                                runOnUiThread { status = "数据库已复制，正在解析 $hashDir…" }

                                val result = DbMomentsParser.parse(db)
                                if (result.moments.isEmpty()) {
                                    val detail = result.warnings.joinToString("\n")
                                    runOnUiThread {
                                        status = "数据库解析到 0 条朋友圈。\n$detail\n\n数据库: ${db.absolutePath}"
                                        busy = false
                                    }
                                    return@Thread
                                }

                                val exportDir = File(getExternalFilesDir(null), "WechatExport")
                                val summary = MonthlyExporter.exportAll(result.moments, exportDir)
                                val monthLines = summary.months.take(6)
                                    .joinToString("\n") { (month, count) -> "  $month：$count 条" } +
                                    if (summary.months.size > 6) "\n  …共 ${summary.months.size} 个月" else ""

                                val warningText = if (result.warnings.isEmpty()) "" else
                                    "\n\n警告:\n" + result.warnings.joinToString("\n")
                                runOnUiThread {
                                    status = "已从数据库导出 ${summary.total} 条朋友圈（时间倒序）→\n" +
                                        "${summary.json.absolutePath}\n" +
                                        "${summary.md.absolutePath}\n" +
                                        "${summary.html.absolutePath}\n\n" +
                                        "每月文件（YYYY-MM.md / .html）:\n$monthLines$warningText"
                                    busy = false
                                    try {
                                        shareResults(this@MainActivity, summary.md, summary.html)
                                    } catch (e: Exception) {
                                        status = "已导出 ${summary.total} 条，但打开分享面板失败: ${e.message ?: e.javaClass.simpleName}"
                                    }
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    status = "数据库导出失败: ${e.message ?: e.javaClass.simpleName}"
                                    busy = false
                                }
                            }
                        }.start()
                    },
                    enabled = !busy
                ) { Text("② 从本机微信提取并导出数据库(需ROOT)") }
            }
        }
    }

    private fun copyUriToTemp(uri: Uri): Pair<File, ByteArray> {
        val temp = File(getExternalFilesDir(null), "WechatImport_${System.currentTimeMillis()}.tmp")
        contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { out -> input.copyTo(out) }
        } ?: error("无法读取所选文件")
        val head = ByteArray(16)
        temp.inputStream().use { it.read(head) }
        return temp to head
    }

    private fun isSqlite(head: ByteArray): Boolean {
        // SQLite database files begin with "SQLite format 3\0" (15 chars + NUL).
        val magic = "SQLite format 3".toByteArray(Charsets.US_ASCII)
        if (head.size < magic.size) return false
        for (i in magic.indices) if (head[i] != magic[i]) return false
        return true
    }

    private fun shareResults(activity: Activity, md: File, html: File) {
        val authority = "${activity.packageName}.fileprovider"
        val uris = arrayListOf(
            FileProvider.getUriForFile(activity, authority, md),
            FileProvider.getUriForFile(activity, authority, html)
        )
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, "分享导出结果"))
    }
}
