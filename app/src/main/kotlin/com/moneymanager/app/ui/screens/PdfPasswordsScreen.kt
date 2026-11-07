package com.moneymanager.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moneymanager.app.domain.AppLockManager
import com.moneymanager.app.ui.components.*
import com.moneymanager.app.ui.main.MainViewModel
import com.moneymanager.app.ui.theme.OneUiTheme

@Composable
fun PdfPasswordsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val passwords by viewModel.pdfPasswords.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordToDelete by remember { mutableStateOf<String?>(null) }
    val revealedPasswords = remember { mutableStateListOf<String>() }
    var isAppLockVerified by remember { mutableStateOf(!AppLockManager.isLockConfigured()) }
    var pendingVerifyAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showVerifyDialog by remember { mutableStateOf(false) }

    fun runWithAuth(action: () -> Unit) {
        if (isAppLockVerified || !AppLockManager.isLockConfigured()) {
            action()
        } else {
            pendingVerifyAction = action
            showVerifyDialog = true
        }
    }

    OneUiCollapsibleScaffold(
        title = "Statement Passwords",
        expandedHeight = 205.dp,
        centeredExpandedTitle = true,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OneUiTheme.colors.textPrimary
                )
            }
        },
        actions = {
            IconButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Password",
                    tint = OneUiTheme.colors.primaryAccent
                )
            }
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(OneUiTheme.colors.background),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = 96.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Explanatory Info Card
            item(key = "info_card") {
                OneUiCard(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = OneUiTheme.colors.primaryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Auto-Unlock Decryption",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = OneUiTheme.colors.textPrimary,
                                    fontSize = 16.sp
                                )
                            )
                        }
                        Text(
                            text = "Add passwords commonly used for your bank and credit card statements (e.g. name + DOB DDMM, PAN, or last 4 digits). When statements are imported manually or via Gmail sync, the app tests each saved password automatically.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }
            }

            // Empty State
            if (passwords.isEmpty()) {
                item(key = "empty_passwords_state") {
                    OneUiCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Password,
                                contentDescription = null,
                                tint = OneUiTheme.colors.primaryAccent,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "No Passwords Saved",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OneUiTheme.colors.textPrimary
                                )
                            )
                            Text(
                                text = "Save your statement passwords here so encrypted bank PDFs unlock automatically without manual entry.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OneUiPillButton(
                                text = "Add Password",
                                onClick = { showAddDialog = true },
                                containerColor = OneUiTheme.colors.primaryAccent,
                                contentColor = Color.White
                            )
                        }
                    }
                }
            } else {
                item(key = "passwords_list_header") {
                    OneUiSectionHeader(text = "SAVED PASSWORDS (${passwords.size})")
                }

                items(passwords, key = { it }) { pwd ->
                    val isRevealed = revealedPasswords.contains(pwd)

                    OneUiCard(
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(OneUiTheme.colors.primaryAccent.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = OneUiTheme.colors.primaryAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = if (isRevealed) pwd else "••••••••",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = OneUiTheme.colors.textPrimary,
                                            fontSize = 16.sp
                                        )
                                    )
                                    Text(
                                        text = "Saved for auto-decrypt",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = OneUiTheme.colors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (isRevealed) {
                                            revealedPasswords.remove(pwd)
                                        } else {
                                            runWithAuth {
                                                revealedPasswords.add(pwd)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isRevealed) "Hide password" else "Show password",
                                        tint = OneUiTheme.colors.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        runWithAuth {
                                            passwordToDelete = pwd
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Password",
                                        tint = OneUiTheme.colors.expense,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Password Dialog
    if (showAddDialog) {
        var isVisible by remember { mutableStateOf(false) }

        OneUiBottomDialog(
            onDismissRequest = {
                showAddDialog = false
                passwordInput = ""
            },
            title = "Add Statement Password",
            confirmButtonText = "Save",
            onConfirm = {
                val clean = passwordInput.trim()
                if (clean.isNotBlank()) {
                    viewModel.addPdfPassword(clean)
                    showAddDialog = false
                    passwordInput = ""
                }
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Enter a password used by any of your banks (e.g. HDFC, ICICI, Axis, SBI). The app will try it automatically when opening statements.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = OneUiTheme.colors.textSecondary,
                        fontSize = 13.sp
                    )
                )

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isVisible = !isVisible }) {
                            Icon(
                                imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }
    }

    // Delete Confirmation Dialog
    if (passwordToDelete != null) {
        OneUiBottomDialog(
            onDismissRequest = { passwordToDelete = null },
            title = "Delete Password?",
            confirmButtonText = "Delete",
            confirmButtonColor = OneUiTheme.colors.expense,
            onConfirm = {
                passwordToDelete?.let { viewModel.removePdfPassword(it) }
                passwordToDelete = null
            }
        ) {
            Text(
                text = "Are you sure you want to remove this saved password? The app will no longer be able to auto-decrypt statements using it.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = OneUiTheme.colors.textPrimary,
                    fontSize = 14.sp
                )
            )
        }
    }

    if (showVerifyDialog) {
        Dialog(
            onDismissRequest = {
                showVerifyDialog = false
                pendingVerifyAction = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AppLockScreen(
                mode = LockScreenMode.VERIFY,
                onSuccess = {
                    showVerifyDialog = false
                    isAppLockVerified = true
                    val action = pendingVerifyAction
                    pendingVerifyAction = null
                    action?.invoke()
                },
                onDismiss = {
                    showVerifyDialog = false
                    pendingVerifyAction = null
                }
            )
        }
    }
}
