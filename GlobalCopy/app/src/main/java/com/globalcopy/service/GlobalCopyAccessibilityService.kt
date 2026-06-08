package com.globalcopy.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.globalcopy.R
import com.globalcopy.overlay.CopyOverlayView

class GlobalCopyAccessibilityService : AccessibilityService() {

    companion object {
        var instance: GlobalCopyAccessibilityService? = null
            private set
        var isCopyModeActive = false
            private set
    }

    private var overlayView: CopyOverlayView? = null
    private var windowManager: WindowManager? = null
    private var cachedTextNodes: List<TextNodeInfo> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        dismissAll()
        instance = null
        isCopyModeActive = false
    }

    fun activateCopyMode() {
        if (isCopyModeActive) return
        isCopyModeActive = true

        cachedTextNodes = collectAppTextNodes()
        // 取不到无障碍文字时：若系统支持 OCR（API 30+），仍进入叠层让用户用 OCR 识别；
        // 否则提示未找到文本并退出。
        val ocrSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        if (cachedTextNodes.isEmpty() && !ocrSupported) {
            Toast.makeText(this, R.string.no_text_toast, Toast.LENGTH_SHORT).show()
            isCopyModeActive = false
            CopyTileService.instance?.updateTileState()
            return
        }

        // 固定使用中文界面，直接进入识别叠层
        showOverlay(cachedTextNodes, "zh")
    }

    fun deactivateCopyMode() {
        isCopyModeActive = false
        cachedTextNodes = emptyList()
        dismissAll()
    }

    private fun collectAppTextNodes(): List<TextNodeInfo> {
        val nodes = mutableListOf<TextNodeInfo>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (window in windows) {
                    // 只从应用窗口中采集，跳过系统UI
                    // （状态栏、导航栏、通知面板等）
                    if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue

                    val root = window.root ?: continue
                    traverseNode(root, nodes)
                    root.recycle()
                }
            } catch (_: Exception) {
                // 回退方案
                val rootNode = rootInActiveWindow ?: return nodes
                traverseNode(rootNode, nodes)
                rootNode.recycle()
            }
        } else {
            val rootNode = rootInActiveWindow ?: return nodes
            traverseNode(rootNode, nodes)
            rootNode.recycle()
        }

        // 获取屏幕尺寸，过滤掉明显超出屏幕范围的节点
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        return nodes.filter { node ->
            node.bounds.right > 0 &&
            node.bounds.bottom > 0 &&
            node.bounds.left < screenWidth &&
            node.bounds.top < screenHeight &&
            node.bounds.width() > 1 &&
            node.bounds.height() > 1
        }
    }

    private fun traverseNode(node: AccessibilityNodeInfo, nodes: MutableList<TextNodeInfo>) {
        // 跳过不可见的节点
        if (!node.isVisibleToUser) return

        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrBlank()) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                nodes.add(TextNodeInfo(text, rect))
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, nodes)
            child.recycle()
        }
    }

    private fun showOverlay(textNodes: List<TextNodeInfo>, language: String) {
        dismissOverlay()

        // OCR 依赖 takeScreenshot()，仅 Android 11 (API 30) 及以上可用
        val ocrSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        val overlay = CopyOverlayView(this, textNodes, language, ocrSupported)
        overlay.onDismissListener = {
            deactivateCopyMode()
            CopyTileService.instance?.updateTileState()
        }
        overlay.onOcrRequested = { runOcrFromScreenshot() }

        val params = createOverlayParams()
        windowManager?.addView(overlay, params)
        overlayView = overlay
    }

    /**
     * 截取当前屏幕并进行 OCR 文字识别，把结果加入叠层。
     * 截屏前先隐藏叠层，避免遮罩和蓝色框被一并识别。
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun runOcrFromScreenshot() {
        val overlay = overlayView ?: return
        overlay.setOcrButtonLoading(true)
        // 隐藏叠层，延迟一帧确保其内容已从屏幕移除后再截屏
        overlay.visibility = View.INVISIBLE
        overlay.postDelayed({
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            val hwBitmap = Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer, screenshot.colorSpace
                            )
                            // ML Kit 无法处理 HARDWARE 配置的 Bitmap，需拷贝为软件位图
                            val bitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            hwBitmap?.recycle()
                            screenshot.hardwareBuffer.close()

                            overlay.visibility = View.VISIBLE
                            if (bitmap == null) {
                                overlay.setOcrButtonLoading(false)
                                toastOcrFailed()
                                return
                            }
                            OcrHelper.recognize(
                                bitmap,
                                onResult = { nodes ->
                                    bitmap.recycle()
                                    overlay.setOcrButtonLoading(false)
                                    overlay.addOcrNodes(nodes)
                                },
                                onError = {
                                    bitmap.recycle()
                                    overlay.setOcrButtonLoading(false)
                                    toastOcrFailed()
                                }
                            )
                        }

                        override fun onFailure(errorCode: Int) {
                            overlay.visibility = View.VISIBLE
                            overlay.setOcrButtonLoading(false)
                            toastOcrFailed()
                        }
                    }
                )
            } catch (e: Exception) {
                // 截屏接口异常（如缺少能力）不应导致无障碍服务崩溃
                overlay.visibility = View.VISIBLE
                overlay.setOcrButtonLoading(false)
                toastOcrFailed()
            }
        }, 150)
    }

    private fun toastOcrFailed() {
        Toast.makeText(this, R.string.ocr_failed_toast, Toast.LENGTH_SHORT).show()
    }

    private fun createOverlayParams(): WindowManager.LayoutParams {
        // 是否已授予悬浮窗权限（M 以下默认拥有）
        val canOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

        // 选择窗口类型：
        // TYPE_ACCESSIBILITY_OVERLAY 无法弹出输入法（系统限制），
        // 因此在已授予悬浮窗权限时改用支持输入法的应用级悬浮窗类型，
        // 这样点击文本框才能正常弹出键盘进行编辑。
        val layoutType = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                if (canOverlay) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                }
            canOverlay -> {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            else -> {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
            }
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 键盘弹出时调整窗口布局，避免底部输入面板被键盘遮挡
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun dismissOverlay() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }

    private fun dismissAll() {
        dismissOverlay()
    }

    data class TextNodeInfo(val text: String, val bounds: Rect)
}
