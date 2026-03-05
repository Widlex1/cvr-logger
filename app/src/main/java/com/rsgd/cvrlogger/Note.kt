package com.rsgd.cvrlogger

data class Note(
    val title: String,
    val date: String,
    val snippet: String,
    val fileName: String,
    var isSelected: Boolean = false
)