package com.neurogarden.app.passive

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent

class TypingFeatureAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 80
        }
        AccessibilitySignalStore.recordServiceConnected(this, System.currentTimeMillis())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        AccessibilitySignalStore.recordRawEvent(this, event.eventType, now)
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        if (event.isPassword) return
        val added = event.addedCount.coerceAtLeast(0)
        val removed = event.removedCount.coerceAtLeast(0)
        val fallbackDelta = fallbackDelta(event)
        if (added > 0 || removed > 0 || fallbackDelta != 0) {
            AccessibilitySignalStore.recordTextChange(
                context = this,
                addedCount = added,
                removedCount = removed,
                fallbackDelta = fallbackDelta,
                eventType = event.eventType,
                eventTime = now
            )
        }
    }

    override fun onInterrupt() = Unit

    private fun fallbackDelta(event: AccessibilityEvent): Int {
        val beforeLength = event.beforeText?.length ?: 0
        val currentLength = event.text.maxOfOrNull { it.length } ?: 0
        return currentLength - beforeLength
    }
}
