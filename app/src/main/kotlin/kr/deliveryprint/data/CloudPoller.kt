package kr.deliveryprint.data

import android.util.Log
import kr.deliveryprint.printer.PrintController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 클라우드(중계 서버)를 주기적으로 폴링해 **새 주문을 자동 출력**한다.
 *
 * - 쿠팡: `PENDING,PROCESSING`(신규+수락·진행중)을 가져와, 이전에 본 적 없는 주문만 1회 출력.
 *   서버 측 ack 가 없으므로 클라이언트가 본 주문번호를 기억해 중복 출력을 막는다.
 * - 배민: 서버 큐(미출력분만 존재)를 가져와 출력하고 `ack` 로 제거 → 중복 방지.
 *
 * **백로그 방지**: 폴링을 막 시작했을 때 이미 쌓여 있던 쿠팡 주문은 "본 것"으로만 표시하고
 * 출력하지 않는다(과거 주문이 한꺼번에 쏟아지는 것 방지). 시작 이후 새로 들어온 주문만 출력한다.
 *
 * 자동 출력은 [SettingsStore.AppSettings.autoPrint] 가 켜져 있을 때만 한다.
 */
class CloudPoller(
    private val settings: SettingsStore,
    private val printController: PrintController,
    private val scope: CoroutineScope,
) {
    private val seenCoupang = mutableSetOf<String>()
    private var primed = false
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            Log.i(TAG, "클라우드 폴링 시작 (${POLL_INTERVAL_MS}ms 주기)")
            while (isActive) {
                runCatching { pollOnce() }.onFailure { Log.w(TAG, "폴링 실패: ${it.message}") }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun pollOnce() {
        val auto = settings.settings.first().autoPrint

        // 쿠팡: 새 주문만 출력. 첫 폴은 기존 주문을 "본 것"으로만 표시.
        CloudOrderClient.fetch("PENDING,PROCESSING").onSuccess { orders ->
            for (o in orders) {
                val key = o.orderNumber?.takeIf { it.isNotBlank() } ?: continue
                val isNew = seenCoupang.add(key)
                if (isNew && primed && auto) {
                    Log.i(TAG, "새 쿠팡 주문 자동 출력: $key")
                    printController.submit(o)
                }
            }
            primed = true
        }

        // 배민: 서버 큐에 있는 건 모두 미출력분 → 출력 후 ack(중복 방지). autoPrint 꺼져 있으면 건드리지 않음.
        if (auto) {
            CloudOrderClient.fetchBaemin().onSuccess { receipts ->
                for (o in receipts) {
                    val id = o.orderNumber?.takeIf { it.isNotBlank() } ?: continue
                    Log.i(TAG, "새 배민 주문 자동 출력: $id")
                    printController.submit(o)
                    CloudOrderClient.ackBaemin(id)
                }
            }
        }
    }

    companion object {
        private const val TAG = "CloudPoller"
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
