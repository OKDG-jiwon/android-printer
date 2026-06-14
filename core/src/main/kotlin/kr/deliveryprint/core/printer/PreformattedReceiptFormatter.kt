package kr.deliveryprint.core.printer

/**
 * 이미 프린터 폭(42칸)에 맞춰 재배치된 텍스트를 ESC/POS 로 출력한다.
 *
 * 배민처럼 API 를 따라할 수 없는 플랫폼은 PC 클라이언트가 뽑은 PDF 를 서버가
 * `pdftotext -layout` → 42칸 재배치한 텍스트로 변환한다. 그 결과는 이미 완성된
 * 영수증 레이아웃이므로, 여기서는 제어 바이트(init/인코딩/강조/하단 피드/절단)만 붙인다.
 *
 * 줄 머리의 스타일 접두어(서버 reflow 가 부여):
 *   - [BIG] ()  = 세로 2배 + 굵게 (제목/메뉴명/총결제금액)
 *   - [BOLD] () = 굵게 (주문금액/배달팁)
 *   - 접두어 없음      = 보통
 * 가로 배율은 1배로 두어 42칸 정렬을 유지한다.
 *
 * 내장 KICC 프린터 주의점(쿠팡 포맷터와 동일):
 *   - 하단 피드는 `ESC d` 가 무시되므로 순수 LF(\n) 반복으로 먹인다.
 *   - 절단은 [CutMode]. 내장 프린터는 ESC m.
 */
class PreformattedReceiptFormatter(private val config: ReceiptConfig = ReceiptConfig()) {

    fun format(text: String): ByteArray {
        val e = EscPos(config.charsetName).init()
        e.newline(2) // 상단 여백(잘림 방지)
        text.split("\n").forEach { raw ->
            when {
                raw.startsWith(BIG) ->
                    e.bold(true).size(1, 2).line(raw.substring(1)).size(1, 1).bold(false)
                raw.startsWith(BOLD) ->
                    e.bold(true).line(raw.substring(1)).bold(false)
                else -> e.line(raw)
            }
        }
        e.newline(4) // 하단 피드(절단 여백) — ESC d 무시되므로 LF 반복. 절단날 클리어 최소치.
        if (config.openCashDrawer) e.openCashDrawer()
        if (config.cutPaper) e.cut(config.cutMode)
        return e.bytes()
    }

    companion object {
        const val BIG = '\u0001'   // 세로 2배 + 굵게
        const val BOLD = '\u0002'  // 굵게
    }
}
