package net.xvis.streaming.rtsp;

import android.util.Log;

import net.xvis.streaming.MediaStream;
import net.xvis.streaming.Session;
import net.xvis.streaming.SessionManager;
import net.xvis.streaming.resources.MediaContainer;
import net.xvis.streaming.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RtspServer {
    public final static String TAG = "RtspServer";
    public static String SERVER_NAME = "XVIS RTSP Server";

    public static final String RTSP_VERSION = "RTSP/1.0";
    public static final String SCHEME = "rtsp";
    public static final int DEFAULT_RTSP_PORT = 8086; // default: 554, RTSPS default = 322

    // RTSP header
    public final static int ERROR_BIND_FAILED = 0x00;
    public final static int ERROR_START_FAILED = 0x01;
    public final static int MESSAGE_STREAMING_STARTED = 0X00;
    public final static int MESSAGE_STREAMING_STOPPED = 0X01;

    private int rtspPort = DEFAULT_RTSP_PORT;
    private ServerThread serverThread;
    private boolean restart;

    public synchronized void start() throws IOException {
        if (restart) {
            Log.d(TAG, "Restarting ServerThread");
            stop();
        }

        if (serverThread == null) {
            Log.d(TAG, "ServerThread starting...");
            serverThread = new ServerThread();
            serverThread.startListening();
            Log.d(TAG, "ServerThread started.");
        } else {
            Log.d(TAG, "ServerThread started already.");
        }

        restart = false;
    }

    public synchronized void stop() {
        if (serverThread == null) {
            Log.e(TAG, "ServerThread not started before");
            return;
        }

        Log.d(TAG, "ServerThread stopListening...");
        try {
            serverThread.stopListening();
        } catch (Exception ignore) {
        } finally {
            serverThread = null;
            Log.d(TAG, "ServerThread stopped.");
        }
    }

    private class ServerThread extends Thread {
        private ServerSocket serverSocket;

        @Override
        public void run() {
            Log.i(TAG, "RTSP server listening on local port at " + serverSocket.getLocalPort());
            while (!Thread.interrupted()) {
                try {
                    new SessionThread(serverSocket.accept()).start();
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
                start();
            } catch (BindException e) {
                Log.e(TAG, "Port already in use !");
                throw e;
            }
        }

        private void stopListening() {
            try {
                Log.d(TAG, "closing serverSocket.");
                serverSocket.close();
                Log.d(TAG, "wait for serverThread thread to stop");
                this.join();
            } catch (IOException ignore) {

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private enum RtspState {
        INIT,
        READY,
        PLAY
    }

    // One thread per client
    private class SessionThread extends Thread {

        private final Socket clientSocket;
        private final BufferedReader inputReader;
        private RtspState state;

        SessionThread(final Socket clientSocket) throws IOException {
            this.clientSocket = clientSocket;
            inputReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            state = RtspState.INIT;
        }

        @Override
        public void run() {
            Log.i(TAG, "Connected from " + clientSocket.toString());
            while (!Thread.interrupted()) {
                try {
                    RtspRequest rtspRequest = RtspRequest.waitForRequest(inputReader);
                    RtspResponse serverResponse = processRequest(rtspRequest, clientSocket);
                    serverResponse.send(clientSocket.getOutputStream());
                } catch (IOException e) {
                    Log.e(TAG, "Client might have been disconnected");
                    break;
                }
            }

            try {
                clientSocket.close();
            } catch (IOException ignore) {
            }

            Log.i(TAG, "Client disconnected");
        }
    }

    private RtspResponse processRequest(RtspRequest request, Socket clientSocket) {
        RtspResponse response = new RtspResponse(request);

        if (request == null || !request.validate()) {
            response.setStatus(RtspResponse.STATUS_400_BAD_REQUEST);
            return response;
        }

        if (!request.getVersion().equals(RTSP_VERSION)) {
            response.setStatus(RtspResponse.STATUS_505_RTSP_VERSION_NOT_SUPPORTED);
            return response;
        }

        //Ask for authorization unless this is an OPTIONS request
        if (!request.getMethod().equals(RtspMethod.OPTIONS)) {
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
                response.setStatus(RtspResponse.STATUS_405_METHOD_NOT_ALLOWED);
                break;
        }
        return response;
    }

    private RtspResponse handleOptions(RtspRequest request, Socket clientSocket) {
        RtspResponse response = new RtspResponse(request);
        //If the Request-URI refers to a specific media resource on a given host, the scope is
        //limited to the set of methods supported for that media resource by
        //the indicated RTSP agent.  A Request-URI with only the host address
        //limits the scope to the specified RTSP agent's general capabilities
        //without regard to any specific media.  If the Request-URI is an
        //asterisk ("*"), the scope is limited to the general capabilities of
        //the next hop (i.e., the RTSP agent in direct communication with the
        //        request sender).
        String sb = "";
        URI uri = request.getUri();
        MediaContainer mediaContainer = ResourceManager.findResource(uri);
        if (mediaContainer == null) {
            response.setStatus(RtspResponse.STATUS_404_NOT_FOUND);
            return response;
        }

        response.setStatus(RtspResponse.STATUS_200_OK);
        response.addHeader(RtspHeader.PUBLIC, mediaContainer.getSupportedMethods());
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
        MediaContainer mediaContainer = ResourceManager.findResource(request.getUri());
        if (mediaContainer == null) {
            response.setStatus(RtspResponse.STATUS_404_NOT_FOUND);
            return response;
        }

        String description = mediaContainer.getDescription(clientSocket.getLocalAddress(), clientSocket.getInetAddress());
        response.addHeader(RtspHeader.CONTENT_TYPE, contentType);
        response.addHeader(RtspHeader.CONTENT_LENGTH, String.valueOf(description.length()));
        response.setContent(description);
        response.setStatus(RtspResponse.STATUS_200_OK);

        return response;
    }

    RtspResponse handleSetup(RtspRequest request, Socket clientSocket) {
        RtspResponse response = new RtspResponse(request);

        // see if requested URI is available
        MediaContainer mediaContainer = ResourceManager.findResource(request.getUri());
        if (mediaContainer == null) {
            response.setStatus(RtspResponse.STATUS_404_NOT_FOUND);
            return response;
        }

        Session session = null;
        // check if the request contains the sessionID
        String sessionId = request.getValue(RtspRequest.SESSION);
        if (sessionId != null && !sessionId.isEmpty()) {
            session = SessionManager.findSession(sessionId);
        }

        // create a new session
        if (session == null) {
            session = Session.builder().build(request.getUri());
        }

        String trackId = "";
        int ssrc = session.getTrack(trackId).getSSRC();

        //MediaStream mediaStream = mediaContainer.
        clientSocket.getPort();

        InetAddress destination = clientSocket.getInetAddress();
        int rtpPort, rtcpPort;
        Pattern portPattern = Pattern.compile("client_port=(\\d+)-(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher portMatcher = portPattern.matcher(request.getValue(RtspHeader.TRANSPORT));
        if (portMatcher.find()) {
            rtpPort = Integer.parseInt(portMatcher.group(1));
            String group2 = portMatcher.group(2);
            rtcpPort = (group2 == null) ? rtpPort + 1 : Integer.parseInt(group2);
        } else {
            rtpPort = session.getTrack(trackId).getRtpPort(destination);
            rtcpPort = session.getTrack(trackId).getRtcpPort(destination);
        }

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
        Session session = SessionManager.findSession(request.getUri());
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
