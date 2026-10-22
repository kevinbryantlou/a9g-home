package com.beacon2.home;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.TextView;

import java.util.Calendar;

/** Shared look: one bundled typeface, pixel sizes designed at 1920x1080. */
final class Ui {
    private Ui() {}

    private static Typeface regular, semibold, bold;

    /** Design pixels (1920x1080 reference) to real pixels. */
    static float px(Context c, float designPx) {
        return designPx * c.getResources().getDisplayMetrics().widthPixels / 1920f;
    }

    static Typeface font(Context c, int weight) {
        if (bold == null) {
            regular = load(c, 500);
            semibold = load(c, 600);
            bold = load(c, 700);
        }
        return weight >= 700 ? bold : weight >= 600 ? semibold : regular;
    }

    private static Typeface load(Context c, int weight) {
        try {
            return new Typeface.Builder(c.getAssets(), "fonts/SchibstedGrotesk.ttf")
                    .setFontVariationSettings("'wght' " + weight)
                    .build();
        } catch (RuntimeException e) {
            return Typeface.create(Typeface.SANS_SERIF, weight >= 700 ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    static TextView text(Context c, float sizeDesignPx, int weight, int color) {
        TextView t = new TextView(c);
        t.setTypeface(font(c, weight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(c, sizeDesignPx));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    static GradientDrawable pill(int fill, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        return d;
    }

    /** 20:00 to 06:30 by the local clock; there is no light sensor. */
    static boolean isNight() {
        Calendar now = Calendar.getInstance();
        int minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        return minutes >= 20 * 60 || minutes < 6 * 60 + 30;
    }
}
