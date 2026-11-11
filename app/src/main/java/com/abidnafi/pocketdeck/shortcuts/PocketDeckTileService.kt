package com.abidnafi.pocketdeck.shortcuts

import android.annotation.SuppressLint
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.abidnafi.pocketdeck.data.ReturnNotifier

/** Quick Settings tile: one tap from the pull-down panel opens PocketDeck from anywhere. */
class PocketDeckTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = "Open"
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val open = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(ReturnNotifier.openAppIntent(this))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(ReturnNotifier.launchIntent(this))
            }
        }
        // On the lock screen, ask for unlock first instead of opening behind it.
        if (isLocked) unlockAndRun(open) else open()
    }
}
