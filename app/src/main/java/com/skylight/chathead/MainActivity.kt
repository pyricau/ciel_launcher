package com.skylight.chathead

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/**
 * Tiny launcher activity: makes sure the overlay permission is granted, then
 * starts the bubble service and gets out of the way.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!canDrawOverlays()) {
            // Send the user to the system screen to grant "Display over other apps".
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            Toast.makeText(
                this,
                "Grant \"Display over other apps\", then reopen Chathead",
                Toast.LENGTH_LONG
            ).show()
        } else {
            startOverlayService()
            Toast.makeText(this, "Chathead started", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
