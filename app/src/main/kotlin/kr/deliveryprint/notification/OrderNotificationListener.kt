package kr.deliveryprint.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kr.deliveryprint.di.ServiceLocator
import kr.deliveryprint.service.PrintForegroundService

/**
 * 배달앱(배민/쿠팡이츠 등)의 주문 알림을 가로채는 핵심 서비스.
 * 사용자가 시스템 설정에서 "알림 접근 권한"을 허용해야 동작한다.
 */
class OrderNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "알림 접근 연결됨")
        // 자동 출력을 위해 출력 서비스 기동(이미 떠 있으면 무시됨)
        PrintForegroundService.start(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val data = NotificationMapper.from(sbn) ?: return
        ServiceLocator.notificationRepository.onNotificationPosted(data)
    }

    companion object {
        private const val TAG = "OrderNotiListener"
    }
}
