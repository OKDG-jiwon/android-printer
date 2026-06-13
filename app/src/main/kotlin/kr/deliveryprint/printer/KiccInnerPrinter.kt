package kr.deliveryprint.printer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 이지체크 TS-194N 등 KICC(한국정보통신) 단말기의 **내장 프린터** 출력.
 *
 * 단말의 KICC 데몬과 유닉스 도메인 소켓으로 통신해 [comPort] 에 연결된 프린터로 출력한다.
 * (프로토콜 구현은 [KiccUdsClient] 참고 — 제조사 "POS 장치설정" 앱을 분석해 재현했다.)
 *
 * @param comPort 출력 대상 포트. 내부(내장) 프린터는 "INTERNAL".
 *                "COM1"/"COM2" 등은 시리얼 포트(서명패드 등 주변기기).
 */
class KiccInnerPrinter(private val comPort: String = "INTERNAL") : Printer {

    private val client = KiccUdsClient()

    override val displayName: String = "내장 프린터 (KICC · $comPort)"

    override suspend fun print(bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!client.open()) {
                error(
                    "내장 프린터 데몬에 연결하지 못했습니다. " +
                        "이 단말이 KICC POS(이지체크)인지, 프린터 포트($comPort) 설정이 맞는지 확인하세요.",
                )
            }
            client.sendRs232(comPort, bytes)
        }
    }
}
