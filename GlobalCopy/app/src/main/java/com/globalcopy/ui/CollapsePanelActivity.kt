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
        // 立即结束，使此Activity不可见
        finish()

        // 延迟等待通知面板完全收起，
        // 然后触发复制模式，从下方真实应用中采集文本
        Handler(Looper.getMainLooper()).postDelayed({
            val service = GlobalCopyAccessibilityService.instance
            if (service != null && !GlobalCopyAccessibilityService.isCopyModeActive) {
                service.activateCopyMode()
                CopyTileService.instance?.updateTileState()
            }
        }, 600)
    }
}
