package com.globalcopy.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.globalcopy.service.CopyTileService
import com.globalcopy.service.GlobalCopyAccessibilityService

/**
 * 由快速设置磁贴启动的透明中转 Activity。
 *
 * 注意：复制模式未结束前不能调用 finish()。
 * 通知面板收起后，如果目标应用上方没有任何属于本应用的窗口，
 * 目标应用会重新获得窗口焦点/恢复运行，TikTok 等视频应用会因此在此时自动恢复播放。
 * 因此让本 Activity 在整个复制模式期间停留在目标应用上方，
 * 等复制模式结束时（见 deactivateCopyMode）再关闭，目标应用就不会被重新激活。
 */
class CollapsePanelActivity : Activity() {

    companion object {
        private var instance: CollapsePanelActivity? = null

        /** 复制模式结束时关闭中转 Activity，把窗口焦点交还目标应用 */
        fun finishIfAlive() {
            val activity = instance
            instance = null
            activity?.finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        // 延迟等待通知面板完全收起，
        // 然后触发复制模式，从下方真实应用中采集文本
        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed

            val service = GlobalCopyAccessibilityService.instance
            if (service == null) {
                // 无障碍服务已停止，不再需要停留，直接关闭
                finish()
                return@postDelayed
            }
            if (!GlobalCopyAccessibilityService.isCopyModeActive) {
                // 未成功进入复制模式（如没有可识别文字）时立即关闭，避免挡住下层应用
                if (!service.activateCopyMode()) {
                    finish()
                }
                CopyTileService.instance?.updateTileState()
            }
        }, 600)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        // 返回键、回到桌面等非配置变更导致的销毁：同步退出复制模式，避免叠层残留
        if (!isChangingConfigurations && GlobalCopyAccessibilityService.isCopyModeActive) {
            GlobalCopyAccessibilityService.instance?.deactivateCopyMode()
        }
    }

    @Deprecated("依赖默认返回键处理，无需手动调用")
    override fun onBackPressed() {
        GlobalCopyAccessibilityService.instance?.deactivateCopyMode()
        finish()
    }
}
