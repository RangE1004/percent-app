package com.percent.discount

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var popupView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingWidget()
    }

    private fun showFloatingWidget() {
        // 동동 떠다니는 원형 퍼센트(%) 버튼 디자인
        val button = TextView(this).apply {
            text = "%"
            textSize = 22f
            paint.isFakeBoldText = true
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF1B263B.toInt()) // 네이비 색상
                setStroke(3, Color.WHITE)
            }
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            150, 150,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        // 손가락으로 드래그하여 이동 & 터치 시 혜택 팝업 열기
        button.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isMove = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMove = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isMove = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager.updateViewLayout(button, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isMove) {
                            toggleBenefitPopup()
                        }
                        return true
                    }
                }
                return false
            }
        })

        floatingView = button
        windowManager.addView(floatingView, params)
    }

    private fun toggleBenefitPopup() {
        if (popupView != null) {
            windowManager.removeView(popupView)
            popupView = null
            return
        }

        val pref = getSharedPreferences("PercentProfile", Context.MODE_PRIVATE)
        val cardKeys = arrayOf("none", "kb", "shinhan", "hyundai", "samsung", "lotte", "nh")
        val memKeys = arrayOf("none", "wow", "naver", "universe", "space")
        val telKeys = arrayOf("none", "skt", "kt", "lgu")
        val statKeys = arrayOf("none", "military", "student", "public", "mom")

        val userCard = cardKeys.getOrElse(pref.getInt("card_idx", 0)) { "none" }
        val userMem = memKeys.getOrElse(pref.getInt("mem_idx", 0)) { "none" }
        val userTel = telKeys.getOrElse(pref.getInt("tel_idx", 0)) { "none" }
        val userStat = statKeys.getOrElse(pref.getInt("stat_idx", 0)) { "none" }

        // 혜택 표시용 팝업 뷰 생성
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply {
                setColor(0xFFF8F9FA.toInt())
                cornerRadius = 30f
                setStroke(3, 0xFF1B263B.toInt())
            }
        }

        val title = TextView(this).apply {
            text = "✨ 퍼센트 맞춤 할인 가이드"
            textSize = 17f
            paint.isFakeBoldText = true
            setTextColor(0xFF1B263B.toInt())
            setPadding(0, 0, 0, 20)
        }
        container.addView(title)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 500
            )
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 전체 지원 플랫폼 혜택 요약 렌더링
        BenefitManager.benefitDB.forEach { (key, platform) ->
            val platformTitle = TextView(this).apply {
                text = "📌 [${platform.name}]"
                textSize = 14f
                paint.isFakeBoldText = true
                setTextColor(0xFF415A77.toInt())
                setPadding(0, 10, 0, 5)
            }
            contentLayout.addView(platformTitle)

            var benefitText = platform.base + "\n"
            if (platform.membership.containsKey(userMem)) benefitText += "${platform.membership[userMem]}\n"
            if (platform.card.containsKey(userCard)) benefitText += "${platform.card[userCard]}\n"
            if (platform.telecom.containsKey(userTel)) benefitText += "${platform.telecom[userTel]}\n"
            if (platform.status.containsKey(userStat)) benefitText += "${platform.status[userStat]}\n"

            val desc = TextView(this).apply {
                text = benefitText
                textSize = 12f
                setTextColor(0xFF333333.toInt())
                setPadding(0, 0, 0, 15)
            }
            contentLayout.addView(desc)
        }
        scrollView.addView(contentLayout)
        container.addView(scrollView)

        val closeBtn = Button(this).apply {
            text = "닫기"
            setBackgroundColor(0xFF1B263B.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener {
                if (popupView != null) {
                    windowManager.removeView(popupView)
                    popupView = null
                }
            }
        }
        container.addView(closeBtn)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val popupParams = WindowManager.LayoutParams(
            750, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        popupView = container
        windowManager.addView(popupView, popupParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) windowManager.removeView(floatingView)
        if (popupView != null) windowManager.removeView(popupView)
    }
}
