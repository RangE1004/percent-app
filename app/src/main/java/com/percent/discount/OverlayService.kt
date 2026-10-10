package com.percent.discount

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        val button = TextView(this).apply {
            text = "%"
            textSize = 22f
            paint.isFakeBoldText = true
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF1B263B.toInt())
                setStroke(3, Color.WHITE)
            }
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            150, 150, layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

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
                        if (!isMove) toggleBenefitPopup()
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
            text = "✨ 퍼센트 AI 분석 결과"
            textSize = 17f
            paint.isFakeBoldText = true
            setTextColor(0xFF1B263B.toInt())
            setPadding(0, 0, 0, 20)
        }
        container.addView(title)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 600
            )
        }
        val resultText = TextView(this).apply {
            text = "🔎 화면 내용을 읽어 AI 서버로 전송 중입니다...\n(잠시만 기다려주세요)"
            textSize = 14f
            setTextColor(0xFF333333.toInt())
        }
        scrollView.addView(resultText)
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
            layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        popupView = container
        windowManager.addView(popupView, popupParams)

        // API 통신 백그라운드 실행
        Thread { fetchAIResult(userCard, userMem, userTel, userStat, resultText) }.start()
    }

    private fun fetchAIResult(
        card: String, mem: String, tel: String, stat: String, resultView: TextView
    ) {
        try {
            val todayDate = SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN).format(Date())
            
            val rawScreenText = ScreenReaderService.currentScreenText
            val safeScreenText = rawScreenText.replace("\"", "\\\"")
                .replace("\n", " ").take(1500)

            // 확인해주신 정확한 버셀 주소 적용 완료!
            val url = URL("https://discount-scouter.vercel.app/api/chat")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Accept", "application/json; charset=UTF-8")
            conn.doOutput = true

            val jsonInputString = """
                {
                    "messages": [
                        {
                            "role": "system",
                            "content": "당신은 쇼핑 결제 혜택 분석 AI입니다. 오늘 날짜는 ${todayDate}입니다. 반드시 최신 날짜 기준으로 확인된 사실만 거짓 없이 대답하세요. 화면 정보에서 상품명과 가격을 찾고, 사용자의 [카드, 멤버십, 통신사] 정보와 결합하여 얻을 수 있는 최종 할인 혜택만 짧고 명확하게 제시하세요. 중복된 문장을 피하세요."
                        },
                        {
                            "role": "user",
                            "content": "현재 화면 정보: $safeScreenText \n\n나의 설정: 카드($card), 멤버십($mem), 통신사($tel), 신분($stat) \n최대 할인 방법을 알려줘."
                        }
                    ]
                }
            """.trimIndent()

            conn.outputStream.use { os ->
                val input = jsonInputString.toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(
                    InputStreamReader(conn.inputStream, Charsets.UTF_8)
                ).use { it.readText() }
                
                val cleanText = response.replace(Regex("0:\"|\""), "")
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")

                Handler(Looper.getMainLooper()).post {
                    resultView.text = cleanText
                }
            } else {
                Handler(Looper.getMainLooper()).post {
                    resultView.text = "서버 오류 (코드: ${conn.responseCode})\n서버 상태를 확인해주세요."
                }
            }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                resultView.text = "통신 실패: 연결 상태를 확인해주세요!\n(${e.message})"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) windowManager.removeView(floatingView)
        if (popupView != null) windowManager.removeView(popupView)
    }
}
