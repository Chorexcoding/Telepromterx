package com.rajesh.floatprompter;

import android.view.Choreographer;

/**
 * Time-based auto scroller driven by Choreographer (vsync).
 * Distance per frame = speed * elapsed time, so speed stays constant even if frames are dropped.
 * Sub-pixel position is kept in a float, so slow speeds scroll smoothly instead of stepping.
 * Must be created and used on the main thread.
 */
public final class ScrollEngine implements Choreographer.FrameCallback {

    /** The view being scrolled. */
    public interface Host {
        int getScrollY();
        void scrollToY(int y);
        int getMaxScroll();
        void onPlayStateChanged(boolean playing);
    }

    private static final float MAX_FRAME_SECONDS = 0.1f;

    private final Host host;
    private final Choreographer choreographer = Choreographer.getInstance();

    private boolean playing;
    private boolean suspended;
    private float pos;
    private long lastNanos;
    private float pixelsPerSecond;

    public ScrollEngine(Host host) {
        this.host = host;
    }

    public void setSpeed(float pixelsPerSecond) {
        this.pixelsPerSecond = pixelsPerSecond;
    }

    public boolean isPlaying() { return playing; }

    public void play() {
        if (playing) return;
        int max = host.getMaxScroll();
        if (max > 0 && host.getScrollY() >= max - 1) {
            host.scrollToY(0); // finished earlier: start again from the top
        }
        playing = true;
        suspended = false;
        pos = host.getScrollY();
        lastNanos = 0;
        choreographer.postFrameCallback(this);
        host.onPlayStateChanged(true);
    }

    public void pause() {
        if (!playing) return;
        playing = false;
        suspended = false;
        choreographer.removeFrameCallback(this);
        host.onPlayStateChanged(false);
    }

    public void toggle() {
        if (playing) pause(); else play();
    }

    /** Jump to a position without changing play state. */
    public void seekTo(int y) {
        pos = y;
        host.scrollToY(y);
    }

    /** Called when the finger touches the text so manual scrolling is not fought by the engine. */
    public void suspend() {
        if (playing && !suspended) {
            suspended = true;
            choreographer.removeFrameCallback(this);
        }
    }

    /** Called when the finger lifts: continue from wherever the user left the text. */
    public void resumeAfterSuspend() {
        if (!suspended) return;
        suspended = false;
        if (playing) {
            pos = host.getScrollY();
            lastNanos = 0;
            choreographer.postFrameCallback(this);
        }
    }

    /** Stop without notifying the host (used while tearing down). */
    public void release() {
        playing = false;
        suspended = false;
        choreographer.removeFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!playing || suspended) return;
        if (lastNanos == 0) lastNanos = frameTimeNanos;
        float dt = (frameTimeNanos - lastNanos) / 1_000_000_000f;
        lastNanos = frameTimeNanos;
        if (dt > MAX_FRAME_SECONDS) dt = MAX_FRAME_SECONDS; // no jump after a stall

        pos += pixelsPerSecond * dt;
        int max = host.getMaxScroll();
        if (pos >= max) {
            host.scrollToY(max);
            pos = max;
            playing = false;
            host.onPlayStateChanged(false); // auto-stop at the end of the script
            return;
        }
        host.scrollToY(Math.round(pos));
        choreographer.postFrameCallback(this);
    }
}
