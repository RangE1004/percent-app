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
import android.widget.Toast
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
    
    // 💡 [토큰 방어 1 & 3] 연타 방지 스위치 및 통신 강제 종료를 위한 변수
    private var isFetchingAI = false
    private var activeConnection: HttpURLConnection? = null

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

        closeAreaView = TextView(this).apply {
            text = "✕"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
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

        // 💡 [토큰 방어 1] 광클(연타) 방지 로직: 통신 중이면 튕겨냄
        if (isFetchingAI) {
            Toast.makeText(this, "AI가 열심히 혜택을 계산 중이에요. 잠시만요!", Toast.LENGTH_SHORT).show()
            return
        }

        val pref = getSharedPreferences("PercentProfile", Context.MODE_PRIVATE)
        
        // 💡 [토큰 방어 4] 캐시 키 통일을 위한 알파벳 정렬(sorted) 및 다중 데이터 조합
        val sortedCard = pref.getStringSet("selected_cards", emptySet())?.sorted()?.joinToString(",") ?: "none"
        val sortedPay = pref.getStringSet("selected_pays", emptySet())?.sorted()?.joinToString(",") ?: "none"
        val sortedMem = pref.getStringSet("selected_mems", emptySet())?.sorted()?.joinToString(",") ?: "none"
        val sortedTel = pref.getStringSet("selected_tels", emptySet())?.sorted()?.joinToString(",") ?: "none"
        val pText = "카드($sortedCard), 페이($sortedPay), 멤버십($sortedMem), 통신사/신분($sortedTel)"

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
            text = "✨ 할인 스카우터 결과"
            textSize = 17f
            paint.isFakeBoldText = true
            setTextColor(0xFF1B263B.toInt())
            setPadding(0, 0, 0, 20)
            
            setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0; private var initialY = 0
                private var initialTouchX = 0f; private var initialTouchY = 0f

                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = popupParams.x; initialY = popupParams.y
                            initialTouchX = event.rawX; initialTouchY = event.rawY
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 600)
        }
        
        val resultText = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFF333333.toInt())
            setLineSpacing(0f, 1.3f)
            gravity = Gravity.CENTER
        }
        scrollView.addView(resultText)
        container.addView(scrollView)

        val closeBtn = Button(this).apply {
            text = "닫기"
            setBackgroundColor(0xFF1B263B.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener {
                // 💡 [토큰 방어 3] 닫기 버튼 누르면 진행 중이던 통신 강제 단절 (유령 요청 차단)
                try { activeConnection?.disconnect() } catch (e: Exception) {}
                isFetchingAI = false 
                
                if (popupView != null) {
                    windowManager.removeView(popupView)
                    popupView = null
                }
            }
        }
        container.addView(closeBtn)

        popupView = container
        windowManager.addView(popupView, popupParams)

        val loadingHandler = Handler(Looper.getMainLooper())
        val loadingMessages = arrayOf(
            "🔎 화면 정보를 스캔하고 있어요\n(잠시만 기다려주세요)",
            "🎁 숨겨진 혜택을 분석 중이에요\n(쿠폰 및 멤버십 확인)",
            "✨ 최적의 할인 조합 계산 중...\n(거의 다 왔어요!)"
        )
        var msgIndex = 0
        val loadingRunnable = object : Runnable {
            override fun run() {
                resultText.text = loadingMessages[msgIndex % loadingMessages.size]
                msgIndex++
                loadingHandler.postDelayed(this, 2000)
            }
        }
        loadingHandler.post(loadingRunnable) 

        // 💡 락 걸고 서버 요청 시작
        isFetchingAI = true
        Thread { fetchAIResult(pText, resultText, loadingHandler, loadingRunnable) }.start()
    }

    private fun fetchAIResult(
        pText: String, resultView: TextView, 
        loadingHandler: Handler, loadingRunnable: Runnable
    ) {
        try {
            val rawScreenText = ScreenReaderService.getHybridSnapshot()
            
            // 💡 [토큰 방어 2] 허공 스캔 방지 (화면에 글자가 50자 이하면 차단)
            if (rawScreenText.trim().length < 50) {
                loadingHandler.removeCallbacks(loadingRunnable)
                Handler(Looper.getMainLooper()).post {
                    resultView.gravity = Gravity.CENTER
                    resultView.text = "현재 화면에서 결제/할인 정보를\n충분히 찾지 못했어요!\n(상품 페이지에서 다시 눌러주세요)"
                }
                return // 서버로 전송하지 않고 종료
            }

            val safeScreenText = rawScreenText.replace("\\", "\\\\").replace("\"", "\\\"")

            val url = URL("https://discount-scouter.vercel.app/api/analyze")
            val conn = url.openConnection() as HttpURLConnection
            activeConnection = conn // 통신 객체 연결 (강제 종료를 위함)
            
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

                        loadingHandler.removeCallbacks(loadingRunnable)
                        Handler(Looper.getMainLooper()).post {
                            resultView.gravity = Gravity.START
                            resultView.text = sb.toString().trim()
                        }
                    } else {
                        loadingHandler.removeCallbacks(loadingRunnable)
                        Handler(Looper.getMainLooper()).post {
                            resultView.text = "혜택 정보를 찾을 수 없습니다.\n($textResult)"
                        }
                    }
                } catch (e: Exception) {
                    loadingHandler.removeCallbacks(loadingRunnable)
                    Handler(Looper.getMainLooper()).post {
                        resultView.text = "서버 응답 해독 실패.\n원문: $response"
                    }
                }
            } else {
                loadingHandler.removeCallbacks(loadingRunnable)
                Handler(Looper.getMainLooper()).post {
                    resultView.text = "통신 에러 (코드: ${conn.responseCode})"
                }
            }
        } catch (e: Exception) {
            loadingHandler.removeCallbacks(loadingRunnable)
            Handler(Looper.getMainLooper()).post {
                resultView.text = "통신 실패 또는 강제 종료 됨.\n(${e.message})"
            }
        } finally {
            // 💡 [토큰 방어 1] 통신이 성공/실패/강제종료 어떤 경우든 무조건 락 해제
            isFetchingAI = false
            activeConnection = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) windowManager.removeView(floatingView)
        if (popupView != null) windowManager.removeView(popupView)
        if (closeAreaView != null) windowManager.removeView(closeAreaView)
    }
}
