package com.winlator.cmod;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.midi.MidiManager;
import com.winlator.cmod.xenvironment.ImageFsInstaller;

public class OtherSettingsFragment extends Fragment {
    private Callback<Uri> installSoundFontCallback;
    private SharedPreferences preferences;

    private CheckBox cbCursorLock;
    private CheckBox cbXinputToggle;
    private CheckBox cbEnableBigPictureMode;
    private CheckBox cbEnableCustomApiKey;
    private EditText etCustomApiKey;
    private CheckBox cbUseDRI3;
    private CheckBox cbUseXR;
    private CheckBox cbEnableFileProvider;
    private CheckBox cbOpenInBrowser;
    private CheckBox cbShareClipboard;
    private SeekBar sbCursorSpeed;

    private static final int REQUEST_CODE_WINLATOR_PATH = 1002;
    private static final int REQUEST_CODE_SHORTCUT_EXPORT_PATH = 1003;
    private static final int REQUEST_CODE_INSTALL_SOUNDFONT = 1001;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getActivity() != null && ((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.other);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.other_settings_fragment, container, false);
        final Context context = getContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Initialize Big Picture Mode Checkbox
        cbEnableBigPictureMode = view.findViewById(R.id.CBEnableBigPictureMode);
        cbEnableBigPictureMode.setChecked(preferences.getBoolean("enable_big_picture_mode", false));

        initCustomApiKeySettings(view);

        // Initialize the cursor lock checkbox
        cbCursorLock = view.findViewById(R.id.CBCursorLock);
        cbCursorLock.setChecked(preferences.getBoolean("cursor_lock", false));

        // Initialize the xinput toggle checkbox
        cbXinputToggle = view.findViewById(R.id.CBXinputToggle);
        cbXinputToggle.setChecked(preferences.getBoolean("xinput_toggle", false));

        Button btnChooseWinlatorPath = view.findViewById(R.id.BTChooseWinlatorPath);
        TextView tvWinlatorPath = view.findViewById(R.id.TVWinlatorPath);

        String savedUriString = preferences.getString("winlator_path_uri", null);
        if (savedUriString == null) {
            tvWinlatorPath.setText(SettingsConfig.DEFAULT_WINLATOR_PATH);
        } else {
            Uri savedUri = Uri.parse(savedUriString);
            String displayPath = FileUtils.getFilePathFromUri(getContext(), savedUri);
            tvWinlatorPath.setText(displayPath != null ? displayPath : savedUriString);
        }

        btnChooseWinlatorPath.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, REQUEST_CODE_WINLATOR_PATH);
        });

        Button btChooseShortcutExportPath = view.findViewById(R.id.BTChooseShortcutExportPath);
        TextView tvShortcutExportPath = view.findViewById(R.id.TVShortcutExportPath);

        savedUriString = preferences.getString("shortcuts_export_path_uri", null);

        if (savedUriString != null) {
            Uri savedUri = Uri.parse(savedUriString);
            String displayPath = FileUtils.getFilePathFromUri(context, savedUri);
            tvShortcutExportPath.setText(displayPath != null ? displayPath : savedUriString);
        } else {
            tvShortcutExportPath.setText(SettingsConfig.DEFAULT_SHORTCUT_EXPORT_PATH);
        }

        btChooseShortcutExportPath.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, REQUEST_CODE_SHORTCUT_EXPORT_PATH);
        });

        final Spinner sMIDISoundFont = view.findViewById(R.id.SMIDISoundFont);
        sMIDISoundFont.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

        final View btInstallSF = view.findViewById(R.id.BTInstallSF);
        final View btRemoveSF = view.findViewById(R.id.BTRemoveSF);

        MidiManager.loadSFSpinnerWithoutDisabled(sMIDISoundFont);
        btInstallSF.setOnClickListener(v -> {
            installSoundFontCallback = uri -> {
                PreloaderDialog dialog = new PreloaderDialog(requireActivity());
                dialog.showOnUiThread(R.string.installing_content);
                MidiManager.installSF2File(context, uri, new MidiManager.OnSoundFontInstalledCallback() {
                    @Override
                    public void onSuccess() {
                        dialog.closeOnUiThread();
                        requireActivity().runOnUiThread(() -> {
                            ContentDialog.alert(context, R.string.sound_font_installed_success, null);
                            MidiManager.loadSFSpinnerWithoutDisabled(sMIDISoundFont);
                        });
                    }

                    @Override
                    public void onFailed(int reason) {
                        dialog.closeOnUiThread();
                        int resId = switch (reason) {
                            case MidiManager.ERROR_BADFORMAT -> R.string.sound_font_bad_format;
                            case MidiManager.ERROR_EXIST -> R.string.sound_font_already_exist;
                            default -> R.string.sound_font_installed_failed;
                        };
                        requireActivity().runOnUiThread(() -> ContentDialog.alert(context, resId, null));
                    }
                });
            };

            openFile(REQUEST_CODE_INSTALL_SOUNDFONT);
        });

        btRemoveSF.setOnClickListener(v -> {
            if (sMIDISoundFont.getSelectedItemPosition() != 0) {
                ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_sound_font, () -> {
                    if (MidiManager.removeSF2File(context, sMIDISoundFont.getSelectedItem().toString())) {
                        AppUtils.showToast(context, R.string.sound_font_removed_success);
                        MidiManager.loadSFSpinnerWithoutDisabled(sMIDISoundFont);
                    } else
                        AppUtils.showToast(context, R.string.sound_font_removed_failed);
                });
            } else
                AppUtils.showToast(context, R.string.cannot_remove_default_sound_font);
        });

        cbUseDRI3 = view.findViewById(R.id.CBUseDRI3);
        cbUseDRI3.setChecked(preferences.getBoolean("use_dri3", true));

        cbUseXR = view.findViewById(R.id.CBUseXR);
        cbUseXR.setChecked(preferences.getBoolean("use_xr", true));
        if (!XrActivity.isSupported()) {
            cbUseXR.setVisibility(View.GONE);
        }

        final TextView tvCursorSpeed = view.findViewById(R.id.TVCursorSpeed);
        sbCursorSpeed = view.findViewById(R.id.SBCursorSpeed);
        sbCursorSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvCursorSpeed.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sbCursorSpeed.setProgress((int) (preferences.getFloat("cursor_speed", 1.0f) * 100));

        cbEnableFileProvider = view.findViewById(R.id.CBEnableFileProvider);
        final View btHelpFileProvider = view.findViewById(R.id.BTHelpFileProvider);

        cbEnableFileProvider.setChecked(preferences.getBoolean("enable_file_provider", true));
        cbEnableFileProvider.setOnClickListener(v -> AppUtils.showToast(context, R.string.take_effect_next_startup));
        btHelpFileProvider.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.help_file_provider));

        cbOpenInBrowser = view.findViewById(R.id.CBOpenWithAndroidBrowser);
        cbOpenInBrowser.setChecked(preferences.getBoolean("open_with_android_browser", false));

        cbShareClipboard = view.findViewById(R.id.CBShareAndroidClipboard);
        cbShareClipboard.setChecked(preferences.getBoolean("share_android_clipboard", false));

        view.findViewById(R.id.BTReInstallImagefs).setOnClickListener(v -> {
            ContentDialog.confirm(context, R.string.do_you_want_to_reinstall_imagefs, () -> ImageFsInstaller.installFromAssets((MainActivity) getActivity()));
        });

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        SharedPreferences.Editor editor = preferences.edit();

        editor.putBoolean("use_dri3", cbUseDRI3.isChecked());
        editor.putBoolean("use_xr", cbUseXR.isChecked());
        editor.putFloat("cursor_speed", sbCursorSpeed.getProgress() / 100.0f);
        editor.putBoolean("cursor_lock", cbCursorLock.isChecked());
        editor.putBoolean("xinput_toggle", cbXinputToggle.isChecked());
        editor.putBoolean("enable_file_provider", cbEnableFileProvider.isChecked());
        editor.putBoolean("open_with_android_browser", cbOpenInBrowser.isChecked());
        editor.putBoolean("share_android_clipboard", cbShareClipboard.isChecked());

        editor.putBoolean("enable_big_picture_mode", cbEnableBigPictureMode.isChecked());
        saveCustomApiKeySettings(editor);
        editor.apply();
    }

    private void initCustomApiKeySettings(View view) {
        cbEnableCustomApiKey = view.findViewById(R.id.CBEnableCustomApiKey);
        etCustomApiKey = view.findViewById(R.id.ETCustomApiKey);

        boolean isCustomApiKeyEnabled = preferences.getBoolean("enable_custom_api_key", false);
        String customApiKey = preferences.getString("custom_api_key", "");

        cbEnableCustomApiKey.setChecked(isCustomApiKeyEnabled);
        etCustomApiKey.setText(customApiKey);

        etCustomApiKey.setVisibility(isCustomApiKeyEnabled ? View.VISIBLE : View.GONE);

        cbEnableCustomApiKey.setOnCheckedChangeListener((buttonView, isChecked) -> {
            etCustomApiKey.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        view.findViewById(R.id.BTHelpApiKey).setOnClickListener(v -> {
            String url = "https://www.steamgriddb.com/profile/preferences/api";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });
    }

    private void saveCustomApiKeySettings(SharedPreferences.Editor editor) {
        boolean isCustomApiKeyEnabled = cbEnableCustomApiKey.isChecked();
        editor.putBoolean("enable_custom_api_key", isCustomApiKeyEnabled);

        if (isCustomApiKeyEnabled) {
            String customApiKey = etCustomApiKey.getText().toString().trim();
            editor.putString("custom_api_key", customApiKey);
        } else {
            editor.remove("custom_api_key");
        }
    }

    private void openFile(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        getActivity().startActivityFromFragment(this, intent, requestCode);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();

            if (uri != null) {
                SharedPreferences.Editor editor = preferences.edit();

                switch (requestCode) {
                    case REQUEST_CODE_WINLATOR_PATH:
                        editor.putString("winlator_path_uri", uri.toString());
                        editor.apply();

                        try {
                            requireContext().getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            );
                        } catch (SecurityException e) {
                            AppUtils.showToast(getContext(), "Unable to take persistable permissions: " + e.getMessage());
                        }

                        String fullPath = FileUtils.getFilePathFromUri(getContext(), uri);
                        TextView tvWinlatorPath = getView().findViewById(R.id.TVWinlatorPath);
                        tvWinlatorPath.setText(fullPath != null ? fullPath : uri.toString());
                        break;

                    case REQUEST_CODE_SHORTCUT_EXPORT_PATH:
                        editor.putString("shortcuts_export_path_uri", uri.toString());
                        editor.apply();

                        try {
                            requireContext().getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            );
                        } catch (SecurityException e) {
                            AppUtils.showToast(getContext(), "Unable to take persistable permissions: " + e.getMessage());
                        }

                        String path = FileUtils.getFilePathFromUri(getContext(), uri);
                        TextView tvShortcutExportPath = getView().findViewById(R.id.TVShortcutExportPath);
                        tvShortcutExportPath.setText(path != null ? path : uri.toString());
                        break;

                    case REQUEST_CODE_INSTALL_SOUNDFONT:
                        if (installSoundFontCallback != null) {
                            try {
                                installSoundFontCallback.call(uri);
                            } catch (Exception e) {
                                AppUtils.showToast(getContext(), R.string.unable_to_install_soundfont);
                            } finally {
                                installSoundFontCallback = null;
                            }
                        }
                        break;

                    default:
                        break;
                }
            }
        }
    }
}
