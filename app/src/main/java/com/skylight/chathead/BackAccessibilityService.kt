package com.skylight.chathead

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * A minimal accessibility service whose only job is to perform the global "Back"
 * action on behalf of the chathead. A normal app cannot inject a BACK key event
 * into another app's window (that needs the signature-level INJECT_EVENTS
 * permission), but an enabled accessibility service can trigger it globally via
 * [performGlobalAction]. The service must be enabled in system settings; on this
 * debug device that is done over adb.
 */
class BackAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't observe events; we only expose the global Back action.
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: BackAccessibilityService? = null

        /**
         * Sends the global Back action to the foreground app.
         * @return true if the service was running and the action was dispatched.
         */
        fun performBack(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }
}
