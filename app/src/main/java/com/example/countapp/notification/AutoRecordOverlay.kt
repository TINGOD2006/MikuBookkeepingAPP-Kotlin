package com.example.countapp.notification

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import com.example.countapp.MainActivity
import com.example.countapp.R
import com.example.countapp.data.Record
import kotlin.math.abs
import kotlin.math.roundToInt

/** 使用已授權的無障礙浮層，付款 App 保持前景；只佔小球的觸控區域。 */
internal class AutoRecordOverlay(private val service: AccessibilityService) {
    private val manager = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    private val size = (64 * density).roundToInt()
    // 拖動使用螢幕實體座標，LEFT 在 RTL 環境仍與 rawX 同向。
    @SuppressLint("RtlHardcoded")
    private val params = WindowManager.LayoutParams(
        size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        x = service.resources.displayMetrics.widthPixels - size - (16 * density).roundToInt()
        y = (200 * density).roundToInt()
    }
    private var ball: TextView? = null
    private var record: Record? = null

    /**
     * 依目前狀態顯示／收起浮球。
     *
     * @param enabled 「我的」頁的「自動記帳浮球」開關。false 時一律收起
     *   （連 View 都不建立），但記錄已經寫入，使用者仍可從通知進來確認。
     */
    fun render(pending: List<Record>, appInForeground: Boolean, enabled: Boolean) {
        record = pending.firstOrNull()
        if (record == null || appInForeground || !enabled) { hide(); return }
        val view = ball ?: createBall().also {
            // 系統撤銷服務權限時，通知與 App 內小球仍可操作。
            manager.addView(it, params)
            ball = it
        }
        view.text = service.getString(R.string.auto_record_ball_count, pending.size)
        view.contentDescription = service.getString(R.string.auto_record_ball_description, pending.size)
    }

    private fun createBall() = object : TextView(service) {
        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }.apply {
        gravity = Gravity.CENTER
        textSize = 12f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(32, 160, 160))
            setStroke((2 * density).roundToInt(), Color.WHITE)
        }
        elevation = 8 * density
        setOnClickListener {
            record?.let { selected ->
                val intent = Intent(service, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(RecordNotifier.EXTRA_PROMPT_RECORD_ID, selected.id)
                }
                service.startActivity(intent)
                hide()
            }
        }
        var downX = 0f
        var downY = 0f
        var initialX = 0
        var initialY = 0
        var dragging = false
        val slop = ViewConfiguration.get(service).scaledTouchSlop
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    initialX = params.x; initialY = params.y; dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    dragging = dragging || abs(dx) > slop || abs(dy) > slop
                    if (dragging) {
                        val metrics = service.resources.displayMetrics
                        params.x = (initialX + dx).roundToInt().coerceIn(0, (metrics.widthPixels - size).coerceAtLeast(0))
                        params.y = (initialY + dy).roundToInt().coerceIn(0, (metrics.heightPixels - size).coerceAtLeast(0))
                        manager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!dragging) view.performClick(); true }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    fun hide() {
        ball?.let { runCatching { manager.removeView(it) } }
        ball = null
    }
}
