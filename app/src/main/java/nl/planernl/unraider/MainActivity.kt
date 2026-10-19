package nl.planernl.unraider

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import nl.planernl.unraider.data.IconLoader
import nl.planernl.unraider.data.LocalIconLoader
import nl.planernl.unraider.data.ServerConfig
import nl.planernl.unraider.ui.LoadingState
import nl.planernl.unraider.ui.MainViewModel
import nl.planernl.unraider.ui.screens.AppsScreen
import nl.planernl.unraider.ui.screens.NotificationsScreen
import nl.planernl.unraider.ui.screens.OverviewScreen
import nl.planernl.unraider.ui.screens.SetupScreen
import nl.planernl.unraider.ui.screens.SharesScreen
import nl.planernl.unraider.ui.screens.StorageScreen
import nl.planernl.unraider.ui.screens.VmsScreen
import nl.planernl.unraider.ui.screens.WebGuiScreen
import nl.planernl.unraider.ui.theme.Rack
import nl.planernl.unraider.ui.theme.UnraiderTheme

// AppCompatActivity is nodig voor de taalkeuze per app op Android 12 en ouder
class MainActivity : AppCompatActivity() {

    /** Gezet als de app via een pushmelding wordt geopend. */
    private var openNotifications by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Altijd donker: lichte iconen in status- en navigatiebalk
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#141619")),
        )
        openNotifications = intent?.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false) == true
        setContent {
            UnraiderTheme {
                Surface(color = Rack.background) {
                    App(
                        openNotifications = openNotifications,
                        onNotificationsOpened = { openNotifications = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false)) openNotifications = true
    }

    companion object {
        const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"
    }
}

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    OVERVIEW("overzicht", R.string.tab_overview, Icons.Outlined.Dashboard),
    STORAGE("opslag", R.string.tab_storage, Icons.Outlined.Dns),
    SHARES("shares", R.string.tab_shares, Icons.Outlined.Folder),
    APPS("apps", R.string.tab_apps, Icons.Outlined.ViewInAr),
    VMS("vms", R.string.tab_vms, Icons.Outlined.Computer),
}

private const val ROUTE_SETTINGS = "instellingen"
private const val ROUTE_NOTIFICATIONS = "meldingen"
private const val ROUTE_WEBGUI = "webgui"

@Composable
private fun App(
    openNotifications: Boolean,
    onNotificationsOpened: () -> Unit,
    vm: MainViewModel = viewModel(),
) {
    val config by vm.config.collectAsStateWithLifecycle()
    val cfg = config
    when {
        cfg == null -> LoadingState()
        !cfg.isComplete -> Scaffold(containerColor = Rack.background) { padding ->
            Surface(Modifier.padding(padding), color = Rack.background) { SetupScreen(vm, cfg) }
        }
        // Bij een ander adres of sleutel alles opnieuw opbouwen, zodat schermen direct verversen
        else -> key(cfg) { MainScaffold(vm, cfg, openNotifications, onNotificationsOpened) }
    }
}

@Composable
private fun MainScaffold(
    vm: MainViewModel,
    cfg: ServerConfig,
    openNotifications: Boolean,
    onNotificationsOpened: () -> Unit,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val iconLoader = remember(cfg) { IconLoader.create(context.applicationContext, cfg) }

    val resources = context.resources
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it.resolve(resources)) }
    }
    LaunchedEffect(openNotifications) {
        if (openNotifications) {
            nav.navigate(ROUTE_NOTIFICATIONS) { launchSingleTop = true }
            onNotificationsOpened()
        }
    }

    fun openWebGui(path: String = "") = nav.navigate("$ROUTE_WEBGUI?path=${android.net.Uri.encode(path)}")

    CompositionLocalProvider(LocalIconLoader provides iconLoader) {
        Scaffold(
            containerColor = Rack.background,
            bottomBar = {
                Column {
                    HorizontalDivider(color = Rack.divider)
                    NavigationBar(containerColor = Rack.background, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.label)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Rack.accent,
                                    selectedTextColor = Rack.accent,
                                    indicatorColor = Color.Transparent,
                                    unselectedIconColor = Rack.textSecondary,
                                    unselectedTextColor = Rack.textSecondary,
                                ),
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            // Het overzicht is het startscherm
            NavHost(nav, startDestination = Tab.OVERVIEW.route, modifier = Modifier.padding(padding)) {
                composable(Tab.OVERVIEW.route) {
                    OverviewScreen(
                        vm = vm,
                        onOpenStorage = {
                            nav.navigate(Tab.STORAGE.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onOpenNotifications = { nav.navigate(ROUTE_NOTIFICATIONS) { launchSingleTop = true } },
                        onOpenWebGui = { openWebGui() },
                        onOpenSettings = { nav.navigate(ROUTE_SETTINGS) { launchSingleTop = true } },
                    )
                }
                composable(Tab.STORAGE.route) { StorageScreen(vm) }
                composable(Tab.SHARES.route) { SharesScreen(vm) }
                composable(Tab.APPS.route) { AppsScreen(vm, cfg.baseUrl, onOpenWebGui = { openWebGui(it) }) }
                composable(Tab.VMS.route) { VmsScreen(vm) }
                composable(ROUTE_NOTIFICATIONS) { NotificationsScreen(vm, onBack = { nav.popBackStack() }) }
                composable(ROUTE_SETTINGS) { SetupScreen(vm, cfg, onDone = { nav.popBackStack() }) }
                composable(
                    "$ROUTE_WEBGUI?path={path}",
                    arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    WebGuiScreen(cfg.baseUrl, cfg.certPin, entry.arguments?.getString("path").orEmpty())
                }
            }
        }
    }
}
