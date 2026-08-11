package com.nilaweera.aalearn;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String FILE = "aalearn";
    private static final String KEY_URL = "start_url";

    public static final String MOBILE = "https://m.youtube.com/feed/subscriptions";
    public static final String TV = "https://www.youtube.com/tv";

    private Prefs() {}

    private static SharedPreferences p(Context ctx) {
        return ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String startUrl(Context ctx) {
        return p(ctx).getString(KEY_URL, MOBILE);
    }

    public static void setStartUrl(Context ctx, String url) {
        p(ctx).edit().putString(KEY_URL, url).apply();
    }
}
