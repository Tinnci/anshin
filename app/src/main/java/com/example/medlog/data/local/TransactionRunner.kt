package com.example.medlog.data.local

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 事务运行器接口，用于将 Room 事务操作抽象化以便测试。
 */
interface TransactionRunner {
    suspend fun <R> withTransaction(block: suspend () -> R): R
}

@Singleton
class RoomTransactionRunner @Inject constructor(
    private val db: MedLogDatabase,
) : TransactionRunner {
    override suspend fun <R> withTransaction(block: suspend () -> R): R =
        db.withTransaction(block)
}
