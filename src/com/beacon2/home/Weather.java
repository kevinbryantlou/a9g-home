package com.beacon2.home;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Current conditions and a five-day forecast from Open-Meteo (free, no key, no account).
 * Location is the TV's own coarse network fix (Google Play services Wi-Fi location, about
 * 2 km), rounded to two decimals; Pasadena if the permission is missing. One fetch an hour
 * at most, only while the home screen is started; the last result is cached so a cold
 * start shows weather immediately. Measured on the TV: 0.2-1 s and +0.9 MB per fetch.
 */
final class Weather {
    static final long REFRESH_MS = 60 * 60_000L, MAX_AGE_MS = 2 * 60 * 60_000L;
    private static final double PASADENA_LAT = 34.15, PASADENA_LON = -118.14;
    private static final String PREFS = "weather", KEY_JSON = "json", KEY_AT = "at";

    /** One day of forecast. */
    static final class Day {
        final String name;
        final int code, high, low;
        Day(String name, int code, int high, int low) {
            this.name = name; this.code = code; this.high = high; this.low = low;
        }
    }

    /** A parsed forecast; immutable. */
    static final class Report {
        final int temp, code;
        final Day[] days;
        final long fetchedAt;
        Report(int temp, int code, Day[] days, long fetchedAt) {
            this.temp = temp; this.code = code; this.days = days; this.fetchedAt = fetchedAt;
        }
        boolean fresh() {
            long age = System.currentTimeMillis() - fetchedAt;
            return age >= 0 && age < MAX_AGE_MS;
        }
    }

    interface Listener {
        void onWeather(Report r);
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean fetching;
    private Report last;

    Weather(Context c) {
        app = c.getApplicationContext();
        last = parse(prefs().getString(KEY_JSON, null), prefs().getLong(KEY_AT, 0));
    }

    Report cached() {
        return last;
    }

    /** Fetch in the background if the cached report is older than an hour. */
    void refreshIfStale(final Listener l) {
        if (fetching) return;
        if (last != null && System.currentTimeMillis() - last.fetchedAt < REFRESH_MS) return;
        fetching = true;
        new Thread(() -> {
            Report r = null;
            try {
                String json = fetch();
                long now = System.currentTimeMillis();
                r = parse(json, now);
                if (r != null) prefs().edit().putString(KEY_JSON, json).putLong(KEY_AT, now).apply();
                Log.i(Sources.TAG, "weather: " + (r == null ? "unparseable response (" + json.length() + " chars)"
                        : r.temp + "° code " + r.code + ", " + r.days.length + " days"));
            } catch (Throwable e) {
                Log.w(Sources.TAG, "weather fetch failed", e);
            }
            final Report got = r;
            main.post(() -> {
                fetching = false;
                if (got != null) last = got;
                l.onWeather(last);
            });
        }, "weather").start();
    }

    private String fetch() throws Exception {
        double[] ll = location();
        URL u = new URL(String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                        + "&current=temperature_2m,weather_code"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                        + "&temperature_unit=fahrenheit&timezone=auto&forecast_days=5",
                ll[0], ll[1]));
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(10_000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    private double[] location() {
        try {
            if (app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                LocationManager lm = app.getSystemService(LocationManager.class);
                Location l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (l == null) l = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
                if (l != null) return new double[]{l.getLatitude(), l.getLongitude()};
            }
        } catch (RuntimeException e) {
            Log.w(Sources.TAG, "location unavailable", e);
        }
        return new double[]{PASADENA_LAT, PASADENA_LON};
    }

    private static Report parse(String json, long at) {
        if (json == null) return null;
        try {
            JSONObject j = new JSONObject(json);
            JSONObject cur = j.getJSONObject("current");
            JSONObject d = j.getJSONObject("daily");
            JSONArray time = d.getJSONArray("time"), code = d.getJSONArray("weather_code"),
                    hi = d.getJSONArray("temperature_2m_max"), lo = d.getJSONArray("temperature_2m_min");
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("EEE", Locale.getDefault());
            Day[] days = new Day[time.length()];
            for (int i = 0; i < days.length; i++) {
                days[i] = new Day(out.format(in.parse(time.getString(i))), code.getInt(i),
                        (int) Math.round(hi.getDouble(i)), (int) Math.round(lo.getDouble(i)));
            }
            return new Report((int) Math.round(cur.getDouble("temperature_2m")), cur.getInt("weather_code"), days, at);
        } catch (Exception e) {
            Log.w(Sources.TAG, "weather parse failed", e);
            return null;
        }
    }

    private SharedPreferences prefs() {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** WMO weather code to a short sentence-case phrase. */
    static String words(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow";
        if (code >= 95) return "Thunderstorms";
        return "Cloudy";
    }

    /** WMO weather code to one of the eight line icons. */
    static int icon(int code) {
        if (code == 0) return R.drawable.wx_sun;
        if (code == 1 || code == 2) return R.drawable.wx_partly;
        if (code == 45 || code == 48) return R.drawable.wx_fog;
        if (code >= 51 && code <= 57) return R.drawable.wx_drizzle;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return R.drawable.wx_rain;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.wx_snow;
        if (code >= 95) return R.drawable.wx_thunder;
        return R.drawable.wx_cloud;
    }
}
