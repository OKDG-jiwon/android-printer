package kr.deliveryprint.core.printer

import kr.deliveryprint.core.model.Order
import kr.deliveryprint.core.model.OrderType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 영수증 출력 설정. */
data class ReceiptConfig(
    /** 한 줄에 들어가는 ASCII 문자 수(58mm≈32, 80mm≈48). */
    val widthChars: Int = 32,
    val charsetName: String = "EUC-KR",
    val cutPaper: Boolean = true,
    val openCashDrawer: Boolean = false,
    val zoneId: String = "Asia/Seoul",
)

/**
 * [Order] 를 ESC/POS 바이트열로 변환한다.
 * 구조화 필드가 비어 있으면 [Order.rawText] 를 그대로 출력하는 폴백을 사용한다.
 */
class ReceiptFormatter(private val config: ReceiptConfig = ReceiptConfig()) {

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of(config.zoneId))

    fun format(order: Order): ByteArray {
        val e = EscPos(config.charsetName).init()

        // 헤더: 플랫폼 + 주문 유형
        e.align(Align.CENTER).bold(true).size(2, 2)
            .line(order.platform.displayName)
            .size(1, 1)
        if (order.orderType != OrderType.UNKNOWN) {
            e.line("[ ${order.orderType.displayName} 주문 ]")
        } else {
            e.line("새 주문")
        }
        e.bold(false).align(Align.LEFT).line(divider())

        if (hasStructuredData(order)) {
            order.orderNumber?.let { e.line("주문번호: $it") }
            order.storeName?.let { e.line("가게: $it") }
            if (order.items.isNotEmpty()) {
                e.line(divider())
                e.bold(true).line("주문 내역").bold(false)
                order.items.forEach { item ->
                    val left = "- ${item.name} x${item.quantity}"
                    val right = item.price?.let { "${comma(it)}원" } ?: ""
                    e.line(padBetween(left, right))
                }
            }
            e.line(divider())
            order.totalAmount?.let {
                e.bold(true).size(1, 2).line(padBetween("합계", "${comma(it)}원")).size(1, 1).bold(false)
            }
            order.deliveryAddress?.let { e.line("주소: $it") }
            order.phoneNumber?.let { e.line("연락처: $it") }
            order.customerRequest?.let {
                e.line(divider())
                e.bold(true).line("요청사항").bold(false).line(it)
            }
        } else {
            // 폴백: 알림 원문 그대로 출력
            e.line(order.rawText.ifBlank { "(주문 내용 없음)" })
        }

        e.line(divider())
        if (order.receivedAt > 0) {
            e.align(Align.RIGHT).line("수신 ${timeFormatter.format(Instant.ofEpochMilli(order.receivedAt))}")
                .align(Align.LEFT)
        }

        e.feed(3)
        if (config.openCashDrawer) e.openCashDrawer()
        if (config.cutPaper) e.cut(partial = true)
        return e.bytes()
    }

    private fun hasStructuredData(order: Order): Boolean =
        order.items.isNotEmpty() || order.totalAmount != null ||
            order.orderNumber != null || order.deliveryAddress != null

    private fun divider(): String = "-".repeat(config.widthChars)

    private fun comma(value: Int): String = "%,d".format(value)

    /** 좌측/우측 문자열을 줄 너비에 맞춰 양끝 정렬(한글 2칸 폭 고려). */
    private fun padBetween(left: String, right: String): String {
        if (right.isEmpty()) return left
        val used = displayWidth(left) + displayWidth(right)
        val gap = (config.widthChars - used).coerceAtLeast(1)
        return left + " ".repeat(gap) + right
    }

    private fun displayWidth(s: String): Int = s.sumOf { if (isWide(it)) 2 else 1 }

    private fun isWide(c: Char): Boolean {
        val code = c.code
        return code in 0x1100..0x11FF ||   // 한글 자모
            code in 0x3000..0x303F ||       // CJK 기호
            code in 0x3130..0x318F ||       // 한글 호환 자모
            code in 0x4E00..0x9FFF ||       // CJK 한자
            code in 0xAC00..0xD7A3 ||       // 한글 음절
            code in 0xFF00..0xFFEF          // 전각 형태
    }
}
