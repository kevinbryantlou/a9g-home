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

import java.util.Date;

/**
 * "Type": the service names are the menu. The focused name is full size and white,
 * the rest shrink and fade. A photo slideshow sits behind a neutral dark shade. Focus is managed here rather than by Android's focus search,
 * because the rows move with translationY and focus search ignores transforms.
 */
public class HomeActivity extends Activity {
    private static final int ROWS = Sources.HOME.length;
    private static final String[] BUTTONS = {"Inputs", "All apps", "Settings"};
    private static final long MOVE_MS = 180, TINT_MS = 240, BANNER_MS = 160;
    private static final long IDLE_MS = 45_000, SHIFT_MS = 60_000;
    private static final float UNFOCUSED_SCALE = 0.64f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DecelerateInterpolator decel = new DecelerateInterpolator(1.5f);

    private FrameLayout content;
    private View tint;
    private TextView[] rows = new TextView[ROWS];
    private TextView[] buttons = new TextView[BUTTONS.length];
    private ImageView[] bannerLayers = new ImageView[2];
    private int bannerFront = 0;
    private TextView clock, date, credit;
    private Slideshow slideshow;
    private final Bitmap[] banners = new Bitmap[ROWS];

    /** 0..ROWS-1 are rows, ROWS.. are buttons. */
    private int focus = 0, lastRow = 0, lastButton = 0, launchedRow = -1;
    private boolean idle, resumed, night, consumeKeyUp;
    private int shiftStep;

    // ---- lifecycle -------------------------------------------------------

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        night = Ui.isNight();
        setContentView(build());
        applyNight();
        applyFocus(0, false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, f);
        updateClock();
        loadBannersAsync();
        if (slideshow != null) slideshow.start();
        handler.postDelayed(shift, SHIFT_MS);
        scheduleIdle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        wake(false);
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

        credit = Ui.text(this, 20, 500, 0xFFFFFFFF);
        credit.setAlpha(0.5f);
        FrameLayout.LayoutParams cp = wrap(Gravity.BOTTOM | Gravity.END);
        cp.rightMargin = px(110);
        cp.bottomMargin = px(56);
        root.addView(credit, cp);

        content = new FrameLayout(this);
        root.addView(content, match());

        clock = Ui.text(this, 64, 500, 0xFFFFFFFF);
        clock.setGravity(Gravity.END);
        FrameLayout.LayoutParams clp = wrap(Gravity.TOP | Gravity.END);
        clp.rightMargin = px(110);
        clp.topMargin = px(80);
        content.addView(clock, clp);

        date = Ui.text(this, 26, 500, 0xFFFFFFFF);
        date.setAlpha(0.55f);
        FrameLayout.LayoutParams dp = wrap(Gravity.TOP | Gravity.END);
        dp.rightMargin = px(110);
        dp.topMargin = px(80 + 74);
        content.addView(date, dp);

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
            ImageView iv = new ImageView(this);
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

        slideshow = new Slideshow(this, a, b, credit);
        if (slideshow.isEmpty()) {
            slideshow = null;
            credit.setVisibility(View.GONE);
        }
        return root;
    }

    /** Scale rows toward their baseline so shrunken names keep their line spacing. */
    static void pinPivotToBaseline(final TextView t) {
        t.addOnLayoutChangeListener((v, l, top, r, b, ol, ot, or, ob) -> {
            int base = t.getBaseline();
            if (base > 0) t.setPivotY(base);
        });
    }

    private ImageView photoLayer() {
        ImageView v = new ImageView(this);
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

    /** The only layer between photo and menu: a neutral shade. */
    private void applyShade() {
        // One shade, one number: 40% by day, 60% at night.
        int alpha = slideshow == null ? 0xFF : (night ? 0x99 : 0x66);
        tint.setBackgroundColor((alpha << 24) | 0x0A0A0A);
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
                loaded[i] = s.isInput() ? ps4Banner(w, h) : Sources.banner(app, s.pkg, w, h);
            }
            handler.post(() -> {
                if (isDestroyed()) return;
                System.arraycopy(loaded, 0, banners, 0, ROWS);
                showBanner(lastRow, false);
                applyBannerNight();
            });
        }, "banners").start();
    }

    /** The PS4 has no app banner; draw one: PlayStation blue with "PS4". */
    private Bitmap ps4Banner(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        c.drawColor(0xFF003791);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        p.setTypeface(Ui.font(this, 700));
        p.setTextSize(px(96));
        p.setTextAlign(android.graphics.Paint.Align.CENTER);
        android.graphics.Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText("PS4", w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, p);
        return b;
    }

    // ---- keys ------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
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

        if (code == KeyEvent.KEYCODE_MENU) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.isLongPress()) {
                confirmRestoreStockHome(this);
            }
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
                startActivity(new Intent(this, AllAppsActivity.class));
                break;
            default:
                Sources.start(this, Sources.settingsIntent());
        }
    }

    /** Hand the HOME role back to the stock launcher. Shared with All apps. */
    static void confirmRestoreStockHome(final Activity a) {
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Use the stock home screen?")
                .setMessage("Google's home screen becomes the default again. This app stays installed; "
                        + "open it from the stock app list to switch back.")
                .setPositiveButton("Restore", (d, w) -> {
                    a.getPackageManager().clearPackagePreferredActivities(a.getPackageName());
                    Sources.start(a, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    a.finish();
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
                tint.animate().alpha(0.35f).setDuration(1500).start();
                credit.animate().alpha(0.7f).setDuration(1500).start();
            } else {
                content.animate().alpha(0.5f).setDuration(1500).start();
            }
        }
    };

    private void wake(boolean animate) {
        idle = false;
        long d = animate ? 150 : 0;
        content.animate().cancel();
        tint.animate().cancel();
        credit.animate().cancel();
        content.animate().alpha(1f).setDuration(d).start();
        tint.animate().alpha(1f).setDuration(d).start();
        credit.animate().alpha(0.5f).setDuration(d).start();
        scheduleIdle();
    }

    /** Every minute, jump the layout through a 3x3 grid of +-4 px offsets. */
    private final Runnable shift = new Runnable() {
        @Override public void run() {
            shiftStep = (shiftStep + 1) % 9;
            content.setTranslationX(px((shiftStep % 3 - 1) * 4));
            content.setTranslationY(px((shiftStep / 3 - 1) * 4));
            handler.postDelayed(this, SHIFT_MS);
        }
    };

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            updateClock();
            boolean n = Ui.isNight();
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

    private int textColor() {
        return night ? 0xFFE6DED6 : 0xFFFFFFFF;
    }

    private void applyNight() {
        int c = textColor();
        applyShade();
        for (TextView t : rows) t.setTextColor(c);
        clock.setTextColor(c);
        date.setTextColor(c);
        clock.setAlpha(night ? 0.45f : 0.7f);
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
