package com.percent.discount

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ScreenReaderService : AccessibilityService() {

    companion object {
        var instance: ScreenReaderService? = null
        
        // 💡 1차 스냅샷(페이지가 처음 열릴 때의 맨 위 상품명/가격)을 보관할 메모장
        var initialPageText: String = ""

        // 💡 위젯을 누를 때 호출되는 하이브리드 스냅샷
        fun getHybridSnapshot(): String {
            val service = instance ?: return ""
            val rootNode = service.rootInActiveWindow ?: return initialPageText
            
            val sb = java.lang.StringBuilder()
            extractText(rootNode, sb)
            
            // 현재 화면(2차 스냅샷) 압축
            val currentText = sb.toString().replace(Regex("\\s+"), " ").trim()
            
            // 💡 [처음 기억해둔 상단 정보] + [현재 눈에 보이는 화면]을 하나로 합침
            val combined = "페이지상단정보: $initialPageText | 현재보고있는부분: $currentText"
            
            // 토큰(비용) 방어를 위해 최종 글자 수를 1000자로 날카롭게 제한
            return combined.take(1000)
        }

        private fun extractText(node: AccessibilityNodeInfo?, sb: java.lang.StringBuilder) {
            if (node == null) return
            if (node.text != null) {
                sb.append(node.text).append(" ")
            } else if (node.contentDescription != null) {
                sb.append(node.contentDescription).append(" ")
            }
            for (i in 0 until node.childCount) {
                extractText(node.getChild(i), sb)
            }
        }
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // 💡 핵심: 스크롤할 때(CONTENT_CHANGED)는 무시하고, '새 창이 열릴 때'만 딱 한 번 동작
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val rootNode = rootInActiveWindow ?: return
            val sb = java.lang.StringBuilder()
            extractText(rootNode, sb)
            
            // 페이지 상단의 상품명/가격 위주로 앞부분 300자만 떼어서 기억해둠
            initialPageText = sb.toString().replace(Regex("\\s+"), " ").trim().take(300)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }
}
