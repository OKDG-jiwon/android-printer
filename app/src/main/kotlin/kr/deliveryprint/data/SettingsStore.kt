package kr.deliveryprint.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kr.deliveryprint.core.printer.ReceiptConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class PrinterType { BLUETOOTH, KICC_INNER }

/** 사용자 설정 일체. ReceiptConfig(core) 를 그대로 재사용한다. */
data class AppSettings(
    val printerType: PrinterType = PrinterType.BLUETOOTH,
    val bluetoothMac: String? = null,
    val autoPrint: Boolean = true,
    val receipt: ReceiptConfig = ReceiptConfig(),
)

/** DataStore 기반 설정 저장소. */
class SettingsStore(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            printerType = prefs[KEY_PRINTER_TYPE]?.let { runCatching { PrinterType.valueOf(it) }.getOrNull() }
                ?: PrinterType.BLUETOOTH,
            bluetoothMac = prefs[KEY_BT_MAC],
            autoPrint = prefs[KEY_AUTO_PRINT] ?: true,
            receipt = ReceiptConfig(
                widthChars = prefs[KEY_WIDTH] ?: 32,
                cutPaper = prefs[KEY_CUT] ?: true,
                openCashDrawer = prefs[KEY_DRAWER] ?: false,
            ),
        )
    }

    suspend fun setPrinterType(type: PrinterType) = context.dataStore.edit { it[KEY_PRINTER_TYPE] = type.name }
    suspend fun setBluetoothMac(mac: String) = context.dataStore.edit { it[KEY_BT_MAC] = mac }
    suspend fun setAutoPrint(enabled: Boolean) = context.dataStore.edit { it[KEY_AUTO_PRINT] = enabled }
    suspend fun setWidthChars(width: Int) = context.dataStore.edit { it[KEY_WIDTH] = width }
    suspend fun setCutPaper(enabled: Boolean) = context.dataStore.edit { it[KEY_CUT] = enabled }
    suspend fun setOpenCashDrawer(enabled: Boolean) = context.dataStore.edit { it[KEY_DRAWER] = enabled }

    companion object {
        private val KEY_PRINTER_TYPE = stringPreferencesKey("printer_type")
        private val KEY_BT_MAC = stringPreferencesKey("bluetooth_mac")
        private val KEY_AUTO_PRINT = booleanPreferencesKey("auto_print")
        private val KEY_WIDTH = intPreferencesKey("paper_width_chars")
        private val KEY_CUT = booleanPreferencesKey("cut_paper")
        private val KEY_DRAWER = booleanPreferencesKey("open_cash_drawer")
    }
}
