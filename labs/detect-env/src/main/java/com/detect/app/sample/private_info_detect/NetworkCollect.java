package com.detect.app.sample.private_info_detect;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkCollect {
    private static final String TAG = "NetworkInfoCollect";


    public static String getAllInfo(Context context) {
        Map<String, Object> result = allMapInfo(context);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        String resultStr = gson.toJson(result);

        Log.d(TAG, new GsonBuilder().create().toJson(result));

        return resultStr;
    }


    public static Map<String, Object> allMapInfo(Context context) {
        Map<String, Object> result = new HashMap<>();
        result.put("WifiListInfo", getWifiListInfo(context));
        result.put("NetworkInfo", getNetworkInfo(context));
        result.put("TelephonyManagerInfo", getTelephonyManagerInfo(context));
        result.put("ConnectivityManagerInfo", getConnectivityManagerInfo(context));
        return result;
    }

    /**
     * This method is used to gather various network related information from the device.
     * It collects data such as network type, state, subtype, connection status, availability, roaming status, detailed state, extra info,
     * downstream bandwidth and upstream bandwidth.
     * The collected data is then stored in a Map and returned.
     *
     * @param context The context of the caller
     * @return A Map containing key-value pairs of the collected network information
     */
    private static Map<String, Object> getNetworkInfo(Context context) {
        Map<String, Object> result = new HashMap<>();
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());

        // Check if there is an active network
        if (activeNetwork != null) {
            // Collect various network related information
            result.put("network_type", activeNetwork.getTypeName());
            result.put("network_state", activeNetwork.getState());
            result.put("network_subtype", activeNetwork.getSubtypeName());
            result.put("network_connected", activeNetwork.isConnected());
            result.put("network_available", activeNetwork.isAvailable());
            result.put("network_roaming", activeNetwork.isRoaming());
            result.put("network_detailed_state", activeNetwork.getDetailedState().toString());
            result.put("network_extra_info", activeNetwork.getExtraInfo());
        }

        // Check if there are network capabilities
        if (nc != null) {
            // Collect downstream and upstream bandwidth information
            result.put("network_downstream_bandwidth", nc.getLinkDownstreamBandwidthKbps());
            result.put("network_upstream_bandwidth", nc.getLinkUpstreamBandwidthKbps());
        }

        return result;
    }

    /**
     * This method is used to gather various network related information from the device.
     * It collects data such as network type, state, subtype, connection status, availability, roaming status, detailed state, extra info,
     * downstream bandwidth and upstream bandwidth.
     * The collected data is then stored in a List of Maps and returned.
     *
     * @param context The context of the caller
     * @return A List of Maps containing key-value pairs of the collected network information
     */
    private static List<Map<String, String>> getNetworkInfoList(Context context) {
        List<Map<String, String>> result = new ArrayList<>();
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());

        // Check if there is an active network
        if (activeNetwork != null) {
            // Collect various network related information
            Map<String, String> network = new HashMap<>();
            network.put("network_type", activeNetwork.getTypeName());
            network.put("network_state", activeNetwork.getState().toString());
            network.put("network_subtype", activeNetwork.getSubtypeName());
            network.put("network_connected", String.valueOf(activeNetwork.isConnected()));
            network.put("network_available", String.valueOf(activeNetwork.isAvailable()));
            network.put("network_roaming", String.valueOf(activeNetwork.isRoaming()));
            network.put("network_detailed_state", activeNetwork.getDetailedState().toString());
            network.put("network_extra_info", activeNetwork.getExtraInfo());
            result.add(network);
        }

        // Check if there are network capabilities
        if (nc != null) {
            // Collect downstream and upstream bandwidth information
            Map<String, String> bandwidth = new HashMap<>();
            bandwidth.put("network_downstream_bandwidth", String.valueOf(nc.getLinkDownstreamBandwidthKbps()));
            bandwidth.put("network_upstream_bandwidth", String.valueOf(nc.getLinkUpstreamBandwidthKbps()));
            result.add(bandwidth);
        }

        return result;
    }

    /**
     * This method is used to gather information about the WiFi networks in the vicinity of the device.
     * It collects data such as SSID, BSSID and signal strength of the WiFi networks.
     * The collected data is then stored in a List of Maps and returned.
     *
     * @param context The context of the caller
     * @return A List of Maps containing key-value pairs of the collected WiFi network information
     */

    private static List<Map<String, String>> getWifiListInfo(Context context) {
        List<Map<String, String>> wifiList = new ArrayList<>();
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        // 开始扫描WiFi网络
        wifiManager.startScan();
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "getWifiList:  权限不支持");
        } else {
            @SuppressLint("MissingPermission")
            List<ScanResult> scanResults = wifiManager.getScanResults();

            for (ScanResult scanResult : scanResults) {
                Map<String, String> wifi = new HashMap<>();
                wifi.put("SSID", scanResult.SSID);
                wifi.put("BSSID", scanResult.BSSID);
                wifi.put("Signal Strength", String.valueOf(scanResult.level));
                wifiList.add(wifi);
            }
        }
        return wifiList;
    }


    private static Map<String, Object> getTelephonyManagerInfo(Context context) {
        Map<String, Object> result = new HashMap<>();
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        result.put("carrier_name", tm.getNetworkOperatorName());
        result.put("country_code", tm.getNetworkCountryIso());
        result.put("sim_country_code", tm.getSimCountryIso());
        result.put("sim_operator", tm.getSimOperator());
        result.put("sim_operator_name", tm.getSimOperatorName());

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            result.put("phone_type", tm.getPhoneType());
            result.put("network_operator", tm.getNetworkOperator());
            result.put("network_type_telephony", tm.getNetworkType());
        }

        return result;
    }


    private static Map<String, Object> getConnectivityManagerInfo(Context context) {
        Map<String, Object> result = new HashMap<>();
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        if (activeNetwork != null) {
            result.put("network_type", activeNetwork.getTypeName());
            result.put("network_state", activeNetwork.getState());
            result.put("network_subtype", activeNetwork.getSubtypeName());
            result.put("network_connected", activeNetwork.isConnected());
        }

        return result;
    }


}
