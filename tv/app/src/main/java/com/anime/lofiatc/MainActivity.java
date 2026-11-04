package com.anime.lofiatc;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

/**
 * Lofi music with live air traffic control radio on top, like lofiatc.com. Remote: up/down picks a row,
 * left/right changes it, OK or play/pause starts and stops, channel up/down switches airport.
 * Playback starts on open and stops when the app is left.
 */
public class MainActivity extends Activity {
    private static final int AMBER = 0xFFFFB86B, TEXT = 0xFFE9E6F5, DIM = 0xFF9A96B4, LIVE = 0xFF5CF2A5;
    private static final long HIDE_AFTER_MS = 10_000, RETRY_MS = 4_000;
    /** Panel rows, top to bottom. */
    private static final int BUSIEST = 0, AIRPORT = 1, FEED = 2, ATC_VOL = 3, MUSIC = 4, TRACK = 5, MUSIC_VOL = 6;

    private static class Airport {
        String icao, name, city, tz;
        double lat, lon;
        final List<String[]> feeds = new ArrayList<>();
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Airport> airports = new ArrayList<>();
    /** Music (assets/stations.json, same list as the PC page): name, url; url is null for the Soft lofi playlist. */
    private final List<String[]> stations = new ArrayList<>();
    /** The Soft lofi playlist (assets/tracks.json, Creative Commons tracks): title, artist, license, url. */
    private final List<String[]> tracks = new ArrayList<>();
    /** For testing before the tracks are uploaded: play them from this address instead (intent extra). */
    private String musicBase;
    private int trackErrors;
    private final Traffic traffic = new Traffic(this::onTraffic);
    private int offAir;
    private final Random random = new Random();
    private SharedPreferences prefs;
    private int airport, feed, station, atcVol, musicVol;
    private boolean playing;

    private ExoPlayer atc, music;
    private final PeakTap atcPeak = new PeakTap();

    private Typeface font;
    private LinearLayout content, panel;
    private TextView icaoText, whereText, clockText, statusText, hintText;
    private View dot;
    private final List<LinearLayout> rows = new ArrayList<>();
    private final List<TextView> values = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        loadAirports();
        musicBase = getIntent().getStringExtra("musicBase");
        prefs = getSharedPreferences("lofiatc", MODE_PRIVATE);
        airport = clamp(indexOf(prefs.getString("icao", "KLAS")), airports.size());
        feed = clamp(prefs.getInt("feed", 0), airports.get(airport).feeds.size());
        station = clamp(prefs.getInt("station", 0), stations.size());
        atcVol = prefs.getInt("atcVol", 8);
        musicVol = prefs.getInt("musicVol", 5);
        buildUi();
        refresh();
    }

    @Override
    protected void onStart() {
        super.onStart();
        play();
        ui.post(clock);
        ui.post(talkLight);
        ui.postDelayed(drift, 60_000);
        showPanel();
        double[][] pos = new double[airports.size()][];
        for (int i = 0; i < pos.length; i++) pos[i] = new double[]{airports.get(i).lat, airports.get(i).lon};
        traffic.start(pos);
    }

    @Override
    protected void onStop() {
        super.onStop();
        stop();
        traffic.stop();
        ui.removeCallbacksAndMessages(null);
    }

    // ---- playback ----

    @OptIn(markerClass = UnstableApi.class)
    private void play() {
        playing = true;
        if (atc == null) {
            atc = new ExoPlayer.Builder(this, atcPeak.renderers(this)).build();
            music = new ExoPlayer.Builder(this, new DefaultRenderersFactory(this)).build();
            atc.addListener(new Retry(true));
            music.addListener(new Retry(false));
        }
        applyVolume();
        start(atc, airports.get(airport).feeds.get(feed)[1]);
        startMusic();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setStatus("Tuning in…");
    }

    private void stop() {
        playing = false;
        ui.removeCallbacks(retryAtc);
        ui.removeCallbacks(retryMusic);
        if (atc != null) { atc.release(); music.release(); atc = null; music = null; }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setStatus("Paused · press OK to play");
    }

    private void start(ExoPlayer p, String url) {
        if (p == null) return;
        p.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        p.prepare();
        p.setPlayWhenReady(true);
    }

    private boolean isPlaylist() { return stations.get(station)[1] == null; }

    /** A radio station, or the Soft lofi playlist: every track, shuffled, round and round. */
    private void startMusic() {
        if (music == null) return;
        trackErrors = 0;
        if (!isPlaylist()) {
            music.setShuffleModeEnabled(false);
            music.setRepeatMode(Player.REPEAT_MODE_OFF);
            start(music, stations.get(station)[1]);
            return;
        }
        List<MediaItem> items = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            String[] t = tracks.get(i);
            String url = musicBase == null ? t[3] : musicBase + t[3].substring(t[3].lastIndexOf('/') + 1);
            items.add(new MediaItem.Builder().setUri(url).setMediaId(String.valueOf(i))
                    .setMediaMetadata(new MediaMetadata.Builder().setTitle(t[0]).setArtist(t[1]).build()).build());
        }
        music.setShuffleModeEnabled(true);
        music.setRepeatMode(Player.REPEAT_MODE_ALL);
        music.setMediaItems(items, random.nextInt(items.size()), 0);
        music.prepare();
        music.setPlayWhenReady(true);
    }

    /** The playlist track playing now, or null. */
    private String[] currentTrack() {
        if (music == null || !isPlaylist() || music.getCurrentMediaItem() == null) return null;
        return tracks.get(Integer.parseInt(music.getCurrentMediaItem().mediaId));
    }

    private void skipTrack(int d) {
        if (music == null || !isPlaylist()) return;
        if (d > 0) music.seekToNextMediaItem(); else music.seekToPreviousMediaItem();
        music.prepare();
        music.setPlayWhenReady(true);
    }

    private void applyVolume() {
        if (atc != null) { atc.setVolume(atcVol / 10f); music.setVolume(musicVol / 10f); }
    }

    private final Runnable retryAtc = () -> { if (playing) start(atc, airports.get(airport).feeds.get(feed)[1]); };
    private final Runnable retryMusic = () -> { if (playing) startMusic(); };

    /** Live streams drop now and then: start them again after a short wait. */
    private class Retry implements Player.Listener {
        private final boolean isAtc;
        Retry(boolean isAtc) { this.isAtc = isAtc; }

        @Override
        public void onPlayerError(PlaybackException e) {
            // A channel LiveATC has taken off air answers 404: move on to the airport's next channel by itself.
            if (isAtc && playing && e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                    && ++offAir < airports.get(airport).feeds.size()) {
                feed = wrap(feed + 1, airports.get(airport).feeds.size());
                save();
                refresh();
                setStatus("Channel off air, trying " + airports.get(airport).feeds.get(feed)[0] + "…");
                start(atc, airports.get(airport).feeds.get(feed)[1]);
                return;
            }
            // A playlist track that fails to load is skipped; after a run of failures, start over after a wait.
            if (!isAtc && playing && isPlaylist() && ++trackErrors <= 3) {
                skipTrack(1);
                return;
            }
            again();
        }

        @Override
        public void onPlaybackStateChanged(int s) {
            if (s == Player.STATE_ENDED) again();
            if (s == Player.STATE_READY && playing) {
                if (isAtc) offAir = 0; else trackErrors = 0;
                setStatus(nowPlaying());
            }
        }

        @Override
        public void onMediaItemTransition(MediaItem item, int reason) {
            if (!isAtc) refresh();
        }

        private void again() {
            if (!playing) return;
            setStatus(isAtc ? "Radio reconnecting…" : "Music reconnecting…");
            Runnable r = isAtc ? retryAtc : retryMusic;
            ui.removeCallbacks(r);
            ui.postDelayed(r, RETRY_MS);
        }
    }

    /** New plane counts arrived (on a background thread). */
    private void onTraffic() {
        ui.post(() -> { refresh(); });
    }

    private String nowPlaying() {
        return airports.get(airport).feeds.get(feed)[0] + "  ·  " + stations.get(station)[0];
    }

    // ---- remote ----

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN && k != KeyEvent.KEYCODE_BACK) {
            boolean wasHidden = panel.getAlpha() < 0.5f;
            showPanel();
            // The first press only wakes the controls, unless it is a key that means something on its own.
            if (wasHidden && (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                    || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) return true;
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (rows.indexOf(getCurrentFocus()) == BUSIEST) { change(BUSIEST, 0); return true; }
                    // fall through: OK anywhere else plays and pauses
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    if (playing) stop(); else play();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PLAY: if (!playing) play(); return true;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_STOP: if (playing) stop(); return true;
                case KeyEvent.KEYCODE_MEDIA_NEXT: skipTrack(1); return true;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS: skipTrack(-1); return true;
                case KeyEvent.KEYCODE_CHANNEL_UP: change(AIRPORT, 1); return true;
                case KeyEvent.KEYCODE_CHANNEL_DOWN: change(AIRPORT, -1); return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT: {
                    int row = rows.indexOf(getCurrentFocus());
                    if (row >= 0) { change(row, k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1); return true; }
                }
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private void change(int row, int d) {
        switch (row) {
            case BUSIEST:
            case AIRPORT: {
                int to = row == BUSIEST ? traffic.busiest() : wrap(airport + d, airports.size());
                if (to < 0 || to == airport) return;
                airport = to;
                feed = 0;
                offAir = 0;
                if (playing) start(atc, airports.get(airport).feeds.get(feed)[1]);
                else if (row == BUSIEST) play();
                break;
            }
            case FEED:
                feed = wrap(feed + d, airports.get(airport).feeds.size());
                offAir = 0;
                if (playing) start(atc, airports.get(airport).feeds.get(feed)[1]);
                break;
            case ATC_VOL: atcVol = Math.max(0, Math.min(10, atcVol + d)); applyVolume(); break;
            case MUSIC:
                station = wrap(station + d, stations.size());
                if (playing) startMusic();
                break;
            case TRACK: skipTrack(d); break;
            case MUSIC_VOL: musicVol = Math.max(0, Math.min(10, musicVol + d)); applyVolume(); break;
        }
        if (playing && (row == BUSIEST || row == AIRPORT || row == FEED || row == MUSIC)) setStatus("Tuning in…");
        save();
        refresh();
    }

    private void save() {
        prefs.edit().putString("icao", airports.get(airport).icao).putInt("feed", feed).putInt("station", station)
                .putInt("atcVol", atcVol).putInt("musicVol", musicVol).apply();
    }

    // ---- screen ----

    private void buildUi() {
        font = getResources().getFont(R.font.outfit);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B1020);
        root.addView(new SkyView(this), new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(content, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        icaoText = text(92, TEXT, Typeface.BOLD);
        icaoText.setLetterSpacing(0.08f);
        whereText = text(22, DIM, Typeface.NORMAL);
        clockText = text(26, AMBER, Typeface.NORMAL);
        content.addView(icaoText);
        content.addView(whereText);
        content.addView(clockText);

        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        dot = new View(this);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(LIVE);
        dot.setBackground(circle);
        dot.setAlpha(0.15f);
        status.addView(dot, new LinearLayout.LayoutParams(dp(12), dp(12)));
        statusText = text(16, DIM, Typeface.NORMAL);
        statusText.setPadding(dp(10), 0, 0, 0);
        status.addView(statusText);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = dp(14);
        content.addView(status, sp);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable card = new GradientDrawable();
        card.setColor(0x1AFFFFFF);
        card.setStroke(dp(1), 0x26FFFFFF);
        card.setCornerRadius(dp(14));
        panel.setBackground(card);
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));
        String[] labels = {"Busiest now", "Airport", "Radio", "ATC volume", "Music", "Track", "Music volume"};
        for (String label : labels) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(16), dp(6), dp(16), dp(6));
            TextView l = text(18, DIM, Typeface.NORMAL), v = text(18, TEXT, Typeface.NORMAL);
            l.setGravity(Gravity.START);
            v.setGravity(Gravity.START);
            v.setSingleLine(true);
            v.setEllipsize(TextUtils.TruncateAt.END);
            l.setText(label);
            r.addView(l, new LinearLayout.LayoutParams(dp(150), -2));
            r.addView(v);
            values.add(v);
            r.setFocusable(true);
            r.setOnFocusChangeListener((view, has) -> view.setBackground(has ? pill() : null));
            rows.add(r);
            panel.addView(r, new LinearLayout.LayoutParams(dp(700), -2));
        }
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, -2);
        pp.topMargin = dp(18);
        content.addView(panel, pp);

        hintText = text(13, DIM, Typeface.NORMAL);
        hintText.setText("▲▼ choose   ◀▶ change   OK play/pause   CH+/CH− airport   ⏭ next track");
        hintText.setPadding(0, dp(14), 0, 0);
        content.addView(hintText);

        setContentView(root);
        rows.get(AIRPORT).requestFocus();
    }

    private void refresh() {
        Airport a = airports.get(airport);
        icaoText.setText(a.icao);
        whereText.setText(a.name + "  ·  " + a.city);
        int top = traffic.busiest();
        values.get(BUSIEST).setText(top < 0 ? "counting planes…" : top == airport ? "you're there  ✈ " + traffic.planes(top)
                : airports.get(top).icao + " · " + airports.get(top).city + "  ✈ " + traffic.planes(top) + "   OK to go");
        values.get(AIRPORT).setText(row(a.icao + " · " + a.city + (traffic.planes(airport) < 0 ? "" : "  ✈ " + traffic.planes(airport))));
        values.get(FEED).setText(row(a.feeds.get(feed)[0]));
        values.get(ATC_VOL).setText(row(bar(atcVol)));
        values.get(MUSIC).setText(row(stations.get(station)[0]));
        // The track playing, with the credit its Creative Commons license asks for.
        String[] t = currentTrack();
        rows.get(TRACK).setVisibility(isPlaylist() ? View.VISIBLE : View.GONE);
        values.get(TRACK).setText(t == null ? "‹  starts when playing  ›" : row(t[0] + " — " + t[1] + "  ·  " + t[2]));
        values.get(MUSIC_VOL).setText(row(bar(musicVol)));
        clock.run();
    }

    private static String row(String value) {
        return "‹  " + value + "  ›";
    }

    private static String bar(int v) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 10; i++) b.append(i < v ? '▮' : '▯');
        return b.toString();
    }

    private void setStatus(String s) { ui.post(() -> statusText.setText(s)); }

    private void showPanel() {
        panel.animate().alpha(1).setDuration(150);
        hintText.animate().alpha(1).setDuration(150);
        ui.removeCallbacks(hidePanel);
        ui.postDelayed(hidePanel, HIDE_AFTER_MS);
    }

    private final Runnable hidePanel = () -> {
        panel.animate().alpha(0).setDuration(800);
        hintText.animate().alpha(0).setDuration(800);
    };

    private final Runnable clock = new Runnable() {
        @Override
        public void run() {
            Airport a = airports.get(airport);
            SimpleDateFormat f = new SimpleDateFormat("h:mm a", Locale.US);
            if (!a.tz.isEmpty()) f.setTimeZone(TimeZone.getTimeZone(a.tz));
            int n = traffic.planes(airport);
            clockText.setText(f.format(new Date()) + (a.tz.isEmpty() ? "" : " local")
                    + (n < 0 ? "" : "   ·   ✈ " + n + (n == 1 ? " plane" : " planes") + " around"));
            ui.removeCallbacks(this);
            ui.postDelayed(this, 10_000);
        }
    };

    /** Lights the dot while someone is talking on the radio. */
    private final Runnable talkLight = new Runnable() {
        @Override
        public void run() {
            boolean on = playing && atcPeak.level() > 0.05f;
            dot.setAlpha(on ? 1f : 0.15f);
            ui.postDelayed(this, 100);
        }
    };

    /** Nudges everything a little each minute so nothing burns into the panel. */
    private final Runnable drift = new Runnable() {
        @Override
        public void run() {
            content.animate().translationX(dp(random.nextInt(61) - 30)).translationY(dp(random.nextInt(41) - 20))
                    .setDuration(4000);
            ui.postDelayed(this, 60_000);
        }
    };

    private GradientDrawable pill() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x33FFB86B);
        g.setStroke(dp(2), AMBER);
        g.setCornerRadius(dp(10));
        return g;
    }

    private TextView text(float sp, int color, int style) {
        TextView t = new TextView(this);
        t.setTypeface(Typeface.create(font, style));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ---- data ----

    private void loadAirports() {
        try {
            JSONArray arr = new JSONArray(readAsset("airports.json"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Airport a = new Airport();
                a.icao = o.getString("icao");
                a.name = o.getString("name");
                a.city = o.optString("city");
                a.tz = o.optString("tz");
                a.lat = o.getDouble("lat");
                a.lon = o.getDouble("lon");
                JSONArray f = o.getJSONArray("feeds");
                for (int j = 0; j < f.length(); j++)
                    a.feeds.add(new String[]{f.getJSONObject(j).getString("name"), f.getJSONObject(j).getString("url")});
                airports.add(a);
            }
            JSONArray st = new JSONArray(readAsset("stations.json"));
            for (int i = 0; i < st.length(); i++)
                stations.add(new String[]{st.getJSONObject(i).getString("name"), st.getJSONObject(i).optString("url", null)});
            JSONArray tr = new JSONArray(readAsset("tracks.json"));
            for (int i = 0; i < tr.length(); i++) {
                JSONObject o = tr.getJSONObject(i);
                tracks.add(new String[]{o.getString("title"), o.getString("artist"), o.getString("license"), o.getString("url")});
            }
        } catch (Exception e) {
            throw new IllegalStateException("assets", e);
        }
    }

    private String readAsset(String name) throws IOException {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[16384];
            for (int n; (n = in.read(chunk)) > 0; ) buf.write(chunk, 0, n);
            return buf.toString(StandardCharsets.UTF_8.name());
        }
    }

    private int indexOf(String icao) {
        for (int i = 0; i < airports.size(); i++) if (airports.get(i).icao.equals(icao)) return i;
        return 0;
    }

    private static int clamp(int i, int n) { return i < 0 || i >= n ? 0 : i; }

    private static int wrap(int i, int n) { return ((i % n) + n) % n; }
}
