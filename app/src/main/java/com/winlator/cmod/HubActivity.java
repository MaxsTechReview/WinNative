package com.winlator.cmod;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class HubActivity extends AppCompatActivity {

    private TextView tabLibrary;
    private TextView tabStore;
    private TextView tabDownloads;
    private View viewLibrary;
    private View viewStore;
    private View viewDownloads;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Force landscape and fullscreen is handled in AndroidManifest and AppThemeFullscreen
        setContentView(R.layout.activity_hub);

        tabLibrary = findViewById(R.id.tab_library);
        tabStore = findViewById(R.id.tab_store);
        tabDownloads = findViewById(R.id.tab_downloads);
        
        viewLibrary = findViewById(R.id.view_library);
        viewStore = findViewById(R.id.view_store);
        viewDownloads = findViewById(R.id.view_downloads);

        tabLibrary.setOnClickListener(v -> switchTab(0));
        tabStore.setOnClickListener(v -> switchTab(1));
        tabDownloads.setOnClickListener(v -> switchTab(2));
        
        TextView emptyLibrary = findViewById(R.id.tv_empty_library);
        emptyLibrary.setOnClickListener(v -> {
            startActivity(new Intent(this, UnifiedActivity.class));
        });

        setupLibraryRecyclerView();
        setupStoreRecyclerView();
        
        com.winlator.cmod.ui.WaveView waveView = findViewById(R.id.wave_view);
        if (waveView != null) {
            waveView.setSpeed(45.2f); // Mock speed
        }
    }

    private void switchTab(int index) {
        tabLibrary.setTextColor(Color.parseColor("#888888"));
        tabStore.setTextColor(Color.parseColor("#888888"));
        tabDownloads.setTextColor(Color.parseColor("#888888"));
        
        viewLibrary.setVisibility(View.GONE);
        viewStore.setVisibility(View.GONE);
        viewDownloads.setVisibility(View.GONE);

        if (index == 0) {
            tabLibrary.setTextColor(Color.parseColor("#FFFFFF"));
            viewLibrary.setVisibility(View.VISIBLE);
        } else if (index == 1) {
            tabStore.setTextColor(Color.parseColor("#FFFFFF"));
            viewStore.setVisibility(View.VISIBLE);
        } else if (index == 2) {
            tabDownloads.setTextColor(Color.parseColor("#FFFFFF"));
            viewDownloads.setVisibility(View.VISIBLE);
        }
    }

    private void setupLibraryRecyclerView() {
        RecyclerView rv = findViewById(R.id.rv_library);
        rv.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        List<String> games = new ArrayList<>();
        games.add("Winlator Backend");
        games.add("Cyberpunk 2077");
        games.add("Elden Ring");
        games.add("The Witcher 3");
        games.add("Hades");
        games.add("Sekiro");
        rv.setAdapter(new GameAdapter(games, true));
    }

    private void setupStoreRecyclerView() {
        RecyclerView rv = findViewById(R.id.rv_store);
        rv.setLayoutManager(new GridLayoutManager(this, 4));
        List<String> games = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            games.add("Store Game " + i);
        }
        rv.setAdapter(new GameAdapter(games, false));
    }

    private class GameAdapter extends RecyclerView.Adapter<GameAdapter.ViewHolder> {
        private List<String> games;
        private boolean isHorizontal;

        public GameAdapter(List<String> games, boolean isHorizontal) {
            this.games = games;
            this.isHorizontal = isHorizontal;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            int width = isHorizontal ? 280 : ViewGroup.LayoutParams.MATCH_PARENT;
            int height = isHorizontal ? ViewGroup.LayoutParams.MATCH_PARENT : 300;
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(width, height);
            lp.setMargins(16, 16, 16, 16);
            tv.setLayoutParams(lp);
            tv.setBackgroundColor(Color.parseColor("#3D3D3D"));
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(18);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setFocusable(true);
            tv.setClickable(true);
            
            // Controller focus highlight
            tv.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.setBackgroundColor(Color.parseColor("#5F5F5F"));
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start();
                } else {
                    v.setBackgroundColor(Color.parseColor("#3D3D3D"));
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }
            });
            
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ((TextView) holder.itemView).setText(games.get(position));
            holder.itemView.setOnClickListener(v -> {
                if (games.get(position).equals("Winlator Backend")) {
                    startActivity(new Intent(HubActivity.this, UnifiedActivity.class));
                }
            });
        }

        @Override
        public int getItemCount() {
            return games.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}
