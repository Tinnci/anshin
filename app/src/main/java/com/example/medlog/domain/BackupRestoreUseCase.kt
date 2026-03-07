package com.example.medlog.domain

import android.content.Context
import android.net.Uri
import com.example.medlog.data.local.MedLogDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据库备份与恢复用例。
 *
 * 备份：将 Room 数据库文件（及 WAL/SHM）写入用户选择的 URI。
 * 恢复：从用户选择的 URI 读取数据库文件，覆盖当前数据库。
 *
 * 使用 SQLite checkpoint 保证 WAL 日志合并后再复制，确保数据完整性。
 */
@Singleton
class BackupRestoreUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: MedLogDatabase,
) {
    private val dbName = "medlog.db"

    /**
     * 将当前数据库备份到指定 URI。
     *
     * @param uri 用户通过 SAF 选择的目标文件 URI
     * @throws IOException 如果写入失败
     */
    suspend fun backup(uri: Uri) {
        // 强制 WAL checkpoint，将所有日志合并到主数据库文件
        database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")

        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) throw IOException("Database file not found")

        context.contentResolver.openOutputStream(uri)?.use { output ->
            dbFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: throw IOException("Cannot open output stream for URI: $uri")
    }

    /**
     * 从指定 URI 恢复数据库。
     *
     * 恢复后需要重启进程以使 Room 重新打开数据库。
     *
     * @param uri 用户通过 SAF 选择的备份文件 URI
     * @throws IOException 如果读取或写入失败
     * @throws IllegalArgumentException 如果文件不是有效的 SQLite 数据库
     */
    suspend fun restore(uri: Uri) {
        // 1. 读取备份文件到临时文件，验证是否为有效 SQLite
        val tempFile = context.cacheDir.resolve("restore_temp.db")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Cannot open input stream for URI: $uri")

            // 验证 SQLite 魔数（前 16 字节为 "SQLite format 3\000"）
            val header = tempFile.inputStream().use { it.readNBytes(16) }
            val magicString = String(header, 0, minOf(header.size, 15))
            if (!magicString.startsWith("SQLite format 3")) {
                throw IllegalArgumentException("Not a valid SQLite database file")
            }

            // 2. 关闭当前数据库连接
            database.close()

            // 3. 覆盖数据库文件（包括删除 WAL/SHM）
            val dbFile = context.getDatabasePath(dbName)
            val walFile = context.getDatabasePath("$dbName-wal")
            val shmFile = context.getDatabasePath("$dbName-shm")

            tempFile.copyTo(dbFile, overwrite = true)
            walFile.delete()
            shmFile.delete()
        } finally {
            tempFile.delete()
        }
    }
}
