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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            showMainScreen() 
            isSplashFinished = true
            checkPermissions()
            checkForUpdate()   
        }, 1500)
    }

    private fun showMainScreen() {
        setContentView(R.layout.activity_main)

        val searchEditText = findViewById<EditText>(R.id.searchEditText)
        val profileButton = findViewById<ImageView>(R.id.profileButton)
        val productRecyclerView = findViewById<RecyclerView>(R.id.productRecyclerView)
        val startAssistantButton = findViewById<Button>(R.id.startAssistantButton)

        productRecyclerView.layoutManager = LinearLayoutManager(this)

        searchCoupang("오늘의 특가", productRecyclerView)

        startAssistantButton.setOnClickListener {
            if (isAccessibilityEnabled() && Settings.canDrawOverlays(this)) {
                startService(Intent(this@MainActivity, OverlayService::class.java))
                Toast.makeText(this, "쇼핑 비서 가동 중! 쇼핑 앱을 켜보세요.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "권한 설정이 필요합니다.", Toast.LENGTH_SHORT).show()
                checkPermissions() 
            }
        }

        // 💡 [수정됨] ProfileManager가 싱글톤(object)이므로 새로 생성하지 않고 직접 호출합니다.
        profileButton.setOnClickListener {
            ProfileManager.show(this@MainActivity) 
        }

        searchEditText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchEditText.text.toString()
                if (query.isNotEmpty()) {
                    Toast.makeText(this, "쿠팡 최저가 검색 중...", Toast.LENGTH_SHORT).show()
                    searchCoupang(query, productRecyclerView)
                }
                true
            } else {
                false
            }
        }
    }

    private fun searchCoupang(query: String, recyclerView: RecyclerView) {
        thread {
            try {
                val url = URL("https://discount-scouter.vercel.app/api/search") 
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val jsonInputString = "{\"query\": \"$query\"}"
                conn.outputStream.use { os ->
                    val input = jsonInputString.toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                if (conn.responseCode == 200) {
                    val response = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).readText()
                    val json = JSONObject(response)
                    val results = json.getJSONArray("results")
                    val productList = mutableListOf<Product>()

                    for (i in 0 until results.length()) {
                        val item = results.getJSONObject(i)
                        productList.add(Product(
                            item.getString("title"),
                            item.getInt("price"),
                            item.getString("imageUrl"),
                            item.getString("productUrl"),
                            item.optBoolean("isRocket", false)
                        ))
                    }

                    runOnUiThread {
                        recyclerView.adapter = ProductAdapter(productList) { productUrl ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(productUrl))
                            startActivity(intent)
                        }
                    }
                } else {
                    runOnUiThread { Toast.makeText(this@MainActivity, "검색 서버 오류", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this@MainActivity, "네트워크 에러", Toast.LENGTH_SHORT).show() }
            }
        }
    }

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

data class Product(val title: String, val price: Int, val imageUrl: String, val productUrl: String, val isRocket: Boolean)

class ProductAdapter(private val products: List<Product>, private val onClick: (String) -> Unit) : RecyclerView.Adapter<ProductAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.productImage)
        val title: TextView = view.findViewById(R.id.productTitle)
        val price: TextView = view.findViewById(R.id.productPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val p = products[position]
        holder.title.text = if (p.isRocket) "🚀 " + p.title else p.title
        holder.price.text = "%,d원".format(p.price)
        holder.itemView.setOnClickListener { onClick(p.productUrl) }

        holder.image.setImageResource(android.R.color.transparent)
        thread {
            try {
                val url = URL(p.imageUrl)
                val bmp = android.graphics.BitmapFactory.decodeStream(url.openConnection().inputStream)
                Handler(Looper.getMainLooper()).post { holder.image.setImageBitmap(bmp) }
            } catch (e: Exception) {}
        }
    }

    override fun getItemCount() = products.size
}
