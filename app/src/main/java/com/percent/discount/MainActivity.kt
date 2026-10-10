// 위젯 켜기 버튼 (예: widgetStartButton) 클릭 리스너 설정
widgetStartButton.setOnClickListener {
    // 1. 위젯 서비스 시작
    val intent = Intent(this, OverlayService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent)
    } else {
        startService(intent)
    }
    
    // 2. 실행 즉시 앱 화면을 바탕화면 뒤로 부드럽게 숨김 (최소화)
    moveTaskToBack(true)
}

// 💡 (참고) 프로필 저장 버튼은 별개의 클릭 리스너(saveButton.setOnClickListener)로 분리해두면 됩니다.
