package com.percent.discount

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    // 💡 현재 폰에 설치된 앱의 버전 (2단계 깃허브 릴리즈에 적었던 이름과 동일하게 설정)
    private val CURRENT_VERSION = "v1.0.0"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pref = getSharedPreferences("PercentProfile", Context.MODE_PRIVATE)

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 60, 50, 60)
        }
        scrollView.addView(layout)

        val title = TextView(this).apply {
            text = "퍼센트(%) 맞춤 결제 환경"
            textSize = 22f
            paint.isFakeBoldText = true
            setTextColor(0xFF1B263B.toInt())
            setPadding(0, 0, 0, 40)
        }
        layout.addView(title)

        // 1. 카드 선택
        layout.addView(createLabel("💳 주로 쓰는 카드"))
        val cardList = arrayOf("선택 안함 (none)", "국민카드 (kb)", "신한카드 (shinhan)", "현대카드 (hyundai)", "삼성카드 (samsung)", "롯데카드 (lotte)", "NH농협카드 (nh)")
        val cardSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, cardList)
            setSelection(pref.getInt("card_idx", 0))
        }
        layout.addView(cardSpinner)

        // 2. 멤버십 선택
        layout.addView(createLabel("👑 구독 중인 멤버십"))
        val memList = arrayOf("가입 안함 (none)", "쿠팡 와우회원 (wow)", "네이버플러스 멤버십 (naver)", "신세계 유니버스 클럽 (universe)", "T우주패스 (space)")
        val memSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, memList)
            setSelection(pref.getInt("mem_idx", 0))
        }
        layout.addView(memSpinner)

        // 3. 통신사 선택
        layout.addView(createLabel("📱 통신사 혜택"))
        val telList = arrayOf("선택 안함 (none)", "SKT (skt)", "KT (kt)", "LG U+ (lgu)")
        val telSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, telList)
            setSelection(pref.getInt("tel_idx", 0))
        }
        layout.addView(telSpinner)

        // 4. 특화 신분 선택
        layout.addView(createLabel("🎓 특화 신분 혜택"))
        val statList = arrayOf("해당 없음 (none)", "군인 / 군가족 (military)", "대학생 (student)", "공무원 / 교직원 (public)", "임산부 / 육아맘 (mom)")
        val statSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, statList)
            setSelection(pref.getInt("stat_idx", 0))
        }
        layout.addView(statSpinner)

        val saveBtn = Button(this).apply {
            text = "프로필 정보 저장"
            setBackgroundColor(0xFF415A77.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            val marginParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 30, 0, 15) }
            layoutParams = marginParams
            setPadding(0, 30, 0, 30)
            setOnClickListener {
                pref.edit()
                    .putInt("card_idx", cardSpinner.selectedItemPosition)
                    .putInt("mem_idx", memSpinner.selectedItemPosition)
                    .putInt("tel_idx", telSpinner.selectedItemPosition)
                    .putInt("stat_idx", statSpinner.selectedItemPosition)
                    .apply()
                Toast.makeText(this@MainActivity, "설정이 안전하게 저장되었습니다!", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(saveBtn)

        val startBtn = Button(this).apply {
            text = "플로팅 위젯 켜기"
            setBackgroundColor(0xFF1B263B.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            val marginParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 40) }
            layoutParams = marginParams
            setPadding(0, 30, 0, 30)
            setOnClickListener {
                checkOverlayPermissionAndStart()
            }
        }
        layout.addView(startBtn)

        setContentView(scrollView)

        // 💡 앱 켜자마자 몰래 깃허브 최신 버전 검사 시작
        checkForUpdate()
    }

    private fun createLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(0xFF415A77.toInt())
            setPadding(0, 30, 0, 10)
        }
    }

    private fun checkOverlayPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "'다른 앱 위에 표시' 권한을 허용해 주세요.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        } else {
            val serviceIntent = Intent(this, OverlayService::class.java)
            startService(serviceIntent)
            Toast.makeText(this, "화면에 퍼센트(%) 위젯이 활성화되었습니다!", Toast.LENGTH_SHORT).show()
            moveTaskToBack(true)
        }
    }

    // 💡 깃허브 API를 찔러서 최신 릴리즈 태그와 비교하는 함수
    private fun checkForUpdate() {
        Thread {
            try {
                // 사용자의 깃허브 저장소 주소를 API 형식으로 입력
                val url = URL("https://api.github.com/repos/RangeE1004/percent-app/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).readText()
                    val json = JSONObject(response)
                    val latestVersion = json.getString("tag_name")

                    // 서버의 버전(예: v1.1)이 내 버전(v1.0.0)과 다르면 팝업 띄우기
                    if (latestVersion != CURRENT_VERSION) {
                        val assets = json.getJSONArray("assets")
                        if (assets.length() > 0) {
                            val apkUrl = assets.getJSONObject(0).getString("browser_download_url")
                            runOnUiThread { showUpdateDialog(latestVersion, apkUrl) }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // 💡 업데이트 알림 팝업 띄우고, 확인 시 크롬 브라우저로 다운로드 바로가기 연결
    private fun showUpdateDialog(latestVersion: String, apkUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("✨ 새 버전 업데이트 안내")
            .setMessage("새로운 기능이 추가된 퍼센트 앱($latestVersion)이 출시되었습니다!\n지금 바로 업데이트 하시겠습니까?")
            .setPositiveButton("업데이트 하기") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))
                startActivity(intent)
            }
            .setNegativeButton("나중에", null)
            .setCancelable(false)
            .show()
    }
}
