package com.hui.zxing.scanner.util;

import android.content.Context;

public class DensityUtil {
	public static int getScreenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }
}
