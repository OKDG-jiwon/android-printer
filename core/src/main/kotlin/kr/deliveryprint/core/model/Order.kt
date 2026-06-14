package kr.deliveryprint.core.model

/** 주문이 들어온 배달 플랫폼. */
enum class Platform(val displayName: String) {
    BAEMIN("배달의민족"),
    COUPANG_EATS("쿠팡이츠"),
    UNKNOWN("기타"),
}

/** 배달 / 포장 구분. */
enum class OrderType(val displayName: String) {
    DELIVERY("배달"),
    TAKEOUT("포장"),
    UNKNOWN(""),
}

/** 주문에 포함된 메뉴 한 줄. */
data class OrderItem(
    val name: String,
    val quantity: Int = 1,
    val price: Int? = null,
    /** 옵션명 목록(예: "ICE", "덜 달게"). 영수증에 "+ 옵션" 줄로 표시. */
    val options: List<String> = emptyList(),
)

/**
 * 알림에서 파싱한 주문 정보.
 *
 * 사장님 앱 알림은 정보가 제한적일 수 있으므로 대부분의 필드는 nullable 이며,
 * 구조화 파싱이 실패하면 [rawText] 를 그대로 영수증에 출력하는 폴백을 사용한다.
 */
data class Order(
    val platform: Platform,
    val orderType: OrderType = OrderType.UNKNOWN,
    val storeName: String? = null,
    val orderNumber: String? = null,
    val items: List<OrderItem> = emptyList(),
    /** 주문금액(메뉴 합계, 배달비 제외). */
    val subtotal: Int? = null,
    /** 배달비. */
    val deliveryFee: Int? = null,
    /** 최종 결제금액. */
    val totalAmount: Int? = null,
    /** 결제수단 표시문구(예: "쿠페이 선결제 완료"). 없으면 결제 줄 생략. */
    val paymentMethod: String? = null,
    val customerRequest: String? = null,
    val deliveryAddress: String? = null,
    val phoneNumber: String? = null,
    val rawText: String = "",
    /**
     * 이미 프린터 폭(42칸)에 맞춰 재배치된 영수증 본문. null 이 아니면
     * 구조화 포맷터를 건너뛰고 이 텍스트를 그대로 출력한다(배민 PDF 변환 결과 등).
     */
    val preformattedText: String? = null,
    val receivedAt: Long = 0L,
)
