package com.moneypulse.app.domain.model

import androidx.compose.ui.graphics.Color

/**
 * Defines the transaction categories used throughout the app
 * with standardized names and color coding
 */
data class Category(
    val id: String,
    val displayName: String,
    val color: Color,
    val iconResId: Int? = null
)

/**
 * Singleton object providing access to all predefined categories
 */
object Categories {
    // Define all possible categories
    val FOOD = Category(
        id = "food",
        displayName = "Food",
        color = Color(0xFFFF5252) // Red
    )
    
    val TRANSPORT = Category(
        id = "transport",
        displayName = "Transport",
        color = Color(0xFF448AFF) // Blue
    )
    
    val SHOPPING = Category(
        id = "shopping",
        displayName = "Shopping",
        color = Color(0xFFAB47BC) // Purple
    )
    
    val BILLS = Category(
        id = "bills",
        displayName = "Bills",
        color = Color(0xFFFFB300) // Amber
    )
    
    val ENTERTAINMENT = Category(
        id = "entertainment",
        displayName = "Entertainment",
        color = Color(0xFF66BB6A) // Green
    )
    
    val HEALTHCARE = Category(
        id = "healthcare",
        displayName = "Healthcare",
        color = Color(0xFF26C6DA) // Cyan
    )
    
    val OTHER = Category(
        id = "other",
        displayName = "Other",
        color = Color(0xFF78909C) // Blue-grey
    )
    
    // List of all available categories
    val ALL = listOf(
        FOOD,
        TRANSPORT,
        SHOPPING,
        BILLS,
        ENTERTAINMENT,
        HEALTHCARE,
        OTHER
    )
    
    // Get a category by its ID
    fun getById(id: String): Category {
        return ALL.find { it.id == id } ?: OTHER
    }
    
    // Get a category by its display name
    fun getByName(name: String): Category {
        return ALL.find { 
            it.displayName.equals(name, ignoreCase = true) ||
            it.id.equals(name, ignoreCase = true)
        } ?: OTHER
    }
} 