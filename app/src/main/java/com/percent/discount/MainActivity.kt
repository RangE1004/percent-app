package com.percent.discount

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val CURRENT_VERSION = "v1.2.0"

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

        layout.addView(createLabel("💳 주로 쓰는 카드"))
        val cardList = arrayOf("선택 안함 (none)", "국민카드 (kb)", "신한카드 (shinhan)", "현대카드 (hyundai)", "삼성카드 (samsung)", "롯데카드 (lotte)", "NH농협카드 (nh)")
        val cardSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, cardList)
            setSelection(pref.getInt("card_idx", 0))
        }
        layout.addView(cardSpinner)

        layout.addView(createLabel("👑 구독 중인 멤버십"))
        val memList = arrayOf("가입 안함 (none)", "쿠팡 와우회원 (wow)", "네이버플러스 멤버십 (naver)", "신세계 유니버스 클럽 (universe)", "T우주패스 (space)")
        val memSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, memList)
            setSelection(pref.getInt("mem_idx", 0))
        }
        layout.addView(memSpinner)

        layout.addView(createLabel("📱 통신사 혜택"))
        val telList = arrayOf("선택 안함 (none)", "SKT (skt)", "KT (kt)", "LG U+ (lgu)")
        val telSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, telList)
            setSelection(pref.getInt("tel_idx", 0))
        }
        layout.addView(telSpinner)

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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 30, 0, 15) }
            setOnClickListener {
                pref.edit().putInt("card_idx", cardSpinner.selectedItemPosition)
                    .putInt("mem_idx", memSpinner.selectedItemPosition)
                    .putInt("tel_idx", telSpinner.selectedItemPosition)
                    .putInt("stat_idx", statSpinner.selectedItemPosition).apply()
                Toast.makeText(this@MainActivity, "설정이 안전하게 저장되었습니다!", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(saveBtn)

        val startBtn = Button(this).apply {
            text = "플로팅 위젯 켜기"
            setBackgroundColor(0xFF1B263B.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 40) }
            setOnClickListener {
                val serviceIntent = Intent(this@MainActivity, OverlayService::class.java)
                startService(serviceIntent)
                Toast.makeText(this@MainActivity, "퍼센트(%) 위젯이 활성화되었습니다!", Toast.LENGTH_SHORT).show()
                moveTaskToBack(true)
            }
        }
        layout.addView(startBtn)
        setContentView(scrollView)

        checkForUpdate()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun createLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text; textSize = 14f; setTextColor(0xFF415A77.toInt()); setPadding(0, 30, 0, 10)
        }
    }

    // 💡 프로급 권한 안내 디자인 (커스텀 레이아웃)
    private fun showCustomPermissionDialog(iconText: String, titleText: String, messageText: String, action: String) {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(70, 70, 70, 70)
            
            addView(TextView(this@MainActivity).apply {
                text = iconText
                textSize = 45f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 20)
            })
            
            addView(TextView(this@MainActivity).apply {
                text = titleText
                textSize = 20f
                paint.isFakeBoldText = true
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 30)
            })
            
            addView(TextView(this@MainActivity).apply {
                text = messageText
                textSize = 15f
                setLineSpacing(0f, 1.3f)
                setTextColor(Color.DKGRAY)
            })
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
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

    private fun checkPermissions() {
        if (!isAccessibilityEnabled()) {
            val guideMsg = "AI가 쇼핑몰 화면을 읽고 할인 정보를 분석하려면 권한이 꼭 필요해요.\n\n👉 [설정하러 가기] 클릭\n👉 화면에서 [설치된 앱] 선택\n👉 [퍼센트] 찾아서 켜기"
            showCustomPermissionDialog("🔍", "화면 스캔 권한 필요", guideMsg, Settings.ACTION_ACCESSIBILITY_SETTINGS)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val guideMsg = "쇼핑몰 화면 위에 할인 위젯(%)을 띄우기 위해 권한이 필요합니다.\n\n👉 화면 목록에서 [퍼센트]를 켜주세요."
            showCustomPermissionDialog("📱", "위젯 표시 권한 필요", guideMsg, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val prefString = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return prefString?.contains(packageName) == true
    }

    // 💡 찌꺼기 없는 자체 다운로드 및 자동 설치 로직
    private fun checkForUpdate() {
        thread {
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
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun showUpdateDialog(latestVersion: String, apkUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("✨ 새 버전 업데이트")
            .setMessage("더 편리해진 퍼센트 최신 버전($latestVersion)이 나왔습니다!\n지금 바로 업데이트를 진행할까요?")
            .setPositiveButton("업데이트 하기") { _, _ ->
                downloadAndInstallApk(apkUrl)
            }
            .setNegativeButton("나중에", null)
            .setCancelable(false)
            .show()
    }

    private fun downloadAndInstallApk(apkUrl: String) {
        Toast.makeText(this, "업데이트를 준비 중입니다. 잠시만 기다려주세요...", Toast.LENGTH_LONG).show()
        thread {
            try {
                val url = URL(apkUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connect()

                // 앱 내부 임시(Cache) 폴더 사용
                val updateDir = File(cacheDir, "updates")
                if (!updateDir.exists()) updateDir.mkdirs()

                // 💡 핵심: 기존 찌꺼기 파일 완벽하게 삭제 (용량 절약)
                updateDir.listFiles()?.forEach { it.delete() }

                val apkFile = File(updateDir, "update.apk")
                val inputStream = conn.inputStream
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.close(); inputStream.close()

                // 자동 설치 화면 띄우기
                val intent = Intent(Intent.ACTION_VIEW)
                val apkUri = FileProvider.getUriForFile(
                    this,
                    "com.percent.discount.fileprovider",
                    apkFile
                )
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this@MainActivity, "다운로드 실패: 인터넷 연결을 확인해주세요.", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}
