package com.beacon2.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Full-screen photo slideshow behind the menu: two ImageViews, one visible, one
 * loading. Photos are bundled 1920x1080 JPEGs decoded as RGB_565 (about 4 MB each),
 * so at most two are in memory. Decoding runs on a background thread.
 */
final class Slideshow {
    static final long INTERVAL_MS = 60_000;
    private static final long FADE_MS = 1_200;

    private final Context context;
    private final ImageView[] layers;
    private final TextView credit;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Photo> photos = new ArrayList<>();
    private HandlerThread worker;
    private Handler workerHandler;
    private int front = 0, next = 0;
    private boolean running;
    /** Bumped by stop(); a decode that finishes after a stop/start is dropped. */
    private int generation;

    static final class Photo {
        final String file, credit;
        Photo(String file, String credit) { this.file = file; this.credit = credit; }
    }

    Slideshow(Context context, ImageView a, ImageView b, TextView credit, String first) {
        this.context = context;
        this.layers = new ImageView[]{a, b};
        this.credit = credit;
        loadIndex();
        Collections.shuffle(photos);
        if (first != null) {
            for (int i = 0; i < photos.size(); i++) {
                if (photos.get(i).file.equals("photos/" + first)) {
                    photos.add(0, photos.remove(i));
                    break;
                }
            }
        }
    }

    /** assets/photos/photos.json: [{"file":"x.jpg","credit":"Name, CC BY-SA 4.0"}, ...] */
    private void loadIndex() {
        try (InputStream in = context.getAssets().open("photos/photos.json")) {
            byte[] buf = new byte[in.available()];
            int read = 0;
            while (read < buf.length) {
                int n = in.read(buf, read, buf.length - read);
                if (n < 0) break;
                read += n;
            }
            JSONArray arr = new JSONArray(new String(buf, 0, read, "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                photos.add(new Photo("photos/" + o.getString("file"), o.optString("credit", "")));
            }
        } catch (Exception e) {
            Log.w(Sources.TAG, "no photo index", e);
        }
    }

    boolean isEmpty() { return photos.isEmpty(); }

    void start() {
        if (running || photos.isEmpty()) return;
        running = true;
        worker = new HandlerThread("slideshow");
        worker.start();
        workerHandler = new Handler(worker.getLooper());
        if (layers[front].getDrawable() == null) {
            show(next, false);
        } else {
            main.postDelayed(advance, INTERVAL_MS);
        }
    }

    void stop() {
        running = false;
        generation++;
        main.removeCallbacks(advance);
        if (worker != null) {
            worker.quitSafely();
            worker = null;
            workerHandler = null;
        }
    }

    /** Release both bitmaps; called when the screen is not visible for a while. */
    void release() {
        for (ImageView v : layers) clear(v);
    }

    /** Detach and free a layer's photo. Photos are decoded here, so nothing else holds them. */
    private static void clear(ImageView v) {
        android.graphics.drawable.Drawable d = v.getDrawable();
        v.setImageDrawable(null);
        if (d instanceof android.graphics.drawable.BitmapDrawable) {
            Bitmap b = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
            if (b != null && !b.isRecycled()) b.recycle();
        }
    }

    private final Runnable advance = new Runnable() {
        @Override public void run() {
            if (!running) return;
            show(next, true);
        }
    };

    private void show(int index, boolean fade) {
        final Photo p = photos.get(index % photos.size());
        next = (index + 1) % photos.size();
        final Handler wh = workerHandler;
        if (wh == null) return;
        final int gen = generation;
        wh.post(() -> {
            final Bitmap bmp = Sources.decodeAssetPhoto(context, p.file, 1920, 1080);
            main.post(() -> {
                if (!running || gen != generation) return;
                if (bmp != null) present(bmp, p.credit, fade);
                main.postDelayed(advance, INTERVAL_MS);
            });
        });
    }

    private void present(Bitmap bmp, String creditText, boolean fade) {
        final ImageView out = layers[front];
        final ImageView in = layers[1 - front];
        in.setImageBitmap(bmp);
        in.bringToFront();
        credit.setText(creditText);
        if (fade) {
            in.setAlpha(0f);
            in.animate().alpha(1f).setDuration(FADE_MS).withEndAction(() -> {
                // Only clear the layer if it is still the back one.
                if (out != layers[front]) clear(out);
            }).start();
        } else {
            in.setAlpha(1f);
            clear(out);
        }
        front = 1 - front;
    }
}
