package com.proxy.wireopen.util;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Log manager providing in-memory log buffer and listener registration.
 *
 * The listener is stored as a WeakReference to avoid retaining Activity instances
 * after they are destroyed (e.g. on screen rotation), which would cause memory leaks.
 */
public class LogManager {
    private static final int MAX_LOG_LINES = 500;
    private static final List<String> logs = new ArrayList<>();
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    // Fix: use WeakReference so the static field cannot prevent GC of the Activity.
    private static WeakReference<LogListener> listenerRef = null;

    public interface LogListener {
        void onNewLog(String logEntry);
    }

    public static synchronized void setListener(LogListener l) {
        listenerRef = (l != null) ? new WeakReference<>(l) : null;
    }

    public static synchronized void log(String tag, String message) {
        String timestamp = timeFormat.format(new Date());
        String entry = "[" + timestamp + "] [" + tag + "] " + message;
        if (logs.size() >= MAX_LOG_LINES) {
            logs.remove(0);
        }
        logs.add(entry);
        if (listenerRef != null) {
            LogListener l = listenerRef.get();
            if (l != null) {
                l.onNewLog(entry);
            } else {
                // Listener was GC'd — clean up the dead reference
                listenerRef = null;
            }
        }
    }

    public static synchronized List<String> getLogs() {
        return new ArrayList<>(logs);
    }

    public static synchronized void clear() {
        logs.clear();
    }
}
