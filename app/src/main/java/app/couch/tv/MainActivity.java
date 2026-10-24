package app.couch.tv;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements CardAdapter.Actions, FollowAdapter.Actions {
    /** Name für die Begrüssung oben links. */
    private static final String USER_NAME = "Erlin";

    private final Handler h = new Handler(Looper.getMainLooper());

    private LinearLayout sidebar;
    private View scrim, navHome, navSearch, followDivider;
    private TextView greeting, subtitle, clock, snack, brand, followLabel;
    private ProgressBar loading;
    private RecyclerView rows, followList;
    private RowsAdapter rowsAdapter;
    private FollowAdapter followAdapter;

    private boolean expanded, firstFocusDone;
    private int collapsedW, expandedW;
    private ValueAnimator sideAnim;

    private List<Channel> followed = new ArrayList<>();
    private List<Stream> popular = new ArrayList<>();
    private List<Stream> german = new ArrayList<>();
    private List<TwitchApi.Category> categories = new ArrayList<>();
    private long lastFullLoad;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        sidebar = findViewById(R.id.sidebar);
        scrim = findViewById(R.id.scrim);
        greeting = findViewById(R.id.greeting);
        subtitle = findViewById(R.id.subtitle);
        clock = findViewById(R.id.clock);
        snack = findViewById(R.id.snack);
        brand = findViewById(R.id.brand);
        followLabel = findViewById(R.id.followLabel);
        followDivider = findViewById(R.id.followDivider);
        loading = findViewById(R.id.loading);
        rows = findViewById(R.id.rows);
        followList = findViewById(R.id.followList);
        navHome = findViewById(R.id.navHome);
        navSearch = findViewById(R.id.navSearch);

        setupNav(navHome, R.drawable.ic_home, "Start");
        setupNav(navSearch, R.drawable.ic_search, "Suche");
        navHome.setOnClickListener(v -> {
            rows.scrollToPosition(0);
            focusFirstCard();
        });
        navSearch.setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));

        collapsedW = Util.dp(this, 96);
        expandedW = Util.dp(this, 320);

        rowsAdapter = new RowsAdapter(this);
        rows.setLayoutManager(new LinearLayoutManager(this));
        rows.setItemAnimator(null);
        rows.setAdapter(rowsAdapter);

        followAdapter = new FollowAdapter(this);
        followList.setLayoutManager(new LinearLayoutManager(this));
        followList.setItemAnimator(null);
        followList.setAdapter(followAdapter);

        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener(focusWatcher);

        followed = new ArrayList<>(Follows.all(this));
        followAdapter.setItems(followed);
        updateHeader();
        navHome.requestFocus();
        setExpanded(false, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFollowed();
        if (System.currentTimeMillis() - lastFullLoad > 120_000) loadAll();
        h.removeCallbacks(tick);
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        int seconds;

        @Override
        public void run() {
            updateHeader();
            seconds += 30;
            if (seconds % 60 == 0) refreshFollowed();
            if (System.currentTimeMillis() - lastFullLoad > 180_000) loadAll();
            h.postDelayed(this, 30_000);
        }
    };

    private void setupNav(View nav, int icon, String label) {
        ((ImageView) nav.findViewById(R.id.navIcon)).setImageResource(icon);
        ((TextView) nav.findViewById(R.id.navLabel)).setText(label);
    }

    // ------------------------------------------------------------ Daten

    private void loadAll() {
        lastFullLoad = System.currentTimeMillis();
        TwitchApi.async(() -> TwitchApi.topStreams(30), list -> {
            loading.setVisibility(View.GONE);
            if (list == null) {
                if (popular.isEmpty()) showSnack("Keine Verbindung zu Twitch – versuche es gleich nochmals");
                lastFullLoad = 0;
                return;
            }
            popular = list;
            rebuildRows();
        });
        TwitchApi.async(() -> TwitchApi.germanStreams(24), list -> {
            if (list != null) {
                german = list;
                rebuildRows();
            }
        });
        TwitchApi.async(TwitchApi::topCategories, list -> {
            if (list != null) {
                categories = list;
                rebuildRows();
            }
        });
    }

    private void refreshFollowed() {
        final List<Channel> stored = Follows.all(this);
        if (stored.isEmpty()) {
            followed = new ArrayList<>();
            followAdapter.setItems(followed);
            rebuildRows();
            return;
        }
        final List<String> logins = Follows.logins(this);
        TwitchApi.async(() -> TwitchApi.channels(logins), list -> {
            if (list == null) return;
            Follows.updateMeta(this, list);
            List<Channel> live = new ArrayList<>();
            List<Channel> off = new ArrayList<>();
            for (Channel c : list) {
                if (!Follows.is(this, c.login)) continue; // inzwischen entfolgt
                if (c.live != null) live.add(c);
                else off.add(c);
            }
            Collections.sort(live, (a, b2) -> b2.live.viewers - a.live.viewers);
            Collections.sort(off, (a, b2) -> a.displayName.compareToIgnoreCase(b2.displayName));
            followed = new ArrayList<>(live);
            followed.addAll(off);
            followAdapter.setItems(followed);
            rebuildRows();
        });
    }

    private void rebuildRows() {
        List<RowsAdapter.Row> list = new ArrayList<>();
        List<Stream> liveFollowed = new ArrayList<>();
        for (Channel c : followed) if (c.live != null) liveFollowed.add(c.live);
        if (!liveFollowed.isEmpty()) list.add(new RowsAdapter.Row("Jetzt live · Von dir gefolgt", liveFollowed, true));
        if (!popular.isEmpty()) list.add(new RowsAdapter.Row("Beliebt auf Twitch", popular, false));
        if (!german.isEmpty()) list.add(new RowsAdapter.Row("Deutschsprachig", german, false));
        for (TwitchApi.Category c : categories) list.add(new RowsAdapter.Row(c.name, c.streams, false));
        rowsAdapter.setRows(list);
        updateHeader();

        if (!firstFocusDone && !list.isEmpty()) {
            firstFocusDone = true;
            rows.post(this::focusFirstCard);
        }
    }

    private void focusFirstCard() {
        RecyclerView.ViewHolder vh = rows.findViewHolderForAdapterPosition(0);
        if (vh instanceof RowsAdapter.VH) {
            RecyclerView list = ((RowsAdapter.VH) vh).list;
            View first = list.getChildAt(0);
            if (first != null) {
                first.requestFocus();
                return;
            }
            list.post(() -> {
                View f = list.getChildAt(0);
                if (f != null) f.requestFocus();
            });
        } else {
            rows.postDelayed(this::focusFirstCard, 150);
        }
    }

    private void updateHeader() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g = hour < 5 ? "Gute Nacht" : hour < 11 ? "Guten Morgen" : hour < 17 ? "Hallo" : hour < 22 ? "Guten Abend" : "Gute Nacht";
        greeting.setText(g + ", " + USER_NAME);
        clock.setText(new SimpleDateFormat("HH:mm", Locale.GERMAN).format(new Date()));
        int live = 0;
        for (Channel c : followed) if (c.live != null) live++;
        if (followed.isEmpty()) {
            subtitle.setText("Tipp: OK auf einem Stream lange drücken, um dem Streamer zu folgen");
        } else if (live == 0) {
            subtitle.setText("Gerade ist keiner deiner " + followed.size() + " Kanäle live");
        } else if (live == 1) {
            subtitle.setText("1 deiner Kanäle ist gerade live");
        } else {
            subtitle.setText(live + " deiner Kanäle sind gerade live");
        }
    }

    // ------------------------------------------------------------ Aktionen

    @Override
    public void open(Stream s) {
        PlayerActivity.start(this, s);
    }

    @Override
    public void open(Channel c) {
        if (c.live != null) {
            PlayerActivity.start(this, c.live);
        } else {
            showSnack(c.displayName + " ist gerade offline");
        }
    }

    @Override
    public void toggleFollow(Stream s) {
        boolean now = Follows.toggle(this, Channel.of(s));
        showSnack(now ? "♥ Du folgst jetzt " + s.displayName : s.displayName + " entfolgt");
        if (now) {
            followed.add(0, Channel.of(s));
        } else {
            removeFollowed(s.login);
        }
        followAdapter.setItems(followed);
        rebuildRows();
        refreshFollowed();
    }

    @Override
    public boolean isFollowed(String login) {
        return Follows.is(this, login);
    }

    @Override
    public void unfollow(Channel c) {
        Follows.remove(this, c.login);
        removeFollowed(c.login);
        followAdapter.setItems(followed);
        rebuildRows();
        showSnack(c.displayName + " entfolgt");
        if (followed.isEmpty()) navHome.requestFocus();
    }

    private void removeFollowed(String login) {
        for (int i = followed.size() - 1; i >= 0; i--) {
            if (followed.get(i).login.equalsIgnoreCase(login)) followed.remove(i);
        }
    }

    private final Runnable hideSnack = () -> snack.animate().alpha(0f).translationY(20f).setDuration(250).start();

    private void showSnack(String text) {
        snack.setText(text);
        snack.setTranslationY(20f);
        snack.animate().alpha(1f).translationY(0f).setDuration(200).start();
        h.removeCallbacks(hideSnack);
        h.postDelayed(hideSnack, 2400);
    }

    // ------------------------------------------------------------ Seitenleiste

    private final ViewTreeObserver.OnGlobalFocusChangeListener focusWatcher = (oldF, newF) -> {
        boolean inSide = newF != null && isInside(sidebar, newF);
        if (inSide != expanded) setExpanded(inSide, true);
    };

    private static boolean isInside(View parent, View v) {
        View cur = v;
        while (cur != null) {
            if (cur == parent) return true;
            if (!(cur.getParent() instanceof View)) return false;
            cur = (View) cur.getParent();
        }
        return false;
    }

    private void setExpanded(boolean e, boolean animate) {
        expanded = e;
        if (sideAnim != null) sideAnim.cancel();
        final int from = sidebar.getLayoutParams().width;
        final int to = e ? expandedW : collapsedW;
        final float aFrom = brand.getAlpha();
        final float aTo = e ? 1f : 0f;
        if (e) {
            scrim.setVisibility(View.VISIBLE);
        }
        sideAnim = ValueAnimator.ofFloat(0f, 1f);
        sideAnim.setDuration(animate ? 220 : 0);
        sideAnim.setInterpolator(new DecelerateInterpolator());
        sideAnim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            ViewGroup.LayoutParams lp = sidebar.getLayoutParams();
            lp.width = (int) (from + (to - from) * f);
            sidebar.setLayoutParams(lp);
            float al = aFrom + (aTo - aFrom) * f;
            brand.setAlpha(al);
            followLabel.setAlpha(al);
            followDivider.setAlpha(1f - al);
            ((View) navHome.findViewById(R.id.navLabel)).setAlpha(al);
            ((View) navSearch.findViewById(R.id.navLabel)).setAlpha(al);
            followAdapter.setLabelAlpha(followList, al);
            scrim.setAlpha(al);
        });
        sideAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!expanded) scrim.setVisibility(View.GONE);
            }
        });
        sideAnim.start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_DOWN) {
            if (!expanded) {
                // Zurück öffnet zuerst die Seitenleiste, ein zweites Zurück beendet die App
                navHome.requestFocus();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }
}
