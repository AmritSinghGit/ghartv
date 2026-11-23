package in.ghartv.nova;

/**
 * The one place for held-key speed (owner 13 Nov: slow first, faster the longer it is held, to a top speed a
 * person can still stop on; one tap = one move at once; release resets). Used by every list (home, guide, IPTV)
 * and by the pointer. Owner 22 Nov: the speed must keep rising with the time the key stays down, so the curve is
 * continuous (no steps). The numbers are a first guess (no key-repeat logs exist); tune them here only.
 */
public final class HeldKeyRamp {
    /** Gap between two list moves at the moment the key goes down (owner 15 Nov: 900 ms was the agreed slow start). */
    public static final long START_GAP_MS = 900;
    /** Gap between two list moves at top speed (about 4.4 moves a second, the fastest a person can still stop on). */
    public static final long TOP_GAP_MS = 225;
    /** Hold time after which the top speed is reached; the gap falls in a straight line from START to TOP over this time. */
    public static final long RAMP_MS = 6000;
    /** Pointer speed in dp per second when the key goes down, and at top (owner 18 Nov: slow first, then faster). */
    public static final float POINTER_START_DP_PER_SEC = 120f;
    public static final float POINTER_TOP_DP_PER_SEC = 420f;

    private int key = -1;
    private long lastMoveAt;

    /** Minimum gap in ms between two list moves after this long held; falls smoothly from START to TOP over RAMP. */
    public static long gapMs(long heldMs) {
        float f = Math.min(1f, Math.max(0, heldMs) / (float) RAMP_MS);
        return Math.round(START_GAP_MS - (START_GAP_MS - TOP_GAP_MS) * f);
    }

    /** Pointer speed in dp per second after this long held; rises smoothly like the list speed. */
    public static float pointerDpPerSec(long heldMs) {
        float f = Math.min(1f, Math.max(0, heldMs) / (float) RAMP_MS);
        return POINTER_START_DP_PER_SEC + (POINTER_TOP_DP_PER_SEC - POINTER_START_DP_PER_SEC) * f;
    }

    /** True if this key event should move the selection; false to swallow a too-fast repeat. */
    public boolean allow(int keyCode, long downTime, long eventTime, int repeatCount) {
        if (repeatCount == 0 || keyCode != key) {
            key = keyCode;
            lastMoveAt = eventTime;
            return true;
        }
        long held = Math.max(0, eventTime - downTime);
        if (eventTime - lastMoveAt >= gapMs(held)) {
            lastMoveAt = eventTime;
            return true;
        }
        return false;
    }

    public void reset() { key = -1; }
}
