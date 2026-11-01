package com.percent.discount

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
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

        // 💡 우측 상단 프로필 버튼 클릭 시 찾아낸 원본 네이티브 UI를 팝업으로 호출
        profileButton.setOnClickListener {
            showNativeProfileDialog()
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

    // ==========================================
    // 💡 복원 완료: 사용자 제작 오리지널 프로필 UI 팝업
    // ==========================================
    private fun showNativeProfileDialog() {
        val bottomSheet = BottomSheetDialog(this)
        val pref = getSharedPreferences("PercentProfile", Context.MODE_PRIVATE)

        val selectedCards = pref.getStringSet("selected_cards", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val selectedPays = pref.getStringSet("selected_pays", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val selectedMems = pref.getStringSet("selected_mems", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val selectedTels = pref.getStringSet("selected_tels", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        val root = RelativeLayout(this).apply { 
            setBackgroundColor(0xFFF2F4F6.toInt()) 
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.9).toInt()
            )
        }

        val saveBtn = Button(this).apply {
            id = View.generateViewId()
            text = "프로필 저장하기 ✨"
            textSize = 16f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            background = GradientDrawable().apply {
                setColor(0xFF1B263B.toInt())
                cornerRadius = 30f
            }
            setOnClickListener {
                pref.edit()
                    .putStringSet("selected_cards", selectedCards)
                    .putStringSet("selected_pays", selectedPays)
                    .putStringSet("selected_mems", selectedMems)
                    .putStringSet("selected_tels", selectedTels)
                    .apply()
                
                Toast.makeText(this@MainActivity, "프로필이 성공적으로 저장되었습니다!", Toast.LENGTH_SHORT).show()
                bottomSheet.dismiss()
            }
        }
        val btnParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, 150).apply {
            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            setMargins(40, 20, 40, 40)
        }
        root.addView(saveBtn, btnParams)

        val scrollView = ScrollView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT).apply {
                addRule(RelativeLayout.ABOVE, saveBtn.id)
            }
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 60, 50, 40)
        }
        scrollView.addView(scrollContent)
        root.addView(scrollView)

        scrollContent.addView(TextView(this).apply {
            text = "어떤 결제 수단을\n쓰시나요?"
            textSize = 26f
            paint.isFakeBoldText = true
            setTextColor(0xFF1B263B.toInt())
            setLineSpacing(0f, 1.2f)
            setPadding(0, 0, 0, 15)
        })
        
        scrollContent.addView(TextView(this).apply {
            text = "AI가 숨은 할인을 모두 찾아낼게요."
            textSize = 15f
            setTextColor(0xFF888888.toInt())
            setPadding(0, 0, 0, 50)
        })

        val cardList = listOf("KB국민카드", "신한카드", "현대카드", "삼성카드", "롯데카드", "NH농협카드", "우리카드", "하나카드", "BC카드", "IBK기업은행", "카카오뱅크", "토스뱅크", "케이뱅크")
        val payList = listOf("카카오페이", "네이버페이", "토스페이", "페이코", "KB Pay", "스마일페이", "SSG페이", "SK페이", "삼성페이", "애플페이")
        val memList = listOf("쿠팡 와우", "네이버플러스", "신세계 유니버스", "T우주패스", "컬리멤버스", "배민클럽", "요기패스", "CJ ONE", "L.POINT", "해피포인트", "OK캐쉬백")
        val telList = listOf("SKT", "KT", "LGU+", "알뜰폰", "군인", "대학생", "임산부/육아맘", "시니어")

        scrollContent.addView(createSectionBox("💳 보유 카드", cardList, selectedCards))
        scrollContent.addView(createSectionBox("⚡ 간편결제", payList, selectedPays))
        scrollContent.addView(createSectionBox("👑 구독 멤버십 & 포인트", memList, selectedMems))
        scrollContent.addView(createSectionBox("📱 통신사 & 신분", telList, selectedTels))

        bottomSheet.setContentView(root)
        bottomSheet.show()
    }

    private fun createSectionBox(titleText: String, items: List<String>, selectedSet: MutableSet<String>): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 40f
            }
            setPadding(45, 50, 45, 60)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { 
                setMargins(0, 0, 0, 40) 
            }
        }

        box.addView(TextView(this).apply {
            text = titleText
            textSize = 17f
            paint.isFakeBoldText = true
            setTextColor(Color.BLACK)
            setPadding(10, 0, 0, 40)
        })

        val flowLayout = FlowLayout(this).apply {
            itemSpacing = 20
            lineSpacing = 25
        }
        
        for (item in items) {
            flowLayout.addView(createChip(item, selectedSet))
        }
        box.addView(flowLayout)

        return box
    }

    private fun getIconForName(name: String): Int {
        return when (name) {
            "KB국민카드" -> R.drawable.ic_card_kb
            "신한카드" -> R.drawable.ic_card_shinhan
            "롯데카드" -> R.drawable.ic_card_lotte
            "NH농협카드" -> R.drawable.ic_card_nh
            "우리카드" -> R.drawable.ic_card_woori
            "하나카드" -> R.drawable.ic_card_hana
            "BC카드" -> R.drawable.ic_card_bc
            "IBK기업은행" -> R.drawable.ic_card_ibk
            "카카오뱅크" -> R.drawable.ic_card_kakaobank
            "토스뱅크" -> R.drawable.ic_card_tossbank
            "케이뱅크" -> R.drawable.ic_card_kbank
            "카카오페이" -> R.drawable.ic_pay_kakao
            "네이버페이" -> R.drawable.ic_pay_naver
            "페이코" -> R.drawable.ic_pay_payco
            "KB Pay" -> R.drawable.ic_pay_kb
            "스마일페이" -> R.drawable.ic_pay_smile
            "SSG페이" -> R.drawable.ic_pay_ssg
            "SK페이" -> R.drawable.ic_pay_sk
            "삼성페이" -> R.drawable.ic_pay_samsung
            "애플페이" -> R.drawable.ic_pay_apple
            "쿠팡 와우" -> R.drawable.ic_mem_wow
            "네이버플러스" -> R.drawable.ic_mem_naverplus
            "신세계 유니버스" -> R.drawable.ic_mem_ssg_universe
            "T우주패스" -> R.drawable.ic_mem_t_universe
            "컬리멤버스" -> R.drawable.ic_mem_kurly
            "요기패스" -> R.drawable.ic_mem_yogiyo
            "CJ ONE" -> R.drawable.ic_point_cjone
            "L.POINT" -> R.drawable.ic_point_lpoint
            "해피포인트" -> R.drawable.ic_point_happy
            else -> 0 
        }
    }

    private fun getScaledDrawable(resId: Int): Drawable? {
        if (resId == 0) return null
        val drawable = ContextCompat.getDrawable(this, resId) ?: return null
        val size = (24 * resources.displayMetrics.density).toInt() 
        drawable.setBounds(0, 0, size, size)
        return drawable
    }

    private fun createChip(textStr: String, selectedSet: MutableSet<String>): TextView {
        val tv = TextView(this).apply {
            text = textStr
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(40, 25, 40, 25)
            compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
            
            val iconResId = getIconForName(textStr)
            val scaledIcon = getScaledDrawable(iconResId)
            setCompoundDrawables(scaledIcon, null, null, null)
        }
        
        val updateUI = {
            if (selectedSet.contains(textStr)) {
                tv.setTextColor(0xFF1B263B.toInt())
                tv.paint.isFakeBoldText = true
                tv.background = GradientDrawable().apply {
                    setColor(0xFFE8EEF6.toInt()) 
                    setStroke(5, 0xFF1B263B.toInt()) 
                    cornerRadius = 100f 
                }
            } else {
                tv.setTextColor(0xFF333333.toInt()) 
                tv.paint.isFakeBoldText = false
                tv.background = GradientDrawable().apply {
                    setColor(0xFFFFFFFF.toInt()) 
                    setStroke(2, 0xFFDDDDDD.toInt()) 
                    cornerRadius = 100f 
                }
            }
        }
        updateUI() 
        
        tv.setOnClickListener {
            if (selectedSet.contains(textStr)) selectedSet.remove(textStr)
            else selectedSet.add(textStr)
            updateUI()
        }
        return tv
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

    class FlowLayout(context: Context) : ViewGroup(context) {
        var itemSpacing: Int = 20
        var lineSpacing: Int = 20

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val targetWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            var currentWidth = 0; var currentHeight = 0; var maxLineHeight = 0

            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.visibility == View.GONE) continue
                measureChild(child, widthMeasureSpec, heightMeasureSpec)
                if (currentWidth + child.measuredWidth > targetWidth) {
                    currentWidth = 0
                    currentHeight += maxLineHeight + lineSpacing
                    maxLineHeight = 0
                }
                currentWidth += child.measuredWidth + itemSpacing
                if (child.measuredHeight > maxLineHeight) maxLineHeight = child.measuredHeight
            }
            val finalWidth = MeasureSpec.getSize(widthMeasureSpec)
            val finalHeight = currentHeight + maxLineHeight + paddingTop + paddingBottom
            setMeasuredDimension(finalWidth, resolveSize(finalHeight, heightMeasureSpec))
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val targetWidth = r - l - paddingLeft - paddingRight
            var currentX = paddingLeft; var currentY = paddingTop; var maxLineHeight = 0

            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.visibility == View.GONE) continue
                if (currentX + child.measuredWidth > paddingLeft + targetWidth) {
                    currentX = paddingLeft
                    currentY += maxLineHeight + lineSpacing
                    maxLineHeight = 0
                }
                child.layout(currentX, currentY, currentX + child.measuredWidth, currentY + child.measuredHeight)
                currentX += child.measuredWidth + itemSpacing
                if (child.measuredHeight > maxLineHeight) maxLineHeight = child.measuredHeight
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
