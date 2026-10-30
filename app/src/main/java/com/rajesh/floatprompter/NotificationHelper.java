package com.rajesh.floatprompter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;

/** Builds the foreground-service notification, with Pause/Resume and Stop actions. */
public final class NotificationHelper {
    public static final String CHANNEL_ID = "tp";
    public static final int NOTIFICATION_ID = 1;
    public static final String ACTION_TOGGLE = "com.rajesh.floatprompter.action.TOGGLE";
    public static final String ACTION_STOP = "com.rajesh.floatprompter.action.STOP";

    private NotificationHelper() {}

    public static void ensureChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Teleprompter", NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    public static Notification build(Context context, boolean playing) {
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent togglePi = servicePendingIntent(context, 1, ACTION_TOGGLE);
        PendingIntent stopPi = servicePendingIntent(context, 2, ACTION_STOP);

        Notification.Action toggle = new Notification.Action.Builder(
                Icon.createWithResource(context, playing
                        ? android.R.drawable.ic_media_pause
                        : android.R.drawable.ic_media_play),
                playing ? "Pause" : "Play", togglePi).build();
        Notification.Action stop = new Notification.Action.Builder(
                Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                "Stop", stopPi).build();

        Notification.Builder b = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Teleprompter chal raha hai")
                .setContentText(playing ? "Scroll chal raha hai" : "Paused")
                .setContentIntent(openPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(toggle)
                .addAction(stop);
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }

    public static void update(Context context, boolean playing) {
        context.getSystemService(NotificationManager.class)
                .notify(NOTIFICATION_ID, build(context, playing));
    }

    private static PendingIntent servicePendingIntent(Context context, int requestCode, String action) {
        Intent i = new Intent(context, OverlayService.class).setAction(action);
        return PendingIntent.getService(context, requestCode, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
