package com.moneypulse.app.domain.model

/**
 * Represents a parsed SMS message containing transaction information
 */
data class TransactionSms(
    val sender: String,
    val body: String,
    val amount: Double,
    val merchantName: String,
    val timestamp: Long
) 