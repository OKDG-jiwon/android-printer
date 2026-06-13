package kr.deliveryprint.printer

/**
 * 이지체크 TS-194N 등 KICC(한국정보통신) 단말기의 **내장 프린터** 출력 자리.
 *
 * 내장 프린터는 제조사 전용 SDK/Intent API 로만 접근할 수 있고 공개 문서가 없다.
 * KICC/공급처에서 SDK를 확보하면:
 *   1) SDK(aar/jar)를 app/libs 에 추가하고 build.gradle 의존성에 등록,
 *   2) 아래 [print] 에서 SDK의 프린터 객체로 [bytes] 또는 텍스트를 출력하도록 구현.
 *
 * 그 전까지는 명확한 실패를 반환해 블루투스 프린터로 폴백하도록 한다.
 */
class KiccInnerPrinter : Printer {

    override val displayName: String = "내장 프린터 (KICC SDK 미연동)"

    override suspend fun print(bytes: ByteArray): Result<Unit> =
        Result.failure(
            UnsupportedOperationException(
                "내장 프린터는 KICC SDK 연동이 필요합니다. " +
                    "SDK 확보 전까지는 설정에서 '블루투스 프린터'를 사용하세요.",
            ),
        )
}
