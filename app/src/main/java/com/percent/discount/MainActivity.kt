package com.percent.discount

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.EditorInfo
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

    private val CURRENT_VERSION = "v1.2.1"
    private var isSplashFinished = false 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash) // 스플래시 유지

        Handler(Looper.getMainLooper()).postDelayed({
            showMainScreen() 
            isSplashFinished = true
            
            checkPermissions()
            checkForUpdate()   
        }, 1500)
    }

    // 💡 [변경됨] 복잡한 프로그램 UI 대신, 새로 만든 activity_main.xml을 불러옵니다.
    private fun showMainScreen() {
        setContentView(R.layout.activity_main)

        val searchEditText = findViewById<EditText>(R.id.searchEditText)
        val profileButton = findViewById<ImageView>(R.id.profileButton)

        // 1. 권한이 모두 켜져 있다면, 앱 메인 화면 진입 시 오버레이 스캐너 자동 가동
        if (isAccessibilityEnabled() && Settings.canDrawOverlays(this)) {
            startService(Intent(this@MainActivity, OverlayService::class.java))
            Toast.makeText(this, "퍼센트 스캐너 가동 중! 쇼핑몰로 이동해보세요.", Toast.LENGTH_SHORT).show()
        }

        // 2. 우측 상단 프로필(동그라미) 터치 이벤트
        profileButton.setOnClickListener {
            // TODO: 기존의 예쁜 카드/페이 선택 화면을 띄우는 로직 연결 예정
            Toast.makeText(this, "프로필 수정 화면 준비 중", Toast.LENGTH_SHORT).show()
        }

        // 3. 중앙 검색창 검색(엔터) 이벤트
        searchEditText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchEditText.text.toString()
                if (query.isNotEmpty()) {
                    Toast.makeText(this, "'$query' 검색 및 쿠팡 연동 준비 중", Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        }
    }

    // ==========================================
    // 💡 아래부터는 기존 사용자님의 권한/업데이트 로직 (100% 원본 유지)
    // ==========================================
    override fun onResume() {
        super.onResume()
        if (isSplashFinished) checkPermissions()
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

    private fun showCustomPermissionDialog(iconText: String, titleText: String, messageText: String, action: String) {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(70, 70, 70, 70)
            addView(TextView(this@MainActivity).apply { text = iconText; textSize = 45f; gravity = Gravity.CENTER; setPadding(0, 0, 0, 20) })
            addView(TextView(this@MainActivity).apply { text = titleText; textSize = 20f; paint.isFakeBoldText = true; setTextColor(Color.BLACK); gravity = Gravity.CENTER; setPadding(0, 0, 0, 30) })
            addView(TextView(this@MainActivity).apply { text = messageText; textSize = 15f; setLineSpacing(0f, 1.3f); setTextColor(Color.DKGRAY) })
        }
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("설정하러 가기") { _, _ ->
                val intent = Intent(action)
                if (action == Settings.ACTION_MANAGE_OVERLAY_PERMISSION) intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            .setCancelable(false).show()
    }

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
            .setPositiveButton("업데이트 하기") { _, _ -> downloadAndInstallApk(apkUrl) }
            .setNegativeButton("나중에", null).setCancelable(false).show()
    }

    private fun downloadAndInstallApk(apkUrl: String) {
        Toast.makeText(this, "업데이트를 준비 중입니다. 잠시만 기다려주세요...", Toast.LENGTH_LONG).show()
        thread {
            try {
                val url = URL(apkUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connect()
                val updateDir = File(cacheDir, "updates")
                if (!updateDir.exists()) updateDir.mkdirs()
                updateDir.listFiles()?.forEach { it.delete() }
                val apkFile = File(updateDir, "update.apk")
                val inputStream = conn.inputStream
                val outputStream = FileOutputStream(apkFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) outputStream.write(buffer, 0, bytesRead)
                outputStream.close(); inputStream.close()
                val intent = Intent(Intent.ACTION_VIEW)
                val apkUri = FileProvider.getUriForFile(this, "com.percent.discount.fileprovider", apkFile)
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
