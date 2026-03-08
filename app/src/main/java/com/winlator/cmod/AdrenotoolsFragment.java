/* Components/Drivers screen: manages Adreno driver download progress and install actions. */
package com.winlator.cmod;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import com.winlator.cmod.R;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.contents.Downloader;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.DownloadProgressDialog;

import java.io.File;
import java.util.ArrayList;

public class AdrenotoolsFragment extends Fragment {
    
    private AdrenotoolsManager adrenotoolsManager;
    private RecyclerView recyclerView;
    
    @Override 
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.adrenotoolsManager = new AdrenotoolsManager(getActivity());
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup layout = (ViewGroup)inflater.inflate(R.layout.adrenotools_fragment, container, false);
        recyclerView = layout.findViewById(R.id.RecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(new DriversAdapter(adrenotoolsManager.enumarateInstalledDrivers()));
        View btInstallDriver = layout.findViewById(R.id.BTInstallDriver);
        btInstallDriver.setOnClickListener((v) -> {
            ContentDialog.confirm(getContext(), getString(R.string.install_drivers_message) + " " + getString(R.string.install_drivers_warning), () -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                getActivity().startActivityFromFragment(this, intent, MainActivity.OPEN_FILE_REQUEST_CODE);               
            });
        });

        View btDownloadDriver = layout.findViewById(R.id.BTDownloadDriver);
        TextView etDownloadURL = layout.findViewById(R.id.ETDriverDownloadURL);
        btDownloadDriver.setOnClickListener(v -> {
            String url = etDownloadURL.getText().toString().trim();
            if (url.isEmpty()) {
                AppUtils.showToast(getContext(), "Please enter a URL.");
                return;
            }

            DownloadProgressDialog downloadDialog = new DownloadProgressDialog(getActivity());
            downloadDialog.show("Downloading Driver...");

            new Thread(() -> {
                File output = new File(getContext().getCacheDir(), "driver_temp_" + System.currentTimeMillis() + ".zip");
                boolean success = Downloader.downloadFile(url, output, (downloadedBytes, totalBytes) -> {
                    int percent = totalBytes > 0 ? (int)((downloadedBytes * 100) / totalBytes) : 0;
                    getActivity().runOnUiThread(() -> downloadDialog.setProgress(percent));
                });

                getActivity().runOnUiThread(() -> {
                    downloadDialog.close();
                    if (success) {
                        String driver = adrenotoolsManager.installDriver(Uri.fromFile(output));
                        if (!driver.isEmpty()) {
                            ((DriversAdapter)recyclerView.getAdapter()).addItem(driver);
                            AppUtils.showToast(getContext(), "Driver installed successfully.");
                        } else {
                            AppUtils.showToast(getContext(), "Failed to install downloaded driver.");
                        }
                        output.delete();
                    } else {
                        AppUtils.showToast(getContext(), "Download failed.");
                    }
                });
            }).start();
        });

        return layout;
    }
    
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getActivity() != null && ((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.drivers);
        }
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MainActivity.OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            String driver = adrenotoolsManager.installDriver(uri);
            if (!driver.isEmpty())
                ((DriversAdapter)recyclerView.getAdapter()).addItem(driver);
        }
     }       
    
    private class DriversAdapter extends RecyclerView.Adapter<DriversAdapter.ViewHolder> {
        private ArrayList<String> driversList;

        public class ViewHolder extends RecyclerView.ViewHolder {
            private TextView tvName;
            private TextView tvVersion;
            private ImageButton btMenu;

            public ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.TVName);
                tvVersion = v.findViewById(R.id.TVVersion);
                btMenu = v.findViewById(R.id.BTMenu);
            }
        }
        
        public DriversAdapter(ArrayList<String> driversList) {
            this.driversList = driversList;
        }
        
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.adrenotools_list_item, viewGroup, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(ViewHolder viewHolder, final int position) {
            viewHolder.tvName.setText(adrenotoolsManager.getDriverName(driversList.get(position)));
            viewHolder.tvVersion.setText(adrenotoolsManager.getDriverVersion(driversList.get(position)));
            viewHolder.btMenu.setOnClickListener((v) -> {
                removeAtIndex(position);
            });
        }
        
        public void addItem(String item) {
            driversList.add(item);
            notifyItemInserted(getItemCount() - 1);
        }
        
        public void removeAtIndex(int index) {
            String deletedDriver = driversList.remove(index);
            adrenotoolsManager.removeDriver(deletedDriver);
            notifyItemRemoved(index);
            notifyItemRangeChanged(index, getItemCount());
        }
        
        @Override
        public int getItemCount() {
            return driversList.size();
        }
    }
}
