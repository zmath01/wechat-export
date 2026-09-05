package com.wechatexport.engine

import java.io.File

/** Extracts the WeChat Moments SQLite database from a rooted device. */
object DbExtractor {
    private const val WECHAT_PKG = "com.tencent.mm"
    private const val BASE = "/data/data/$WECHAT_PKG/MicroMsg"

    fun hasRoot(): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val line = p.inputStream.bufferedReader().use { it.readLine().orEmpty() }
        p.waitFor() == 0 && line.contains("uid=0")
    } catch (_: Exception) {
        false
    }

    /** Return only account/hash directories containing a readable Moments DB. */
    fun listHashDirs(): List<String> = try {
        // Single glob expansion inside su's root shell — no quoting pitfalls.
        val command = "ls -1d $BASE/*/SnsMicroMsg.db 2>/dev/null"
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        try {
            p.inputStream.bufferedReader().use { reader ->
                reader.readLines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { it.removePrefix("$BASE/").removeSuffix("/SnsMicroMsg.db") }
            }
        } finally {
            p.waitFor()
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Copy the main DB and optional SQLite sidecars. */
    fun copyDb(hashDir: String, destDir: File): File {
        require(hashDir.isNotBlank()) { "hashDir 不能为空" }
        destDir.mkdirs()
        val base = "$BASE/$hashDir/SnsMicroMsg.db"
        for (ext in listOf("", "-wal", "-shm", ".ini")) {
            val source = "$base$ext"
            val target = File(destDir, "SnsMicroMsg.db$ext")
            try {
                suCat(source, target)
            } catch (e: Exception) {
                if (ext.isEmpty()) throw e
            }
        }
        val result = File(destDir, "SnsMicroMsg.db")
        require(result.isFile && result.length() > 0) {
            "复制后的 SnsMicroMsg.db 不存在或为空"
        }
        return result
    }

    private fun suCat(src: String, dst: File) {
        val quoted = src.replace("'", "'\\''")
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat '$quoted'"))
        try {
            dst.outputStream().use { out -> p.inputStream.copyTo(out) }
            val stderr = p.errorStream.bufferedReader().use { it.readText() }
            val exit = p.waitFor()
            if (exit != 0) {
                dst.delete()
                throw IllegalStateException("ROOT 读取失败: $src${if (stderr.isNotBlank()) " — $stderr" else ""}")
            }
        } catch (e: Exception) {
            dst.delete()
            throw e
        }
    }
}
