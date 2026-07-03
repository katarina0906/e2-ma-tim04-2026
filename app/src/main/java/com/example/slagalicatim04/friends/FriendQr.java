package com.example.slagalicatim04.friends;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public final class FriendQr {
    private static final String PREFIX = "slagalica:user:";

    private FriendQr() {
    }

    public static String contentsForUser(String userId) {
        return PREFIX + userId;
    }

    public static String userIdFromContents(String contents) {
        if (contents == null) {
            return "";
        }
        String trimmed = contents.trim();
        if (trimmed.startsWith(PREFIX)) {
            return trimmed.substring(PREFIX.length()).trim();
        }
        return trimmed;
    }

    public static Bitmap bitmapForUser(String userId, int sizePx) throws WriterException {
        BitMatrix matrix = new QRCodeWriter().encode(
                contentsForUser(userId),
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx
        );
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < sizePx; x++) {
            for (int y = 0; y < sizePx; y++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }
}
