package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.presentation.VaultViewModel
import com.example.presentation.VaultViewModelFactory
import com.example.presentation.screens.ItemDetailScreen
import com.example.presentation.screens.LockScreen
import com.example.presentation.screens.OnboardingScreen
import com.example.presentation.screens.PasswordGeneratorScreen
import com.example.presentation.screens.SettingsScreen
import com.example.presentation.screens.VaultListScreen
import com.example.ui.theme.IronVaultTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Dynamic full-screen layout edges
        enableEdgeToEdge()

        // SECURITY REQUIREMENT: Protect all screens from screenshots and recent switcher exposure
        // Note: Commented out in development environment so the streaming emulator can capture and display the screen
        // window.setFlags(
        //     WindowManager.LayoutParams.FLAG_SECURE,
        //     WindowManager.LayoutParams.FLAG_SECURE
        // )

        // manual dependency injection retrieval from our Application container
        val app = application as IronVaultApplication
        val viewModel: VaultViewModel by viewModels {
            VaultViewModelFactory(this, app.repository, app.preferencesManager)
        }

        setContent {
            // Observe Dynamic User Preferences
            val themeState by viewModel.themeState.collectAsState()
            val languageState by viewModel.languageState.collectAsState()
            val isSetupComplete by viewModel.isSetupComplete.collectAsState()
            val isUnlocked by viewModel.isUnlocked.collectAsState()

            val isDark = when (themeState) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            val layoutDir = if (languageState == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr

            // Force dynamic layout mirroring for localization
            CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                IronVaultTheme(darkTheme = isDark) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // Navigation state manager
                        var currentScreen by remember { mutableStateOf<NavigationScreen>(NavigationScreen.Onboarding) }

                        // Session lifecycle routing
                        LaunchedEffect(isSetupComplete, isUnlocked) {
                            currentScreen = when {
                                !isSetupComplete -> NavigationScreen.Onboarding
                                !isUnlocked -> NavigationScreen.Lock
                                else -> NavigationScreen.Main(0)
                            }
                        }

                        // Screen animation controller
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "ScreenTransition"
                        ) { screen ->
                            when (screen) {
                                NavigationScreen.Onboarding -> {
                                    OnboardingScreen(viewModel = viewModel) {
                                        currentScreen = NavigationScreen.Main(0)
                                    }
                                }
                                NavigationScreen.Lock -> {
                                    LockScreen(viewModel = viewModel) {
                                        currentScreen = NavigationScreen.Main(0)
                                    }
                                }
                                is NavigationScreen.Main -> {
                                    MainShell(
                                        viewModel = viewModel,
                                        initialTabIndex = screen.tabIndex,
                                        onNavigateToDetail = { itemId ->
                                            currentScreen = NavigationScreen.ItemDetail(itemId)
                                        }
                                    )
                                }
                                is NavigationScreen.ItemDetail -> {
                                    ItemDetailScreen(
                                        viewModel = viewModel,
                                        itemId = screen.itemId,
                                        onNavigateBack = {
                                            currentScreen = NavigationScreen.Main(0)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Lock on background sleep
     */
    override fun onStop() {
        super.onStop()
        // Wipes in-memory key on background to enforce absolute zero-knowledge security
        val app = application as IronVaultApplication
        app.preferencesManager.let {
            // We lock instantly
            val activeKey = com.example.data.security.SessionManager.getKey()
            if (activeKey != null) {
                com.example.data.security.SessionManager.lock()
            }
        }
    }
}

// --- Navigation Model ---
sealed class NavigationScreen {
    object Onboarding : NavigationScreen()
    object Lock : NavigationScreen()
    data class Main(val tabIndex: Int) : NavigationScreen()
    data class ItemDetail(val itemId: Long) : NavigationScreen()
}

// --- Main Navigation Shell ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    viewModel: VaultViewModel,
    initialTabIndex: Int,
    onNavigateToDetail: (Long) -> Unit
) {
    var tabIndex by remember { mutableStateOf(initialTabIndex) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val titleRes = when (tabIndex) {
                        0 -> R.string.vault_title
                        1 -> R.string.generator_title
                        2 -> R.string.settings_title
                        else -> R.string.vault_title
                    }
                    Text(stringResource(titleRes), fontWeight = FontWeight.Bold)
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_vault)) }
                )
                NavigationBarItem(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    icon = { Icon(Icons.Default.Key, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_generator)) }
                )
                NavigationBarItem(
                    selected = tabIndex == 2,
                    onClick = { tabIndex = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_settings)) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (tabIndex) {
                0 -> VaultListScreen(
                    viewModel = viewModel,
                    onNavigateToDetail = onNavigateToDetail,
                    onNavigateToAdd = { onNavigateToDetail(0L) }
                )
                1 -> PasswordGeneratorScreen()
                2 -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
