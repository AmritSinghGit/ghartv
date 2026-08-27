package in.ghartv.nova;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

public final class AuroraBackgroundView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long startedAt = SystemClock.uptimeMillis();

    public AuroraBackgroundView(Context context) { super(context); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        paint.setShader(new LinearGradient(0, 0, w, h,
                new int[]{Color.rgb(2, 6, 12), Color.rgb(3, 18, 29), Color.rgb(4, 9, 17)},
                new float[]{0f, .52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        float phase = ((SystemClock.uptimeMillis() - startedAt) % 18000L) / 18000f;
        float cx = w * (.18f + .13f * (float) Math.sin(phase * Math.PI * 2));
        paint.setShader(new RadialGradient(cx, h * .20f, w * .55f,
                new int[]{Color.argb(90, 0, 190, 220), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, h * .20f, w * .55f, paint);

        float cx2 = w * (.86f + .08f * (float) Math.cos(phase * Math.PI * 2));
        paint.setShader(new RadialGradient(cx2, h * .82f, w * .42f,
                new int[]{Color.argb(64, 103, 70, 255), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx2, h * .82f, w * .42f, paint);

        paint.setShader(null);
        paint.setColor(Color.argb(18, 115, 245, 194));
        paint.setStrokeWidth(1f);
        int spacing = Math.max(48, TvUi.dp(getContext(), 56));
        for (int x = 0; x < w; x += spacing) canvas.drawLine(x, h * .58f, x, h, paint);
        for (int y = (int) (h * .58f); y < h; y += spacing) canvas.drawLine(0, y, w, y, paint);
        postInvalidateDelayed(40L);
    }
}
