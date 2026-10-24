package app.couch.tv;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Werbeerkennung für Twitch-Streams.
 *
 * Twitch schneidet Werbung direkt in die Videoliste (HLS) ein. Werbe-Segmente
 * sind dort markiert (Titel ungleich "live", bzw. "stitched-ad"-Markierungen).
 * Diese Klasse liest jede Videoliste mit, die der Player lädt, und meldet,
 * sobald gerade Werbung läuft.
 */
@OptIn(markerClass = UnstableApi.class)
public final class AdBlock {
    private AdBlock() {}

    public interface Listener {
        void onPlaylist(String tag, boolean ads);
    }

    /** true, wenn in den neuesten Segmenten der Videoliste Werbung steckt. */
    public static boolean hasAds(String playlist) {
        if (playlist == null) return false;
        List<Boolean> segs = new ArrayList<>();
        boolean anyTitle = false;
        for (String raw : playlist.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("#EXTINF:")) {
                int c = line.indexOf(',');
                String t = c >= 0 ? line.substring(c + 1).trim() : "";
                if (!t.isEmpty()) anyTitle = true;
                segs.add(!t.isEmpty() && !t.equalsIgnoreCase("live"));
            }
        }
        if (anyTitle) {
            int n = segs.size();
            for (int i = Math.max(0, n - 4); i < n; i++) if (segs.get(i)) return true;
            return false;
        }
        return playlist.contains("stitched-ad") || playlist.contains("X-TV-TWITCH-AD");
    }

    /** Erzeugt Datenquellen, die Videolisten auf Werbung prüfen. */
    public static final class Factory implements DataSource.Factory {
        private final DataSource.Factory upstream;
        private final String tag;
        private final Listener listener;

        public Factory(DataSource.Factory upstream, String tag, Listener listener) {
            this.upstream = upstream;
            this.tag = tag;
            this.listener = listener;
        }

        @NonNull
        @Override
        public DataSource createDataSource() {
            return new Source(upstream.createDataSource(), tag, listener);
        }
    }

    private static final class Source implements DataSource {
        private static final Handler MAIN = new Handler(Looper.getMainLooper());
        private final DataSource upstream;
        private final String tag;
        private final Listener listener;
        private byte[] buffer;
        private int pos;
        private Uri uri;

        Source(DataSource upstream, String tag, Listener listener) {
            this.upstream = upstream;
            this.tag = tag;
            this.listener = listener;
        }

        @Override
        public void addTransferListener(@NonNull TransferListener transferListener) {
            upstream.addTransferListener(transferListener);
        }

        @Override
        public long open(@NonNull DataSpec dataSpec) throws IOException {
            String path = dataSpec.uri.getPath();
            boolean playlist = path != null && path.endsWith(".m3u8");
            if (!playlist) {
                buffer = null;
                return upstream.open(dataSpec);
            }
            uri = dataSpec.uri;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                upstream.open(dataSpec);
                byte[] chunk = new byte[16 * 1024];
                int n;
                while ((n = upstream.read(chunk, 0, chunk.length)) != C.RESULT_END_OF_INPUT) {
                    out.write(chunk, 0, n);
                }
            } finally {
                upstream.close();
            }
            buffer = out.toByteArray();
            pos = 0;
            String text = new String(buffer, "UTF-8");
            if (text.contains("#EXTINF")) {
                final boolean ads = hasAds(text);
                MAIN.post(() -> listener.onPlaylist(tag, ads));
            }
            return buffer.length;
        }

        @Override
        public int read(@NonNull byte[] target, int offset, int length) throws IOException {
            if (buffer == null) return upstream.read(target, offset, length);
            if (length == 0) return 0;
            if (pos >= buffer.length) return C.RESULT_END_OF_INPUT;
            int n = Math.min(length, buffer.length - pos);
            System.arraycopy(buffer, pos, target, offset, n);
            pos += n;
            return n;
        }

        @Nullable
        @Override
        public Uri getUri() {
            return buffer != null ? uri : upstream.getUri();
        }

        @NonNull
        @Override
        public Map<String, List<String>> getResponseHeaders() {
            return upstream.getResponseHeaders();
        }

        @Override
        public void close() throws IOException {
            if (buffer != null) {
                buffer = null;
                return;
            }
            upstream.close();
        }
    }
}
