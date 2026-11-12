package dev.ijemwohajo.signalsupervisor.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.ijemwohajo.signalsupervisor.MainActivity
import dev.ijemwohajo.signalsupervisor.core.TripReport

/** 정산 완료 알림. 소리 없음, 탭하면 앱을 연다. */
class SettlementNotifier(private val ctx: Context) {
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    init {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "운행 정산", NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null) },
        )
    }

    fun notify(r: TripReport) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(dev.ijemwohajo.signalsupervisor.R.drawable.ic_stat_signal)
            .setContentTitle("시그널 감독관")
            .setContentText(body(r))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(r.id.hashCode(), n)
    }

    companion object {
        const val CHANNEL = "signal_settlement"
        fun body(r: TripReport) = if (r.score == null) "운행 정산 완료 · 판정 없음" else "운행 정산 완료 · 이번 운행 ${r.score}점"
    }
}
