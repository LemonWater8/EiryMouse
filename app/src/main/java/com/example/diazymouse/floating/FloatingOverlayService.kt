package com.example.diazymouse.floating

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.example.diazymouse.R
import com.example.diazymouse.app.MainActivity
import com.example.diazymouse.settings.store.MarkPositionStore
import com.example.diazymouse.settings.store.FloatingSettingsStore
import com.example.diazymouse.settings.store.MarkPositionSlot

/**
 * Floating controller with only two real states.
 *
 * EXPANDED: EiryMouse Activity is visible + expanded state overlay image.
 * COLLAPSED: EiryMouse Activity is moved behind the current task + mark_out overlay image.
 *
 * expanded state / mark_out are tap-only during normal use.
 * Position editing is performed only from Config -> MARK POSITION.
 * The saved relative position is shared by both marks and survives app restarts.
 * Missing/corrupt position data falls back to the physical screen center.
 *
 * No screenshot, fake desktop, full-screen transparent overlay, or forced HOME launch is used.
 */
class FloatingOverlayService : Service() {

    companion object {
        const val ACTION_SHOW_EXPANDED = "com.example.diazymouse.floating.SHOW_EXPANDED"
        const val ACTION_SHOW_COLLAPSED = "com.example.diazymouse.floating.SHOW_COLLAPSED"
        const val ACTION_STOP = "com.example.diazymouse.floating.STOP"
        const val ACTION_COLLAPSE_REQUEST = "com.example.diazymouse.floating.COLLAPSE_REQUEST"
        const val ACTION_INTERACTION_BEGIN = "com.example.diazymouse.floating.INTERACTION_BEGIN"
        const val ACTION_INTERACTION_END = "com.example.diazymouse.floating.INTERACTION_END"
        const val ACTION_INTERACTION_PULSE = "com.example.diazymouse.floating.INTERACTION_PULSE"
        const val ACTION_LOCK_STATE_CHANGED = "com.example.diazymouse.floating.LOCK_STATE_CHANGED"
        const val ACTION_UI_SURFACE_BEGIN = "com.example.diazymouse.floating.UI_SURFACE_BEGIN"
        const val ACTION_UI_SURFACE_END = "com.example.diazymouse.floating.UI_SURFACE_END"

        private const val CHANNEL_ID = "eirymouse_floating"
        private const val NOTIFICATION_ID = 3108
        private const val TAG = "FLOATING_TRACE"

    }

    private lateinit var windowManager: WindowManager
    private var currentView: View? = null
    private var currentParams: WindowManager.LayoutParams? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoCollapseArmed = false
    private var interactionActive = false
    private var uiSurfaceActive = false
    private var isExpandedState = false
    private val autoCollapseRunnable = Runnable {
        if (!autoCollapseArmed || interactionActive || uiSurfaceActive || !isExpandedState || FloatingSettingsStore.isLocked(this)) return@Runnable
        Log.i(TAG, "stowage inactivity -> auto collapse")
        showCollapsedMark()
        sendBroadcast(Intent(ACTION_COLLAPSE_REQUEST).apply {
            setPackage(packageName)
        })
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("EiryMouse Floating")
                .setContentText("Floating control is active")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        )
        Log.i(TAG, "service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "STOP")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_COLLAPSED -> {
                cancelAutoCollapse()
                showCollapsedMark()
            }
            ACTION_INTERACTION_BEGIN -> beginInteraction()
            ACTION_INTERACTION_END -> endInteraction()
            ACTION_INTERACTION_PULSE -> pulseInteraction()
            ACTION_LOCK_STATE_CHANGED -> {
                if (FloatingSettingsStore.isLocked(this)) cancelAutoCollapse() else scheduleAutoCollapseIfNeeded()
            }
            ACTION_UI_SURFACE_BEGIN -> {
                uiSurfaceActive = true
                cancelAutoCollapse()
                Log.d(TAG, "Config/Help visible -> stowage timer paused")
            }
            ACTION_UI_SURFACE_END -> {
                uiSurfaceActive = false
                Log.d(TAG, "Config/Help closed -> stowage timer resumed")
                scheduleAutoCollapseIfNeeded()
            }
            ACTION_SHOW_EXPANDED, null -> showExpandedMark()
            else -> showExpandedMark()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "task removed -> remove mark and stop floating service")
        cancelAutoCollapse()
        removeCurrentView()
        mainHandler.removeCallbacksAndMessages(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        cancelAutoCollapse()
        removeCurrentView()
        mainHandler.removeCallbacksAndMessages(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        Log.i(TAG, "service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Expanded state: the app is visible, so no overlay mark is shown. */
    private fun showExpandedMark() {
        if (!ensureOverlayPermission()) return
        isExpandedState = true
        removeCurrentView()
        Log.i(TAG, "EXPANDED: no overlay mark")
        scheduleAutoCollapseIfNeeded()
    }

    /** mark_out: DiazyMouse is collapsed; another app behind it remains untouched. */
    private fun showCollapsedMark() {
        if (!ensureOverlayPermission()) return
        isExpandedState = false
        cancelAutoCollapse()
        interactionActive = false
        uiSurfaceActive = false
        removeCurrentView()

        val mark = makeMark(R.drawable.mark_out).apply {
            contentDescription = "DiazyMouse outside"
            setOnClickListener {
                Log.i(TAG, "mark_out tapped -> restore MainActivity; arm stowage inactivity")
                autoCollapseArmed = true
                interactionActive = false
                showExpandedMark()
                restoreMainActivity()
            }
        }

        addFixedMark(mark)
        Log.i(TAG, "COLLAPSED mark_out shown")
    }

    private fun restoreMainActivity() {
        val restoreIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        runCatching { startActivity(restoreIntent) }
            .onSuccess { Log.i(TAG, "restore MainActivity requested") }
            .onFailure { Log.e(TAG, "restore MainActivity failed", it) }
    }

    private fun makeMark(drawableRes: Int): ImageView = ImageView(this).apply {
        setImageResource(drawableRes)
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = true
        setPadding(0, 0, 0, 0)
        isClickable = true
    }

    private fun addFixedMark(mark: View) {
        val size = dp(64)
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        applySavedPosition(params, size, MarkPositionSlot.STORED_COLLAPSED)

        currentView = mark
        currentParams = params
        windowManager.addView(mark, params)
    }

    // Normal marks are fixed. Position editing is Config-only.

    private fun beginInteraction() {
        if (!autoCollapseArmed) return
        interactionActive = true
        cancelAutoCollapse()
        Log.d(TAG, "interaction begin -> inactivity timer paused")
    }

    private fun endInteraction() {
        if (!autoCollapseArmed) return
        interactionActive = false
        Log.d(TAG, "interaction end -> restart stowage inactivity timer")
        scheduleAutoCollapseIfNeeded()
    }

    private fun pulseInteraction() {
        if (!autoCollapseArmed || !isExpandedState) return
        // IME events happen outside MainActivity dispatchTouchEvent. Treat each
        // edit/composition/key event as fresh user activity without holding the
        // interaction state open indefinitely.
        if (!interactionActive) scheduleAutoCollapseIfNeeded()
        Log.v(TAG, "interaction pulse -> stowage timer refreshed")
    }

    private fun scheduleAutoCollapseIfNeeded() {
        cancelAutoCollapse()
        if (!autoCollapseArmed || interactionActive || uiSurfaceActive || !isExpandedState || FloatingSettingsStore.isLocked(this)) return
        val delayMs = FloatingSettingsStore.getStowageTimeSeconds(this) * 1_000L
        mainHandler.postDelayed(autoCollapseRunnable, delayMs)
        Log.d(TAG, "stowage inactivity timer scheduled: ${delayMs}ms")
    }

    private fun cancelAutoCollapse() {
        mainHandler.removeCallbacks(autoCollapseRunnable)
    }

    private fun applySavedPosition(
        params: WindowManager.LayoutParams,
        markSize: Int,
        slot: MarkPositionSlot
    ) {
        val saved = MarkPositionStore.load(this, slot)
        if (saved == null) {
            centerToScreen(params, markSize)
            Log.i(TAG, "mark position $slot missing/unreadable -> center")
            return
        }

        val (maxX, maxY) = markBounds(markSize)
        params.x = (saved.xRatio * maxX).toInt().coerceIn(0, maxX)
        params.y = (saved.yRatio * maxY).toInt().coerceIn(0, maxY)
        Log.i(TAG, "mark position $slot restored xRatio=${saved.xRatio} yRatio=${saved.yRatio}")
    }

    private fun centerToScreen(params: WindowManager.LayoutParams, markSize: Int) {
        val (maxX, maxY) = markBounds(markSize)
        params.x = maxX / 2
        params.y = maxY / 2
    }

    private fun markBounds(markSize: Int): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        return Pair(
            (metrics.widthPixels - markSize).coerceAtLeast(0),
            (metrics.heightPixels - markSize).coerceAtLeast(0)
        )
    }

    private fun removeCurrentView() {
        currentView?.let { view ->
            runCatching { windowManager.removeViewImmediate(view) }
        }
        currentView = null
        currentParams = null
    }

    private fun ensureOverlayPermission(): Boolean {
        if (Settings.canDrawOverlays(this)) return true
        Log.w(TAG, "overlay permission missing -> stop")
        stopSelf()
        return false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "DiazyMouse Floating",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps the DiazyMouse floating control available"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
