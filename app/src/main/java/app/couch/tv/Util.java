package app.couch.tv;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import java.util.Locale;

public final class Util {
    private Util() {}

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static String viewers(int n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) {
            double k = n / 1000.0;
            return (k < 10 ? String.format(Locale.GERMAN, "%.1f", k) : String.valueOf(Math.round(k))) + "K";
        }
        return String.format(Locale.GERMAN, "%.1f", n / 1_000_000.0) + " Mio.";
    }

    public static String viewersLong(int n) {
        return viewers(n) + (n == 1 ? " Zuschauer" : " Zuschauer");
    }

    public static String safe(String s) {
        return s == null ? "" : s;
    }

    /** Vorschaubild mit Cache-Breaker, damit es jede Minute frisch ist. */
    public static String fresh(String url) {
        if (url == null || url.isEmpty()) return url;
        long minute = System.currentTimeMillis() / 60000;
        return url + (url.contains("?") ? "&" : "?") + "t=" + minute;
    }

    public static void avatar(ImageView v, String url) {
        Glide.with(v).load(url).circleCrop().placeholder(R.drawable.avatar_placeholder).into(v);
    }

    public static void thumb(ImageView v, String url) {
        Glide.with(v).load(fresh(url))
                .transform(new CenterCrop(), new RoundedCorners(dp(v.getContext(), 16)))
                .placeholder(R.drawable.thumb_bg)
                .into(v);
    }
}
