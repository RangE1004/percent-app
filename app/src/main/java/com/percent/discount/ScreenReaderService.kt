package com.percent.discount

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ScreenReaderService : AccessibilityService() {

    companion object {
        var instance: ScreenReaderService? = null
        var initialPageText: String = ""

        fun getHybridSnapshot(): String {
            val service = instance ?: return ""
            val rootNode = service.rootInActiveWindow ?: return initialPageText
            
            val sb = java.lang.StringBuilder()
            extractText(rootNode, sb)
            
            val currentText = sb.toString().replace(Regex("\\s+"), " ").trim()
            val combined = "페이지상단정보: $initialPageText | 현재보고있는부분: $currentText"
            
            // 💡 쿠폰 정보를 더 많이 담기 위해 1000자 -> 2000자로 확장
            return combined.take(2000)
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
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val rootNode = rootInActiveWindow ?: return
            val sb = java.lang.StringBuilder()
            extractText(rootNode, sb)
            // 💡 초기 상품명도 더 많이 기억하도록 500자로 확장
            initialPageText = sb.toString().replace(Regex("\\s+"), " ").trim().take(500)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }
}
