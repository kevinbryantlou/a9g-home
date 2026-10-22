package com.beacon2.home;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.provider.Settings;
import android.util.Log;

/** The five home rows and how each one launches. */
final class Sources {
    private Sources() {}

    static final String TAG = "A9GHome";

    /** PS4 sits on HDMI 2; HW2 is the fixed port-2 hardware input (not the CEC child id). */
    static final String PS4_INPUT = "com.sony.dtv.tvinput.external/.ExternalTvInputService/HW2";
    static final ComponentName SONY_TV_APP = new ComponentName("com.sony.dtv.tvx", "com.sony.dtv.tvx.MainActivity");
    static final ComponentName SONY_INPUTS = new ComponentName("com.android.systemui",
            "com.sony.dtv.systemui.quicklauncher.LaunchActivity");
    static final ComponentName STOCK_HOME = new ComponentName("com.google.android.tvlauncher",
            "com.google.android.tvlauncher.MainActivity");

    static final class Source {
        final String name, pkg, tag;
        Source(String name, String pkg, String tag) {
            this.name = name; this.pkg = pkg; this.tag = tag;
        }
        boolean isInput() { return pkg == null; }
    }

    /** Kevin's order. */
    static final Source[] HOME = {
            new Source("YouTube", "com.google.android.youtube.tv", null),
            new Source("Prime Video", "com.amazon.amazonvideo.livingroom", null),
            new Source("Netflix", "com.netflix.ninja", null),
            new Source("PS4", null, "HDMI 2"),
            new Source("Spotify", "com.spotify.tv.android", null),
    };

    static Intent appIntent(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    static Intent ps4Intent() {
        return new Intent(Intent.ACTION_VIEW)
                .setDataAndType(TvContract.buildChannelUriForPassthroughInput(PS4_INPUT),
                        "vnd.android.cursor.item/channel")
                .setComponent(SONY_TV_APP)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    static Intent inputsIntent() {
        return new Intent("com.android.tv.action.VIEW_INPUTS")
                .setComponent(SONY_INPUTS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    static Intent settingsIntent() {
        return new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    static Intent stockHomeIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setComponent(STOCK_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    static Intent intentFor(Context c, Source s) {
        return s.isInput() ? ps4Intent() : appIntent(c, s.pkg);
    }

    /** Start an intent without ever throwing into the home screen. */
    static boolean start(Context c, Intent i) {
        if (i == null) return false;
        try {
            c.startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "launch failed: " + i, e);
            return false;
        }
    }

    /**
     * The app's own TV banner, decoded at (about) display size. Returns null rather than
     * throwing; callers draw a text tile instead.
     */
    static Bitmap banner(Context c, String pkg, int width, int height) {
        try {
            PackageManager pm = c.getPackageManager();
            Drawable d = pm.getApplicationBanner(pkg);
            if (d == null) {
                Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (li != null) d = pm.getActivityBanner(li);
            }
            if (d == null) return null;
            return render(d, width, height);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            Log.w(TAG, "no banner for " + pkg, e);
            return null;
        }
    }

    static Bitmap icon(Context c, String pkg, int size) {
        try {
            return render(c.getPackageManager().getApplicationIcon(pkg), size, size);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    private static Bitmap render(Drawable d, int width, int height) {
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            Bitmap src = ((BitmapDrawable) d).getBitmap();
            if (src.getWidth() == width && src.getHeight() == height) return src;
            return Bitmap.createScaledBitmap(src, width, height, true);
        }
        Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        d.setBounds(0, 0, width, height);
        d.draw(canvas);
        return b;
    }

    static Bitmap decodeAssetPhoto(Context c, String path, int targetW, int targetH) {
        try (java.io.InputStream in = c.getAssets().open(path)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeStream(in, null, o);
        } catch (java.io.IOException | RuntimeException | OutOfMemoryError e) {
            Log.w(TAG, "photo decode failed: " + path, e);
            return null;
        }
    }
}
