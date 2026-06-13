package kr.deliveryprint.core.printer

import org.junit.Assert.assertEquals
import org.junit.Test

class EscPosTest {

    private fun ByteArray.ints(): List<Int> = map { it.toInt() and 0xFF }

    @Test
    fun `init 은 ESC @ 를 낸다`() {
        assertEquals(listOf(0x1B, 0x40), EscPos().init().bytes().ints())
    }

    @Test
    fun `정렬 명령 바이트`() {
        assertEquals(listOf(0x1B, 0x61, 0x01), EscPos().align(Align.CENTER).bytes().ints())
        assertEquals(listOf(0x1B, 0x61, 0x02), EscPos().align(Align.RIGHT).bytes().ints())
    }

    @Test
    fun `문자 크기 배율은 GS ! 로 인코딩된다`() {
        // 가로2 세로2 -> 상위니블 1, 하위니블 1 -> 0x11
        assertEquals(listOf(0x1D, 0x21, 0x11), EscPos().size(2, 2).bytes().ints())
        // 가로1 세로1 -> 0x00
        assertEquals(listOf(0x1D, 0x21, 0x00), EscPos().size(1, 1).bytes().ints())
    }

    @Test
    fun `한글은 EUC-KR 로 인코딩된다`() {
        // "가" = 0xB0 0xA1 (EUC-KR)
        assertEquals(listOf(0xB0, 0xA1), EscPos("EUC-KR").text("가").bytes().ints())
    }

    @Test
    fun `부분 절단 명령 바이트`() {
        assertEquals(listOf(0x1D, 0x56, 0x01), EscPos().cut(partial = true).bytes().ints())
        assertEquals(listOf(0x1D, 0x56, 0x00), EscPos().cut(partial = false).bytes().ints())
    }
}
