package com.app.castall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * Keeps the CPU and Wi-Fi radio awake while the app is serving a local video
 * file to a Chromecast device. Without this, Android's Doze / App Standby
 * power management throttles the app a short time after the screen locks,
 * which stalls or drops the local HTTP server and freezes/kills the cast.
 */
public class CastKeepAliveService extends Service {

    private static final String CHANNEL_ID = "cast_keep_alive_channel";
    private static final int NOTIFICATION_ID = 9001;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        acquireLocks();
        // If the system kills the process, don't automatically recreate this
        // service with a null intent; the app will restart it explicitly
        // the next time it starts casting.
        return START_NOT_STICKY;
    }

    private void acquireLocks() {
        if (wakeLock == null) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, "CastAll:LocalCastWakeLock");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(30 * 60 * 1000L /* 30 minute safety timeout */);
            }
        }
        if (wifiLock == null) {
            WifiManager wifiManager = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                wifiLock = wifiManager.createWifiLock(
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CastAll:LocalCastWifiLock");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
            }
        }
    }

    private void releaseLocks() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        wifiLock = null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.casting_notification_title),
                        NotificationManager.IMPORTANCE_LOW);
                channel.setDescription(getString(R.string.casting_notification_text));
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openAppIntent, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.casting_notification_title))
                .setContentText(getString(R.string.casting_notification_text))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        releaseLocks();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
