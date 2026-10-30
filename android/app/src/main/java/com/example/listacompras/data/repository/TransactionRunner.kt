package com.example.listacompras.data.repository

import androidx.room.withTransaction
import com.example.listacompras.data.local.AppDatabase

/** Runs a block atomically. Abstracted so repository tests do not need a real database. */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactionRunner(private val db: AppDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}
