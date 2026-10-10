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

    // 💡 업데이트 테스트를 위해 버전을 1.1.0으로 올렸습니다!
    private val CURRENT_VERSION = "v1.1.0"

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
                val serviceIntent = Intent(this@MainActivity, OverlayService::class.java)
                startService(serviceIntent)
                Toast.makeText(this@MainActivity, "화면에 퍼센트(%) 위젯이 활성화되었습니다!", Toast.LENGTH_SHORT).show()
                moveTaskToBack(true)
            }
        }
        layout.addView(startBtn)

        setContentView(scrollView)

        // 💡 앱 켜자마자 몰래 깃허브 최신 버전 검사 시작
        checkForUpdate()
    }

    // 💡 앱 화면이 다시 열릴 때마다 권한이 켜져 있는지 확인합니다.
    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    // 💡 권한 자동 확인 및 설정 팝업 띄우기
    private fun checkPermissions() {
        // 1. 화면 글자 읽기(접근성) 권한 체크
        if (!isAccessibilityEnabled()) {
            showPermissionDialog(
                "화면 스캔 권한 필요",
                "쇼핑몰 화면의 할인 정보를 AI가 분석하려면 '접근성 권한'이 필요합니다.\n\n설정 화면이 열리면 [퍼센트]를 찾아 켜주세요.",
                Settings.ACTION_ACCESSIBILITY_SETTINGS
            )
            return // 한 번에 하나씩 띄우기 위해 여기서 멈춤
        }

        // 2. 다른 앱 위에 그리기(위젯) 권한 체크
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showPermissionDialog(
                "위젯 표시 권한 필요",
                "할인 위젯(%)을 쇼핑몰 화면 위에 띄우려면 권한이 필요합니다.\n\n설정에서 [퍼센트]를 허용해주세요.",
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            )
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val prefString = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return prefString?.contains(packageName) == true
    }

    private fun showPermissionDialog(title: String, message: String, action: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("설정하러 가기") { _, _ ->
                val intent = Intent(action)
                if (action == Settings.ACTION_MANAGE_OVERLAY_PERMISSION) {
                    intent.data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
            .setCancelable(false)
            .show()
    }

    private fun createLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(0xFF415A77.toInt())
            setPadding(0, 30, 0, 10)
        }
    }

    // 💡 깃허브 API 통신 로직
    private fun checkForUpdate() {
        Thread {
            try {
                val url = URL("https://api.github.com/repos/RangeE1004/percent-app/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).readText()
                    val json = JSONObject(response)
                    val latestVersion = json.getString("tag_name")

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

    private fun showUpdateDialog(latestVersion: String, apkUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("✨ 새 버전 업데이트 안내")
            .setMessage("권한 자동 설정 기능이 추가된 새로운 퍼센트 앱($latestVersion)이 출시되었습니다!\n지금 바로 업데이트 하시겠습니까?")
            .setPositiveButton("업데이트 하기") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))
                startActivity(intent)
            }
            .setNegativeButton("나중에", null)
            .setCancelable(false)
            .show()
    }
}
