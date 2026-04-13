package com.winlator.cmod;

import static androidx.core.content.ContextCompat.getSystemService;
import static com.winlator.cmod.ShortcutsScreenKt.setupShortcutsComposeView;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.ShortcutSettingsComposeDialog;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.ui.dialog.WinNativeComposeDialogs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShortcutsFragment extends Fragment {
    private ComposeView composeView;
    private ContainerManager manager;
    private List<Shortcut> shortcuts = Collections.emptyList();

    private final ShortcutsActionListener shortcutsActionListener = new ShortcutsActionListener() {
        @Override
        public void onRunShortcut(Shortcut shortcut) {
            runFromShortcut(shortcut);
        }

        @Override
        public void onEditShortcut(Shortcut shortcut) {
            try {
                new ShortcutSettingsComposeDialog(ShortcutsFragment.this, shortcut).show();
            } catch (Throwable e) {
                Log.e("ShortcutsFragment", "Error opening shortcut settings", e);
                Toast.makeText(getContext(), R.string.shortcuts_list_error_opening_settings, Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onAddToHomeScreen(Shortcut shortcut) {
            boolean added = addShortcutToScreen(shortcut);
            Toast.makeText(
                    requireContext(),
                    added ? R.string.shortcuts_list_added : R.string.shortcuts_list_failed_add,
                    Toast.LENGTH_SHORT
            ).show();
        }

        @Override
        public void onRemoveShortcut(Shortcut shortcut) {
            removeShortcut(shortcut);
        }

        @Override
        public void onExportShortcut(Shortcut shortcut) {
            exportShortcut(shortcut);
        }

        @Override
        public void onCloneShortcut(Shortcut shortcut) {
            cloneShortcut(shortcut);
        }

        @Override
        public void onShowProperties(Shortcut shortcut) {
            showShortcutProperties(shortcut);
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        composeView = new ComposeView(inflater.getContext());
        renderShortcuts();
        return composeView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        manager = new ContainerManager(requireContext());
        loadShortcutsList();
        if (getActivity() != null && ((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.common_ui_shortcuts);
        }
    }

    private void renderShortcuts() {
        if (composeView == null) return;
        setupShortcutsComposeView(composeView, shortcuts, shortcutsActionListener);
    }

    public void loadShortcutsList() {
        if (manager == null && getContext() != null) {
            manager = new ContainerManager(getContext());
        }
        if (manager == null) return;

        ArrayList<Shortcut> loadedShortcuts = manager.loadShortcuts();
        loadedShortcuts.removeIf(shortcut -> shortcut == null || shortcut.file == null || shortcut.file.getName().isEmpty());
        shortcuts = loadedShortcuts;
        renderShortcuts();
    }

    private void removeShortcut(Shortcut shortcut) {
        Context context = getContext();
        if (context == null) return;

        ContentDialog.confirm(context, R.string.shortcuts_list_confirm_remove, () -> {
            boolean fileDeleted = shortcut.file.delete();
            File lnkFile = new File(shortcut.file.getPath().substring(0, shortcut.file.getPath().lastIndexOf(".")) + ".lnk");
            if (lnkFile.exists()) {
                lnkFile.delete();
            }

            if (fileDeleted) {
                disableShortcutOnScreen(requireContext(), shortcut);
                loadShortcutsList();
                Toast.makeText(context, R.string.shortcuts_list_removed, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, R.string.shortcuts_list_remove_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void cloneShortcut(Shortcut shortcut) {
        Context context = getContext();
        if (context == null) return;

        ContainerManager containerManager = new ContainerManager(context);
        ArrayList<Container> containers = containerManager.getContainers();
        showContainerSelectionDialog(containers, selectedContainer -> {
            if (shortcut.cloneToContainer(selectedContainer)) {
                Toast.makeText(context, R.string.shortcuts_list_cloned, Toast.LENGTH_SHORT).show();
                loadShortcutsList();
            } else {
                Toast.makeText(context, R.string.shortcuts_list_clone_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private interface OnContainerSelectedListener {
        void onContainerSelected(Container container);
    }

    private void showContainerSelectionDialog(ArrayList<Container> containers, OnContainerSelectedListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.shortcuts_list_select_a_container);

        String[] containerNames = new String[containers.size()];
        for (int i = 0; i < containers.size(); i++) {
            containerNames[i] = containers.get(i).getName();
        }

        builder.setItems(containerNames, (dialog, which) -> listener.onContainerSelected(containers.get(which)));
        builder.show();
    }

    private void runFromShortcut(Shortcut shortcut) {
        Activity activity = getActivity();
        if (activity == null) return;

        Intent intent = new Intent(activity, XServerDisplayActivity.class);
        intent.putExtra("container_id", shortcut.container.id);
        intent.putExtra("shortcut_path", shortcut.file.getPath());
        intent.putExtra("shortcut_name", shortcut.name);
        intent.putExtra("disableXinput", shortcut.getExtra("disableXinput", "0"));
        activity.startActivity(intent);
    }

    private void exportShortcut(Shortcut shortcut) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        String uriString = sharedPreferences.getString("shortcuts_export_path_uri", null);

        File shortcutsDir;
        if (uriString != null) {
            Uri folderUri = Uri.parse(uriString);
            DocumentFile pickedDir = DocumentFile.fromTreeUri(getContext(), folderUri);
            if (pickedDir == null || !pickedDir.canWrite()) {
                Toast.makeText(getContext(), R.string.common_ui_cannot_write_folder, Toast.LENGTH_SHORT).show();
                return;
            }
            shortcutsDir = new File(FileUtils.getFilePathFromUri(getContext(), folderUri));
        } else {
            shortcutsDir = new File(SettingsConfig.DEFAULT_SHORTCUT_EXPORT_PATH);
        }

        if (!shortcutsDir.exists() && !shortcutsDir.mkdirs()) {
            Toast.makeText(getContext(), R.string.common_ui_failed_create_directory, Toast.LENGTH_SHORT).show();
            return;
        }

        File exportFile = new File(shortcutsDir, shortcut.file.getName());
        boolean fileExists = exportFile.exists();
        boolean containerIdFound = false;

        try {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(shortcut.file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("container_id:")) {
                        lines.add("container_id:" + shortcut.container.id);
                        containerIdFound = true;
                    } else {
                        lines.add(line);
                    }
                }
            }

            if (!containerIdFound) {
                lines.add("container_id:" + shortcut.container.id);
            }

            try (FileWriter writer = new FileWriter(exportFile, false)) {
                for (String line : lines) {
                    writer.write(line + "\n");
                }
                writer.flush();
            }

            Log.d("ShortcutsFragment", "Shortcut exported successfully to " + exportFile.getPath());
            String message = fileExists
                    ? getString(R.string.shortcuts_properties_updated_at, exportFile.getPath())
                    : getString(R.string.shortcuts_list_exported_to, exportFile.getPath());
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Log.e("ShortcutsFragment", "Failed to export shortcut", e);
            Toast.makeText(getContext(), R.string.shortcuts_list_failed_export, Toast.LENGTH_LONG).show();
        }
    }

    private void showShortcutProperties(Shortcut shortcut) {
        Context context = getContext();
        if (context == null) return;

        SharedPreferences playtimePrefs = context.getSharedPreferences("playtime_stats", Context.MODE_PRIVATE);
        String playtimeKey = shortcut.name + "_playtime";
        String playCountKey = shortcut.name + "_play_count";

        long totalPlaytime = playtimePrefs.getLong(playtimeKey, 0);
        int playCount = playtimePrefs.getInt(playCountKey, 0);

        long seconds = (totalPlaytime / 1000) % 60;
        long minutes = (totalPlaytime / (1000 * 60)) % 60;
        long hours = (totalPlaytime / (1000 * 60 * 60)) % 24;
        long days = (totalPlaytime / (1000 * 60 * 60 * 24));
        String playtimeFormatted = String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds);

        String playCountText = getString(R.string.library_games_times_played_label) + playCount;
        String playtimeText = getString(R.string.library_games_playtime_label) + playtimeFormatted;

        if (WinNativeComposeDialogs.showShortcutProperties(
                context,
                playCountText,
                playtimeText,
                () -> {
                    playtimePrefs.edit().remove(playtimeKey).remove(playCountKey).apply();
                    Toast.makeText(getContext(), R.string.shortcuts_properties_properties_reset, Toast.LENGTH_SHORT).show();
                })) {
            return;
        }
    }

    private ShortcutInfo buildScreenShortCut(String shortLabel, String longLabel, int containerId, String shortcutPath, Icon icon, String uuid) {
        Intent intent = new Intent(getActivity(), XServerDisplayActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra("container_id", containerId);
        intent.putExtra("shortcut_path", shortcutPath);

        return new ShortcutInfo.Builder(getActivity(), uuid)
                .setShortLabel(shortLabel)
                .setLongLabel(longLabel)
                .setIcon(icon)
                .setIntent(intent)
                .build();
    }

    public boolean addShortcutToScreen(Shortcut shortcut) {
        if (shortcut == null) return false;
        if (shortcut.getExtra("uuid").equals("")) {
            shortcut.genUUID();
        }
        ShortcutManager shortcutManager = getSystemService(requireContext(), ShortcutManager.class);
        if (shortcutManager == null || !shortcutManager.isRequestPinShortcutSupported()) return false;

        Icon shortcutIcon = shortcut.icon != null
                ? Icon.createWithBitmap(shortcut.icon)
                : Icon.createWithResource(requireContext(), R.drawable.icon_shortcut);

        return shortcutManager.requestPinShortcut(
                buildScreenShortCut(
                        shortcut.name,
                        shortcut.name,
                        shortcut.container.id,
                        shortcut.file.getPath(),
                        shortcutIcon,
                        shortcut.getExtra("uuid")),
                null
        );
    }

    public static void disableShortcutOnScreen(Context context, Shortcut shortcut) {
        ShortcutManager shortcutManager = getSystemService(context, ShortcutManager.class);
        try {
            shortcutManager.disableShortcuts(
                    Collections.singletonList(shortcut.getExtra("uuid")),
                    context.getString(R.string.shortcuts_list_not_available)
            );
        } catch (Exception ignored) {
        }
    }

    public void updateShortcutOnScreen(String shortLabel, String longLabel, int containerId, String shortcutPath, Icon icon, String uuid) {
        ShortcutManager shortcutManager = getSystemService(requireContext(), ShortcutManager.class);
        try {
            for (ShortcutInfo shortcutInfo : shortcutManager.getPinnedShortcuts()) {
                if (shortcutInfo.getId().equals(uuid)) {
                    shortcutManager.updateShortcuts(Collections.singletonList(
                            buildScreenShortCut(shortLabel, longLabel, containerId, shortcutPath, icon, uuid)));
                    break;
                }
            }
        } catch (Exception ignored) {
        }
    }
}
