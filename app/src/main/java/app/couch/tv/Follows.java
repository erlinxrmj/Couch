package app.couch.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Eigene Folgen-Liste, lokal auf dem Fernseher gespeichert.
 * (Twitch erlaubt fremden Apps das Folgen über die Schnittstelle nicht mehr.)
 */
public final class Follows {
    private static final String PREFS = "follows";
    private static final String KEY = "list";
    private static List<Channel> cache;

    private Follows() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<Channel> all(Context c) {
        if (cache == null) {
            cache = new ArrayList<>();
            try {
                JSONArray arr = new JSONArray(prefs(c).getString(KEY, "[]"));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    cache.add(new Channel(o.optString("login"), o.optString("name"), o.optString("avatar")));
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>(cache);
    }

    public static List<String> logins(Context c) {
        List<String> out = new ArrayList<>();
        for (Channel ch : all(c)) out.add(ch.login);
        return out;
    }

    public static synchronized boolean is(Context c, String login) {
        if (login == null) return false;
        for (Channel ch : all(c)) if (ch.login.equalsIgnoreCase(login)) return true;
        return false;
    }

    public static synchronized void add(Context c, Channel ch) {
        if (is(c, ch.login)) return;
        all(c);
        cache.add(new Channel(ch.login.toLowerCase(), ch.displayName, ch.avatar));
        save(c);
    }

    public static synchronized void remove(Context c, String login) {
        all(c);
        for (int i = cache.size() - 1; i >= 0; i--) {
            if (cache.get(i).login.equalsIgnoreCase(login)) cache.remove(i);
        }
        save(c);
    }

    /** Umschalten. Gibt true zurück, wenn man danach folgt. */
    public static boolean toggle(Context c, Channel ch) {
        if (is(c, ch.login)) {
            remove(c, ch.login);
            return false;
        }
        add(c, ch);
        return true;
    }

    /** Name und Profilbild aktuell halten. */
    public static synchronized void updateMeta(Context c, List<Channel> fresh) {
        all(c);
        boolean changed = false;
        for (Channel f : fresh) {
            for (Channel ch : cache) {
                if (ch.login.equalsIgnoreCase(f.login)) {
                    if (!f.displayName.isEmpty() && !f.displayName.equals(ch.displayName)) {
                        ch.displayName = f.displayName;
                        changed = true;
                    }
                    if (!f.avatar.isEmpty() && !f.avatar.equals(ch.avatar)) {
                        ch.avatar = f.avatar;
                        changed = true;
                    }
                }
            }
        }
        if (changed) save(c);
    }

    private static void save(Context c) {
        JSONArray arr = new JSONArray();
        try {
            for (Channel ch : cache) {
                JSONObject o = new JSONObject();
                o.put("login", ch.login);
                o.put("name", ch.displayName);
                o.put("avatar", ch.avatar);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString(KEY, arr.toString()).apply();
    }
}
