package kr.deliveryprint.printer

import android.content.Context
import kr.deliveryprint.data.AppSettings
import kr.deliveryprint.data.PrinterType

/** 현재 설정에 맞는 [Printer] 구현을 생성한다. */
class PrinterFactory(private val context: Context) {

    fun create(settings: AppSettings): Printer = when (settings.printerType) {
        PrinterType.BLUETOOTH -> {
            val mac = settings.bluetoothMac
            if (mac.isNullOrBlank()) NoPrinter("블루투스 프린터가 선택되지 않았습니다")
            else BluetoothEscPosPrinter(context, mac)
        }
        PrinterType.KICC_INNER -> KiccInnerPrinter(settings.kiccComPort)
    }
}

/** 프린터가 설정되지 않았을 때의 안전한 폴백(항상 실패). */
class NoPrinter(private val reason: String) : Printer {
    override val displayName: String = "프린터 미설정"
    override suspend fun print(bytes: ByteArray): Result<Unit> =
        Result.failure(IllegalStateException(reason))
}
