package app.couch.tv;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Stream-Kacheln einer Reihe. */
public class CardAdapter extends RecyclerView.Adapter<CardAdapter.VH> {
    public interface Actions {
        void open(Stream s);

        void toggleFollow(Stream s);

        boolean isFollowed(String login);
    }

    private final List<Stream> items = new ArrayList<>();
    private final Actions actions;

    public CardAdapter(Actions actions) {
        this.actions = actions;
        setHasStableIds(true);
    }

    public void setItems(List<Stream> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).login.hashCode();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final Stream s = items.get(position);
        h.name.setText(s.displayName);
        h.title.setText(s.title);
        h.game.setText(s.game);
        h.game.setVisibility(s.game.isEmpty() ? View.GONE : View.VISIBLE);
        h.viewers.setText("● " + Util.viewers(s.viewers));
        h.followed.setVisibility(actions.isFollowed(s.login) ? View.VISIBLE : View.GONE);
        Util.thumb(h.thumb, s.preview);
        Util.avatar(h.avatar, s.avatar);

        h.itemView.setOnClickListener(v -> actions.open(s));
        h.itemView.setOnLongClickListener(v -> {
            actions.toggleFollow(s);
            return true;
        });
        h.itemView.setOnKeyListener((v, keyCode, e) -> {
            if (keyCode == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_DOWN) {
                actions.toggleFollow(s);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView thumb, avatar;
        final TextView name, title, game, viewers, followed;

        VH(View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            avatar = v.findViewById(R.id.avatar);
            name = v.findViewById(R.id.name);
            title = v.findViewById(R.id.title);
            game = v.findViewById(R.id.game);
            viewers = v.findViewById(R.id.viewers);
            followed = v.findViewById(R.id.followedBadge);
            v.setOnFocusChangeListener((view, focus) -> {
                float sc = focus ? 1.07f : 1f;
                view.animate().scaleX(sc).scaleY(sc).translationZ(focus ? 24f : 0f).setDuration(160).start();
            });
        }
    }
}
