package net.xvis.utils;

import android.os.Build;

/**
 * Created by kimy2 on 11/22/17.
 */

public class SystemUtils {
    public static int getApiLevel() {
        return Build.VERSION.SDK_INT;
    }
}
