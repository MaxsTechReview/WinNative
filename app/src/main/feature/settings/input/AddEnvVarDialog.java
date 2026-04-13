package com.winlator.cmod.feature.settings;

import android.content.Context;
import android.view.Menu;
import android.widget.EditText;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.PopupMenu;

import com.winlator.cmod.R;
import com.winlator.cmod.shared.ui.dialog.ContentDialog;
import com.winlator.cmod.shared.ui.widget.EnvVarsView;

public class AddEnvVarDialog extends ContentDialog {
    public AddEnvVarDialog(final Context context, final EnvVarsView envVarsView) {
        super(context, R.layout.add_env_var_dialog);
        final EditText etName = findViewById(R.id.ETName);
        final EditText etValue = findViewById(R.id.ETValue);

        setTitle(context.getString(R.string.container_config_new_env_var));
        setIcon(R.drawable.ic_content_env_var);

        findViewById(R.id.BTMenu).setOnClickListener((v) -> {
            PopupMenu popupMenu = new PopupMenu(new ContextThemeWrapper(context, R.style.ThemeOverlay_ContentPopupMenu), v);
            Menu menu = popupMenu.getMenu();
            for (String[] knownEnvVar : EnvVarsView.knownEnvVars) menu.add(knownEnvVar[0]);

            popupMenu.setOnMenuItemClickListener((menuItem) -> {
                etName.setText(menuItem.getTitle());
                return true;
            });
            popupMenu.show();
        });

        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        etName.requestFocus();

        setOnConfirmCallback(() -> {
            String name = etName.getText().toString().trim().replace(" ", "");
            String value = etValue.getText().toString().trim().replace(" ", "");

            if (!name.isEmpty() && !envVarsView.containsName(name)) {
                envVarsView.add(name, value);
            }
        });
    }
}
