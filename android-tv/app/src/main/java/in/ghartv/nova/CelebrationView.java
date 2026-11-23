package in.ghartv.nova;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Static, allocation-light birthday decoration. No animation or timer is used. */
public final class CelebrationView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] colors = {
            Color.rgb(255, 202, 92),
            Color.rgb(255, 105, 171),
            Color.rgb(77, 239, 202),
            Color.rgb(110, 231, 255),
            Color.rgb(183, 132, 255)
    };

    public CelebrationView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(false);
        setFocusable(false);
        setAlpha(FamilyTheme.isBirthday(context) ? .58f : 0f);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!FamilyTheme.isBirthday(getContext())) return;
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        // Soft celebratory bokeh in the corners.
        for (int i = 0; i < 9; i++) {
            paint.setColor(Color.argb(22 + i * 2,
                    Color.red(colors[i % colors.length]),
                    Color.green(colors[i % colors.length]),
                    Color.blue(colors[i % colors.length])));
            float radius = TvUi.dp(getContext(), 22 + i * 8);
            canvas.drawCircle(width * (.06f + i * .028f), height * (.09f + (i % 3) * .055f), radius, paint);
        }

        // Deterministic confetti strokes.
        for (int i = 0; i < 42; i++) {
            int x = Math.floorMod(i * 211 + 47, width);
            int y = Math.floorMod(i * 127 + 29, Math.max(1, height));
            paint.setColor(colors[i % colors.length]);
            paint.setStrokeWidth(TvUi.dp(getContext(), 2.0f + (i % 2)));
            float size = TvUi.dp(getContext(), 4 + (i % 5));
            canvas.save();
            canvas.rotate((i * 31) % 180, x, y);
            canvas.drawLine(x - size, y, x + size, y, paint);
            canvas.restore();
        }

        // Three simple balloons at the upper-right; still static and cheap.
        float baseX = width * .86f;
        float baseY = height * .10f;
        for (int i = 0; i < 3; i++) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(110,
                    Color.red(colors[(i + 1) % colors.length]),
                    Color.green(colors[(i + 1) % colors.length]),
                    Color.blue(colors[(i + 1) % colors.length])));
            float cx = baseX + TvUi.dp(getContext(), i * 34 - 24);
            float cy = baseY + TvUi.dp(getContext(), (i % 2) * 18);
            float rx = TvUi.dp(getContext(), 18);
            float ry = TvUi.dp(getContext(), 23);
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(TvUi.dp(getContext(), 1.2f));
            paint.setColor(Color.argb(115, 255, 255, 255));
            canvas.drawLine(cx, cy + ry, cx + TvUi.dp(getContext(), (i - 1) * 8), cy + TvUi.dp(getContext(), 74), paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }
}
