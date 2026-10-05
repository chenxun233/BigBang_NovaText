package com.cashewteam.novatext.android.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.cashewteam.novatext.android.ManualOcrSourceStore
import com.cashewteam.novatext.android.TextBoomSettingsActivity
import com.cashewteam.novatext.android.data.BigBangPreferences
import com.cashewteam.novatext.android.data.BigBangSettings
import com.cashewteam.novatext.android.util.NovaTextLogger
import kotlin.math.roundToInt

class NovaTextAccessibilityService : AccessibilityService() {
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressGeneration = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        // Default off. Only tear down a window if the user has not turned the ball on.
        if (!BigBangPreferences(this).isFloatingBallEnabled()) {
            FloatingBallService.stop(this)
        }
        ExperimentalTouchController.connect(this)
        NovaTextLogger.d("accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            scheduleLongPress(event)
            return
        }
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }
        val packageName = event?.packageName?.toString()
        if (!packageName.isNullOrBlank()) {
            ExperimentalTouchController.onForegroundPackage(packageName)
            if (packageName != packageName()) {
                latestExternalPackage = packageName
                latestExternalPackageAt = SystemClock.elapsedRealtime()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Passthrough Region is in px; recompute from stored dp on fold/rotate/size change.
        ExperimentalTouchController.refreshPassthrough(this)
    }

    override fun onInterrupt() {
        cancelPendingLongPress()
        NovaTextLogger.d("accessibility service interrupted")
    }

    override fun onDestroy() {
        cancelPendingLongPress()
        ExperimentalTouchController.disconnect()
        stopExperimentalTouchForeground()
        if (activeInstance === this) {
            activeInstance = null
        }
        NovaTextLogger.d("accessibility service destroyed")
        super.onDestroy()
    }

    companion object {
        private const val PACKAGE_CACHE_TTL_MS = 2_000L
        private const val NEAR_FULLSCREEN_FRACTION = 0.90f
        private const val NOTIFICATION_CHANNEL = "experimental_touch"
        private const val NOTIFICATION_ID = 2202

        @Volatile
        var activeInstance: NovaTextAccessibilityService? = null
            private set

        @Volatile
        private var latestExternalPackage: String? = null

        @Volatile
        private var latestExternalPackageAt: Long = 0L

        fun latestActivePackage(selfPackage: String): String? {
            val cached = latestExternalPackage
            if (cached.isNullOrBlank() || cached == selfPackage) return null
            val ageMs = SystemClock.elapsedRealtime() - latestExternalPackageAt
            return cached.takeIf { ageMs in 0..PACKAGE_CACHE_TTL_MS }
        }
    }

    private fun packageName(): String = applicationContext.packageName

    private fun scheduleLongPress(event: AccessibilityEvent) {
        val settings = BigBangSettings.get(this)
        if (!settings.isExperimentalTouchSelected || !settings.isExperimentalTouchEnabled) return
        val bounds = Rect()
        val source = event.source
        val text = if (source == null) {
            null
        } else {
            try {
                source.getBoundsInScreen(bounds)
                usableText(source)
            } finally {
                source.recycle()
            }
        }
        val target = LongPressTarget(
            text = text,
            bounds = Rect(bounds),
            packageName = event.packageName?.toString(),
        )
        val generation = ++longPressGeneration
        val delayMs = BigBangSettings.get(this).longPressExtraDelayMs.toLong()
        longPressHandler.postDelayed({
            if (generation != longPressGeneration) return@postDelayed
            dispatchLongPress(target)
        }, delayMs)
    }

    private fun cancelPendingLongPress() {
        longPressGeneration++
        longPressHandler.removeCallbacksAndMessages(null)
    }

    private fun dispatchLongPress(target: LongPressTarget) {
        val bounds = target.bounds
        if (!hasNormalRect(bounds)) {
            NovaTextLogger.d("long-press ignored: no normal rect")
            return
        }
        val text = target.text
        if (!text.isNullOrEmpty()) {
            BoomActivityLauncher.openText(
                context = this,
                text = text,
                touchX = bounds.centerX(),
                touchY = bounds.centerY(),
                animateLaunch = true,
            )
            return
        }
        launchCropOcr(bounds, target.packageName)
    }

    private fun hasNormalRect(bounds: Rect): Boolean {
        if (bounds.isEmpty || bounds.width() <= 0 || bounds.height() <= 0) return false
        val screen = screenSize()
        if (screen.x <= 0 || screen.y <= 0) return false
        val coversWidth = bounds.width() >= (screen.x * NEAR_FULLSCREEN_FRACTION)
        val coversHeight = bounds.height() >= (screen.y * NEAR_FULLSCREEN_FRACTION)
        return !(coversWidth && coversHeight)
    }

    private fun screenSize(): Point {
        val point = Point()
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            point.set(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
        }
        return point
    }

    private fun usableText(node: AccessibilityNodeInfo): String? {
        val raw = node.text?.toString() ?: node.contentDescription?.toString() ?: return null
        val normalized = raw.replace('\u3000', ' ').replace('\t', ' ').replace('\r', ' ').trim()
        return normalized.takeIf { it.isNotEmpty() }
    }

    private fun launchCropOcr(bounds: Rect, packageName: String?) {
        val started = AccessibilityScreenshotCapture.captureToCache(
            context = this,
            onFinished = { bitmap ->
                if (bitmap == null) {
                    NovaTextLogger.d("long-press crop skipped: screenshot missing")
                    return@captureToCache
                }
                val screen = screenSize()
                val scaleX = if (screen.x > 0) bitmap.width / screen.x.toFloat() else 1f
                val scaleY = if (screen.y > 0) bitmap.height / screen.y.toFloat() else 1f
                val selection = Rect(
                    (bounds.left * scaleX).roundToInt().coerceIn(0, (bitmap.width - 1).coerceAtLeast(0)),
                    (bounds.top * scaleY).roundToInt().coerceIn(0, (bitmap.height - 1).coerceAtLeast(0)),
                    (bounds.right * scaleX).roundToInt().coerceIn(1, bitmap.width),
                    (bounds.bottom * scaleY).roundToInt().coerceIn(1, bitmap.height),
                )
                if (selection.width() <= 1 || selection.height() <= 1) {
                    bitmap.recycle()
                    NovaTextLogger.d("long-press crop skipped: mapped rect empty")
                    return@captureToCache
                }
                val mode = BigBangSettings.get(this).ocrRecognizerMode
                val token = ManualOcrSourceStore.newActiveToken()
                ManualOcrSourceStore.put(
                    ManualOcrSourceStore.Source(
                        token = token,
                        cachedBitmap = bitmap,
                        touchX = selection.centerX(),
                        touchY = selection.centerY(),
                        callerPackage = packageName,
                        fullscreen = true,
                        offsetX = 0,
                        offsetY = 0,
                        sourceTag = "long_press_crop",
                        replayMode = ManualOcrSourceStore.REPLAY_MODE_SELECTION_RECT,
                        selectionRect = Rect(selection),
                        ocrMode = mode,
                    ),
                )
                BoomOcrLauncher.replayWithLanguage(
                    context = this,
                    sourceToken = token,
                    touchX = selection.centerX(),
                    touchY = selection.centerY(),
                    mode = mode,
                    replayMode = ManualOcrSourceStore.REPLAY_MODE_SELECTION_RECT,
                )
            },
            onCaptured = { },
        )
        if (!started) {
            NovaTextLogger.d("long-press crop skipped: capture not started")
        }
    }

    private data class LongPressTarget(
        val text: String?,
        val bounds: Rect,
        val packageName: String?,
    )

    fun startExperimentalTouchForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL, "实验性触控监听", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Nova Text 正在监听触控")
            .setContentText("点按可打开实验性触控设置并停止监听")
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    TextBoomSettingsActivity.createExperimentalTouchIntent(this),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun stopExperimentalTouchForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun isInputMethodVisible(): Boolean {
        val activeWindows = windows
        val visible = activeWindows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        NovaTextLogger.d("experimental_touch ime_visible=$visible windowCount=${activeWindows.size}")
        return visible
    }

}
