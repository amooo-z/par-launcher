package com.parboard.launcher.model

data class DraggedAppData(
    val packageName: String,
    val activityName: String,
    val source: String, // "PAGE" or "DOCK"
    val sourcePageIndex: Int,
    val sourcePos: Int
)
