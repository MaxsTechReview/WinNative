package com.winlator.cmod;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.StorageInfoDialog;
import com.winlator.cmod.core.PreloaderDialog;
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
        setHasOptionsMenu(true);
        preloaderDialog = new PreloaderDialog(getActivity());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getActivity() instanceof AppCompatActivity) {
            androidx.appcompat.app.ActionBar actionBar =
                    ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(R.string.containers);
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
            Toast.makeText(getContext(), "System image not installed. Please install components first.", Toast.LENGTH_LONG).show();
            return;
        }
        FragmentManager fragmentManager = getParentFragmentManager();
        fragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.settings_enter, R.anim.settings_exit, R.anim.settings_enter, R.anim.settings_exit)
                .addToBackStack(null)
                .replace(R.id.FLFragmentContainer, new ContainerDetailFragment())
                .commit();
    }

    private void loadContainersList() {
        ArrayList<Container> containers = manager.getContainers();
        recyclerView.setAdapter(new ContainersAdapter(containers));
        emptyTextView.setVisibility(containers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        // Menu removed to clean up header bar
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.containers_menu_add:
                openAddContainer();
                return true;

            default:
                return super.onOptionsItemSelected(menuItem);
        }
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
            FragmentManager fragmentManager = getParentFragmentManager();
            fragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.settings_enter, R.anim.settings_exit, R.anim.settings_enter, R.anim.settings_exit)
                    .addToBackStack(null)
                    .replace(R.id.FLFragmentContainer, new ContainerDetailFragment(container.id))
                    .commit();
        }

        private void duplicateContainer(Container container) {
            ContentDialog.confirm(getContext(), R.string.do_you_want_to_duplicate_this_container, () -> {
                preloaderDialog.show(R.string.duplicating_container, false);
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
                    case R.id.container_remove:
                        ContentDialog.confirm(getContext(), R.string.do_you_want_to_remove_this_container, () -> {
                            preloaderDialog.show(R.string.removing_container);
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
