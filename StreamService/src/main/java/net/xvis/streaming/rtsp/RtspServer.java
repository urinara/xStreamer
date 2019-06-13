package net.xvis.streaming.rtsp;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Binder;
import android.util.Log;

import net.xvis.streaming.Session;
import net.xvis.streaming.SessionManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RtspServer {

    public final static String TAG = "RtspServer";
    public static String SERVER_NAME = "XVIS RTSP Server";
    public static final int DEFAULT_RTSP_PORT = 8086; // default: 554, RTSPS default = 322

    // RTSP header
    public final static int ERROR_BIND_FAILED = 0x00;
    public final static int ERROR_START_FAILED = 0x01;
    public final static int MESSAGE_STREAMING_STARTED = 0X00;
    public final static int MESSAGE_STREAMING_STOPPED = 0X01;

    public final static String KEY_ENABLED = "rtsp_enabled";
    public final static String KEY_PORT = "rtsp_port";

    protected SharedPreferences mSharedPreferences;
    protected int rtspPort = DEFAULT_RTSP_PORT;
    protected WeakHashMap<Session, Object> mSessions = new WeakHashMap<>(2);

    private RequestListener requestListener;
    private boolean restart = false;
    private final LinkedList<CallbackListener> mListeners = new LinkedList<>();

    private String mUsername;
    private String mPassword;

    public RtspServer() { }

    /**
     * Be careful: those callbacks won't necessarily be called from the ui thread !
     */
    public interface CallbackListener {

        /**
         * Called when an error occurs.
         */
        void onError(RtspServer server, Exception e, int error);

        /**
         * Called when streaming starts/stops.
         */
        void onMessage(RtspServer server, int message);
    }

    /**
     * See {@link CallbackListener} to check out what events will be fired once you set up a listener.
     *
     * @param listener The listener
     */
    public void addCallbackListener(CallbackListener listener) {
        synchronized (mListeners) {
            if (!mListeners.isEmpty()) {
                for (CallbackListener cl : mListeners) {
                    if (cl == listener) return;
                }
            }
            mListeners.add(listener);
        }
    }

    /**
     * Removes the listener.
     *
     * @param listener The listener
     */
    public void removeCallbackListener(CallbackListener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    /**
     * Returns the port used by the RTSP server.
     */
    public int getPort() {
        return rtspPort;
    }

    /**
     * Sets the port for the RTSP server to use.
     *
     * @param port The port
     */
    public void setPort(int port) {
        Editor editor = mSharedPreferences.edit();
        editor.putString(KEY_PORT, String.valueOf(port));
        editor.commit();
    }

    /**
     * Set Basic authorization to access RTSP Stream
     *
     * @param username username
     * @param password password
     */
    public void setAuthorization(String username, String password) {
        mUsername = username;
        mPassword = password;
    }

    synchronized public void start() throws IOException {
        if (restart) {
            Log.d(TAG, "Restarting RequestListener");
            stop();
        }

        if (requestListener == null) {
            Log.d(TAG, "RequestListener starting...");
            requestListener = new RequestListener();
            requestListener.startListening();
            Log.d(TAG, "RequestListener started.");
        } else {
            Log.d(TAG, "RequestListener started already.");
        }

        restart = false;
    }

    synchronized public void stop() {
        if (requestListener == null) {
            Log.e(TAG, "RequestListener not started before");
            return;
        }

        Log.d(TAG, "RequestListener stopListening...");
        try {
            requestListener.stopListening();
            for (Session session : mSessions.keySet()) {
                if (session != null && session.isStreaming()) {
                    //session.stop();
                }
            }
        } catch (Exception ignore) {
        } finally {
            requestListener = null;
            Log.d(TAG, "RequestListener stopped.");
        }
    }

    public boolean isStreaming() {
        for (Session session : mSessions.keySet()) {
            if (session != null && session.isStreaming()) {
                return true;
            }
        }
        return false;
    }

    public long getBitrate() {
        long bitrate = 0;
        for (Session session : mSessions.keySet()) {
            if (session != null && session.isStreaming()) {
                //bitrate += session.getBitrate();
            }
        }
        return bitrate;
    }

//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        return START_STICKY;
//    }
//
//    @Override
//    public void onCreate() {
//        Log.e(TAG, "onCreate");
//        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
//        mPort = Integer.parseInt(mSharedPreferences.getString(KEY_PORT, String.valueOf(mPort)));
//
//        // If the configuration is modified, the server will adjust
//        mSharedPreferences.registerOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
//
//        start();
//    }
//
//    @Override
//    public void onDestroy() {
//        stop();
//        Log.e(TAG, "onDestroy");
//        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
//    }

//    private OnSharedPreferenceChangeListener mOnSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
//        @Override
//        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
//            if (key.equals(KEY_PORT)) {
//                int port = Integer.parseInt(sharedPreferences.getString(KEY_PORT, String.valueOf(rtspPort)));
//                if (port != rtspPort) {
//                    rtspPort = port;
//                    restart = true;
//                    start();
//                }
//            }
//        }
//    };

    /**
     * The Binder you obtain when a connection with the Service is established.
     */
    public class LocalBinder extends Binder {
        public RtspServer getService() {
            return RtspServer.this;
        }
    }

//    @Override
//    public IBinder onBind(Intent intent) {
//        return mBinder;
//    }

    protected void postMessage(int id) {
        synchronized (mListeners) {
            if (!mListeners.isEmpty()) {
                for (CallbackListener cl : mListeners) {
                    cl.onMessage(this, id);
                }
            }
        }
    }

    protected void postError(Exception exception, int id) {
        synchronized (mListeners) {
            if (!mListeners.isEmpty()) {
                for (CallbackListener cl : mListeners) {
                    cl.onError(this, exception, id);
                }
            }
        }
    }

    protected Session handleRequest(String uri, Socket client) throws IllegalStateException, IOException {
        Session session = UriParser.parse(uri);
        //session.setOrigin(client.getLocalAddress().getHostAddress());
        if (session.getDestination() == null) {
            //session.setDestination(client.getInetAddress().getHostAddress());
        }
        return session;
    }

    private class RequestListener extends Thread {
        private ServerSocket serverSocket;

        @Override
        public void run() {
            Log.i(TAG, "RTSP server listening on local port at " + serverSocket.getLocalPort());
            while (!Thread.interrupted()) {
                try {
                    new ClientThread(serverSocket.accept()).start();
                } catch (SocketException e) {
                    Log.e(TAG, e.getMessage());
                    break;
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
            Log.i(TAG, "RTSP server stopped !");
        }

        private void startListening() throws IOException {
            try {
                Log.d(TAG, "Starting RTSP server at " + rtspPort);
                serverSocket = new ServerSocket(rtspPort);
                Log.d(TAG, "InetAddress=" + serverSocket.getInetAddress().toString());
                this.start();
            } catch (BindException e) {
                Log.e(TAG, "Port already in use !");
                postError(e, ERROR_BIND_FAILED);
                throw e;
            }
        }

        private void stopListening() {
            try {
                Log.d(TAG, "closing serverSocket.");
                serverSocket.close();
                Log.d(TAG, "wait for requestListener thread to stop");
                this.join();
            } catch (IOException ignore) {

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // One thread per client
    private class ClientThread extends Thread {
        private final Socket clientSocket;
        private final BufferedReader inputReader;

        ClientThread(final Socket clientSocket) throws IOException {
            this.clientSocket = clientSocket;
            inputReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            //mSession = new Session();
        }

        @Override
        public void run() {
            Log.i(TAG, "Connected from " + clientSocket.toString());
            RtspRequest rtspRequest;
            RtspResponse serverResponse;

            while (!Thread.interrupted()) {
                rtspRequest = null;
                serverResponse = null;

                try {
                    rtspRequest = RtspRequest.waitForRequest(inputReader);
                } catch (SocketException e) {
                    Log.e(TAG, "Client might have been disconnected. SocketException from the client");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "error in parsing client request, " + e.getMessage());
                }

                if (rtspRequest != null) {
                    try {
                        serverResponse = processRequest(rtspRequest);
                    } catch (Exception e) {
                        // This alerts the main thread that something has gone wrong in this thread
                        postError(e, ERROR_START_FAILED);
                        Log.e(TAG, e.getMessage() != null ? e.getMessage() : "An error occurred");
                        e.printStackTrace();
                        serverResponse = new RtspResponse(rtspRequest);
                    }
                }

                if (serverResponse == null) {
                    serverResponse = new RtspResponse(rtspRequest);
                    serverResponse.setStatus(RtspResponse.STATUS_BAD_REQUEST);
                }

                // serverResponse should be null here as we always send a response
                // The client will receive an "INTERNAL SERVER ERROR" if an exception has been thrown at some point
                try {
                    Log.e(TAG, "====> Sending response...");
                    serverResponse.send(clientSocket.getOutputStream());
                    Log.e(TAG, "<==== Sending successful");
                } catch (IOException e) {
                    Log.e(TAG, "<==== Failed to send response");
                    break;
                }
            }

            // Streaming stops when client disconnects
            boolean streaming = isStreaming();
            //mSession.syncStop();
            if (streaming && !isStreaming()) {
                postMessage(MESSAGE_STREAMING_STOPPED);
            }
            //mSession.release();

            try {
                clientSocket.close();
            } catch (IOException ignore) {
            }

            Log.i(TAG, "Client disconnected");

        }

        private RtspResponse processRequest(RtspRequest request) {
            Log.e(TAG, "Processing " + request.getMethod());
            RtspResponse response = new RtspResponse(request);

            //Ask for authorization unless this is an OPTIONS request
            if (request.getMethod().equals(RtspMethod.OPTIONS)) {
                response = HttpAuth.checkAuthorization(request, null, null);
                if (!response.getStatus().equals(RtspResponse.STATUS_200_OK)) {
                    return response;
                }
            }

            switch (request.getMethod()) {
                case RtspMethod.OPTIONS:
                    return handleOptions(request, clientSocket);
                case RtspMethod.DESCRIBE:
                    return handleDescribe(request, clientSocket);
                case RtspMethod.SETUP:
                    return handleSetup(request, clientSocket);
                case RtspMethod.PLAY:
                    return handlePlay(request, clientSocket);
                case RtspMethod.PAUSE:
                    //response.status = ServerResponse.STATUS_OK;
                    break;
                case RtspMethod.TEARDOWN:
                    //response.status = ServerResponse.STATUS_OK;
                    break;
                case RtspMethod.ANNOUNCE:
                case RtspMethod.GET_PARAMETER:
                case RtspMethod.SET_PARAMETER:
                case RtspMethod.REDIRECT:
                case RtspMethod.RECORD:
                default:
                    Log.e(TAG, "Command unknown: " + request.getMethod());
                    //response.status = ServerResponse.STATUS_BAD_REQUEST;
                    break;
            }
            return response;
        }
    }

    private RtspResponse handleOptions(RtspRequest request, Socket clientSocket) {
        String sb = RtspMethod.DESCRIBE +
                ", " + RtspMethod.SETUP +
                ", " + RtspMethod.OPTIONS +
                ", " + RtspMethod.TEARDOWN +
                ", " + RtspMethod.PLAY +
                ", " + RtspMethod.PAUSE;

        RtspResponse response = new RtspResponse(request);
        response.setStatus(RtspResponse.STATUS_200_OK);
        response.addHeader(RtspHeader.PUBLIC, sb);
        return response;
    }

    private RtspResponse handleDescribe(RtspRequest request, Socket clientSocket) {
        final String contentType = "application/sdp";
        RtspResponse response = new RtspResponse(request);

        String acceptValue = request.getValue(RtspHeader.ACCEPT);
        if (acceptValue == null || !acceptValue.toLowerCase().contains(contentType)) {
            response.setStatus(RtspResponse.STATUS_406_NOT_ACCEPTABLE);
            return response;
        }

        // Parse the requested URI and configure the session
        Session session = SessionManager.findSession(Uri.parse(request.getUri()));
        if (session == null) {
            return null;
        }

        Log.e(TAG, "session found=" + session.getUri());

        //mSession = handleRequest(clientRequest.uri, clientSocket);
        //mSessions.put(mSession, null);
        //mSession.syncConfigure();
        String sessionDescription = session.getDescription(clientSocket.getLocalAddress(), clientSocket.getInetAddress());
        response.addHeader(RtspHeader.CONTENT_TYPE, contentType);
        response.addHeader(RtspHeader.CONTENT_LENGTH, String.valueOf(sessionDescription.length()));
        response.setContent(sessionDescription);
        response.setStatus(RtspResponse.STATUS_200_OK);

        //response.attributes =
        //        "Content-Base: " + clientSocket.getLocalAddress().getHostAddress() + ":" + clientSocket.getLocalPort() + "/\r\n" +
        //                "Content-Type: application/sdp\r\n";
        //response.content = mSession.getSessionDescription();
        return response;
    }

    RtspResponse handleSetup(RtspRequest request, Socket clientSocket) {
        RtspResponse response = new RtspResponse(request);

        String sessionKey;
        final String trackID = "/trackID=";
        String requestUri = request.getUri();
        int idx = requestUri.lastIndexOf(trackID);
        if (idx > -1) {
            sessionKey = requestUri.substring(0, idx);
        } else {
            response.setStatus(RtspResponse.STATUS_455_METHOD_NOT_VALID_IN_THIS_STATE);
            return response;
        }

        Session session = SessionManager.findSession(Uri.parse(sessionKey));
        if (session == null) {
            response.setStatus(RtspResponse.STATUS_454_SESSION_NOT_FOUND);
            return response;
        }

        String trackId = requestUri.substring(idx + trackID.length());
        if (!session.trackExists(trackId)) {
            response.setStatus(RtspResponse.STATUS_404_NOT_FOUND);
            return response;
        }

        InetAddress destination = clientSocket.getInetAddress();

        int rtpPort, rtcpPort;
        Pattern portPattern = Pattern.compile("client_port=(\\d+)-(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher portMatcher = portPattern.matcher(request.getValue(RtspHeader.TRANSPORT));
        if (!portMatcher.find()) {
            rtpPort = session.getTrack(trackId).getRtpPort(destination);
            rtcpPort = session.getTrack(trackId).getRtcpPort(destination);
        } else {
            rtpPort = Integer.parseInt(portMatcher.group(1));
            String group2 = portMatcher.group(2);
            rtcpPort = (group2 == null) ? rtpPort + 1 : Integer.parseInt(group2);
        }

        int ssrc = session.getTrack(trackId).getSSRC();
        int serverRtpPort = session.getTrack(trackId).getLocalRtpPort();
        int serverRtcpPort = session.getTrack(trackId).getLocalRtcpPort();
        String castMode = destination.isMulticastAddress() ? "multicast" : "unicast";
        session.getTrack(trackId).addDestination(destination, rtpPort, rtcpPort);

        //boolean streaming = isStreaming();
        //mSession.syncStart(trackId);
        //if (!streaming && isStreaming()) {
        //    postMessage(MESSAGE_STREAMING_STARTED);
        //}
        response.setStatus(RtspResponse.STATUS_200_OK);
        response.addHeader(RtspHeader.TRANSPORT, "RTP/AVP/UDP;" + castMode +
                ";destination=" + destination.getHostAddress() +
                ";client_port=" + rtpPort + "-" + rtcpPort +
                ";server_port=" + serverRtpPort + "-" + serverRtcpPort +
                ";ssrc=" + Integer.toHexString(ssrc) +
                ";mode=play\r\n" +
                "Session: " + session.getSessionId() + "\r\n" +
                "Cache-Control: no-cache\r\n");
        return response;
    }

    private RtspResponse handlePlay(RtspRequest request, Socket clientSocket) {
        RtspResponse response = new RtspResponse(request);
        Session session = SessionManager.findSession(Uri.parse(request.getUri()));
        if (session == null) {
            response.setStatus(RtspResponse.STATUS_455_METHOD_NOT_VALID_IN_THIS_STATE);
            return response;
        }

        String rtpInfo = "";
        for (String trackId : session.getAllTrackIds()) {
            rtpInfo += "url=" + request.getUri() + "/trackID=" + trackId + ";seq=0,";
        }
        response.addHeader(RtspHeader.RTP_INFO, rtpInfo);
        response.addHeader(RtspHeader.SESSION, session.getSessionId());

        //serverResponse.attributes = requestAttributes.substring(0, requestAttributes.length() - 1) + "\r\nSession: 1185d20035702ca\r\n";
        // If no exception has been thrown, we reply with OK
        response.setStatus(RtspResponse.STATUS_200_OK);
        return response;
    }

}
