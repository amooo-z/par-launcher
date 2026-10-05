package com.parboard.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Ultra-lightweight accessibility service used exclusively for invoking the system
 * Recent Apps (Overview) screen via performGlobalAction(GLOBAL_ACTION_RECENTS).
 * Event observation is disabled in configuration to guarantee 0% CPU and 0 MB RAM overhead.
 */
class LauncherAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op: dormant by design
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }

    companion object {
        private var instance: LauncherAccessibilityService? = null

        fun isRunning(): Boolean = instance != null

        fun openRecents(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) == true
        }

        fun requestEnable(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Ignore fallback failure
            }
        }
    }
}
