package com.beacon2.wxprobe;

import android.app.Activity;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Measures what a weather feature would cost: location read + one HTTPS fetch + JSON parse. */
public class Probe extends Activity {
    static final String T = "WXPROBE";
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        new Thread(this::run).start();
    }
    private long pss() { return Debug.getPss(); }
    private void run() {
        try {
            System.gc(); SystemClock.sleep(500);
            long pss0 = pss(); long cpu0 = Process.getElapsedCpuTime();
            long t0 = SystemClock.elapsedRealtime();
            LocationManager lm = getSystemService(LocationManager.class);
            Location loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            long t1 = SystemClock.elapsedRealtime();
            double lat = loc != null ? loc.getLatitude() : 34.1478, lon = loc != null ? loc.getLongitude() : -118.1445;
            Log.i(T, "location " + (loc != null ? "network" : "fallback") + " " + Math.round(lat * 100) / 100.0 + "," + Math.round(lon * 100) / 100.0 + " acc=" + (loc != null ? loc.getAccuracy() : -1) + " in " + (t1 - t0) + " ms");
            for (int run = 1; run <= 3; run++) {
                long a = SystemClock.elapsedRealtime();
                URL u = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min"
                        + "&temperature_unit=fahrenheit&timezone=auto&forecast_days=5");
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(10000); c.setReadTimeout(10000);
                int n = 0; byte[] buf = new byte[4096];
                try (InputStream in = c.getInputStream()) { int r; StringBuilder sb = new StringBuilder(); while ((r = in.read(buf)) > 0) { n += r; sb.append(new String(buf, 0, r, "UTF-8")); }
                    org.json.JSONObject j = new org.json.JSONObject(sb.toString());
                    double temp = j.getJSONObject("current").getDouble("temperature_2m");
                    long z = SystemClock.elapsedRealtime();
                    Log.i(T, "fetch " + run + ": HTTP " + c.getResponseCode() + " " + n + " bytes, " + (z - a) + " ms, temp=" + temp);
                }
                c.disconnect();
            }
            long cpu1 = Process.getElapsedCpuTime();
            System.gc(); SystemClock.sleep(500);
            long pss1 = pss();
            Log.i(T, "cpu " + (cpu1 - cpu0) + " ms total; PSS before " + pss0 + " KB, after " + pss1 + " KB, delta " + (pss1 - pss0) + " KB");
        } catch (Throwable e) {
            Log.e(T, "failed", e);
        }
    }
}
