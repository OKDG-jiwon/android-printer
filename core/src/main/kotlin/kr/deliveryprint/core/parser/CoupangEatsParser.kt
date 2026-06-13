package kr.deliveryprint.core.parser

import kr.deliveryprint.core.model.Order
import kr.deliveryprint.core.model.Platform

/**
 * 쿠팡이츠(스토어/사장님) 알림 파서.
 *
 * 패키지명은 앱 버전에 따라 다를 수 있어 본문 키워드("쿠팡이츠")로도 판별한다.
 * 실제 패키지명은 앱의 "알림 덤프" 화면에서 확인 후 [KNOWN_PACKAGES] 에 추가한다.
 */
class CoupangEatsParser : OrderParser {
    override val platform = Platform.COUPANG_EATS

    override fun canParse(data: NotificationData): Boolean {
        if (data.packageName in KNOWN_PACKAGES) return true
        val text = data.combinedText()
        return KEYWORDS.any { text.contains(it) } && ORDER_HINTS.any { text.contains(it) }
    }

    override fun parse(data: NotificationData): Order? {
        if (!canParse(data)) return null
        val text = data.combinedText()
        val items = text.lineSequence().mapNotNull(ParseUtils::parseItem).toList()
        return Order(
            platform = platform,
            orderType = ParseUtils.detectOrderType(text),
            storeName = ParseUtils.labeled(text, "가게", "매장", "상호"),
            orderNumber = ParseUtils.labeled(text, "주문번호", "주문 번호"),
            items = items,
            totalAmount = ParseUtils.amountNear(text, "결제금액", "결제 금액", "총금액", "합계", "결제"),
            customerRequest = ParseUtils.labeled(text, "요청사항", "요청 사항"),
            deliveryAddress = ParseUtils.labeled(text, "주소", "배달주소", "배달 주소"),
            phoneNumber = ParseUtils.labeled(text, "연락처", "전화", "전화번호"),
            rawText = text,
            receivedAt = data.postedAt,
        )
    }

    companion object {
        /** 확인되면 추가: 쿠팡이츠 사장님 앱 패키지명. */
        val KNOWN_PACKAGES = setOf("com.coupang.mobile.eats.merchant", "com.coupang.eats.store")
        private val KEYWORDS = listOf("쿠팡이츠", "쿠팡 이츠")
        private val ORDER_HINTS = listOf("주문", "접수", "신규")
    }
}
