package com.parboard.launcher.util

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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

    /**
     * Opens system Application Details settings for the given package (for uninstall, storage, etc.).
     */
    fun openAppDetails(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "امکان باز کردن تنظیمات برنامه وجود ندارد", Toast.LENGTH_SHORT).show()
        }
    }
}
