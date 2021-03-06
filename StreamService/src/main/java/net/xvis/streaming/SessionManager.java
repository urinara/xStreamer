package net.xvis.streaming;

import java.net.URI;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class SessionManager {
    private static String TAG = "SessionManager";
    private static Map<String, Session> sessionMap = new HashMap<>();
    private static Map<String, Session> sessionIdMap = new HashMap<>();

    private SessionManager() { }

    public synchronized static Session findSession(URI resourceUri) {
        Log.e(TAG, "finding a session: " + resourceUri.getPath());
        return sessionMap.get(resourceUri.getPath());
    }

    public synchronized static Session findSession(String sessionId) {
        Log.e(TAG, "finding a session: " + sessionId);
        return sessionIdMap.get(sessionId);
    }

    public synchronized static boolean addSession(Session newSession) {
        String path = newSession.getUri().getPath();
        if (path == null || path.isEmpty()) {
            return false;
        }
        Log.e(TAG, "Adding session: " + path);
        sessionMap.put(path, newSession);
        return false;
    }

    public synchronized static boolean removeSession(URI sessionUri) {
        Session session = sessionMap.get(sessionUri.getPath());
        if (session == null) {
            return false;
        }

        if (session.isStreaming()) {
            return false;
        }

        sessionMap.remove(sessionUri.getPath());
        return true;
    }

}
