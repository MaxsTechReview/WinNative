package com.winlator.cmod;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.winlator.cmod.steam.SteamLoginActivity;
import com.winlator.cmod.steam.service.SteamService;

/**
 * Fragment showing store sign-in / sign-out for Steam, Epic, GOG, Amazon.
 */
public class StoresFragment extends Fragment {
    
    private LinearLayout layout;


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getActivity() != null && ((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.stores);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        this.layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(20);
        layout.setPadding(pad, pad, pad, pad);
        return layout;
    }

    private void updateStores() {
        if (layout == null) return;
        layout.removeAllViews();

        addStoreRow(layout, "Steam",
                SteamService.Companion.isLoggedIn(),
                v -> {
                    if (SteamService.Companion.isLoggedIn()) {
                        SteamService.Companion.logOut();
                        updateStores();
                    } else {
                        startActivity(new Intent(getContext(), SteamLoginActivity.class));
                    }
                });

        boolean isEpicLoggedIn = com.winlator.cmod.epic.service.EpicAuthManager.isLoggedIn(getContext());
        addStoreRow(layout, "Epic Games", isEpicLoggedIn, v -> {
            if (isEpicLoggedIn) {
                com.winlator.cmod.epic.service.EpicAuthManager.logoutSync(getContext());
                updateStores();
            } else {
                startActivity(new Intent(getContext(), com.winlator.cmod.epic.ui.auth.EpicOAuthActivity.class));
            }
        });

        addStoreRow(layout, "GOG", false, v ->
                android.widget.Toast.makeText(getContext(), "GOG integration coming soon", android.widget.Toast.LENGTH_SHORT).show());

        addStoreRow(layout, "Amazon Games", false, v ->
                android.widget.Toast.makeText(getContext(), "Amazon Games integration coming soon", android.widget.Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStores();
    }

    private void refreshView() {
        if (getView() != null) {
            getParentFragmentManager().beginTransaction()
                    .detach(this)
                    .attach(this)
                    .commit();
        }
    }

    private void addStoreRow(LinearLayout parent, String storeName, boolean isLoggedIn, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dpToPx(4), dpToPx(12), dpToPx(4), dpToPx(12));
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Store name
        TextView nameView = new TextView(getContext());
        nameView.setText(storeName);
        nameView.setTextSize(15);
        nameView.setTextColor(0xFFE6EDF3);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameView.setLayoutParams(nameParams);
        row.addView(nameView);

        // Status dot + text
        TextView statusView = new TextView(getContext());
        statusView.setText(isLoggedIn ? "● Signed In" : "○ Not Signed In");
        statusView.setTextSize(12);
        statusView.setTextColor(isLoggedIn ? 0xFF57CBDE : 0xFF8B949E);
        statusView.setPadding(0, 0, dpToPx(12), 0);
        row.addView(statusView);

        // Action button
        TextView actionBtn = new TextView(getContext());
        actionBtn.setText(isLoggedIn ? "SIGN OUT" : "SIGN IN");
        actionBtn.setTextSize(12);
        actionBtn.setTextColor(isLoggedIn ? 0xFFFF6B6B : 0xFF57CBDE);
        actionBtn.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        actionBtn.setBackgroundColor(isLoggedIn ? 0x20FF6B6B : 0x2057CBDE);
        actionBtn.setOnClickListener(onClick);
        row.addView(actionBtn);

        parent.addView(row);

        // Thin divider
        View divider = new View(getContext());
        divider.setBackgroundColor(0xFF30363D);
        parent.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
