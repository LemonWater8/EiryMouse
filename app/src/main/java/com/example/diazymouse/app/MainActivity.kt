package com.example.diazymouse.app

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.diazymouse.connection.socket.MouseConnection
import com.example.diazymouse.floating.FloatingOverlayService
import com.example.diazymouse.settings.store.FloatingSettingsStore
import com.example.diazymouse.settings.store.MarkPosition
import com.example.diazymouse.settings.store.MarkPositionStore
import com.example.diazymouse.settings.store.MarkPositionSlot
import com.example.diazymouse.ui.screen.MainScreen

class MainActivity : ComponentActivity() {

    private lateinit var mouseConnection: MouseConnection
    private lateinit var mainScreen: MainScreen
    private var returnToConfigAfterOrientationChange: Boolean = false
    private var overlayPermissionRequested = false
    private var collapseToDesktopAfterOverlayPermission = false
    private var activityInForeground = false
    private val appLaunchStartMs = System.currentTimeMillis()
    private var bluetoothConnectStartedAtMs: Long? = null
    private var adbConnectStartedAtMs: Long? = null

    private val floatingCollapseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != FloatingOverlayService.ACTION_COLLAPSE_REQUEST) return
            Log.i("FLOATING_TRACE", "collapse request received; foreground=$activityInForeground")
            if (activityInForeground) {
                // Stop the visible background renderer first (especially MP4 audio),
                // but keep its saved selection for the next onResume().
                mainScreen.onHostCollapseForFloating()

                // Do not launch HOME. Moving only this task back reveals whichever task
                // was actually underneath (HOME, YouTube, browser, etc.).
                window.decorView.post {
                    val moved = moveTaskToBack(true)
                    Log.i("FLOATING_TRACE", "moveTaskToBack result=$moved")
                }
            }
        }
    }

    fun appLaunchElapsedMs(): Long = System.currentTimeMillis() - appLaunchStartMs

    fun bluetoothConnectionElapsedMs(): Long = bluetoothConnectStartedAtMs?.let {
        System.currentTimeMillis() - it
    } ?: 0L

    fun adbConnectionElapsedMs(): Long = adbConnectStartedAtMs?.let {
        System.currentTimeMillis() - it
    } ?: 0L

    fun markBluetoothConnectStart() {
        if (bluetoothConnectStartedAtMs == null) {
            bluetoothConnectStartedAtMs = System.currentTimeMillis()
        }
    }

    fun markAdbConnectStart() {
        if (adbConnectStartedAtMs == null) {
            adbConnectStartedAtMs = System.currentTimeMillis()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBarsForOrientation(resources.configuration.orientation)

        // EiryMouse always starts in Portrait. Landscape is an explicit
        // Config action and is never selected automatically on launch.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Repair/migrate old Floating settings before MainScreen reads them.
        // This guarantees expanded state is not suppressed by a stale false left by
        // an older build. Future explicit OFF remains persistent.
        FloatingSettingsStore.ensureCurrentDefaults(this)
        // Floating LOCK is a per-launch safety state. Start every app launch unlocked.
        FloatingSettingsStore.setLocked(this, false)

        mouseConnection = MouseConnection()
        mainScreen = MainScreen(activity = this, mouseConnection = mouseConnection)
        setContentView(mainScreen.create())

        ContextCompat.registerReceiver(
            this,
            floatingCollapseReceiver,
            IntentFilter(FloatingOverlayService.ACTION_COLLAPSE_REQUEST),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        activityInForeground = true

        // Every real return to EiryMouse restores the selected PNG/MP4.
        // VideoView is rebuilt so MP4 playback/audio restarts reliably.
        mainScreen.onHostResume(false)
        Log.i("FLOATING_TRACE", "MainActivity onResume -> restore saved background")

        val floatingEnabled = FloatingSettingsStore.isEnabled(this)
        val overlayAllowed = Settings.canDrawOverlays(this)

        // Returning from Android's "display over other apps" settings.
        if (overlayPermissionRequested) {
            overlayPermissionRequested = false
            if (!overlayAllowed) {
                FloatingSettingsStore.setEnabled(this, false)
                collapseToDesktopAfterOverlayPermission = false
                return
            }
            if (collapseToDesktopAfterOverlayPermission) {
                collapseToDesktopAfterOverlayPermission = false
                window.decorView.post { showDesktopAtMarkOut() }
                return
            }
        }

        if (floatingEnabled && overlayAllowed) {
            Log.i("FLOATING_TRACE", "onResume -> floating enabled; force show expanded state")
            startFloatingOverlayService()
        } else if (floatingEnabled && !overlayAllowed && !overlayPermissionRequested) {
            // expanded state is part of the normal EiryMouse UI contract. If the
            // overlay permission has been lost, explicitly ask for it instead
            // of silently leaving the mark absent.
            overlayPermissionRequested = true
            Log.w("FLOATING_TRACE", "overlay permission missing -> request permission for expanded state")
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (FloatingSettingsStore.isEnabled(this) && Settings.canDrawOverlays(this)) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> notifyFloatingInteraction(FloatingOverlayService.ACTION_INTERACTION_BEGIN)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    notifyFloatingInteraction(FloatingOverlayService.ACTION_INTERACTION_END)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun notifyFloatingInteraction(actionName: String) {
        val intent = Intent(this, FloatingOverlayService::class.java).apply {
            action = actionName
        }
        // The floating service is already foreground while floating mode is enabled.
        // startService here only delivers the interaction action to that existing service.
        runCatching { startService(intent) }
    }

    override fun onPause() {
        activityInForeground = false
        super.onPause()
    }

    fun showDesktopAtMarkOut() {
        FloatingSettingsStore.setEnabled(this, true)
        if (!Settings.canDrawOverlays(this)) {
            collapseToDesktopAfterOverlayPermission = true
            overlayPermissionRequested = true
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        mainScreen.onHostCollapseForFloating()
        val intent = Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_SHOW_COLLAPSED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        window.decorView.post {
            val moved = moveTaskToBack(true)
            Log.i("FLOATING_TRACE", "Floating button -> desktop + mark_out; moveTaskToBack=$moved")
        }
    }

    fun setFloatingLocked(locked: Boolean) {
        FloatingSettingsStore.setLocked(this, locked)
        if (!Settings.canDrawOverlays(this)) return
        val intent = Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_LOCK_STATE_CHANGED
        }
        runCatching { startService(intent) }
        Log.i("FLOATING_TRACE", "floating lock changed: $locked")
    }

    fun pulseFloatingInteraction() {
        if (FloatingSettingsStore.isEnabled(this) && Settings.canDrawOverlays(this)) {
            notifyFloatingInteraction(FloatingOverlayService.ACTION_INTERACTION_PULSE)
        }
    }

    fun setFloatingUiSurfaceActive(active: Boolean) {
        if (FloatingSettingsStore.isEnabled(this) && Settings.canDrawOverlays(this)) {
            notifyFloatingInteraction(
                if (active) FloatingOverlayService.ACTION_UI_SURFACE_BEGIN
                else FloatingOverlayService.ACTION_UI_SURFACE_END
            )
        }
    }

    fun setFloatingEnabled(enabled: Boolean) {
        FloatingSettingsStore.setEnabled(this, enabled)
        if (!enabled) {
            stopFloatingOverlayService()
            return
        }

        if (Settings.canDrawOverlays(this)) {
            startFloatingOverlayService()
        } else {
            overlayPermissionRequested = true
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(permissionIntent)
        }
    }

    private fun startFloatingOverlayService() {
        val intent = Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_SHOW_EXPANDED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopFloatingOverlayService() {
        val stopIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_STOP
        }
        runCatching { startService(stopIntent) }
        stopService(Intent(this, FloatingOverlayService::class.java))
        Log.i("FLOATING_TRACE", "floating service stop requested")
    }

    fun isLandscapeMode(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun orientationModeLabel(): String =
        if (isLandscapeMode()) "LANDSCAPE" else "PORTRAIT"

    fun toggleScreenOrientation() {
        returnToConfigAfterOrientationChange = true
        requestedOrientation = if (isLandscapeMode()) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    /**
     * Complete app shutdown used by Config -> FINISH.
     * Remove the floating mark/service before removing the Activity task.
     */
    fun requestCompleteExit() {
        // FINISH removes the current overlay/service, but does not erase the user's
        // Floating preference or saved mark position. Next app launch can restore it.
        stopFloatingOverlayService()
        Log.i("FLOATING_TRACE", "complete exit -> mark removed; floating preference/position preserved")
        finishAndRemoveTask()
    }


    fun showMarkPositionEditor() {
        val original = MarkPositionStore.loadOrCenter(this, MarkPositionSlot.STORED_COLLAPSED)
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(28, 28, 28)) }
        val buttonHeight = dpForMarkEditor(72)
        val markSize = dpForMarkEditor(64)
        val labelHeight = dpForMarkEditor(28)

        val title = TextView(this).apply {
            text = "MARK POSITION\nmark_out"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        root.addView(title, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dpForMarkEditor(72)).apply { gravity = Gravity.TOP })

        val label = TextView(this).apply { text = "mark_out"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setBackgroundColor(Color.argb(150, 0, 0, 0)) }
        val mark = ImageView(this).apply { setImageResource(com.example.diazymouse.R.drawable.mark_out); scaleType = ImageView.ScaleType.FIT_CENTER }
        root.addView(label, FrameLayout.LayoutParams(markSize, labelHeight))
        root.addView(mark, FrameLayout.LayoutParams(markSize, markSize))
        var xRatio = original.xRatio
        var yRatio = original.yRatio

        fun place() {
            val maxX = (root.width - markSize).coerceAtLeast(0)
            val minY = labelHeight
            val maxY = (root.height - buttonHeight - markSize).coerceAtLeast(minY)
            mark.x = xRatio * maxX
            mark.y = minY + yRatio * (maxY - minY)
            label.x = mark.x; label.y = (mark.y - labelHeight).coerceAtLeast(0f)
        }
        var downRawX = 0f; var downRawY = 0f; var startX = 0f; var startY = 0f
        mark.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downRawX=event.rawX; downRawY=event.rawY; startX=mark.x; startY=mark.y; true }
                MotionEvent.ACTION_MOVE -> {
                    val maxX=(root.width-markSize).coerceAtLeast(0).toFloat()
                    val minY=labelHeight.toFloat(); val maxY=(root.height-buttonHeight-markSize).coerceAtLeast(labelHeight).toFloat()
                    mark.x=(startX+event.rawX-downRawX).coerceIn(0f,maxX); mark.y=(startY+event.rawY-downRawY).coerceIn(minY,maxY)
                    label.x=mark.x; label.y=(mark.y-labelHeight).coerceAtLeast(0f)
                    xRatio=if(maxX>0) mark.x/maxX else 0.5f; yRatio=if(maxY>minY) (mark.y-minY)/(maxY-minY) else 0.5f
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        val buttons=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER }
        val yes=Button(this).apply { text="YES"; setOnClickListener {
            if (MarkPositionStore.save(this@MainActivity, MarkPositionSlot.STORED_COLLAPSED, xRatio, yRatio)) {
                Log.i("FLOATING_TRACE", "mark_out position saved $xRatio,$yRatio")
                dialog.dismiss(); mainScreen.showConfigTop()
            }
        } }
        val no=Button(this).apply { text="NO"; setOnClickListener { dialog.dismiss(); mainScreen.showConfigTop() } }
        buttons.addView(yes, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT,1f)); buttons.addView(no, LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.MATCH_PARENT,1f))
        root.addView(buttons, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,buttonHeight).apply { gravity=Gravity.BOTTOM })
        dialog.setContentView(root); dialog.setOnCancelListener { mainScreen.showConfigTop() }; dialog.setOnShowListener { root.post { place() } }; dialog.show()
    }

    private fun dpForMarkEditor(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBarsForOrientation(newConfig.orientation)
        mainScreen.onConfigurationChanged()
        if (returnToConfigAfterOrientationChange) {
            returnToConfigAfterOrientationChange = false
            // Both directions land on Config Top. In particular Landscape -> Portrait
            // must never briefly return to the normal keyboard/lock surface.
            mainScreen.showConfigTop()
        }
    }

    /**
     * Landscape is the compact controller surface, so Android system bars must not
     * reserve a colored strip at the right edge. Portrait keeps the normal bars.
     */
    private fun applySystemBarsForOrientation(orientation: Int) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(floatingCollapseReceiver) }
        mainScreen.release()
        mouseConnection.stop()

        // If the Activity itself is really finishing (Back/launcher/task close),
        // do not leave the application-overlay mark behind.
        if (isFinishing) {
            // Remove the current mark/service but preserve the user's enabled setting
            // and saved position so the next launch restores the mark.
            stopFloatingOverlayService()
            Log.i("FLOATING_TRACE", "MainActivity finishing -> mark removed; preference/position preserved")
        }
        super.onDestroy()
    }
}
