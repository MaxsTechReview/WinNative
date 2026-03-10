package com.winlator.cmod;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Html;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.winlator.cmod.R;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.ImageUtils;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.WineThemeManager;
import com.winlator.cmod.xenvironment.ImageFsInstaller;

import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.KeyEvent;
import android.view.MotionEvent;
import com.winlator.cmod.widget.ChasingBorderDrawable;

import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    public static final @IntRange(from = 1, to = 19) byte CONTAINER_PATTERN_COMPRESSION_LEVEL = 9;
    public static final byte PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 1;
    public static final byte OPEN_FILE_REQUEST_CODE = 2;
    public static final byte EDIT_INPUT_CONTROLS_REQUEST_CODE = 3;
    public static final byte OPEN_DIRECTORY_REQUEST_CODE = 4;
    public static final byte OPEN_IMAGE_REQUEST_CODE = 5;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    public final PreloaderDialog preloaderDialog = new PreloaderDialog(this);
    private boolean editInputControls = false;
    private int selectedProfileId;
    private int currentNavigationItemId = 0;
    private RecyclerView navigationRecyclerView;
    private final Runnable hideNavigationScrollbar = () -> {
        if (navigationRecyclerView != null) {
            navigationRecyclerView.setVerticalScrollBarEnabled(false);
        }
    };
    private SharedPreferences sharedPreferences;
    private ContainerManager containerManager;
    private boolean isDarkMode;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get shared preferences
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Check if Big Picture Mode is enabled
        boolean isBigPictureModeEnabled = sharedPreferences.getBoolean("enable_big_picture_mode", false);

        if (isBigPictureModeEnabled) {
            // If enabled, launch the BigPictureActivity and finish MainActivity
            Intent intent = new Intent(MainActivity.this, BigPictureActivity.class);
            startActivity(intent);
        }

        // Apply the dark theme unconditionally, as the new unified UI is fully dark-themed
        // and content_dialog_background defaults to a dark gradient.
        isDarkMode = true;
        setTheme(R.style.AppTheme_Dark);

        AppUtils.showSystemUI(this);
        getWindow().setNavigationBarColor(Color.parseColor("#0D1117"));
        getWindow().setStatusBarColor(Color.parseColor("#0D1117"));

        setContentView(R.layout.main_activity);

        findViewById(R.id.nav_header_back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        navigationView = findViewById(R.id.NavigationView);
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setItemBackground(createNavHighlightDrawable());
        navigationView.post(this::stripNavigationItemRipple);

        // Background is dark (#1B2838), so text color from XML app:itemTextColor="@color/white" is correct.
        // We do not override it here.

        // Create Winlator folder if not present
        File winlatorDir = new File(SettingsFragment.DEFAULT_WINLATOR_PATH);
        if (!winlatorDir.exists())
            winlatorDir.mkdirs();

        containerManager = new ContainerManager(this);

        handleIntent(getIntent());

        if (!requestAppPermissions()) {
            ImageFsInstaller.installIfNeeded(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String editShortcutPath = intent.getStringExtra("edit_shortcut_path");
        if (editShortcutPath != null) {
            File shortcutFile = new File(editShortcutPath);
            for (Shortcut shortcut : containerManager.loadShortcuts()) {
                if (shortcut.file.getPath().equals(shortcutFile.getPath())) {
                    show(new ContainerDetailFragment(shortcut), false);
                    return;
                }
            }
        }

        int createShortcutForAppId = intent.getIntExtra("create_shortcut_for_app_id", 0);
        int createShortcutForEpicId = intent.getIntExtra("create_shortcut_for_epic_id", 0);

        if (createShortcutForAppId > 0 || createShortcutForEpicId > 0) {
            String createShortcutForAppName = intent.getStringExtra("create_shortcut_for_app_name");
            int targetAppId = createShortcutForAppId > 0 ? createShortcutForAppId : createShortcutForEpicId;
            String targetSource = createShortcutForAppId > 0 ? "STEAM" : "EPIC";

            // Search for an existing shortcut with this app_id so we can edit it
            // instead of creating a new one each time
            Shortcut existingShortcut = null;
            for (Shortcut s : containerManager.loadShortcuts()) {
                String appIdExtra = s.getExtra("app_id");
                String sourceExtra = s.getExtra("game_source", "STEAM");
                if (appIdExtra != null && !appIdExtra.isEmpty() && sourceExtra.equals(targetSource)) {
                    try {
                        if (Integer.parseInt(appIdExtra) == targetAppId) {
                            existingShortcut = s;
                            break;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            
            if (existingShortcut != null) {
                // Found existing shortcut — open in edit mode so saved settings are loaded
                show(new ContainerDetailFragment(existingShortcut), false);
            } else {
                // No existing shortcut — open in create-new mode
                if (createShortcutForAppId > 0) {
                    show(new ContainerDetailFragment(0, createShortcutForAppId, createShortcutForAppName), false);
                } else {
                    show(new ContainerDetailFragment(0, createShortcutForEpicId, createShortcutForAppName, "EPIC"), false);
                }
            }
            return;
        }

        editInputControls = intent.getBooleanExtra("edit_input_controls", false);
        if (editInputControls) {
            selectedProfileId = intent.getIntExtra("selected_profile_id", 0);
            onNavigationItemSelected(navigationView.getMenu().findItem(R.id.main_menu_input_controls));
            navigationView.setCheckedItem(R.id.main_menu_input_controls);
        } else {
            int selectedMenuItemId = intent.getIntExtra("selected_menu_item_id", 0);
            int menuItemId = selectedMenuItemId > 0 ? selectedMenuItemId : R.id.main_menu_containers;

            onNavigationItemSelected(navigationView.getMenu().findItem(menuItemId));
            navigationView.setCheckedItem(menuItemId);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ImageFsInstaller.installIfNeeded(this);
            }
            else finish();
        }
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getSupportFragmentManager();
        
        // If there are fragments in the backstack (like ContainerDetailFragment), pop them normally!
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
            return;
        }

        // If backstack is empty, we are at the root fragment (ContainersFragment, Settings, etc).
        if (getIntent().getBooleanExtra("return_to_unified", false)) {
            finish();
            return;
        }

        super.onBackPressed();
    }

    private boolean requestAppPermissions() {
        boolean hasWritePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean hasReadPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean hasManageStoragePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();

        if (hasWritePermission && hasReadPermission && hasManageStoragePermission) {
            return false; // All permissions are granted
        }

        if (!hasWritePermission || !hasReadPermission) {
            String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
        }

        return true; // Permissions are still being requested
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        } else {
            return super.onOptionsItemSelected(menuItem);
        }
    }

    public void toggleDrawer() {
        // Drawer removed in new UI
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (item == null) return false;

        if (item.getItemId() == currentNavigationItemId) {
            return true;
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        Fragment fragment = null;
        switch (item.getItemId()) {
            case R.id.main_menu_containers:
                fragment = new ContainersFragment();
                break;
            case R.id.main_menu_input_controls:
                fragment = new InputControlsFragment(selectedProfileId);
                break;
            case R.id.main_menu_contents:
                fragment = new ContentsFragment();
                break;
            case R.id.main_menu_adrenotools_gpu_drivers:
                fragment = new AdrenotoolsFragment();
                break;
            case R.id.main_menu_stores:
                fragment = new StoresFragment();
                break;
            case R.id.main_menu_settings:
                fragment = new SettingsFragment();
                break;
            case R.id.main_menu_about:
                showAboutDialog();
                break;
        }

        if (fragment != null) {
            show(fragment, false);
            currentNavigationItemId = item.getItemId();
        }
        return true;
    }


//    private void show(Fragment fragment) {
//        FragmentManager fragmentManager = getSupportFragmentManager();
//        fragmentManager.beginTransaction()
//                .replace(R.id.FLFragmentContainer, fragment)
//                .commit();
//
//    }

    private void show(Fragment fragment, boolean reverse) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (reverse) {
            fragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.slide_in_down, R.anim.slide_out_up)  // Reverse animation
                    .replace(R.id.FLFragmentContainer, fragment)
                    .commit();
        } else {
            fragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.slide_in_up, R.anim.slide_out_down)  // Forward animation
                    .replace(R.id.FLFragmentContainer, fragment)
                    .commit();
        }
    }

    private void showAboutDialog() {
        ContentDialog dialog = new ContentDialog(this, R.layout.about_dialog);
        dialog.findViewById(R.id.LLBottomBar).setVisibility(View.GONE);

        if (isDarkMode) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.content_dialog_background_dark);
        } else {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.content_dialog_background);
        }

        try {
            final PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);

            TextView tvWebpage = dialog.findViewById(R.id.TVWebpage);
            tvWebpage.setText(Html.fromHtml("<a href=\"https://www.winlator.org\">winlator.org</a>", Html.FROM_HTML_MODE_LEGACY));
            tvWebpage.setMovementMethod(LinkMovementMethod.getInstance());

            ((TextView) dialog.findViewById(R.id.TVAppVersion)).setText(getString(R.string.version) + " " + pInfo.versionName);

            String creditsAndThirdPartyAppsHTML = String.join("<br />",
                    "Winlator Cmod by coffincolors, me (<a href=\"https://github.com/coffincolors/winlator\">Fork</a>, <a href=\"https://github.com/Pipetto-crypto/winlator\">Fork</a>)",
                    "Big Picture Mode Music by",
                    "Dale Melvin Blevens III (Fumer)",
                    "---",
                    "Termux Package(<a href=\"https://github.com/termux/termux-packages\">github.com/termux/termux-package</a>)",
                    "Wine (<a href=\"https://www.winehq.org\">winehq.org</a>)",
                    "Box64 (<a href=\"https://github.com/ptitSeb/box64\">github.com/ptitSeb/box64</a>)",
                    "Mesa (Turnip/Zink/Wrapper) (<a href=\"https://github.com/xMeM/mesa/tree/wrapper\">github.com/xMeM/mesa</a>)",
                    "DXVK (<a href=\"https://github.com/doitsujin/dxvk\">github.com/doitsujin/dxvk</a>)",
                    "VKD3D (<a href=\"https://gitlab.winehq.org/wine/vkd3d\">gitlab.winehq.org/wine/vkd3d</a>)",
                    "D8VK (<a href=\"https://github.com/AlpyneDreams/d8vk\">github.com/AlpyneDreams/d8vk</a>)",
                    "CNC DDraw (<a href=\"https://github.com/FunkyFr3sh/cnc-ddraw\">github.com/FunkyFr3sh/cnc-ddraw</a>)",
                    "dxwrapper (<a href=\"https://github.com/elishacloud/dxwrapper\">github.com/elishacloud/dxwrapper</a>)",
                    "FEX-Emu (<a href=\"https://github.com/FEX-Emu/FEX\">github.com/FEX-Emu/FEX</a>)",
                    "libadrenotools (<a href=\"https://github.com/bylaws/libadrenotools\">github.com/bylaws/libadrenotools</a>)"
            );

            TextView tvCreditsAndThirdPartyApps = dialog.findViewById(R.id.TVCreditsAndThirdPartyApps);
            tvCreditsAndThirdPartyApps.setText(Html.fromHtml(creditsAndThirdPartyAppsHTML, Html.FROM_HTML_MODE_LEGACY));
            tvCreditsAndThirdPartyApps.setMovementMethod(LinkMovementMethod.getInstance());

            String glibcExpVersionForkHTML = String.join("<br />",
                    "longjunyu2's <a href=\"https://github.com/longjunyu2/winlator/tree/use-glibc-instead-of-proot\">(GLIBC Fork)</a>");
            TextView tvGlibcExpVersionFork = dialog.findViewById(R.id.TVGlibcExpVersionFork);
            tvGlibcExpVersionFork.setText(Html.fromHtml(glibcExpVersionForkHTML, Html.FROM_HTML_MODE_LEGACY));
            tvGlibcExpVersionFork.setMovementMethod(LinkMovementMethod.getInstance());
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        dialog.show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_IMAGE_REQUEST_CODE && resultCode == RESULT_OK) {
            Bitmap bitmap = ImageUtils.getBitmapFromUri(this, data.getData(), 1280);
            if (bitmap == null) return;
            File userWallpaperFile = WineThemeManager.getUserWallpaperFile(this);
            ImageUtils.save(bitmap, userWallpaperFile, Bitmap.CompressFormat.PNG, 100);
        }
    }

    private StateListDrawable createNavHighlightDrawable() {
        StateListDrawable states = new StateListDrawable();
        float density = getResources().getDisplayMetrics().density;
        int rightInset = (int) (10 * density);

        // Only the selected item gets the animated outline.
        ChasingBorderDrawable checkedDrawable = new ChasingBorderDrawable(8f, 1.5f, density);
        states.addState(new int[]{android.R.attr.state_checked}, new InsetDrawable(checkedDrawable, 0, 0, rightInset, 0));

        // Controller/keyboard focus on an unselected item should be static, not animated.
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0x00000000);
        focused.setStroke((int) (1.5f * density), 0x5000D7F5);
        focused.setCornerRadius(8 * density);
        states.addState(new int[]{android.R.attr.state_focused, -android.R.attr.state_checked}, new InsetDrawable(focused, 0, 0, rightInset, 0));

        // Hover uses the same static outline treatment.
        GradientDrawable hovered = new GradientDrawable();
        hovered.setColor(0x00000000);
        hovered.setStroke((int) (1.5f * density), 0x5000D7F5);
        hovered.setCornerRadius(8 * density);
        states.addState(new int[]{android.R.attr.state_hovered, -android.R.attr.state_checked}, new InsetDrawable(hovered, 0, 0, rightInset, 0));

        // Default state
        GradientDrawable defaultState = new GradientDrawable();
        defaultState.setColor(0x00000000);
        defaultState.setCornerRadius(8 * density);
        states.addState(new int[]{}, new InsetDrawable(defaultState, 0, 0, rightInset, 0));

        return states;
    }

    private void stripNavigationItemRipple() {
        for (int i = 0; i < navigationView.getChildCount(); i++) {
            View child = navigationView.getChildAt(i);
            if (!(child instanceof RecyclerView)) continue;

            RecyclerView recyclerView = (RecyclerView) child;
            configureNavigationRecyclerView(recyclerView);

            for (int index = 0; index < recyclerView.getChildCount(); index++) {
                applyNavigationItemBackground(recyclerView.getChildAt(index));
            }
        }
    }

    private void configureNavigationRecyclerView(RecyclerView recyclerView) {
        if (navigationRecyclerView == recyclerView) {
            return;
        }

        navigationRecyclerView = recyclerView;
        navigationView.setVerticalScrollBarEnabled(false);
        recyclerView.setItemAnimator(null);
        recyclerView.setScrollbarFadingEnabled(true);
        recyclerView.setScrollBarDefaultDelayBeforeFade(220);
        recyclerView.setScrollBarFadeDuration(320);
        recyclerView.setVerticalScrollBarEnabled(false);
        recyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                view.post(() -> applyNavigationItemBackground(view));
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
            }
        });
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                rv.removeCallbacks(hideNavigationScrollbar);

                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    rv.postDelayed(hideNavigationScrollbar, 540L);
                } else {
                    rv.setVerticalScrollBarEnabled(true);
                }
            }
        });
    }

    private void applyNavigationItemBackground(@NonNull View itemView) {
        Drawable background = itemView.getBackground();
        if (background instanceof RippleDrawable || background == null) {
            StateListDrawable navHighlightDrawable = createNavHighlightDrawable();
            Drawable.ConstantState constantState = navHighlightDrawable.getConstantState();
            itemView.setBackground(constantState != null
                    ? constantState.newDrawable().mutate()
                    : navHighlightDrawable.mutate());
        }
        itemView.setForeground(null);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.FLFragmentContainer);
        if (fragment instanceof InputControlsFragment) {
            if (((InputControlsFragment) fragment).dispatchKeyEvent(event)) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.FLFragmentContainer);
        if (fragment instanceof InputControlsFragment) {
            if (((InputControlsFragment) fragment).dispatchGenericMotionEvent(event)) return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }
}
