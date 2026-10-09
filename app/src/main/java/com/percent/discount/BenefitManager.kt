package com.percent.discount

data class PlatformBenefit(
    val name: String,
    val base: String,
    val membership: Map<String, String> = emptyMap(),
    val card: Map<String, String> = emptyMap(),
    val telecom: Map<String, String> = emptyMap(),
    val status: Map<String, String> = emptyMap()
)

object BenefitManager {
    // 기존 웹에서 쓰던 100% 동일한 공식 팩트 룰 데이터베이스
    val benefitDB = mapOf(
        "coupang" to PlatformBenefit(
            name = "쿠팡",
            base = "로켓배송 상품인지 확인하고, 장바구니 담기 후 정기배송(5~10% 할인) 가능 품목인지 살펴보세요.",
            membership = mapOf("wow" to "🚀 [와우회원 공식 혜택] 로켓배송 상품 100% 무료배송 및 와우 전용 즉시 할인가 적용."),
            card = mapOf(
                "kb" to "💳 [국민카드] KB국민 체크/신용카드 결제 시 연계 프로모션 할인 여부를 확인하세요.",
                "samsung" to "💳 [삼성카드] 쿠팡 제휴 및 결제 할인 이벤트 적용 대상인지 확인하세요."
            )
        ),
        "naver" to PlatformBenefit(
            name = "네이버쇼핑",
            base = "상품페이지 하단에 Npay 추가 적립 마크나 간편결제 이벤트 배너가 있는지 확인하세요.",
            membership = mapOf("naver" to "🟩 [네이버플러스 공식 혜택] 네이버페이로 결제 시 기본 최대 5% 추가 적립이 적용됩니다."),
            card = mapOf("hyundai" to "💳 [네이버 현대카드] 결제 시 상시 5% 네이버페이 추가 적립 혜택이 적용됩니다.")
        ),
        "11st" to PlatformBenefit(
            name = "11번가",
            base = "결제 직전 T멤버십 할인/적립 적용 버튼을 반드시 눌러 확인하세요.",
            telecom = mapOf("skt" to "📱 [SKT T멤버십] 결제 금액의 최대 11% 할인 또는 적립 혜택을 이용할 수 있습니다."),
            membership = mapOf("space" to "🪐 [T우주패스] 아마존 무료배송 및 11번가 쇼핑 포인트/쿠폰 혜택이 연동됩니다.")
        ),
        "musinsa" to PlatformBenefit(
            name = "무신사",
            base = "회원 등급별 상시 할인율과 장바구니 쿠폰, 적립금 선할인을 중복 적용해 보세요.",
            card = mapOf("hyundai" to "💳 [무신사 현대카드] 카드 결제 시 5% 청구 할인 혜택이 제공됩니다.")
        ),
        "oliveyoung" to PlatformBenefit(
            name = "올리브영",
            base = "당일 배송인 '오늘드림' 서비스와 매장 픽업 서비스의 가격 및 배송비를 비교해 보세요.",
            telecom = mapOf("kt" to "📱 [KT VIP/VVIP] 멤버십 포인트 차감으로 올리브영 1만원 할인 혜택을 활용할 수 있습니다.")
        ),
        "ssg" to PlatformBenefit(
            name = "SSG닷컴",
            base = "이마트 쓱배송 상품과 합배송 조건을 맞추어 배송비를 절약하세요.",
            membership = mapOf("universe" to "👑 [신세계 유니버스] 스마일클럽/유니버스 전용 5~7% 할인 쿠폰을 적용하세요.")
        ),
        "kurly" to PlatformBenefit(
            name = "마켓컬리",
            base = "카카오페이 또는 토스페이 간편결제 시 제공되는 선착순 즉시 할인 프로모션을 확인하세요."
        ),
        "common" to PlatformBenefit(
            name = "쇼핑몰",
            base = "신규 가입 웰컴 쿠폰이나 간편결제 즉시 할인 이벤트가 있는지 확인하세요.",
            status = mapOf(
                "military" to "🎖️ [군 복지] 군인 복지몰에 동일 상품이 면세가로 있는지 비교해 보세요.",
                "public" to "🏢 [공직자] 공무원연금매장 및 교직원공제회 복지몰 가격과 비교해 보세요.",
                "student" to "🎓 [학생] 학생복지스토어나 공식 교육할인몰 가격인지 확인하세요."
            )
        )
    )

    fun detectPlatform(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("coupang") || lower.contains("쿠팡") -> "coupang"
            lower.contains("naver") || lower.contains("네이버") -> "naver"
            lower.contains("11st") || lower.contains("11번가") -> "11st"
            lower.contains("musinsa") || lower.contains("무신사") -> "musinsa"
            lower.contains("oliveyoung") || lower.contains("올리브영") -> "oliveyoung"
            lower.contains("ssg") || lower.contains("쓱") -> "ssg"
            lower.contains("kurly") || lower.contains("컬리") -> "kurly"
            else -> "common"
        }
    }
}
