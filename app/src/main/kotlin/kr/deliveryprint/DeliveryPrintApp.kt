package kr.deliveryprint

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import kr.deliveryprint.di.ServiceLocator

class DeliveryPrintApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        createServiceChannel()
    }

    private fun createServiceChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_SERVICE,
                "출력 서비스",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "배달 주문 자동 출력 상태 표시" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_SERVICE = "print_service"
    }
}
