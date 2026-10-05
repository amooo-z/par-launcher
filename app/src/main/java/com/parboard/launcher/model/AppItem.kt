package com.parboard.launcher.model

data class AppItem(
    val label: String,
    val packageName: String,
    val activityName: String,
    val normalizedToken: String
)
