package com.parboard.launcher.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.DragEvent
import android.view.Gravity
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.parboard.launcher.R
import com.parboard.launcher.data.AppRepository
import com.parboard.launcher.data.FavoritesRepository
import com.parboard.launcher.data.FavoritesRepository.LayoutMode
import com.parboard.launcher.model.AppItem
import com.parboard.launcher.model.DraggedAppData
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
    private lateinit var rvHomePager: RecyclerView
    private lateinit var layoutPageDots: LinearLayout
    private lateinit var btnAddPage: TextView
    private lateinit var rvDock: RecyclerView
    private lateinit var homePagerAdapter: HomePagerAdapter
    private lateinit var dockAdapter: FavoritesAdapter
    private lateinit var pagerSnapHelper: PagerSnapHelper

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
    private var lastPageFlipTime: Long = 0L

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
        setupHomePager()
        setupDock()
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
        rvHomePager = findViewById(R.id.rv_home_pager)
        layoutPageDots = findViewById(R.id.layout_page_dots)
        btnAddPage = findViewById(R.id.btn_add_page)
        rvDock = findViewById(R.id.rv_dock)

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

        findViewById<View>(R.id.btn_settings).setOnClickListener {
            showSettingsDialog()
        }

        btnAddPage.setOnClickListener {
            val newPageIdx = favoritesRepository.addEmptyPage()
            refreshFavoritesOnHome()
            rvHomePager.post {
                rvHomePager.smoothScrollToPosition(newPageIdx)
                updatePageDots(newPageIdx, homePagerAdapter.itemCount)
            }
            Toast.makeText(this, "صفحه جدید اضافه شد", Toast.LENGTH_SHORT).show()
        }

        tvDefaultPrompt.setOnClickListener {
            try {
                startActivity(DefaultRoleHelper.createSetDefaultIntent(this))
            } catch (e: Exception) {
                // Ignore fallback failure
            }
        }
    }

    private fun setupHomePager() {
        homePagerAdapter = HomePagerAdapter(
            onAppClick = { item ->
                AppLauncher.launch(this, item)
            },
            onItemDroppedOnPage = { dragData, targetPage, targetPos ->
                handleDropOnPage(dragData, targetPage, targetPos)
            },
            colorProvider = { pkg ->
                favoritesRepository.getIconColor(pkg)
            },
            onColorPickerClick = { item ->
                showColorPickerDialog(item)
            }
        )

        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvHomePager.layoutManager = layoutManager
        rvHomePager.adapter = homePagerAdapter

        pagerSnapHelper = PagerSnapHelper()
        pagerSnapHelper.attachToRecyclerView(rvHomePager)

        rvHomePager.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                homePagerAdapter.hideAllBadges()
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val snapView = pagerSnapHelper.findSnapView(layoutManager)
                    if (snapView != null) {
                        val currentPos = layoutManager.getPosition(snapView)
                        updatePageDots(currentPos, homePagerAdapter.itemCount)
                    }
                }
            }
        })

        // Edge scrolling between pages during Drag & Drop
        rvHomePager.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_LOCATION -> {
                    val width = rvHomePager.width
                    val x = event.x
                    val now = System.currentTimeMillis()
                    if (now - lastPageFlipTime > 550) {
                        val currentPos = layoutManager.findFirstVisibleItemPosition()
                        val totalPages = homePagerAdapter.itemCount
                        val edgeMargin = 65 * resources.displayMetrics.density

                        if (x > width - edgeMargin) {
                            if (currentPos < totalPages - 1) {
                                rvHomePager.smoothScrollToPosition(currentPos + 1)
                                lastPageFlipTime = now
                            }
                        } else if (x < edgeMargin) {
                            if (currentPos > 0) {
                                rvHomePager.smoothScrollToPosition(currentPos - 1)
                                lastPageFlipTime = now
                            }
                        }
                    }
                    true
                }
                DragEvent.ACTION_DROP -> {
                    val dragData = event.localState as? DraggedAppData ?: return@setOnDragListener false
                    val currentPos = layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
                    rvHomePager.post {
                        handleDropOnPage(dragData, currentPos, targetPos = -1)
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    rvHomePager.post {
                        refreshFavoritesOnHome()
                        refreshDock()
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun setupDock() {
        dockAdapter = FavoritesAdapter(
            onItemClick = { item ->
                AppLauncher.launch(this, item)
            },
            isDock = true,
            colorProvider = { pkg ->
                favoritesRepository.getIconColor(pkg)
            },
            onColorPickerClick = { item ->
                showColorPickerDialog(item)
            },
            onDragStarted = {
                homePagerAdapter.hideAllBadges()
            }
        )

        rvDock.layoutManager = object : GridLayoutManager(this, 5) {
            override fun canScrollVertically(): Boolean = false
        }
        rvDock.overScrollMode = View.OVER_SCROLL_NEVER
        rvDock.adapter = dockAdapter

        // Listen for drops into bottom dock
        rvDock.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DROP -> {
                    val dragData = event.localState as? DraggedAppData ?: return@setOnDragListener false
                    val child = rvDock.findChildViewUnder(event.x, event.y)
                    val dropPos = if (child != null) rvDock.getChildAdapterPosition(child).coerceAtLeast(0) else -1
                    rvDock.post {
                        handleDropOnDock(dragData, dropPos)
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    rvDock.post {
                        refreshDock()
                    }
                    true
                }
                else -> true
            }
        }

        // Listen for drops outside dock (dragging downwards or onto bottom bar) to remove from dock
        btnOpenDrawer.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DROP -> {
                    val dragData = event.localState as? DraggedAppData ?: return@setOnDragListener false
                    btnOpenDrawer.post {
                        handleDropOutsideDock(dragData)
                    }
                    true
                }
                else -> true
            }
        }

        homeContainer.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DROP -> {
                    val dragData = event.localState as? DraggedAppData ?: return@setOnDragListener false
                    homeContainer.post {
                        handleDropOutsideDock(dragData)
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    homeContainer.post {
                        refreshFavoritesOnHome()
                        refreshDock()
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun handleDropOutsideDock(dragData: DraggedAppData) {
        if (dragData.source == "DOCK") {
            favoritesRepository.removeDockAppAt(dragData.sourcePos)
            refreshDock()
            Toast.makeText(this, "برنامه از داک حذف شد", Toast.LENGTH_SHORT).show()
        } else if (dragData.source == "PAGE" && favoritesRepository.getLayoutMode() == LayoutMode.DRAWER) {
            favoritesRepository.removeFavoriteAt(dragData.sourcePageIndex, dragData.sourcePos)
            refreshFavoritesOnHome()
            Toast.makeText(this, "از صفحه اصلی حذف شد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleDropOnDock(dragData: DraggedAppData, dropPos: Int) {
        val dockApps = favoritesRepository.getDockApps()
        if (dragData.source == "DOCK") {
            // Reordering within dock
            val target = if (dropPos != -1) dropPos.coerceIn(0, (dockApps.size - 1).coerceAtLeast(0)) else (dockApps.size - 1).coerceAtLeast(0)
            favoritesRepository.swapDockApps(dragData.sourcePos, target)
            refreshDock()
        } else {
            // Dragged from home page into dock
            if (dockApps.size >= 5) {
                Toast.makeText(this, "داک پر است (حداکثر ۵ برنامه)", Toast.LENGTH_SHORT).show()
                return
            }
            val item = favoritesRepository.removeFavoriteAt(dragData.sourcePageIndex, dragData.sourcePos) ?: return
            val target = if (dropPos != -1) dropPos else dockApps.size
            favoritesRepository.addDockAppAt(target, item)
            refreshDock()
            refreshFavoritesOnHome()
            Toast.makeText(this, "به داک اضافه شد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleDropOnPage(dragData: DraggedAppData, targetPage: Int, targetPos: Int) {
        if (dragData.source == "DOCK") {
            // Dragged OUT of dock onto home page
            val item = favoritesRepository.removeDockAppAt(dragData.sourcePos) ?: return
            val pos = if (targetPos != -1) targetPos else 0
            favoritesRepository.addFavoriteAt(targetPage, pos, item)
            refreshDock()
            refreshFavoritesOnHome()
            Toast.makeText(this, "از داک به صفحه منتقل شد", Toast.LENGTH_SHORT).show()
        } else {
            // Moving between pages or within same page
            val pos = if (targetPos != -1) targetPos else 0
            favoritesRepository.moveFavorite(
                fromPage = dragData.sourcePageIndex,
                fromPos = dragData.sourcePos,
                toPage = targetPage,
                toPos = pos
            )
            refreshFavoritesOnHome()
        }
    }

    private fun updatePageDots(currentPage: Int, totalPages: Int) {
        if (totalPages <= 1) {
            layoutPageDots.visibility = View.GONE
            return
        }

        layoutPageDots.visibility = View.VISIBLE
        layoutPageDots.removeAllViews()

        val dotSize = (6 * resources.displayMetrics.density).toInt()
        val dotMargin = (4 * resources.displayMetrics.density).toInt()

        for (i in 0 until totalPages) {
            val dot = View(this).apply {
                val params = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    setMargins(dotMargin, 0, dotMargin, 0)
                }
                layoutParams = params
                setBackgroundResource(
                    if (i == currentPage) R.drawable.dot_active else R.drawable.dot_inactive
                )
            }
            layoutPageDots.addView(dot)
        }
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
        } else {
            btnOpenDrawer.visibility = View.VISIBLE
            tvFavoritesTitle.visibility = View.VISIBLE
            tvFavoritesTitle.text = "برنامه‌های برگزیده"
        }

        val storedPages = favoritesRepository.getPages().map { it.toMutableList() }.toMutableList()

        if (currentMode == LayoutMode.ALL_APPS && allApps.isNotEmpty()) {
            val placedPackages = HashSet<String>()
            for (page in storedPages) {
                for (item in page) {
                    placedPackages.add(item.first)
                }
            }
            val missingApps = allApps.filter { !placedPackages.contains(it.packageName) }
            if (missingApps.isNotEmpty()) {
                var missingIdx = 0
                if (storedPages.isEmpty()) {
                    storedPages.add(mutableListOf())
                }
                for (page in storedPages) {
                    while (page.size < 20 && missingIdx < missingApps.size) {
                        val app = missingApps[missingIdx++]
                        page.add(Pair(app.packageName, app.activityName))
                    }
                }
                while (missingIdx < missingApps.size) {
                    val newPage = mutableListOf<Pair<String, String>>()
                    while (newPage.size < 20 && missingIdx < missingApps.size) {
                        val app = missingApps[missingIdx++]
                        newPage.add(Pair(app.packageName, app.activityName))
                    }
                    storedPages.add(newPage)
                }
                favoritesRepository.savePages(storedPages)
            }
        }

        val validPages = ArrayList<List<AppItem>>()
        for (page in storedPages) {
            val pageApps = ArrayList<AppItem>()
            for (fav in page) {
                val matchingApp = allApps.firstOrNull { it.packageName == fav.first && it.activityName == fav.second }
                    ?: allApps.firstOrNull { it.packageName == fav.first }
                if (matchingApp != null) {
                    pageApps.add(matchingApp)
                }
            }
            validPages.add(pageApps)
        }

        val finalPages = if (validPages.isEmpty()) listOf(emptyList()) else validPages
        homePagerAdapter.submitPages(finalPages)

        val layoutManager = rvHomePager.layoutManager as? LinearLayoutManager
        val currentPos = layoutManager?.findFirstVisibleItemPosition()?.coerceAtLeast(0) ?: 0
        updatePageDots(currentPos.coerceIn(0, (finalPages.size - 1).coerceAtLeast(0)), finalPages.size)

        refreshDock()
    }

    private fun refreshDock() {
        var dockPairs = favoritesRepository.getDockApps()
        if (dockPairs.isEmpty() && allApps.isNotEmpty()) {
            val defaults = resolveDefaultDockApps()
            favoritesRepository.saveDockApps(defaults)
            dockPairs = defaults
        }

        val validPairs = mutableListOf<Pair<String, String>>()
        val dockApps = ArrayList<AppItem>()
        for (pair in dockPairs) {
            val matching = allApps.firstOrNull { it.packageName == pair.first && it.activityName == pair.second }
                ?: allApps.firstOrNull { it.packageName == pair.first }
            if (matching != null) {
                validPairs.add(Pair(matching.packageName, matching.activityName))
                dockApps.add(matching)
            }
        }
        if (validPairs.size != dockPairs.size) {
            favoritesRepository.saveDockApps(validPairs)
        }
        dockAdapter.submitList(dockApps)
    }

    private fun resolveDefaultDockApps(): List<Pair<String, String>> {
        val pm = packageManager
        val candidates = mutableListOf<Pair<String, String>>()

        fun findIntentApp(intent: Intent) {
            try {
                val resolveInfo = pm.resolveActivity(intent, 0)
                if (resolveInfo != null) {
                    val pkg = resolveInfo.activityInfo.packageName
                    val matching = allApps.firstOrNull { it.packageName == pkg }
                    if (matching != null && candidates.none { it.first == pkg }) {
                        candidates.add(Pair(matching.packageName, matching.activityName))
                    }
                }
            } catch (e: Exception) {
                // Ignore resolution failure
            }
        }

        findIntentApp(Intent(Intent.ACTION_DIAL))
        findIntentApp(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
        findIntentApp(Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")))
        findIntentApp(Intent(MediaStore.ACTION_IMAGE_CAPTURE))

        for (app in allApps) {
            if (candidates.size >= 5) break
            if (candidates.none { it.first == app.packageName }) {
                candidates.add(Pair(app.packageName, app.activityName))
            }
        }

        return candidates.take(5)
    }

    private fun showColorPickerDialog(item: AppItem) {
        val density = resources.displayMetrics.density
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val tvTitle = TextView(this).apply {
            text = "انتخاب رنگ برای «${item.label}»"
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@MainActivity, R.font.vazirmatn)
            setPadding(0, 0, 0, (12 * density).toInt())
            gravity = Gravity.CENTER
        }
        dialogView.addView(tvTitle)

        // Live preview box
        val previewBox = View(this).apply {
            val size = (46 * density).toInt()
            val params = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (14 * density).toInt()
            }
            layoutParams = params
        }
        fun updatePreview(color: Int) {
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f * density
                setColor(Color.TRANSPARENT)
                setStroke((2f * density).toInt(), color)
            }
            previewBox.background = shape
        }
        val currentColor = favoritesRepository.getIconColor(item.packageName) ?: 0x80FFFFFF.toInt()
        updatePreview(currentColor)
        dialogView.addView(previewBox)

        var selectedColor = currentColor

        val etHex = EditText(this).apply {
            hint = "#RRGGBB یا #AARRGGBB"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0x80FFFFFF.toInt())
            textSize = 13f
            setText(String.format("#%08X", currentColor))
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@MainActivity, R.font.vazirmatn)
            gravity = Gravity.CENTER
            val hexParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * density).toInt()
            }
            layoutParams = hexParams
        }

        // 30 curated Material & One UI colors grid (5 rows x 6 columns)
        val paletteRows = listOf(
            listOf(0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFFFF5252.toInt(), 0xFFFF4081.toInt(), 0xFFD32F2F.toInt(), 0xFFC2185B.toInt()),
            listOf(0xFF9C27B0.toInt(), 0xFF673AB7.toInt(), 0xFF7F5AF0.toInt(), 0xFF7C4DFF.toInt(), 0xFFB388FF.toInt(), 0xFF512DA8.toInt()),
            listOf(0xFF2196F3.toInt(), 0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(), 0xFF009688.toInt(), 0xFF448AFF.toInt(), 0xFF18FFFF.toInt()),
            listOf(0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(), 0xFFFFEB3B.toInt(), 0xFF2ECC71.toInt(), 0xFF1ABC9C.toInt()),
            listOf(0xFFFF9800.toInt(), 0xFFFF5722.toInt(), 0xFFFFC107.toInt(), 0xFF795548.toInt(), 0xFF607D8B.toInt(), 0xFFFFFFFF.toInt())
        )

        val paletteContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, (12 * density).toInt())
        }

        for (rowColors in paletteRows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, (2 * density).toInt(), 0, (2 * density).toInt())
            }
            for (color in rowColors) {
                val circle = View(this).apply {
                    val cSize = (28 * density).toInt()
                    val cMargin = (3 * density).toInt()
                    val params = LinearLayout.LayoutParams(cSize, cSize).apply {
                        setMargins(cMargin, 0, cMargin, 0)
                    }
                    layoutParams = params
                    val shape = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                        setStroke((1f * density).toInt(), 0x40FFFFFF.toInt())
                    }
                    background = shape
                    setOnClickListener {
                        selectedColor = color
                        etHex.setText(String.format("#%08X", color))
                        updatePreview(color)
                    }
                }
                rowLayout.addView(circle)
            }
            paletteContainer.addView(rowLayout)
        }
        dialogView.addView(paletteContainer)

        etHex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                try {
                    val parsed = Color.parseColor(s.toString().trim())
                    selectedColor = parsed
                    updatePreview(parsed)
                } catch (e: Exception) {
                    // Ignore partial hex typing
                }
            }
        })
        dialogView.addView(etHex)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("تأیید") { _, _ ->
                favoritesRepository.setIconColor(item.packageName, selectedColor)
                refreshFavoritesOnHome()
                refreshDock()
                Toast.makeText(this, "رنگ آیکون تغییر یافت", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("پیش‌فرض") { _, _ ->
                favoritesRepository.setIconColor(item.packageName, null)
                refreshFavoritesOnHome()
                refreshDock()
                Toast.makeText(this, "به رنگ پیش‌فرض بازگشت", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
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
            "اطلاعات برنامه (تنظیمات سیستم)",
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
                        AppLauncher.openAppDetails(this, item.packageName)
                    }
                    2 -> {
                        enterSelectionMode(item)
                    }
                }
            }
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
