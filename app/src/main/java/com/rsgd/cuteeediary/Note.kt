package com.rsgd.cuteeediary

data class Note(
    val title: String,
    val date: String,
    val snippet: String,
    val fileName: String,
    var isSelected: Boolean = false
)