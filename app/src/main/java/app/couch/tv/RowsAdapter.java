package app.couch.tv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Die Reihen der Startseite. */
public class RowsAdapter extends RecyclerView.Adapter<RowsAdapter.VH> {
    public static class Row {
        public final String title;
        public final List<Stream> items;
        public final boolean followed;

        public Row(String title, List<Stream> items, boolean followed) {
            this.title = title;
            this.items = items;
            this.followed = followed;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final CardAdapter.Actions actions;
    private final RecyclerView.RecycledViewPool pool = new RecyclerView.RecycledViewPool();

    public RowsAdapter(CardAdapter.Actions actions) {
        this.actions = actions;
        setHasStableIds(true);
        pool.setMaxRecycledViews(0, 40);
    }

    public void setRows(List<Row> list) {
        boolean sameShape = list.size() == rows.size();
        if (sameShape) {
            for (int i = 0; i < list.size(); i++) {
                if (!list.get(i).title.equals(rows.get(i).title)) {
                    sameShape = false;
                    break;
                }
            }
        }
        rows.clear();
        rows.addAll(list);
        if (sameShape) notifyItemRangeChanged(0, rows.size());
        else notifyDataSetChanged();
    }

    public int count() {
        return rows.size();
    }

    @Override
    public long getItemId(int position) {
        return rows.get(position).title.hashCode();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false);
        VH h = new VH(v, new CardAdapter(actions));
        h.list.setRecycledViewPool(pool);
        return h;
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Row r = rows.get(position);
        h.title.setText(r.title);
        h.dot.setVisibility(r.followed ? View.VISIBLE : View.GONE);
        h.count.setText(r.followed ? r.items.size() + " live" : "");
        h.adapter.setItems(r.items);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, count;
        final View dot;
        final RecyclerView list;
        final CardAdapter adapter;

        VH(View v, CardAdapter adapter) {
            super(v);
            title = v.findViewById(R.id.rowTitle);
            count = v.findViewById(R.id.rowCount);
            dot = v.findViewById(R.id.rowDot);
            list = v.findViewById(R.id.rowList);
            this.adapter = adapter;
            list.setLayoutManager(new LinearLayoutManager(v.getContext(), LinearLayoutManager.HORIZONTAL, false));
            list.setItemAnimator(null);
            list.setAdapter(adapter);
        }
    }
}
