package com.tombolku.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs
import kotlin.math.max

class TombolkuService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var root: View
    private lateinit var fab: View
    private lateinit var panel: View

    private var panelOpen = false
    private var viewAdded = false

    private val prefs by lazy { getSharedPreferences("tombolku", Context.MODE_PRIVATE) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        root = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)
        fab = root.findViewById(R.id.fab)
        panel = root.findViewById(R.id.panel)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        windowManager.addView(root, params)
        viewAdded = true

        fab.setOnClickListener { togglePanel() }
        root.setOnClickListener { if (panelOpen) togglePanel() }
        root.findViewById<View>(R.id.btnClose).setOnClickListener { togglePanel() }
        setupPanelActions()
        setupFabDrag()
        restoreFabPosition()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (viewAdded) windowManager.removeView(root)
        super.onDestroy()
    }

    private fun setupPanelActions() {
        root.findViewById<View>(R.id.btnLock).setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            togglePanel()
        }
        root.findViewById<View>(R.id.btnPower).setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            togglePanel()
        }
        root.findViewById<View>(R.id.btnVolUp).setOnClickListener {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )
        }
        root.findViewById<View>(R.id.btnVolDown).setOnClickListener {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
        }
        root.findViewById<View>(R.id.btnMute).setOnClickListener {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
        }
    }

    private fun setupFabDrag() {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        var moved = false

        fab.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = fab.translationX
                    startY = fab.translationY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                    if (moved) moveFabTo(startX + dx, startY + dy)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) saveFabPosition() else v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun moveFabTo(x: Float, y: Float) {
        val metrics = resources.displayMetrics
        val maxX = max(0f, (metrics.widthPixels - fab.width).toFloat())
        val maxY = max(0f, (metrics.heightPixels - fab.height).toFloat())
        fab.translationX = x.coerceIn(0f, maxX)
        fab.translationY = y.coerceIn(0f, maxY)
    }

    private fun saveFabPosition() {
        prefs.edit()
            .putFloat(KEY_X, fab.translationX)
            .putFloat(KEY_Y, fab.translationY)
            .apply()
    }

    private fun restoreFabPosition() {
        val x = prefs.getFloat(KEY_X, dp(16).toFloat())
        val y = prefs.getFloat(KEY_Y, dp(16).toFloat())
        fab.post { moveFabTo(x, y) }
    }

    private fun togglePanel() {
        panelOpen = !panelOpen
        root.isClickable = panelOpen
        if (panelOpen) {
            panel.isClickable = true
            panel.visibility = View.VISIBLE
            fab.visibility = View.INVISIBLE
            positionPanel()
        } else {
            panel.isClickable = false
            panel.visibility = View.GONE
            fab.visibility = View.VISIBLE
        }
    }

    private fun positionPanel() {
        panel.post {
            val metrics = resources.displayMetrics
            val margin = dp(8).toFloat()
            val pw = panel.width.toFloat()
            val ph = panel.height.toFloat()
            var x = fab.translationX + fab.width / 2f - pw / 2f
            var y = fab.translationY + fab.height + margin
            if (y + ph > metrics.heightPixels - margin) {
                y = fab.translationY - ph - margin
            }
            x = x.coerceIn(margin, max(margin, metrics.widthPixels - pw - margin))
            y = y.coerceIn(margin, max(margin, metrics.heightPixels - ph - margin))
            panel.translationX = x
            panel.translationY = y
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val KEY_X = "fab_x"
        private const val KEY_Y = "fab_y"
    }
}
