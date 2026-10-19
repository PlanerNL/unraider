package nl.planernl.unraider

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import nl.planernl.unraider.data.ServerConfig
import nl.planernl.unraider.ui.LoadingState
import nl.planernl.unraider.ui.MainViewModel
import nl.planernl.unraider.ui.screens.DashboardScreen
import nl.planernl.unraider.ui.screens.DisksScreen
import nl.planernl.unraider.ui.screens.DockerScreen
import nl.planernl.unraider.ui.screens.SetupScreen
import nl.planernl.unraider.ui.screens.VmsScreen
import nl.planernl.unraider.ui.screens.WebGuiScreen
import nl.planernl.unraider.ui.theme.UnraidTheme

// AppCompatActivity is nodig voor de taalkeuze per app op Android 12 en ouder
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnraidTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }
}

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    SCHIJVEN("schijven", R.string.tab_disks, Icons.Outlined.Storage),
    OVERZICHT("overzicht", R.string.tab_overview, Icons.Outlined.Dashboard),
    DOCKER("docker", R.string.tab_docker, Icons.Outlined.ViewInAr),
    VMS("vms", R.string.tab_vms, Icons.Outlined.Dns),
    WEBGUI("webgui", R.string.tab_webgui, Icons.Outlined.Language),
}

private const val ROUTE_SETTINGS = "instellingen"

@Composable
private fun App(vm: MainViewModel = viewModel()) {
    val config by vm.config.collectAsStateWithLifecycle()
    val cfg = config
    when {
        cfg == null -> LoadingState()
        !cfg.isComplete -> Scaffold { padding ->
            Surface(Modifier.padding(padding)) { SetupScreen(vm, cfg) }
        }
        // Bij een ander adres/sleutel alles opnieuw opbouwen, zodat schermen direct verversen
        else -> key(cfg) { MainScaffold(vm, cfg) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(vm: MainViewModel, cfg: ServerConfig) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }

    val resources = LocalContext.current.resources
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it.resolve(resources)) }
    }

    val title = Tab.entries.firstOrNull { it.route == route }?.let { stringResource(it.label) }
        ?: if (route == ROUTE_SETTINGS) stringResource(R.string.settings) else "Unraid"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    if (route != ROUTE_SETTINGS) {
                        IconButton(onClick = { nav.navigate(ROUTE_SETTINGS) }) {
                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar {
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
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        NavHost(nav, startDestination = Tab.SCHIJVEN.route, modifier = Modifier.padding(padding)) {
            composable(Tab.OVERZICHT.route) {
                DashboardScreen(vm, onOpenDisks = { nav.navigate(Tab.SCHIJVEN.route) { launchSingleTop = true } })
            }
            composable(Tab.SCHIJVEN.route) { DisksScreen(vm) }
            composable(Tab.DOCKER.route) { DockerScreen(vm) }
            composable(Tab.VMS.route) { VmsScreen(vm) }
            composable(Tab.WEBGUI.route) { WebGuiScreen(cfg.baseUrl, cfg.certPin) }
            composable(ROUTE_SETTINGS) { SetupScreen(vm, cfg, onDone = { nav.popBackStack() }) }
        }
    }
}
