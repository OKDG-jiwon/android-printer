package kr.deliveryprint.core.parser

import kr.deliveryprint.core.model.OrderType
import kr.deliveryprint.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderParsersTest {

    private val parsers = OrderParsers()

    private fun baeminNotification() = NotificationData(
        packageName = "com.unknown.app",
        title = "배달의민족",
        text = "신규 주문이 접수되었습니다",
        bigText = """
            배달 주문
            주문번호: B123456
            가게: 홍콩반점
            아메리카노 x2
            짜장면 x1
            결제금액: 23,000원
            주소: 서울시 강남구 테헤란로 1
            연락처: 010-1234-5678
            요청사항: 단무지 많이 주세요
        """.trimIndent(),
        postedAt = 1_700_000_000_000L,
    )

    @Test
    fun `배민 주문 알림을 구조화 파싱한다`() {
        val order = parsers.parse(baeminNotification())
        assertNotNull(order)
        requireNotNull(order)
        assertEquals(Platform.BAEMIN, order.platform)
        assertEquals(OrderType.DELIVERY, order.orderType)
        assertEquals("B123456", order.orderNumber)
        assertEquals("홍콩반점", order.storeName)
        assertEquals(23_000, order.totalAmount)
        assertEquals("서울시 강남구 테헤란로 1", order.deliveryAddress)
        assertEquals("010-1234-5678", order.phoneNumber)
        assertEquals("단무지 많이 주세요", order.customerRequest)
        assertEquals(2, order.items.size)
        assertEquals("아메리카노", order.items[0].name)
        assertEquals(2, order.items[0].quantity)
        assertEquals("짜장면", order.items[1].name)
        assertEquals(1, order.items[1].quantity)
    }

    @Test
    fun `쿠팡이츠 포장 주문을 인식한다`() {
        val data = NotificationData(
            packageName = "com.unknown.app",
            title = "쿠팡이츠",
            bigText = "포장 신규 주문\n주문번호: CE-77\n결제금액: 8,500원",
            postedAt = 1L,
        )
        val order = parsers.parse(data)
        assertNotNull(order)
        requireNotNull(order)
        assertEquals(Platform.COUPANG_EATS, order.platform)
        assertEquals(OrderType.TAKEOUT, order.orderType)
        assertEquals("CE-77", order.orderNumber)
        assertEquals(8_500, order.totalAmount)
    }

    @Test
    fun `알려진 패키지명이면 본문 없이도 인식한다`() {
        val data = NotificationData(
            packageName = BaeminParser.KNOWN_PACKAGES.first(),
            title = "알림",
            text = "확인하세요",
        )
        assertTrue(parsers.isOrderNotification(data))
    }

    @Test
    fun `배달 주문이 아닌 알림은 무시한다`() {
        val data = NotificationData(
            packageName = "com.kakao.talk",
            title = "카카오톡",
            text = "새 메시지가 도착했습니다",
        )
        assertFalse(parsers.isOrderNotification(data))
        assertNull(parsers.parse(data))
    }
}
