package net.xvis.streaming.resources;

import net.xvis.streaming.MediaStream;
import net.xvis.streaming.rtsp.RtspHeader;
import net.xvis.utils.TimeUtils;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MediaContainer {
    private String baseUri;
    private Map<String, MediaStream> streamMap;
    private List<String> supportedMethods;

    // attributes
    private long timeCreated;
    private String userName;
    private String sessionId;
    private String sessionVersion;
    private String sessionName;
    private String sessionInformation;
    private String serverAddress;
    private URI sessionInfoUri;
    private String emailAddress;
    private String phoneNumber;
    private String connectionAddress;
    private long startTimeMillis;
    private long endTimeMillis;

    public MediaContainer(String baseUri) {
        this.baseUri = baseUri;
        streamMap = new HashMap<>();
        supportedMethods = new ArrayList<>();

        // session attributes
        timeCreated = TimeUtils.currentTimeMillis();
        userName = "-"; // no user id available
        sessionId = String.valueOf(TimeUtils.toNtpTimestamp(timeCreated)); // NTP timestamp
        sessionVersion = sessionId;
        sessionInformation = "";
        serverAddress = "";
        sessionName = "Unnamed";
        sessionInformation = "N/A";
        sessionInfoUri = null;
        emailAddress = ""; // j.doe@example.com (Jane Doe) or Jane Doe <j.doe@example.com>
        phoneNumber = ""; // p=+1 617 555-6011
        connectionAddress = "";
        startTimeMillis = 0; // "regarded as permanent"
        endTimeMillis = 0; // "not bounded"
    }

    public String getBaseUri() {
        return baseUri;
    }

    public void addMedia(String controlUri, MediaStream mediaStream) {
        streamMap.put(controlUri, mediaStream);
    }

    public Set<String> getControlUris() {
        return streamMap.keySet();
    }

    public void addSupportedMethod(String method) {
        supportedMethods.add(method);
    }

    public String getSupportedMethods() {
        if (supportedMethods.size() == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder(supportedMethods.get(0));
        for (int i = 1; i < supportedMethods.size(); i++) {
            sb.append(", ").append(supportedMethods.get(i));
        }
        return sb.toString();
    }

    public void removeMedia(String path) {
        streamMap.remove(path);
    }

    public String getDescription(InetAddress originator, InetAddress destination) {
        StringBuilder description = new StringBuilder();
        //if (mDestination == null) {
        //    throw new IllegalStateException("setDestination() has not been called !");
        //}

        // Protocol Version ("v=")
        description.append("v=");
        description.append("0").append(RtspHeader.CRLF);

        // Origin ("o=")
        description.append("o=");
        description.append(userName).append(RtspHeader.SP);
        description.append(sessionId).append(RtspHeader.SP);
        description.append(sessionVersion).append(RtspHeader.SP);
        description.append("IN").append(RtspHeader.SP); // Net type: IN
        description.append("IP4").append(RtspHeader.SP); // Address type: IP4 or IP6
        description.append(originator.getHostAddress()).append(RtspHeader.CRLF);

        // Session Name ("s=")
        description.append("s=");
        description.append(sessionName).append(RtspHeader.CRLF);

        // Session Information ("i=")
        description.append("i=");
        description.append(sessionInformation).append(RtspHeader.CRLF);

        // URI ("u=") -- OPTIONAL
        if (sessionInfoUri != null) {
            description.append("u=");
            description.append(baseUri).append(RtspHeader.CRLF);
        }

        // Email Address ("e=")
        if (emailAddress != null && !emailAddress.isEmpty()) {
            description.append("e=");
            description.append(emailAddress).append(RtspHeader.CRLF);
        }

        // Phone Number ("p=")
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            description.append("p=");
            description.append(phoneNumber).append(RtspHeader.CRLF);
        }

        // Connection Data ("c=")
        description.append("c=");
        description.append("IN").append(RtspHeader.SP); // net type: IN
        description.append("IP4").append(RtspHeader.SP); // Address type: IP4, IP6
        description.append(destination.getHostAddress()).append(RtspHeader.CRLF);

        // Bandwidth ("b=") -- OPTIONAL
        //description.append("b="); // b=<bwtype>:<bandwidth> bwtype = CT|AS
        //description.append("CT:1000");

        // Timing ("t=")
        description.append("t=");
        description.append(startTimeMillis != 0 ? TimeUtils.toNtpSeconds(startTimeMillis) : 0).append(RtspHeader.SP);
        description.append(endTimeMillis != 0 ? TimeUtils.toNtpSeconds(endTimeMillis) : 0);
        description.append(RtspHeader.CRLF);

        // Repeat Times ("r=")
        //description.append("r=");
        //description.append("0 0 0");
        //description.append(RtspHeader.CRLF);

        // Time Zones ("z=")
        //description.append("z=");
        //description.append("0 0 0 0");
        //description.append(RtspHeader.CRLF);

        // Encryption Keys ("k=")
        //description.append("k=");
        //description.append("uri:https://abc.def");
        //description.append(RtspHeader.CRLF);

        // Media Descriptions ("m=")
        //description.append("m=");
        //description.append("video 49170/2 RTP/AVP 31")
        //description.append(RtspHeader.CRLF);

        // Attributes ("a=")
        description.append("a=");
        description.append("recvonly");
        description.append(RtspHeader.CRLF);
        // Prevents two different sessions from using the same peripheral at the same time

        for (String controlUri : streamMap.keySet()) {
            MediaStream mediaStream = streamMap.get(controlUri);
            if (mediaStream != null) {
                description.append(mediaStream.getSessionDescription());
                description.append("a=control:").append(controlUri);
                description.append(RtspHeader.CRLF);
            }
        }

        return description.toString();
    }
}
