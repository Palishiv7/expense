package com.moneypulse.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.moneypulse.app.data.local.util.DateConverter
import java.util.Date

/**
 * Database entity representing a transaction
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val description: String,
    val merchantName: String,
    val category: String,
    @TypeConverters(DateConverter::class)
    val date: Date,
    val type: TransactionType,
    val smsBody: String?,
    val smsSender: String?
)

enum class TransactionType {
    EXPENSE,
    INCOME
} 