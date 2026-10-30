package com.rajesh.floatprompter;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

/** Foreground service that hosts the floating teleprompter. All window logic lives in OverlayController. */
public class OverlayService extends Service implements OverlayController.Callbacks {

    private SettingsManager settings;
    private OverlayController controller;

    @Override
    public void onCreate() {
        super.onCreate();
        settings = new SettingsManager(this);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (NotificationHelper.ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (NotificationHelper.ACTION_TOGGLE.equals(action)) {
            if (controller != null) controller.togglePlay(); else stopSelf();
            return START_NOT_STICKY;
        }

        // Normal start. startForeground must run first because we were started with startForegroundService.
        NotificationHelper.ensureChannel(this);
        try {
            Notification n = NotificationHelper.build(this, false);
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NotificationHelper.NOTIFICATION_ID, n,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NotificationHelper.NOTIFICATION_ID, n);
            }
        } catch (RuntimeException e) {
            Toast.makeText(this, "Service start nahi ho payi", Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!PermissionManager.canDrawOverlay(this)) {
            Toast.makeText(this, "'Display over other apps' permission off hai", Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (controller == null) controller = new OverlayController(this, settings, this);
        // The script is read from storage, not from the Intent, so long scripts cannot overflow the Binder limit.
        controller.show(settings.getScript());
        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (controller != null) controller.onConfigurationChanged();
    }

    @Override
    public void onDestroy() {
        if (controller != null) {
            controller.destroy();
            controller = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    // OverlayController.Callbacks

    @Override
    public void onPlayStateChanged(boolean playing) {
        NotificationHelper.update(this, playing);
    }

    @Override
    public void onError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        stopSelf();
    }

    @Override
    public void onCloseRequested() {
        stopSelf();
    }
}
