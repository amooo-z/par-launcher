package com.parboard.launcher.data

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import com.parboard.launcher.model.AppItem
import com.parboard.launcher.util.SearchEngine
import java.text.Collator
import java.util.Locale

class AppRepository(private val context: Context) {

    private val launcherApps: LauncherApps? = context.getSystemService(LauncherApps::class.java)
    private var registeredCallback: LauncherApps.Callback? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Loads all launchable applications sorted alphabetically by label.
     * Guaranteed zero-bitmap allocation (never touches icon drawables).
     */
    fun loadInstalledApps(): List<AppItem> {
        val userHandle = Process.myUserHandle()
        val activityList: List<LauncherActivityInfo> = try {
            launcherApps?.getActivityList(null, userHandle) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val items = ArrayList<AppItem>(activityList.size)
        val ownPackage = context.packageName

        for (info in activityList) {
            val pkg = info.applicationInfo.packageName
            // Exclude ParLauncher itself from the app drawer list
            if (pkg == ownPackage) continue

            val label = info.label?.toString()?.trim() ?: pkg
            val activityName = info.name
            val normalized = SearchEngine.normalize(label)

            items.add(
                AppItem(
                    label = label,
                    packageName = pkg,
                    activityName = activityName,
                    normalizedToken = normalized
                )
            )
        }

        // Sort alphabetically using Persian/locale collation
        val collator = Collator.getInstance(Locale("fa", "IR"))
        items.sortWith { a, b -> collator.compare(a.label, b.label) }

        return items
    }

    /**
     * Registers a zero-overhead callback for package additions, removals, and changes.
     */
    fun registerPackageCallback(onPackageChanged: () -> Unit) {
        unregisterPackageCallback()
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String?, user: UserHandle?) {
                onPackageChanged()
            }

            override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
                onPackageChanged()
            }

            override fun onPackageChanged(packageName: String?, user: UserHandle?) {
                onPackageChanged()
            }

            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
                onPackageChanged()
            }

            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
                onPackageChanged()
            }
        }
        registeredCallback = callback
        launcherApps?.registerCallback(callback, mainHandler)
    }

    /**
     * Unregisters the callback when launcher lifecycle is stopped/destroyed.
     */
    fun unregisterPackageCallback() {
        registeredCallback?.let {
            launcherApps?.unregisterCallback(it)
            registeredCallback = null
        }
    }
}
