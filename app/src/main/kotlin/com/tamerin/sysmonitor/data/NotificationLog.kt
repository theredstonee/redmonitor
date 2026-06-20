package com.tamerin.sysmonitor.data

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class NotificationEntry(
    val key: String,
    val packageName: String,
    val displayName: String,
    val title: String,
    val text: String,
    val subText: String,
    val ticker: String,
    val postedAt: Long,
    val channelId: String?,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    val event: Event
) {
    enum class Event { POSTED, REMOVED }
}

/**
 * In-Memory Log aller Notifications (kein DB-Persist — privacy: stoppt mit dem Prozess).
 *
 * Wird vom NotificationLoggerService gefüttert, der vom System gebunden wird
 * sobald der User „Notification-Zugriff" für die App aktiviert.
 *
 * Kapazität: zirkulärer Puffer mit MAX_ENTRIES — älteste fallen raus.
 */
object NotificationLog {
    const val MAX_ENTRIES = 500

    private val _entries = MutableStateFlow<List<NotificationEntry>>(emptyList())
    val entries: StateFlow<List<NotificationEntry>> = _entries.asStateFlow()

    fun add(entry: NotificationEntry) {
        val current = _entries.value
        val next = (listOf(entry) + current).take(MAX_ENTRIES)
        _entries.value = next
    }

    fun clear() {
        _entries.value = emptyList()
    }
}

class NotificationLoggerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { NotificationLog.add(toEntry(it, NotificationEntry.Event.POSTED)) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { NotificationLog.add(toEntry(it, NotificationEntry.Event.REMOVED)) }
    }

    private fun toEntry(sbn: StatusBarNotification, event: NotificationEntry.Event): NotificationEntry {
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val ticker = n.tickerText?.toString().orEmpty()
        val display = runCatching {
            val pm = packageManager
            val ai = pm.getApplicationInfo(sbn.packageName, 0)
            ai.loadLabel(pm).toString()
        }.getOrDefault(sbn.packageName)
        val isOngoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val isGroup = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        return NotificationEntry(
            key = sbn.key,
            packageName = sbn.packageName,
            displayName = display,
            title = title,
            text = text,
            subText = subText,
            ticker = ticker,
            postedAt = sbn.postTime,
            channelId = n.channelId,
            isOngoing = isOngoing,
            isGroupSummary = isGroup,
            event = event
        )
    }
}

/**
 * Check ob dem User-NotificationListener Zugriff erteilt wurde.
 * Liest die Settings-Liste enabled_notification_listeners.
 */
fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = android.provider.Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    val pkg = context.packageName
    return flat.split(':').any { it.startsWith("$pkg/") }
}
