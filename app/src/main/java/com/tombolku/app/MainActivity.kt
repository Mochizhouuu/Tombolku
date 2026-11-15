package com.tombolku.app

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnables = mutableListOf<Runnable>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnAddTile).setOnClickListener { requestAddTile() }
        findViewById<Button>(R.id.btnTestPanel).setOnClickListener { testPanel() }
    }

    override fun onResume() {
        super.onResume()
        scheduleRefreshes()
    }

    override fun onPause() {
        super.onPause()
        refreshRunnables.forEach { handler.removeCallbacks(it) }
        refreshRunnables.clear()
    }

    /**
     * Status diperbarui beberapa kali setelah resume agar perubahan dari
     * Pengaturan Aksesibilitas dan reconnect service tertangkap otomatis.
     */
    private fun scheduleRefreshes() {
        refreshRunnables.forEach { handler.removeCallbacks(it) }
        refreshRunnables.clear()
        for (delayMs in longArrayOf(500, 1500, 3000, 5000)) {
            val r = Runnable { updateStatus() }
            refreshRunnables.add(r)
            handler.postDelayed(r, delayMs)
        }
        updateStatus()
    }

    /** Tes langsung: buka panel lewat service, tanpa lewat ubin QS. */
    private fun testPanel() {
        val shown = TombolkuService.requestShowPanel()
        if (!shown) {
            val msg = if (isServiceEnabled()) {
                R.string.status_enabled_not_connected
            } else {
                R.string.tile_service_not_ready
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Minta sistem menampilkan dialog "Tambahkan ubin Tombolku?" (API 33+).
     * Sebelum API 33: pengguna menambahkan ubin manual lewat editor panel QS.
     */
    private fun requestAddTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val sbm = getSystemService(StatusBarManager::class.java)
                sbm.requestAddTileService(
                    ComponentName(this, TombolkuTileService::class.java),
                    getString(R.string.tile_label),
                    Icon.createWithResource(this, R.drawable.ic_power),
                    ContextCompat.getMainExecutor(this)
                ) { result ->
                    Log.d("Tombolku", "requestAddTileService result=$result")
                }
            } catch (e: Exception) {
                Log.e("Tombolku", "requestAddTileService gagal", e)
                Toast.makeText(this, R.string.add_tile_manual, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, R.string.add_tile_manual, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Status yang ditampilkan membedakan:
     * - Enabled  : layanan tercantum di daftar ENABLED_ACCESSIBILITY_SERVICES.
     * - Connected: onServiceConnected() TombolkuService berhasil (flag statis).
     */
    private fun updateStatus() {
        val enabled = isServiceEnabled()
        val connected = TombolkuService.isConnected

        val raw = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: "(kosong)"
        findViewById<TextView>(R.id.diagText).text = getString(R.string.diag_line, raw)

        val dot = findViewById<View>(R.id.statusDot)
        val text = findViewById<TextView>(R.id.statusText)

        when {
            enabled && connected -> {
                dot.background?.setTint(
                    ContextCompat.getColor(this, R.color.status_on)
                )
                text.setText(R.string.status_connected)
            }
            enabled -> {
                dot.background?.setTint(
                    ContextCompat.getColor(this, R.color.status_error)
                )
                text.setText(R.string.status_enabled_not_connected)
            }
            else -> {
                dot.background?.setTint(
                    ContextCompat.getColor(this, R.color.status_off)
                )
                text.setText(R.string.status_off)
            }
        }
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val component = ComponentName(this, TombolkuService::class.java)
        val full = component.flattenToString()
        val shortForm = component.flattenShortToString()
        return enabled.split(":").any {
            it.equals(full, ignoreCase = true) || it.equals(shortForm, ignoreCase = true)
        }
    }
}
