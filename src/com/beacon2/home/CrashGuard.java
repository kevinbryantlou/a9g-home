package com.beacon2.home;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers when the app last crashed. With Google's launcher disabled this app is the
 * only real HOME, so Android relaunches it after every crash and shows no dialog on a TV.
 * HomeActivity checks this on start and shows its plain fallback screen instead of
 * crashing again.
 */
final class CrashGuard {
    private CrashGuard() {}

    private static final String PREFS = "crash", KEY = "last";
    private static final long WINDOW_MS = 60_000;
    private static boolean installed;

    static void install(Context c) {
        if (installed) return;
        installed = true;
        final Context app = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                prefs(app).edit().putLong(KEY, System.currentTimeMillis()).commit();
            } catch (Throwable ignored) {
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    static boolean crashedRecently(Context c) {
        long last = prefs(c).getLong(KEY, 0);
        long age = System.currentTimeMillis() - last;
        return last > 0 && age >= 0 && age < WINDOW_MS;
    }

    static void clear(Context c) {
        prefs(c).edit().remove(KEY).commit();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
