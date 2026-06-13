package kr.deliveryprint.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import kr.deliveryprint.core.parser.NotificationData

/** [StatusBarNotification] 을 플랫폼 비의존 [NotificationData] 로 변환한다. */
object NotificationMapper {

    fun from(sbn: StatusBarNotification): NotificationData? {
        val extras = sbn.notification?.extras ?: return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString() }
            ?: emptyList()

        // 텍스트가 전혀 없는 알림은 무시
        if (title.isNullOrBlank() && text.isNullOrBlank() && bigText.isNullOrBlank() && lines.isEmpty()) {
            return null
        }

        return NotificationData(
            packageName = sbn.packageName,
            title = title,
            text = text,
            subText = subText,
            bigText = bigText,
            textLines = lines,
            postedAt = sbn.postTime,
        )
    }
}
