package com.parboard.launcher.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.parboard.launcher.R
import com.parboard.launcher.data.AppRepository
import com.parboard.launcher.data.FavoritesRepository
import com.parboard.launcher.model.AppItem
import com.parboard.launcher.util.AppLauncher
import com.parboard.launcher.util.DateFormatter
import com.parboard.launcher.util.DefaultRoleHelper
import com.parboard.launcher.util.SearchEngine

class MainActivity : Activity() {

    private lateinit var appRepository: AppRepository
    private lateinit var favoritesRepository: FavoritesRepository

    private lateinit var homeContainer: View
    private lateinit var drawerRoot: View
    private lateinit var tvPersianDate: TextView
    private lateinit var tvDefaultPrompt: TextView
    private lateinit var favoritesListLayout: LinearLayout
    private lateinit var etSearch: EditText
    private lateinit var tvClearSearch: TextView
    private lateinit var rvApps: RecyclerView
    private lateinit var appAdapter: AppAdapter

    private var allApps: List<AppItem> = emptyList()

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updatePersianDate()
        }
    }

    private var backCallback: OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make window edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        appRepository = AppRepository(this)
        favoritesRepository = FavoritesRepository.create(this)

        initViews()
        setupDrawerRecyclerView()
        setupSearch()
        setupBackHandling()

        loadApps()
    }

    private fun initViews() {
        homeContainer = findViewById(R.id.home_container)
        drawerRoot = findViewById(R.id.included_drawer)
        tvPersianDate = findViewById(R.id.tv_persian_date)
        tvDefaultPrompt = findViewById(R.id.tv_default_prompt)
        favoritesListLayout = findViewById(R.id.favorites_list)

        etSearch = findViewById(R.id.et_search)
        tvClearSearch = findViewById(R.id.tv_clear_search)
        rvApps = findViewById(R.id.rv_apps)

        findViewById<View>(R.id.btn_open_drawer).setOnClickListener {
            openDrawer()
        }

        tvDefaultPrompt.setOnClickListener {
            try {
                startActivity(DefaultRoleHelper.createSetDefaultIntent(this))
            } catch (e: Exception) {
                // Ignore fallback failure
            }
        }
    }

    private fun setupDrawerRecyclerView() {
        appAdapter = AppAdapter(
            onAppClick = { item ->
                AppLauncher.launch(this, item)
            },
            onAppLongClick = { item ->
                showAppOptionsDialog(item)
            }
        )
        rvApps.layoutManager = LinearLayoutManager(this)
        rvApps.setHasFixedSize(true)
        rvApps.adapter = appAdapter
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                val filtered = SearchEngine.filter(allApps, query)
                appAdapter.submitList(filtered)
                tvClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            }
        })

        tvClearSearch.setOnClickListener {
            etSearch.text.clear()
        }
    }

    private fun setupBackHandling() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback = OnBackInvokedCallback {
                if (drawerRoot.visibility == View.VISIBLE) {
                    closeDrawer()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (drawerRoot.visibility == View.VISIBLE) {
            closeDrawer()
        } else {
            // Launcher is root of task stack; stay on home
        }
    }

    private fun openDrawer() {
        drawerRoot.visibility = View.VISIBLE
        etSearch.text.clear()
        appAdapter.submitList(allApps)
        etSearch.requestFocus()

        // Immediate keyboard popup (e.g. ParBoard)
        val insetsController = WindowInsetsControllerCompat(window, etSearch)
        insetsController.show(WindowInsetsCompat.Type.ime())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback?.let {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    it
                )
            }
        }
    }

    private fun closeDrawer() {
        val insetsController = WindowInsetsControllerCompat(window, etSearch)
        insetsController.hide(WindowInsetsCompat.Type.ime())

        val imm = getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(etSearch.windowToken, 0)

        etSearch.clearFocus()
        drawerRoot.visibility = View.GONE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback?.let {
                try {
                    onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
                } catch (e: Exception) {
                    // Ignore unregister exception
                }
            }
        }
    }

    private fun loadApps() {
        allApps = appRepository.loadInstalledApps()
        appAdapter.submitList(allApps)
        refreshFavoritesOnHome()
    }

    private fun refreshFavoritesOnHome() {
        favoritesListLayout.removeAllViews()
        val favorites = favoritesRepository.getFavorites()
        val inflater = LayoutInflater.from(this)

        val deadFavorites = ArrayList<Pair<String, String>>()

        for (fav in favorites) {
            val matchingApp = allApps.firstOrNull { it.packageName == fav.first && it.activityName == fav.second }
            if (matchingApp == null) {
                deadFavorites.add(fav)
                continue
            }

            val view = inflater.inflate(R.layout.item_app, favoritesListLayout, false) as TextView
            view.text = matchingApp.label
            view.textSize = 19f
            view.setPadding(0, 16, 0, 16)

            view.setOnClickListener {
                AppLauncher.launch(this, matchingApp)
            }
            view.setOnLongClickListener {
                showUnpinDialog(matchingApp)
                true
            }
            favoritesListLayout.addView(view)
        }

        for (dead in deadFavorites) {
            favoritesRepository.removeFavorite(dead.first, dead.second)
        }
    }

    private fun showAppOptionsDialog(item: AppItem) {
        val isFav = favoritesRepository.isFavorite(item.packageName, item.activityName)
        val options: Array<CharSequence> = if (isFav) {
            arrayOf(getString(R.string.unpin_from_favorites))
        } else {
            arrayOf(getString(R.string.pin_to_favorites))
        }

        AlertDialog.Builder(this)
            .setTitle(item.label)
            .setItems(options) { _, which ->
                if (which == 0) {
                    if (isFav) {
                        favoritesRepository.removeFavorite(item.packageName, item.activityName)
                        Toast.makeText(this, "از علاقه‌مندی‌ها حذف شد", Toast.LENGTH_SHORT).show()
                    } else {
                        val added = favoritesRepository.addFavorite(item.packageName, item.activityName)
                        if (!added) {
                            Toast.makeText(this, getString(R.string.max_favorites_reached), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "به علاقه‌مندی‌ها اضافه شد", Toast.LENGTH_SHORT).show()
                        }
                    }
                    refreshFavoritesOnHome()
                }
            }
            .show()
    }

    private fun showUnpinDialog(item: AppItem) {
        AlertDialog.Builder(this)
            .setTitle(item.label)
            .setMessage("آیا می‌خواهید این برنامه از علاقه‌مندی‌ها حذف شود؟")
            .setPositiveButton("حذف") { _, _ ->
                favoritesRepository.removeFavorite(item.packageName, item.activityName)
                refreshFavoritesOnHome()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun updatePersianDate() {
        tvPersianDate.text = DateFormatter.getCurrentPersianDate()
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        updatePersianDate()

        // Check default launcher status
        if (!DefaultRoleHelper.isDefaultLauncher(this)) {
            tvDefaultPrompt.visibility = View.VISIBLE
        } else {
            tvDefaultPrompt.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        // Register package updates
        appRepository.registerPackageCallback {
            loadApps()
        }
    }

    override fun onPause() {
        super.onPause()
        appRepository.unregisterPackageCallback()
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(timeTickReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }
}
