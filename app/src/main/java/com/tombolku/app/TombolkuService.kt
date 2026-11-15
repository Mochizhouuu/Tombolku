package com.tombolku.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs

/**
 * Menampilkan tombol melayang (FAB) dan panel kontrol sebagai dua window
 * TYPE_ACCESSIBILITY_OVERLAY yang terpisah. Ukuran window hanya sebesar
 * kontennya sehingga sentuhan di luar tombol/panel tetap diteruskan ke
 * aplikasi di belakangnya.
 */
class TombolkuService : AccessibilityService() {

    companion object {
        private const val TAG = "Tombolku"
        private const val KEY_X = "fab_x"
        private const val KEY_Y = "fab_y"
        private const val FAB_SIZE_DP = 48

        /** True setelah onServiceConnected() selesai tanpa error. */
        @Volatile
        var isConnected: Boolean = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var audioManager: AudioManager? = null

    private var fab: View? = null
    private var fabParams: WindowManager.LayoutParams? = null

    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelOpen = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "onServiceConnected: mulai inisialisasi")
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            windowManager = wm
            audioManager = am

            addFabWindow(wm)

            isConnected = true
            Log.i(TAG, "onServiceConnected: FAB berhasil ditambahkan")
        } catch (e: Exception) {
            Log.e(TAG, "onServiceConnected gagal", e)
            isConnected = false
            cleanupWindows()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Service overlay-only: tidak memerlukan event accessibility.
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.i(TAG, "onUnbind: membersihkan window")
        isConnected = false
        cleanupWindows()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isConnected = false
        cleanupWindows()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- FAB

    private fun addFabWindow(wm: WindowManager) {
        val fabView = LayoutInflater.from(this).inflate(R.layout.overlay_fab, null)
        fab = fabView

        val prefs = getSharedPreferences("tombolku", Context.MODE_PRIVATE)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = prefs.getFloat(KEY_X, dp(16)).toInt()
        params.y = prefs.getFloat(KEY_Y, dp(16)).toInt()
        fabParams = params

        wm.addView(fabView, params)

        fabView.setOnClickListener { openPanel() }
        setupFabDrag(fabView, params)
        Log.i(TAG, "FAB window ditambahkan di x=${params.x} y=${params.y}")
    }

    private fun setupFabDrag(fabView: View, params: WindowManager.LayoutParams) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        fabView.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                    if (moved) {
                        params.x = clampInt(startX + dx.toInt(), 0, maxFabX(v))
                        params.y = clampInt(startY + dy.toInt(), 0, maxFabY(v))
                        updateFabLayout()
                    }
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

    private fun maxFabX(view: View): Int {
        val metrics = resources.displayMetrics
        return (metrics.widthPixels - view.width).coerceAtLeast(0)
    }

    private fun maxFabY(view: View): Int {
        val metrics = resources.displayMetrics
        return (metrics.heightPixels - view.height).coerceAtLeast(0)
    }

    private fun updateFabLayout() {
        val v = fab ?: return
        val p = fabParams ?: return
        try {
            windowManager?.updateViewLayout(v, p)
        } catch (e: Exception) {
            Log.e(TAG, "updateViewLayout FAB gagal", e)
        }
    }

    private fun saveFabPosition() {
        val p = fabParams ?: return
        getSharedPreferences("tombolku", Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_X, p.x.toFloat())
            .putFloat(KEY_Y, p.y.toFloat())
            .apply()
    }

    // -------------------------------------------------------------- Panel

    private fun openPanel() {
        val wm = windowManager ?: return
        val fabView = fab ?: return
        val fabP = fabParams ?: return
        if (panelOpen) return

        try {
            val panelView = panel ?: LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)
                .also { created ->
                    setupPanelActions(created)
                    panel = created
                }

            val p = panelParams ?: WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // WATCH_OUTSIDE_TOUCH: tap di luar panel mengirim ACTION_OUTSIDE
                // sehingga panel bisa ditutup tanpa window layar penuh.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).also { created ->
                created.gravity = Gravity.TOP or Gravity.START
                panelParams = created
            }

            // Posisi kasar berbasis FAB; disempurnakan setelah panel terukur.
            p.x = fabP.x
            p.y = fabP.y + (fabView.height.takeIf { it > 0 } ?: dp(FAB_SIZE_DP))

            if (panelView.parent == null) wm.addView(panelView, p)

            panelView.post {
                if (!panelOpen) return@post
                positionPanel()
            }

            fabView.visibility = View.INVISIBLE
            panelOpen = true
            Log.i(TAG, "Panel dibuka")
        } catch (e: Exception) {
            Log.e(TAG, "openPanel gagal", e)
            closePanel()
        }
    }

    private fun positionPanel() {
        val panelView = panel ?: return
        val p = panelParams ?: return
        val fabView = fab ?: return
        val fabP = fabParams ?: return
        val metrics = resources.displayMetrics
        val margin = dp(8)
        val fw = fabView.width.takeIf { it > 0 } ?: dp(FAB_SIZE_DP)
        val fh = fabView.height.takeIf { it > 0 } ?: dp(FAB_SIZE_DP)
        val pw = panelView.width
        val ph = panelView.height
        if (pw <= 0 || ph <= 0) return

        var x = fabP.x + fw / 2 - pw / 2
        var y = fabP.y + fh + margin
        if (y + ph > metrics.heightPixels - margin) {
            y = fabP.y - ph - margin
        }
        x = clampInt(x, margin, (metrics.widthPixels - pw - margin).coerceAtLeast(margin))
        y = clampInt(y, margin, (metrics.heightPixels - ph - margin).coerceAtLeast(margin))
        p.x = x
        p.y = y
        try {
            windowManager?.updateViewLayout(panelView, p)
        } catch (e: Exception) {
            Log.e(TAG, "positionPanel updateViewLayout gagal", e)
        }
    }

    private fun closePanel() {
        panelOpen = false
        try {
            val wm = windowManager
            val v = panel
            if (wm != null && v != null && v.parent != null) {
                wm.removeView(v)
            }
        } catch (e: Exception) {
            Log.e(TAG, "closePanel removeView gagal", e)
        }
        try {
            fab?.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e(TAG, "closePanel restore FAB gagal", e)
        }
        Log.i(TAG, "Panel ditutup")
    }

    private fun setupPanelActions(panelView: View) {
        panelView.findViewById<View>(R.id.btnClose)?.setOnClickListener {
            // ACTION_OUTSIDE juga menutup panel; tombol ini sebagai alternatif eksplisit.
            closePanel()
        }
        panelView.findViewById<View>(R.id.btnLock)?.setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            closePanel()
        }
        panelView.findViewById<View>(R.id.btnPower)?.setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            closePanel()
        }
        panelView.findViewById<View>(R.id.btnVolUp)?.setOnClickListener {
            audioManager?.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )
        }
        panelView.findViewById<View>(R.id.btnVolDown)?.setOnClickListener {
            audioManager?.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
        }
        panelView.findViewById<View>(R.id.btnMute)?.setOnClickListener {
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
        }
    }

    // ------------------------------------------------------------- Cleanup

    private fun cleanupWindows() {
        closePanel()
        try {
            val wm = windowManager
            val v = fab
            if (wm != null && v != null && v.parent != null) {
                wm.removeView(v)
                Log.i(TAG, "FAB window dihapus")
            }
        } catch (e: Exception) {
            Log.e(TAG, "cleanup FAB gagal", e)
        }
    }

    // ------------------------------------------------------------- Utility

    private fun clampInt(value: Int, min: Int, max: Int): Int =
        if (value < min) min else if (value > max) max else value

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
