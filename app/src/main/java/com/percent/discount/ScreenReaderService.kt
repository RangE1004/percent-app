package com.percent.discount

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ScreenReaderService : AccessibilityService() {

    // 다른 파일(오버레이 등)에서 읽은 글자를 가져다 쓸 수 있게 저장하는 공간
    companion object {
        var currentScreenText: String = "아직 읽은 텍스트가 없습니다."
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return
        val textBuilder = StringBuilder()
        
        extractText(rootNode, textBuilder)
        
        val detectedText = textBuilder.toString().trim()
        if (detectedText.isNotEmpty()) {
            currentScreenText = detectedText
        }
    }

    // 화면의 모든 텍스트 노드를 파고들면서 글자를 긁어모으는 함수
    private fun extractText(node: AccessibilityNodeInfo?, builder: StringBuilder) {
        if (node == null) return
        
        if (!node.text.isNullOrBlank()) {
            builder.append(node.text).append("\n")
        }
        if (!node.contentDescription.isNullOrBlank()) {
            builder.append(node.contentDescription).append("\n")
        }
        
        for (i in 0 until node.childCount) {
            extractText(node.getChild(i), builder)
        }
    }

    override fun onInterrupt() {}
}
