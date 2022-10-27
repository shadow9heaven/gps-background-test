package com.example.gps_foreground_test

import android.app.*
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

//前台服务
class ForegroundCoreService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    private var mForegroundNF:ForegroundNF  = ForegroundNF(this)

    override fun onCreate() {
        super.onCreate()
        mForegroundNF.startForegroundNotification()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(null == intent){
            //服务被系统kill掉之后重启进来的
            return START_NOT_STICKY
        }
        mForegroundNF.startForegroundNotification()
        return super.onStartCommand(intent, flags, startId)
    }
    override fun onDestroy() {
        mForegroundNF.stopForegroundNotification()
        super.onDestroy()
    }
}

//初始化前台通知，停止前台通知
class ForegroundNF(private val service: ForegroundCoreService) : ContextWrapper(service) {
    companion object {
        private const val START_ID = 101
        private const val CHANNEL_ID = "app_foreground_service"
        private const val CHANNEL_NAME = "前台保活服务"
    }
    private var mNotificationManager: NotificationManager? = null

    private var mCompatBuilder: NotificationCompat.Builder?=null

    private val compatBuilder: NotificationCompat.Builder?
        get() {
            if (mCompatBuilder == null) {
                val notificationIntent = Intent(this, MainActivity::class.java)
                notificationIntent.action = Intent.ACTION_MAIN
                notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                notificationIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                //动作意图
                val pendingIntent = PendingIntent.getActivity(
                    this, (Math.random() * 10 + 10).toInt(),
                    notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT
                )
                val notificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(this,CHANNEL_ID)
                //标题
                notificationBuilder.setContentTitle("warn")
                //通知内容
                notificationBuilder.setContentText("service")
                //状态栏显示的小图标
                notificationBuilder.setSmallIcon(R.mipmap.ic_launcher_round)
                //通知内容打开的意图
                notificationBuilder.setContentIntent(pendingIntent)
                mCompatBuilder = notificationBuilder
            }
            return mCompatBuilder
        }

    init {
        createNotificationChannel()
    }

    //创建通知渠道
    private fun createNotificationChannel() {
        mNotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        //针对8.0+系统
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.setShowBadge(false)
            mNotificationManager?.createNotificationChannel(channel)
        }
    }

    //开启前台通知
    fun startForegroundNotification() {
        service.startForeground(START_ID, compatBuilder?.build())
    }

    //停止前台服务并清除通知
    fun stopForegroundNotification() {
        mNotificationManager?.cancelAll()
        service.stopForeground(true)
    }
}

//作者：Halifax
//链接：https://juejin.cn/post/7003992225575075876
//来源：稀土掘金
