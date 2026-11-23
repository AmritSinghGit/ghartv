package in.ghartv.nova;

import android.os.Handler;
import android.os.Looper;

public final class ChannelNavigator {
    public interface Listener {
        void onDigits(String digits);
        void onCommit(int channelNumber);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder digits = new StringBuilder();
    private final Listener listener;
    private final Runnable commit = this::commitNow;

    public ChannelNavigator(Listener listener) { this.listener = listener; }

    public void append(int digit) {
        if (digits.length() >= 4) digits.setLength(0);
        digits.append(digit);
        listener.onDigits(digits.toString());
        handler.removeCallbacks(commit);
        handler.postDelayed(commit, digits.length() >= 4 ? 180L : 1100L);
    }

    public void commitNow() {
        handler.removeCallbacks(commit);
        if (digits.length() == 0) return;
        try { listener.onCommit(Integer.parseInt(digits.toString())); }
        finally { digits.setLength(0); }
    }

    public void clear() {
        handler.removeCallbacks(commit);
        digits.setLength(0);
    }
}
