package com.winlator.cmod.shared.ui.widget;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupWindow;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import com.winlator.cmod.R;
import com.winlator.cmod.runtime.wine.WineThemeManager;
import com.winlator.cmod.shared.android.ActivityResultHost;
import com.winlator.cmod.shared.android.AppUtils;
import com.winlator.cmod.shared.io.FileUtils;
import com.winlator.cmod.shared.util.UnitUtils;
import java.io.File;

public class ImagePickerView extends AppCompatImageButton implements View.OnClickListener {

  public ImagePickerView(Context context) {
    this(context, null);
  }

  public ImagePickerView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ImagePickerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setImageResource(R.drawable.ic_image);
    setBackgroundResource(R.drawable.content_action_button_background);
    setScaleType(ScaleType.CENTER_INSIDE);
    int padding = (int) UnitUtils.dpToPx(9);
    setPadding(padding, padding, padding, padding);
    setColorFilter(ContextCompat.getColor(context, R.color.settings_icon_tint));
    setOnClickListener(this);
  }

  @Override
  public void onClick(View anchor) {
    final Context context = getContext();
    final File userWallpaperFile = WineThemeManager.getUserWallpaperFile(context);

    View view = LayoutInflater.from(context).inflate(R.layout.image_picker_view, null);
    ImageView imageView = view.findViewById(R.id.ImageView);

    if (userWallpaperFile.isFile()) {
      imageView.setImageBitmap(BitmapFactory.decodeFile(userWallpaperFile.getPath()));
    } else {
      imageView.setImageResource(R.drawable.wallpaper);
    }

    final PopupWindow[] popupWindow = {null};
    View browseButton = view.findViewById(R.id.BTBrowse);
    browseButton.setOnClickListener(
        (v) -> {
          popupWindow[0].dismiss();
          ActivityResultHost host = findActivityResultHost(context);
          if (host != null) {
            host.launchWallpaperImagePicker();
          } else {
            AppUtils.showToast(context, "Image picker is unavailable on this screen.");
          }
        });

    View removeButton = view.findViewById(R.id.BTRemove);
    if (userWallpaperFile.isFile()) {
      removeButton.setVisibility(View.VISIBLE);
      removeButton.setOnClickListener(
          (v) -> {
            FileUtils.delete(userWallpaperFile);
            popupWindow[0].dismiss();
          });
    }

    popupWindow[0] = AppUtils.showPopupWindow(anchor, view, 200, 230);
  }

  @Nullable private ActivityResultHost findActivityResultHost(Context context) {
    Context current = context;
    while (current instanceof android.content.ContextWrapper) {
      if (current instanceof ActivityResultHost) {
        return (ActivityResultHost) current;
      }
      current = ((android.content.ContextWrapper) current).getBaseContext();
    }
    return current instanceof ActivityResultHost ? (ActivityResultHost) current : null;
  }
}
