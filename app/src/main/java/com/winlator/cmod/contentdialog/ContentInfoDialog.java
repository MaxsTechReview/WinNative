/* Components screen detail dialog for viewing content metadata, files, and install location. */
package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.R;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;

import java.util.Collections;
import java.util.List;

public class ContentInfoDialog extends ContentDialog {
    public ContentInfoDialog(Context context, ContentProfile profile) {
        super(context, R.layout.content_info_dialog);
        setTitle(R.string.content_info);

        TextView tvType = findViewById(R.id.TVType);
        TextView tvVersion = findViewById(R.id.TVVersion);
        TextView tvVersionCode = findViewById(R.id.TVVersionCode);
        TextView tvDescription = findViewById(R.id.TVDesc);
        View descriptionSection = findViewById(R.id.LLDescriptionSection);
        TextView tvCancel = findViewById(R.id.BTCancel);
        TextView tvConfirm = findViewById(R.id.BTConfirm);
        View installPathSection = findViewById(R.id.LLInstallPathSection);
        TextView tvInstallPath = findViewById(R.id.TVInstallPath);
        View fileListSection = findViewById(R.id.LLFileListSection);
        RecyclerView recyclerView = findViewById(R.id.recyclerView);

        if (tvCancel != null) tvCancel.setText(R.string.cancel);
        if (tvConfirm != null) tvConfirm.setText(R.string.ok);

        tvType.setText(profile.type.toString());
        tvVersion.setText(profile.verName);
        tvVersionCode.setText(String.valueOf(profile.verCode));

        boolean hasDescription = profile.desc != null && !profile.desc.trim().isEmpty();
        if (descriptionSection != null) {
            descriptionSection.setVisibility(hasDescription ? View.VISIBLE : View.GONE);
        }
        if (hasDescription) {
            tvDescription.setText(profile.desc);
        }

        if (profile.isInstalled) {
            if (installPathSection != null) {
                installPathSection.setVisibility(View.VISIBLE);
            }
            if (tvInstallPath != null) {
                tvInstallPath.setText(ContentsManager.getInstallDir(context, profile).getAbsolutePath());
            }
            if (fileListSection != null) {
                fileListSection.setVisibility(View.GONE);
            }
        } else {
            if (installPathSection != null) {
                installPathSection.setVisibility(View.GONE);
            }

            List<ContentProfile.ContentFile> fileList =
                    profile.fileList != null ? profile.fileList : Collections.emptyList();
            if (fileListSection != null) {
                fileListSection.setVisibility(fileList.isEmpty() ? View.GONE : View.VISIBLE);
            }
            if (!fileList.isEmpty()) {
                recyclerView.setAdapter(new ContentInfoFileAdapter(fileList));
                recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
            }
        }

    }

    public static class ContentInfoFileAdapter extends RecyclerView.Adapter<ContentInfoFileAdapter.ViewHolder> {
        private static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView tvSource;
            private final TextView tvtarget;

            private ViewHolder(View view) {
                super(view);
                tvSource = view.findViewById(R.id.TVFileSource);
                tvtarget = view.findViewById(R.id.TVFileTarget);
            }
        }

        private final List<ContentProfile.ContentFile> data;

        public ContentInfoFileAdapter(List<ContentProfile.ContentFile> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ContentInfoFileAdapter.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.content_file_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.tvSource.setText(data.get(position).source);
            holder.tvtarget.setText(data.get(position).target);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }
}
