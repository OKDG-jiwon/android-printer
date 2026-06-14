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
 * MVP 단계라 BASE 는 임시 터널 주소. 오라클 클라우드 이전 시 이 값만 교체한다.
 */
object CloudOrderClient {

    // TODO: 오라클 클라우드 이전 시 교체
    const val BASE = "https://void-pages-sega-advertisers.trycloudflare.com"

    /** status: PENDING(신규/진행) | COMPLETED(완료) 등 쿠팡 상태값. */
    suspend fun fetch(status: String): Result<List<Order>> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("$BASE/orders?status=$status").openConnection() as HttpURLConnection).apply {
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
