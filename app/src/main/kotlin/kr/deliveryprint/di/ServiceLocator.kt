package kr.deliveryprint.di

import android.content.Context
import kr.deliveryprint.data.CloudOrderClient
import kr.deliveryprint.data.CloudPoller
import kr.deliveryprint.data.SettingsStore
import kr.deliveryprint.printer.PrintController
import kr.deliveryprint.printer.PrinterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 경량 수동 DI. 규모가 작아 Hilt 등 프레임워크 대신 lazy 싱글톤으로 묶는다.
 * [init] 은 Application.onCreate 에서 1회 호출한다.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        // 설정의 서버 주소(쿠팡/배민)를 CloudOrderClient 에 반영(설정 변경 시 재빌드 없이 즉시 적용).
        appScope.launch {
            settings.settings.distinctUntilChanged { a, b ->
                a.coupangServerUrl == b.coupangServerUrl && a.baeminServerUrl == b.baeminServerUrl
            }.collect {
                CloudOrderClient.coupangBase = it.coupangServerUrl
                CloudOrderClient.baeminBase = it.baeminServerUrl
            }
        }
    }

    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    val settings: SettingsStore by lazy { SettingsStore(appContext) }

    val printerFactory: PrinterFactory by lazy { PrinterFactory(appContext) }

    val printController: PrintController by lazy { PrintController(settings, printerFactory) }

    val cloudPoller: CloudPoller by lazy { CloudPoller(settings, printController, appScope) }
}
