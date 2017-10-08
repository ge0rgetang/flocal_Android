@file:Suppress("MemberVisibilityCanPrivate")


package com.dotnative.flocal

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.support.v4.app.NotificationCompat
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FirebaseMessagingService: FirebaseMessagingService() {

    // MARK: - Vars

    val tag = "MyAndroidFCMService"
    var notificationID = 1

    // MARK - Lifecycle

    override fun onMessageReceived(p0: RemoteMessage?) {
        super.onMessageReceived(p0)

        val body = p0?.notification?.body
        Log.d(tag, "From: " + p0?.from)
        Log.d(tag, "Notification Message Body: " + body)

        if (body != null) {
            createNotification(body)
        }
    }

    fun createNotification(body: String) {
        val intent = Intent(this, MainActivity().javaClass)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT)

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notBuilder = NotificationCompat.Builder(this, "channel")
        notBuilder.setSmallIcon(R.mipmap.ic_launcher)
        notBuilder.setContentTitle("flocal Push Notification")
        notBuilder.setContentText(body)
        notBuilder.setAutoCancel(true)
        notBuilder.setSound(defaultSoundUri)
        notBuilder.setContentIntent(pendingIntent)

        val notManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationID > 1073741824) {
            notificationID = 0
        }
        notManager.notify(notificationID++, notBuilder.build())
    }

}