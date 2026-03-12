package com.winlator.cmod.widget;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupWindow;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.R;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.UnitUtils;
import com.winlator.cmod.core.WineThemeManager;

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
        browseButton.setOnClickListener((v) -> {
            MainActivity activity = (MainActivity) context;
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            popupWindow[0].dismiss();
            activity.startActivityForResult(intent, MainActivity.OPEN_IMAGE_REQUEST_CODE);
        });

        View removeButton = view.findViewById(R.id.BTRemove);
        if (userWallpaperFile.isFile()) {
            removeButton.setVisibility(View.VISIBLE);
            removeButton.setOnClickListener((v) -> {
                FileUtils.delete(userWallpaperFile);
                popupWindow[0].dismiss();
            });
        }

        popupWindow[0] = AppUtils.showPopupWindow(anchor, view, 200, 230);
    }
}
