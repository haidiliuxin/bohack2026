package com.neurogarden.app.passive

import android.content.Context
import android.content.Intent
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
import com.neurogarden.app.MainActivity

object PassiveOverlayAlert {
    private var currentView: View? = null
    private var breathRunnable: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun canShow(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun show(context: Context, title: String, message: String) {
        if (!canShow(context)) return
        mainHandler.post {
            val appContext = context.applicationContext
            val manager = appContext.getSystemService(WindowManager::class.java)
            if (currentView != null) dismiss(appContext)

            val container = LinearLayout(appContext).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(appContext.dp(22), appContext.dp(18), appContext.dp(22), appContext.dp(18))
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
                setPadding(0, appContext.dp(10), 0, appContext.dp(10))
            }
            val careView = TextView(appContext).apply {
                text = "先把注意力放回身体：慢慢吸气 4 秒，停一下，再呼气 6 秒。你现在不需要立刻解释任何事。"
                textSize = 15f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 0, 0, appContext.dp(12))
            }
            val buttonRow = LinearLayout(appContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            val closeButton = Button(appContext).apply {
                text = "知道了"
                setOnClickListener { dismiss(appContext) }
            }
            val breathingButton = Button(appContext).apply {
                text = "30秒呼吸"
                setOnClickListener { startBreathingGuide(careView) }
            }
            val openAppButton = Button(appContext).apply {
                text = "打开陪伴"
                setOnClickListener {
                    appContext.startActivity(
                        Intent(appContext, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                    dismiss(appContext)
                }
            }
            container.addView(titleView)
            container.addView(messageView)
            container.addView(careView)
            buttonRow.addView(closeButton)
            buttonRow.addView(breathingButton)
            buttonRow.addView(openAppButton)
            container.addView(buttonRow)

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
                mainHandler.postDelayed({ dismiss(appContext) }, 45_000L)
            }
        }
    }

    private fun startBreathingGuide(target: TextView) {
        val steps = listOf(
            "吸气：像闻到清新的空气一样，慢慢数到 4。",
            "停一下：肩膀可以放低一点，手指松开。",
            "呼气：慢慢数到 6，把刚才紧绷的感觉送出去。",
            "再来一轮：只做这一口气就好，不用急着解决全部问题。"
        )
        var index = 0
        breathRunnable?.let { mainHandler.removeCallbacks(it) }
        breathRunnable = object : Runnable {
            override fun run() {
                target.text = steps[index % steps.size]
                index += 1
                if (index <= 8) mainHandler.postDelayed(this, 4_000L)
            }
        }
        breathRunnable?.let { mainHandler.post(it) }
    }

    fun dismiss(context: Context) {
        breathRunnable?.let { mainHandler.removeCallbacks(it) }
        breathRunnable = null
        val manager = context.applicationContext.getSystemService(WindowManager::class.java)
        currentView?.let { view ->
            runCatching { manager.removeView(view) }
        }
        currentView = null
    }

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
