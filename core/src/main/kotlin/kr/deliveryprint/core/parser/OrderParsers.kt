package kr.deliveryprint.core.parser

import kr.deliveryprint.core.model.Order

/**
 * 등록된 모든 [OrderParser] 를 순회하며 알림을 주문으로 변환하는 진입점.
 * 새 플랫폼을 추가하려면 [DEFAULT] 목록에 파서를 넣으면 된다.
 */
class OrderParsers(private val parsers: List<OrderParser> = DEFAULT) {

    /** 어떤 파서든 처리 가능한 알림이면 첫 번째 결과를 반환, 아니면 null. */
    fun parse(data: NotificationData): Order? =
        parsers.firstOrNull { it.canParse(data) }?.parse(data)

    fun isOrderNotification(data: NotificationData): Boolean =
        parsers.any { it.canParse(data) }

    companion object {
        val DEFAULT: List<OrderParser> = listOf(
            BaeminParser(),
            CoupangEatsParser(),
        )
    }
}
