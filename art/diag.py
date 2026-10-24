"""Einmaliger Verbindungstest zu Twitch (wird beim Bauen ausgeführt)."""
import json
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


q_streams = "query{streams(first:2){edges{node{id title viewersCount previewImageURL(width:640,height:360) game{displayName} broadcaster{login displayName profileImageURL(width:150)}}}}}"
dev = {"X-Device-Id": uuid.uuid4().hex, "Device-ID": uuid.uuid4().hex}

show("raw streams web", post({"query": q_streams}, WEB))
show("raw streams web+device", post({"query": q_streams}, WEB, dev))
show("raw streams android", post({"query": q_streams}, ANDROID))
show("raw streams web list", post([{"query": q_streams}], WEB))
show("raw users web", post({"query": "query{users(logins:[\"zackrawrr\",\"papaplatte\"]){login displayName stream{viewersCount}}}"}, WEB))
show("raw german web", post({"query": "query{streams(first:2,options:{broadcasterLanguages:[DE]}){edges{node{id broadcaster{login}}}}}"}, WEB))
show("raw games web", post({"query": "query{games(first:2){edges{node{displayName}}}}"}, WEB))
show("raw searchFor web", post({"query": "query{searchFor(userQuery:\"papa\",platform:\"web\"){channels{edges{item{...on User{login}}}}}}"}, WEB))
show("raw token web", post({"query": "query{streamPlaybackAccessToken(channelName:\"papaplatte\",params:{platform:\"web\",playerBackend:\"mediaplayer\",playerType:\"site\"}){value signature}}"}, WEB))
show("persisted token web", post({"operationName": "PlaybackAccessToken", "variables": {"isLive": True, "login": "papaplatte", "isVod": False, "vodID": "", "playerType": "site", "platform": "web"}, "extensions": {"persistedQuery": {"version": 1, "sha256Hash": "ed230aa1e33e07eebb8928504583da78a5173989fadfb1ac94be06a04f3cdbe9"}}}, WEB))
raise SystemExit("DIAG ENDE (absichtlicher Abbruch, damit das Protokoll gespeichert wird)")
