package com.premiumtv.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.*
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.AtomicFile
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var prefs: Preferences
    private lateinit var apps: InstalledApps
    private lateinit var icons: IconCache
    private lateinit var backgroundView: WallpaperView
    private lateinit var grid: RecyclerView
    private lateinit var adapter: HomeAdapter
    private lateinit var layout: GridLayoutManager
    private lateinit var title: TextView
    private lateinit var summary: TextView
    private lateinit var hint: TextView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var network: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val imageWorker = Executors.newSingleThreadExecutor()
    private var installed = emptyList<AppEntry>()
    private var loaded = false
    private var query = ""
    private var selectedKey: String? = null
    private var columns = 5
    private var compact = false
    private var toolbarIndex = 0
    private var toolbar = emptyList<Button>()
    private var started = false
    private var packageRegistered = false
    private var timeRegistered = false
    private var networkRegistered = false
    private var wallpaperGeneration = 0
    private var focusGeneration = 0
    private var move: Move? = null
    private data class Move(val appKey: String, val section: Section, var keys: List<String>)

    private val clockTick = object : Runnable {
        override fun run() {
            if (!started) return
            updateStatus()
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    private val delayedScan = Runnable { if (started) refreshApps() }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED &&
                intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            handler.removeCallbacks(delayedScan)
            handler.postDelayed(delayedScan, 180L)
        }
    }
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { updateStatus() }
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        private fun changed() { handler.post { if (started) updateStatus() } }
        override fun onAvailable(network: Network) = changed()
        override fun onLost(network: Network) = changed()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = changed()
    }

    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(LocaleContext.wrap(newBase)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Preferences(this)
        apps = InstalledApps(this)
        icons = IconCache(this, prefs.lowMemory)
        query = savedInstanceState?.getString("query") ?: ""
        selectedKey = savedInstanceState?.getString("selection")
        toolbarIndex = savedInstanceState?.getInt("toolbar", 0) ?: 0
        immersive()
        buildUi()
        loadWallpaper()
    }

    private fun buildUi() {
        compact = resources.configuration.screenHeightDp < 540
        val frame = FrameLayout(this)
        backgroundView = WallpaperView(this)
        frame.addView(backgroundView, FrameLayout.LayoutParams(-1, -1))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = resources.configuration.layoutDirection
            setPadding(dp(prefs.margin), dp(if (compact) 12 else 18), dp(prefs.margin), dp(if (compact) 8 else 13))
            clipChildren = false
            clipToPadding = false
        }
        frame.addView(page, FrameLayout.LayoutParams(-1, -1))
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val wordmark = Ui.text(this, "NOIR", 24f, Ui.WHITE, true).apply { letterSpacing = .21f }
        header.addView(wordmark, LinearLayout.LayoutParams(0, -2, 1f))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        network = Ui.text(this, "", 11f, Ui.MUTED)
        line.addView(network, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(18) })
        clock = Ui.text(this, "", 22f)
        line.addView(clock)
        date = Ui.text(this, "", 10f, Ui.MUTED).apply { gravity = Gravity.END }
        info.addView(line)
        info.addView(date, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        header.addView(info)
        page.addView(header, LinearLayout.LayoutParams(-1, dp(if (compact) 45 else 53)))
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
        }
        toolbar = listOf(
            Ui.chip(this, getString(R.string.search), prefs) { showSearch() }.apply { id = R.id.search_button },
            Ui.chip(this, getString(R.string.customize), prefs) { showCustomize() }.apply { id = R.id.customize_button },
            Ui.chip(this, getString(R.string.device_settings), prefs) { openSystemSettings() }.apply { id = R.id.system_button },
            Ui.chip(this, getString(R.string.home_app), prefs) { showHomeHelp() }.apply { id = R.id.home_button }
        )
        toolbar.forEachIndexed { index, button ->
            navigation.addView(button, LinearLayout.LayoutParams(-2, dp(38)).apply { marginEnd = dp(9) })
            val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            button.nextFocusLeftId = toolbar[(index + if (rtl) 1 else -1).coerceIn(0, toolbar.lastIndex)].id
            button.nextFocusRightId = toolbar[(index + if (rtl) -1 else 1).coerceIn(0, toolbar.lastIndex)].id
            button.nextFocusUpId = button.id
            button.onFocusChangeListener = View.OnFocusChangeListener { view, focused ->
                Ui.animateFocus(view, focused, prefs.animations)
                if (focused) toolbarIndex = index
            }
            button.setOnKeyListener { _, code, event ->
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.action == KeyEvent.ACTION_DOWN) focusGrid(selectedKey)
                    true
                } else false
            }
        }
        page.addView(navigation, LinearLayout.LayoutParams(-1, dp(if (compact) 43 else 47)))
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), 0, dp(7), 0)
        }
        hero.addView(Ui.text(this, getString(R.string.tagline), 11f, Ui.MUTED).apply {
            if (compact) visibility = View.GONE
        })
        title = Ui.text(this, getString(R.string.welcome), if (compact) 22f else 27f, Ui.WHITE, true).apply {
            id = R.id.selected_title
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        hero.addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        summary = Ui.text(this, getString(R.string.loading), 11f, Ui.MUTED)
        hero.addView(summary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        page.addView(hero, LinearLayout.LayoutParams(-1, dp(if (compact) 60 else 91)))
        adapter = HomeAdapter(prefs, icons, ::selected, ::openTile, ::showAppMenu).apply { compact = this@MainActivity.compact }
        layout = GridLayoutManager(this, columns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) = if (adapter.items.getOrNull(position) is HomeItem.Tile) 1 else columns
            }
        }
        grid = RecyclerView(this).apply {
            id = R.id.app_grid
            layoutManager = layout
            adapter = this@MainActivity.adapter
            clipToPadding = true
            clipChildren = true
            setPadding(dp(1), dp(1), dp(1), dp(if (compact) 6 else 12))
            itemAnimator = null
            setItemViewCacheSize(if (prefs.lowMemory) 4 else 8)
            setHasFixedSize(true)
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
                val width = right - left
                if (width > 0 && width != oldRight - oldLeft) updateColumns(width)
            }
        }
        page.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        hint = Ui.text(this, getString(R.string.remote_hint), 10f, Ui.MUTED).apply {
            setPadding(dp(7), dp(5), dp(7), 0)
            maxLines = 2
        }
        page.addView(hint, LinearLayout.LayoutParams(-1, dp(if (compact) 24 else 31)))
        setContentView(frame)
        adapter.submit(listOf(HomeItem.Message(getString(R.string.loading), "loading")))
        toolbar[toolbarIndex.coerceIn(0, toolbar.lastIndex)].requestFocus()
    }

    private fun updateColumns(width: Int) {
        val next = (width / dp(155)).coerceIn(3, 8)
        val height = ((width / next - dp(14)) * .67f).toInt().coerceIn(dp(104), dp(139))
        if (next == columns && adapter.tileHeight == (height / resources.displayMetrics.density).toInt()) return
        columns = next
        adapter.columns = next
        adapter.tileHeight = (height / resources.displayMetrics.density).toInt()
        layout.spanCount = next
        // A column change can reuse holders with old dimensions, so clear that small pool.
        grid.recycledViewPool.clear()
        render(grid.hasFocus())
    }

    override fun onStart() {
        super.onStart()
        started = true
        registerChanges()
        handler.removeCallbacks(clockTick)
        clockTick.run()
        refreshApps()
    }

    override fun onStop() {
        started = false
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(delayedScan)
        if (packageRegistered) { unregisterReceiver(packageReceiver); packageRegistered = false }
        if (timeRegistered) { unregisterReceiver(timeReceiver); timeRegistered = false }
        if (networkRegistered) {
            try { connectivity().unregisterNetworkCallback(networkCallback) } catch (_: RuntimeException) { }
            networkRegistered = false
        }
        super.onStop()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerChanges() {
        val packages = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        val times = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, packages, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(timeReceiver, times, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageReceiver, packages)
            registerReceiver(timeReceiver, times)
        }
        packageRegistered = true
        timeRegistered = true
        try {
            if (Build.VERSION.SDK_INT >= 24) connectivity().registerDefaultNetworkCallback(networkCallback)
            else connectivity().registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)
            networkRegistered = true
        } catch (_: RuntimeException) { }
    }

    private fun refreshApps() {
        apps.refresh(currentLocale()) { result ->
            result.onSuccess { list ->
                if (loaded && list == installed) return@onSuccess
                val hadFocus = grid.hasFocus()
                installed = list
                loaded = true
                move = null
                if (!prefs.favoritesInitialized && list.isNotEmpty()) {
                    prefs.favorites = list.filter { it.packageName !in prefs.hidden }.take(5).map { it.packageName }
                }
                render(hadFocus || selectedKey != null)
            }.onFailure {
                if (!loaded) { loaded = true; render(false) }
                toast(R.string.action_unavailable)
            }
        }
    }

    private fun orderedApps(section: Section): List<AppEntry> {
        val hiddenKeys = prefs.hidden
        val favoriteKeys = prefs.favorites
        val available = installed.filter { it.packageName !in hiddenKeys }
        val byKey = available.associateBy { it.packageName }
        val keys = if (section == Section.ALL) available.map { it.packageName }
            else favoriteKeys.filter { it in byKey }
        val saved = move?.takeIf { it.section == section }?.keys
            ?: if (section == Section.ALL) prefs.order else favoriteKeys
        return AppOrder.merge(saved, keys).mapNotNull { byKey[it] }
    }

    private fun render(requestFocus: Boolean = true) {
        if (!::adapter.isInitialized) return
        val all = orderedApps(Section.ALL)
        val favorites = orderedApps(Section.FAVORITES)
        val favoriteKeys = prefs.favorites.toHashSet()
        val items = buildList<HomeItem> {
            if (query.isBlank()) {
                add(HomeItem.Header(getString(R.string.favorites), "favorites_header"))
                if (favorites.isEmpty()) add(HomeItem.Message(getString(R.string.favorites_empty), "favorites_empty"))
                favorites.forEach { add(HomeItem.Tile(it, Section.FAVORITES, true)) }
                add(HomeItem.Header(getString(R.string.all_apps), "all_header"))
                if (all.isEmpty()) add(HomeItem.Message(getString(R.string.apps_empty), "all_empty"))
                all.forEach { add(HomeItem.Tile(it, Section.ALL, it.packageName in favoriteKeys)) }
            } else {
                add(HomeItem.Header(getString(R.string.search_results), "results_header"))
                val matching = all.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
                if (matching.isEmpty()) add(HomeItem.Message(getString(R.string.no_results), "no_results"))
                matching.forEach { add(HomeItem.Tile(it, Section.ALL, it.packageName in favoriteKeys)) }
            }
        }
        summary.text = if (query.isBlank()) getString(R.string.app_count, all.size, favorites.size)
                       else getString(R.string.search_query, query)
        hint.setText(if (move != null) R.string.move_hint else R.string.remote_hint)
        adapter.submit(items)
        if (adapter.firstTile() < 0) title.setText(R.string.welcome)
        if (requestFocus) focusGrid(selectedKey)
    }

    private fun selected(tile: HomeItem.Tile) {
        selectedKey = tile.key
        title.text = if (move != null) getString(R.string.move_active, tile.app.label) else tile.app.label
    }

    private fun focusGrid(key: String? = null) {
        val index = key?.let(adapter::indexOf)?.takeIf { it >= 0 } ?: adapter.firstTile()
        if (index >= 0) focusIndex(index) else toolbar[toolbarIndex].requestFocus()
    }

    private fun focusIndex(index: Int) {
        if (adapter.items.getOrNull(index) !is HomeItem.Tile) return
        val token = ++focusGeneration
        grid.scrollToPosition(index)
        fun attempt(remaining: Int) {
            grid.post {
                if (token != focusGeneration || isDestroyed) return@post
                val view = grid.findViewHolderForAdapterPosition(index)?.itemView
                if (view != null) view.requestFocus()
                else if (remaining > 0) { grid.scrollToPosition(index); attempt(remaining - 1) }
                else toolbar[toolbarIndex].requestFocus()
            }
        }
        attempt(3)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> FocusNavigator.Direction.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> FocusNavigator.Direction.RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> FocusNavigator.Direction.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> FocusNavigator.Direction.DOWN
            else -> null
        }
        if (direction != null && grid.hasFocus()) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (move != null) moveSelection(direction)
                else {
                    val from = selectedKey?.let(adapter::indexOf) ?: adapter.firstTile()
                    val next = adapter.navigator.next(from, direction,
                        resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL)
                    if (next < 0) toolbar[toolbarIndex].requestFocus() else focusIndex(next)
                }
            }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_MENU && grid.hasFocus()) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && move == null) {
                (adapter.items.getOrNull(selectedKey?.let(adapter::indexOf) ?: -1) as? HomeItem.Tile)?.let(::showAppMenu)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun openTile(tile: HomeItem.Tile) {
        if (move != null) { finishMove(true); return }
        try {
            startActivity(Intent(Intent.ACTION_MAIN).apply {
                component = tile.app.component
                addCategory(if (tile.app.television) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            })
        } catch (_: RuntimeException) { toast(R.string.launch_error); refreshApps() }
    }

    private fun showAppMenu(tile: HomeItem.Tile) {
        if (move != null) return
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += getString(R.string.open_app) to { openTile(tile) }
        actions += getString(if (tile.app.packageName in prefs.favorites) R.string.remove_favorite else R.string.add_favorite) to {
            prefs.toggleFavorite(tile.app.packageName); render()
        }
        if (query.isBlank()) actions += getString(R.string.move_app) to {
            move = Move(tile.app.packageName, tile.section, orderedApps(tile.section).map { it.packageName })
            render()
            title.text = getString(R.string.move_active, tile.app.label)
        }
        actions += getString(R.string.hide_app) to {
            AlertDialog.Builder(this).setTitle(getString(R.string.hide_title, tile.app.label))
                .setMessage(R.string.hide_message).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.hide) { _, _ ->
                    prefs.hidden = prefs.hidden + tile.app.packageName
                    render()
                }.show()
        }
        actions += getString(R.string.app_info) to {
            startSafe(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + tile.app.packageName)))
        }
        actionDialog(tile.app.label, actions)
    }

    private fun moveSelection(direction: FocusNavigator.Direction) {
        val state = move ?: return
        val from = state.keys.indexOf(state.appKey)
        if (from < 0) { finishMove(false); return }
        val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val to = when (direction) {
            FocusNavigator.Direction.LEFT, FocusNavigator.Direction.RIGHT -> {
                val delta = if ((direction == FocusNavigator.Direction.RIGHT) != rtl) 1 else -1
                val candidate = from + delta
                if (candidate >= 0 && candidate < state.keys.size && candidate / columns == from / columns) candidate else from
            }
            FocusNavigator.Direction.UP -> (from - columns).takeIf { it >= 0 } ?: from
            FocusNavigator.Direction.DOWN ->
                if (from / columns < state.keys.lastIndex / columns) (from + columns).coerceAtMost(state.keys.lastIndex) else from
        }
        if (from != to) { state.keys = AppOrder.move(state.keys, from, to); render() }
    }

    private fun finishMove(save: Boolean) {
        val state = move ?: return
        if (save) {
            if (state.section == Section.ALL) prefs.order = AppOrder.merge(state.keys, installed.map { it.packageName })
            else prefs.favorites = state.keys + prefs.favorites.filterNot { it in state.keys }
        }
        move = null
        render()
        if (save) toast(R.string.order_saved)
    }

    private fun showSearch() {
        SearchKeyboard.show(this, prefs, query) { value ->
            query = value
            selectedKey = null
            render()
        }
    }

    private fun showCustomize() {
        val actions = listOf<Pair<String, () -> Unit>>(
            getString(R.string.theme) to { showTheme() },
            getString(R.string.wallpaper) to { showWallpapers() },
            getString(R.string.animations, getString(if (prefs.animations) R.string.on else R.string.off)) to {
                prefs.animations = !prefs.animations; recreate()
            },
            getString(R.string.language) to { showLanguage() },
            getString(R.string.hidden_apps) to { showHidden() },
            getString(R.string.spacing) to { showMargins() },
            getString(R.string.reset_order) to {
                AlertDialog.Builder(this).setTitle(R.string.reset_order).setMessage(R.string.reset_order_message)
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.reset) { _, _ ->
                        prefs.order = emptyList()
                        prefs.favorites = installed.filter { it.packageName in prefs.favorites }.map { it.packageName }
                        render()
                    }.show()
            },
            getString(R.string.clear_cache) to { icons.clear(); render(); toast(R.string.cache_cleared) },
            getString(R.string.restore_home) to { openHomeSettings() },
            getString(R.string.about) to { showAbout() }
        )
        actionDialog(getString(R.string.customize), actions)
    }

    private fun showTheme() {
        val names = listOf("ice", "pearl", "gold", "sage")
        val labels = listOf(R.string.accent_ice, R.string.accent_pearl, R.string.accent_gold, R.string.accent_sage)
        choose(R.string.theme, labels.map(::getString), names.indexOf(prefs.accentName)) {
            prefs.accentName = names[it]; recreate()
        }
    }

    private fun showWallpapers() {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        listOf("black" to R.string.wallpaper_black, "graphite" to R.string.wallpaper_graphite,
            "ribbon" to R.string.wallpaper_ribbon).forEach { (value, label) ->
            actions += getString(label) to { prefs.wallpaper = value; loadWallpaper() }
        }
        if (File(filesDir, "wallpaper.jpg").isFile) {
            actions += getString(R.string.wallpaper_saved) to { prefs.wallpaper = "custom"; loadWallpaper() }
        }
        actions += getString(R.string.wallpaper_custom) to { pickWallpaper() }
        actionDialog(getString(R.string.wallpaper), actions)
    }

    private fun showLanguage() {
        val tags = listOf("auto", "ar", "en")
        choose(R.string.language, listOf(getString(R.string.system_language), "العربية", "English"),
            tags.indexOf(prefs.language)) { prefs.language = tags[it]; recreate() }
    }

    private fun showMargins() {
        val margins = listOf(24, 32, 48)
        choose(R.string.spacing, listOf(R.string.margin_compact, R.string.margin_balanced, R.string.margin_safe).map(::getString),
            margins.indexOf(prefs.margin)) { prefs.margin = margins[it]; recreate() }
    }

    private fun showHidden() {
        val hidden = installed.filter { it.packageName in prefs.hidden }
        if (hidden.isEmpty()) { toast(R.string.hidden_empty); return }
        val actions = hidden.map { app ->
            getString(R.string.restore_app, app.label) to { prefs.hidden = prefs.hidden - app.packageName; render() }
        }.toMutableList()
        actions += getString(R.string.restore_all) to { prefs.hidden = emptySet(); render() }
        actionDialog(getString(R.string.hidden_apps), actions)
    }

    private fun actionDialog(title: String, actions: List<Pair<String, () -> Unit>>) {
        AlertDialog.Builder(this).setTitle(title).setItems(actions.map { it.first }.toTypedArray()) { _, which ->
            actions[which].second()
        }.setNegativeButton(R.string.close, null).show()
    }

    private fun choose(title: Int, labels: List<String>, current: Int, chosen: (Int) -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels.toTypedArray(), current) { dialog, which ->
            dialog.dismiss(); chosen(which)
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showHomeHelp() {
        val home = defaultHome()
        val message = if (home == packageName) getString(R.string.home_current) + "\n\n" + getString(R.string.home_message)
            else getString(R.string.home_message)
        AlertDialog.Builder(this).setTitle(R.string.home_title).setMessage(message)
            .setPositiveButton(R.string.home_choose) { _, _ -> requestHomeRole() }
            .setNeutralButton(R.string.home_open_settings) { _, _ -> openHomeSettings() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    @Suppress("DEPRECATION")
    private fun requestHomeRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                    !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), 300)
                    return
                }
            } catch (_: RuntimeException) { }
        }
        openHomeSettings()
    }

    private fun openHomeSettings() {
        if (!tryStart(Intent(Settings.ACTION_HOME_SETTINGS)) &&
            !tryStart(Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"))) openSystemSettings()
    }
    private fun openSystemSettings() = startSafe(Intent(Settings.ACTION_SETTINGS))
    private fun startSafe(intent: Intent) { if (!tryStart(intent)) toast(R.string.action_unavailable) }
    private fun tryStart(intent: Intent): Boolean = try { startActivity(intent); true } catch (_: RuntimeException) { false }

    @Suppress("DEPRECATION")
    private fun defaultHome(): String = try {
        packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName ?: getString(R.string.not_set)
    } catch (_: RuntimeException) { getString(R.string.not_set) }

    private fun showAbout() {
        val memory = ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        AlertDialog.Builder(this).setTitle(R.string.about).setMessage(getString(R.string.about_format,
            BuildConfig.VERSION_NAME, Build.MANUFACTURER + " " + Build.MODEL, Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT, String.format(Locale.ROOT, "%.1f", memory.totalMem / 1_073_741_824.0),
            defaultHome())).setPositiveButton(R.string.close, null).show()
    }

    @Suppress("DEPRECATION")
    private fun pickWallpaper() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivityForResult(intent, 200) } catch (_: RuntimeException) { toast(R.string.wallpaper_missing) }
    }

    @Deprecated("Framework activity result callback is used to keep the runtime small.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 200 && resultCode == RESULT_OK) data?.data?.let(::importWallpaper)
    }

    private fun importWallpaper(uri: Uri) {
        val importToken = ++wallpaperGeneration
        toast(R.string.image_loading)
        imageWorker.execute {
            val incoming = File(cacheDir, "wallpaper-import.tmp")
            var bitmap: Bitmap? = null
            try {
                contentResolver.openInputStream(uri)?.use { source ->
                    incoming.outputStream().use { sink ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 32L * 1024 * 1024)
                            sink.write(buffer, 0, count)
                        }
                    }
                } ?: error("Missing image stream")
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(incoming.path, options)
                require(options.outWidth > 0 && options.outHeight > 0)
                val maxWidth = if (prefs.lowMemory) 960 else 1280
                val maxHeight = if (prefs.lowMemory) 540 else 720
                var sample = 1
                while (options.outWidth / sample > maxWidth || options.outHeight / sample > maxHeight) sample *= 2
                options.inJustDecodeBounds = false
                options.inSampleSize = sample
                bitmap = BitmapFactory.decodeFile(incoming.path, options) ?: error("Invalid image")
                val orientation = try {
                    ExifInterface(incoming.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
                }
                if (!matrix.isIdentity) {
                    val original = bitmap
                    val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
                    if (rotated !== original) original.recycle()
                    bitmap = rotated
                }
                val atomic = AtomicFile(File(filesDir, "wallpaper.jpg"))
                val stream = atomic.startWrite()
                try {
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream))
                    atomic.finishWrite(stream)
                } catch (error: Exception) { atomic.failWrite(stream); throw error }
                handler.post { if (!isDestroyed && importToken == wallpaperGeneration) { prefs.wallpaper = "custom"; loadWallpaper() } }
            } catch (_: Exception) {
                handler.post { if (!isDestroyed) toast(R.string.wallpaper_error) }
            } catch (_: OutOfMemoryError) {
                handler.post { if (!isDestroyed) toast(R.string.wallpaper_error) }
            } finally { bitmap?.recycle(); incoming.delete() }
        }
    }

    private fun loadWallpaper() {
        val token = ++wallpaperGeneration
        backgroundView.configure(prefs.wallpaper, prefs.accent)
        if (prefs.wallpaper != "custom") return
        imageWorker.execute {
            val bitmap = try {
                AtomicFile(File(filesDir, "wallpaper.jpg")).openRead().use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
            handler.post {
                if (!isDestroyed && token == wallpaperGeneration) backgroundView.configure(prefs.wallpaper, prefs.accent, bitmap)
            }
        }
    }

    private fun connectivity() = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    @Suppress("DEPRECATION")
    private fun currentLocale(): Locale = if (Build.VERSION.SDK_INT >= 24) resources.configuration.locales[0]
        else resources.configuration.locale

    private fun updateStatus() {
        val now = Date()
        clock.text = android.text.format.DateFormat.getTimeFormat(this).format(now)
        date.text = java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL, currentLocale()).format(now)
        network.text = try {
            val manager = connectivity()
            val caps = manager.getNetworkCapabilities(manager.activeNetwork)
            if (caps == null) getString(R.string.network_offline) else {
                val label = getString(when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> R.string.network_vpn
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> R.string.network_ethernet
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> R.string.network_wifi
                    else -> R.string.network_connected
                })
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) label
                else getString(R.string.network_local, label)
            }
        } catch (_: RuntimeException) { getString(R.string.network_offline) }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            move != null -> finishMove(false)
            query.isNotBlank() -> { query = ""; selectedKey = null; render() }
            else -> { grid.scrollToPosition(0); toolbar[0].requestFocus() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            move = null; query = ""; selectedKey = null; render()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("query", query)
        outState.putString("selection", selectedKey)
        outState.putInt("toolbar", toolbarIndex)
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) immersive() }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::icons.isInitialized && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) icons.trim()
    }

    private fun toast(message: Int) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }

    override fun onDestroy() {
        wallpaperGeneration++
        focusGeneration++
        apps.close()
        icons.close()
        imageWorker.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
