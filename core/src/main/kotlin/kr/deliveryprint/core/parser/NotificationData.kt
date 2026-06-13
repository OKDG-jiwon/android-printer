package kr.deliveryprint.core.parser

/**
 * 안드로이드 [android.service.notification.StatusBarNotification] 에서 추출한,
 * 플랫폼 비의존(순수 Kotlin) 형태의 알림 데이터. 파서는 이 값만 입력으로 받는다.
 */
data class NotificationData(
    val packageName: String,
    val title: String? = null,
    val text: String? = null,
    val subText: String? = null,
    val bigText: String? = null,
    /** Notification.InboxStyle 등 여러 줄로 들어오는 경우. */
    val textLines: List<String> = emptyList(),
    val postedAt: Long = 0L,
) {
    /** 파싱·키워드 검색에 쓰기 위해 모든 텍스트 필드를 한 덩어리로 합친다. */
    fun combinedText(): String {
        val parts = mutableListOf<String>()
        title?.let { parts.add(it) }
        subText?.let { parts.add(it) }
        // bigText 가 있으면 우선 사용하고, 없으면 text 사용
        (bigText?.takeIf { it.isNotBlank() } ?: text)?.let { parts.add(it) }
        parts.addAll(textLines)
        return parts.joinToString("\n").trim()
    }
}
