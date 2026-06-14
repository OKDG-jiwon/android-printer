package kr.deliveryprint.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kr.deliveryprint.core.printer.CutMode
import kr.deliveryprint.core.printer.ReceiptConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class PrinterType { BLUETOOTH, KICC_INNER }

/** 사용자 설정 일체. ReceiptConfig(core) 를 그대로 재사용한다. */
data class AppSettings(
    val printerType: PrinterType = PrinterType.BLUETOOTH,
    val bluetoothMac: String? = null,
    val kiccComPort: String = "INTERNAL",
    val autoPrint: Boolean = true,
    /** 쿠팡 중계 서버 주소(맥). 바뀌면 앱에서 값만 고치면 됨(재빌드 불필요). */
    val coupangServerUrl: String = CloudOrderClient.COUPANG_DEFAULT,
    /** 배민 중계 서버 주소(오라클 VM). */
    val baeminServerUrl: String = CloudOrderClient.BAEMIN_DEFAULT,
    val receipt: ReceiptConfig = ReceiptConfig(),
)

/** DataStore 기반 설정 저장소. */
class SettingsStore(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val printerType = prefs[KEY_PRINTER_TYPE]?.let { runCatching { PrinterType.valueOf(it) }.getOrNull() }
            ?: PrinterType.BLUETOOTH
        AppSettings(
            printerType = printerType,
            bluetoothMac = prefs[KEY_BT_MAC],
            kiccComPort = prefs[KEY_KICC_PORT] ?: "INTERNAL",
            autoPrint = prefs[KEY_AUTO_PRINT] ?: true,
            coupangServerUrl = prefs[KEY_COUPANG_URL]?.ifBlank { null } ?: CloudOrderClient.COUPANG_DEFAULT,
            baeminServerUrl = prefs[KEY_BAEMIN_URL]?.ifBlank { null } ?: CloudOrderClient.BAEMIN_DEFAULT,
            receipt = ReceiptConfig(
                widthChars = prefs[KEY_WIDTH] ?: 32,
                cutPaper = prefs[KEY_CUT] ?: true,
                // KICC 내부 프린터는 ESC m 절단, 블루투스 등은 표준 GS V.
                cutMode = if (printerType == PrinterType.KICC_INNER) CutMode.ESC_M else CutMode.GS_V,
                openCashDrawer = prefs[KEY_DRAWER] ?: false,
            ),
        )
    }

    suspend fun setPrinterType(type: PrinterType) = context.dataStore.edit { it[KEY_PRINTER_TYPE] = type.name }
    suspend fun setBluetoothMac(mac: String) = context.dataStore.edit { it[KEY_BT_MAC] = mac }
    suspend fun setKiccComPort(port: String) = context.dataStore.edit { it[KEY_KICC_PORT] = port }
    suspend fun setAutoPrint(enabled: Boolean) = context.dataStore.edit { it[KEY_AUTO_PRINT] = enabled }
    suspend fun setCoupangServerUrl(url: String) =
        context.dataStore.edit { it[KEY_COUPANG_URL] = url.trim().trimEnd('/') }
    suspend fun setBaeminServerUrl(url: String) =
        context.dataStore.edit { it[KEY_BAEMIN_URL] = url.trim().trimEnd('/') }
    suspend fun setWidthChars(width: Int) = context.dataStore.edit { it[KEY_WIDTH] = width }
    suspend fun setCutPaper(enabled: Boolean) = context.dataStore.edit { it[KEY_CUT] = enabled }
    suspend fun setOpenCashDrawer(enabled: Boolean) = context.dataStore.edit { it[KEY_DRAWER] = enabled }

    companion object {
        private val KEY_PRINTER_TYPE = stringPreferencesKey("printer_type")
        private val KEY_BT_MAC = stringPreferencesKey("bluetooth_mac")
        private val KEY_KICC_PORT = stringPreferencesKey("kicc_com_port")
        private val KEY_AUTO_PRINT = booleanPreferencesKey("auto_print")
        private val KEY_COUPANG_URL = stringPreferencesKey("coupang_server_url")
        private val KEY_BAEMIN_URL = stringPreferencesKey("baemin_server_url")
        private val KEY_WIDTH = intPreferencesKey("paper_width_chars")
        private val KEY_CUT = booleanPreferencesKey("cut_paper")
        private val KEY_DRAWER = booleanPreferencesKey("open_cash_drawer")
    }
}
