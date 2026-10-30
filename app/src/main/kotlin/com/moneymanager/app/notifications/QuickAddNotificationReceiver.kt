package com.moneymanager.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class QuickAddNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            QuickAddNotificationManager.ACTION_NOTIFICATION_DISMISSED -> {
                QuickAddNotificationManager.showPersistentNotification(context)
            }
        }
    }
}
