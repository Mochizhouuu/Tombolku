package com.tombolku.app

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    /**
     * Status yang ditampilkan membedakan:
     * - Enabled  : layanan tercantum di daftar ENABLED_ACCESSIBILITY_SERVICES.
     * - Connected: onServiceConnected() TombolkuService berhasil (flag statis).
     */
    private fun updateStatus() {
        val enabled = isServiceEnabled()
        val connected = TombolkuService.isConnected

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
        val component = ComponentName(this, TombolkuService::class.java).flattenToString()
        return enabled.split(":").any { it.equals(component, ignoreCase = true) }
    }
}
