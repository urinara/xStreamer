package net.xvis.streaming.rtsp;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.SocketException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RtspRequest extends RtspHeader {
    private static final String TAG = "RtspRequest";

    private static final Pattern regexMethod = Pattern.compile("(\\w+) (\\S+) (\\S+)");
    private static final Pattern regexHeader = Pattern.compile("(\\S+):(.+)");

    private String method = "";
    private String uri = "";
    private String version = "";

    public static RtspRequest waitForRequest(BufferedReader input) throws IOException {
        Log.e(TAG, "----> waiting for request...");
        RtspRequest request = new RtspRequest();

        // Try get the request line
        String requestLine = input.readLine();
        if (requestLine == null) {
            Log.e(TAG, "<---- requestLine is null");
            throw new SocketException("Client disconnected");
        }
        Log.e(TAG, "C->S: " + requestLine);
        Matcher methodMatcher = regexMethod.matcher(requestLine);
        if (methodMatcher.find()) {
            request.method = methodMatcher.group(1);
            if (request.method == null) {
                request.method = "";
            }
            request.uri = methodMatcher.group(2);
            if (request.uri == null) {
                request.uri = "";
            }
            request.version = methodMatcher.group(3);
            if (request.version == null) {
                request.version = "";
            }
        } else {
            return null;
        }

        // Parse the following headers
        String header = input.readLine();
        while (header != null && header.length() > 3) {
            Log.e(TAG, "C->S: " + header);
            Matcher requestMatcher = regexHeader.matcher(header);
            if (requestMatcher.find()) {
                String field = requestMatcher.group(1);
                String value = requestMatcher.group(2);
                request.addHeader(field, value);
            } else {
                Log.e(TAG, "---> invalid request");
                break;
            }
            header = input.readLine();
        }

        if (header == null) {
            Log.e(TAG, "<---- header is null");
            throw new SocketException("Client disconnected");
        }

        Log.e(TAG, "<---- end of request waiting");
        return request;
    }

    public String getMethod() {
        return method;
    }

    public String getUri() {
        return uri;
    }

    public String getVersion() {
        return version;
    }
}
