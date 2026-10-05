package com.parboard.launcher.util

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.parboard.launcher.model.AppItem

object AppLauncher {

    /**
     * Launches the target application activity with zero-latency transition and new task flag.
     */
    fun launch(context: Context, item: AppItem) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(item.packageName, item.activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(intent)
            if (context is Activity) {
                // Eliminate launch animation latency for instant feel
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    context.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
                } else {
                    @Suppress("DEPRECATION")
                    context.overridePendingTransition(0, 0)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "اجرای برنامه امکان‌پذیر نیست", Toast.LENGTH_SHORT).show()
        }
    }
}
