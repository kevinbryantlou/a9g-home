package com.beacon2.home;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every TV app as a big-type A-Z list, in the same language as home. The focused row
 * stays at a fixed height and the list moves under it. Ends with the two stock-home
 * escape hatches.
 */
public class AllAppsActivity extends Activity {
    private static final float FOCUS_TOP = 440, PITCH = 84, GAP_AFTER = 40, UNFOCUSED = 0.73f;

    private static final class Entry {
        final String label, pkg;
        final Intent intent;
        final int kind; // 0 app, 1 open stock home, 2 restore stock home
        Bitmap banner;
        Entry(String label, String pkg, Intent intent, int kind) {
            this.label = label; this.pkg = pkg; this.intent = intent; this.kind = kind;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DecelerateInterpolator decel = new DecelerateInterpolator(1.5f);
    private final List<Entry> entries = new ArrayList<>();
    private final List<TextView> rows = new ArrayList<>();
    private View field;
    private ImageView banner;
    private TextView hint;
    private int focus = 0;
    private volatile boolean alive;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        collect();
        setContentView(build());
        applyFocus(0, false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        alive = true;
        loadBannersAsync();
    }

    @Override
    protected void onStop() {
        alive = false;
        // Release banners: All apps is opened rarely, and memory is tight on this TV.
        for (Entry e : entries) e.banner = null;
        banner.setImageDrawable(null);
        super.onStop();
    }

    private void collect() {
        try {
            collectApps();
        } catch (RuntimeException e) {
            android.util.Log.w(Sources.TAG, "app list failed", e);
        }
        Collections.sort(entries, (a, b) -> a.label.compareToIgnoreCase(b.label));
        if (Sources.stockHomeEnabled(this)) {
            entries.add(new Entry("Stock home", null, Sources.stockHomeIntent(), 1));
        }
        entries.add(new Entry("Restore stock home", null, null, 2));
    }

    private void collectApps() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(q, 0);
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setClassName(pkg, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            entries.add(new Entry(String.valueOf(ri.loadLabel(pm)), pkg, i, 0));
        }
        entries.add(new Entry("PS4", null, Sources.ps4Intent(), 0));
        entries.add(new Entry("Settings", "com.android.tv.settings", Sources.settingsIntent(), 0));
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        field = new View(this);
        field.setBackgroundColor(0xFF121212);
        root.addView(field, match());
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x59000000}));
        root.addView(scrim, match());

        for (Entry e : entries) {
            TextView t = Ui.text(this, 88, 700, 0xFFFFFFFF);
            t.setLetterSpacing(-0.03f);
            t.setText(e.label);
            t.setPivotX(0);
            HomeActivity.pinPivotToBaseline(t);
            FrameLayout.LayoutParams lp = wrap(Gravity.TOP | Gravity.START);
            lp.leftMargin = px(110);
            root.addView(t, lp);
            rows.add(t);
        }

        // Fades at the top and bottom so rows slide under the header and off the edge.
        View top = new View(this);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF000000, 0x00000000}));
        top.setAlpha(0.55f);
        root.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(300), Gravity.TOP));
        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xFF000000, 0x00000000}));
        bottom.setAlpha(0.55f);
        root.addView(bottom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(220), Gravity.BOTTOM));

        TextView title = Ui.text(this, 34, 600, 0xFFFFFFFF);
        title.setText("All apps");
        title.setAlpha(0.6f);
        FrameLayout.LayoutParams tp = wrap(Gravity.TOP | Gravity.START);
        tp.leftMargin = px(110);
        tp.topMargin = px(80);
        root.addView(title, tp);

        FrameLayout frame = new FrameLayout(this);
        frame.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(16));
            }
        });
        frame.setClipToOutline(true);
        banner = new ImageView(this);
        banner.setScaleType(ImageView.ScaleType.FIT_XY);
        frame.addView(banner, match());
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(px(480), px(270), Gravity.TOP | Gravity.START);
        bp.leftMargin = px(1330);
        bp.topMargin = px(405);
        root.addView(frame, bp);

        hint = Ui.text(this, 30, 500, 0xFFFFFFFF);
        hint.setAlpha(0.8f);
        hint.setSingleLine(false);
        hint.setMaxWidth(px(480));
        FrameLayout.LayoutParams hp = wrap(Gravity.TOP | Gravity.START);
        hp.leftMargin = px(1330);
        hp.topMargin = px(405 + 270 + 26);
        root.addView(hint, hp);
        return root;
    }

    private void applyFocus(int target, boolean animate) {
        focus = target;
        for (int i = 0; i < rows.size(); i++) {
            int d = i - focus;
            float top = FOCUS_TOP + d * PITCH + (d > 0 ? GAP_AFTER : 0);
            boolean on = d == 0;
            float scale = on ? 1f : UNFOCUSED;
            float alpha = on ? 1f : (Math.abs(d) > 7 ? 0f : 0.4f);
            TextView t = rows.get(i);
            t.animate().cancel();
            if (animate) {
                t.animate().translationY(px(top)).scaleX(scale).scaleY(scale).alpha(alpha)
                        .setDuration(180).setInterpolator(decel).start();
            } else {
                t.setTranslationY(px(top));
                t.setScaleX(scale);
                t.setScaleY(scale);
                t.setAlpha(alpha);
            }
        }
        Entry e = entries.get(focus);
        banner.setImageBitmap(e.banner);
        banner.setBackgroundColor(e.banner == null ? (e.pkg == null && e.kind == 0 ? 0xFF003791 : 0xFF2A2A2A) : 0);
        hint.setText(e.kind == 1 ? "Opens Google's home screen without changing the default."
                : e.kind == 2 ? "Switch back to Google's home screen." : "");
    }

    private void loadBannersAsync() {
        final int w = px(480), h = px(270);
        new Thread(() -> {
            for (int i = 0; i < entries.size() && alive; i++) {
                final Entry e = entries.get(i);
                if (e.pkg == null) continue;
                Bitmap b;
                try {
                    b = Sources.banner(getApplicationContext(), e.pkg, w, h);
                    if (b == null) {
                        Bitmap ic = Sources.icon(getApplicationContext(), e.pkg, h);
                        b = ic == null ? null : centered(ic, w, h);
                    }
                } catch (RuntimeException | OutOfMemoryError err) {
                    b = null; // the focused row's name is already on screen in large type
                }
                final Bitmap fb = b;
                handler.post(() -> {
                    if (!alive) return;
                    e.banner = fb;
                    if (entries.get(focus) == e) applyFocus(focus, false);
                });
            }
        }, "allapps-banners").start();
    }

    private static Bitmap centered(Bitmap icon, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        c.drawColor(0xFF2A2724);
        int s = h * 2 / 3;
        android.graphics.Rect dst = new android.graphics.Rect((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2);
        c.drawBitmap(icon, null, dst, new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
        return b;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (e.getAction() != KeyEvent.ACTION_DOWN) {
            return isNav(code) || super.dispatchKeyEvent(e);
        }
        boolean animate = e.getRepeatCount() == 0;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (focus > 0) applyFocus(focus - 1, animate);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (focus < entries.size() - 1) applyFocus(focus + 1, animate);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (e.getRepeatCount() == 0) activate();
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    private static boolean isNav(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                return true;
            default:
                return false;
        }
    }

    private void activate() {
        Entry e = entries.get(focus);
        if (e.kind == 2) {
            HomeActivity.confirmRestoreStockHome(this);
            return;
        }
        if (!Sources.start(this, e.intent)) {
            Toast.makeText(this, "Couldn't open " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

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
