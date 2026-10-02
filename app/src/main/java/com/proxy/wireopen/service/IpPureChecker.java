package com.proxy.wireopen.service;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.proxy.wireopen.model.IpPureResult;
import com.proxy.wireopen.util.LogManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IpPureChecker {

    public static final String IPPURE_API_URL = "https://my.ippure.com/v1/info";
    public static final String IPPURE_REPORT_URL = "https://ippure.com/";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Gson gson = new Gson();

    public interface Callback {
        void onSuccess(IpPureResult result);
        void onError(String errorMessage);
    }

    private static void postToMain(Runnable runnable) {
        try {
            Looper looper = Looper.getMainLooper();
            if (looper != null) {
                new Handler(looper).post(runnable);
                return;
            }
        } catch (Throwable ignored) {}
        runnable.run();
    }

    public static void checkIpPureness(Callback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            try {
                LogManager.log("IpPureChecker", "正在请求 ippure.com API: " + IPPURE_API_URL);
                URL url = new URL(IPPURE_API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("User-Agent", "WireOpenProxy/1.0 (Android; M3)");
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    IpPureResult result = parseResult(response.toString());
                    LogManager.log("IpPureChecker", "ippure.com 检测完成: IP=" + result.getIp() +
                            ", 欺诈分=" + result.getFraudScore() + ", ASN=" + result.getAsn());

                    postToMain(() -> {
                        if (callback != null) callback.onSuccess(result);
                    });
                } else {
                    String err = "HTTP 错误码: " + code;
                    LogManager.log("IpPureChecker", err);
                    postToMain(() -> {
                        if (callback != null) callback.onError(err);
                    });
                }
            } catch (Exception e) {
                String errMsg = "检测失败: " + e.getMessage();
                LogManager.log("IpPureChecker", errMsg);
                postToMain(() -> {
                    if (callback != null) callback.onError(errMsg);
                });
            } finally {
                if (reader != null) {
                    try { reader.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }
            }
        });
    }

    public static IpPureResult parseResult(String json) {
        return gson.fromJson(json, IpPureResult.class);
    }
}
