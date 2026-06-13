package kr.deliveryprint.core.parser

import kr.deliveryprint.core.model.Order
import kr.deliveryprint.core.model.OrderType
import kr.deliveryprint.core.model.Platform

/** 한 배달 플랫폼의 알림을 [Order] 로 변환하는 파서. */
interface OrderParser {
    val platform: Platform

    /** 이 알림이 해당 플랫폼의 "새 주문" 알림인지 판단. */
    fun canParse(data: NotificationData): Boolean

    /** 주문 정보를 추출한다. 주문 알림이 아니면 null. */
    fun parse(data: NotificationData): Order?
}

/**
 * 라벨/패턴 기반 추출 유틸 모음.
 *
 * 실제 배달앱 알림 포맷은 앱 업데이트로 바뀔 수 있으므로, 각 플랫폼 파서는
 * 앱에 내장된 "알림 덤프" 화면으로 수집한 실제 샘플을 보고 정규식을 보정한다.
 */
internal object ParseUtils {
    /** "12,500원", "12500 원" 같은 금액에서 정수(원)만 추출. */
    private val AMOUNT_REGEX = Regex("""([0-9][0-9,]*)\s*원""")

    /** 라벨 뒤에 오는 값을 같은 줄에서 추출. 예: parseLabeled(text, "주문번호") */
    fun labeled(text: String, vararg labels: String): String? {
        for (label in labels) {
            val regex = Regex("""$label\s*[:：]?\s*(.+)""")
            regex.find(text)?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        return null
    }

    fun amountNear(text: String, vararg labels: String): Int? {
        for (label in labels) {
            val line = text.lineSequence().firstOrNull { it.contains(label) } ?: continue
            amount(line)?.let { return it }
        }
        return null
    }

    fun amount(text: String): Int? =
        AMOUNT_REGEX.find(text)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()

    fun detectOrderType(text: String): OrderType = when {
        text.contains("포장") || text.contains("테이크아웃") -> OrderType.TAKEOUT
        text.contains("배달") -> OrderType.DELIVERY
        else -> OrderType.UNKNOWN
    }

    /**
     * "메뉴명 x2", "메뉴명 2개", "2 메뉴명" 형태의 줄을 [OrderItem] 으로.
     * 매칭되지 않는 줄은 무시한다.
     */
    private val ITEM_PATTERNS = listOf(
        Regex("""^(.+?)\s*[xX×]\s*(\d+)\s*(?:개)?$"""),
        Regex("""^(.+?)\s+(\d+)\s*개$"""),
        Regex("""^(\d+)\s*[xX×]?\s*(.+)$"""),
    )

    fun parseItem(line: String): kr.deliveryprint.core.model.OrderItem? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        ITEM_PATTERNS[0].matchEntire(trimmed)?.let { m ->
            return kr.deliveryprint.core.model.OrderItem(m.groupValues[1].trim(), m.groupValues[2].toInt())
        }
        ITEM_PATTERNS[1].matchEntire(trimmed)?.let { m ->
            return kr.deliveryprint.core.model.OrderItem(m.groupValues[1].trim(), m.groupValues[2].toInt())
        }
        ITEM_PATTERNS[2].matchEntire(trimmed)?.let { m ->
            val qty = m.groupValues[1].toIntOrNull() ?: return null
            return kr.deliveryprint.core.model.OrderItem(m.groupValues[2].trim(), qty)
        }
        return null
    }
}
