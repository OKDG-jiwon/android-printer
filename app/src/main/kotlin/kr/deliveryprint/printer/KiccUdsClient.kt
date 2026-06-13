package kr.deliveryprint.printer

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import java.io.IOException

/**
 * KICC(한국정보통신) POS 단말 — 이지체크 TS-194N 등 — 의 **내장 프린터 데몬**과 통신하는 최소 클라이언트.
 *
 * 단말에는 프린터/리더 등 하드웨어를 관장하는 KICC 데몬이 돌고 있고,
 * **유닉스 도메인 소켓(FILESYSTEM)** 으로 명령을 받는다.
 *   - 소켓 경로: API < 30 → `/Flash/hkjt_buffer`, API ≥ 30 → `/tmp/hkjt_buffer`
 *
 * 와이어 프레임(매 메시지):
 * ```
 *   [4B ASCII 길이 = %04d(8 + payload)] ["1001" SrcId] ["0000" DestId] [payload]
 * ```
 * payload 종류:
 *   - 연결 직후 등록: `{ 'A' }`
 *   - RS232(프린터) 출력: `'S'` + 포트명(여러 개면 0x1D 구분) + `0x1C` + 데이터
 *
 * (제조사 "POS 장치설정" 앱의 `kicc.module.KiccPos.KReqSendRS232` 동작을 그대로 재현한 것.)
 */
class KiccUdsClient {

    private var socket: LocalSocket? = null

    val isOpen: Boolean get() = socket?.isConnected == true

    /** 데몬 소켓에 연결하고 등록 메시지('A')를 보낸다. 성공 시 true. */
    @Synchronized
    fun open(): Boolean {
        if (isOpen) return true
        val path = if (Build.VERSION.SDK_INT >= 30) PATH_API30 else PATH_LEGACY
        return try {
            val s = LocalSocket()
            s.connect(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM))
            socket = s
            writeFrame(byteArrayOf(REGISTER)) // KSendMessageID 등가
            true
        } catch (e: IOException) {
            runCatching { socket?.close() }
            socket = null
            false
        }
    }

    @Synchronized
    fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    /**
     * 지정 COM 포트로 프린터(RS232) 데이터를 전송한다.
     * [port] 가 비어 있으면 "0"(기본 프린터)으로 보낸다. [data] 는 완성된 ESC/POS 바이트열.
     */
    @Synchronized
    fun sendRs232(port: String, data: ByteArray) {
        val portBytes = port.ifBlank { "0" }.toByteArray(Charsets.US_ASCII)
        // routing = 'S' + port + 0x1C
        val routing = ByteArray(1 + portBytes.size + 1)
        routing[0] = SEND_RS232
        System.arraycopy(portBytes, 0, routing, 1, portBytes.size)
        routing[routing.size - 1] = FS
        val full = routing + data

        if (full.size <= PAGE) {
            writeFrame(full)
            return
        }
        // 8000바이트 초과 시 데몬 규약대로 페이지 분할(연속 페이지는 routing 헤더를 다시 붙임).
        var offset = 0
        var first = true
        while (offset < full.size) {
            val end = minOf(offset + PAGE, full.size)
            val chunk = full.copyOfRange(offset, end)
            writeFrame(if (first) chunk else routing + chunk)
            first = false
            offset = end
        }
    }

    private fun writeFrame(payload: ByteArray) {
        val s = socket ?: throw IOException("KICC 소켓이 열려있지 않습니다")
        val len = 8 + payload.size // SrcId(4) + DestId(4) + payload
        val frame = ByteArray(12 + payload.size)
        val lenStr = String.format("%04d", len).toByteArray(Charsets.US_ASCII)
        System.arraycopy(lenStr, 0, frame, 0, 4)
        System.arraycopy(SRC_ID, 0, frame, 4, 4)
        System.arraycopy(DEST_ID, 0, frame, 8, 4)
        System.arraycopy(payload, 0, frame, 12, payload.size)
        s.outputStream.apply {
            write(frame)
            flush()
        }
    }

    companion object {
        private const val PATH_LEGACY = "/Flash/hkjt_buffer"
        private const val PATH_API30 = "/tmp/hkjt_buffer"
        private const val PAGE = 8000
        private const val REGISTER = 'A'.code.toByte()
        private const val SEND_RS232 = 'S'.code.toByte()
        private const val FS: Byte = 0x1C
        private val SRC_ID = "1001".toByteArray(Charsets.US_ASCII)
        private val DEST_ID = "0000".toByteArray(Charsets.US_ASCII)
    }
}
