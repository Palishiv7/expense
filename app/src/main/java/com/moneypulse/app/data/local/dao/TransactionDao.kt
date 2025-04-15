package com.moneypulse.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moneypulse.app.data.local.entity.TransactionEntity
import com.moneypulse.app.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Data Access Object for the transactions table
 */
@Dao
interface TransactionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long
    
    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)
    
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>
    
    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int): Flow<List<TransactionEntity>>
    
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?
    
    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type AND date BETWEEN :startDate AND :endDate")
    fun getTotalAmountByTypeAndDateRange(
        type: TransactionType,
        startDate: Date,
        endDate: Date
    ): Flow<Double?>
    
    @Query("SELECT * FROM transactions WHERE merchantName LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchTransactions(query: String): Flow<List<TransactionEntity>>
    
    @Query("SELECT DISTINCT category FROM transactions ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>
    
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long): Int
    
    @Query("SELECT * FROM transactions WHERE date >= :startTime ORDER BY date DESC")
    suspend fun getRecentTransactionsByTimeWindow(startTime: Long): List<TransactionEntity>
    
    @Query("SELECT * FROM transactions WHERE merchantName = :merchantName AND ABS(amount - :amount) < 0.01 AND date >= :startTime")
    suspend fun findSimilarTransactions(merchantName: String, amount: Double, startTime: Long): List<TransactionEntity>
    
    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getAllTransactionsByDateRange(startDate: Date, endDate: Date): Flow<List<TransactionEntity>>
    
    @Query("SELECT * FROM transactions WHERE type = :type AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getTransactionsByTypeAndDateRange(type: TransactionType, startDate: Date, endDate: Date): Flow<List<TransactionEntity>>
    
    // Synchronous methods needed for database migration
    
    /**
     * Get all transactions synchronously (used for database migration)
     */
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactionsSync(): List<TransactionEntity>
    
    /**
     * Insert multiple transactions in a single operation (used for database migration)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllTransactions(transactions: List<TransactionEntity>): List<Long>
    
    /**
     * Clear SMS bodies from transactions older than the specified timestamp
     * This reduces database size by removing unnecessary SMS content from old transactions
     * while preserving all transaction details (amount, merchant, date, category)
     * 
     * @param cutoffTimestamp All transactions older than this time will have SMS body cleared
     * @return Number of transactions updated
     */
    @Query("UPDATE transactions SET smsBody = NULL WHERE date < :cutoffTimestamp")
    suspend fun clearSmsBodiesOlderThan(cutoffTimestamp: Long): Int
    
    /**
     * Count transactions with non-empty SMS bodies older than the specified timestamp
     * Used for logging/debugging to see how many records will be cleaned up
     * 
     * @param cutoffTimestamp The threshold timestamp
     * @return Count of transactions that would be cleaned up
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE smsBody IS NOT NULL AND date < :cutoffTimestamp")
    suspend fun countTransactionsWithSmsBodiesOlderThan(cutoffTimestamp: Long): Int
} 