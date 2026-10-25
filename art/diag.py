"""Einmaliger Test: welche werbefreien Wege funktionieren (wird beim Bauen ausgeführt)."""
import json
import random
import urllib.parse
import urllib.request
import uuid

A = "kd1unb4b3q4t58fwlpcbzcbnm76a8fp"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
HASH = "ed230aa1e33e07eebb8928504583da78a5173989fadfb1ac94be06a04f3cdbe9"


def gql(body):
    req = urllib.request.Request("https://gql.twitch.tv/gql", data=json.dumps(body).encode(),
                                 headers={"Client-ID": A, "User-Agent": UA, "Content-Type": "application/json"})
    return json.loads(urllib.request.urlopen(req, timeout=20).read().decode())


def get(url, headers=None):
    h = {"User-Agent": UA}
    if headers:
        h.update(headers)
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=15) as r:
            return r.status, r.read().decode(errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode(errors="replace")[:200]
    except Exception as e:
        return -1, repr(e)[:200]


def token(login, pt, platform="web"):
    d = gql({"operationName": "PlaybackAccessToken", "variables": {"isLive": True, "login": login, "isVod": False, "vodID": "", "playerType": pt, "platform": platform}, "extensions": {"persistedQuery": {"version": 1, "sha256Hash": HASH}}})
    t = d["data"]["streamPlaybackAccessToken"]
    return t["value"], t["signature"]


def params():
    return ("allow_source=true&fast_bread=true&player_backend=mediaplayer&playlist_include_framerate=true"
            "&reassignments_supported=true&supported_codecs=avc1&cdm=wv&p=" + str(random.randint(1, 9999999))
            + "&play_session_id=" + uuid.uuid4().hex)


def analyse(name, status, master):
    if status != 200 or "#EXTM3U" not in master:
        print("### DIAG", name, "STATUS", status, master[:160].replace("\n", " "))
        return
    variants = [l for l in master.split("\n") if l.startswith("http")]
    if not variants:
        print("### DIAG", name, "keine Varianten", master[:200].replace("\n", " "))
        return
    st, v = get(variants[0])
    titles = [l.split(",", 1)[1] for l in v.split("\n") if l.startswith("#EXTINF") and "," in l]
    ad = any(t and t != "live" for t in titles) or "stitched" in v
    res = [l for l in master.split("\n") if "RESOLUTION" in l]
    print("### DIAG", name, "OK varianten", len(variants), "WERBUNG" if ad else "werbefrei", titles[-3:], (res[0][-60:] if res else ""))


top = gql({"query": "query{streams(first:12){edges{node{broadcaster{login}}}}}"})
logins = [e["node"]["broadcaster"]["login"] for e in top["data"]["streams"]["edges"]]
de = gql({"query": "query{streams(first:3,options:{broadcasterLanguages:[DE]}){edges{node{broadcaster{login}}}}}"})
logins = [e["node"]["broadcaster"]["login"] for e in de["data"]["streams"]["edges"]] + logins[:3]
print("### DIAG logins", logins)

for login in logins[:4]:
    print("### DIAG ===== Kanal", login)
    for pt, plat in [("site", "web"), ("embed", "web"), ("popout", "web"), ("autoplay", "web"),
                     ("picture-by-picture", "web"), ("site", "android"), ("site", "ios"), ("mobile", "android")]:
        try:
            tv, sig = token(login, pt, plat)
            url = "https://usher.ttvnw.net/api/channel/hls/" + login + ".m3u8?" + params() + "&sig=" + urllib.parse.quote(sig) + "&token=" + urllib.parse.quote(tv)
            st, m = get(url)
            analyse("direkt " + pt + "/" + plat, st, m)
        except Exception as e:
            print("### DIAG direkt", pt, plat, "FEHLER", repr(e)[:150])
    tv, sig = token(login, "site")
    with_tok = login + ".m3u8?token=" + urllib.parse.quote(tv) + "&sig=" + sig + "&" + params()
    for name, url, hdr in [
        ("proxy kwabang", "https://api.twitch.hkg.kwabang.net/hls-raw/" + with_tok, None),
        ("proxy T1080", "https://api1080.ontdb.com/" + with_tok, None),
        ("proxy ttv.lol", "https://api.ttv.lol/playlist/" + login + ".m3u8" + urllib.parse.quote("?" + params()), {"X-Donate-To": "https://ttv.lol/donate"}),
        ("proxy luminous eu", "https://eu.luminous.dev/live/" + login + "?allow_source=true&allow_audio_only=true&fast_bread=true", None),
        ("proxy luminous eu2", "https://eu2.luminous.dev/live/" + login + "?allow_source=true&allow_audio_only=true&fast_bread=true", None),
        ("proxy perfprod", "https://lb-eu.cdn-perfprod.com/live/" + login + "?allow_source=true&allow_audio_only=true&fast_bread=true", None),
        ("proxy perfprod2", "https://lb-eu2.cdn-perfprod.com/live/" + login + "?allow_source=true&allow_audio_only=true&fast_bread=true", None),
    ]:
        st, m = get(url, hdr)
        analyse(name, st, m)
raise SystemExit("DIAG ENDE (absichtlicher Abbruch, damit das Protokoll gespeichert wird)")
