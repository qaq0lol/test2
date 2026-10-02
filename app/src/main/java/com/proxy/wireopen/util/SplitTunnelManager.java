package com.proxy.wireopen.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages split tunneling routing modes:
 * - GLOBAL: routes all applications
 * - BYPASS: bypasses selected packages (direct local internet)
 * - ALLOW: only routes selected packages through VPN
 */
public class SplitTunnelManager {
    private static final String TAG = "SplitTunnelManager";
    private static final String PREF_NAME = "wireopen_routing_prefs";
    private static final String KEY_ROUTING_MODE = "routing_mode";
    private static final String KEY_SELECTED_PACKAGES = "selected_packages";

    public static final String MODE_GLOBAL = "global";
    public static final String MODE_BYPASS = "bypass";
    public static final String MODE_ALLOW = "allow";

    public static String getRoutingMode(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_ROUTING_MODE, MODE_BYPASS);
    }

    public static void setRoutingMode(Context context, String mode) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_ROUTING_MODE, mode).apply();
    }

    public static Set<String> getSelectedPackages(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return new HashSet<>(sp.getStringSet(KEY_SELECTED_PACKAGES, new HashSet<>()));
    }

    public static void setSelectedPackages(Context context, Set<String> packages) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putStringSet(KEY_SELECTED_PACKAGES, new HashSet<>(packages)).apply();
    }

    public static void togglePackage(Context context, String packageName, boolean selected) {
        Set<String> set = getSelectedPackages(context);
        if (selected) {
            set.add(packageName);
        } else {
            set.remove(packageName);
        }
        setSelectedPackages(context, set);
    }

    public static void applyRoutingRules(Context context, VpnService.Builder builder) {
        String mode = getRoutingMode(context);
        Set<String> packages = getSelectedPackages(context);

        Log.i(TAG, "Applying split-tunnel rules: mode=" + mode + ", selectedCount=" + packages.size());

        if (MODE_GLOBAL.equalsIgnoreCase(mode)) {
            // All traffic goes through VPN
            return;
        }

        if (MODE_BYPASS.equalsIgnoreCase(mode)) {
            for (String pkg : packages) {
                try {
                    builder.addDisallowedApplication(pkg);
                } catch (Exception e) {
                    Log.w(TAG, "Cannot bypass package " + pkg + ": " + e.getMessage());
                }
            }
        } else if (MODE_ALLOW.equalsIgnoreCase(mode)) {
            // Only allowed packages
            boolean hasAllowed = false;
            for (String pkg : packages) {
                try {
                    builder.addAllowedApplication(pkg);
                    hasAllowed = true;
                } catch (Exception e) {
                    Log.w(TAG, "Cannot allow package " + pkg + ": " + e.getMessage());
                }
            }
            // Always allow the proxy app itself so the built-in IP Pureness WebView routes through VPN
            try {
                builder.addAllowedApplication(context.getPackageName());
            } catch (Exception e) {
                Log.w(TAG, "Cannot allow self package: " + e.getMessage());
            }
            if (!hasAllowed) {
                Log.w(TAG, "Allow mode active with no third-party packages allowed, only self proxied.");
            }
        }
    }
}
