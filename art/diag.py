"""Einmaliger Verbindungstest zu Twitch (wird beim Bauen ausgeführt)."""
import json
import urllib.parse
import urllib.request
import uuid

WEB = "kimne78kx3ncx6brgo4mf6uqjbnv4r9"
ANDROID = "kd1unb4b3q4t58fwlpcbzcbnm76a8fp"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"


def post(body, cid, extra=None):
    h = {"Client-ID": cid, "User-Agent": UA, "Content-Type": "application/json"}
    if extra:
        h.update(extra)
    req = urllib.request.Request("https://gql.twitch.tv/gql", data=json.dumps(body).encode(), headers=h)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, r.read().decode()[:700]
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:700]
    except Exception as e:
        return -1, repr(e)


def show(name, res):
    print("### DIAG", name, "->", res[0], res[1].replace("\n", " "))


A = ANDROID
show("A users", post({"query": "query{users(logins:[\"papaplatte\",\"caedrel\"]){login displayName profileImageURL(width:150) stream{id title viewersCount previewImageURL(width:640,height:360) game{displayName}}}}"}, A))
show("A german", post({"query": "query{streams(first:2,options:{broadcasterLanguages:[DE]}){edges{node{id broadcaster{login}}}}}"}, A))
show("A games", post({"query": "query{games(first:2){edges{node{displayName streams(first:1){edges{node{id}}}}}}}"}, A))
show("A searchFor", post({"query": "query{searchFor(userQuery:\"papa\",platform:\"web\"){channels{edges{item{...on User{login}}}}}}"}, A))
show("A searchUsers", post({"query": "query{searchUsers(userQuery:\"papa\",first:3){edges{node{login}}}}"}, A))
top = json.loads(post({"query": "query{streams(first:1){edges{node{broadcaster{login}}}}}"}, A)[1])
login = top["data"]["streams"]["edges"][0]["node"]["broadcaster"]["login"]
print("### DIAG login", login)
for pt in ["site", "embed", "popout", "autoplay", "picture-by-picture"]:
    for kind in ["persisted", "raw"]:
        if kind == "persisted":
            body = {"operationName": "PlaybackAccessToken", "variables": {"isLive": True, "login": login, "isVod": False, "vodID": "", "playerType": pt, "platform": "web"}, "extensions": {"persistedQuery": {"version": 1, "sha256Hash": "ed230aa1e33e07eebb8928504583da78a5173989fadfb1ac94be06a04f3cdbe9"}}}
        else:
            body = {"query": "query($l:String!,$p:String!){streamPlaybackAccessToken(channelName:$l,params:{platform:\"web\",playerBackend:\"mediaplayer\",playerType:$p}){value signature}}", "variables": {"l": login, "p": pt}}
        st, txt = post(body, A)
        ok = "signature" in txt
        print("### DIAG token", pt, kind, st, "OK" if ok else txt[:300])
        if not ok:
            continue
        t = json.loads(txt)["data"]["streamPlaybackAccessToken"]
        url = ("https://usher.ttvnw.net/api/channel/hls/" + login + ".m3u8?allow_source=true&fast_bread=true&player_backend=mediaplayer&playlist_include_framerate=true&reassignments_supported=true&supported_codecs=avc1&cdm=wv&p=1234567&play_session_id=" + uuid.uuid4().hex
               + "&sig=" + urllib.parse.quote(t["signature"]) + "&token=" + urllib.parse.quote(t["value"]))
        try:
            m = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=20).read().decode()
            variants = [l for l in m.split("\n") if l.startswith("http")]
            v = urllib.request.urlopen(urllib.request.Request(variants[-1], headers={"User-Agent": UA}), timeout=20).read().decode()
            titles = [l.split(",", 1)[1] for l in v.split("\n") if l.startswith("#EXTINF") and "," in l]
            print("### DIAG usher", pt, kind, "varianten", len(variants), "titel", titles[-4:], "stitched" in v)
        except Exception as e:
            print("### DIAG usher", pt, kind, "FEHLER", repr(e)[:200])
        break
raise SystemExit("DIAG ENDE (absichtlicher Abbruch, damit das Protokoll gespeichert wird)")
