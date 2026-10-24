package app.couch.tv;

/** Ein Kanal (Streamer). live ist null, wenn der Kanal offline ist. */
public class Channel {
    public String login = "";
    public String displayName = "";
    public String avatar = "";
    public Stream live;

    public Channel() {}

    public Channel(String login, String displayName, String avatar) {
        this.login = login;
        this.displayName = displayName;
        this.avatar = avatar;
    }

    public static Channel of(Stream s) {
        Channel c = new Channel(s.login, s.displayName, s.avatar);
        c.live = s;
        return c;
    }
}
