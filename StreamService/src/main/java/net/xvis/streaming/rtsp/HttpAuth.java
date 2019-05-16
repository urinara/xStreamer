package net.xvis.streaming.rtsp;

import android.util.Base64;
import android.util.Log;

public class HttpAuth {
    private static final String TAG = "HttpAuth";
    public static final String BASIC = "Basic";
    public static final String DIGEST = "Digest";

    public static String getBasicCredentials(String userId, String password) {
        String decodedCredentials = userId + ":" + password;
        return Base64.encodeToString(decodedCredentials.getBytes(), Base64.DEFAULT);
    }

    public static RtspResponse checkAuthorization(RtspRequest rtspRequest, String userId, String password) {
        Log.e(TAG, "checking authorization");
        RtspResponse rtspResponse = new RtspResponse(rtspRequest);

        if (userId == null || password.isEmpty()) {
            Log.e(TAG, "Server's userId is null or password is empty -> Authorized by default.");
            rtspResponse.setStatus(RtspResponse.STATUS_200_OK);
            return rtspResponse;
        }

        String auth = rtspRequest.getValue(RtspHeader.AUTHORIZATION);
        if (auth == null || auth.isEmpty()) {
            Log.e(TAG, "Client auth is not provide -> Authorization failed");
            rtspResponse.setStatus(RtspResponse.STATUS_401_UNAUTHORIZED);
            return rtspResponse;
        }

        String scheme;
        if (auth.startsWith(BASIC)) {
            scheme = BASIC;
            String field = RtspHeader.WWW_AUTHENTICATE;
            String value = scheme + " realm=\"servername\"";
            rtspResponse.addHeader(field, value);
            rtspResponse.setStatus(RtspResponse.STATUS_401_UNAUTHORIZED);

            String received = auth.substring(auth.lastIndexOf(" ") + 1);
            String localCredentials = getBasicCredentials(userId, password);
            if (localCredentials.equals(received)) {
                rtspResponse.setStatus(RtspResponse.STATUS_200_OK);
                return rtspResponse;
            }

        } else if (auth.startsWith(DIGEST)) {
            scheme = DIGEST;
        }


        return rtspResponse;
    }
}
