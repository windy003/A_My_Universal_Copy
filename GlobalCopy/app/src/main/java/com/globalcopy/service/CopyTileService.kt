package com.globalcopy.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.TextUtils
import android.widget.Toast
import com.globalcopy.ui.CollapsePanelActivity

class CopyTileService : TileService() {

    companion object {
        var instance: CopyTileService? = null
            private set
    }

    override fun onStartListening() {
        super.onStartListening()
        instance = this
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        instance = null
    }

    override fun onClick() {
        super.onClick()

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show()
            return
        }

        val service = GlobalCopyAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "无障碍服务未运行", Toast.LENGTH_SHORT).show()
            return
        }

        if (GlobalCopyAccessibilityService.isCopyModeActive) {
            service.deactivateCopyMode()
            updateTileState()
        } else {
            // Use startActivityAndCollapse to collapse the panel,
            // then CollapsePanelActivity triggers copy mode.
            val intent = Intent(this, CollapsePanelActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // API 34+: requires PendingIntent
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }

            updateTileState()
        }
    }

    fun updateTileState() {
        val tile = qsTile ?: return
        if (GlobalCopyAccessibilityService.isCopyModeActive) {
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "复制模式"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = null
            }
        }
        tile.updateTile()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, GlobalCopyAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }
}
