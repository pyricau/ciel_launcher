package com.skylight.chathead

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Foreground service that draws a draggable "chathead" face on top of every
 * other app. Tapping the face opens a radial menu (Back, Home, and the apps the
 * user chose) floating around it; tapping an icon runs it, and tapping the face
 * again or outside hides the menu. Long-pressing the face opens the app picker.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager

    private lateinit var bubbleView: ImageView
    private lateinit var bubbleParams: WindowManager.LayoutParams

    /** Full-screen window holding the radial app icons; null when the menu is closed. */
    private var menuView: View? = null

    private val overlayType: Int
        @Suppress("DEPRECATION")
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startAsForeground()
        addBubble()
    }

    /**
     * Hides the chathead (and dismisses any open menu) while [hidden] is true —
     * used while the Skylight photo screensaver is up or our own app-picker is
     * in front. Shows it again otherwise. Called by [BackAccessibilityService].
     */
    fun setBubbleHidden(hidden: Boolean) {
        mainHandler.post {
            if (!this::bubbleView.isInitialized) return@post
            if (hidden) {
                closeMenu()
                if (bubbleView.visibility != View.GONE) bubbleView.visibility = View.GONE
            } else if (bubbleView.visibility != View.VISIBLE) {
                bubbleView.visibility = View.VISIBLE
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Restart if the system kills us so the chathead comes back.
        return START_STICKY
    }

    // region Bubble (the floating face)

    private fun addBubble() {
        bubbleView = ImageView(this).apply {
            setImageResource(R.drawable.ic_weird_face)
        }

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        var initialX = 0
        var initialY = 0
        var downRawX = 0f
        var downRawY = 0f
        var dragged = false
        var longPressed = false

        // Fires if the finger stays still long enough: open the app picker.
        val longPressRunnable = Runnable {
            if (!dragged) {
                longPressed = true
                closeMenu()
                openAppPicker()
            }
        }

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragged = false
                    longPressed = false
                    mainHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) > TOUCH_SLOP || abs(dy) > TOUCH_SLOP) {
                        dragged = true
                        mainHandler.removeCallbacks(longPressRunnable)
                    }
                    bubbleParams.x = initialX + dx.toInt()
                    bubbleParams.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(bubbleView, bubbleParams)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    if (!dragged && !longPressed) toggleMenu()
                    true
                }

                else -> false
            }
        }

        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun openAppPicker() {
        startActivity(
            Intent(this, AppPickerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // endregion

    // region Radial app menu

    private fun toggleMenu() {
        if (menuView != null) closeMenu() else openMenu()
    }

    private fun closeMenu() {
        menuView?.let { windowManager.removeView(it) }
        menuView = null
    }

    private fun openMenu() {
        val items = buildMenuItems()
        if (items.isEmpty()) {
            Toast.makeText(this, "Nothing to show", Toast.LENGTH_SHORT).show()
            return
        }

        val metrics = resources.displayMetrics
        val container = FrameLayout(this).apply {
            // Tapping empty space dismisses the menu.
            setOnClickListener { closeMenu() }
        }

        val bubbleSize = if (bubbleView.width > 0) bubbleView.width else dp(84)
        val centerX = bubbleParams.x + bubbleSize / 2
        val centerY = bubbleParams.y + bubbleSize / 2

        // Adapt icon size and ring radius to how many items there are. Icons get
        // smaller as the count grows but never below the 48dp tap-target minimum;
        // the radius grows just enough to keep them from overlapping.
        val count = items.size
        val iconSize = dp(
            when {
                count <= 4 -> 68
                count <= 6 -> 60
                count <= 8 -> 54
                else -> 48
            }
        )
        val pad = (iconSize * 0.16f).toInt()
        val minSpacing = iconSize * 1.25
        val neededRadius = if (count > 1) {
            (minSpacing / (2 * sin(Math.PI / count))).toInt()
        } else {
            dp(120)
        }
        val radius = neededRadius.coerceIn(dp(120), dp(170))

        items.forEachIndexed { index, item ->
            // Spread the icons evenly in a circle, starting from the top.
            val angle = -Math.PI / 2 + 2 * Math.PI * index / items.size
            val targetX = (centerX + radius * cos(angle)).toInt() - iconSize / 2
            val targetY = (centerY + radius * sin(angle)).toInt() - iconSize / 2

            val icon = ImageView(this).apply {
                setImageDrawable(item.icon)
                setPadding(pad, pad, pad, pad)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(item.backgroundColor)
                    setStroke(dp(2), Color.parseColor("#33000000"))
                }
                elevation = dp(6).toFloat()
                setOnClickListener {
                    item.action()
                    closeMenu()
                }
            }

            val lp = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                leftMargin = targetX.coerceIn(0, metrics.widthPixels - iconSize)
                topMargin = targetY.coerceIn(0, metrics.heightPixels - iconSize)
            }
            container.addView(icon, lp)

            // Little pop-in animation so the icons feel like they fly out.
            icon.alpha = 0f
            icon.scaleX = 0.3f
            icon.scaleY = 0.3f
            icon.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay((index * 35).toLong())
                .setDuration(180)
                .setInterpolator(OvershootInterpolator())
                .start()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        menuView = container
        windowManager.addView(container, params)
    }

    /**
     * Builds the radial menu: a Back action, a "go to Skylight calendar" action,
     * then the user-chosen apps. Back and Home are kept first so they sit at
     * stable positions (top of the ring).
     */
    private fun buildMenuItems(): List<MenuItem> {
        val items = mutableListOf<MenuItem>()

        // Back: send the global Back action via the accessibility service.
        items.add(
            MenuItem(
                icon = getDrawable(R.drawable.ic_back)!!,
                backgroundColor = Color.parseColor("#FF7043"),
                action = { performBack() }
            )
        )

        // Home: return to the Skylight calendar (this device's home launcher).
        items.add(
            MenuItem(
                icon = getDrawable(R.drawable.ic_home)!!,
                backgroundColor = Color.parseColor("#2D7FF9"),
                action = { goToSkylight() }
            )
        )

        items.addAll(loadSelectedApps())
        return items
    }

    private fun performBack() {
        if (!BackAccessibilityService.performBack()) {
            // Service not enabled yet — guide the user to turn it on.
            Toast.makeText(
                this,
                "Enable \"Chathead Back\" in Accessibility settings to use Back",
                Toast.LENGTH_LONG
            ).show()
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    private fun goToSkylight() {
        val skylight = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(
                "com.skylight",
                "odesk.johnlife.skylight.activity.MainActivity"
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        try {
            startActivity(skylight)
        } catch (t: Throwable) {
            // Fall back to whatever the default home launcher is.
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /**
     * Loads the apps the user chose in [AppPickerActivity]. Saved entries whose
     * app is no longer installed are skipped.
     */
    private fun loadSelectedApps(): List<MenuItem> {
        val pm = packageManager
        return AppPrefs.getSelected(this).mapNotNull { flat ->
            val component = ComponentName.unflattenFromString(flat) ?: return@mapNotNull null
            runCatching {
                val info = pm.getActivityInfo(component, 0)
                val label = info.loadLabel(pm).toString()
                val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    this.component = component
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                MenuItem(
                    icon = info.loadIcon(pm),
                    backgroundColor = Color.WHITE,
                    action = {
                        try {
                            startActivity(launchIntent)
                        } catch (t: Throwable) {
                            Toast.makeText(this, "Couldn't open $label", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }.getOrNull()
        }
    }

    // endregion

    private fun startAsForeground() {
        val channelId = "chathead_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chathead Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Chathead running")
            .setContentText("Tap the floating face")
            .setSmallIcon(R.drawable.ic_bubble)
            .build()
        startForeground(1, notification)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        closeMenu()
        if (this::bubbleView.isInitialized && bubbleView.isAttachedToWindow) {
            windowManager.removeView(bubbleView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** One entry in the radial menu: an icon, its circle color, and what it does. */
    private class MenuItem(
        val icon: Drawable,
        val backgroundColor: Int,
        val action: () -> Unit
    )

    companion object {
        private const val TOUCH_SLOP = 10f
        private val LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout().toLong()

        /** Set while the service is running so the accessibility service can reach it. */
        @Volatile
        var instance: OverlayService? = null
            private set
    }
}
