package com.skylight.chathead

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * A minimal accessibility service with two jobs:
 *
 * 1. Perform the global "Back" action on behalf of the chathead. A normal app
 *    cannot inject a BACK key event into another app's window (that needs the
 *    signature-level INJECT_EVENTS permission), but an enabled accessibility
 *    service can trigger it globally via [performGlobalAction].
 *
 * 2. Hide the chathead when it shouldn't be shown: while our own full-screen
 *    app-picker is in front, or while the Skylight in-app photo screensaver is
 *    up. The picker is detected from window-state-changed events (package +
 *    class); the screensaver runs inside the same CalendarActivity window as the
 *    calendar, so it's detected by looking for its distinctive view ids in the
 *    active window's node tree.
 *
 * The service must be enabled in system settings; on this debug device that is
 * done over adb.
 */
class BackAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val recheck = Runnable { updateScreensaverState() }

    /**
     * Whether our own full-screen app-picker is the foreground activity. Tracked
     * from window-state-changed events (not the node tree, which can be null mid-
     * transition and would also pick up our own overlay/menu windows).
     */
    @Volatile
    private var pickerForeground = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        updateScreensaverState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // A new window/activity took focus. Our picker is the only full-screen
            // activity we care about; our overlay/menu windows share our package
            // name but have different class names, so match on the class.
            pickerForeground = event.packageName == packageName &&
                event.className == PICKER_CLASS
        }
        // Window content can change rapidly (e.g. each photo slide); debounce so
        // we only inspect the tree a few times a second.
        handler.removeCallbacks(recheck)
        handler.postDelayed(recheck, 200)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(recheck)
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(recheck)
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Tells the overlay to hide while it shouldn't be shown, show otherwise. */
    private fun updateScreensaverState() {
        OverlayService.instance?.setBubbleHidden(shouldHideBubble())
    }

    /**
     * The chathead is hidden when our own app-picker is in front (so it doesn't
     * cover the list) or when the Skylight photo screensaver is up.
     */
    private fun shouldHideBubble(): Boolean {
        if (pickerForeground) return true
        return isSkylightScreensaver()
    }

    private fun isSkylightScreensaver(): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            if (root.packageName == SKYLIGHT_PACKAGE) {
                SCREENSAVER_VIEW_IDS.any { id -> hasNode(root, id) }
            } else {
                false
            }
        } catch (t: Throwable) {
            false
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    private fun hasNode(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId) ?: return false
        val found = nodes.isNotEmpty()
        @Suppress("DEPRECATION")
        nodes.forEach { it.recycle() }
        return found
    }

    companion object {
        private const val SKYLIGHT_PACKAGE = "com.skylight"
        private const val PICKER_CLASS = "com.skylight.chathead.AppPickerActivity"

        /** View ids that only exist while the Skylight photo screensaver is up. */
        private val SCREENSAVER_VIEW_IDS = listOf(
            "com.skylight:id/slideshow_image_image",
            "com.skylight:id/frame_calendar_screensaver_widgets"
        )

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
