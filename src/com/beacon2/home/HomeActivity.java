package com.beacon2.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.res.ColorStateList;

import java.util.Date;

/**
 * "Type": the service names are the menu. The focused name is full size and white,
 * the rest shrink and fade. A photo slideshow sits behind a neutral dark shade. Focus is managed here rather than by Android's focus search,
 * because the rows move with translationY and focus search ignores transforms.
 */
public class HomeActivity extends Activity {
    private static final int ROWS = Sources.HOME.length;
    private static final String[] BUTTONS = {"Inputs", "All apps", "Settings"};
    private static final long MOVE_MS = 180, BANNER_MS = 160;
    private static final long IDLE_MS = 45_000, SHIFT_MS = 60_000;
    private static final float UNFOCUSED_SCALE = 0.64f;
    // Right column type: 128 time, 64 temperature, then one secondary size (26) ranked by
    // opacity: caption and forecast 85%, date 60%, photo credit 50% in both states.
    private static final float FORECAST_ALPHA = 0.85f, IDLE_WEATHER_ALPHA = 0.85f, CREDIT_ALPHA = 0.5f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DecelerateInterpolator decel = new DecelerateInterpolator(1.5f);

    private FrameLayout content;
    private View tint, leftShade;
    private TextView[] rows = new TextView[ROWS];
    private TextView[] buttons = new TextView[BUTTONS.length];
    private ImageView[] bannerLayers = new ImageView[2];
    private int bannerFront = 0;
    private TextView clock, date, credit;
    /** Top-right block: temperature + time, date, caption, forecast. Stays put in idle. */
    private LinearLayout wxBlock, forecast;
    private TextView wxTemp, caption;
    private Weather weather;
    private android.animation.ValueAnimator shadeAnim;
    /** Current shade alphas, top / middle / bottom, 0..1. */
    private final float[] shade = new float[3];
    private Slideshow slideshow;
    private final Bitmap[] banners = new Bitmap[ROWS];

    /** 0..ROWS-1 are rows, ROWS.. are buttons. */
    private int focus = 0, lastRow = 0, lastButton = 0, launchedRow = -1;
    private boolean idle, resumed, night, consumeKeyUp;
    /** Set if the normal screen failed to build; a plain fallback screen is shown instead. */
    private boolean safeMode;
    private boolean returningFromAllApps, bannersStale, packageReceiverRegistered;
    private int shiftStep;

    // ---- lifecycle -------------------------------------------------------

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        night = isNight();
        CrashGuard.install(this);
        if (CrashGuard.crashedRecently(this)) {
            // A crash in any phase (not just here) within the last minute: with Google's
            // launcher disabled, Android would relaunch us straight back into it, forever.
            safeMode = true;
            setContentView(safeView());
            return;
        }
        try {
            setContentView(build());
            applyNight();
            applyFocus(0, false);
        } catch (RuntimeException | OutOfMemoryError e) {
            // With Google's launcher disabled, a crash here would relaunch this activity
            // in a loop. Show a plain screen that still reaches Settings and the way back.
            android.util.Log.e(Sources.TAG, "home failed to build; safe mode", e);
            safeMode = true;
            if (slideshow != null) slideshow.release();
            slideshow = null;
            setContentView(safeView());
        }
    }

    private View safeView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(0xFF121212);
        TextView msg = new TextView(this);
        msg.setText("The home screen hit a problem.");
        msg.setTextColor(0xFFFFFFFF);
        msg.setTextSize(28);
        msg.setGravity(Gravity.CENTER);
        box.addView(msg);
        String[] labels = {"Settings", "Use Google's home screen", "Try again"};
        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            android.widget.Button b = new android.widget.Button(this);
            b.setText(labels[i]);
            b.setOnClickListener(v -> {
                if (which == 0) Sources.start(this, Sources.settingsIntent());
                else if (which == 1) confirmRestoreStockHome(this);
                else {
                    CrashGuard.clear(this);
                    recreate();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(640), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = px(24);
            box.addView(b, lp);
        }
        return box;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (safeMode) return;
        if (!packageReceiverRegistered) {
            IntentFilter pf = new IntentFilter();
            pf.addAction(Intent.ACTION_PACKAGE_ADDED);
            pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
            pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
            pf.addDataScheme("package");
            registerReceiver(packageReceiver, pf);
            packageReceiverRegistered = true;
        }
        boolean n = isNight();
        if (n != night) {
            night = n;
            applyNight();
            applyFocus(focus, false);
        }
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, f);
        updateClock();
        if (banners[0] == null || bannersStale) loadBannersAsync();
        if (returningFromAllApps) {
            returningFromAllApps = false;
            // All apps' banners and window buffers are garbage now; Bitmap memory is only
            // returned on a GC, which may not come for minutes on an idle home screen.
            handler.postDelayed(() -> Runtime.getRuntime().gc(), 1_000);
        }
        if (slideshow != null) slideshow.start();
        if (weather == null) weather = new Weather(this);
        renderWeather(weather.cached());
        handler.post(weatherTick);
        handler.postDelayed(shift, SHIFT_MS);
        scheduleIdle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (!safeMode) wake(false);
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onStop() {
        try {
            unregisterReceiver(timeReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (slideshow != null) slideshow.stop();
        handler.removeCallbacksAndMessages(null);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (packageReceiverRegistered) {
            try {
                unregisterReceiver(packageReceiver);
            } catch (IllegalArgumentException ignored) {
            }
        }
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // Free the two full-screen photos while another app is in front.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && slideshow != null && !resumed) {
            slideshow.release();
        }
    }

    /** Home pressed while already home resets focus; returning from an app keeps it. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (safeMode) return;
        if (resumed) {
            wake(false);
            applyFocus(0, true);
        } else if (launchedRow >= 0) {
            applyFocus(launchedRow, false);
        }
    }

    @Override
    public void onBackPressed() {
        // The home screen has nowhere to go back to.
    }

    // ---- layout ----------------------------------------------------------

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        FrameLayout photos = new FrameLayout(this);
        ImageView a = photoLayer(), b = photoLayer();
        photos.addView(a);
        photos.addView(b);
        root.addView(photos, match());

        tint = new View(this);
        root.addView(tint, match());

        // Extra dimming behind the names only: solid for the first 22% of the width,
        // fading out by 44%, so the rest of the photo stays bright. Set in applyShade().
        leftShade = new View(this);
        root.addView(leftShade, new FrameLayout.LayoutParams(px(845), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));

        credit = Ui.text(this, 20, 500, 0xFFFFFFFF);
        credit.setAlpha(CREDIT_ALPHA);
        FrameLayout.LayoutParams cp = wrap(Gravity.BOTTOM | Gravity.END);
        cp.rightMargin = px(110);
        cp.bottomMargin = px(47);
        root.addView(credit, cp);

        content = new FrameLayout(this);
        root.addView(content, match());

        for (int i = 0; i < ROWS; i++) {
            Sources.Source s = Sources.HOME[i];
            TextView t = Ui.text(this, 150, 700, 0xFFFFFFFF);
            t.setLetterSpacing(-0.035f);
            t.setText(label(s));
            t.setPivotX(0);
            pinPivotToBaseline(t);
            FrameLayout.LayoutParams lp = wrap(Gravity.TOP | Gravity.START);
            lp.leftMargin = px(110);
            content.addView(t, lp);
            rows[i] = t;
        }

        FrameLayout bannerFrame = new FrameLayout(this);
        bannerFrame.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(16));
            }
        });
        bannerFrame.setClipToOutline(true);
        for (int i = 0; i < 2; i++) {
            ImageView iv = new FlatImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            bannerFrame.addView(iv, match());
            bannerLayers[i] = iv;
        }
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(px(480), px(270), Gravity.TOP | Gravity.START);
        bp.leftMargin = px(1330);
        bp.topMargin = px(405);
        content.addView(bannerFrame, bp);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < BUTTONS.length; i++) {
            TextView t = Ui.text(this, 34, 600, 0xFFFFFFFF);
            t.setText(BUTTONS[i]);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(px(26), 0, px(26), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(60));
            lp.rightMargin = px(12);
            bar.addView(t, lp);
            buttons[i] = t;
        }
        FrameLayout.LayoutParams barp = wrap(Gravity.TOP | Gravity.START);
        barp.leftMargin = px(84);
        barp.topMargin = px(950);
        content.addView(bar, barp);

        root.addView(buildWeatherBlock(), wxParams());
        FrameLayout.LayoutParams fp = wrap(Gravity.BOTTOM | Gravity.END);
        fp.rightMargin = px(110);
        // Puts the forecast's baseline on the Inputs / All apps / Settings baseline (~y 992).
        fp.bottomMargin = px(82);
        root.addView(forecast, fp);

        // Optional start photo, e.g. `am start -n com.beacon2.home/.HomeActivity --es photo big_sur.jpg`.
        slideshow = new Slideshow(this, a, b, credit, getIntent().getStringExtra("photo"));
        if (slideshow.isEmpty()) {
            slideshow = null;
            credit.setVisibility(View.GONE);
        }
        return root;
    }

    /**
     * Option D from the weather design review, as adjusted by Kevin: right-aligned at
     * x=1810, top y=64. Row 1: temperature (64 px) then time (128 px), sharing a baseline.
     * Then the date and a sentence caption ("Mostly clear. High 67°, low 59°."). The
     * four-day line sits bottom right above the photo credit. Both live outside the menu
     * layer, so idle only dims them to 85% instead of hiding them.
     */
    private View buildWeatherBlock() {
        wxBlock = new LinearLayout(this);
        wxBlock.setOrientation(LinearLayout.VERTICAL);
        wxBlock.setGravity(Gravity.END);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setBaselineAligned(true);
        wxTemp = Ui.text(this, 64, 400, 0xFFFFFFFF);
        wxTemp.setLetterSpacing(-0.03f);
        wxTemp.setAlpha(0.8f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.rightMargin = px(30);
        top.addView(wxTemp, tp);
        clock = Ui.text(this, 128, 400, 0xFFFFFFFF);
        clock.setLetterSpacing(-0.035f);
        clock.setAlpha(0.95f);
        top.addView(clock);
        // Wrap, not match: as a full-width row its contents would sit at the left edge.
        wxBlock.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        date = Ui.text(this, 26, 500, 0xFFFFFFFF);
        date.setAlpha(0.6f);
        wxBlock.addView(date, blockRow(12));

        caption = Ui.text(this, 26, 500, 0xFFFFFFFF);
        caption.setAlpha(0.85f);
        wxBlock.addView(caption, blockRow(10));

        // The four-day line lives bottom right, above the photo credit (see build()).
        forecast = new LinearLayout(this);
        forecast.setOrientation(LinearLayout.HORIZONTAL);
        forecast.setGravity(Gravity.CENTER_VERTICAL);
        forecast.setAlpha(FORECAST_ALPHA);
        return wxBlock;
    }

    private LinearLayout.LayoutParams blockRow(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(topMargin);
        return lp;
    }

    private FrameLayout.LayoutParams wxParams() {
        FrameLayout.LayoutParams lp = wrap(Gravity.TOP | Gravity.END);
        lp.rightMargin = px(110);
        lp.topMargin = px(64);
        return lp;
    }

    /** Fill or hide the weather parts; the time and date always show. */
    private void renderWeather(Weather.Report r) {
        if (wxTemp == null) return;
        boolean ok = r != null && r.fresh();
        int vis = ok ? View.VISIBLE : View.GONE;
        wxTemp.setVisibility(vis);
        caption.setVisibility(vis);
        if (!ok) {
            forecast.setVisibility(View.GONE);
            return;
        }
        wxTemp.setText(r.temp + "°");
        StringBuilder cap = new StringBuilder(Weather.words(r.code)).append('.');
        if (r.days.length > 0) {
            cap.append(" High ").append(r.days[0].high).append("°, low ").append(r.days[0].low).append("°.");
        }
        caption.setText(cap);
        forecast.removeAllViews();
        if (r.days.length < 5) {
            forecast.setVisibility(View.GONE);
            return;
        }
        int ink = textColor();
        for (int i = 1; i < 5; i++) {
            Weather.Day d = r.days[i];
            TextView name = Ui.text(this, 26, 500, ink);
            name.setText(d.name);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 1) np.leftMargin = px(30);
            forecast.addView(name, np);
            ImageView icon = new ImageView(this);
            icon.setImageResource(Weather.icon(d.code));
            icon.setImageTintList(ColorStateList.valueOf(ink));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(px(28), px(28));
            ip.leftMargin = px(10);
            ip.rightMargin = px(8);
            forecast.addView(icon, ip);
            TextView hi = Ui.text(this, 26, 500, ink);
            hi.setText(d.high + "°");
            forecast.addView(hi);
        }
        forecast.setVisibility(View.VISIBLE);
    }

    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            if (weather != null) weather.refreshIfStale(r -> renderWeather(r));
            renderWeather(weather == null ? null : weather.cached()); // hides data past 2 h
            handler.postDelayed(this, 30 * 60_000L);
        }
    };

    /** Scale rows toward their baseline so shrunken names keep their line spacing. */
    static void pinPivotToBaseline(final TextView t) {
        t.addOnLayoutChangeListener((v, l, top, r, b, ol, ot, or, ob) -> {
            int base = t.getBaseline();
            if (base > 0) t.setPivotY(base);
        });
    }

    private ImageView photoLayer() {
        ImageView v = new FlatImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setLayoutParams(match());
        return v;
    }

    private CharSequence label(Sources.Source s) {
        if (s.tag == null) return s.name;
        SpannableString sp = new SpannableString(s.name + "  " + s.tag);
        int start = s.name.length() + 2;
        sp.setSpan(new AbsoluteSizeSpan(px(34)), start, sp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sp.setSpan(new ForegroundColorSpan(0xB3FFFFFF), start, sp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    // ---- focus -----------------------------------------------------------

    private void applyFocus(int target, boolean animate) {
        focus = target;
        boolean onRow = target < ROWS;
        if (onRow) lastRow = target;
        else lastButton = target - ROWS;

        float dim = night ? 0.35f : 0.45f;
        float[] tops = rowTops(lastRow);
        for (int i = 0; i < ROWS; i++) {
            boolean on = i == lastRow;
            float alpha = on ? (onRow ? 1f : 0.6f) : dim;
            float scale = on ? 1f : UNFOCUSED_SCALE;
            TextView t = rows[i];
            t.animate().cancel();
            if (animate) {
                t.animate().translationY(tops[i]).scaleX(scale).scaleY(scale).alpha(alpha)
                        .setDuration(MOVE_MS).setInterpolator(decel).withLayer().start();
            } else {
                t.setTranslationY(tops[i]);
                t.setScaleX(scale);
                t.setScaleY(scale);
                t.setAlpha(alpha);
            }
        }

        for (int i = 0; i < buttons.length; i++) {
            boolean on = !onRow && i == lastButton;
            buttons[i].setBackground(on ? Ui.pill(0xFFF2F2F2, px(30)) : null);
            buttons[i].setTextColor(on ? 0xFF141414 : textColor());
            buttons[i].setAlpha(on ? 1f : 0.6f);
        }

        showBanner(lastRow, animate);
    }

    /** Row tops in px: unfocused pitch 108, focused 165, plus 56 above the focused row. */
    private float[] rowTops(int focused) {
        float[] tops = new float[ROWS];
        float y = 150;
        for (int i = 0; i < ROWS; i++) {
            if (i == focused && i > 0) y += 56;
            tops[i] = px(y);
            y += i == focused ? 165 : 108;
        }
        return tops;
    }

    /**
     * The only full-screen layer between photo and menu: a neutral top-to-bottom gradient.
     * Home: 30% flat (45% at night). Idle: 35/6/22% (45/12/25% at night), darker at the top
     * so the weather block stays readable while the rest of the photo shows. The left
     * gradient behind the names is separate and fades out in idle.
     */
    private void applyShade() {
        float scale = night ? 1.5f : 1f;
        if (slideshow == null) {
            setShade(1f, 1f, 1f);
        } else if (idle) {
            float[] t = idleShade();
            setShade(t[0], t[1], t[2]);
        } else {
            setShade(0.30f * scale, 0.30f * scale, 0.30f * scale);
        }
        int left = Math.round(0.40f * scale * 255) << 24;
        leftShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{left, left, 0}));
        leftShade.setVisibility(slideshow == null ? View.GONE : View.VISIBLE);
    }

    private float[] homeShade() {
        float a = night ? 0.45f : 0.30f;
        return new float[]{a, a, a};
    }

    private float[] idleShade() {
        return night ? new float[]{0.45f, 0.12f, 0.25f} : new float[]{0.35f, 0.06f, 0.22f};
    }

    private void setShade(float top, float mid, float bottom) {
        shade[0] = top; shade[1] = mid; shade[2] = bottom;
        GradientDrawable g = tint.getBackground() instanceof GradientDrawable
                ? (GradientDrawable) tint.getBackground() : null;
        int[] colors = {shadeColor(top), shadeColor(mid), shadeColor(bottom)};
        if (g == null) {
            tint.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors));
        } else {
            g.setColors(colors);
        }
    }

    private static int shadeColor(float a) {
        return (Math.round(Math.max(0f, Math.min(1f, a)) * 255) << 24) | 0x0A0A0A;
    }

    private void animateShade(final float[] to, long ms) {
        if (shadeAnim != null) shadeAnim.cancel();
        if (slideshow == null) return;
        final float[] from = shade.clone();
        if (ms <= 0) {
            setShade(to[0], to[1], to[2]);
            return;
        }
        shadeAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        shadeAnim.setDuration(ms);
        shadeAnim.addUpdateListener(an -> {
            float f = (float) an.getAnimatedValue();
            setShade(from[0] + (to[0] - from[0]) * f, from[1] + (to[1] - from[1]) * f, from[2] + (to[2] - from[2]) * f);
        });
        shadeAnim.start();
    }

    private void showBanner(int row, boolean animate) {
        Bitmap b = banners[row];
        ImageView out = bannerLayers[bannerFront], in = bannerLayers[1 - bannerFront];
        if (b != null) {
            in.setImageBitmap(b);
            in.setBackground(null);
        } else {
            in.setImageDrawable(null);
            in.setBackgroundColor(Sources.HOME[row].isInput() ? 0xFF003791 : 0xFF2A2A2A);
        }
        in.bringToFront();
        in.animate().cancel();
        if (animate) {
            in.setAlpha(0f);
            in.animate().alpha(1f).setDuration(BANNER_MS).start();
        } else {
            in.setAlpha(1f);
        }
        out.animate().cancel();
        out.setAlpha(animate ? out.getAlpha() : 0f);
        if (animate) out.animate().alpha(0f).setDuration(BANNER_MS).start();
        bannerFront = 1 - bannerFront;
    }

    private void loadBannersAsync() {
        final int w = px(480), h = px(270);
        final Context app = getApplicationContext();
        new Thread(() -> {
            final Bitmap[] loaded = new Bitmap[ROWS];
            for (int i = 0; i < ROWS; i++) {
                Sources.Source s = Sources.HOME[i];
                try {
                    loaded[i] = s.isInput() ? ps4Banner(w, h) : Sources.banner(app, s.pkg, w, h);
                } catch (RuntimeException | OutOfMemoryError e) {
                    loaded[i] = null; // drawn as a text tile instead
                }
                if (loaded[i] == null) loaded[i] = textBanner(s.name, w, h);
            }
            handler.post(() -> {
                if (isDestroyed()) return;
                bannersStale = false;
                System.arraycopy(loaded, 0, banners, 0, ROWS);
                showBanner(lastRow, false);
                applyBannerNight();
            });
        }, "banners").start();
    }

    /** The PS4 has no app banner; draw one: PlayStation blue with "PS4". */
    private Bitmap ps4Banner(int w, int h) {
        return labelBanner("PS4", 0xFF003791, 96, w, h);
    }

    /** Fallback for an app whose banner is missing: its name on a dark tile. */
    private Bitmap textBanner(String name, int w, int h) {
        try {
            return labelBanner(name, 0xFF2A2A2A, 56, w, h);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private Bitmap labelBanner(String label, int bg, int textSize, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        c.drawColor(bg);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        p.setTypeface(Ui.font(this, 700));
        p.setTextSize(px(textSize));
        p.setTextAlign(android.graphics.Paint.Align.CENTER);
        android.graphics.Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(label, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, p);
        return b;
    }

    // ---- keys ------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (safeMode) return super.dispatchKeyEvent(e);
        int code = e.getKeyCode();
        boolean ours = isNavKey(code);

        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (idle) {
                wake(true);
                if (ours) {
                    consumeKeyUp = true;
                    return true; // the first press only wakes the screen
                }
                return super.dispatchKeyEvent(e);
            }
            scheduleIdle();
        } else if (e.getAction() == KeyEvent.ACTION_UP && consumeKeyUp && ours) {
            consumeKeyUp = false;
            return true;
        }

        if (!ours) return super.dispatchKeyEvent(e); // mic, Assistant, volume, Home: system's

        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        boolean animate = e.getRepeatCount() == 0;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (focus >= ROWS) applyFocus(lastRow, animate);
                else if (focus > 0) applyFocus(focus - 1, animate);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (focus < ROWS - 1) applyFocus(focus + 1, animate);
                else if (focus == ROWS - 1) applyFocus(ROWS + lastButton, animate);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (focus > ROWS) applyFocus(focus - 1, animate);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (focus >= ROWS && focus < ROWS + buttons.length - 1) applyFocus(focus + 1, animate);
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (focus >= ROWS) applyFocus(lastRow, true);
                return true;
            default:
                if (e.getRepeatCount() == 0) activate();
                return true;
        }
    }

    private static boolean isNavKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BACK:
                return true;
            default:
                return false;
        }
    }

    private void activate() {
        if (focus < ROWS) {
            final int row = focus;
            final Sources.Source s = Sources.HOME[row];
            final TextView t = rows[row];
            t.setAlpha(0.7f);
            handler.postDelayed(() -> {
                t.setAlpha(1f);
                launchedRow = row;
                if (!Sources.start(this, Sources.intentFor(this, s))) {
                    Toast.makeText(this, "Couldn't open " + s.name, Toast.LENGTH_SHORT).show();
                }
            }, 80);
            return;
        }
        switch (focus - ROWS) {
            case 0:
                if (!Sources.start(this, Sources.inputsIntent())) {
                    Toast.makeText(this, "Couldn't open the input switcher", Toast.LENGTH_SHORT).show();
                }
                break;
            case 1:
                // All apps runs in this process; free the two full-screen photos first.
                if (slideshow != null) {
                    slideshow.stop();
                    slideshow.release();
                }
                returningFromAllApps = true;
                startActivity(new Intent(this, AllAppsActivity.class));
                break;
            default:
                Sources.start(this, Sources.settingsIntent());
        }
    }

    /**
     * Hand HOME back to Google's launcher. Shared by All apps and the safe-mode screen.
     * If the launcher is disabled (the normal state, since its system priority otherwise
     * always wins HOME), this app cannot re-enable it; it opens the launcher's app-info
     * page so the user can press Enable, after which HOME goes back to it on its own.
     */
    static void confirmRestoreStockHome(final Activity a) {
        final boolean enabled = Sources.stockHomeEnabled(a);
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Use Google's home screen?")
                .setMessage(enabled
                        ? "Google's home screen becomes the default again. This app stays installed."
                        : "Google's home screen is switched off. On the next screen choose Enable, "
                        + "then press Home. This app stays installed.")
                .setPositiveButton(enabled ? "Restore" : "Continue", (d, w) -> {
                    if (enabled) {
                        // Enabled, it already wins HOME on priority; just go there.
                        Sources.start(a, Sources.stockHomeIntent());
                    } else {
                        Sources.start(a, Sources.stockHomeAppInfoIntent());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- idle, burn-in, clock, night --------------------------------------

    private void scheduleIdle() {
        handler.removeCallbacks(goIdle);
        handler.postDelayed(goIdle, IDLE_MS);
    }

    /** Idle: the menu fades out and the photo comes up; with no photos, dim to half. */
    private final Runnable goIdle = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            idle = true;
            if (slideshow != null) {
                content.animate().alpha(0f).setDuration(1500).start();
                leftShade.animate().alpha(0f).setDuration(1500).start();
                wxBlock.animate().alpha(IDLE_WEATHER_ALPHA).setDuration(1500).start();
                forecast.animate().alpha(FORECAST_ALPHA * IDLE_WEATHER_ALPHA).setDuration(1500).start();
                animateShade(idleShade(), 1500);
                credit.animate().alpha(CREDIT_ALPHA).setDuration(1500).start();
            } else {
                content.animate().alpha(0.5f).setDuration(1500).start();
            }
        }
    };

    private void wake(boolean animate) {
        idle = false;
        long d = animate ? 150 : 0;
        content.animate().cancel();
        leftShade.animate().cancel();
        wxBlock.animate().cancel();
        forecast.animate().cancel();
        credit.animate().cancel();
        content.animate().alpha(1f).setDuration(d).start();
        leftShade.animate().alpha(1f).setDuration(d).start();
        wxBlock.animate().alpha(1f).setDuration(d).start();
        forecast.animate().alpha(FORECAST_ALPHA).setDuration(d).start();
        animateShade(homeShade(), d);
        credit.animate().alpha(CREDIT_ALPHA).setDuration(d).start();
        scheduleIdle();
    }

    /** Every minute, jump the layout through a 3x3 grid of +-4 px offsets. */
    private final Runnable shift = new Runnable() {
        @Override public void run() {
            shiftStep = (shiftStep + 1) % 9;
            float dx = px((shiftStep % 3 - 1) * 4), dy = px((shiftStep / 3 - 1) * 4);
            content.setTranslationX(dx);
            content.setTranslationY(dy);
            wxBlock.setTranslationX(dx);
            wxBlock.setTranslationY(dy);
            forecast.setTranslationX(dx);
            forecast.setTranslationY(dy);
            handler.postDelayed(this, SHIFT_MS);
        }
    };

    /** Reload banners on the next start if any app was installed, updated or removed. */
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            bannersStale = true;
        }
    };

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            updateClock();
            boolean n = isNight();
            if (n != night) {
                night = n;
                applyNight();
                applyFocus(focus, true);
            }
        }
    };

    private void updateClock() {
        Date now = new Date();
        String pattern = DateFormat.is24HourFormat(this) ? "H:mm" : "h:mm";
        clock.setText(DateFormat.format(pattern, now));
        date.setText(DateFormat.format("EEEE d MMMM", now));
    }

    /** The clock decides, unless launched with `--ez night true|false` for testing. */
    private boolean isNight() {
        Intent i = getIntent();
        return i != null && i.hasExtra("night") ? i.getBooleanExtra("night", false) : Ui.isNight();
    }

    private int textColor() {
        return night ? 0xFFE6DED6 : 0xFFFFFFFF;
    }

    private void applyNight() {
        int c = textColor();
        applyShade();
        for (TextView t : rows) t.setTextColor(c);
        clock.setTextColor(c);
        date.setTextColor(c);
        wxTemp.setTextColor(c);
        caption.setTextColor(c);
        credit.setTextColor(c);
        if (weather != null) renderWeather(weather.cached()); // re-tints the forecast line
        applyBannerNight();
    }

    private void applyBannerNight() {
        ColorMatrixColorFilter filter = null;
        if (night) {
            ColorMatrix m = new ColorMatrix();
            m.setScale(0.7f, 0.7f, 0.7f, 1f);
            filter = new ColorMatrixColorFilter(m);
        }
        for (ImageView iv : bannerLayers) iv.setColorFilter(filter);
    }

    // ---- helpers ---------------------------------------------------------

    private int px(float design) {
        return Math.round(Ui.px(this, design));
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static FrameLayout.LayoutParams wrap(int gravity) {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
    }
}
