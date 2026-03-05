package com.rsgd.cvrlogger

data class Note(
    var title: String,
    val date: String,
    val snippet: String,
    val fileName: String,
    var isSelected: Boolean = false,
    var isLocked: Boolean = false
)