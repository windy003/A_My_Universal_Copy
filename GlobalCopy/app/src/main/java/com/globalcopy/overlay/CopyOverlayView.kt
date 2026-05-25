package com.globalcopy.overlay

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.globalcopy.R
import com.globalcopy.service.GlobalCopyAccessibilityService.TextNodeInfo

@SuppressLint("ViewConstructor")
class CopyOverlayView(
    context: Context,
    private val textNodes: List<TextNodeInfo>,
    private val language: String
) : FrameLayout(context) {

    var onDismissListener: (() -> Unit)? = null

    private val canvasView: CanvasOverlay
    private val bottomPanel: LinearLayout
    private val editText: EditText
    private var selectedIndex = -1

    init {
        canvasView = CanvasOverlay(context)
        addView(canvasView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        bottomPanel = createBottomPanel()
        bottomPanel.visibility = View.GONE
        val panelParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        panelParams.gravity = Gravity.BOTTOM
        addView(bottomPanel, panelParams)

        editText = bottomPanel.findViewById(EDIT_TEXT_ID)

        isFocusable = true
        isFocusableInTouchMode = true

        // 监听输入法高度，键盘弹出时把底部面板上移，避免被键盘遮挡。
        // 因窗口带有 FLAG_LAYOUT_NO_LIMITS，系统的自动 resize 不生效，所以手动处理。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setOnApplyWindowInsetsListener { _, insets ->
                val imeHeight = insets.getInsets(WindowInsets.Type.ime()).bottom
                bottomPanel.translationY = -imeHeight.toFloat()
                insets
            }
        }
    }

    private fun createBottomPanel(): LinearLayout {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(245, 40, 40, 40))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            elevation = 8f
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val label = TextView(context).apply {
            text = if (language == "zh") "识别文本" else "Recognized Text"
            setTextColor(Color.argb(180, 255, 255, 255))
            textSize = 12f
        }
        headerRow.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener { hideBottomPanel() }
        }
        headerRow.addView(closeBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        panel.addView(headerRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val et = EditText(context).apply {
            id = EDIT_TEXT_ID
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(100, 255, 255, 255))
            hint = if (language == "zh") "点击上方文本区域..." else "Tap a text area above..."
            textSize = 15f
            setBackgroundColor(Color.argb(60, 255, 255, 255))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            minLines = 2
            maxLines = 6
            isFocusable = true
            isFocusableInTouchMode = true
            isCursorVisible = true
            // 点击文本框时弹出输入法进行编辑
            setOnClickListener { showKeyboard(it) }
            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus) showKeyboard(v)
            }
        }
        val etParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
        panel.addView(et, etParams)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val copyBtn = Button(context).apply {
            text = if (language == "zh") "复制" else "Copy"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(200, 33, 150, 243))
            setPadding(dp(24), dp(6), dp(24), dp(6))
            textSize = 14f
            setOnClickListener { copyText() }
        }

        val copyAllBtn = Button(context).apply {
            text = if (language == "zh") "复制全部" else "Copy All"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(200, 76, 175, 80))
            setPadding(dp(24), dp(6), dp(24), dp(6))
            textSize = 14f
            setOnClickListener { copyAllText() }
        }

        val exitBtn = Button(context).apply {
            text = if (language == "zh") "退出" else "Exit"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(200, 244, 67, 54))
            setPadding(dp(24), dp(6), dp(24), dp(6))
            textSize = 14f
            setOnClickListener { onDismissListener?.invoke() }
        }

        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(8) }

        btnRow.addView(exitBtn, btnParams)
        btnRow.addView(copyAllBtn, btnParams)
        btnRow.addView(copyBtn, btnParams)

        val rowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) }
        panel.addView(btnRow, rowParams)

        return panel
    }

    private fun showBottomPanel(text: String) {
        editText.setText(text)
        editText.setSelection(text.length)
        bottomPanel.visibility = View.VISIBLE
    }

    /** 让文本框获得焦点并弹出软键盘 */
    private fun showKeyboard(view: View) {
        view.requestFocus()
        view.post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideBottomPanel() {
        bottomPanel.visibility = View.GONE
        selectedIndex = -1
        canvasView.selectedIndex = -1
        canvasView.invalidate()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText.windowToken, 0)
    }

    private fun copyText() {
        val full = editText.text?.toString() ?: ""
        val start = editText.selectionStart
        val end = editText.selectionEnd
        // 复制文本框中选中的文本；若未选中则复制文本框全部内容
        val text = if (start in 0..full.length && end in 0..full.length && start != end) {
            full.substring(minOf(start, end), maxOf(start, end))
        } else {
            full
        }
        copyToClipboard(text)
    }

    private fun copyAllText() {
        // 复制文本框中的全部内容
        copyToClipboard(editText.text?.toString() ?: "")
    }

    private fun copyToClipboard(text: String) {
        if (text.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("GlobalCopy", text))
            Toast.makeText(context, R.string.copied_toast, Toast.LENGTH_SHORT).show()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            // 返回手势/返回键：直接退出复制模式
            onDismissListener?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val EDIT_TEXT_ID = 0x7F100001
    }

    @SuppressLint("ViewConstructor")
    inner class CanvasOverlay(context: Context) : View(context) {

        var selectedIndex = -1

        // View在屏幕上的位置，用于修正节点边界坐标
        private val viewLocationOnScreen = IntArray(2)

        private val dimPaint = Paint().apply {
            color = Color.argb(100, 0, 0, 0)
            style = Paint.Style.FILL
        }

        private val highlightPaint = Paint().apply {
            color = Color.argb(50, 33, 150, 243)
            style = Paint.Style.FILL
        }

        private val borderPaint = Paint().apply {
            color = Color.argb(160, 33, 150, 243)
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }

        private val selectedPaint = Paint().apply {
            color = Color.argb(80, 76, 175, 80)
            style = Paint.Style.FILL
        }

        private val selectedBorderPaint = Paint().apply {
            color = Color.argb(220, 76, 175, 80)
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        private val hintPaint = Paint().apply {
            color = Color.WHITE
            textSize = 30f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        private val hintBgPaint = Paint().apply {
            color = Color.argb(180, 0, 0, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val langLabelPaint = Paint().apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = 24f
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }

        /**
         * 将屏幕绝对坐标转换为View本地坐标，
         * 通过减去View自身的屏幕位置来实现。
         */
        private fun toLocalRect(node: TextNodeInfo): RectF {
            getLocationOnScreen(viewLocationOnScreen)
            val offsetX = viewLocationOnScreen[0].toFloat()
            val offsetY = viewLocationOnScreen[1].toFloat()
            return RectF(
                node.bounds.left - offsetX,
                node.bounds.top - offsetY,
                node.bounds.right - offsetX,
                node.bounds.bottom - offsetY
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // 半透明背景遮罩
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

            // 使用修正后的坐标绘制文本节点高亮
            for ((index, node) in textNodes.withIndex()) {
                val rect = toLocalRect(node)
                if (index == selectedIndex) {
                    canvas.drawRoundRect(rect, 4f, 4f, selectedPaint)
                    canvas.drawRoundRect(rect, 4f, 4f, selectedBorderPaint)
                } else {
                    canvas.drawRoundRect(rect, 4f, 4f, highlightPaint)
                    canvas.drawRoundRect(rect, 4f, 4f, borderPaint)
                }
            }

            // 顶部提示栏
            val hintText = if (language == "zh") {
                "点击蓝色区域识别文字"
            } else {
                "Tap blue areas to recognize text"
            }
            val langLabel = if (language == "zh") "中文" else "EN"
            val hintY = 80f
            val hintWidth = hintPaint.measureText(hintText)
            val hintRect = RectF(
                width / 2f - hintWidth / 2 - 32f,
                hintY - 30f,
                width / 2f + hintWidth / 2 + 32f,
                hintY + 14f
            )
            canvas.drawRoundRect(hintRect, 20f, 20f, hintBgPaint)
            canvas.drawText(hintText, width / 2f, hintY, hintPaint)

            // 左上角语言标签
            val badgeRect = RectF(dp(12).toFloat(), dp(36).toFloat(), dp(60).toFloat(), dp(58).toFloat())
            canvas.drawRoundRect(badgeRect, 10f, 10f, hintBgPaint)
            canvas.drawText(langLabel, dp(18).toFloat(), dp(52).toFloat(), langLabelPaint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                // event.rawX/rawY是屏幕坐标，直接与节点边界进行匹配
                val screenX = event.rawX.toInt()
                val screenY = event.rawY.toInt()
                val index = findNodeAtScreenPoint(screenX, screenY)
                if (index >= 0) {
                    selectedIndex = index
                    this@CopyOverlayView.selectedIndex = index
                    invalidate()
                    showBottomPanel(textNodes[index].text)
                } else {
                    if (bottomPanel.visibility == View.VISIBLE) {
                        hideBottomPanel()
                    } else {
                        onDismissListener?.invoke()
                    }
                }
                return true
            }
            return true
        }

        /**
         * 使用屏幕绝对坐标查找节点（与getBoundsInScreen匹配）。
         */
        private fun findNodeAtScreenPoint(x: Int, y: Int): Int {
            var bestIndex = -1
            var bestArea = Int.MAX_VALUE
            for ((index, node) in textNodes.withIndex()) {
                if (node.bounds.contains(x, y)) {
                    val area = node.bounds.width() * node.bounds.height()
                    if (area < bestArea) {
                        bestArea = area
                        bestIndex = index
                    }
                }
            }
            return bestIndex
        }
    }
}
