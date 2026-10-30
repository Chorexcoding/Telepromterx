package com.rajesh.floatprompter;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Owns every overlay window (teleprompter, lock chip, orientation hint) and the views inside them.
 * The service only starts/stops it. Never adds a second teleprompter window.
 */
public final class OverlayController implements ScrollEngine.Host {

    public interface Callbacks {
        void onPlayStateChanged(boolean playing);
        void onError(String message);
        void onCloseRequested();
    }

    private interface IntCallback { void on(int value); }

    private static final int TYPE_OVERLAY = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    private static final int BAR_HEIGHT_DP = 48;
    private static final int MIN_WIDTH_DP = 200;
    private static final int MIN_HEIGHT_DP = 160;
    private static final float SPEED_UNIT = 1.2f;

    private final Context ctx;
    private final SettingsManager settings;
    private final Callbacks callbacks;
    private final WindowManager wm;
    private final ScrollEngine engine;
    private final float d;

    private WindowManager.LayoutParams lp;
    private FrameLayout root;
    private LinearLayout bar;
    private LinearLayout chip;
    private ScrollView panelHolder;
    private NoFlingScrollView sv;
    private TextView tv;
    private TextView playBtn;
    private TextView chipPlay;
    private TextView handle;
    private View orientView;
    private final TextView[] orientButtons = new TextView[3];

    private int speed;
    private int size;
    private int opacity;
    private int orient;
    private boolean mirror;
    private boolean destroyed;

    private float startX;
    private float startY;
    private int startW;
    private int startH;
    private int startPx;
    private int startPy;

    public OverlayController(Context context, SettingsManager settings, Callbacks callbacks) {
        this.ctx = context;
        this.settings = settings;
        this.callbacks = callbacks;
        this.wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.d = context.getResources().getDisplayMetrics().density;
        this.engine = new ScrollEngine(this);
    }

    // ---------------------------------------------------------------- public API

    /** Shows the teleprompter, or reloads the script if it is already on screen. */
    public boolean show(String script) {
        if (destroyed) return false;
        if (root != null) {
            engine.pause();
            tv.setText(script);
            engine.seekTo(0);
            return true;
        }
        speed = settings.getSpeed();
        size = settings.getSize();
        opacity = settings.getOpacity();
        mirror = settings.getMirror();
        orient = settings.getOrient();

        buildViews(script);
        try {
            wm.addView(root, lp);
        } catch (RuntimeException e) { // BadTokenException / SecurityException when permission is gone
            clearViews();
            callbacks.onError("Overlay start nahi ho paya. 'Display over other apps' permission check karo.");
            return false;
        }
        applyOrientation();
        return true;
    }

    public void togglePlay() {
        if (root != null) engine.toggle();
    }

    public void onConfigurationChanged() {
        if (root == null) return;
        applyGeometry();
        applyLayout();
    }

    public void destroy() {
        destroyed = true;
        engine.release();
        removeChip();
        removeOrientView();
        if (root != null) {
            try { wm.removeView(root); } catch (RuntimeException ignored) { }
        }
        clearViews();
    }

    // ---------------------------------------------------------------- ScrollEngine.Host

    @Override
    public int getScrollY() { return sv == null ? 0 : sv.getScrollY(); }

    @Override
    public void scrollToY(int y) { if (sv != null) sv.scrollTo(0, y); }

    @Override
    public int getMaxScroll() {
        if (sv == null || tv == null) return 0;
        return Math.max(0, tv.getHeight() - sv.getHeight());
    }

    @Override
    public void onPlayStateChanged(boolean playing) {
        String s = playing ? "\u23F8" : "\u25B6";
        if (playBtn != null) playBtn.setText(s);
        if (chipPlay != null) chipPlay.setText(s);
        callbacks.onPlayStateChanged(playing);
    }

    // ---------------------------------------------------------------- view construction

    private void buildViews(String script) {
        root = new FrameLayout(ctx);
        root.setBackgroundColor(Color.argb(opacity * 255 / 100, 0, 0, 0));
        root.setKeepScreenOn(true);

        LinearLayout column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);

        bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xCC000000);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setMinimumHeight(dp(BAR_HEIGHT_DP));
        TextView dragBtn = button("\u2261 Hilao");
        dragBtn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        playBtn = button("\u25B6");
        TextView restartBtn = button("\u23EE");
        TextView lockBtn = button("\uD83D\uDD12");
        TextView gearBtn = button("\u2699");
        TextView closeBtn = button("\u2715");
        bar.addView(dragBtn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(restartBtn);
        bar.addView(playBtn);
        bar.addView(lockBtn);
        bar.addView(gearBtn);
        bar.addView(closeBtn);

        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xE6222222);
        addSeek(panel, "Speed", 0, 100, speed,
                v -> { speed = v; engine.setSpeed(speedPx()); },
                v -> settings.setSpeed(v));
        addSeek(panel, "Size", 12, 72, size,
                v -> { size = v; tv.setTextSize(v); },
                v -> settings.setSize(v));
        addSeek(panel, "Opacity", 0, 100, opacity,
                v -> { opacity = v; root.setBackgroundColor(Color.argb(v * 255 / 100, 0, 0, 0)); },
                v -> settings.setOpacity(v));

        Switch mirrorSwitch = new Switch(ctx);
        mirrorSwitch.setText("Mirror  ");
        mirrorSwitch.setTextColor(Color.WHITE);
        mirrorSwitch.setPadding(dp(14), dp(8), dp(14), dp(8));
        mirrorSwitch.setChecked(mirror);
        mirrorSwitch.setOnCheckedChangeListener((btn, on) -> {
            mirror = on;
            tv.setScaleX(on ? -1f : 1f);
            settings.setMirror(on);
        });
        panel.addView(mirrorSwitch);

        TextView orientLabel = new TextView(ctx);
        orientLabel.setText("Orientation lock (experimental)");
        orientLabel.setTextColor(Color.WHITE);
        orientLabel.setPadding(dp(14), dp(6), 0, dp(4));
        panel.addView(orientLabel);
        LinearLayout seg = new LinearLayout(ctx);
        seg.setPadding(dp(10), 0, dp(10), dp(10));
        String[] names = {"Auto", "Vertical", "Horizontal"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            TextView t = button(names[i]);
            t.setTextSize(14);
            t.setOnClickListener(view -> {
                orient = k;
                settings.setOrient(k);
                paintOrient();
                applyOrientation();
            });
            orientButtons[i] = t;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            p.setMargins(dp(3), 0, dp(3), 0);
            seg.addView(t, p);
        }
        panel.addView(seg);
        paintOrient();

        panelHolder = new ScrollView(ctx);
        panelHolder.setVisibility(View.GONE);
        panelHolder.addView(panel);

        sv = new NoFlingScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOnTouchListener((view, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                engine.suspend();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                engine.resumeAfterSuspend();
            }
            return false; // let the ScrollView do the manual scrolling
        });
        tv = new TextView(ctx);
        tv.setText(script);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(size);
        tv.setGravity(Gravity.CENTER_HORIZONTAL);
        tv.setLineSpacing(0, 1.3f);
        tv.setShadowLayer(8, 0, 0, Color.BLACK);
        tv.setPadding(dp(16), dp(60), dp(16), dp(260));
        tv.setScaleX(mirror ? -1f : 1f);
        sv.addView(tv);

        column.addView(bar);
        column.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // settings panel floats over the text, just under the top bar
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        panelParams.topMargin = dp(BAR_HEIGHT_DP);
        root.addView(panelHolder, panelParams);

        handle = button("\u25E2");
        root.addView(handle, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END));

        lp = new WindowManager.LayoutParams(100, 100, TYPE_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        applyGeometry();

        dragBtn.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = e.getRawX();
                    startY = e.getRawY();
                    startPx = lp.x;
                    startPy = lp.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    lp.x = startPx + (int) (e.getRawX() - startX);
                    lp.y = startPy + (int) (e.getRawY() - startY);
                    clampToScreen();
                    applyLayout();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    saveGeometry();
                    return true;
                default:
                    return true;
            }
        });
        handle.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = e.getRawX();
                    startY = e.getRawY();
                    startW = lp.width;
                    startH = lp.height;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int[] s = screen();
                    lp.width = clamp(startW + (int) (e.getRawX() - startX),
                            Math.min(dp(MIN_WIDTH_DP), s[0]), s[0] - lp.x);
                    lp.height = clamp(startH + (int) (e.getRawY() - startY),
                            Math.min(dp(MIN_HEIGHT_DP), s[1]), s[1] - lp.y);
                    applyLayout();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    saveGeometry();
                    return true;
                default:
                    return true;
            }
        });

        playBtn.setOnClickListener(view -> engine.toggle());
        restartBtn.setOnClickListener(view -> engine.seekTo(0));
        lockBtn.setOnClickListener(view -> setLocked(true));
        gearBtn.setOnClickListener(view -> panelHolder.setVisibility(
                panelHolder.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        closeBtn.setOnClickListener(view -> callbacks.onCloseRequested());

        engine.setSpeed(speedPx());
    }

    private void addSeek(LinearLayout parent, String label, int min, int max, int init,
                         IntCallback live, IntCallback commit) {
        TextView l = new TextView(ctx);
        l.setText(label + ": " + init);
        l.setTextColor(Color.WHITE);
        l.setPadding(dp(14), dp(6), 0, 0);
        parent.addView(l);
        SeekBar s = new SeekBar(ctx);
        s.setMax(max - min);
        s.setProgress(init - min);
        s.setPadding(dp(18), dp(4), dp(18), dp(4));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int value = progress + min;
                l.setText(label + ": " + value);
                live.on(value);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) { }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                commit.on(sb.getProgress() + min);
            }
        });
        parent.addView(s);
    }

    // ---------------------------------------------------------------- lock chip

    private void setLocked(boolean locked) {
        if (root == null) return;
        if (locked) {
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            bar.setVisibility(View.GONE);
            panelHolder.setVisibility(View.GONE);
            handle.setVisibility(View.GONE);
            addChip();
        } else {
            lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            bar.setVisibility(View.VISIBLE);
            handle.setVisibility(View.VISIBLE);
            removeChip();
        }
        applyLayout();
    }

    private void addChip() {
        removeChip();
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(0xCC000000, 16));
        chipPlay = button(engine.isPlaying() ? "\u23F8" : "\u25B6");
        TextView unlock = button("\uD83D\uDD13");
        chipPlay.setOnClickListener(view -> engine.toggle());
        unlock.setOnClickListener(view -> setLocked(false));
        c.addView(chipPlay);
        c.addView(unlock);
        WindowManager.LayoutParams cp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                TYPE_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        cp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        cp.x = dp(6);
        try {
            wm.addView(c, cp);
            chip = c;
        } catch (RuntimeException e) {
            chipPlay = null;
            // could not show the unlock chip: never leave the user locked out
            setLocked(false);
        }
    }

    private void removeChip() {
        if (chip != null) {
            try { wm.removeView(chip); } catch (RuntimeException ignored) { }
            chip = null;
        }
        chipPlay = null;
    }

    // ---------------------------------------------------------------- orientation hint window

    private void paintOrient() {
        for (int i = 0; i < 3; i++) {
            orientButtons[i].setBackground(rounded(i == orient ? 0xFF2563EB : 0xFF3A3A42, 10));
        }
    }

    /**
     * Tiny invisible window that requests an orientation. Whether other apps follow it depends
     * on the phone maker, so this is labelled experimental in the UI.
     */
    private void applyOrientation() {
        removeOrientView();
        if (root == null || orient == SettingsManager.ORIENT_AUTO) return;
        View v = new View(ctx);
        WindowManager.LayoutParams o = new WindowManager.LayoutParams(1, 1, TYPE_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        o.gravity = Gravity.TOP | Gravity.START;
        o.screenOrientation = orient == SettingsManager.ORIENT_PORTRAIT
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
        try {
            wm.addView(v, o);
            orientView = v;
        } catch (RuntimeException e) {
            orientView = null;
        }
    }

    private void removeOrientView() {
        if (orientView != null) {
            try { wm.removeView(orientView); } catch (RuntimeException ignored) { }
            orientView = null;
        }
    }

    // ---------------------------------------------------------------- geometry

    private int[] screen() {
        if (Build.VERSION.SDK_INT >= 30) {
            Rect b = wm.getMaximumWindowMetrics().getBounds();
            return new int[]{b.width(), b.height()};
        }
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        return new int[]{dm.widthPixels, dm.heightPixels};
    }

    private float[] defaultWindow(boolean landscape, int[] s) {
        float w = landscape ? 0.62f : 0.92f;
        float h = landscape ? 0.5f : 0.4f;
        float x = (1 - w) / 2f;
        float y = (landscape ? dp(8) : dp(120)) / (float) s[1];
        return new float[]{x, y, w, h};
    }

    /** Loads the saved size/position for the current screen orientation. */
    private void applyGeometry() {
        int[] s = screen();
        boolean landscape = s[0] > s[1];
        float[] g = settings.getWindow(landscape);
        if (g == null) g = defaultWindow(landscape, s);
        lp.width = Math.round(g[2] * s[0]);
        lp.height = Math.round(g[3] * s[1]);
        lp.x = Math.round(g[0] * s[0]);
        lp.y = Math.round(g[1] * s[1]);
        clampToScreen();
    }

    private void saveGeometry() {
        int[] s = screen();
        if (s[0] <= 0 || s[1] <= 0) return;
        settings.saveWindow(s[0] > s[1],
                (float) lp.x / s[0], (float) lp.y / s[1],
                (float) lp.width / s[0], (float) lp.height / s[1]);
    }

    /** Keeps the whole window inside the screen. */
    private void clampToScreen() {
        int[] s = screen();
        lp.width = clamp(lp.width, Math.min(dp(MIN_WIDTH_DP), s[0]), s[0]);
        lp.height = clamp(lp.height, Math.min(dp(MIN_HEIGHT_DP), s[1]), s[1]);
        lp.x = clamp(lp.x, 0, s[0] - lp.width);
        lp.y = clamp(lp.y, 0, s[1] - lp.height);
    }

    private void applyLayout() {
        if (root == null) return;
        try {
            wm.updateViewLayout(root, lp);
        } catch (RuntimeException ignored) {
            // view already detached (service is shutting down)
        }
    }

    // ---------------------------------------------------------------- helpers

    private void clearViews() {
        root = null;
        bar = null;
        panelHolder = null;
        sv = null;
        tv = null;
        playBtn = null;
        handle = null;
        lp = null;
    }

    private float speedPx() { return speed * d * SPEED_UNIT; }

    private int dp(int v) { return (int) (v * d); }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(v, hi)); }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView button(String text) {
        TextView b = new TextView(ctx);
        b.setText(text);
        b.setTextSize(19);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        return b;
    }

    /** ScrollView without fling, so manual scrolling never fights the auto-scroll engine. */
    private static final class NoFlingScrollView extends ScrollView {
        NoFlingScrollView(Context context) { super(context); }

        @Override
        public void fling(int velocityY) { }
    }
}
