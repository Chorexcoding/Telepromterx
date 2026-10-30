package com.rajesh.floatprompter;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ_NOTIFICATIONS = 1001;
    private static final long AUTOSAVE_DELAY_MS = 600;

    private SettingsManager settings;
    private EditText et;
    private int orient;
    private float d = 1;
    private final TextView[] orientButtons = new TextView[3];
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autosave = this::saveScriptNow;

    private int dp(int v) { return (int) (v * d); }

    private GradientDrawable box(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView txt(String t, int sz, int color) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(sz);
        v.setTextColor(color);
        return v;
    }

    private TextView pill(String t) {
        TextView v = txt(t, 14, Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(16), dp(8), dp(16), dp(8));
        v.setBackground(box(0xFF2A2A32, 20));
        return v;
    }

    private void paintOrient() {
        for (int i = 0; i < 3; i++) {
            orientButtons[i].setBackground(box(i == orient ? 0xFF2563EB : 0xFF2A2A32, 12));
            orientButtons[i].setTextColor(i == orient ? Color.WHITE : 0xFFB0B0BA);
        }
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        d = getResources().getDisplayMetrics().density;
        settings = new SettingsManager(this);
        orient = settings.getOrient();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121216);
        root.setFitsSystemWindows(true);

        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(14), dp(18), dp(18));
        root.addView(c, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        TextView title = txt("Float Teleprompter", 26, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        c.addView(title);
        TextView sub = txt("Script paste karo, Start dabao, fir Camera app kholo. Script uske upar tairegi.",
                14, 0xFF9A9AA6);
        sub.setPadding(0, dp(4), 0, dp(12));
        c.addView(sub);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView paste = pill("Paste");
        TextView clear = pill("Clear");
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, 0, dp(8), dp(8));
        row.addView(paste, rp);
        row.addView(clear, rp);
        c.addView(row);

        et = new EditText(this);
        et.setGravity(Gravity.TOP);
        et.setHint("Apni script yahan likho ya paste karo...");
        et.setHintTextColor(0xFF6A6A76);
        et.setTextColor(Color.WHITE);
        et.setTextSize(17);
        et.setBackground(box(0xFF1E1E26, 14));
        et.setPadding(dp(14), dp(14), dp(14), dp(14));
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(8);
        et.setText(settings.getScript());
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int n) { }
            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(autosave);
                handler.postDelayed(autosave, AUTOSAVE_DELAY_MS);
            }
        });
        c.addView(et, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        paste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = cm != null ? cm.getPrimaryClip() : null;
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence s = clip.getItemAt(0).coerceToText(this);
                if (s != null) et.setText(s);
            }
        });
        clear.setOnClickListener(v -> et.setText(""));

        TextView ol = txt("Orientation lock (experimental)", 14, 0xFF9A9AA6);
        ol.setPadding(0, dp(14), 0, dp(2));
        c.addView(ol);
        TextView note = txt("Sirf kuch phones par camera app ko follow karwata hai. Guarantee nahi hai.",
                12, 0xFF6A6A76);
        note.setPadding(0, 0, 0, dp(6));
        c.addView(note);

        LinearLayout seg = new LinearLayout(this);
        String[] names = {"Auto", "Vertical", "Horizontal"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            TextView t = txt(names[i], 15, Color.WHITE);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(12), 0, dp(12));
            t.setOnClickListener(v -> {
                orient = k;
                settings.setOrient(k);
                paintOrient();
            });
            orientButtons[i] = t;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            p.setMargins(dp(3), 0, dp(3), 0);
            seg.addView(t, p);
        }
        c.addView(seg);
        paintOrient();

        TextView start = txt("Start floating prompter", 18, Color.WHITE);
        start.setTypeface(null, Typeface.BOLD);
        start.setGravity(Gravity.CENTER);
        start.setPadding(0, dp(16), 0, dp(16));
        start.setBackground(box(0xFF2563EB, 14));
        start.setOnClickListener(v -> startPrompter());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.setMargins(0, dp(16), 0, 0);
        c.addView(start, sp);

        setContentView(root);
    }

    private void saveScriptNow() {
        if (et != null && settings != null) {
            settings.setScript(et.getText().toString());
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(autosave);
        saveScriptNow(); // nothing typed is lost when the user leaves the app
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(autosave);
        super.onDestroy();
    }

    private void startPrompter() {
        String s = et.getText().toString().trim();
        if (s.isEmpty()) {
            Toast.makeText(this, "Pehle script likho ya paste karo", Toast.LENGTH_SHORT).show();
            return;
        }
        settings.setScript(s);

        if (!PermissionManager.canDrawOverlay(this)) {
            Toast.makeText(this, "'Display over other apps' allow karo, fir wapas aakar Start dabao",
                    Toast.LENGTH_LONG).show();
            startActivity(PermissionManager.overlaySettingsIntent(this));
            return;
        }
        if (PermissionManager.needsNotificationPermission(this)) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return; // launchService() runs from onRequestPermissionsResult
        }
        launchService();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                Toast.makeText(this, "Notification off hai. Pause/Stop prompter ke andar ke buttons se karo.",
                        Toast.LENGTH_LONG).show();
            }
            launchService(); // the prompter works without the notification being visible
        }
    }

    private void launchService() {
        try {
            startForegroundService(new Intent(this, OverlayService.class));
            moveTaskToBack(true);
        } catch (RuntimeException e) {
            Toast.makeText(this, "Prompter start nahi ho paya", Toast.LENGTH_LONG).show();
        }
    }
}
