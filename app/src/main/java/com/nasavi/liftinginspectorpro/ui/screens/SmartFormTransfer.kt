package com.nasavi.liftinginspectorpro.ui.screens

// טיפוסי העברת נתונים בין טופס חכם אביזרים לעריכת התסקיר
data class SmartFormTableRow(
    val rowNumber: Int,
    val templateName: String,
    val description: String,
    val manufacturer: String = "",
    val model: String = "",
    val quantity: String,
    val serialNumbers: String,
    val testLoad: String = "",
    val wll: String = ""
)

data class SmartFormDefect(
    val accessoryRowNumber: Int,
    val description: String,
    val fixUntil: String = ""
)

