package com.winlator.cmod.feature.settings;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import com.winlator.cmod.R;
import com.winlator.cmod.runtime.system.ui.LogView;
import com.winlator.cmod.shared.android.AppUtils;
import com.winlator.cmod.shared.ui.dialog.ContentDialog;
import com.winlator.cmod.shared.util.Callback;
import com.winlator.cmod.shared.util.UnitUtils;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class DebugDialog extends ContentDialog implements Callback<String> {
  private final LogView logView;
  private static boolean paused = false;
  private BufferedWriter writer;

  public DebugDialog(@NonNull Context context) {
    super(context, R.layout.debug_dialog);
    setIcon(R.drawable.icon_debug);
    setTitle(context.getString(R.string.session_drawer_logs));
    logView = findViewById(R.id.LogView);

    logView.getLayoutParams().width =
        (int) UnitUtils.dpToPx(UnitUtils.pxToDp(AppUtils.getScreenWidth()) * 0.7f);

    findViewById(R.id.BTCancel).setVisibility(View.GONE);

    LinearLayout llBottomBarPanel = findViewById(R.id.LLBottomBarPanel);
    llBottomBarPanel.setVisibility(View.VISIBLE);

    View toolbarView =
        LayoutInflater.from(context).inflate(R.layout.debug_toolbar, llBottomBarPanel, false);
    toolbarView.findViewById(R.id.BTClear).setOnClickListener((v) -> logView.clear());
    toolbarView
        .findViewById(R.id.BTPause)
        .setOnClickListener(
            (v) -> {
              setPaused(!paused);
              ((ImageButton) v)
                  .setImageResource(getPaused() ? R.drawable.icon_play : R.drawable.icon_pause);
            });
    llBottomBarPanel.addView(toolbarView);
    try {
      writer = new BufferedWriter(new FileWriter(logView.getLogFile(context)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public synchronized void call(final String line) {
    if (writer == null) return;
    if (!getPaused()) logView.append(line + "\n");
    try {
      writer.write(line + "\n");
      writer.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public synchronized void dispose() {
    if (writer == null) return;
    try {
      writer.close();
    } catch (IOException ignored) {
    } finally {
      writer = null;
    }
  }

  public static void setPaused(boolean cond) {
    paused = cond;
  }

  public static boolean getPaused() {
    return paused;
  }
}
