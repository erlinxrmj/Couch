package app.couch.tv;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Zugriff auf Twitch über die öffentliche GraphQL-Schnittstelle (ohne Login),
 * nach dem Vorbild von SmartTwitchTV (fgl27).
 */
public final class TwitchApi {
    private static final String TAG = "TwitchApi";
    public static final String WEB_CLIENT_ID = "kimne78kx3ncx6brgo4mf6uqjbnv4r9";
    public static final String ANDROID_CLIENT_ID = "kd1unb4b3q4t58fwlpcbzcbnm76a8fp";
    public static final String UA =
            "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private static final String GQL = "https://gql.twitch.tv/gql";
    private static final String TOKEN_HASH = "ed230aa1e33e07eebb8928504583da78a5173989fadfb1ac94be06a04f3cdbe9";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    public static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Random RND = new Random();

    private static final String STREAM_CORE =
            "id title viewersCount previewImageURL(width:640,height:360) game{displayName}";
    private static final String STREAM_FIELDS =
            STREAM_CORE + " broadcaster{login displayName profileImageURL(width:150)}";

    private TwitchApi() {}

    public interface Callback<T> {
        void done(T result);
    }

    public interface Job<T> {
        T run() throws Exception;
    }

    /** Führt eine Anfrage im Hintergrund aus; das Ergebnis (oder null bei Fehler) kommt im UI-Thread an. */
    public static <T> void async(Job<T> job, Callback<T> cb) {
        POOL.execute(() -> {
            T r = null;
            try {
                r = job.run();
            } catch (Exception e) {
                Log.w(TAG, "Anfrage fehlgeschlagen", e);
            }
            final T res = r;
            MAIN.post(() -> cb.done(res));
        });
    }

    // ---------------------------------------------------------------- HTTP

    private static JSONObject post(String body, String clientId) throws Exception {
        Request req = new Request.Builder()
                .url(GQL)
                .header("Client-ID", clientId)
                .header("User-Agent", UA)
                .post(RequestBody.create(body, JSON_TYPE))
                .build();
        try (Response resp = HTTP.newCall(req).execute()) {
            ResponseBody rb = resp.body();
            String s = rb != null ? rb.string() : "";
            if (!resp.isSuccessful()) throw new Exception("HTTP " + resp.code() + " " + s);
            return new JSONObject(s);
        }
    }

    public static String get(String url) throws Exception {
        Request req = new Request.Builder().url(url).header("User-Agent", UA).build();
        try (Response resp = HTTP.newCall(req).execute()) {
            ResponseBody rb = resp.body();
            String s = rb != null ? rb.string() : "";
            if (!resp.isSuccessful()) throw new HttpException(resp.code());
            return s;
        }
    }

    public static class HttpException extends Exception {
        public final int code;

        HttpException(int code) {
            super("HTTP " + code);
            this.code = code;
        }
    }

    private static JSONObject query(String q, JSONObject vars) throws Exception {
        JSONObject body = new JSONObject();
        body.put("query", q);
        if (vars != null) body.put("variables", vars);
        JSONObject res = post(body.toString(), WEB_CLIENT_ID);
        JSONObject data = res.optJSONObject("data");
        if (data == null) throw new Exception("GQL-Fehler: " + res.optJSONArray("errors"));
        return data;
    }

    // ---------------------------------------------------------------- Parsing

    private static String str(JSONObject o, String key) {
        if (o == null) return "";
        Object v = o.opt(key);
        return (v == null || v == JSONObject.NULL) ? "" : v.toString();
    }

    private static Stream parseStream(JSONObject n, JSONObject user) {
        if (n == null) return null;
        JSONObject b = user != null ? user : n.optJSONObject("broadcaster");
        if (b == null) return null;
        Stream s = new Stream();
        s.login = str(b, "login");
        s.displayName = str(b, "displayName");
        if (s.displayName.isEmpty()) s.displayName = s.login;
        s.avatar = str(b, "profileImageURL");
        s.title = str(n, "title");
        s.viewers = n.optInt("viewersCount");
        s.preview = str(n, "previewImageURL");
        s.game = str(n.optJSONObject("game"), "displayName");
        return s.login.isEmpty() ? null : s;
    }

    private static List<Stream> edges(JSONObject conn) {
        List<Stream> out = new ArrayList<>();
        if (conn == null) return out;
        JSONArray arr = conn.optJSONArray("edges");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject e = arr.optJSONObject(i);
            Stream s = parseStream(e != null ? e.optJSONObject("node") : null, null);
            if (s != null) out.add(s);
        }
        return out;
    }

    private static Channel parseUser(JSONObject u) {
        if (u == null) return null;
        Channel c = new Channel(str(u, "login"), str(u, "displayName"), str(u, "profileImageURL"));
        if (c.displayName.isEmpty()) c.displayName = c.login;
        JSONObject st = u.optJSONObject("stream");
        if (st != null) c.live = parseStream(st, u);
        return c.login.isEmpty() ? null : c;
    }

    // ---------------------------------------------------------------- Listen

    public static class Category {
        public String name;
        public List<Stream> streams;
    }

    public static List<Stream> topStreams(int n) throws Exception {
        return edges(query("query{streams(first:" + n + "){edges{node{" + STREAM_FIELDS + "}}}}", null)
                .optJSONObject("streams"));
    }

    public static List<Stream> germanStreams(int n) throws Exception {
        return edges(query("query{streams(first:" + n + ",options:{broadcasterLanguages:[DE]}){edges{node{"
                + STREAM_FIELDS + "}}}}", null).optJSONObject("streams"));
    }

    /** Die beliebtesten Kategorien mit ihren Streams. */
    public static List<Category> topCategories() throws Exception {
        List<Category> out = new ArrayList<>();
        try {
            JSONObject games = query("query{games(first:6){edges{node{displayName streams(first:16){edges{node{"
                    + STREAM_FIELDS + "}}}}}}}", null).optJSONObject("games");
            JSONArray arr = games != null ? games.optJSONArray("edges") : null;
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject g = arr.optJSONObject(i).optJSONObject("node");
                    if (g == null) continue;
                    Category c = new Category();
                    c.name = str(g, "displayName");
                    c.streams = edges(g.optJSONObject("streams"));
                    if (!c.streams.isEmpty()) out.add(c);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "games-Abfrage fehlgeschlagen, nutze feste Kategorien", e);
        }
        if (!out.isEmpty()) return out;

        String[] fixed = {"Just Chatting", "Grand Theft Auto V", "League of Legends", "Fortnite", "Counter-Strike"};
        StringBuilder q = new StringBuilder("query{");
        for (int i = 0; i < fixed.length; i++) {
            q.append("g").append(i).append(":game(name:\"").append(fixed[i])
                    .append("\"){displayName streams(first:16){edges{node{").append(STREAM_FIELDS).append("}}}}");
        }
        q.append("}");
        JSONObject data = query(q.toString(), null);
        for (int i = 0; i < fixed.length; i++) {
            JSONObject g = data.optJSONObject("g" + i);
            if (g == null) continue;
            Category c = new Category();
            c.name = str(g, "displayName");
            c.streams = edges(g.optJSONObject("streams"));
            if (!c.streams.isEmpty()) out.add(c);
        }
        return out;
    }

    /** Kanalinfos inkl. Live-Status für eine Liste von Logins. */
    public static List<Channel> channels(List<String> logins) throws Exception {
        List<Channel> out = new ArrayList<>();
        for (int start = 0; start < logins.size(); start += 100) {
            List<String> part = logins.subList(start, Math.min(logins.size(), start + 100));
            JSONObject vars = new JSONObject();
            vars.put("l", new JSONArray(part));
            JSONArray users = query("query($l:[String!]){users(logins:$l){login displayName profileImageURL(width:150) stream{"
                    + STREAM_CORE + "}}}", vars).optJSONArray("users");
            if (users == null) continue;
            for (int i = 0; i < users.length(); i++) {
                Channel c = parseUser(users.optJSONObject(i));
                if (c != null) out.add(c);
            }
        }
        return out;
    }

    public static Channel channel(String login) throws Exception {
        List<Channel> l = channels(Collections.singletonList(login));
        return l.isEmpty() ? null : l.get(0);
    }

    /** Kanalsuche. Probiert mehrere Abfragen, weil Twitch diese gelegentlich ändert. */
    public static List<Channel> search(String q) throws Exception {
        List<Channel> out = new ArrayList<>();
        String user = "...on User{login displayName profileImageURL(width:150) stream{" + STREAM_CORE + "}}";
        JSONObject vars = new JSONObject();
        vars.put("q", q);

        try {
            JSONObject sf = query("query($q:String!){searchFor(userQuery:$q,platform:\"web\"){channels{edges{item{"
                    + user + "}}}}}", vars).optJSONObject("searchFor");
            JSONObject ch = sf != null ? sf.optJSONObject("channels") : null;
            JSONArray arr = ch != null ? ch.optJSONArray("edges") : null;
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject e = arr.optJSONObject(i);
                    Channel c = parseUser(e != null ? e.optJSONObject("item") : null);
                    if (c != null) out.add(c);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "searchFor fehlgeschlagen", e);
        }

        if (out.isEmpty()) {
            try {
                JSONObject su = query("query($q:String!){searchUsers(userQuery:$q,first:20){edges{node{"
                        + user + "}}}}", vars).optJSONObject("searchUsers");
                JSONArray arr = su != null ? su.optJSONArray("edges") : null;
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject e = arr.optJSONObject(i);
                        Channel c = parseUser(e != null ? e.optJSONObject("node") : null);
                        if (c != null) out.add(c);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "searchUsers fehlgeschlagen", e);
            }
        }

        // Exakter Treffer immer zuoberst
        String login = q.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (login.matches("[a-z0-9_]{2,25}")) {
            try {
                Channel exact = channel(login);
                if (exact != null) {
                    for (int i = out.size() - 1; i >= 0; i--) {
                        if (out.get(i).login.equalsIgnoreCase(exact.login)) out.remove(i);
                    }
                    out.add(0, exact);
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- Wiedergabe

    /** Holt Token+Signatur für einen Live-Stream mit einem bestimmten Player-Typ. */
    private static String[] token(String login, String playerType, String clientId) throws Exception {
        JSONObject vars = new JSONObject();
        vars.put("isLive", true);
        vars.put("login", login);
        vars.put("isVod", false);
        vars.put("vodID", "");
        vars.put("playerType", playerType);
        vars.put("platform", "web");

        JSONObject body = new JSONObject();
        body.put("operationName", "PlaybackAccessToken");
        body.put("variables", vars);
        body.put("extensions", new JSONObject().put("persistedQuery",
                new JSONObject().put("version", 1).put("sha256Hash", TOKEN_HASH)));

        JSONObject t = null;
        try {
            JSONObject data = post(body.toString(), clientId).optJSONObject("data");
            t = data != null ? data.optJSONObject("streamPlaybackAccessToken") : null;
        } catch (Exception e) {
            Log.w(TAG, "Token (persisted) fehlgeschlagen", e);
        }
        if (t == null) {
            JSONObject raw = new JSONObject();
            raw.put("query", "query($login:String!,$pt:String!){streamPlaybackAccessToken(channelName:$login,"
                    + "params:{platform:\"web\",playerBackend:\"mediaplayer\",playerType:$pt}){value signature}}");
            raw.put("variables", new JSONObject().put("login", login).put("pt", playerType));
            JSONObject data = post(raw.toString(), clientId).optJSONObject("data");
            t = data != null ? data.optJSONObject("streamPlaybackAccessToken") : null;
        }
        if (t == null || str(t, "value").isEmpty()) return null;
        return new String[]{str(t, "value"), str(t, "signature")};
    }

    private static String usherUrl(String login, String[] tok) {
        String sid = Long.toHexString(RND.nextLong()) + Long.toHexString(RND.nextLong());
        return "https://usher.ttvnw.net/api/channel/hls/" + login.toLowerCase(Locale.ROOT) + ".m3u8"
                + "?allow_source=true&allow_audio_only=true&fast_bread=true&player_backend=mediaplayer"
                + "&playlist_include_framerate=true&reassignments_supported=true&supported_codecs=avc1"
                + "&cdm=wv&p=" + RND.nextInt(9_999_999) + "&play_session_id=" + sid
                + "&sig=" + Uri.encode(tok[1]) + "&token=" + Uri.encode(tok[0]);
    }

    /** Ergebnis einer Stream-Anfrage. url == null bedeutet offline/nicht verfügbar. */
    public static class Playback {
        public String url;
        public String master;
        public boolean hasAds;
    }

    /** Haupt-Stream (beste Qualität). */
    public static Playback mainPlayback(String login) throws Exception {
        String[] tok = null;
        for (String cid : new String[]{WEB_CLIENT_ID, ANDROID_CLIENT_ID}) {
            try {
                tok = token(login, "site", cid);
                if (tok != null) break;
            } catch (Exception e) {
                Log.w(TAG, "Token mit " + cid + " fehlgeschlagen", e);
            }
        }
        if (tok == null) return null;
        Playback p = new Playback();
        p.url = usherUrl(login, tok);
        try {
            p.master = get(p.url);
        } catch (HttpException e) {
            if (e.code == 404 || e.code == 403) return new Playback(); // offline
            throw e;
        }
        String variant = lowestVariant(p.url, p.master);
        if (variant != null) {
            try {
                p.hasAds = AdBlock.hasAds(get(variant));
            } catch (Exception ignored) {
            }
        }
        return p;
    }

    /**
     * Sucht eine werbefreie Ersatz-Ausgabe des Streams (andere Player-Typen
     * bekommen von Twitch oft keine Werbung eingeblendet).
     */
    public static Playback backupPlayback(String login) {
        String[] types = {"embed", "popout", "autoplay", "picture-by-picture", "thunderdome"};
        for (String pt : types) {
            try {
                String[] tok = token(login, pt, WEB_CLIENT_ID);
                if (tok == null) continue;
                String url = usherUrl(login, tok);
                String master = get(url);
                String variant = bestVariant(url, master);
                if (variant == null) continue;
                if (!AdBlock.hasAds(get(variant))) {
                    Playback p = new Playback();
                    p.url = url;
                    p.master = master;
                    Log.i(TAG, "Werbefreier Ersatz gefunden: " + pt);
                    return p;
                }
            } catch (Exception e) {
                Log.w(TAG, "Ersatz " + pt + " fehlgeschlagen", e);
            }
        }
        return null;
    }

    private static List<String[]> variants(String base, String master) {
        // [url, bandwidth]
        List<String[]> out = new ArrayList<>();
        if (master == null) return out;
        String[] lines = master.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].trim();
            if (l.startsWith("#EXT-X-STREAM-INF")) {
                String bw = "0";
                int b = l.indexOf("BANDWIDTH=");
                if (b >= 0) {
                    int e = l.indexOf(',', b);
                    bw = l.substring(b + 10, e < 0 ? l.length() : e);
                }
                for (int j = i + 1; j < lines.length; j++) {
                    String u = lines[j].trim();
                    if (u.isEmpty() || u.startsWith("#")) continue;
                    out.add(new String[]{resolve(base, u), bw});
                    break;
                }
            }
        }
        return out;
    }

    private static String resolve(String base, String u) {
        if (u.startsWith("http")) return u;
        int slash = base.lastIndexOf('/');
        return base.substring(0, slash + 1) + u;
    }

    public static String lowestVariant(String base, String master) {
        String best = null;
        long bw = Long.MAX_VALUE;
        for (String[] v : variants(base, master)) {
            long b = parseLong(v[1]);
            if (b < bw) {
                bw = b;
                best = v[0];
            }
        }
        return best;
    }

    public static String bestVariant(String base, String master) {
        String best = null;
        long bw = -1;
        for (String[] v : variants(base, master)) {
            long b = parseLong(v[1]);
            if (b > bw) {
                bw = b;
                best = v[0];
            }
        }
        return best;
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
