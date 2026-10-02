package com.proxy.wireopen.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages global OpenVPN credentials.
 * Strictly applied ONLY to OpenVPN configs containing auth-user-pass.
 * WireGuard is completely isolated and will never touch this credential store.
 */
public class CredentialManager {
    private static final String PREF_NAME = "openvpn_credentials";
    private static final String KEY_USERNAME = "ovpn_username";
    private static final String KEY_PASSWORD = "ovpn_password";

    private static CredentialManager instance;
    private final SharedPreferences prefs;

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new CredentialManager(context.getApplicationContext());
        }
    }

    public static synchronized CredentialManager getInstance() {
        return instance;
    }

    private CredentialManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void saveCredentials(String username, String password) {
        prefs.edit()
                .putString(KEY_USERNAME, username != null ? username.trim() : "")
                .putString(KEY_PASSWORD, password != null ? password : "")
                .apply();
        LogManager.log("CredentialManager", "OpenVPN 专属凭据已安全落盘 (与 WireGuard 协议严格隔离)");
    }

    public synchronized String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    public synchronized String getPassword() {
        return prefs.getString(KEY_PASSWORD, "");
    }

    public synchronized boolean hasCredentials() {
        return !getUsername().isEmpty() && !getPassword().isEmpty();
    }

    public synchronized void clearCredentials() {
        prefs.edit().clear().apply();
        LogManager.log("CredentialManager", "OpenVPN 专属凭据已清除");
    }
}
