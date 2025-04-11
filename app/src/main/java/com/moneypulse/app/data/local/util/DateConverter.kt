package com.moneypulse.app.data.local.util

import androidx.room.TypeConverter
import java.util.Date

/**
 * Type converter for Room to convert between Date and Long (timestamp)
 */
class DateConverter {
    
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
} 