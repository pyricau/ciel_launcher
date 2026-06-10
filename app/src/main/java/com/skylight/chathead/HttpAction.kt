package com.skylight.chathead

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fires a fire-and-forget HTTP POST to a URL (e.g. a Home Assistant webhook) on
 * a background thread, then shows a toast with the result. No app is launched.
 */
object HttpAction {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun fire(context: Context, url: String, label: String) {
        val appContext = context.applicationContext
        Thread {
            val message = try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val code = conn.responseCode
                conn.disconnect()
                if (code == 200) "✅" else "❌ $code"
            } catch (t: Throwable) {
                "❌ ${t.message ?: t.javaClass.simpleName}"
            }
            mainHandler.post {
                Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}
