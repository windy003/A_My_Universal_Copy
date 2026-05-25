package com.globalcopy.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

@SuppressLint("ViewConstructor")
class LanguagePickerView(
    context: Context,
    private val onLanguageSelected: (String) -> Unit,
    private val onCancel: () -> Unit
) : FrameLayout(context) {

    init {
        setBackgroundColor(Color.argb(150, 0, 0, 0))
        setOnClickListener { onCancel() }
        addDialogView()
        isFocusable = true
        isFocusableInTouchMode = true
    }

    private fun addDialogView() {
        val dialog = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.argb(240, 50, 50, 50))
                cornerRadius = dp(16).toFloat()
            }
            background = bg
            setPadding(dp(24), dp(20), dp(24), dp(20))
            elevation = 16f
            setOnClickListener { /* 消费点击事件 */ }
        }

        // 标题
        val title = TextView(context).apply {
            text = "选择语言 / Select Language"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        }
        dialog.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(20) })

        // 中文按钮
        dialog.addView(createLangButton("中文", "zh", Color.argb(200, 33, 150, 243)))

        // 英文按钮
        dialog.addView(createLangButton("English", "en", Color.argb(200, 76, 175, 80)).apply {
            val lp = layoutParams as LinearLayout.LayoutParams
            lp.topMargin = dp(12)
            layoutParams = lp
        })

        // 取消按钮
        val cancelBtn = TextView(context).apply {
            text = "取消 / Cancel"
            setTextColor(Color.argb(180, 255, 255, 255))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
            setOnClickListener { onCancel() }
        }
        dialog.addView(cancelBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val dialogParams = LayoutParams(dp(280), LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        }
        addView(dialog, dialogParams)
    }

    private fun createLangButton(label: String, langCode: String, bgColor: Int): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(8).toFloat()
            }
            background = bg
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onLanguageSelected(langCode) }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            // 返回手势/返回键：退出复制模式
            onCancel()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
