package com.globalcopy.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.globalcopy.service.CopyTileService
import com.globalcopy.service.GlobalCopyAccessibilityService

class CollapsePanelActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Finish immediately so this activity is invisible
        finish()

        // Delay to let the notification panel fully collapse,
        // then trigger copy mode to collect text from the real app underneath
        Handler(Looper.getMainLooper()).postDelayed({
            val service = GlobalCopyAccessibilityService.instance
            if (service != null && !GlobalCopyAccessibilityService.isCopyModeActive) {
                service.activateCopyMode()
                CopyTileService.instance?.updateTileState()
            }
        }, 600)
    }
}
