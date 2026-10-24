package app.couch.tv;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private EditText input;
    private TextView label;
    private RecyclerView results;
    private final ResultAdapter adapter = new ResultAdapter();
    private int requestId;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_search);
        input = findViewById(R.id.input);
        label = findViewById(R.id.resultLabel);
        results = findViewById(R.id.results);
        results.setLayoutManager(new LinearLayoutManager(this));
        results.setItemAnimator(null);
        results.setAdapter(adapter);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int c, int d) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                h.removeCallbacks(run);
                h.postDelayed(run, 450);
            }
        });
        input.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                h.removeCallbacks(run);
                run.run();
                hideKeyboard();
                results.postDelayed(() -> {
                    if (results.getChildCount() > 0) results.getChildAt(0).findViewById(R.id.main).requestFocus();
                }, 600);
                return true;
            }
            return false;
        });
        input.requestFocus();
        showPopular();
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private final Runnable run = () -> {
        final String q = input.getText().toString().trim();
        if (q.isEmpty()) {
            showPopular();
            return;
        }
        final int id = ++requestId;
        label.setText("ERGEBNISSE FÜR „" + q.toUpperCase() + "“");
        TwitchApi.async(() -> TwitchApi.search(q), list -> {
            if (id != requestId) return;
            if (list == null || list.isEmpty()) {
                label.setText("KEINE TREFFER FÜR „" + q.toUpperCase() + "“");
                adapter.set(new ArrayList<>());
                return;
            }
            adapter.set(list);
        });
    };

    private void showPopular() {
        final int id = ++requestId;
        label.setText("BELIEBT GERADE");
        TwitchApi.async(() -> TwitchApi.topStreams(20), list -> {
            if (id != requestId || list == null) return;
            List<Channel> ch = new ArrayList<>();
            for (Stream s : list) ch.add(Channel.of(s));
            adapter.set(ch);
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        // Vom Eingabefeld mit Pfeil-runter direkt in die Ergebnisse
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN
                && input.hasFocus() && results.getChildCount() > 0) {
            hideKeyboard();
            results.getChildAt(0).findViewById(R.id.main).requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.VH> {
        private final List<Channel> items = new ArrayList<>();

        void set(List<Channel> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_result, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            final Channel c = items.get(position);
            h.name.setText(c.displayName);
            if (c.live != null) {
                String s = "● Live · " + Util.viewersLong(c.live.viewers);
                if (!c.live.game.isEmpty()) s += " · " + c.live.game;
                h.status.setText(s);
                h.status.setTextColor(getResources().getColor(R.color.live));
            } else {
                h.status.setText("Offline");
                h.status.setTextColor(getResources().getColor(R.color.text_dim));
            }
            h.ring.setVisibility(c.live != null ? View.VISIBLE : View.GONE);
            Util.avatar(h.avatar, c.avatar);
            bindFollow(h, c);
            h.main.setOnClickListener(v -> {
                if (c.live != null) PlayerActivity.start(SearchActivity.this, c.live);
                else Toast.makeText(SearchActivity.this, c.displayName + " ist gerade offline", Toast.LENGTH_SHORT).show();
            });
            h.follow.setOnClickListener(v -> {
                boolean now = Follows.toggle(SearchActivity.this, c);
                Toast.makeText(SearchActivity.this,
                        now ? "♥ Du folgst jetzt " + c.displayName : c.displayName + " entfolgt",
                        Toast.LENGTH_SHORT).show();
                bindFollow(h, c);
            });
        }

        private void bindFollow(VH h, Channel c) {
            boolean f = Follows.is(SearchActivity.this, c.login);
            h.follow.setText(f ? "♥ Gefolgt" : "♡ Folgen");
            h.follow.setSelected(f);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final View main, ring;
            final ImageView avatar;
            final TextView name, status, follow;

            VH(View v) {
                super(v);
                main = v.findViewById(R.id.main);
                ring = v.findViewById(R.id.ring);
                avatar = v.findViewById(R.id.avatar);
                name = v.findViewById(R.id.name);
                status = v.findViewById(R.id.status);
                follow = v.findViewById(R.id.follow);
            }
        }
    }
}
