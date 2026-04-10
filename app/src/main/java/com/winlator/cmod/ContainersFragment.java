package com.winlator.cmod;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contentdialog.ContainerSettingsComposeDialog;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.StorageInfoDialog;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.google.ContainerBackupManager;
import com.winlator.cmod.xenvironment.ImageFs;

import java.util.ArrayList;
import java.util.List;

public class ContainersFragment extends Fragment {
    private static final int VIEW_TYPE_ADD = 0;
    private static final int VIEW_TYPE_CONTAINER = 1;

    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private ContainerManager manager;
    private PreloaderDialog preloaderDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preloaderDialog = new PreloaderDialog(getActivity());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getActivity() instanceof AppCompatActivity) {
            androidx.appcompat.app.ActionBar actionBar =
                    ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(R.string.common_ui_containers);
            }
        }
        manager = new ContainerManager(getContext());
        loadContainersList();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.containers_fragment, container, false);
        recyclerView = view.findViewById(R.id.RecyclerView);
        emptyTextView = view.findViewById(R.id.TVEmptyText);

        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 3);
        recyclerView.setLayoutManager(gridLayoutManager);

        int spacing = (int) (8 * getResources().getDisplayMetrics().density);
        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View v,
                                       @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(v);
                int column = position % 3;
                outRect.left = column * spacing / 3;
                outRect.right = spacing - (column + 1) * spacing / 3;
                outRect.bottom = spacing;
            }
        });

        return view;
    }

    private void openAddContainer() {
        if (!ImageFs.find(getContext()).isValid()) {
            Toast.makeText(getContext(), R.string.setup_wizard_system_image_not_installed, Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            Context ctx = getContext();
            if (ctx == null) return;
            boolean installed = ContentsManager.hasInstalledRuntimes(ctx);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                if (!installed) {
                    Toast.makeText(getContext(), R.string.container_no_wine_installed, Toast.LENGTH_LONG).show();
                    return;
                }
                new ContainerSettingsComposeDialog(requireActivity(), null, this::loadContainersList).show();
            });
        }).start();
    }

    private void loadContainersList() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        manager = new ContainerManager(context);
        ArrayList<Container> containers = manager.getContainers();
        recyclerView.setAdapter(new ContainersAdapter(containers));
        emptyTextView.setVisibility(containers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showContainerBackupsDialog(Container container) {
        ContentDialog dialog = new ContentDialog(requireContext());
        dialog.setTitle(R.string.container_backups_title);
        dialog.setMessage(R.string.container_backups_prompt);

        TextView backupButton = dialog.findViewById(R.id.BTConfirm);
        TextView restoreButton = dialog.findViewById(R.id.BTCancel);
        backupButton.setText(R.string.google_cloud_backup);
        restoreButton.setText(R.string.google_cloud_restore);

        dialog.setOnConfirmCallback(() -> startContainerBackup(container));
        dialog.setOnCancelCallback(() -> startContainerRestore(container));
        dialog.show();
    }

    private void startContainerBackup(Container container) {
        preloaderDialog.show(R.string.container_backups_backing_up);
        ContainerBackupManager.backupContainer(requireActivity(), container, result -> {
            preloaderDialog.close();
            ContentDialog.alert(requireContext(), result.message, () -> {});
        });
    }

    private void startContainerRestore(Container container) {
        preloaderDialog.show(R.string.container_backups_checking);
        ContainerBackupManager.prepareRestore(requireActivity(), container, preparation -> {
            if (!isAdded()) {
                preloaderDialog.close();
                return;
            }

            if (!preparation.success) {
                preloaderDialog.close();
                ContentDialog.alert(requireContext(), preparation.message, () -> {});
                return;
            }

            if (preparation.matchedFile != null) {
                executeContainerRestore(container, preparation.matchedFile);
                return;
            }

            preloaderDialog.close();
            showBackupSelectionDialog(container, preparation.candidates);
        });
    }

    private void showBackupSelectionDialog(Container container, List<ContainerBackupManager.DriveBackupFile> backups) {
        if (backups == null || backups.isEmpty()) {
            ContentDialog.alert(requireContext(), R.string.container_backups_no_files, () -> {});
            return;
        }

        String[] names = new String[backups.size()];
        for (int i = 0; i < backups.size(); i++) {
            names[i] = backups.get(i).name;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.container_backups_select_title)
                .setItems(names, (dialog, which) -> executeContainerRestore(container, backups.get(which)))
                .setNegativeButton(R.string.common_ui_cancel, null)
                .show();
    }

    private void executeContainerRestore(Container container, ContainerBackupManager.DriveBackupFile driveFile) {
        preloaderDialog.show(R.string.container_backups_restoring);
        ContainerBackupManager.restoreContainerFromDriveFile(requireActivity(), container, driveFile, result -> {
            preloaderDialog.close();
            ContentDialog.alert(requireContext(), result.message, () -> {});
        });
    }

    private class ContainersAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Container> data;

        private class AddViewHolder extends RecyclerView.ViewHolder {
            private AddViewHolder(View view) {
                super(view);
                view.setOnClickListener(v -> openAddContainer());
            }
        }

        private class ContainerViewHolder extends RecyclerView.ViewHolder {
            private final ImageView runButton;
            private final ImageView editButton;
            private final ImageView duplicateButton;
            private final ImageView menuButton;
            private final ImageView imageView;
            private final TextView title;

            private ContainerViewHolder(View view) {
                super(view);
                this.runButton = view.findViewById(R.id.BTRun);
                this.editButton = view.findViewById(R.id.BTEdit);
                this.duplicateButton = view.findViewById(R.id.BTDuplicate);
                this.imageView = view.findViewById(R.id.ImageView);
                this.title = view.findViewById(R.id.TVTitle);
                this.menuButton = view.findViewById(R.id.BTMenu);
            }
        }

        public ContainersAdapter(List<Container> data) {
            this.data = data;
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? VIEW_TYPE_ADD : VIEW_TYPE_CONTAINER;
        }

        @Override
        public final int getItemCount() {
            return data.size() + 1; // +1 for the add card
        }

        @NonNull
        @Override
        public final RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_ADD) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.container_add_card, parent, false);
                return new AddViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.container_list_item, parent, false);
                return new ContainerViewHolder(view);
            }
        }

        @Override
        public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
            if (holder instanceof ContainerViewHolder) {
                ContainerViewHolder ch = (ContainerViewHolder) holder;
                ch.runButton.setOnClickListener(null);
                ch.editButton.setOnClickListener(null);
                ch.duplicateButton.setOnClickListener(null);
                ch.menuButton.setOnClickListener(null);
            }
            super.onViewRecycled(holder);
        }

        @Override
        public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof ContainerViewHolder) {
                ContainerViewHolder ch = (ContainerViewHolder) holder;
                final Container item = data.get(position - 1); // offset by 1 for add card
                ch.imageView.setImageResource(R.drawable.ic_containers);
                ch.title.setText(item.getName());

                ch.runButton.setOnClickListener(view -> runContainer(item));
                ch.editButton.setOnClickListener(view -> editContainer(item));
                ch.duplicateButton.setOnClickListener(view -> duplicateContainer(item));
                ch.menuButton.setOnClickListener(view -> showListItemMenu(view, item));
            }
        }

        private void runContainer(Container container) {
            final Context context = getContext();
            Intent intent = new Intent(context, XServerDisplayActivity.class);
            intent.putExtra("container_id", container.id);
            requireActivity().startActivity(intent);
        }

        private void editContainer(Container container) {
            new ContainerSettingsComposeDialog(requireActivity(), container,
                    ContainersFragment.this::loadContainersList).show();
        }

        private void duplicateContainer(Container container) {
            ContentDialog.confirm(getContext(), R.string.containers_list_confirm_duplicate, () -> {
                preloaderDialog.show(R.string.containers_list_duplicating, false);
                manager.duplicateContainerAsync(container, progress -> {
                    preloaderDialog.setProgress(progress);
                }, () -> {
                    preloaderDialog.setProgress(100);
                    preloaderDialog.closeWithDelay(600);
                    new android.os.Handler().postDelayed(() -> loadContainersList(), 600);
                });
            });
        }

        private void showListItemMenu(View anchorView, Container container) {
            final Context context = getContext();
            Context popupContext = new ContextThemeWrapper(context, R.style.ThemeOverlay_ContentPopupMenu);
            PopupMenu listItemMenu = new PopupMenu(popupContext, anchorView);
            listItemMenu.getMenuInflater().inflate(R.menu.container_popup_menu, listItemMenu.getMenu());
            listItemMenu.setForceShowIcon(true);

            listItemMenu.setOnMenuItemClickListener((menuItem) -> {
                switch (menuItem.getItemId()) {
                    case R.id.container_duplicate:
                        duplicateContainer(container);
                        break;
                    case R.id.container_backups:
                        showContainerBackupsDialog(container);
                        break;
                    case R.id.container_remove:
                        ContentDialog.confirm(getContext(), R.string.containers_list_confirm_remove, () -> {
                            preloaderDialog.show(R.string.containers_list_removing);
                            for (Shortcut shortcut : manager.loadShortcuts()) {
                                if (shortcut.container == container)
                                    ShortcutsFragment.disableShortcutOnScreen(context, shortcut);
                            }
                            manager.removeContainerAsync(container, () -> {
                                preloaderDialog.close();
                                loadContainersList();
                            });
                        });
                        break;
                    case R.id.container_info:
                        (new StorageInfoDialog(getActivity(), container)).show();
                        break;
                }
                return true;
            });
            listItemMenu.show();
        }
    }
}
