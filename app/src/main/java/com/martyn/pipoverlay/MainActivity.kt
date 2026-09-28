package com.martyn.pipoverlay

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : Activity() {

    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(80, 40, 80, 40)
        }
        info = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
        }
        val grant = Button(this).apply {
            text = "Open overlay permission settings"
            setOnClickListener {
                try {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    )
                } catch (_: Exception) {
                    info.text = info.text.toString() + "\n\nSettings screen not available – use the adb command above."
                }
            }
        }
        val start = Button(this).apply {
            text = "Start / restart service"
            setOnClickListener {
                ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, OverlayService::class.java))
                refresh()
            }
        }
        layout.addView(info)
        layout.addView(grant)
        layout.addView(start)
        setContentView(layout)
        start.requestFocus()

        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val ok = Settings.canDrawOverlays(this)
        info.text = buildString {
            append("PIP Overlay\n\n")
            append("API: http://${localIp()}:${OverlayService.PORT}/\n\n")
            append("Overlay permission: ${if (ok) "GRANTED" else "NOT GRANTED"}\n")
            if (!ok) append("\nGrant via adb:\nadb shell appops set $packageName SYSTEM_ALERT_WINDOW allow\n")
        }
    }

    private fun localIp(): String = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }?.hostAddress ?: "?"
    } catch (_: Exception) { "?" }
}
