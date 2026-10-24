# Couch – Twitch für Android TV

Eigene, moderne Twitch-App für den Fernseher:

- **Startseite:** zuoberst eine Reihe mit deinen gefolgten Streamern, die gerade live sind, darunter beliebte Streams, deutschsprachige Streams und Reihen nach Kategorien.
- **Seitenleiste links:** alle gefolgten Streamer. Wer live ist, steht oben, mit rotem Ring und Zuschauerzahl. Die Leiste klappt auf, sobald du nach links gehst.
- **Folgen:** OK auf einer Kachel **lange drücken** (oder die Menü-Taste). Auch im Player und in der Suche gibt es einen „Folgen“-Knopf. Die Liste wird auf dem Fernseher gespeichert, ein Twitch-Login ist nicht nötig.
- **Player im Vollbild** mit Chat-Ansichten: *Nur Video · Chat rechts · Chat über Video · Chat klein*, dazu drei Chat-Grössen. Twitch-Emotes werden angezeigt.
- **Werbeblocker:** erkennt Twitch-Werbung im Stream. Während der Pause läuft ein werbefreier Ersatz-Stream, notfalls wird die Werbung verdeckt und stummgeschaltet.

## Bedienung im Player

| Taste | Funktion |
|---|---|
| ◀ / ▶ | Chat-Ansicht wechseln |
| OK, ▲, ▼ | Steuerleiste: Ansicht, Chat-Grösse, Qualität, Werbeblocker, Folgen |
| Zurück | Steuerleiste schliessen, dann zurück zur Startseite |

## Bauen

Jede Änderung auf `main` startet unter **Actions** automatisch den Bau (etwa 5 Minuten).
Die fertige APK liegt danach unter **Releases**. Fester Link für den Fernseher:

`https://github.com/erlinxrmj/Couch/releases/latest/download/Couch.apk`

Schlägt der Bau fehl, steht das Fehlerprotokoll im Branch `build-log`.

## Auf dem Fernseher installieren

1. Im Play Store des TVs die App **„Downloader“** (von AFTVnews) installieren.
2. In den TV-Einstellungen unter *Geräteeinstellungen → Sicherheit & Einschränkungen → Unbekannte Quellen* dem **Downloader** erlauben, Apps zu installieren.
3. Im Downloader den Link von oben eingeben, und die APK wird installiert.
4. **Couch** erscheint mit eigenem Banner auf dem Startbildschirm.

## Technik

- Java, AndroidX Media3 (ExoPlayer/HLS), OkHttp, Glide, Schrift Inter
- Twitch-Daten über die öffentliche GraphQL-Schnittstelle ohne Login; Chat anonym über IRC-WebSocket
- Abruf von Stream-Token und HLS-Wiedergabe nach dem Vorbild von [SmartTwitchTV](https://github.com/fgl27/smarttwitchtv)
- Werbeerkennung: `AdBlock.java` · Ersatz-Stream-Suche: `TwitchApi.backupPlayback()`

Hinweis: Twitch ändert seine Schnittstellen und die Werbeauslieferung immer wieder. Wenn etwas plötzlich nicht mehr geht, liegt es meist daran, und es lässt sich anpassen.
