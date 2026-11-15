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
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Layanan aksesibilitas Tombolku.
 *
 * Tidak menampilkan floating button permanen. Panel kontrol hanya muncul
 * saat diminta dari ubin Quick Settings (TombolkuTileService) dan otomatis
 * ditutup saat aksi dilakukan, tombol close ditekan, atau pengguna mengetuk
 * di luar panel (FLAG_WATCH_OUTSIDE_TOUCH).
 */
class TombolkuService : AccessibilityService() {

    companion object {
        private const val TAG = "Tombolku"

        /** True setelah onServiceConnected() selesai tanpa error. */
        @Volatile
        var isConnected: Boolean = false
            private set

        /** True selagi panel sedang ditampilkan. */
        @Volatile
        var isPanelOpen: Boolean = false
            private set

        @Volatile
        private var instance: TombolkuService? = null

        /** Dipanggil dari TileService. Return false jika service belum connected. */
        fun requestShowPanel(): Boolean {
            val svc = instance ?: return false
            return svc.tryShowPanel()
        }

        /** Dipanggil dari TileService untuk menutup panel. */
        fun requestHidePanel() {
            instance?.tryHidePanel()
        }
    }

    private var windowManager: WindowManager? = null
    private var audioManager: AudioManager? = null

    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "onServiceConnected: mulai inisialisasi")
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

            instance = this
            isConnected = true
            Log.i(TAG, "onServiceConnected: siap")
        } catch (e: Exception) {
            Log.e(TAG, "onServiceConnected gagal", e)
            isConnected = false
            instance = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Layanan hanya untuk global actions & overlay panel: tidak butuh event.
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.i(TAG, "onUnbind: membersihkan window")
        isConnected = false
        if (instance == this) instance = null
        tryHidePanel()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isConnected = false
        if (instance == this) instance = null
        tryHidePanel()
        super.onDestroy()
    }

    // -------------------------------------------------------------- Panel

    private fun tryShowPanel(): Boolean {
        if (isPanelOpen) return true
        val wm = windowManager ?: return false

        try {
            val panelView = panel ?: LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)
                .also { created ->
                    setupPanelActions(created)
                    setupOutsideTap(created)
                    panel = created
                }

            val p = panelParams ?: WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).also { created ->
                created.gravity = Gravity.CENTER
                panelParams = created
            }

            if (panelView.parent == null) {
                wm.addView(panelView, p)
                Log.i(TAG, "Panel window ditambahkan")
            }
            isPanelOpen = true
            TombolkuTileService.updateTile()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "tryShowPanel gagal", e)
            tryHidePanel()
            return false
        }
    }

    private fun tryHidePanel() {
        isPanelOpen = false
        try {
            val wm = windowManager
            val v = panel
            if (wm != null && v != null && v.parent != null) {
                wm.removeView(v)
                Log.i(TAG, "Panel window dihapus")
            }
        } catch (e: Exception) {
            Log.e(TAG, "tryHidePanel gagal", e)
        }
        TombolkuTileService.updateTile()
    }

    /** Ketuk di luar panel (ACTION_OUTSIDE) menutup panel. */
    private fun setupOutsideTap(panelView: View) {
        panelView.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                Log.i(TAG, "Tap di luar panel: menutup")
                tryHidePanel()
                true
            } else {
                false
            }
        }
    }

    private fun setupPanelActions(panelView: View) {
        panelView.findViewById<View>(R.id.btnClose)?.setOnClickListener {
            tryHidePanel()
        }
        panelView.findViewById<View>(R.id.btnLock)?.setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            tryHidePanel()
        }
        panelView.findViewById<View>(R.id.btnPower)?.setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            tryHidePanel()
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
}
