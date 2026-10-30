package com.moneymanager.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.moneymanager.app.domain.AppLockManager
import com.moneymanager.app.ui.components.OneUiIconBadge
import com.moneymanager.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

enum class LockScreenMode {
    UNLOCK,
    SET_PIN,
    CONFIRM_PIN
}

@Composable
fun AppLockScreen(
    mode: LockScreenMode = LockScreenMode.UNLOCK,
    onSuccess: () -> Unit = {},
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentMode by remember { mutableStateOf(mode) }
    var enteredPin by remember { mutableStateOf("") }
    var firstEnteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val shakeOffset = remember { Animatable(0f) }

    fun triggerShake() {
        coroutineScope.launch {
            shakeOffset.animateTo(20f, tween(50))
            shakeOffset.animateTo(-20f, tween(50))
            shakeOffset.animateTo(10f, tween(50))
            shakeOffset.animateTo(0f, tween(50))
        }
    }

    if (currentMode == LockScreenMode.UNLOCK) {
        LifecycleResumeEffect(Unit) {
            if (AppLockManager.consumeBiometricAutoPrompt()) {
                val activity = context.findActivity()
                if (activity != null) {
                    AppLockManager.promptBiometric(activity) { success ->
                        if (success) {
                            onSuccess()
                        }
                    }
                }
            }
            onPauseOrDispose {
                // If the screen pauses while biometric prompt is showing, cancellation is handled cleanly
            }
        }
    }

    fun handleDigit(digit: String) {
        if (enteredPin.length < 4) {
            val newPin = enteredPin + digit
            enteredPin = newPin
            errorMessage = null

            if (newPin.length == 4) {
                when (currentMode) {
                    LockScreenMode.UNLOCK -> {
                        if (AppLockManager.verifyPin(newPin)) {
                            onSuccess()
                        } else {
                            errorMessage = "Incorrect PIN"
                            triggerShake()
                            coroutineScope.launch {
                                delay(300)
                                enteredPin = ""
                            }
                        }
                    }
                    LockScreenMode.SET_PIN -> {
                        firstEnteredPin = newPin
                        coroutineScope.launch {
                            delay(200)
                            enteredPin = ""
                            currentMode = LockScreenMode.CONFIRM_PIN
                        }
                    }
                    LockScreenMode.CONFIRM_PIN -> {
                        if (newPin == firstEnteredPin) {
                            AppLockManager.setLock(newPin)
                            onSuccess()
                        } else {
                            errorMessage = "PINs do not match. Try again."
                            triggerShake()
                            coroutineScope.launch {
                                delay(400)
                                enteredPin = ""
                                firstEnteredPin = ""
                                currentMode = LockScreenMode.SET_PIN
                            }
                        }
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OneUiTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // One UI Circular Colored Icon Badge
            OneUiIconBadge(
                icon = Icons.Default.Lock,
                backgroundColor = OneUiTheme.colors.badgeIndigo,
                size = 64.dp,
                iconSize = 30.dp
            )

            Spacer(modifier = Modifier.height(22.dp))

            val titleText = when (currentMode) {
                LockScreenMode.UNLOCK -> "Enter PIN"
                LockScreenMode.SET_PIN -> "Create PIN"
                LockScreenMode.CONFIRM_PIN -> "Confirm PIN"
            }
            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = OneUiTheme.colors.textPrimary,
                    fontSize = 26.sp
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = errorMessage ?: when (currentMode) {
                    LockScreenMode.UNLOCK -> "Authenticate to access your finances"
                    LockScreenMode.SET_PIN -> "This 4-digit PIN will protect your vault"
                    LockScreenMode.CONFIRM_PIN -> "Re-enter the 4-digit PIN to confirm"
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (errorMessage != null) OneUiTheme.colors.expense else OneUiTheme.colors.textSecondary,
                    fontWeight = if (errorMessage != null) FontWeight.SemiBold else FontWeight.Normal
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 4 Dots PIN Indicator (Borderless One UI circles)
            Row(
                modifier = Modifier.offset(x = shakeOffset.value.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) {
                                    if (errorMessage != null) OneUiTheme.colors.expense else OneUiTheme.colors.primaryAccent
                                } else OneUiTheme.colors.surfaceElevated
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(44.dp))

            // Borderless One UI Numeric Keypad
            val keypad = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("BIO", "0", "DEL")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in keypad) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(26.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (key in row) {
                            when (key) {
                                "BIO" -> {
                                    if (currentMode == LockScreenMode.UNLOCK && AppLockManager.isBiometricEnabled()) {
                                        Surface(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    val activity = context.findActivity()
                                                    if (activity != null) {
                                                        AppLockManager.promptBiometric(activity) { success ->
                                                            if (success) onSuccess()
                                                        }
                                                    }
                                                },
                                            shape = CircleShape,
                                            color = OneUiTheme.colors.surfaceElevated
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Fingerprint,
                                                    contentDescription = "Biometric",
                                                    tint = OneUiTheme.colors.primaryAccent,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(72.dp))
                                    }
                                }
                                "DEL" -> {
                                    Surface(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .clickable { handleBackspace() },
                                        shape = CircleShape,
                                        color = Color.Transparent
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Backspace,
                                                contentDescription = "Delete",
                                                tint = OneUiTheme.colors.textSecondary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    Surface(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .clickable { handleDigit(key) },
                                        shape = CircleShape,
                                        color = OneUiTheme.colors.surface
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = key,
                                                style = MaterialTheme.typography.headlineMedium.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = OneUiTheme.colors.textPrimary,
                                                    fontSize = 26.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (onDismiss != null) {
                Spacer(modifier = Modifier.height(20.dp))
                TextButton(
                    onClick = onDismiss,
                    shape = CircleShape
                ) {
                    Text("Cancel", color = OneUiTheme.colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
