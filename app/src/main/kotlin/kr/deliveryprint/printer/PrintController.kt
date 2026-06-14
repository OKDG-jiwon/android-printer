package kr.deliveryprint.printer

import android.util.Log
import kr.deliveryprint.core.model.Order
import kr.deliveryprint.core.model.Platform
import kr.deliveryprint.core.printer.CoupangReceiptFormatter
import kr.deliveryprint.core.printer.PreformattedReceiptFormatter
import kr.deliveryprint.core.printer.ReceiptFormatter
import kr.deliveryprint.data.SettingsStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** 출력 결과(상태 화면 표시용). */
data class PrintResult(val order: Order, val success: Boolean, val message: String, val at: Long)

/**
 * 주문 출력 큐. 알림 수신/테스트 출력에서 [submit] 로 주문을 넣으면,
 * [PrintForegroundService] 가 [runWorker] 로 큐를 비우며 출력한다(실패 시 재시도).
 */
class PrintController(
    private val settings: SettingsStore,
    private val printerFactory: PrinterFactory,
) {
    private val queue = Channel<Order>(Channel.UNLIMITED)

    private val _lastResult = MutableStateFlow<PrintResult?>(null)
    val lastResult: StateFlow<PrintResult?> = _lastResult.asStateFlow()

    /** 큐에 주문 적재(논블로킹). */
    fun submit(order: Order) {
        queue.trySend(order)
    }

    /** 서비스에서 호출. 큐를 무한히 소비하며 출력한다. */
    suspend fun runWorker() {
        for (order in queue) {
            printWithRetry(order)
        }
    }

    private suspend fun printWithRetry(order: Order) {
        val current = settings.settings.first()
        // 1) 이미 42칸으로 재배치된 본문(배민 PDF 변환 등)은 그대로 출력.
        // 2) 쿠팡 주문은 실제 쿠팡이츠 영수증 레이아웃으로.
        // 3) 그 외는 기본 포맷.
        val pre = order.preformattedText
        val bytes = when {
            pre != null -> PreformattedReceiptFormatter(current.receipt).format(pre)
            order.platform == Platform.COUPANG_EATS -> CoupangReceiptFormatter(current.receipt).format(order)
            else -> ReceiptFormatter(current.receipt).format(order)
        }
        val printer = printerFactory.create(current)

        var lastError = "알 수 없는 오류"
        repeat(MAX_RETRY) { attempt ->
            val result = printer.print(bytes)
            if (result.isSuccess) {
                publish(order, true, "출력 완료 (${printer.displayName})")
                return
            }
            lastError = result.exceptionOrNull()?.message ?: lastError
            Log.w(TAG, "출력 실패(${attempt + 1}/$MAX_RETRY): $lastError")
            delay(BACKOFF_MS * (attempt + 1))
        }
        publish(order, false, "출력 실패: $lastError")
    }

    private fun publish(order: Order, success: Boolean, message: String) {
        _lastResult.value = PrintResult(order, success, message, System.currentTimeMillis())
    }

    companion object {
        private const val TAG = "PrintController"
        private const val MAX_RETRY = 3
        private const val BACKOFF_MS = 1_500L
    }
}
