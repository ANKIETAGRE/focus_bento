package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val priority: Int = 1, // 0: Low, 1: Medium, 2: High
    val dueDate: Long? = null, // timestamp in millis
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val userEmail: String = ""
)
