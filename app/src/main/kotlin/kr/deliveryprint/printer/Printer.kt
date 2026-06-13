package kr.deliveryprint.printer

/** 영수증 출력 장치 추상화. ESC/POS 바이트열을 받아 출력한다. */
interface Printer {
    /** 미리 만들어진 ESC/POS 바이트열을 출력. */
    suspend fun print(bytes: ByteArray): Result<Unit>

    /** 사람이 읽을 수 있는 장치 이름(설정 화면 표시용). */
    val displayName: String
}
