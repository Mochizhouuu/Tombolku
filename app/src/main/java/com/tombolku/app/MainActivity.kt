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

    private fun updateStatus() {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val component = ComponentName(this, TombolkuService::class.java).flattenToString()
        val running = enabled.split(":").any { it.equals(component, ignoreCase = true) }

        val dot = findViewById<View>(R.id.statusDot)
        val text = findViewById<TextView>(R.id.statusText)
        dot.background?.setTint(
            ContextCompat.getColor(this, if (running) R.color.status_on else R.color.status_off)
        )
        text.setText(if (running) R.string.status_on else R.string.status_off)
    }
}
