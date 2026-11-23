package com.eterion.glade

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.DragEvent
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import java.util.concurrent.Future

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var loadTask: Future<*>? = null
    private var generation = 0
    private var apps = emptyList<LauncherApp>()
    private var placements = emptyList<Placement>()
    private var quickAccessIds = emptyList<String>()
    private lateinit var store: LayoutStore
    private lateinit var root: DrawerHost
    private lateinit var home: FrameLayout
    private lateinit var cells: android.widget.GridLayout
    private lateinit var drawer: LinearLayout
    private lateinit var appList: ListView
    private lateinit var systemBarScrim: SystemBarScrim
    private lateinit var empty: TextView
    private lateinit var removeTarget: TextView
    private lateinit var adapter: AppAdapter
    private var catalogLoaded = false
    private var moving: String? = null
    private var lightStatusBar: Boolean? = null
    private var lightNavigationBar: Boolean? = null

    private val packagesChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                val removedPackage = intent.data?.schemeSpecificPart
                if (removedPackage != null) {
                    placements = HomeLayout.removePackage(placements, removedPackage)
                    saveHome()
                    quickAccessIds = QuickAccess.removePackage(quickAccessIds, removedPackage)
                    store.writeQuickAccess(quickAccessIds)
                }
            }
            refreshApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                handleBack()
            }
        }
        store = LayoutStore(getSharedPreferences("layout", MODE_PRIVATE))
        buildUi()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packagesChanged, filter, RECEIVER_EXPORTED)
        else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(packagesChanged, filter)
        }
        refreshApps()
        if (savedInstanceState?.getBoolean("drawer") == true) {
            showDrawer(animate = false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("drawer", root.progress >= DrawerMotion.OPEN_THRESHOLD)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        hideDrawer(animate = false)
        cancelMove()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && Build.VERSION.SDK_INT < 33) {
            handleBack()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun handleBack() {
        if (root.progress > 0f) hideDrawer() else cancelMove()
    }

    override fun onDestroy() {
        unregisterReceiver(packagesChanged)
        generation++
        main.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi() {
        root = DrawerHost(this)
        home = FrameLayout(this)
        cells = android.widget.GridLayout(this).apply {
            columnCount = HomeLayout.COLUMNS
            rowCount = HomeLayout.ROWS
            setPadding(dp(8), dp(8), dp(8), 0)
        }
        home.addView(cells, FrameLayout.LayoutParams(-1, -1))
        home.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(R.id.action_open_drawer, getString(R.string.open_apps)))
            }
            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                if (action == R.id.action_open_drawer) { showDrawer(); return true }
                return super.performAccessibilityAction(host, action, args)
            }
        }
        removeTarget = text(getString(R.string.remove_home), 16f).apply {
            background = rounded(Color.rgb(55, 55, 55), 24)
            visibility = View.GONE
            setOnDragListener { target, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> event.localState is String
                    DragEvent.ACTION_DRAG_ENTERED -> { target.alpha = 0.7f; true }
                    DragEvent.ACTION_DRAG_EXITED -> { target.alpha = 1f; true }
                    DragEvent.ACTION_DROP -> { (event.localState as? String)?.let { removeHome(it) }; true }
                    DragEvent.ACTION_DRAG_ENDED -> { target.visibility = View.GONE; target.alpha = 1f; true }
                    else -> true
                }
            }
        }
        home.addView(removeTarget, FrameLayout.LayoutParams(dp(220), dp(52), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(12) })
        home.setOnLongClickListener { homeOptions(); true }

        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            val radius = dp(24).toFloat()
            background = GradientDrawable().apply {
                setColor(getColor(R.color.drawer_surface))
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    // Extend the bottom corners beyond the view so only the top is rounded.
                    outline.setRoundRect(0, 0, view.width, view.height + radius.toInt(), radius)
                }
            }
            clipToOutline = true
            setPadding(dp(16), 0, dp(16), 0)
            visibility = View.GONE
        }
        drawer.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(R.id.action_close_drawer, getString(R.string.close_drawer)))
            }
            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                if (action == R.id.action_close_drawer) { hideDrawer(); return true }
                return super.performAccessibilityAction(host, action, args)
            }
        }
        val gridFrame = FrameLayout(this)
        adapter = AppAdapter()
        appList = ListView(this).apply {
            divider = null
            setItemsCanFocus(true)
            clipToPadding = false
            setPadding(0, 0, 0, dp(16))
            adapter = this@MainActivity.adapter
        }
        empty = text(getString(R.string.loading_apps), 16f, getColor(R.color.drawer_secondary))
        gridFrame.addView(appList, FrameLayout.LayoutParams(-1, -1))
        gridFrame.addView(empty, FrameLayout.LayoutParams(-1, -1))
        appList.emptyView = empty
        drawer.addView(gridFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        root.attach(home, drawer, cells, listOf(gridFrame))
        systemBarScrim = SystemBarScrim(root, appList, gridFrame, getColor(R.color.drawer_surface))
        root.foreground = systemBarScrim
        appList.setOnScrollListener(object : android.widget.AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: android.widget.AbsListView, scrollState: Int) = Unit
            override fun onScroll(view: android.widget.AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                root.invalidate()
            }
        })
        root.canDragUp = { moving == null && removeTarget.visibility != View.VISIBLE }
        root.canDragDown = { x, y ->
            val gridBounds = android.graphics.Rect()
            appList.getDrawingRect(gridBounds)
            root.offsetDescendantRectToMyCoords(appList, gridBounds)
            !gridBounds.contains(x.toInt(), y.toInt()) || !appList.canScrollVertically(-1)
        }
        root.onProgressChanged = { updateSystemBars(it); root.invalidate() }
        root.onClosed = { appList.setSelection(0) }
        // List padding keeps the first/last row reachable while scrolling can draw under bars.
        root.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                applyContentInsets(safe.left, safe.top, safe.right, safe.bottom)
            } else {
                @Suppress("DEPRECATION")
                applyContentInsets(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(root)
        updateSystemBars(0f)
        renderHome()
    }

    private fun applyContentInsets(left: Int, top: Int, right: Int, bottom: Int) {
        home.setPadding(left, top, right, bottom)
        drawer.setPadding(left + dp(16), 0, right + dp(16), 0)
        appList.setPadding(0, top + dp(12), 0, bottom + dp(16))
        systemBarScrim.topInset = top
        systemBarScrim.bottomInset = bottom
        root.invalidate()
    }

    @Suppress("DEPRECATION")
    private fun updateSystemBars(progress: Float) {
        val lightTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        val status = lightTheme && progress >= 0.98f
        val navigation = lightTheme && progress >= 0.6f
        if (status == lightStatusBar && navigation == lightNavigationBar) return
        lightStatusBar = status
        lightNavigationBar = navigation
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            val appearance = (if (status) android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0) or
                (if (navigation) android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0)
            window.insetsController?.setSystemBarsAppearance(appearance, mask)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                (if (status) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0) or
                (if (navigation) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        }
    }

    private fun refreshApps() {
        val request = ++generation
        loadTask?.cancel(true)
        loadTask = worker.submit {
            try {
                val loaded = loadApps(applicationContext)
                val savedLayout = if (!catalogLoaded) store.read() else null
                val savedQuickAccess = if (!catalogLoaded) store.readQuickAccess() else null
                main.post {
                    if (request != generation || isDestroyed) return@post
                    apps = loaded
                    if (savedLayout != null) placements = savedLayout
                    if (savedQuickAccess != null) quickAccessIds = savedQuickAccess
                    catalogLoaded = true
                    // Preserve temporarily unavailable shortcuts in storage; hide until available.
                    renderHome()
                    empty.setText(R.string.no_apps)
                    renderDrawer()
                }
            } catch (_: RuntimeException) {
                main.post { if (!isDestroyed && request == generation) empty.setText(R.string.apps_unavailable) }
            }
        }
    }

    private fun renderDrawer() {
        val byId = apps.associateBy { it.id }
        val groups = QuickAccess.group(apps.map { it.id }, quickAccessIds)
        adapter.rows = buildList {
            if (groups.quick.isNotEmpty()) {
                groups.quick.mapNotNull { byId[it] }.chunked(HomeLayout.COLUMNS).forEach { add(DrawerRow.Apps(it)) }
                if (groups.alphabetical.isNotEmpty()) add(DrawerRow.Divider)
            }
            groups.alphabetical.mapNotNull { byId[it] }.chunked(HomeLayout.COLUMNS).forEach { add(DrawerRow.Apps(it)) }
        }
        adapter.notifyDataSetChanged()
    }

    private fun toggleQuickAccess(app: LauncherApp) {
        quickAccessIds = if (app.id in quickAccessIds) QuickAccess.remove(quickAccessIds, app.id)
            else QuickAccess.add(quickAccessIds, app.id)
        store.writeQuickAccess(quickAccessIds)
        renderDrawer()
        appList.setSelection(0)
    }

    private fun renderHome() {
        cells.removeAllViews()
        val byId = apps.associateBy { it.id }
        for (cell in 0 until HomeLayout.SIZE) {
            val placement = placements.firstOrNull { it.cell == cell }
            val app = placement?.let { byId[it.component] }
            val view = if (app != null) appTile(app) else FrameLayout(this)
            val cellLayout = android.widget.GridLayout.LayoutParams(
                android.widget.GridLayout.spec(cell / HomeLayout.COLUMNS, 1f),
                android.widget.GridLayout.spec(cell % HomeLayout.COLUMNS, 1f)
            ).apply { width = 0; height = 0 }
            if (app != null) {
                view.setOnClickListener { if (moving != null) placeMoving(cell) else launch(app) }
                view.setOnLongClickListener {
                    if (moving == null) {
                        val data = ClipData.newPlainText("Glade shortcut", app.id)
                        removeTarget.visibility = View.VISIBLE
                        if (!view.startDragAndDrop(data, View.DragShadowBuilder(view), app.id, 0)) {
                            removeTarget.visibility = View.GONE
                        }
                    }
                    true
                }
                view.setOnCreateContextMenuListener { menu, _, _ ->
                    menu.setHeaderTitle(app.label)
                    menu.add(R.string.move_home).setOnMenuItemClickListener { beginMove(app.id); true }
                    menu.add(R.string.remove_home).setOnMenuItemClickListener { removeHome(app.id); true }
                    menu.add(R.string.app_info).setOnMenuItemClickListener { appInfo(app); true }
                }
                // Explicit accessibility actions supplement drag-and-drop.
                view.accessibilityDelegate = object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.R.id.edit, getString(R.string.move_home)))
                        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.R.id.cut, getString(R.string.remove_home)))
                    }
                    override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean = when (action) {
                        android.R.id.edit -> { beginMove(app.id); true }
                        android.R.id.cut -> { removeHome(app.id); true }
                        else -> super.performAccessibilityAction(host, action, args)
                    }
                }
            } else {
                view.contentDescription = getString(R.string.empty_position, cell / HomeLayout.COLUMNS + 1, cell % HomeLayout.COLUMNS + 1)
                view.setOnClickListener { if (moving != null) placeMoving(cell) }
                view.setOnLongClickListener { homeOptions(); true }
                view.importantForAccessibility = if (moving != null) View.IMPORTANT_FOR_ACCESSIBILITY_YES else View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            view.setOnDragListener { target, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> event.localState is String
                    DragEvent.ACTION_DRAG_ENTERED -> { target.background = rounded(0x44808080, 16); true }
                    DragEvent.ACTION_DRAG_EXITED -> { target.background = null; true }
                    DragEvent.ACTION_DROP -> {
                        val id = event.localState as? String
                        if (id != null) {
                            if (placements.any { it.cell == cell && it.component != id }) toast(R.string.position_taken)
                            else { placements = HomeLayout.move(placements, id, cell); saveHome() }
                        }
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> { target.background = null; true }
                    else -> true
                }
            }
            if (moving != null) view.background = rounded(0x22808080, 16)
            cells.addView(view, cellLayout)
        }
    }

    private fun beginMove(id: String) { moving = id; renderHome(); toast(R.string.move_hint) }
    private fun cancelMove() { if (moving != null) { moving = null; renderHome() } }
    private fun placeMoving(cell: Int) {
        val id = moving ?: return
        if (placements.any { it.cell == cell && it.component != id }) { toast(R.string.position_taken); return }
        placements = HomeLayout.move(placements, id, cell)
        moving = null
        saveHome()
    }
    private fun saveHome() { store.write(placements); renderHome() }
    private fun removeHome(id: String) { placements = placements.filterNot { it.component == id }; saveHome() }

    private fun addHome(app: LauncherApp) {
        if (placements.any { it.component == app.id }) { toast(R.string.already_home); return }
        val updated = HomeLayout.add(placements, app.id)
        if (updated.size == placements.size) { toast(R.string.home_full); return }
        placements = updated
        saveHome()
        hideDrawer()
        toast(R.string.added_home)
    }

    private fun appOptions(app: LauncherApp, onHome: Boolean) {
        val quickAction = getString(if (app.id in quickAccessIds) R.string.remove_quick_access else R.string.add_quick_access)
        val actions = if (onHome) arrayOf(getString(R.string.move_home), getString(R.string.remove_home), getString(R.string.app_info))
            else arrayOf(getString(R.string.add_home), quickAction, getString(R.string.app_info))
        AlertDialog.Builder(this).setTitle(app.label).setItems(actions) { _, index ->
            if (onHome) when (index) { 0 -> beginMove(app.id); 1 -> removeHome(app.id); else -> appInfo(app) }
            else when (index) { 0 -> addHome(app); 1 -> toggleQuickAccess(app); else -> appInfo(app) }
        }.show()
    }

    private fun homeOptions() {
        AlertDialog.Builder(this).setTitle(R.string.home_options)
            .setItems(arrayOf(getString(R.string.choose_home), getString(R.string.wallpaper))) { _, index ->
                if (index == 0) {
                    val roles = getSystemService(RoleManager::class.java)
                    if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                        safeStart(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    } else safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
                } else safeStart(Intent(Intent.ACTION_SET_WALLPAPER))
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun appInfo(app: LauncherApp) = safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.component.packageName}")))
    private fun launch(app: LauncherApp) {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        } catch (_: android.content.ActivityNotFoundException) { toast(R.string.app_unavailable); refreshApps() }
        catch (_: SecurityException) { toast(R.string.app_unavailable) }
    }
    private fun safeStart(intent: Intent) {
        try { startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) { toast(R.string.action_unavailable) }
        catch (_: SecurityException) { toast(R.string.action_unavailable) }
    }

    private fun showDrawer(animate: Boolean = true) { cancelMove(); root.open(animate) }
    private fun hideDrawer(animate: Boolean = true) {
        root.close(animate)
    }

    private fun appTile(app: LauncherApp, onHome: Boolean = true): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(2), dp(8), dp(2), dp(6))
        contentDescription = app.label
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        addView(ImageView(this@MainActivity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(app.icon.constantState?.newDrawable(resources)?.mutate() ?: app.icon)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, resources.getDimensionPixelSize(R.dimen.app_icon_size).let { LinearLayout.LayoutParams(it, it) })
        if (!onHome) addView(text(app.label, 12f, getColor(R.color.drawer_foreground)).apply {
            maxLines = 1
            includeFontPadding = false
            ellipsize = android.text.TextUtils.TruncateAt.END
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(-1, drawerLabelHeight()).apply { topMargin = dp(1) })
    }

    private sealed interface DrawerRow {
        data class Apps(val apps: List<LauncherApp>) : DrawerRow
        data object Divider : DrawerRow
    }

    private inner class AppAdapter : BaseAdapter() {
        var rows = emptyList<DrawerRow>()
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = when (rows[position]) {
            is DrawerRow.Apps -> 0
            DrawerRow.Divider -> 1
        }
        override fun areAllItemsEnabled() = false
        override fun isEnabled(position: Int) = rows[position] is DrawerRow.Apps

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return when (val row = rows[position]) {
                is DrawerRow.Apps -> {
                    val view = convertView as? LinearLayout ?: LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        repeat(HomeLayout.COLUMNS) {
                            addView(appTile(row.apps.first(), onHome = false), LinearLayout.LayoutParams(0, -1, 1f))
                        }
                    }
                    for (column in 0 until HomeLayout.COLUMNS) {
                        val tile = view.getChildAt(column) as LinearLayout
                        val app = row.apps.getOrNull(column)
                        tile.visibility = if (app == null) View.INVISIBLE else View.VISIBLE
                        tile.contentDescription = app?.label
                        if (app != null) {
                            (tile.getChildAt(0) as ImageView).setImageDrawable(app.icon.constantState?.newDrawable(resources)?.mutate() ?: app.icon)
                            (tile.getChildAt(1) as TextView).text = app.label
                            tile.setOnClickListener { launch(app) }
                            tile.setOnLongClickListener { appOptions(app, false); true }
                        }
                    }
                    val height = maxOf(resources.getDimensionPixelSize(R.dimen.drawer_row_pitch), resources.getDimensionPixelSize(R.dimen.app_icon_size) + drawerLabelHeight() + dp(14))
                    // Retain native list recycling metadata on existing layout parameters.
                    if (view.layoutParams == null) view.layoutParams = android.widget.AbsListView.LayoutParams(-1, height)
                    else view.layoutParams.height = height
                    view
                }
                DrawerRow.Divider -> convertView ?: FrameLayout(this@MainActivity).apply {
                    id = R.id.quick_access_divider
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    addView(View(this@MainActivity).apply { setBackgroundColor(getColor(R.color.drawer_divider)) }, FrameLayout.LayoutParams(-1, dp(1), Gravity.CENTER_VERTICAL))
                    layoutParams = android.widget.AbsListView.LayoutParams(-1, dp(20))
                }
            }
        }
    }

    private fun drawerLabelHeight(): Int {
        val metrics = android.graphics.Paint().apply { textSize = 12f * resources.displayMetrics.scaledDensity }.fontMetrics
        return maxOf(dp(25), kotlin.math.ceil(metrics.descent - metrics.ascent).toInt())
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: Int) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    private fun text(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
}
