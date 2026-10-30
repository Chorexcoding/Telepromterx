package com.rajesh.floatprompter;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Single place for every persisted value.
 * Uses the same "tp" file and key names as version 1, so existing user data keeps working.
 */
public final class SettingsManager {
    public static final String PREFS_NAME = "tp";

    public static final int ORIENT_AUTO = 0;
    public static final int ORIENT_PORTRAIT = 1;
    public static final int ORIENT_LANDSCAPE = 2;

    private static final String K_SCRIPT = "script";
    private static final String K_SPEED = "speed";
    private static final String K_SIZE = "size";
    private static final String K_OPACITY = "opacity";
    private static final String K_MIRROR = "mirror";
    private static final String K_ORIENT = "orient";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getScript() { return prefs.getString(K_SCRIPT, ""); }

    public void setScript(String script) {
        prefs.edit().putString(K_SCRIPT, script == null ? "" : script).apply();
    }

    public int getSpeed() { return prefs.getInt(K_SPEED, 25); }

    public void setSpeed(int v) { prefs.edit().putInt(K_SPEED, v).apply(); }

    public int getSize() { return prefs.getInt(K_SIZE, 28); }

    public void setSize(int v) { prefs.edit().putInt(K_SIZE, v).apply(); }

    /** Background opacity, 0..100. */
    public int getOpacity() { return prefs.getInt(K_OPACITY, 35); }

    public void setOpacity(int v) { prefs.edit().putInt(K_OPACITY, v).apply(); }

    public boolean getMirror() { return prefs.getBoolean(K_MIRROR, false); }

    public void setMirror(boolean v) { prefs.edit().putBoolean(K_MIRROR, v).apply(); }

    public int getOrient() { return prefs.getInt(K_ORIENT, ORIENT_AUTO); }

    public void setOrient(int v) { prefs.edit().putInt(K_ORIENT, v).apply(); }

    /**
     * Saved overlay window geometry for portrait or landscape, as fractions of the screen:
     * {x, y, width, height}. Returns null when nothing has been saved yet.
     */
    public float[] getWindow(boolean landscape) {
        String p = landscape ? "l_" : "p_";
        if (!prefs.contains(p + "w")) return null;
        return new float[]{
                prefs.getFloat(p + "x", 0f),
                prefs.getFloat(p + "y", 0f),
                prefs.getFloat(p + "w", 0.9f),
                prefs.getFloat(p + "h", 0.4f)
        };
    }

    public void saveWindow(boolean landscape, float x, float y, float w, float h) {
        String p = landscape ? "l_" : "p_";
        prefs.edit()
                .putFloat(p + "x", x)
                .putFloat(p + "y", y)
                .putFloat(p + "w", w)
                .putFloat(p + "h", h)
                .apply();
    }
}
