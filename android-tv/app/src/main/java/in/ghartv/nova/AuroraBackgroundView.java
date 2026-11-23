package in.ghartv.nova;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Static glass-and-aurora background: attractive on television without a frame timer. */
public final class AuroraBackgroundView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public AuroraBackgroundView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        int[] background = FamilyTheme.isBirthday(getContext())
                ? new int[]{Color.rgb(10, 5, 20), Color.rgb(28, 11, 38), Color.rgb(4, 14, 29)}
                : new int[]{Color.rgb(1, 6, 15), Color.rgb(5, 20, 38), Color.rgb(10, 10, 31)};
        paint.setShader(new LinearGradient(0, 0, w, h, background,
                new float[]{0f, .56f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        int first = FamilyTheme.isBirthday(getContext())
                ? Color.argb(104, 255, 91, 167)
                : Color.argb(86, 0, 202, 224);
        paint.setShader(new RadialGradient(w * .16f, h * .12f, w * .60f,
                new int[]{first, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(w * .16f, h * .12f, w * .60f, paint);

        int second = FamilyTheme.isBirthday(getContext())
                ? Color.argb(82, 255, 183, 76)
                : Color.argb(72, 128, 94, 255);
        paint.setShader(new RadialGradient(w * .90f, h * .84f, w * .48f,
                new int[]{second, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(w * .90f, h * .84f, w * .48f, paint);

        paint.setShader(null);
        paint.setColor(Color.argb(14,
                Color.red(FamilyTheme.accent(getContext())),
                Color.green(FamilyTheme.accent(getContext())),
                Color.blue(FamilyTheme.accent(getContext()))));
        paint.setStrokeWidth(1f);
        int spacing = Math.max(52, TvUi.dp(getContext(), 60));
        for (int x = 0; x < w; x += spacing) canvas.drawLine(x, h * .63f, x, h, paint);
        for (int y = (int) (h * .63f); y < h; y += spacing) canvas.drawLine(0, y, w, y, paint);
    }
}
