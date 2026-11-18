package app.parallax.opencode.ui

import kotlinx.coroutines.flow.first
import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.lifecycle.Lifecycle
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.parallax.opencode.core.data.ServerStore
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.ui.components.IconTabBar
import app.parallax.opencode.ui.components.TabItem
import app.parallax.opencode.ui.screens.agents.AgentsScreen
import app.parallax.opencode.ui.screens.chat.ChatScreen
import app.parallax.opencode.ui.screens.connect.ConnectScreen
import app.parallax.opencode.ui.screens.connectors.ConnectorsScreen
import app.parallax.opencode.ui.screens.inbox.InboxScreen
import app.parallax.opencode.ui.screens.menu.MenuSheet
import app.parallax.opencode.ui.screens.session.SessionScreen
import app.parallax.opencode.ui.theme.ParallaxTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable private data object Chat
@Serializable private data object Agents
@Serializable private data class Session(val sessionId: String)
@Serializable private data object Inbox
@Serializable private data object Connectors
@Serializable private data class Connect(val link: String? = null)
@Serializable private data class Voice(val sessionId: String? = null)

@Composable
fun AppNavHost(repository: AgentRepository, store: ServerStore, deepLink: String?, onDeepLinkHandled: () -> Unit) {
    val c = ParallaxTheme.colors
    val nav = rememberNavController()
    val activeServer by repository.activeServer.collectAsStateWithLifecycle()
    val inbox by repository.inbox.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var pendingConnectLink by remember { mutableStateOf<String?>(null) }

    // Deep links:
    //  - parallax://session/<serverId>/<sessionId> → switch server, open session
    //  - openchamber://connect?... → Connect + redeemPairingLink
    LaunchedEffect(deepLink) {
        if (deepLink == null) return@LaunchedEffect
        // The NavHost only exists once a server is set up, and sets its graph during composition;
        // navigating before that throws. Links that navigate wait for it.
        suspend fun awaitNav() = androidx.compose.runtime.snapshotFlow { nav.currentBackStackEntry }.first { it != null }
        when {
            deepLink.startsWith("parallax://session/") -> {
                val rest = deepLink.removePrefix("parallax://session/").split("/")
                if (rest.size >= 2 && activeServer != null) {
                    val serverId = rest[0]
                    val sessionId = rest.drop(1).joinToString("/")
                    runCatching { repository.setActiveServer(serverId) }
                    awaitNav()
                    nav.navigate(Session(sessionId)) { launchSingleTop = true }
                }
            }
            deepLink.startsWith("openchamber://connect") || deepLink.startsWith("parallax://connect") -> {
                pendingConnectLink = deepLink
                // With no server yet, the standalone Connect screen picks up pendingConnectLink itself.
                if (activeServer != null) {
                    awaitNav()
                    nav.navigate(Connect(link = Uri.encode(deepLink))) { launchSingleTop = true }
                }
            }
        }
        onDeepLinkHandled()
    }

    val entry by nav.currentBackStackEntryAsState()
    val hierarchy = entry?.destination?.hierarchy
    val showBar = hierarchy?.any {
        it.hasRoute(Chat::class) || it.hasRoute(Agents::class) ||
            it.hasRoute(Inbox::class) || it.hasRoute(Connectors::class)
    } == true

    val waiting = inbox.count { it.kind.name == "PERMISSION" || it.kind.name == "FORM" }

    fun goTab(index: Int) {
        val dest = when (index) {
            1 -> Agents
            2 -> Inbox
            3 -> Connectors
            else -> Chat
        }
        nav.navigate(dest) {
            launchSingleTop = true
            popUpTo(Chat) { inclusive = false }
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        if (activeServer == null) {
            ConnectScreen(
                repository = repository,
                initialLink = pendingConnectLink?.also { pendingConnectLink = null },
                onConnected = {},
            )
        } else {
            NavHost(
                navController = nav,
                startDestination = Chat,
                enterTransition = { slideInHorizontally(tween(200, easing = FastOutSlowInEasing)) { it / 12 } + fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(100)) },
                popEnterTransition = { fadeIn(tween(140)) },
                popExitTransition = { slideOutHorizontally(tween(170)) { it / 10 } + fadeOut(tween(135)) },
            ) {
                composable<Chat> {
                    ChatScreen(
                        repository = repository,
                        onMenu = { showMenu = true },
                        onOpenSession = { nav.navigate(Session(it)) },
                        onVoice = { nav.navigate(Voice()) },
                        onSeeAll = { goTab(1) },
                        onLaunched = { ids ->
                            scope.launch {
                                if (ids.size == 1) {
                                    nav.navigate(Session(ids.first()))
                                } else {
                                    goTab(1)
                                }
                            }
                        },
                    )
                }
                composable<Agents> {
                    AgentsScreen(
                        repository = repository,
                        onMenu = { showMenu = true },
                        onOpenSession = { nav.navigate(Session(it)) },
                    )
                }
                composable<Session> { backStack ->
                    val id = backStack.arguments?.getString("sessionId").orEmpty()
                    SessionScreen(
                        sessionId = id,
                        repository = repository,
                        onBack = { if (backStack.lifecycle.currentState == Lifecycle.State.RESUMED) nav.popBackStack() },
                        onOpenSession = { nav.navigate(Session(it)) },
                        onVoice = { sid -> nav.navigate(Voice(sid)) },
                    )
                }
                composable<Inbox> {
                    InboxScreen(
                        repository = repository,
                        onMenu = { showMenu = true },
                        onOpenSession = { nav.navigate(Session(it)) },
                    )
                }
                composable<Connectors> {
                    ConnectorsScreen(
                        repository = repository,
                        onMenu = { showMenu = true },
                    )
                }
                composable<Voice> { backStack ->
                    val vid = backStack.arguments?.getString("sessionId")
                    app.parallax.opencode.ui.screens.voice.VoiceScreen(
                        repository = repository,
                        sessionId = vid,
                        onClose = { nav.popBackStack() },
                        onOpenChat = { id ->
                            nav.navigate(Session(id)) { popUpTo<Voice> { inclusive = true } }
                        },
                    )
                }
                composable<Connect> { backStack ->
                    val link = backStack.arguments?.getString("link")?.let { Uri.decode(it) }
                        ?: pendingConnectLink?.also { pendingConnectLink = null }
                    ConnectScreen(
                        repository = repository,
                        initialLink = link,
                        onConnected = {
                            nav.navigate(Chat) {
                                popUpTo(Chat) { inclusive = true }
                            }
                        },
                    )
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showBar && activeServer != null,
            enter = fadeIn(tween(140)), exit = fadeOut(tween(90)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(Modifier.fillMaxWidth()) {
                // Fade the list out under the floating controls so content never collides with them.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                0f to ParallaxTheme.colors.bg.copy(alpha = 0f),
                                0.5f to ParallaxTheme.colors.bg.copy(alpha = 0.92f),
                                1f to ParallaxTheme.colors.bg,
                            ),
                        ),
                )
                IconTabBar(
                    items = listOf(
                        TabItem(Icons.Rounded.ChatBubbleOutline, "Chat"),
                        TabItem(Icons.Rounded.Layers, "Agents"),
                        TabItem(Icons.Rounded.TaskAlt, "Inbox", badge = waiting),
                        TabItem(Icons.Rounded.Widgets, "Connectors"),
                    ),
                    selected = when {
                        hierarchy?.any { it.hasRoute(Agents::class) } == true -> 1
                        hierarchy?.any { it.hasRoute(Inbox::class) } == true -> 2
                        hierarchy?.any { it.hasRoute(Connectors::class) } == true -> 3
                        else -> 0
                    },
                    onSelect = { goTab(it) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                )
            }
        }

        if (showMenu && activeServer != null) {
            MenuSheet(
                repository = repository,
                store = store,
                onDismiss = { showMenu = false },
                onAddServer = { nav.navigate(Connect()) },
            )
        }
    }
}
