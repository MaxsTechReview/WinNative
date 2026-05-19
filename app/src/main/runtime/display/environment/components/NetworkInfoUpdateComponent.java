package com.winlator.cmod.runtime.display.environment.components;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.util.Log;

import com.winlator.cmod.runtime.display.environment.EnvironmentComponent;
import com.winlator.cmod.shared.io.FileUtils;

import java.io.File;
import java.util.List;

/**
 * Publishes the host device's active-network interface addresses into the
 * Wine prefix so Wine reports a live network. Winlator's Wine reads the
 * interface list from {@code <tmpDir>/ifaddrs} (Android sandboxes direct
 * interface enumeration). Without this file, Wine's iphlpapi reports zero
 * interfaces; steam.exe's startup connectivity / Network List Manager check
 * then concludes the machine is offline and shows "Could not connect to
 * Steam network" without ever attempting a CM connection.
 *
 * Ported from the GameNative reference. Re-runs on CONNECTIVITY_ACTION so a
 * Wi-Fi change mid-session is reflected.
 */
public class NetworkInfoUpdateComponent extends EnvironmentComponent {
    private static final String TAG = "NetworkInfoUpdateComponent";
    private BroadcastReceiver broadcastReceiver;

    @Override
    public void start() {
        Log.d(TAG, "Starting...");
        Context context = environment.getContext();
        final NetworkHelper networkHelper = new NetworkHelper(context);
        updateIFAddrsFile(networkHelper.getIFAddresses());
        updateEtcHostsFile(networkHelper.getIPv4Address());

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                updateIFAddrsFile(networkHelper.getIFAddresses());
                updateEtcHostsFile(networkHelper.getIPv4Address());
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(broadcastReceiver, filter);
    }

    @Override
    public void stop() {
        Log.d(TAG, "Stopping...");
        if (broadcastReceiver != null) {
            try {
                environment.getContext().unregisterReceiver(broadcastReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister broadcast receiver: " + e);
            }
            broadcastReceiver = null;
        }
    }

    public void updateIFAddrsFile(List<NetworkHelper.IFAddress> ifAddresses) {
        File file = new File(environment.getImageFs().getTmpDir(), "ifaddrs");
        StringBuilder content = new StringBuilder();
        if (!ifAddresses.isEmpty()) {
            for (NetworkHelper.IFAddress ifAddress : ifAddresses) {
                if (content.length() > 0) content.append("\n");
                content.append(ifAddress.toString());
            }
        } else {
            content.append(new NetworkHelper.IFAddress().toString());
        }
        FileUtils.writeString(file, content.toString());
    }

    public void updateEtcHostsFile(String ipAddress) {
        String ip = ipAddress != null ? ipAddress : "127.0.0.1";
        File file = new File(environment.getImageFs().getRootDir(), "etc/hosts");
        FileUtils.writeString(file, ip + "\tlocalhost\n");
    }
}
