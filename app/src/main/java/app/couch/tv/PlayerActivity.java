package app.couch.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends Activity implements AdBlock.Listener {
    public static final String EXTRA_LOGIN = "login";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_AVATAR = "avatar";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_GAME = "game";
    public static final String EXTRA_VIEWERS = "viewers";

    static final String[] MODES = {"Nur Video", "Chat rechts", "Chat über Video", "Chat klein"};
    static final String[] SIZES = {"Klein", "Mittel", "Gross"};
    /** Position der Qualitäts-Taste in der Leiste (Label + 4 Ansichten + Label + Grösse + Label). */
    private static final int QUALITY_INDEX = 8;

    private final Handler h = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private FrameLayout root, videoBox;
    private PlayerView playerView;
    private LinearLayout chatPanel, controls, pills, adCover;
    private RecyclerView chatList;
    private TextView chatHeader, status, flash, backupBadge, nameView, titleView, gameView, viewersView;
    private ImageView avatarView;
    private ProgressBar buffering;

    private ExoPlayer player;
    private ChatAdapter chatAdapter;
    private ChatClient chat;

    private String login, displayName, avatar, title, game;
    private int viewers;
    private int mode, size;
    private boolean adblockOn;

    // Werbeblocker-Zustand
    private boolean useProxy;
    private String currentTag = "", currentSource = "";
    private boolean currentReduced;
    private int generation;
    private boolean searching, covered, destroyed;
    private int retries;
    private long lastSearch;
    /** Quellen, die kürzlich Werbung hatten (Quelle -> Zeitpunkt). */
    private final Map<String, Long> adSources = new HashMap<>();

    // Qualität
    private int wantHeight; // 0 = automatisch
    private float wantFps;
    private boolean inQualityMenu;

    // ------------------------------------------------------------ Lebenszyklus

    public static void start(Activity a, Stream s) {
        Intent i = new Intent(a, PlayerActivity.class);
        i.putExtra(EXTRA_LOGIN, s.login);
        i.putExtra(EXTRA_NAME, s.displayName);
        i.putExtra(EXTRA_AVATAR, s.avatar);
        i.putExtra(EXTRA_TITLE, s.title);
        i.putExtra(EXTRA_GAME, s.game);
        i.putExtra(EXTRA_VIEWERS, s.viewers);
        a.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);

        root = findViewById(R.id.root);
        videoBox = findViewById(R.id.videoBox);
        playerView = findViewById(R.id.playerView);
        chatPanel = findViewById(R.id.chatPanel);
        chatList = findViewById(R.id.chatList);
        chatHeader = findViewById(R.id.chatHeader);
        controls = findViewById(R.id.controls);
        pills = findViewById(R.id.pills);
        adCover = findViewById(R.id.adCover);
        status = findViewById(R.id.status);
        flash = findViewById(R.id.flash);
        backupBadge = findViewById(R.id.backupBadge);
        buffering = findViewById(R.id.buffering);
        nameView = findViewById(R.id.name);
        titleView = findViewById(R.id.title);
        gameView = findViewById(R.id.game);
        viewersView = findViewById(R.id.viewers);
        avatarView = findViewById(R.id.avatar);

        Intent i = getIntent();
        login = Util.safe(i.getStringExtra(EXTRA_LOGIN));
        displayName = Util.safe(i.getStringExtra(EXTRA_NAME));
        if (displayName.isEmpty()) displayName = login;
        avatar = Util.safe(i.getStringExtra(EXTRA_AVATAR));
        title = Util.safe(i.getStringExtra(EXTRA_TITLE));
        game = Util.safe(i.getStringExtra(EXTRA_GAME));
        viewers = i.getIntExtra(EXTRA_VIEWERS, 0);

        prefs = getSharedPreferences("player", MODE_PRIVATE);
        mode = prefs.getInt("mode", 1);
        size = prefs.getInt("size", 1);
        adblockOn = prefs.getBoolean("adblock", true);
        useProxy = prefs.getBoolean("proxy", true);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setKeepContentOnPlayerReset(true);
        player.addListener(playerListener);

        chatAdapter = new ChatAdapter(this);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        chatList.setLayoutManager(lm);
        chatList.setItemAnimator(null);
        chatList.setAdapter(chatAdapter);
        chatAdapter.system("Verbinde mit dem Chat von " + displayName + " …");

        chat = new ChatClient(login, new ChatClient.Listener() {
            @Override
            public void onMessage(ChatClient.Message m) {
                chatAdapter.add(m);
                chatList.scrollToPosition(chatAdapter.getItemCount() - 1);
            }

            @Override
            public void onState(boolean connected) {
                if (!connected) chatAdapter.system("Chat-Verbindung unterbrochen – verbinde neu …");
            }
        });
        chat.connect();

        bindInfo();
        root.post(this::applyLayout);
        findSource(true);
        h.postDelayed(infoTick, 60_000);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Beim Verlassen nichts im Hintergrund weiterlaufen lassen
        finish();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        h.removeCallbacksAndMessages(null);
        if (chat != null) chat.close();
        if (player != null) {
            player.removeListener(playerListener);
            player.release();
        }
        super.onDestroy();
    }

    private final Runnable infoTick = new Runnable() {
        @Override
        public void run() {
            TwitchApi.async(() -> TwitchApi.channel(login), c -> {
                if (destroyed || c == null) return;
                if (c.live != null) {
                    title = c.live.title;
                    game = c.live.game;
                    viewers = c.live.viewers;
                }
                if (!c.avatar.isEmpty()) avatar = c.avatar;
                bindInfo();
            });
            h.postDelayed(this, 60_000);
        }
    };

    private void bindInfo() {
        nameView.setText(displayName);
        titleView.setText(title);
        gameView.setText(game);
        gameView.setVisibility(game.isEmpty() ? View.GONE : View.VISIBLE);
        viewersView.setText(viewers > 0 ? Util.viewersLong(viewers) : "");
        Util.avatar(avatarView, avatar);
        chatHeader.setText("LIVE-CHAT · " + displayName.toUpperCase());
    }

    // ------------------------------------------------------------ Wiedergabe

    /**
     * Sucht die beste Quelle (werbefreier Server, dann werbefreie Twitch-Varianten)
     * und spielt sie ab. Quellen, die kürzlich Werbung hatten, werden übersprungen.
     */
    private void findSource(boolean initial) {
        if (searching) return;
        searching = true;
        lastSearch = System.currentTimeMillis();
        if (initial) {
            status.setVisibility(View.GONE);
            buffering.setVisibility(View.VISIBLE);
        }
        final Set<String> skip = recentAdSources();
        final boolean ab = adblockOn;
        final boolean px = useProxy;
        TwitchApi.async(() -> TwitchApi.findPlayback(login, ab, px, skip), p -> {
            searching = false;
            if (destroyed) return;
            if (p == null) {
                if (player.getMediaItemCount() == 0) {
                    showStatus("Verbindung zu Twitch fehlgeschlagen.\nBitte später nochmals versuchen.");
                } else {
                    retryLater();
                }
                return;
            }
            if (p.offline) {
                showStatus(displayName + " ist gerade offline.");
                return;
            }
            if (!p.source.equals(currentSource) || player.getMediaItemCount() == 0) play(p);
            if (adblockOn && p.hasAds) {
                // Alle Quellen haben gerade Werbung: verdecken und bald nochmals suchen
                setCover(true);
                retryLater();
            } else {
                setCover(false);
            }
        });
    }

    private Set<String> recentAdSources() {
        long now = System.currentTimeMillis();
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, Long> e : adSources.entrySet()) {
            if (now - e.getValue() < 180_000) out.add(e.getKey());
        }
        return out;
    }

    private final Runnable retryRun = () -> {
        if (destroyed || !covered) return;
        adSources.clear();
        findSource(false);
    };

    private void retryLater() {
        h.removeCallbacks(retryRun);
        h.postDelayed(retryRun, 15_000);
    }

    private void play(TwitchApi.Playback p) {
        generation++;
        currentSource = p.source;
        currentReduced = p.reduced;
        currentTag = p.source + "#" + generation;
        backupBadge.setText(p.proxy ? "WERBEFREIER SERVER" : "ERSATZ-STREAM · REDUZIERTE QUALITÄT");
        backupBadge.setVisibility(p.reduced ? View.VISIBLE : View.GONE);
        if (p.proxy && generation == 1) flash("Werbefreier Server aktiv ✓");

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(TwitchApi.UA)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10_000)
                .setReadTimeoutMs(15_000);
        AdBlock.Factory factory = new AdBlock.Factory(http, currentTag, this);
        HlsMediaSource source = new HlsMediaSource.Factory(factory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(MediaItem.fromUri(p.url));
        player.setMediaSource(source);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int state) {
            buffering.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
            if (state == Player.STATE_READY) {
                retries = 0;
                status.setVisibility(View.GONE);
            }
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            if (destroyed) return;
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player.seekToDefaultPosition();
                player.prepare();
                return;
            }
            if (retries < 4) {
                retries++;
                // Diese Quelle macht Probleme: eine andere nehmen
                if (!currentSource.isEmpty()) adSources.put(currentSource, System.currentTimeMillis());
                currentSource = "";
                h.postDelayed(() -> {
                    if (!destroyed) findSource(true);
                }, 1500L * retries);
            } else {
                showStatus("Der Stream konnte nicht abgespielt werden.");
            }
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            if (wantHeight > 0) applyQuality();
        }
    };

    private void showStatus(String text) {
        buffering.setVisibility(View.GONE);
        status.setText(text);
        status.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------ Werbeblocker

    @Override
    public void onPlaylist(String tag, boolean ads) {
        if (destroyed || !adblockOn || !tag.equals(currentTag)) return;
        if (ads) {
            // Werbung in der aktuellen Quelle: sofort verdecken und auf eine werbefreie Quelle wechseln
            setCover(true);
            adSources.put(currentSource, System.currentTimeMillis());
            if (System.currentTimeMillis() - lastSearch > 8000) findSource(false);
        } else if (!searching) {
            setCover(false);
        }
    }

    private void setCover(boolean on) {
        if (on == covered) return;
        covered = on;
        adCover.setVisibility(on ? View.VISIBLE : View.GONE);
        player.setVolume(on ? 0f : 1f);
        if (!on) h.removeCallbacks(retryRun);
    }

    private void setAdblock(boolean on) {
        adblockOn = on;
        prefs.edit().putBoolean("adblock", on).apply();
        if (!on) setCover(false);
        flash(on ? "Werbeblocker an" : "Werbeblocker aus");
    }

    private void setProxy(boolean on) {
        useProxy = on;
        prefs.edit().putBoolean("proxy", on).apply();
        adSources.clear();
        currentSource = "";
        findSource(true);
        flash(on ? "Werbefreier Server an" : "Werbefreier Server aus");
    }

    // ------------------------------------------------------------ Ansicht

    private void setMode(int m) {
        mode = m;
        prefs.edit().putInt("mode", m).apply();
        applyLayout();
    }

    private void applyLayout() {
        int w = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int hgt = root.getHeight() > 0 ? root.getHeight() : getResources().getDisplayMetrics().heightPixels;
        float[] side = {0.24f, 0.28f, 0.34f};
        float[] over = {0.26f, 0.30f, 0.36f};
        float[] small = {0.22f, 0.26f, 0.32f};
        float[] text = {13f, 15f, 18f};

        FrameLayout.LayoutParams vp = (FrameLayout.LayoutParams) videoBox.getLayoutParams();
        FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) chatPanel.getLayoutParams();
        int m = Util.dp(this, 24);
        vp.setMargins(0, 0, 0, 0);
        vp.setMarginEnd(0);
        cp.setMargins(0, 0, 0, 0);
        cp.setMarginStart(0);
        cp.setMarginEnd(0);
        chatPanel.setPadding(0, 0, 0, 0);
        chatPanel.setVisibility(View.VISIBLE);
        chatHeader.setVisibility(View.VISIBLE);

        switch (mode) {
            case 0:
                chatPanel.setVisibility(View.GONE);
                break;
            case 1: {
                int cw = (int) (w * side[size]);
                vp.rightMargin = cw;
                vp.setMarginEnd(cw);
                cp.width = cw;
                cp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                cp.gravity = Gravity.END;
                chatPanel.setBackgroundResource(R.drawable.chat_side);
                break;
            }
            case 2: {
                int cw = (int) (w * over[size]);
                cp.width = cw;
                cp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                cp.gravity = Gravity.END | Gravity.TOP;
                chatPanel.setBackgroundResource(R.drawable.chat_overlay);
                chatPanel.setPadding(cw / 5, 0, 0, 0);
                break;
            }
            default: {
                int cw = (int) (w * small[size]);
                cp.width = cw;
                cp.height = (int) (hgt * 0.45f);
                cp.gravity = Gravity.END | Gravity.BOTTOM;
                cp.setMargins(m, m, m, m);
                cp.setMarginEnd(m);
                chatPanel.setBackgroundResource(R.drawable.chat_small);
                chatPanel.setPadding(0, Util.dp(this, 8), 0, 0);
                chatHeader.setVisibility(View.GONE);
                break;
            }
        }
        videoBox.setLayoutParams(vp);
        chatPanel.setLayoutParams(cp);
        chatAdapter.setTextSize(text[size] - (mode == 3 ? 1.5f : 0f));
        if (chatAdapter.getItemCount() > 0) chatList.scrollToPosition(chatAdapter.getItemCount() - 1);
    }

    // ------------------------------------------------------------ Steuerung

    private final Runnable hideControls = this::hideControls;

    private void showControls() {
        bindInfo();
        controls.setVisibility(View.VISIBLE);
        controls.setAlpha(0f);
        controls.animate().alpha(1f).setDuration(180).start();
        buildMainPills(mode + 1);
        scheduleHide();
    }

    private void hideControls() {
        h.removeCallbacks(hideControls);
        controls.animate().alpha(0f).setDuration(180).withEndAction(() -> controls.setVisibility(View.GONE)).start();
        inQualityMenu = false;
    }

    private void scheduleHide() {
        h.removeCallbacks(hideControls);
        h.postDelayed(hideControls, 7000);
    }

    private TextView pill(String text, boolean selected, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTextColor(getResources().getColorStateList(R.color.pill_text));
        t.setTypeface(getResources().getFont(R.font.inter_semibold));
        t.setBackgroundResource(R.drawable.pill);
        t.setGravity(Gravity.CENTER);
        int ph = Util.dp(this, 22);
        t.setPadding(ph, 0, ph, 0);
        t.setFocusable(true);
        t.setClickable(true);
        t.setSelected(selected);
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Util.dp(this, 44));
        lp.setMarginEnd(Util.dp(this, 10));
        t.setLayoutParams(lp);
        pills.addView(t);
        return t;
    }

    private void label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setLetterSpacing(0.14f);
        t.setTextColor(getResources().getColor(R.color.text_dim));
        t.setTypeface(getResources().getFont(R.font.inter_semibold));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Util.dp(this, 12));
        lp.setMarginStart(Util.dp(this, 10));
        t.setLayoutParams(lp);
        pills.addView(t);
    }

    private void focusChild(int index) {
        if (index >= 0 && index < pills.getChildCount()) {
            View v = pills.getChildAt(index);
            if (v.isFocusable()) {
                v.requestFocus();
                return;
            }
        }
        for (int i = 0; i < pills.getChildCount(); i++) {
            if (pills.getChildAt(i).isFocusable()) {
                pills.getChildAt(i).requestFocus();
                return;
            }
        }
    }

    private void buildMainPills(int focusIndex) {
        inQualityMenu = false;
        pills.removeAllViews();
        label("ANSICHT");
        for (int i = 0; i < MODES.length; i++) {
            final int idx = i;
            pill(MODES[i], i == mode, v -> {
                setMode(idx);
                buildMainPills(idx + 1);
            });
        }
        label("CHAT");
        final int sizeIdx = pills.getChildCount();
        pill("Grösse: " + SIZES[size], false, v -> {
            size = (size + 1) % SIZES.length;
            prefs.edit().putInt("size", size).apply();
            applyLayout();
            buildMainPills(sizeIdx);
        });
        label("STREAM");
        final int qIdx = pills.getChildCount();
        pill("Qualität: " + qualityLabel(), false, v -> buildQualityPills());
        final int adIdx = pills.getChildCount();
        pill(adblockOn ? "Werbeblocker: An" : "Werbeblocker: Aus", adblockOn, v -> {
            setAdblock(!adblockOn);
            buildMainPills(adIdx);
        });
        final int pxIdx = pills.getChildCount();
        pill(useProxy ? "Werbefreier Server: An" : "Werbefreier Server: Aus", useProxy, v -> {
            setProxy(!useProxy);
            buildMainPills(pxIdx);
        });
        final int fIdx = pills.getChildCount();
        boolean followed = Follows.is(this, login);
        pill(followed ? "♥ Gefolgt" : "♡ Folgen", followed, v -> {
            boolean now = Follows.toggle(this, new Channel(login, displayName, avatar));
            flash(now ? "Du folgst jetzt " + displayName : displayName + " entfolgt");
            buildMainPills(fIdx);
        });
        focusChild(focusIndex);
    }

    private static class Quality {
        String label;
        int height;
        float fps;
    }

    private List<Quality> qualities() {
        Map<String, Quality> map = new LinkedHashMap<>();
        for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
            if (g.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < g.length; i++) {
                Format f = g.getTrackFormat(i);
                if (f.height <= 0) continue;
                Quality q = new Quality();
                q.height = f.height;
                q.fps = f.frameRate;
                q.label = f.height + "p" + (f.frameRate > 40 ? "60" : "");
                if (!map.containsKey(q.label)) map.put(q.label, q);
            }
        }
        List<Quality> list = new ArrayList<>(map.values());
        Collections.sort(list, (a, b) -> a.height != b.height ? b.height - a.height : Float.compare(b.fps, a.fps));
        return list;
    }

    private String qualityLabel() {
        if (wantHeight > 0) return wantHeight + "p" + (wantFps > 40 ? "60" : "");
        Format f = player.getVideoFormat();
        return f != null && f.height > 0 ? "Auto (" + f.height + "p)" : "Auto";
    }

    private void buildQualityPills() {
        inQualityMenu = true;
        pills.removeAllViews();
        pill("← Zurück", false, v -> buildMainPills(QUALITY_INDEX));
        label("QUALITÄT");
        pill("Automatisch", wantHeight == 0, v -> {
            wantHeight = 0;
            applyQuality();
            flash("Qualität: Automatisch");
            buildMainPills(QUALITY_INDEX);
        });
        for (final Quality q : qualities()) {
            boolean sel = wantHeight == q.height && (wantFps > 40) == (q.fps > 40);
            pill(q.label, sel, v -> {
                wantHeight = q.height;
                wantFps = q.fps;
                applyQuality();
                flash("Qualität: " + q.label);
                buildMainPills(QUALITY_INDEX);
            });
        }
        if (currentReduced) label("ERSATZ-STREAM: QUALITÄT EINGESCHRÄNKT");
        focusChild(2);
    }

    private void applyQuality() {
        if (wantHeight <= 0) {
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build());
            return;
        }
        Tracks.Group bestGroup = null;
        int bestIdx = -1;
        float bestScore = Float.MAX_VALUE;
        for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
            if (g.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < g.length; i++) {
                Format f = g.getTrackFormat(i);
                float score = Math.abs(f.height - wantHeight) * 10f + Math.abs(f.frameRate - wantFps);
                if (score < bestScore) {
                    bestScore = score;
                    bestGroup = g;
                    bestIdx = i;
                }
            }
        }
        if (bestGroup == null) return;
        if (bestGroup.isTrackSelected(bestIdx)) return;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setOverrideForType(new TrackSelectionOverride(bestGroup.getMediaTrackGroup(), bestIdx))
                .build());
    }

    private final Runnable hideFlash = () -> flash.animate().alpha(0f).setDuration(250).start();

    private void flash(String text) {
        flash.setText(text);
        flash.animate().alpha(1f).setDuration(150).start();
        h.removeCallbacks(hideFlash);
        h.postDelayed(hideFlash, 1600);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);

        if (controls.getVisibility() == View.VISIBLE && controls.getAlpha() > 0.5f) {
            scheduleHide();
            if (k == KeyEvent.KEYCODE_BACK) {
                if (inQualityMenu) buildMainPills(QUALITY_INDEX);
                else hideControls();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }

        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                setMode((mode + MODES.length - 1) % MODES.length);
                flash(MODES[mode]);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                setMode((mode + 1) % MODES.length);
                flash(MODES[mode]);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MENU:
                showControls();
                return true;
            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }
}
