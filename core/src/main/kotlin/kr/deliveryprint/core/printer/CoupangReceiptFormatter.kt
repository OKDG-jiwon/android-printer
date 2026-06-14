package kr.deliveryprint.core.printer

import kr.deliveryprint.core.model.Order
import kr.deliveryprint.core.model.Platform
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 실제 쿠팡이츠 영수증(고객용) 레이아웃을 그대로 재현하는 포맷터.
 *
 * 실물 영수증 사진 분석 기반:
 * ```
 *  coupang eats              [고객용]
 *            2EC36P                       ← 주문번호 (2배, 가운데)
 *          [수저포크O]                     ← 수저포크/요청 (가운데, 더 큼)
 *  메뉴              수량    금액
 *  --------------------------------       ← 대시 구분선(폭 전체)
 *  1인분 완전고기만       1   15,500       ← 메뉴명 굵게
 *  + 삼겹살              1       0         ← 옵션
 *  ································        ← 닷 구분선(폭 전체)
 *  주문금액                   16,500
 *  배달비                         0
 *  ································
 *  카드결제                   16,500       ← 결제수단 있을 때만
 *  ································
 *  총결제금액                 16,500       ← 2배 높이 굵게
 *
 *  거래일시: ...
 *  주문매장: ...
 *  결제방식: ...
 *        쿠팡이츠 고객센터 1670-9827
 * ```
 * 글자 모양은 프린터 내장 폰트로 찍히며, 여기선 크기(GS!)·굵기(ESC E)·정렬·구분선만 제어한다.
 */
class CoupangReceiptFormatter(private val config: ReceiptConfig = ReceiptConfig()) {

    // KICC 내부 프린터 실측 폭 = 42칸(제조사 프린터 테스트 패턴이 한 줄 42자). 가로 꽉 차게 채운다.
    private val width = 42
    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of(config.zoneId))

    fun format(order: Order): ByteArray {
        val e = EscPos(config.charsetName).init()
        e.newline(2) // 상단 여백(잘림 방지)

        // 헤더: coupang eats ............ [고객용]
        val brand = if (order.platform == Platform.COUPANG_EATS) "coupang eats" else order.platform.displayName
        e.bold(true).text(brand).bold(false)
        e.text(padLeftTo("[고객용]", width - displayWidth(brand))).newline()

        // 주문번호 (2배 크기, 가운데)
        order.orderNumber?.takeIf { it.isNotBlank() }?.let {
            e.newline()
            e.align(Align.CENTER).bold(true).size(2, 2).line(it)
                .size(1, 1).bold(false).align(Align.LEFT)
        }

        // [수저포크X] + 요청사항 (가운데, 2배 높이, 줄바꿈으로 안 잘림)
        order.customerRequest?.takeIf { it.isNotBlank() }?.let { note ->
            e.align(Align.CENTER).bold(true).size(1, 2)
            wrap(note, width).forEach { e.line(it) }
            e.size(1, 1).bold(false).align(Align.LEFT)
        }

        e.newline()

        // 메뉴 헤더 + 대시 구분선(폭 전체)
        e.line(cols("메뉴", "수량    금액"))
        e.line("-".repeat(width))

        // 메뉴 + 옵션
        order.items.forEach { item ->
            printMenuItem(e, item.name, item.quantity, item.price)
            item.options.forEach { opt ->
                wrap("+ $opt", width).forEach { e.line(it) }
            }
        }

        // 금액부 (닷 구분선)
        e.line(dotLine())
        order.subtotal?.let { e.line(cols("주문금액", comma(it))) }
        order.deliveryFee?.let { e.line(cols("배달비", comma(it))) }
        order.paymentMethod?.takeIf { it.isNotBlank() }?.let { pm ->
            e.line(dotLine())
            e.line(cols(pm, order.totalAmount?.let { comma(it) } ?: ""))
        }
        e.line(dotLine())
        order.totalAmount?.let {
            e.bold(true).size(1, 2).line(cols("총결제금액", comma(it))).size(1, 1).bold(false)
        }

        // 하단 정보
        e.newline()
        if (order.receivedAt > 0) {
            e.line("거래일시: ${timeFormatter.format(Instant.ofEpochMilli(order.receivedAt))}")
        }
        order.storeName?.let { e.line("주문매장: $it") }
        order.paymentMethod?.takeIf { it.isNotBlank() }?.let { e.line("결제방식: $it") }

        // 푸터
        e.newline()
        e.align(Align.CENTER).line("쿠팡이츠 고객센터 1670-9827").align(Align.LEFT)

        // 하단 피드 — ESC d는 이 프린터가 무시하므로 순수 LF(줄바꿈)로 먹인다(제조사 앱과 동일).
        e.newline(12)
        if (config.openCashDrawer) e.openCashDrawer()
        if (config.cutPaper) e.cut(config.cutMode)
        return e.bytes()
    }

    /**
     * 메뉴 한 줄. 금액은 항상 첫 줄에 메뉴명과 함께 출력하고,
     * 이름이 길면 첫 줄에 들어가는 만큼만 넣고 **나머지 이름만** 아래 줄로 흘린다(금액은 절대 다음 줄로 안 넘김).
     */
    private fun printMenuItem(e: EscPos, name: String, qty: Int, price: Int?) {
        e.bold(true)
        val right = qtyPrice(qty, price)
        val firstWidth = width - displayWidth(right) - 1
        val head = truncate(name, firstWidth)
        e.line(cols(head, right))
        val tail = name.substring(head.length).trimStart()
        if (tail.isNotEmpty()) wrap(tail, width).forEach { e.line(it) }
        e.bold(false)
    }

    /** 표시폭 기준 [maxW] 칸까지만 잘라낸다(글자 경계). */
    private fun truncate(s: String, maxW: Int): String {
        if (displayWidth(s) <= maxW) return s
        val sb = StringBuilder()
        var w = 0
        for (ch in s) {
            val cw = if (isWide(ch)) 2 else 1
            if (w + cw > maxW) break
            sb.append(ch); w += cw
        }
        return sb.toString()
    }

    /** 수량 + 금액 우측 정렬 블록(칸 고정으로 행 간 정렬). */
    private fun qtyPrice(qty: Int, price: Int?): String =
        qty.toString().padStart(2) + (price?.let { comma(it) } ?: "").padStart(7)

    /** 좌측 텍스트 + 우측 텍스트를 폭에 맞춰 양끝 정렬(한글 2칸 고려). */
    private fun cols(left: String, right: String): String {
        val gap = width - displayWidth(left) - displayWidth(right)
        return if (gap >= 1) left + " ".repeat(gap) + right else "$left $right"
    }

    /** [s] 를 [totalWidth] 폭 안에서 우측 정렬. */
    private fun padLeftTo(s: String, totalWidth: Int): String {
        val pad = totalWidth - displayWidth(s)
        return if (pad >= 1) " ".repeat(pad) + s else " $s"
    }

    // 중간점(·)은 프린터 EUC-KR 폰트에서 안 찍히는 경우가 있어 ASCII 마침표로 점선을 만든다.
    private fun dotLine(): String = ".".repeat(width)

    private fun comma(value: Int): String = "%,d".format(value)

    /** 표시폭(한글 2칸) 기준으로 [w] 칸을 넘지 않게 줄바꿈. */
    private fun wrap(text: String, w: Int): List<String> {
        val out = mutableListOf<String>()
        val line = StringBuilder()
        var lw = 0
        for (ch in text) {
            val cw = if (isWide(ch)) 2 else 1
            if (lw + cw > w && line.isNotEmpty()) {
                out.add(line.toString()); line.setLength(0); lw = 0
            }
            line.append(ch); lw += cw
        }
        if (line.isNotEmpty()) out.add(line.toString())
        return out.ifEmpty { listOf(text) }
    }

    private fun displayWidth(s: String): Int = s.fold(0) { acc, c -> acc + if (isWide(c)) 2 else 1 }

    private fun isWide(c: Char): Boolean {
        val code = c.code
        return code in 0x1100..0x11FF ||
            code in 0x3000..0x303F ||
            code in 0x3130..0x318F ||
            code in 0x4E00..0x9FFF ||
            code in 0xAC00..0xD7A3 ||
            code in 0xFF00..0xFFEF
    }
}
