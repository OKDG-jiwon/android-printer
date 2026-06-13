package kr.deliveryprint.core.printer

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

enum class Align(val code: Byte) { LEFT(0), CENTER(1), RIGHT(2) }

/** 용지 절단 명령 방식. 프린터 기종마다 지원 명령이 다르다. */
enum class CutMode {
    /** GS V : 표준 ESC/POS(대부분의 블루투스 영수증 프린터). */
    GS_V,

    /** ESC m : KICC 내부(내장) 프린터 등 일부 국산 프린터. */
    ESC_M,
}

/**
 * ESC/POS 명령 바이트열을 쌓는 빌더. 대부분의 영수증(58mm/80mm) 열전사 프린터가 지원하는
 * 표준 명령만 사용한다. 한글은 기본 EUC-KR 로 인코딩한다(국산 프린터 대부분이 EUC-KR 모드).
 */
class EscPos(charsetName: String = "EUC-KR") {
    private val charset: Charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
    private val out = ByteArrayOutputStream()

    /** ESC @ : 프린터 초기화. */
    fun init() = apply { out.write(byteArrayOf(0x1B, 0x40)) }

    /** ESC a n : 정렬. */
    fun align(align: Align) = apply { out.write(byteArrayOf(0x1B, 0x61, align.code)) }

    /** ESC E n : 강조(굵게). */
    fun bold(on: Boolean) = apply { out.write(byteArrayOf(0x1B, 0x45, (if (on) 1 else 0).toByte())) }

    /**
     * GS ! n : 문자 크기 배율(가로 [width], 세로 [height], 1~8).
     * 상위 니블 = 가로 배율-1, 하위 니블 = 세로 배율-1.
     */
    fun size(width: Int = 1, height: Int = 1) = apply {
        val w = (width.coerceIn(1, 8) - 1) shl 4
        val h = (height.coerceIn(1, 8) - 1)
        out.write(byteArrayOf(0x1D, 0x21, (w or h).toByte()))
    }

    fun text(s: String) = apply { out.write(s.toByteArray(charset)) }

    fun line(s: String = "") = apply { text(s); newline() }

    fun newline(count: Int = 1) = apply { repeat(count) { out.write(0x0A) } }

    /** ESC d n : n줄 피드. */
    fun feed(lines: Int) = apply { out.write(byteArrayOf(0x1B, 0x64, lines.toByte())) }

    /** GS V m : 용지 절단(0=완전 절단, 1=부분 절단). */
    fun cut(partial: Boolean = true) = apply {
        out.write(byteArrayOf(0x1D, 0x56, (if (partial) 1 else 0).toByte()))
    }

    /** [mode] 에 맞는 절단 명령을 쓴다. GS_V=부분 절단, ESC_M=ESC m. */
    fun cut(mode: CutMode) = apply {
        when (mode) {
            CutMode.GS_V -> out.write(byteArrayOf(0x1D, 0x56, 1))
            CutMode.ESC_M -> out.write(byteArrayOf(0x1B, 0x6D))
        }
    }

    /** ESC p : 캐시 드로어(금전함) 개방 펄스. */
    fun openCashDrawer() = apply { out.write(byteArrayOf(0x1B, 0x70, 0, 25, 25.toByte())) }

    fun bytes(): ByteArray = out.toByteArray()
}
