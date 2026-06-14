package kr.deliveryprint.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.deliveryprint.core.model.Order
import kr.deliveryprint.core.model.OrderItem
import kr.deliveryprint.core.model.OrderType
import kr.deliveryprint.core.model.Platform
import kr.deliveryprint.data.CloudOrderClient
import kr.deliveryprint.data.PrinterType
import kr.deliveryprint.di.ServiceLocator
import kr.deliveryprint.service.PrintForegroundService
import kotlinx.coroutines.launch

private enum class Screen { HOME, PRINTER, CLOUD, BAEMIN }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱이 뜨면 출력 서비스를 바로 가동 → 클라우드(쿠팡/배민) 신규 주문 자동 폴링·출력 시작.
        PrintForegroundService.start(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    val title = when (screen) {
        Screen.HOME -> "배달주문 자동 출력"
        Screen.PRINTER -> "프린터 설정"
        Screen.CLOUD -> "쿠팡 주문 (수동)"
        Screen.BAEMIN -> "배민 주문 (수동)"
    }
    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    onOpenPrinter = { screen = Screen.PRINTER },
                    onOpenCloud = { screen = Screen.CLOUD },
                    onOpenBaemin = { screen = Screen.BAEMIN },
                )
                Screen.PRINTER -> PrinterSettingsScreen(onBack = { screen = Screen.HOME })
                Screen.CLOUD -> CloudOrdersScreen(onBack = { screen = Screen.HOME })
                Screen.BAEMIN -> BaeminOrdersScreen(onBack = { screen = Screen.HOME })
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onOpenPrinter: () -> Unit,
    onOpenCloud: () -> Unit,
    onOpenBaemin: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberComposableScope()
    val settings by ServiceLocator.settings.settings.collectAsState(initial = null)
    val lastResult by ServiceLocator.printController.lastResult.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("자동 출력 동작 방식", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "쿠팡·배민에 새 주문이 들어오면 중계 서버가 받아두고, 이 앱이 약 5초마다 " +
                        "가져와 프린터로 자동 출력합니다. 출력 버튼을 누를 필요가 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        StatusCard(
            title = "1. 프린터",
            value = settings?.let {
                when (it.printerType) {
                    PrinterType.BLUETOOTH -> it.bluetoothMac?.let { mac -> "블루투스 ($mac)" } ?: "미선택"
                    PrinterType.KICC_INNER -> "내장 프린터 (KICC · ${it.kiccComPort})"
                }
            } ?: "...",
            ok = settings?.bluetoothMac != null || settings?.printerType == PrinterType.KICC_INNER,
        ) {
            OutlinedButton(onClick = onOpenPrinter) { Text("프린터 설정") }
        }

        StatusCard(
            title = "2. 자동 출력 (쿠팡·배민)",
            value = if (settings?.autoPrint == true) "켜짐 — 새 주문 자동 출력" else "꺼짐 — 수동 출력만",
            ok = settings?.autoPrint == true,
        ) {
            Switch(
                checked = settings?.autoPrint == true,
                onCheckedChange = { checked -> scope.launch { ServiceLocator.settings.setAutoPrint(checked) } },
            )
        }

        HorizontalDivider()

        Button(
            onClick = {
                PrintForegroundService.start(context)
                ServiceLocator.printController.submit(sampleOrder())
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("테스트 출력") }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { PrintForegroundService.start(context) }, modifier = Modifier.weight(1f)) {
                Text("출력 서비스 시작")
            }
            OutlinedButton(onClick = { PrintForegroundService.stop(context) }, modifier = Modifier.weight(1f)) {
                Text("중지")
            }
        }

        Text("수동 조회·재출력", style = MaterialTheme.typography.titleSmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenCloud, modifier = Modifier.weight(1f)) { Text("☁ 쿠팡") }
            OutlinedButton(onClick = onOpenBaemin, modifier = Modifier.weight(1f)) { Text("🛵 배민") }
        }

        lastResult?.let { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("최근 출력 결과", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("${if (result.success) "성공" else "실패"} · ${result.order.platform.displayName}")
                    Text(result.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PrinterSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberComposableScope()
    val settings by ServiceLocator.settings.settings.collectAsState(initial = null)
    val current = settings ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("프린터 종류", style = MaterialTheme.typography.titleMedium)
        PrinterType.entries.forEach { type ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = current.printerType == type,
                        onClick = { scope.launch { ServiceLocator.settings.setPrinterType(type) } },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = current.printerType == type, onClick = null)
                Spacer(Modifier.height(0.dp))
                Text(
                    text = when (type) {
                        PrinterType.BLUETOOTH -> "  블루투스 ESC/POS 프린터 (권장)"
                        PrinterType.KICC_INNER -> "  내장 프린터 (KICC · 이지체크)"
                    },
                )
            }
        }

        HorizontalDivider()

        if (current.printerType == PrinterType.BLUETOOTH) {
            Text("페어링된 블루투스 기기", style = MaterialTheme.typography.titleMedium)
            val devices = remember { pairedDevices(context) }
            if (devices.isEmpty()) {
                Text("페어링된 기기가 없습니다. 시스템 설정에서 프린터를 먼저 페어링하세요.")
            }
            devices.forEach { (name, mac) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = current.bluetoothMac == mac,
                            onClick = { scope.launch { ServiceLocator.settings.setBluetoothMac(mac) } },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = current.bluetoothMac == mac, onClick = null)
                    Text("  $name  ($mac)")
                }
            }
        }

        if (current.printerType == PrinterType.KICC_INNER) {
            Text("내장 프린터 포트", style = MaterialTheme.typography.titleMedium)
            Text(
                "내부(내장) 프린터는 INTERNAL. COM 포트는 서명패드 등 주변기기용입니다.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("INTERNAL", "COM1", "COM2", "0").forEach { port ->
                    OutlinedButton(onClick = { scope.launch { ServiceLocator.settings.setKiccComPort(port) } }) {
                        Text(if (current.kiccComPort == port) "● $port" else port)
                    }
                }
            }
        }

        HorizontalDivider()

        Text("용지 폭", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(32 to "58mm", 48 to "80mm").forEach { (width, label) ->
                OutlinedButton(onClick = { scope.launch { ServiceLocator.settings.setWidthChars(width) } }) {
                    Text(if (current.receipt.widthChars == width) "● $label" else label)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("자동 용지 절단", modifier = Modifier.weight(1f))
            Switch(
                checked = current.receipt.cutPaper,
                onCheckedChange = { scope.launch { ServiceLocator.settings.setCutPaper(it) } },
            )
        }

        HorizontalDivider()

        Text("서버 주소", style = MaterialTheme.typography.titleMedium)
        Text(
            "쿠팡·배민 주문을 받아오는 중계 서버 주소입니다. 터널/오라클 등으로 주소가 바뀌면 " +
                "여기만 고치면 됩니다(앱 재설치 불필요).",
            style = MaterialTheme.typography.bodySmall,
        )
        var urlText by remember(current.serverBaseUrl) { mutableStateOf(current.serverBaseUrl) }
        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("https://...") },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { scope.launch { ServiceLocator.settings.setServerBaseUrl(urlText) } },
                modifier = Modifier.weight(1f),
            ) { Text("서버 주소 저장") }
            OutlinedButton(onClick = { urlText = CloudOrderClient.DEFAULT_BASE }) { Text("기본값") }
        }

        HorizontalDivider()

        Button(
            onClick = {
                PrintForegroundService.start(context)
                ServiceLocator.printController.submit(sampleOrder())
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("이 설정으로 테스트 출력") }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("뒤로") }
    }
}

@Composable
private fun CloudOrdersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberComposableScope()
    // 쿠팡은 "진행중"이 PENDING(신규)+PROCESSING(수락·진행)으로 나뉘므로 둘 다 조회한다.
    var status by remember { mutableStateOf("PENDING,PROCESSING") }
    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        loading = true
        error = null
        scope.launch {
            CloudOrderClient.fetch(status)
                .onSuccess { orders = it }
                .onFailure { error = it.message ?: "불러오기 실패" }
            loading = false
        }
    }
    LaunchedEffect(status) { load() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("PENDING,PROCESSING" to "신규/진행", "COMPLETED" to "완료").forEach { (s, label) ->
                OutlinedButton(onClick = { status = s }) {
                    Text(if (status == s) "● $label" else label)
                }
            }
            OutlinedButton(onClick = { load() }) { Text("새로고침") }
        }
        if (loading) Text("불러오는 중…")
        error?.let {
            Text("오류: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (!loading && error == null && orders.isEmpty()) Text("주문이 없습니다.")

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(orders) { order ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${order.orderType.displayName} · ${order.storeName ?: ""}  #${order.orderNumber ?: ""}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        order.items.forEach { item ->
                            Text("• ${item.name} x${item.quantity}", style = MaterialTheme.typography.bodySmall)
                        }
                        order.totalAmount?.let {
                            Text("합계 ${"%,d".format(it)}원", style = MaterialTheme.typography.bodyMedium)
                        }
                        order.customerRequest?.let { Text("요청: $it", style = MaterialTheme.typography.bodySmall) }
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                PrintForegroundService.start(context)
                                ServiceLocator.printController.submit(order)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("출력") }
                    }
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("뒤로") }
    }
}

@Composable
private fun BaeminOrdersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberComposableScope()
    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        loading = true
        error = null
        scope.launch {
            CloudOrderClient.fetchBaemin()
                .onSuccess { orders = it }
                .onFailure { error = it.message ?: "불러오기 실패" }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "자동 출력이 켜져 있으면 새 배민 주문은 자동으로 출력됩니다. 여기서는 대기 중인 전표를 수동으로 출력할 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { load() }) { Text("새로고침") }
        }
        if (loading) Text("불러오는 중…")
        error?.let {
            Text("오류: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (!loading && error == null && orders.isEmpty()) Text("대기 중인 배민 주문이 없습니다.")

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(orders) { order ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("배민 전표 #${order.orderNumber ?: ""}", style = MaterialTheme.typography.titleSmall)
                        order.preformattedText?.lineSequence()?.take(6)?.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                PrintForegroundService.start(context)
                                ServiceLocator.printController.submit(order)
                                order.orderNumber?.let { id ->
                                    scope.launch {
                                        CloudOrderClient.ackBaemin(id)
                                        orders = orders.filterNot { it.orderNumber == id }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("출력") }
                    }
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("뒤로") }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    ok: Boolean,
    action: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = (if (ok) "✓ " else "• ") + value,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            action()
        }
    }
}

// ---- helpers ----

@Composable
private fun rememberComposableScope() = androidx.compose.runtime.rememberCoroutineScope()

@SuppressLint("MissingPermission")
private fun pairedDevices(context: Context): List<Pair<String, String>> {
    val manager = context.getSystemService(BluetoothManager::class.java) ?: return emptyList()
    val adapter = manager.adapter ?: return emptyList()
    return try {
        adapter.bondedDevices.orEmpty().map { (it.name ?: "이름 없음") to it.address }
    } catch (e: SecurityException) {
        emptyList()
    }
}

private fun sampleOrder(): Order = Order(
    platform = Platform.BAEMIN,
    orderType = OrderType.DELIVERY,
    orderNumber = "TEST-0001",
    storeName = "테스트 매장",
    items = listOf(OrderItem("아메리카노", 2, 4_500), OrderItem("카페라떼", 1, 5_000)),
    totalAmount = 14_000,
    deliveryAddress = "서울시 강남구 테헤란로 123",
    phoneNumber = "010-0000-0000",
    customerRequest = "테스트 출력입니다",
    receivedAt = System.currentTimeMillis(),
)
