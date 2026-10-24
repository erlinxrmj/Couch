package app.couch.tv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Gefolgte Kanäle in der Seitenleiste. */
public class FollowAdapter extends RecyclerView.Adapter<FollowAdapter.VH> {
    public interface Actions {
        void open(Channel c);

        void unfollow(Channel c);
    }

    private final List<Channel> items = new ArrayList<>();
    private final Actions actions;
    private float labelAlpha = 0f;

    public FollowAdapter(Actions actions) {
        this.actions = actions;
        setHasStableIds(true);
    }

    public void setItems(List<Channel> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    /** Beschriftungen ein-/ausblenden (Seitenleiste auf-/zugeklappt). */
    public void setLabelAlpha(RecyclerView rv, float a) {
        labelAlpha = a;
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder vh = rv.getChildViewHolder(rv.getChildAt(i));
            if (vh instanceof VH) ((VH) vh).setLabelAlpha(a);
        }
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).login.hashCode();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_follow, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final Channel c = items.get(position);
        boolean live = c.live != null;
        h.name.setText(c.displayName);
        if (live) {
            h.status.setText(c.live.game.isEmpty() ? "Live" : c.live.game);
            h.viewers.setText("● " + Util.viewers(c.live.viewers));
        } else {
            h.status.setText("Offline");
            h.viewers.setText("");
        }
        h.ring.setVisibility(live ? View.VISIBLE : View.GONE);
        h.dot.setVisibility(live ? View.VISIBLE : View.GONE);
        h.avatar.setAlpha(live ? 1f : 0.4f);
        Util.avatar(h.avatar, c.avatar);
        h.setLabelAlpha(labelAlpha);
        h.itemView.setOnClickListener(v -> actions.open(c));
        h.itemView.setOnLongClickListener(v -> {
            actions.unfollow(c);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final View ring, dot, labels;
        final TextView name, status, viewers;

        VH(View v) {
            super(v);
            avatar = v.findViewById(R.id.avatar);
            ring = v.findViewById(R.id.ring);
            dot = v.findViewById(R.id.dot);
            labels = v.findViewById(R.id.labels);
            name = v.findViewById(R.id.name);
            status = v.findViewById(R.id.status);
            viewers = v.findViewById(R.id.viewers);
        }

        void setLabelAlpha(float a) {
            labels.setAlpha(a);
            viewers.setAlpha(a);
        }
    }
}
