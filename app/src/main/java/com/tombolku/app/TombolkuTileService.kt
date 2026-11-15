package com.tombolku.app

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast

/**
 * Ubin Quick Settings "Tombolku".
 *
 * Tap = buka panel kontrol; tap lagi = tutup panel. Panel berada di
 * TombolkuService sehingga semua aksi global (lock, power) tetap bisa
 * dijalankan dari layanan aksesibilitas.
 */
class TombolkuTileService : TileService() {

    companion object {
        private const val TAG = "Tombolku"

        @Volatile
        private var instance: TombolkuTileService? = null

        /** Dipanggil dari TombolkuService saat panel buka/tutup. */
        fun updateTile() {
            val tile = instance?.qsTile ?: return
            try {
                tile.state = if (TombolkuService.isPanelOpen) {
                    Tile.STATE_ACTIVE
                } else {
                    Tile.STATE_INACTIVE
                }
                tile.updateTile()
            } catch (e: Exception) {
                Log.e(TAG, "updateTile gagal", e)
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        instance = this
        updateTile()
    }

    override fun onStopListening() {
        instance = null
        super.onStopListening()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Log.i(TAG, "Tile ditambahkan")
    }

    override fun onClick() {
        super.onClick()
        Log.i(TAG, "Tile diklik, panelOpen=${TombolkuService.isPanelOpen}")
        unlockAndRun {
            if (TombolkuService.isPanelOpen) {
                TombolkuService.requestHidePanel()
            } else {
                val shown = TombolkuService.requestShowPanel()
                if (!shown) {
                    Toast.makeText(
                        this,
                        R.string.tile_service_not_ready,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            updateTile()
        }
    }
}
