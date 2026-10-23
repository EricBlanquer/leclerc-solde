package com.eric.leclercsolde;

import android.graphics.Bitmap;
import android.graphics.Color;

public final class BarcodeLocator {

    public static final int NOT_FOUND = -1;

    private static final int DARK_LUMINANCE = 128;
    private static final int MIN_TRANSITIONS = 40;
    private static final float MAX_ROW_DIFFERENCE = 0.02f;
    private static final int MIN_ROWS_WIDTH_DIVISOR = 100;

    private BarcodeLocator() {
    }

    public static int centerY(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int maxDifferences = (int) (width * MAX_ROW_DIFFERENCE);
        int minRows = width / MIN_ROWS_WIDTH_DIVISOR;
        int[] pixels = new int[width];
        boolean[] previous = new boolean[width];
        boolean[] current = new boolean[width];
        long bestScore = 0;
        int bestCenter = NOT_FOUND;
        long runScore = 0;
        int runStart = 0;
        int runLength = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1);
            int transitions = 0;
            int differences = 0;
            for (int x = 0; x < width; x++) {
                current[x] = isDark(pixels[x]);
                if (x > 0 && current[x] != current[x - 1]) {
                    transitions++;
                }
                if (current[x] != previous[x]) {
                    differences++;
                }
            }
            if (transitions >= MIN_TRANSITIONS && differences <= maxDifferences) {
                if (runLength == 0) {
                    runStart = y;
                    runScore = 0;
                }
                runLength++;
                runScore += transitions;
                if (runLength >= minRows && runScore > bestScore) {
                    bestScore = runScore;
                    bestCenter = runStart + runLength / 2;
                }
            } else {
                runLength = 0;
            }
            boolean[] swap = previous;
            previous = current;
            current = swap;
        }
        return bestCenter;
    }

    private static boolean isDark(int pixel) {
        int luminance = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000;
        return luminance < DARK_LUMINANCE;
    }
}
