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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var popupView: View? = null
    private var closeAreaView: TextView? = null

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
            includeFontPadding = false
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

        // 💡 X 버튼 디자인 완벽 교체: 정중앙 정렬, 완벽한 원형(OVAL), 얇고 깔끔한 특수문자(✕)
        closeAreaView = TextView(this).apply {
            text = "✕"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false // 글자 위아래 기본 여백 제거로 완벽한 중앙 정렬
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL // 네모가 아닌 완벽한 동그라미로 강제 지정
                setColor(0xFFFF4444.toInt())
            }
            visibility = View.GONE
            alpha = 0.5f
        }
        val closeParams = WindowManager.LayoutParams(
            180, 180, layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 150 
        }
        windowManager.addView(closeAreaView, closeParams)

        val screenHeight = resources.displayMetrics.heightPixels

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

                        if (!isMove && (Math.abs(dx) > 15 || Math.abs(dy) > 15)) {
                            isMove = true
                            closeAreaView?.visibility = View.VISIBLE
                        }

                        if (isMove) {
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager.updateViewLayout(button, params)

                            if (event.rawY > screenHeight - 400) {
                                closeAreaView?.scaleX = 1.3f
                                closeAreaView?.scaleY = 1.3f
                                closeAreaView?.alpha = 1.0f
                            } else {
                                closeAreaView?.scaleX = 1.0f
                                closeAreaView?.scaleY = 1.0f
                                closeAreaView?.alpha = 0.5f
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isMove) {
                            closeAreaView?.visibility = View.GONE
                            if (event.rawY > screenHeight - 400) {
                                stopSelf()
                            }
                        } else {
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

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply {
                setColor(0xFFF8F9FA.toInt())
                cornerRadius = 30f
                setStroke(3, 0xFF1B263B.toInt())
            }
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val popupParams = WindowManager.LayoutParams(
            800, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels - 800) / 2
            y = resources.displayMetrics.heightPixels - 1200
        }

        val title = TextView(this).apply {
            text = "✨ 할인 스카우터 결과" // 세련된 제목
            textSize = 17f
            paint.isFakeBoldText = true
            setTextColor(0xFF1B263B.toInt())
            setPadding(0, 0, 0, 20)
            
            setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f

                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = popupParams.x
                            initialY = popupParams.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            popupParams.x = initialX + (event.rawX - initialTouchX).toInt()
                            popupParams.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(container, popupParams)
                            return true
                        }
                    }
                    return false
                }
            })
        }
        container.addView(title)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 600
            )
        }
        
        // 💡 프로 앱 느낌의 고급스러운 로딩 문구
        val resultText = TextView(this).apply {
            text = "최적의 할인 및 결제 혜택 조합을 계산하고 있습니다...\n잠시만 기다려주세요."
            textSize = 15f
            setTextColor(0xFF333333.toInt())
            setLineSpacing(0f, 1.2f)
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

        popupView = container
        windowManager.addView(popupView, popupParams)

        Thread { fetchAIResult(userCard, userMem, userTel, userStat, resultText) }.start()
    }

    private fun fetchAIResult(
        card: String, mem: String, tel: String, stat: String, resultView: TextView
    ) {
        try {
            val rawScreenText = ScreenReaderService.getHybridSnapshot()
            
            // 💡 특수문자 JSON 통신 오류 완벽 차단 방패 (역슬래시, 따옴표 완벽 방어)
            val safeScreenText = rawScreenText
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
            
            val pText = "카드($card), 멤버십($mem), 통신사($tel), 신분($stat)"

            val url = URL("https://discount-scouter.vercel.app/api/analyze")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Accept", "application/json; charset=UTF-8")
            conn.doOutput = true

            val jsonInputString = """
                {
                    "sharedText": "$safeScreenText",
                    "pText": "$pText"
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

                try {
                    val root = JSONObject(response)
                    val candidates = root.getJSONArray("candidates")
                    val content = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    val textResult = parts.getJSONObject(0).getString("text")

                    val startIndex = textResult.indexOf('{')
                    val endIndex = textResult.lastIndexOf('}')

                    if (startIndex != -1 && endIndex != -1 && startIndex <= endIndex) {
                        val cleanJsonStr = textResult.substring(startIndex, endIndex + 1)
                        val innerJson = JSONObject(cleanJsonStr)
                        
                        val tipsArray = innerJson.optJSONArray("tips")
                        val caution = innerJson.optString("caution", "")

                        val sb = StringBuilder()
                        if (tipsArray != null) {
                            for (i in 0 until tipsArray.length()) {
                                sb.append("✅ ").append(tipsArray.getString(i)).append("\n\n")
                            }
                        }
                        if (caution.isNotEmpty()) {
                            sb.append("⚠️ ").append(caution)
                        }

                        Handler(Looper.getMainLooper()).post {
                            resultView.text = sb.toString().trim()
                        }
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            resultView.text = "혜택 정보를 찾을 수 없습니다.\n($textResult)"
                        }
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        resultView.text = "서버 응답 해독 실패.\n원문: $response"
                    }
                }
            } else {
                val errorResponse = try {
                    BufferedReader(InputStreamReader(conn.errorStream, Charsets.UTF_8)).use { it.readText() }
                } catch (e: Exception) { "알 수 없는 에러" }

                Handler(Looper.getMainLooper()).post {
                    resultView.text = "통신 에러 (코드: ${conn.responseCode})\n이유: $errorResponse"
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
        if (closeAreaView != null) windowManager.removeView(closeAreaView)
    }
}
