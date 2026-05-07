package com.globalcopy.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.globalcopy.R
import com.globalcopy.service.GlobalCopyAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlay: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_status)
        btnAccessibility = findViewById(R.id.btn_accessibility)
        btnOverlay = findViewById(R.id.btn_overlay)

        btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val overlayEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        if (accessibilityEnabled && overlayEnabled) {
            tvStatus.text = getString(R.string.status_enabled)
            tvStatus.setTextColor(0xFF4CAF50.toInt())
            btnAccessibility.isEnabled = false
            btnAccessibility.text = "无障碍服务 ✓"
            btnOverlay.isEnabled = false
            btnOverlay.text = "悬浮窗权限 ✓"
        } else {
            tvStatus.text = getString(R.string.status_disabled)
            tvStatus.setTextColor(0xFFF44336.toInt())
            btnAccessibility.isEnabled = !accessibilityEnabled
            btnAccessibility.text = if (accessibilityEnabled) "无障碍服务 ✓" else getString(R.string.btn_enable_accessibility)
            btnOverlay.isEnabled = !overlayEnabled
            btnOverlay.text = if (overlayEnabled) "悬浮窗权限 ✓" else getString(R.string.btn_enable_overlay)
        }
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
