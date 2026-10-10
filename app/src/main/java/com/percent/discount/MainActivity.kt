package com.percent.discount

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
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

        // 💡 버튼 1: 프로필 정보만 따로 저장하는 버튼
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

        // 💡 버튼 2: 위젯을 켜고 앱을 숨기는 전용 버튼
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
            
            // 💡 위젯이 켜진 직후 앱 화면을 바탕화면 뒤로 부드럽게 숨김
            moveTaskToBack(true)
        }
    }
}
