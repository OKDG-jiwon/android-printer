package kr.deliveryprint.data

import kr.deliveryprint.core.model.Order
import kr.deliveryprint.core.model.OrderItem
import kr.deliveryprint.core.model.OrderType
import kr.deliveryprint.core.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 클라우드(현재는 맥 터널) 주문 중계 서버에서 주문 목록을 가져온다.
 * 서버가 쿠팡 POS API를 호출해 정리한 JSON 을 받아 [Order] 로 변환한다.
 *
 * 서버 주소는 앱 설정(SettingsStore.serverBaseUrl)에서 런타임에 바꿀 수 있다.
 * [base] 는 설정 변경을 반영해 갱신되며(ServiceLocator 가 동기화), 미설정 시 [DEFAULT_BASE].
 * 터널/오라클 등으로 주소가 바뀌어도 앱에서 값만 고치면 되고 재빌드가 필요 없다.
 */
object CloudOrderClient {

    // 쿠팡과 배민은 서버가 다르다(쿠팡 API는 한국 IP 필요 → 맥, 배민은 지역 무관 → 오라클 VM).
    /** 쿠팡 중계 서버 기본 주소(맥 터널). */
    const val COUPANG_DEFAULT = "https://void-pages-sega-advertisers.trycloudflare.com"
    /** 배민 중계 서버 기본 주소(오라클 VM, Tailscale Funnel 고정 HTTPS). */
    const val BAEMIN_DEFAULT = "https://instance-20260612-0223.tail5d0f46.ts.net"

    /** 현재 쿠팡 서버 주소. ServiceLocator 가 설정 변경을 반영한다. */
    @Volatile
    var coupangBase: String = COUPANG_DEFAULT

    /** 현재 배민 서버 주소. */
    @Volatile
    var baeminBase: String = BAEMIN_DEFAULT

    /** status: PENDING(신규/진행) | COMPLETED(완료) 등 쿠팡 상태값. */
    suspend fun fetch(status: String): Result<List<Order>> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("$coupangBase/orders?status=$status").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            if (!root.optBoolean("ok")) error(root.optString("error", "서버 오류 (HTTP $code)"))
            val arr = root.getJSONArray("orders")
            (0 until arr.length()).map { parseOrder(arr.getJSONObject(it)) }
        }
    }

    /**
     * 서버에 쌓인 배민 변환 영수증(이미 42칸 재배치된 텍스트)을 가져온다.
     * PC 클라이언트가 올린 PDF 를 서버가 변환해 큐에 보관한 것.
     */
    suspend fun fetchBaemin(): Result<List<Order>> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("$baeminBase/baemin").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            if (!root.optBoolean("ok")) error(root.optString("error", "서버 오류 (HTTP $code)"))
            val arr = root.getJSONArray("receipts")
            (0 until arr.length()).map { i ->
                val r = arr.getJSONObject(i)
                Order(
                    platform = Platform.BAEMIN,
                    orderType = OrderType.DELIVERY,
                    orderNumber = r.optString("id"),
                    preformattedText = r.optString("text"),
                    receivedAt = r.optLong("at", 0L),
                )
            }
        }
    }

    /** 출력 완료한 배민 영수증을 서버 큐에서 제거(중복출력 방지). */
    suspend fun ackBaemin(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("$baeminBase/baemin/ack").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write("""{"id":"$id"}""".toByteArray()) }
            conn.responseCode // 트리거
            Unit
        }
    }

    private fun parseOrder(o: JSONObject): Order {
        val itemsArr = o.optJSONArray("items")
        val items = if (itemsArr == null) emptyList() else (0 until itemsArr.length()).map { i ->
            val it = itemsArr.getJSONObject(i)
            val optsArr = it.optJSONArray("options")
            val options = if (optsArr == null) emptyList()
            else (0 until optsArr.length()).map { j -> optsArr.getString(j) }
            OrderItem(
                name = it.optString("name"),
                quantity = it.optInt("quantity", 1),
                price = if (it.isNull("price")) null else it.optInt("price"),
                options = options,
            )
        }
        val note = o.optString("note").trim()
        val service = o.optString("serviceType")
        return Order(
            platform = Platform.COUPANG_EATS,
            orderType = when (service) {
                "DELIVERY" -> OrderType.DELIVERY
                "PICKUP", "TAKEOUT" -> OrderType.TAKEOUT
                else -> OrderType.UNKNOWN
            },
            storeName = o.optString("storeName").ifBlank { null },
            orderNumber = o.optString("abbrOrderId").ifBlank { null },
            items = items,
            subtotal = if (o.isNull("subtotal")) null else o.optInt("subtotal"),
            deliveryFee = if (o.isNull("deliveryFee")) null else o.optInt("deliveryFee"),
            totalAmount = if (o.isNull("totalAmount")) null else o.optInt("totalAmount"),
            paymentMethod = if (o.isNull("paymentMethod")) null else o.optString("paymentMethod").ifBlank { null },
            customerRequest = note.ifBlank { null },
            rawText = "",
            receivedAt = o.optLong("orderedAt", 0L),
        )
    }
}
