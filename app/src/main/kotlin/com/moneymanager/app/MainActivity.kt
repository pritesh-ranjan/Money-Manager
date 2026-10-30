package com.moneymanager.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.domain.AppLockManager
import com.moneymanager.app.notifications.QuickAddNotificationManager
import com.moneymanager.app.ui.main.MainScreen
import com.moneymanager.app.ui.screens.AppLockScreen
import com.moneymanager.app.ui.theme.AmoledBlack
import com.moneymanager.app.ui.theme.MoneyManagerTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingQuickAddType = MutableStateFlow<TransactionType?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            QuickAddNotificationManager.showPersistentNotification(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLockManager.init(this)
        com.moneymanager.app.ui.theme.ThemePreferenceManager.init(this)
        // Prevent screen capture and task switcher thumbnail leaks when app lock is active
        if (AppLockManager.isLockConfigured()) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()

        ensurePersistentNotification()
        handleQuickAddIntent(intent)

        setContent {
            MoneyManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = com.moneymanager.app.ui.theme.OneUiTheme.colors.background
                ) {
                    val isLocked by AppLockManager.isLocked.collectAsState()
                    val quickAddType by pendingQuickAddType.collectAsState()

                    // MainScreen always stays composed so in-flight state, sheets and activity result launchers survive locking
                    MainScreen(
                        quickAddType = if (isLocked) null else quickAddType,
                        onQuickAddConsumed = {
                            pendingQuickAddType.value = null
                        }
                    )

                    if (isLocked) {
                        Dialog(
                            onDismissRequest = {
                                // Pressing back on lock screen exits app to home, never bypasses lock
                                moveTaskToBack(true)
                            },
                            properties = DialogProperties(
                                dismissOnClickOutside = false,
                                usePlatformDefaultWidth = false,
                                decorFitsSystemWindows = false,
                                securePolicy = SecureFlagPolicy.SecureOn
                            )
                        ) {
                            AppLockScreen(
                                onSuccess = {
                                    AppLockManager.unlock()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickAddIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AppLockManager.onAppForegrounded()
    }

    override fun onResume() {
        super.onResume()
        QuickAddNotificationManager.showPersistentNotification(this)
    }

    override fun onStop() {
        super.onStop()
        AppLockManager.cancelBiometric()
        if (!isChangingConfigurations) {
            AppLockManager.onAppBackgrounded()
        }
    }

    private fun ensurePersistentNotification() {
        QuickAddNotificationManager.ensureChannelCreated(this)
        if (QuickAddNotificationManager.hasNotificationPermission(this)) {
            QuickAddNotificationManager.showPersistentNotification(this)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleQuickAddIntent(intent: Intent?) {
        val resolvedType = QuickAddNotificationManager.resolveTransactionType(intent)
        if (resolvedType != null) {
            pendingQuickAddType.value = resolvedType
            intent?.action = null
        }
    }
}
