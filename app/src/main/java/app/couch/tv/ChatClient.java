package app.couch.tv;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/** Anonymer Twitch-Chat (nur lesen) über IRC-WebSocket. */
public class ChatClient {
    public static class Message {
        public String name = "";
        public int color;
        public String text = "";
        public boolean system;
        /** {start, end(exklusiv), emoteId-Index} in Zeichen-Positionen von text */
        public final List<int[]> emoteRanges = new ArrayList<>();
        public final List<String> emoteIds = new ArrayList<>();
        public CharSequence rendered;
    }

    public interface Listener {
        void onMessage(Message m);

        void onState(boolean connected);
    }

    private static final int[] PALETTE = {
            0xFFFF7A90, 0xFF7DD3FC, 0xFFA78BFA, 0xFF86EFAC, 0xFFFDE68A,
            0xFFF9A8D4, 0xFFFDBA74, 0xFF67E8F9, 0xFFC4B5FD, 0xFFBEF264
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private final String channel;
    private final Listener listener;
    private WebSocket ws;
    private boolean closed;

    public ChatClient(String channel, Listener listener) {
        this.channel = channel.toLowerCase();
        this.listener = listener;
    }

    public void connect() {
        if (closed) return;
        Request req = new Request.Builder().url("wss://irc-ws.chat.twitch.tv:443").build();
        ws = TwitchApi.HTTP.newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket s, @NonNull Response response) {
                s.send("CAP REQ :twitch.tv/tags twitch.tv/commands");
                s.send("PASS SCHMOOPIIE");
                s.send("NICK justinfan" + (10000 + (int) (Math.random() * 80000)));
                s.send("JOIN #" + channel);
                main.post(() -> listener.onState(true));
            }

            @Override
            public void onMessage(@NonNull WebSocket s, @NonNull String text) {
                for (String line : text.split("\r\n")) {
                    if (line.isEmpty()) continue;
                    if (line.startsWith("PING")) {
                        s.send("PONG" + line.substring(4));
                        continue;
                    }
                    final Message m = parse(line);
                    if (m != null) main.post(() -> listener.onMessage(m));
                }
            }

            @Override
            public void onFailure(@NonNull WebSocket s, @NonNull Throwable t, Response r) {
                main.post(() -> listener.onState(false));
                retry();
            }

            @Override
            public void onClosed(@NonNull WebSocket s, int code, @NonNull String reason) {
                retry();
            }
        });
    }

    private void retry() {
        if (closed) return;
        main.postDelayed(this::connect, 3000);
    }

    public void close() {
        closed = true;
        main.removeCallbacksAndMessages(null);
        if (ws != null) ws.close(1000, "bye");
    }

    // ------------------------------------------------------------ IRC-Parser

    static Message parse(String line) {
        Map<String, String> tags = new HashMap<>();
        String rest = line;
        if (rest.startsWith("@")) {
            int sp = rest.indexOf(' ');
            if (sp < 0) return null;
            for (String kv : rest.substring(1, sp).split(";")) {
                int eq = kv.indexOf('=');
                if (eq > 0) tags.put(kv.substring(0, eq), kv.substring(eq + 1));
            }
            rest = rest.substring(sp + 1);
        }
        String prefix = "";
        if (rest.startsWith(":")) {
            int sp = rest.indexOf(' ');
            if (sp < 0) return null;
            prefix = rest.substring(1, sp);
            rest = rest.substring(sp + 1);
        }
        int sp = rest.indexOf(' ');
        String command = sp < 0 ? rest : rest.substring(0, sp);
        int colon = rest.indexOf(" :");
        String trailing = colon >= 0 ? rest.substring(colon + 2) : "";

        if ("PRIVMSG".equals(command)) {
            Message m = new Message();
            String nick = prefix.contains("!") ? prefix.substring(0, prefix.indexOf('!')) : prefix;
            String dn = unescape(tags.get("display-name"));
            m.name = dn.isEmpty() ? nick : dn;
            m.color = color(tags.get("color"), m.name);
            m.text = trailing;
            if (m.text.startsWith("\u0001ACTION ") && m.text.endsWith("\u0001")) {
                m.text = m.text.substring(8, m.text.length() - 1);
            }
            parseEmotes(m, tags.get("emotes"));
            return m;
        }
        if ("USERNOTICE".equals(command)) {
            String sys = unescape(tags.get("system-msg"));
            if (sys.isEmpty()) return null;
            Message m = new Message();
            m.system = true;
            m.text = sys + (trailing.isEmpty() ? "" : " – " + trailing);
            return m;
        }
        return null;
    }

    private static void parseEmotes(Message m, String tag) {
        if (tag == null || tag.isEmpty()) return;
        String text = m.text;
        int cpCount = text.codePointCount(0, text.length());
        for (String part : tag.split("/")) {
            int c = part.indexOf(':');
            if (c <= 0) continue;
            String id = part.substring(0, c);
            for (String range : part.substring(c + 1).split(",")) {
                int dash = range.indexOf('-');
                if (dash <= 0) continue;
                try {
                    int a = Integer.parseInt(range.substring(0, dash));
                    int b = Integer.parseInt(range.substring(dash + 1));
                    if (a < 0 || b >= cpCount || a > b) continue;
                    int start = text.offsetByCodePoints(0, a);
                    int end = text.offsetByCodePoints(0, b + 1);
                    m.emoteIds.add(id);
                    m.emoteRanges.add(new int[]{start, end, m.emoteIds.size() - 1});
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\s", " ").replace("\\:", ";").replace("\\\\", "\\").replace("\\r", "").replace("\\n", "");
    }

    private static int color(String hex, String name) {
        int c;
        if (hex != null && hex.startsWith("#") && hex.length() == 7) {
            try {
                c = Color.parseColor(hex);
                // Zu dunkle Farben auf dunklem Hintergrund aufhellen
                float[] hsv = new float[3];
                Color.colorToHSV(c, hsv);
                if (hsv[2] < 0.6f) hsv[2] = 0.75f;
                if (hsv[1] > 0.85f) hsv[1] = 0.7f;
                return Color.HSVToColor(hsv);
            } catch (Exception ignored) {
            }
        }
        return PALETTE[Math.abs(name.hashCode()) % PALETTE.length];
    }
}
