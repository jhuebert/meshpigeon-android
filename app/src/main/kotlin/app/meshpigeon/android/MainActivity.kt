package app.meshpigeon.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.meshpigeon.feature.contacts.ContactsScreen
import app.meshpigeon.feature.messaging.ChatsScreen
import app.meshpigeon.feature.messaging.ChatsViewModel
import app.meshpigeon.feature.messaging.ConversationScreen
import app.meshpigeon.feature.messaging.ConversationViewModel
import app.meshpigeon.feature.onboarding.OnboardingScreen
import app.meshpigeon.feature.onboarding.OnboardingViewModel
import app.meshpigeon.ui.MeshPigeonTheme
import kotlinx.coroutines.flow.first

/**
 * MeshPigeon main activity: offline-first home is Chats (00 principle 1),
 * bottom navigation (Chats / Contacts / Map), drawer for identities.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = AppGraph.of(this)
        setContent {
            MeshPigeonTheme {
                MeshPigeonApp(graph)
            }
        }
    }
}

private sealed class Destination(val route: String) {
    data object Onboarding : Destination("onboarding")
    data object Chats : Destination("chats")
    data object Contacts : Destination("contacts")
    data object Map : Destination("map")
    data object Conversation : Destination("conversation/{conversationId}") {
        fun of(id: Long) = "conversation/$id"
    }
}

@Composable
fun MeshPigeonApp(graph: AppGraph) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var showConnectSheet by remember { mutableStateOf(false) }

    // first-run gate: without a profile, start at onboarding (07 §2)
    var hasProfile by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        hasProfile = graph.identities.active().first() != null
    }

    if (hasProfile == null) return // profile check is a fast local query

    Scaffold(
        bottomBar = {
            if (currentRoute in setOf(Destination.Chats.route, Destination.Contacts.route, Destination.Map.route)) {
                NavigationBar(
                    modifier = Modifier.semantics { contentDescription = "Main navigation" },
                ) {
                    NavigationBarItem(
                        selected = currentRoute == Destination.Chats.route,
                        onClick = { navController.navigate(Destination.Chats.route) { popUpTo(Destination.Chats.route); launchSingleTop = true } },
                        icon = { Icon(Icons.Filled.Chat, contentDescription = null) },
                        label = { Text("Chats") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Destination.Contacts.route,
                        onClick = { navController.navigate(Destination.Contacts.route) { launchSingleTop = true } },
                        icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                        label = { Text("Contacts") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Destination.Map.route,
                        onClick = { navController.navigate(Destination.Map.route) { launchSingleTop = true } },
                        icon = { Icon(Icons.Filled.Place, contentDescription = null) },
                        label = { Text("Map") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (hasProfile == false) Destination.Onboarding.route else Destination.Chats.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Onboarding.route) {
                val vm: OnboardingViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            OnboardingViewModel(
                                graph.identities, graph.channels, graph.conversations, graph.crypto,
                            )
                        }
                    },
                )
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Destination.Chats.route) {
                            popUpTo(Destination.Onboarding.route) { inclusive = true }
                        }
                    },
                    onScanForRadios = { showConnectSheet = true },
                    viewModel = vm,
                )
            }
            composable(Destination.Chats.route) {
                val vm: ChatsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            ChatsViewModel(
                                graph.identities, graph.conversations, graph.messages,
                                graph.contacts, graph.channels,
                            )
                        }
                    },
                )
                ChatsScreen(
                    viewModel = vm,
                    onOpenConversation = { conv ->
                        navController.navigate(Destination.Conversation.of(conv.id))
                    },
                    onStartChat = { /* start-chat sheet lands with M2 contacts */ },
                    onConnectRadio = { showConnectSheet = true },
                )
            }
            composable(Destination.Contacts.route) {
                ContactsScreen()
            }
            composable(Destination.Map.route) {
                MapPlaceholder()
            }
            composable(Destination.Conversation.route) { entry ->
                val conversationId = entry.arguments?.getString("conversationId")?.toLongOrNull() ?: return@composable
                val vm: ConversationViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            ConversationViewModel(
                                graph.identities, graph.conversations, graph.messages,
                                graph.contacts, graph.channels, graph.sendMessage, conversationId,
                            )
                        }
                    },
                )
                ConversationScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onRetry = { /* retry affordance re-enqueues in M2 */ },
                )
            }
        }
    }

    if (showConnectSheet) {
        RadioConnectSheet(graph = graph, onDismiss = { showConnectSheet = false })
    }
}

@Composable
private fun MapPlaceholder() {
    // Map tab (07 §10): offline OSM tiles + contacts/repeater pins land in M3.
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        app.meshpigeon.ui.EmptyState(
            title = "Map coming soon",
            body = "Contacts with a location will appear here — no internet needed.",
        )
    }
}

/** Reconnect after phone restart (06 §6, opt-in via Settings toggle). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(Intent(context, RadioConnectionService::class.java))
        }
    }
}
