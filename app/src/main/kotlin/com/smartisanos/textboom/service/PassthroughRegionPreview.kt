package com.cashewteam.novatext.android.service

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * Live translucent-blue edge preview for the shared passthrough inset (dp → px).
 * Uses TYPE_APPLICATION_OVERLAY when allowed; otherwise attaches to the hosting Activity window.
 * FLAG_NOT_TOUCHABLE so the slider / gestures keep working underneath.
 */
object PassthroughRegionPreview {
    private const val PREVIEW_COLOR = 0x665D91FF

    private var host: PreviewHost? = null

    fun show(context: Context, insetDp: Int) {
        val appContext = context.applicationContext
        val existing = host
        if (existing != null) {
            existing.update(insetDp)
            return
        }
        val created = createHost(context, appContext) ?: return
        host = created
        created.update(insetDp)
        created.attach()
    }

    fun update(insetDp: Int) {
        host?.update(insetDp)
    }

    fun hide() {
        host?.detach()
        host = null
    }

    private fun createHost(context: Context, appContext: Context): PreviewHost? {
        if (Settings.canDrawOverlays(appContext)) {
            val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = EdgeMaskView(appContext)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                title = "NovaTextPassthroughPreview"
            }
            return OverlayHost(windowManager, view, params)
        }
        val activity = context.findActivity() ?: return null
        val decor = activity.window?.decorView as? android.view.ViewGroup ?: return null
        val view = EdgeMaskView(activity)
        return DecorHost(decor, view)
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private interface PreviewHost {
        fun attach()
        fun update(insetDp: Int)
        fun detach()
    }

    private class OverlayHost(
        private val windowManager: WindowManager,
        private val view: EdgeMaskView,
        private val params: WindowManager.LayoutParams,
    ) : PreviewHost {
        private var attached = false

        override fun attach() {
            if (attached) return
            windowManager.addView(view, params)
            attached = true
        }

        override fun update(insetDp: Int) {
            view.setInsetDp(insetDp)
            if (attached) {
                windowManager.updateViewLayout(view, params)
            }
        }

        override fun detach() {
            if (!attached) return
            runCatching { windowManager.removeViewImmediate(view) }
            attached = false
        }
    }

    private class DecorHost(
        private val decor: android.view.ViewGroup,
        private val view: EdgeMaskView,
    ) : PreviewHost {
        private var attached = false

        override fun attach() {
            if (attached) return
            decor.addView(
                view,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            attached = true
        }

        override fun update(insetDp: Int) {
            view.setInsetDp(insetDp)
        }

        override fun detach() {
            if (!attached) return
            decor.removeView(view)
            attached = false
        }
    }

    private class EdgeMaskView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PREVIEW_COLOR
            style = Paint.Style.FILL
        }
        private var insetDp: Int = 0
        private val scratch = Rect()

        init {
            // Decor-window fallback has no FLAG_NOT_TOUCHABLE; never consume touches.
            isClickable = false
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        fun setInsetDp(value: Int) {
            if (insetDp == value) {
                invalidate()
                return
            }
            insetDp = value.coerceAtLeast(0)
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean = false

        override fun dispatchTouchEvent(event: MotionEvent): Boolean = false

        override fun onDraw(canvas: Canvas) {
            val insetPx = (insetDp * resources.displayMetrics.density).roundToInt()
                .coerceAtLeast(0)
                .coerceAtMost(width / 4)
                .coerceAtMost(height / 4)
            if (insetPx <= 0 || width <= 0 || height <= 0) return
            scratch.set(0, 0, width, insetPx)
            canvas.drawRect(scratch, paint)
            scratch.set(0, height - insetPx, width, height)
            canvas.drawRect(scratch, paint)
            scratch.set(0, 0, insetPx, height)
            canvas.drawRect(scratch, paint)
            scratch.set(width - insetPx, 0, width, height)
            canvas.drawRect(scratch, paint)
        }
    }
}
