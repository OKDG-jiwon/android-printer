package kr.deliveryprint.core.printer

import kr.deliveryprint.core.model.Order
import kr.deliveryprint.core.model.OrderItem
import kr.deliveryprint.core.model.OrderType
import kr.deliveryprint.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptFormatterTest {

    private val formatter = ReceiptFormatter(ReceiptConfig(widthChars = 32))

    /** EUC-KR 바이트열을 사람이 읽을 수 있는 문자열로 디코딩(검증용). */
    private fun ByteArray.decode(): String = String(this, charset("EUC-KR"))

    private val sampleOrder = Order(
        platform = Platform.BAEMIN,
        orderType = OrderType.DELIVERY,
        orderNumber = "B123456",
        storeName = "홍콩반점",
        items = listOf(OrderItem("아메리카노", 2, 4_000), OrderItem("짜장면", 1)),
        totalAmount = 23_000,
        deliveryAddress = "서울시 강남구 테헤란로 1",
        phoneNumber = "010-1234-5678",
        customerRequest = "단무지 많이 주세요",
        receivedAt = 1_700_000_000_000L,
    )

    @Test
    fun `init 으로 시작하고 cut 으로 끝난다`() {
        val bytes = formatter.format(sampleOrder)
        val head = bytes.copyOfRange(0, 2).map { it.toInt() and 0xFF }
        val tail = bytes.copyOfRange(bytes.size - 3, bytes.size).map { it.toInt() and 0xFF }
        assertEquals(listOf(0x1B, 0x40), head)         // ESC @
        assertEquals(listOf(0x1D, 0x56, 0x01), tail)   // GS V 1 (부분 절단)
    }

    @Test
    fun `영수증에 주문 핵심 정보가 포함된다`() {
        val text = formatter.format(sampleOrder).decode()
        assertTrue(text.contains("배달의민족"))
        assertTrue(text.contains("배달 주문"))
        assertTrue(text.contains("B123456"))
        assertTrue(text.contains("홍콩반점"))
        assertTrue(text.contains("아메리카노"))
        assertTrue(text.contains("합계"))
        assertTrue(text.contains("23,000원"))
        assertTrue(text.contains("단무지 많이 주세요"))
    }

    @Test
    fun `수신 시각은 KST 로 출력된다`() {
        // 1_700_000_000_000ms = 2023-11-15 07:13:20 KST
        val text = formatter.format(sampleOrder).decode()
        assertTrue(text.contains("2023-11-15"))
    }

    @Test
    fun `구조화 정보가 없으면 원문을 폴백 출력한다`() {
        val order = Order(platform = Platform.UNKNOWN, rawText = "새 주문 도착")
        val text = formatter.format(order).decode()
        assertTrue(text.contains("새 주문 도착"))
    }
}
