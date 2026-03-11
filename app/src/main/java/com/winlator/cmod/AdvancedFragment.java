package com.winlator.cmod;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.core.ArrayUtils;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.contentdialog.ContentDialog;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;

public class AdvancedFragment extends Fragment {
    private SharedPreferences preferences;
    private CheckBox cbEnableWineDebug;
    private CheckBox cbEnableBox64Logs;
    private CheckBox cbEnableFexcoreLogs;
    private ArrayList<String> wineDebugChannels;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.advanced_fragment, container, false);
        Context context = getContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        cbEnableWineDebug = view.findViewById(R.id.CBEnableWineDebug);
        cbEnableWineDebug.setChecked(preferences.getBoolean("enable_wine_debug", false));
        cbEnableWineDebug.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("enable_wine_debug", isChecked).apply();
        });

        wineDebugChannels = new ArrayList<>(Arrays.asList(preferences.getString("wine_debug_channels", SettingsConfig.DEFAULT_WINE_DEBUG_CHANNELS).split(",")));
        loadWineDebugChannels(view, wineDebugChannels);

        cbEnableBox64Logs = view.findViewById(R.id.CBEnableBox64Logs);
        cbEnableBox64Logs.setChecked(preferences.getBoolean("enable_box64_logs", false));
        cbEnableBox64Logs.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("enable_box64_logs", isChecked).apply();
        });

        cbEnableFexcoreLogs = view.findViewById(R.id.CBEnableFexcoreLogs);
        cbEnableFexcoreLogs.setChecked(preferences.getBoolean("enable_fexcore_logs", false));
        cbEnableFexcoreLogs.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("enable_fexcore_logs", isChecked).apply();
        });

        return view;
    }

    private void loadWineDebugChannels(final View view, final ArrayList<String> debugChannels) {
        final Context context = getContext();
        LinearLayout container = view.findViewById(R.id.LLWineDebugChannels);
        container.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(context);
        View itemView = inflater.inflate(R.layout.wine_debug_channel_list_item, container, false);
        itemView.findViewById(R.id.TextView).setVisibility(View.GONE);
        itemView.findViewById(R.id.BTRemove).setVisibility(View.GONE);

        View addButton = itemView.findViewById(R.id.BTAdd);
        addButton.setVisibility(View.VISIBLE);
        addButton.setOnClickListener((v) -> {
            JSONArray jsonArray = null;
            try {
                jsonArray = new JSONArray(FileUtils.readString(context, "wine_debug_channels.json"));
            }
            catch (JSONException e) {}

            final String[] items = ArrayUtils.toStringArray(jsonArray);
            ContentDialog.showMultipleChoiceList(context, R.string.wine_debug_channel, items, (selectedPositions) -> {
                for (int selectedPosition : selectedPositions) if (!debugChannels.contains(items[selectedPosition])) debugChannels.add(items[selectedPosition]);
                preferences.edit().putString("wine_debug_channels", String.join(",", debugChannels)).apply();
                loadWineDebugChannels(view, debugChannels);
            });
        });

        View resetButton = itemView.findViewById(R.id.BTReset);
        resetButton.setVisibility(View.VISIBLE);
        resetButton.setOnClickListener((v) -> {
            debugChannels.clear();
            debugChannels.addAll(Arrays.asList(SettingsConfig.DEFAULT_WINE_DEBUG_CHANNELS.split(",")));
            preferences.edit().putString("wine_debug_channels", String.join(",", debugChannels)).apply();
            loadWineDebugChannels(view, debugChannels);
        });
        container.addView(itemView);

        for (int i = 0; i < debugChannels.size(); i++) {
            itemView = inflater.inflate(R.layout.wine_debug_channel_list_item, container, false);
            TextView textView = itemView.findViewById(R.id.TextView);
            textView.setText(debugChannels.get(i));
            final int index = i;
            itemView.findViewById(R.id.BTRemove).setOnClickListener((v) -> {
                debugChannels.remove(index);
                preferences.edit().putString("wine_debug_channels", String.join(",", debugChannels)).apply();
                loadWineDebugChannels(view, debugChannels);
            });
            container.addView(itemView);
        }
    }
}
