package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val message: String,
    val timestamp: Long
)
