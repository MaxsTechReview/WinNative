package com.winlator.cmod.runtime.display.environment.components;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.system.OsConstants;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Queries Android's {@link ConnectivityManager} for the active network's
 * interface addresses. {@link NetworkInfoUpdateComponent} writes the result
 * into the Wine prefix so Wine's iphlpapi/ws2_32 (which on Android cannot
 * enumerate interfaces directly) reports a live network — without this,
 * steam.exe's Network List Manager check returns "offline" and Steam aborts
 * with "Could not connect to Steam network" before ever attempting a CM
 * connection. Ported from the GameNative reference (com.winlator.core).
 */
public class NetworkHelper {
    private final ConnectivityManager connectivityManager;

    public static class IFAddress {
        public String name = "eth0";
        public int flags = 0;
        public int family = OsConstants.AF_INET;
        public int scopeId = 0;
        public String address = "0";
        public String netmask = "0";

        @Override
        public String toString() {
            return name + "," + flags + "," + family + "," + scopeId + "," + address + "," + netmask;
        }
    }

    public NetworkHelper(Context context) {
        this.connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public String getIPv4Address() {
        if (!isConnected()) return null;
        Network activeNetwork = connectivityManager.getActiveNetwork();
        LinkProperties linkProperties = connectivityManager.getLinkProperties(activeNetwork);
        if (linkProperties == null) return null;
        for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
            InetAddress address = linkAddress.getAddress();
            if (address instanceof Inet4Address) return address.getHostAddress();
        }
        return null;
    }

    public List<IFAddress> getIFAddresses() {
        ArrayList<IFAddress> result = new ArrayList<>();
        Network activeNetwork = connectivityManager.getActiveNetwork();
        LinkProperties linkProperties =
                activeNetwork != null ? connectivityManager.getLinkProperties(activeNetwork) : null;
        if (linkProperties == null) return result;
        for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
            InetAddress address = linkAddress.getAddress();
            if ((address instanceof Inet4Address) || (address instanceof Inet6Address)) {
                IFAddress ifAddress = new IFAddress();
                if (address instanceof Inet6Address) {
                    ifAddress.family = OsConstants.AF_INET6;
                    ifAddress.scopeId = ((Inet6Address) address).getScopeId();
                }
                ifAddress.address = address.getHostAddress();
                ifAddress.netmask = formatNetmask(linkAddress.getPrefixLength());
                ifAddress.flags = OsConstants.IFF_UP | OsConstants.IFF_RUNNING;
                result.add(ifAddress);
            }
        }
        return result;
    }

    public static String formatIpAddress(int ipAddress) {
        return (ipAddress & 255) + "." + ((ipAddress >> 8) & 255) + "."
                + ((ipAddress >> 16) & 255) + "." + ((ipAddress >> 24) & 255);
    }

    public static String formatNetmask(int prefixLength) {
        switch (prefixLength) {
            case 8:  return "255.0.0.0";
            case 16: return "255.255.0.0";
            case 24: return "255.255.255.0";
            case 32: return "255.255.255.255";
            case 64: return "ffff:ffff:ffff:ffff::";
            default: return "";
        }
    }

    public boolean isConnected() {
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null) return false;
        int type = networkInfo.getType();
        if (networkInfo.isAvailable() && networkInfo.isConnectedOrConnecting()) {
            return type == ConnectivityManager.TYPE_WIFI
                    || type == ConnectivityManager.TYPE_ETHERNET
                    || type == ConnectivityManager.TYPE_MOBILE;
        }
        return false;
    }
}
