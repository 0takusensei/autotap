package com.fajil.autotap

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnStartStop).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant overlay permission first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Please enable the Accessibility Service first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (OverlayService.isRunning) {
                stopService(Intent(this, OverlayService::class.java))
                Toast.makeText(this, "Auto-Tap overlay stopped", Toast.LENGTH_SHORT).show()
            } else {
                startService(Intent(this, OverlayService::class.java))
                Toast.makeText(
                    this,
                    "Overlay started. Tap the floating button to toggle Auto-Tap mode.",
                    Toast.LENGTH_LONG
                ).show()
            }
            updateStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val accessibilityOn = isAccessibilityServiceEnabled()
        val overlayOn = Settings.canDrawOverlays(this)
        val serviceOn = OverlayService.isRunning
        statusText.text = buildString {
            append("Accessibility Service: ")
            append(if (accessibilityOn) "ENABLED" else "DISABLED")
            append("\nOverlay Permission: ")
            append(if (overlayOn) "GRANTED" else "NOT GRANTED")
            append("\nAuto-Tap Overlay: ")
            append(if (serviceOn) "RUNNING" else "STOPPED")
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "$packageName/${TapAccessibilityService::class.java.canonicalName}"
        val enabledServicesSetting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            if (colonSplitter.next().equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
