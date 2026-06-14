package kr.deliveryprint.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kr.deliveryprint.core.model.Order
import kr.deliveryprint.core.model.OrderItem
import kr.deliveryprint.core.model.OrderType
import kr.deliveryprint.core.model.Platform
import kr.deliveryprint.data.CloudOrderClient
import kr.deliveryprint.data.PrinterType
import kr.deliveryprint.di.ServiceLocator
import kr.deliveryprint.service.PrintForegroundService
import kotlinx.coroutines.launch

private enum class Screen { HOME, PRINTER, LOG, CLOUD, BAEMIN }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        Screen.HOME -> "배달주문 출력"
        Screen.PRINTER -> "프린터 설정"
        Screen.LOG -> "알림 로그"
        Screen.CLOUD -> "클라우드 주문 (쿠팡)"
        Screen.BAEMIN -> "배민 주문 (PDF)"
    }
    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    onOpenPrinter = { screen = Screen.PRINTER },
                    onOpenLog = { screen = Screen.LOG },
                    onOpenCloud = { screen = Screen.CLOUD },
                    onOpenBaemin = { screen = Screen.BAEMIN },
                )
                Screen.PRINTER -> PrinterSettingsScreen(onBack = { screen = Screen.HOME })
                Screen.LOG -> NotificationLogScreen(onBack = { screen = Screen.HOME })
                Screen.CLOUD -> CloudOrdersScreen(onBack = { screen = Screen.HOME })
                Screen.BAEMIN -> BaeminOrdersScreen(onBack = { screen = Screen.HOME })
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onOpenPrinter: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenCloud: () -> Unit,
    onOpenBaemin: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberComposableScope()
    val notiGranted by rememberNotificationAccessGranted()
    val settings by ServiceLocator.settings.settings.collectAsState(initial = null)
    val lastResult by ServiceLocator.printController.lastResult.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(
            title = "1. 알림 접근 권한",
            value = if (notiGranted) "허용됨" else "허용 필요",
            ok = notiGranted,
        ) {
            OutlinedButton(onClick = { context.openNotificationAccessSettings() }) {
                Text("권한 설정 열기")
            }
        }

        StatusCard(
            title = "2. 프린터",
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
            title = "3. 자동 출력",
            value = if (settings?.autoPrint == true) "켜짐" else "꺼짐",
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
                Text("서비스 시작")
            }
            OutlinedButton(onClick = { PrintForegroundService.stop(context) }, modifier = Modifier.weight(1f)) {
                Text("서비스 중지")
            }
        }

        Button(onClick = onOpenCloud, modifier = Modifier.fillMaxWidth()) { Text("☁ 클라우드 주문 (쿠팡) 불러오기") }

        Button(onClick = onOpenBaemin, modifier = Modifier.fillMaxWidth()) { Text("🛵 배민 주문 (PDF) 불러오기") }

        OutlinedButton(onClick = onOpenLog, modifier = Modifier.fillMaxWidth()) { Text("알림 로그 보기") }

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
private fun NotificationLogScreen(onBack: () -> Unit) {
    val log by ServiceLocator.notificationRepository.recent.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "들어온 알림을 기록합니다. 실제 배달 주문 알림의 패키지명/본문을 확인해 파서를 보정하세요.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(log) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = (if (item.isOrder) "🧾 주문 · " else "") +
                                (item.platform?.displayName ?: item.packageName),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(item.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            item.text,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
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
            "배민 PC 클라이언트가 PDF로 저장한 주문전표를 서버가 42칸으로 변환해 보관합니다. " +
                "출력하면 서버 큐에서 제거됩니다(중복 방지).",
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
                        // 변환 텍스트 미리보기(앞 6줄)
                        order.preformattedText?.lineSequence()?.take(6)?.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                PrintForegroundService.start(context)
                                ServiceLocator.printController.submit(order)
                                // 출력 큐에 넣은 뒤 서버에서 제거(중복 방지)
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

@Composable
private fun rememberNotificationAccessGranted(): State<Boolean> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf(isNotificationAccessGranted(context)) }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.value = isNotificationAccessGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

private fun isNotificationAccessGranted(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun Context.openNotificationAccessSettings() {
    val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
    } else {
        "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
    }
    startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

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
