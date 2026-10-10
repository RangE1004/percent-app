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
                        // 터치 민감도를 40으로 둔감하게 하여 중복 터치 에러 완벽 차단
                        if (Math.abs(dx) > 40 || Math.abs(dy) > 40) {
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
            text = "🔎 혜택을 분석하고 있습니다...\n(화면 글자를 읽어 서버와 통신 중)"
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

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val popupParams = WindowManager.LayoutParams(
            800, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        popupView = container
        windowManager.addView(popupView, popupParams)

        Thread { fetchAIResult(userCard, userMem, userTel, userStat, resultText) }.start()
    }

    private fun fetchAIResult(
        card: String, mem: String, tel: String, stat: String, resultView: TextView
    ) {
        try {
            val rawScreenText = ScreenReaderService.currentScreenText
            val safeScreenText = rawScreenText.replace("\"", "\\\"").replace("\n", " ").take(1500)
            val pText = "카드($card), 멤버십($mem), 통신사($tel), 신분($stat)"

            val url = URL("https://discount-scouter.vercel.app/api/analyze")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Accept", "application/json; charset=UTF-8")
            conn.doOutput = true

            // 💡 수정 완료 1: 서버 코드(analyze.js)가 요구하는 정확한 포장지(sharedText, pText) 사용
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

                // 💡 수정 완료 2: Gemini가 보내준 복잡한 JSON 데이터를 해독하여 화면에 예쁘게 출력
                try {
                    val root = JSONObject(response)
                    val candidates = root.getJSONArray("candidates")
                    val content = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    val textResult = parts.getJSONObject(0).getString("text")

                    // 서버가 생성한 {"tips": [...], "caution": "..."} 구조 분석
                    val innerJson = JSONObject(textResult)
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
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        resultView.text = "서버 응답을 해독하지 못했습니다.\n원문: $response"
                    }
                }
            } else {
                // 에러 발생 시 Vercel에서 구체적으로 어떤 에러를 보냈는지까지 화면에 출력
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
    }
}
