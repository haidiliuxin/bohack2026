package com.neurogarden.app.passive

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object PassiveOverlayAlert {
    private var currentView: View? = null

    fun canShow(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun show(context: Context, title: String, message: String) {
        if (!canShow(context)) return
        Handler(Looper.getMainLooper()).post {
            val appContext = context.applicationContext
            val manager = appContext.getSystemService(WindowManager::class.java)
            currentView?.let { runCatching { manager.removeView(it) } }

            val container = LinearLayout(appContext).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(34, 30, 34, 26)
                setBackgroundColor(0xEE26212D.toInt())
            }
            val titleView = TextView(appContext).apply {
                text = title
                textSize = 18f
                setTextColor(0xFFFFFFFF.toInt())
            }
            val messageView = TextView(appContext).apply {
                text = message
                textSize = 15f
                setTextColor(0xFFE9FFF9.toInt())
                setPadding(0, 12, 0, 16)
            }
            val closeButton = Button(appContext).apply {
                text = "知道了"
                setOnClickListener { dismiss(appContext) }
            }
            container.addView(titleView)
            container.addView(messageView)
            container.addView(closeButton)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 80
            }

            runCatching {
                manager.addView(container, params)
                currentView = container
                Handler(Looper.getMainLooper()).postDelayed({ dismiss(appContext) }, 15_000L)
            }
        }
    }

    private fun dismiss(context: Context) {
        val manager = context.applicationContext.getSystemService(WindowManager::class.java)
        currentView?.let { view ->
            runCatching { manager.removeView(view) }
        }
        currentView = null
    }
}
