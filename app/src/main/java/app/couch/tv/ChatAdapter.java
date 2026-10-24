package app.couch.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Zeigt Chatnachrichten inkl. Twitch-Emotes. */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {
    private static final int MAX = 150;
    private final Context app;
    private final List<ChatClient.Message> items = new ArrayList<>();
    private final Map<String, BitmapDrawable> emotes = new HashMap<>();
    private final Set<String> loading = new HashSet<>();
    private final Map<String, List<ChatClient.Message>> waiting = new HashMap<>();
    private float textSp = 15f;
    private final int accent;

    public ChatAdapter(Context c) {
        app = c.getApplicationContext();
        accent = app.getResources().getColor(R.color.accent);
    }

    public void setTextSize(float sp) {
        if (sp == textSp) return;
        textSp = sp;
        for (BitmapDrawable d : emotes.values()) size(d);
        for (ChatClient.Message m : items) m.rendered = render(m);
        notifyDataSetChanged();
    }

    public void add(ChatClient.Message m) {
        m.rendered = render(m);
        items.add(m);
        notifyItemInserted(items.size() - 1);
        if (items.size() > MAX) {
            items.remove(0);
            notifyItemRemoved(0);
        }
    }

    public void system(String text) {
        ChatClient.Message m = new ChatClient.Message();
        m.system = true;
        m.text = text;
        add(m);
    }

    private CharSequence render(ChatClient.Message m) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (m.system) {
            sb.append(m.text);
            sb.setSpan(new ForegroundColorSpan(accent), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new StyleSpan(Typeface.ITALIC), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return sb;
        }
        sb.append(m.name);
        sb.setSpan(new ForegroundColorSpan(m.color), 0, m.name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), 0, m.name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(": ");
        int off = sb.length();
        sb.append(m.text);
        for (int[] r : m.emoteRanges) {
            String id = m.emoteIds.get(r[2]);
            BitmapDrawable d = emotes.get(id);
            if (d != null) {
                sb.setSpan(new ImageSpan(d, ImageSpan.ALIGN_BOTTOM), off + r[0], off + r[1],
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                request(id, m);
            }
        }
        return sb;
    }

    private void request(final String id, ChatClient.Message m) {
        List<ChatClient.Message> w = waiting.get(id);
        if (w == null) {
            w = new ArrayList<>();
            waiting.put(id, w);
        }
        if (!w.contains(m)) w.add(m);
        if (loading.contains(id)) return;
        loading.add(id);
        String url = "https://static-cdn.jtvnw.net/emoticons/v2/" + id + "/default/dark/2.0";
        Glide.with(app).asBitmap().load(url).into(new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap b, @Nullable Transition<? super Bitmap> t) {
                BitmapDrawable d = new BitmapDrawable(app.getResources(), b);
                size(d);
                emotes.put(id, d);
                List<ChatClient.Message> list = waiting.remove(id);
                if (list == null) return;
                for (ChatClient.Message msg : list) {
                    int idx = items.indexOf(msg);
                    if (idx >= 0) {
                        msg.rendered = render(msg);
                        notifyItemChanged(idx);
                    }
                }
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
            }
        });
    }

    private void size(BitmapDrawable d) {
        int h = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSp * 1.6f,
                app.getResources().getDisplayMetrics()));
        Bitmap b = d.getBitmap();
        int w = b.getHeight() > 0 ? b.getWidth() * h / b.getHeight() : h;
        d.setBounds(0, 0, w, h);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH((TextView) LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        h.text.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
        h.text.setText(items.get(position).rendered);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView text;

        VH(TextView v) {
            super(v);
            text = v;
        }
    }
}
