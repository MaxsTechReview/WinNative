package com.winlator.cmod.shared.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.google.android.material.tabs.TabLayout;
import com.winlator.cmod.R;
import com.winlator.cmod.shared.util.Callback;
import com.winlator.cmod.shared.util.StringUtils;
import com.winlator.cmod.shared.util.UnitUtils;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public abstract class AppUtils {
  private static WeakReference<Toast> globalToastReference = null;
  private static WeakReference<PopupWindow> globalPopupToastReference = null;

  public static void keepScreenOn(Activity activity) {
    activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  public static String getArchName() {
    for (String arch : Build.SUPPORTED_ABIS) {
      switch (arch) {
        case "arm64-v8a":
          return "arm64";
        case "armeabi-v7a":
          return "armhf";
        case "x86_64":
          return "x86_64";
        case "x86":
          return "x86";
      }
    }
    return "armhf";
  }

  public static void restartActivity(AppCompatActivity activity) {
    Intent intent = new Intent(activity.getIntent());
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
    activity.finish();
    activity.startActivity(intent);
  }

  public static void applyOpenActivityTransition(Activity activity, int enterAnim, int exitAnim) {
    applyActivityTransition(activity, false, enterAnim, exitAnim);
  }

  public static void applyCloseActivityTransition(Activity activity, int enterAnim, int exitAnim) {
    applyActivityTransition(activity, true, enterAnim, exitAnim);
  }

  @SuppressWarnings("deprecation")
  private static void applyActivityTransition(
      Activity activity, boolean closing, int enterAnim, int exitAnim) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      activity.overrideActivityTransition(
          closing ? Activity.OVERRIDE_TRANSITION_CLOSE : Activity.OVERRIDE_TRANSITION_OPEN,
          enterAnim,
          exitAnim);
    } else {
      activity.overridePendingTransition(enterAnim, exitAnim);
    }
  }

  public static void restartApplication(Context context) {
    restartApplication(context, 0);
  }

  public static void restartApplication(Context context, int selectedMenuItemId) {
    AppTerminationHelper.stopManagedServices(context, "restart_application");
    Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
    Intent mainIntent = Intent.makeRestartActivityTask(intent.getComponent());
    if (selectedMenuItemId > 0) mainIntent.putExtra("selected_menu_item_id", selectedMenuItemId);
    context.startActivity(mainIntent);
    Runtime.getRuntime().exit(0);
  }

  public static void showKeyboard(AppCompatActivity activity) {
    View targetView = activity.getCurrentFocus();
    if (targetView == null) targetView = activity.getWindow().getDecorView();
    final View finalTargetView = targetView;
    finalTargetView.requestFocus();
    finalTargetView.postDelayed(
        () -> {
          WindowInsetsControllerCompat insetsController =
              WindowCompat.getInsetsController(activity.getWindow(), finalTargetView);
          if (insetsController != null) {
            insetsController.show(WindowInsetsCompat.Type.ime());
          }
          InputMethodManager imm =
              ContextCompat.getSystemService(activity, InputMethodManager.class);
          if (imm != null) {
            imm.showSoftInput(finalTargetView, InputMethodManager.SHOW_IMPLICIT);
          }
        },
        500L);
  }

  public static void hideKeyboard(Activity activity) {
    if (activity == null) return;
    View view = activity.getCurrentFocus();
    if (view == null) view = activity.getWindow().getDecorView();
    hideKeyboard(view);
  }

  public static void hideKeyboard(View view) {
    if (view == null) return;
    InputMethodManager imm =
        (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
  }

  public static void hideSystemUI(final Activity activity) {
    Window window = activity.getWindow();
    final View decorView = window.getDecorView();
    WindowCompat.setDecorFitsSystemWindows(window, false);
    final WindowInsetsControllerCompat insetsController =
        WindowCompat.getInsetsController(window, decorView);
    if (insetsController != null) {
      insetsController.hide(WindowInsetsCompat.Type.systemBars());
      insetsController.setSystemBarsBehavior(
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }
  }

  public static void showSystemUI(final Activity activity) {
    Window window = activity.getWindow();
    final View decorView = window.getDecorView();
    WindowCompat.setDecorFitsSystemWindows(window, false);
    final WindowInsetsControllerCompat insetsController =
        WindowCompat.getInsetsController(window, decorView);
    if (insetsController != null) {
      insetsController.show(WindowInsetsCompat.Type.systemBars());
      insetsController.setSystemBarsBehavior(
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }
  }

  public static boolean isUiThread() {
    return Looper.getMainLooper().getThread() == Thread.currentThread();
  }

  public static int getScreenWidth() {
    return Resources.getSystem().getDisplayMetrics().widthPixels;
  }

  public static int getScreenHeight() {
    return Resources.getSystem().getDisplayMetrics().heightPixels;
  }

  public static int getPreferredDialogWidth(Context context) {
    int orientation = context.getResources().getConfiguration().orientation;
    float scale = orientation == Configuration.ORIENTATION_PORTRAIT ? 0.8f : 0.5f;
    return (int) UnitUtils.dpToPx(UnitUtils.pxToDp(AppUtils.getScreenWidth()) * scale);
  }

  public static Toast showToast(Context context, int textResId) {
    return showToast(context, context.getString(textResId));
  }

  public static Toast showToast(Context context, int textResId, Bitmap iconBitmap) {
    return showToast(context, context.getString(textResId), iconBitmap);
  }

  public static Toast showToast(Context context, int textResId, int toastDuration) {
    return showToast(context, context.getString(textResId), toastDuration);
  }

  public static void showToast(Context context, int textResId, long durationMs) {
    showToast(context, context.getString(textResId), durationMs);
  }

  public static Toast showToast(final Context context, final String text) {
    return showToast(context, text, null);
  }

  public static Toast showToast(final Context context, final String text, final int toastDuration) {
    return showToast(context, text, null, toastDuration);
  }

  public static Toast showToast(final Context context, final String text, final Bitmap iconBitmap) {
    return showToast(
        context, text, iconBitmap, text.length() >= 40 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
  }

  public static Toast showToast(
      final Context context, final String text, final Bitmap iconBitmap, final int toastDuration) {
    if (!isUiThread()) {
      new Handler(Looper.getMainLooper())
          .post(() -> showToast(context, text, iconBitmap, toastDuration));
      return null;
    }

    dismissActiveToasts();
    Toast toast =
        new Toast(
            context.getApplicationContext() != null ? context.getApplicationContext() : context);
    toast.setDuration(toastDuration);
    toast.setGravity(Gravity.CENTER | Gravity.BOTTOM, 0, 50);
    toast.setView(createToastView(context, text, iconBitmap));
    toast.show();
    globalToastReference = new WeakReference<>(toast);
    return toast;
  }

  public static void showToast(final Context context, final String text, final long durationMs) {
    if (!isUiThread()) {
      new Handler(Looper.getMainLooper()).post(() -> showToast(context, text, durationMs));
      return;
    }

    dismissActiveToasts();

    if (!(context instanceof Activity)) {
      showToast(context, text);
      return;
    }

    Activity activity = (Activity) context;
    if (activity.isFinishing() || activity.isDestroyed()) {
      return;
    }
    showPopupToast(activity, text, null, durationMs);
  }

  private static View createToastView(Context context, String text, Bitmap iconBitmap) {
    View view = LayoutInflater.from(context).inflate(R.layout.custom_toast, null);
    ((TextView) view.findViewById(R.id.TextView)).setText(text);

    ImageView iconView = view.findViewById(R.id.IconView);
    if (iconBitmap != null) {
      iconView.setImageBitmap(iconBitmap);
    } else {
      iconView.setImageResource(R.mipmap.ic_launcher);
    }
    iconView.setVisibility(View.VISIBLE);
    return view;
  }

  private static void dismissActiveToasts() {
    if (globalToastReference != null) {
      Toast toast = globalToastReference.get();
      if (toast != null) toast.cancel();
      globalToastReference = null;
    }

    if (globalPopupToastReference != null) {
      PopupWindow popupWindow = globalPopupToastReference.get();
      if (popupWindow != null) popupWindow.dismiss();
      globalPopupToastReference = null;
    }
  }

  private static void showPopupToast(
      Activity activity, String text, Bitmap iconBitmap, long durationMs) {
    View view = createToastView(activity, text, iconBitmap);

    PopupWindow popupWindow =
        new PopupWindow(
            view, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
    popupWindow.setTouchable(false);
    popupWindow.setOutsideTouchable(false);
    popupWindow.setClippingEnabled(false);
    popupWindow.showAtLocation(
        activity.getWindow().getDecorView(), Gravity.CENTER | Gravity.BOTTOM, 0, 50);
    globalPopupToastReference = new WeakReference<>(popupWindow);

    view.postDelayed(
        () -> {
          try {
            PopupWindow currentPopup =
                globalPopupToastReference != null ? globalPopupToastReference.get() : null;
            if (currentPopup == popupWindow && popupWindow.isShowing()) {
              popupWindow.dismiss();
              globalPopupToastReference = null;
            }
          } catch (Exception ignored) {
          }
        },
        durationMs);
  }

  public static PopupWindow showPopupWindow(View anchor, View contentView) {
    return showPopupWindow(anchor, contentView, 0, 0);
  }

  public static PopupWindow showPopupWindow(View anchor, View contentView, int width, int height) {
    Context context = anchor.getContext();
    PopupWindow popupWindow = new PopupWindow(context);
    popupWindow.setBackgroundDrawable(
        androidx.core.content.ContextCompat.getDrawable(
            context, R.drawable.content_popup_menu_background));
    popupWindow.setElevation(10.0f);
    popupWindow.setFocusable(true);
    popupWindow.setOutsideTouchable(true);

    if (width == 0 && height == 0) {
      int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
      int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
      contentView.measure(widthMeasureSpec, heightMeasureSpec);
      popupWindow.setWidth(contentView.getMeasuredWidth());
      popupWindow.setHeight(contentView.getMeasuredHeight());
    } else {
      if (width > 0) {
        popupWindow.setWidth((int) UnitUtils.dpToPx(width));
      } else popupWindow.setWidth(LinearLayout.LayoutParams.WRAP_CONTENT);

      if (height > 0) {
        popupWindow.setHeight((int) UnitUtils.dpToPx(height));
      } else popupWindow.setHeight(LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    popupWindow.setContentView(contentView);
    popupWindow.setFocusable(false);
    popupWindow.setOutsideTouchable(true);

    popupWindow.update();
    popupWindow.showAsDropDown(anchor);

    popupWindow.setFocusable(true);
    popupWindow.update();
    return popupWindow;
  }

  public static void showHelpBox(Context context, View anchor, int textResId) {
    showHelpBox(context, anchor, context.getString(textResId));
  }

  public static void showHelpBox(Context context, View anchor, String text) {
    int padding = (int) UnitUtils.dpToPx(14);
    TextView textView = new TextView(context);
    textView.setLayoutParams(
        new ViewGroup.LayoutParams(
            (int) UnitUtils.dpToPx(284), ViewGroup.LayoutParams.WRAP_CONTENT));
    textView.setPadding(padding, padding, padding, padding);
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
    textView.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY));
    textView.setTextColor(ContextCompat.getColor(context, R.color.settings_text_primary));
    textView.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(context, R.font.inter));
    textView.setLineSpacing(UnitUtils.dpToPx(4), 1.0f);
    textView.setBackgroundResource(R.drawable.help_popup_background);
    int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
    int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
    textView.measure(widthMeasureSpec, heightMeasureSpec);
    showPopupWindow(anchor, textView, 300, textView.getMeasuredHeight());
  }

  public static int getVersionCode(Context context) {
    try {
      PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      return (int) PackageInfoCompat.getLongVersionCode(pInfo);
    } catch (PackageManager.NameNotFoundException e) {
      return 0;
    }
  }

  public static void observeSoftKeyboardVisibility(View rootView, Callback<Boolean> callback) {
    final boolean[] visible = {false};
    rootView
        .getViewTreeObserver()
        .addOnGlobalLayoutListener(
            () -> {
              Rect rect = new Rect();
              rootView.getWindowVisibleDisplayFrame(rect);
              int screenHeight = rootView.getRootView().getHeight();
              int keypadHeight = screenHeight - rect.bottom;

              if (keypadHeight > screenHeight * 0.15f) {
                if (!visible[0]) {
                  visible[0] = true;
                  callback.call(true);
                }
              } else {
                if (visible[0]) {
                  visible[0] = false;
                  callback.call(false);
                }
              }
            });
  }

  public static <T> void setupThemedSpinner(Spinner spinner, Context context, List<T> items) {
    ArrayAdapter<T> adapter = new ArrayAdapter<>(context, R.layout.spinner_item_themed, items);
    adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_themed);
    spinner.setAdapter(adapter);
    spinner.setPopupBackgroundResource(R.drawable.content_popup_menu_background);
  }

  public static boolean setSpinnerSelectionFromValue(Spinner spinner, String value) {
    spinner.setSelection(0, false);
    for (int i = 0; i < spinner.getCount(); i++) {
      if (spinner.getItemAtPosition(i).toString().equalsIgnoreCase(value)) {
        spinner.setSelection(i, false);
        return true;
      }
    }
    return false;
  }

  public static boolean setSpinnerSelectionFromIdentifier(Spinner spinner, String identifier) {
    spinner.setSelection(0, false);
    for (int i = 0; i < spinner.getCount(); i++) {
      if (StringUtils.parseIdentifier(spinner.getItemAtPosition(i)).equals(identifier)) {
        spinner.setSelection(i, false);
        return true;
      }
    }
    return false;
  }

  public static boolean setSpinnerSelectionFromNumber(Spinner spinner, String number) {
    spinner.setSelection(0, false);
    for (int i = 0; i < spinner.getCount(); i++) {
      if (StringUtils.parseNumber(spinner.getItemAtPosition(i)).equals(number)) {
        spinner.setSelection(i, false);
        return true;
      }
    }
    return false;
  }

  public static void setupTabLayout(final View view, int tabLayoutResId, final int... tabResIds) {
    final Callback<Integer> tabSelectedCallback =
        (position) -> {
          for (int i = 0; i < tabResIds.length; i++) {
            View tabView = view.findViewById(tabResIds[i]);
            tabView.setVisibility(position == i ? View.VISIBLE : View.GONE);
          }
        };

    TabLayout tabLayout = view.findViewById(tabLayoutResId);
    tabLayout.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          @Override
          public void onTabSelected(TabLayout.Tab tab) {
            tabSelectedCallback.call(tab.getPosition());
          }

          @Override
          public void onTabUnselected(TabLayout.Tab tab) {}

          @Override
          public void onTabReselected(TabLayout.Tab tab) {
            tabSelectedCallback.call(tab.getPosition());
          }
        });
    tabLayout.getTabAt(0).select();
  }

  public static void findViewsWithClass(
      ViewGroup parent, Class viewClass, ArrayList<View> outViews) {
    for (int i = 0, childCount = parent.getChildCount(); i < childCount; i++) {
      View child = parent.getChildAt(i);
      Class _class = child.getClass();
      if (_class == viewClass || _class.getSuperclass() == viewClass) {
        outViews.add(child);
      } else if (child instanceof ViewGroup) {
        findViewsWithClass((ViewGroup) child, viewClass, outViews);
      }
    }
  }

  public static String getNativeLibDir(Context context) {
    return context.getApplicationInfo().nativeLibraryDir;
  }

  public static void runDelayed(Runnable callback, long delay) {
    if (callback == null) {
      return;
    }

    // Create a Timer to schedule the task
    Timer timer = new Timer();

    // Schedule the task with the specified delay
    timer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            callback.run();
          }
        },
        delay);
  }
}
