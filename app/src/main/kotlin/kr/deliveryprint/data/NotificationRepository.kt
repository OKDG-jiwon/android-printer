package kr.deliveryprint.data

import kr.deliveryprint.core.model.Platform
import kr.deliveryprint.core.parser.NotificationData
import kr.deliveryprint.core.parser.OrderParsers
import kr.deliveryprint.printer.PrintController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 알림 덤프 화면에 표시할, 캡처된 알림 1건. */
data class CapturedNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val isOrder: Boolean,
    val platform: Platform?,
)

/**
 * 알림 수신의 중심 허브.
 * - 들어온 모든 알림을 [recent] 에 기록(파서 보정용 덤프 화면).
 * - 주문 알림이고 자동출력이 켜져 있으면 [PrintController] 로 출력 요청.
 */
class NotificationRepository(
    private val settings: SettingsStore,
    private val printController: PrintController,
    private val scope: CoroutineScope,
    private val parsers: OrderParsers = OrderParsers(),
) {
    private val _recent = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val recent: StateFlow<List<CapturedNotification>> = _recent.asStateFlow()

    fun onNotificationPosted(data: NotificationData) {
        val order = parsers.parse(data)
        val captured = CapturedNotification(
            packageName = data.packageName,
            title = data.title.orEmpty(),
            text = data.combinedText(),
            postedAt = data.postedAt,
            isOrder = order != null,
            platform = order?.platform,
        )
        _recent.value = (listOf(captured) + _recent.value).take(MAX_LOG)

        if (order != null) {
            scope.launch {
                if (settings.settings.first().autoPrint) {
                    printController.submit(order)
                }
            }
        }
    }

    companion object {
        private const val MAX_LOG = 100
    }
}
