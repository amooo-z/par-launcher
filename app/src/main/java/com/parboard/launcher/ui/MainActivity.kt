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
import com.parboard.launcher.data.FavoritesRepository.LayoutMode
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
    private lateinit var tvFavoritesTitle: TextView
    private lateinit var btnOpenDrawer: View
    private lateinit var rvFavorites: RecyclerView
    private lateinit var favoritesAdapter: FavoritesAdapter

    // Drawer search & selection
    private lateinit var layoutSearchBar: LinearLayout
    private lateinit var layoutSelectionBar: LinearLayout
    private lateinit var etSearch: EditText
    private lateinit var tvClearSearch: TextView
    private lateinit var btnCancelSelection: TextView
    private lateinit var tvSelectionCount: TextView
    private lateinit var btnSelectAll: TextView
    private lateinit var btnAddSelectedToHome: TextView
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

        // Edge-to-edge window
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        appRepository = AppRepository(this)
        favoritesRepository = FavoritesRepository.create(this)

        initViews()
        setupFavoritesRecyclerView()
        setupDrawerRecyclerView()
        setupSearch()
        setupSelectionBar()
        setupBackHandling()

        loadApps()
    }

    private fun initViews() {
        homeContainer = findViewById(R.id.home_container)
        drawerRoot = findViewById(R.id.included_drawer)
        tvPersianDate = findViewById(R.id.tv_persian_date)
        tvDefaultPrompt = findViewById(R.id.tv_default_prompt)
        tvFavoritesTitle = findViewById(R.id.tv_favorites_title)
        btnOpenDrawer = findViewById(R.id.btn_open_drawer)
        rvFavorites = findViewById(R.id.rv_favorites)

        // Drawer components
        layoutSearchBar = findViewById(R.id.layout_search_bar)
        layoutSelectionBar = findViewById(R.id.layout_selection_bar)
        etSearch = findViewById(R.id.et_search)
        tvClearSearch = findViewById(R.id.tv_clear_search)
        btnCancelSelection = findViewById(R.id.btn_cancel_selection)
        tvSelectionCount = findViewById(R.id.tv_selection_count)
        btnSelectAll = findViewById(R.id.btn_select_all)
        btnAddSelectedToHome = findViewById(R.id.btn_add_selected_to_home)
        rvApps = findViewById(R.id.rv_apps)

        btnOpenDrawer.setOnClickListener {
            openDrawer()
        }

        // Long-click on home container or clock opens Settings Dialog to switch modes
        homeContainer.setOnLongClickListener {
            showSettingsDialog()
            true
        }
        findViewById<View>(R.id.clock_date_container).setOnLongClickListener {
            showSettingsDialog()
            true
        }

        tvDefaultPrompt.setOnClickListener {
            try {
                startActivity(DefaultRoleHelper.createSetDefaultIntent(this))
            } catch (e: Exception) {
                // Ignore fallback failure
            }
        }
    }

    private fun setupFavoritesRecyclerView() {
        favoritesAdapter = FavoritesAdapter(
            onItemClick = { item ->
                AppLauncher.launch(this, item)
            },
            onItemLongClick = { item ->
                if (favoritesRepository.getLayoutMode() == LayoutMode.DRAWER) {
                    showUnpinDialog(item)
                } else {
                    showSettingsDialog()
                }
            }
        )
        rvFavorites.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 5)
        rvFavorites.setHasFixedSize(true)
        rvFavorites.adapter = favoritesAdapter
    }

    private fun setupDrawerRecyclerView() {
        appAdapter = AppAdapter(
            onAppClick = { item ->
                AppLauncher.launch(this, item)
            },
            onAppLongClick = { item ->
                showAppOptionsDialog(item)
            },
            onSelectionChanged = { count ->
                tvSelectionCount.text = "$count انتخاب‌شده"
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

    private fun setupSelectionBar() {
        btnCancelSelection.setOnClickListener {
            exitSelectionMode()
        }

        btnSelectAll.setOnClickListener {
            appAdapter.selectAll()
        }

        btnAddSelectedToHome.setOnClickListener {
            val selected = appAdapter.selectedItems.toList()
            if (selected.isNotEmpty()) {
                val pairs = selected.map { Pair(it.packageName, it.activityName) }
                val addedCount = favoritesRepository.addFavorites(pairs)
                Toast.makeText(this, "$addedCount برنامه به صفحه اصلی اضافه شد", Toast.LENGTH_SHORT).show()
                exitSelectionMode()
                closeDrawer()
                refreshFavoritesOnHome()
            } else {
                Toast.makeText(this, "برنامه‌ای انتخاب نشده است", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun enterSelectionMode(initialItem: AppItem? = null) {
        layoutSearchBar.visibility = View.GONE
        layoutSelectionBar.visibility = View.VISIBLE

        // Dismiss keyboard when entering multi-select
        val imm = getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(etSearch.windowToken, 0)

        appAdapter.startSelectionMode(initialItem)
    }

    private fun exitSelectionMode() {
        appAdapter.endSelectionMode()
        layoutSelectionBar.visibility = View.GONE
        layoutSearchBar.visibility = View.VISIBLE
    }

    private fun setupBackHandling() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback = OnBackInvokedCallback {
                handleBackPressedLogic()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!handleBackPressedLogic()) {
            // Launcher root
        }
    }

    private fun handleBackPressedLogic(): Boolean {
        if (drawerRoot.visibility == View.VISIBLE) {
            if (appAdapter.isSelectionMode) {
                exitSelectionMode()
                return true
            }
            closeDrawer()
            return true
        }
        return false
    }

    private fun openDrawer() {
        exitSelectionMode()
        drawerRoot.visibility = View.VISIBLE
        etSearch.text.clear()
        appAdapter.submitList(allApps)
        etSearch.requestFocus()

        // Immediate keyboard popup
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
        exitSelectionMode()
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
        val currentMode = favoritesRepository.getLayoutMode()

        if (currentMode == LayoutMode.ALL_APPS) {
            btnOpenDrawer.visibility = View.GONE
            tvFavoritesTitle.visibility = View.GONE
            favoritesAdapter.submitList(allApps)
        } else {
            btnOpenDrawer.visibility = View.VISIBLE
            tvFavoritesTitle.visibility = View.VISIBLE
            tvFavoritesTitle.text = "برنامه‌های برگزیده"

            val favorites = favoritesRepository.getFavorites()
            val deadFavorites = ArrayList<Pair<String, String>>()
            val favoriteApps = ArrayList<AppItem>()

            for (fav in favorites) {
                val matchingApp = allApps.firstOrNull { it.packageName == fav.first && it.activityName == fav.second }
                if (matchingApp == null) {
                    deadFavorites.add(fav)
                    continue
                }
                favoriteApps.add(matchingApp)
            }

            for (dead in deadFavorites) {
                favoritesRepository.removeFavorite(dead.first, dead.second)
            }

            favoritesAdapter.submitList(favoriteApps)
        }
    }

    private fun showAppOptionsDialog(item: AppItem) {
        val isFav = favoritesRepository.isFavorite(item.packageName, item.activityName)
        val favOption = if (isFav) {
            getString(R.string.unpin_from_favorites)
        } else {
            getString(R.string.pin_to_favorites)
        }

        val options: Array<CharSequence> = arrayOf(
            favOption,
            "انتخاب چندتایی برنامه‌ها..."
        )

        AlertDialog.Builder(this)
            .setTitle(item.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (isFav) {
                            favoritesRepository.removeFavorite(item.packageName, item.activityName)
                            Toast.makeText(this, "از علاقه‌مندی‌ها حذف شد", Toast.LENGTH_SHORT).show()
                        } else {
                            favoritesRepository.addFavorite(item.packageName, item.activityName)
                            Toast.makeText(this, "به علاقه‌مندی‌ها اضافه شد", Toast.LENGTH_SHORT).show()
                        }
                        refreshFavoritesOnHome()
                    }
                    1 -> {
                        enterSelectionMode(item)
                    }
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

    private fun showSettingsDialog() {
        val currentMode = favoritesRepository.getLayoutMode()
        val modeActionText = if (currentMode == LayoutMode.ALL_APPS) {
            "تغییر چیدمان به: با اپ دراور و جستجو"
        } else {
            "تغییر چیدمان به: تمام برنامه‌ها در صفحه اصلی"
        }

        val options: Array<CharSequence> = arrayOf(
            modeActionText,
            getString(R.string.set_as_default_launcher)
        )

        AlertDialog.Builder(this)
            .setTitle("تنظیمات پر لانچر")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val newMode = if (currentMode == LayoutMode.ALL_APPS) {
                            LayoutMode.DRAWER
                        } else {
                            LayoutMode.ALL_APPS
                        }
                        favoritesRepository.setLayoutMode(newMode)
                        val modeName = if (newMode == LayoutMode.ALL_APPS) "تمام برنامه‌ها در صفحه اصلی" else "با اپ دراور"
                        Toast.makeText(this, "حالت چیدمان: $modeName", Toast.LENGTH_SHORT).show()
                        refreshFavoritesOnHome()
                    }
                    1 -> {
                        try {
                            startActivity(DefaultRoleHelper.createSetDefaultIntent(this))
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
            }
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
