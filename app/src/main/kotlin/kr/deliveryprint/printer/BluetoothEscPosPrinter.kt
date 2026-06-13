package kr.deliveryprint.printer

import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 표준 ESC/POS 명령을 블루투스 SPP(RFCOMM)로 전송하는 프린터.
 * 제조사 SDK가 필요 없어 대부분의 영수증 프린터에서 바로 동작한다.
 *
 * 주의: 안드로이드 12(API 31)+ 에서는 BLUETOOTH_CONNECT 런타임 권한이 필요하다.
 */
class BluetoothEscPosPrinter(
    private val context: Context,
    private val macAddress: String,
) : Printer {

    override val displayName: String = "블루투스 프린터 ($macAddress)"

    override suspend fun print(bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                ?: error("블루투스를 사용할 수 없는 기기입니다")
            val adapter = manager.adapter ?: error("블루투스 어댑터가 없습니다")
            check(adapter.isEnabled) { "블루투스가 꺼져 있습니다" }

            val device = adapter.getRemoteDevice(macAddress)
            adapter.cancelDiscovery()
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.use { s ->
                s.connect()
                s.outputStream.use { os ->
                    os.write(bytes)
                    os.flush()
                }
            }
        }
    }

    companion object {
        /** Serial Port Profile 표준 UUID. */
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
