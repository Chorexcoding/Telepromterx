package com.rajesh.floatprompter;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/** Runtime permission helpers. */
public final class PermissionManager {
    private PermissionManager() {}

    public static boolean canDrawOverlay(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public static Intent overlaySettingsIntent(Context context) {
        return new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.getPackageName()));
    }

    /** True on Android 13+ when the notification permission has not been granted yet. */
    public static boolean needsNotificationPermission(Context context) {
        return Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
    }
}
