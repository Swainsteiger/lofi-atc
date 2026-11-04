package com.anime.lofiatc;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Counts the planes around each airport (adsb.fi open data), which says how busy its radio is likely to be.
 * Same rule as the PC helper: within 20 nm and below 10,000 ft, or taxiing. Every 5 minutes while the
 * app is open, one request a second as adsb.fi asks.
 */
class Traffic {
    private static final int RADIUS_NM = 20, CEILING_FT = 10_000;
    private static final long REFRESH_MS = 5 * 60_000, GAP_MS = 1_500;

    private final Runnable onUpdate;
    private volatile int[] counts;
    private Thread thread;

    Traffic(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    void start(double[][] pos) {
        if (thread != null) return;
        if (counts == null) {
            counts = new int[pos.length];
            java.util.Arrays.fill(counts, -1);
        }
        thread = new Thread(() -> {
            try {
                while (!Thread.interrupted()) {
                    int[] next = counts.clone();
                    for (int i = 0; i < pos.length; i++) {
                        try {
                            next[i] = count(pos[i][0], pos[i][1]);
                        } catch (Exception ignored) {
                            // keep the last count for this one
                        }
                        Thread.sleep(GAP_MS);
                    }
                    counts = next;
                    onUpdate.run();
                    Thread.sleep(REFRESH_MS);
                }
            } catch (InterruptedException ignored) {
            }
        }, "traffic");
        thread.setDaemon(true);
        thread.start();
    }

    void stop() {
        if (thread != null) thread.interrupt();
        thread = null;
    }

    /** Planes around airport i, or -1 while not yet counted. */
    int planes(int i) {
        int[] c = counts;
        return c == null || i < 0 || i >= c.length ? -1 : c[i];
    }

    /** The airport with the most planes around it, or -1 before the first count. */
    int busiest() {
        int[] c = counts;
        int top = -1;
        if (c != null) for (int i = 0; i < c.length; i++) if (c[i] >= 0 && (top < 0 || c[i] > c[top])) top = i;
        return top;
    }

    private static int count(double lat, double lon) throws Exception {
        URL url = new URL(String.format(Locale.US, "https://opendata.adsb.fi/api/v2/lat/%.3f/lon/%.3f/dist/%d", lat, lon, RADIUS_NM));
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(15_000);
        c.setRequestProperty("User-Agent", "LofiATC/1.0 (personal ambient player)");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[16384];
            for (int n; (n = in.read(chunk)) > 0; ) buf.write(chunk, 0, n);
            JSONArray planes = new JSONObject(buf.toString("UTF-8")).optJSONArray("aircraft");
            int n = 0;
            if (planes != null) for (int i = 0; i < planes.length(); i++) {
                JSONObject p = planes.getJSONObject(i);
                Object alt = p.opt("alt_baro");
                // On the ground only while taxiing: parked planes often leave their transponders on.
                if (("ground".equals(alt) && p.optDouble("gs", 0) > 5)
                        || (alt instanceof Number && ((Number) alt).doubleValue() < CEILING_FT)) n++;
            }
            return n;
        } finally {
            c.disconnect();
        }
    }
}
