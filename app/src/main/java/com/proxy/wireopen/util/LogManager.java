package com.proxy.wireopen.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Log manager providing in-memory log buffer and listener registration.
 */
public class LogManager {
    private static final int MAX_LOG_LINES = 500;
    private static final List<String> logs = new ArrayList<>();
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private static LogListener listener;

    public interface LogListener {
        void onNewLog(String logEntry);
    }

    public static synchronized void setListener(LogListener l) {
        listener = l;
    }

    public static synchronized void log(String tag, String message) {
        String timestamp = timeFormat.format(new Date());
        String entry = "[" + timestamp + "] [" + tag + "] " + message;
        if (logs.size() >= MAX_LOG_LINES) {
            logs.remove(0);
        }
        logs.add(entry);
        if (listener != null) {
            listener.onNewLog(entry);
        }
    }

    public static synchronized List<String> getLogs() {
        return new ArrayList<>(logs);
    }

    public static synchronized void clear() {
        logs.clear();
    }
}
