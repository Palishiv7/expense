package com.moneypulse.app.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a parsed SMS message containing transaction information
 */
@Parcelize
data class TransactionSms(
    val sender: String,
    val body: String,
    val amount: Double,
    val merchantName: String,
    val timestamp: Long,
    val description: String = "", // Optional description
    val category: String = ""     // Optional category
) : Parcelable 