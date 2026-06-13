package kr.deliveryprint.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kr.deliveryprint.DeliveryPrintApp
import kr.deliveryprint.R
import kr.deliveryprint.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * 항상 떠 있는 포그라운드 서비스. 출력 큐를 소비해 영수증을 출력한다.
 * 포그라운드로 두어 백그라운드에서도 OS가 프로세스를 죽이지 않도록 한다.
 */
class PrintForegroundService : LifecycleService() {

    private var workerStarted = false

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!workerStarted) {
            workerStarted = true
            lifecycleScope.launch { ServiceLocator.printController.runWorker() }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, DeliveryPrintApp.CHANNEL_SERVICE)
            .setContentTitle("배달 주문 자동 출력")
            .setContentText("주문 알림을 감지해 자동으로 출력합니다")
            .setSmallIcon(R.drawable.ic_print)
            .setOngoing(true)
            .build()

    companion object {
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PrintForegroundService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PrintForegroundService::class.java))
        }
    }
}
