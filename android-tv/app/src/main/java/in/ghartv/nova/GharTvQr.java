package in.ghartv.nova;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;
import java.util.EnumMap;
import java.util.Map;

/** Local offline QR rendering: no remote QR service receives the pairing secret. */
final class GharTvQr {
    static Bitmap bitmap(String url, int pixels) throws Exception {
        Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN,2);
        BitMatrix bits=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,Math.max(160,pixels),
                Math.max(160,pixels),hints);
        int w=bits.getWidth(),h=bits.getHeight();
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)
            bitmap.setPixel(x,y,bits.get(x,y)?Color.rgb(6,20,27):Color.WHITE);
        return bitmap;
    }
}
