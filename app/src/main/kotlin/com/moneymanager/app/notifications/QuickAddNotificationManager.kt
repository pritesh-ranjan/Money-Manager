package com.moneymanager.app.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moneymanager.app.MainActivity
import com.moneymanager.app.R
import com.moneymanager.app.data.local.entity.TransactionType

object QuickAddNotificationManager {

    const val CHANNEL_ID = "quick_add_transaction_channel"
    const val NOTIFICATION_ID = 1001

    const val ACTION_QUICK_ADD_EXPENSE = "com.moneymanager.app.action.QUICK_ADD_EXPENSE"
    const val ACTION_QUICK_ADD_INCOME = "com.moneymanager.app.action.QUICK_ADD_INCOME"
    const val ACTION_QUICK_ADD_TRANSFER = "com.moneymanager.app.action.QUICK_ADD_TRANSFER"
    const val ACTION_NOTIFICATION_DISMISSED = "com.moneymanager.app.action.QUICK_ADD_NOTIFICATION_DISMISSED"

    private const val REQUEST_CODE_OPEN_APP = 100
    private const val REQUEST_CODE_EXPENSE = 101
    private const val REQUEST_CODE_INCOME = 102
    private const val REQUEST_CODE_TRANSFER = 104
    private const val REQUEST_CODE_DISMISSED = 103

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannelCreated(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Quick Add Transaction",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Always-staying notification for quickly adding expenses or income"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showPersistentNotification(context: Context) {
        val appContext = context.applicationContext
        ensureChannelCreated(appContext)

        if (!hasNotificationPermission(appContext)) {
            return
        }

        val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_CODE_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Button 1: Add Expense
        val expenseIntent = Intent(appContext, MainActivity::class.java).apply {
            action = ACTION_QUICK_ADD_EXPENSE
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val expensePendingIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_CODE_EXPENSE,
            expenseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Button 2: Add Income
        val incomeIntent = Intent(appContext, MainActivity::class.java).apply {
            action = ACTION_QUICK_ADD_INCOME
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val incomePendingIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_CODE_INCOME,
            incomeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Button 3: Transfer
        val transferIntent = Intent(appContext, MainActivity::class.java).apply {
            action = ACTION_QUICK_ADD_TRANSFER
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val transferPendingIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_CODE_TRANSFER,
            transferIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Re-post automatically if swiped away on Android 14+
        val dismissedIntent = Intent(appContext, QuickAddNotificationReceiver::class.java).apply {
            action = ACTION_NOTIFICATION_DISMISSED
            setPackage(appContext.packageName)
        }
        val dismissedPendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_DISMISSED,
            dismissedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteViews = RemoteViews(appContext.packageName, R.layout.notification_quick_add).apply {
            setOnClickPendingIntent(R.id.btn_quick_add_income, incomePendingIntent)
            setOnClickPendingIntent(R.id.btn_quick_add_transfer, transferPendingIntent)
            setOnClickPendingIntent(R.id.btn_quick_add_expense, expensePendingIntent)
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_wallet)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .addAction(R.drawable.ic_notification_income, "Add Income", incomePendingIntent)
            .addAction(R.drawable.ic_notification_transfer, "Transfer", transferPendingIntent)
            .addAction(R.drawable.ic_notification_expense, "Add Expense", expensePendingIntent)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(dismissedPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR

        try {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Ignored if permission was revoked at runtime
        }
    }

    fun resolveTransactionType(intent: Intent?): TransactionType? {
        return when (intent?.action) {
            ACTION_QUICK_ADD_EXPENSE -> TransactionType.EXPENSE
            ACTION_QUICK_ADD_INCOME -> TransactionType.INCOME
            ACTION_QUICK_ADD_TRANSFER -> TransactionType.TRANSFER
            else -> null
        }
    }
}
