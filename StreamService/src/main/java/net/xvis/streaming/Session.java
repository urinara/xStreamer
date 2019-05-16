package net.xvis.streaming;

import android.hardware.display.VirtualDisplay;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import net.xvis.streaming.audio.AudioQuality;
import net.xvis.streaming.audio.AudioStream;
import net.xvis.streaming.exceptions.ConfNotSupportedException;
import net.xvis.streaming.exceptions.InvalidSurfaceException;
import net.xvis.streaming.exceptions.StorageUnavailableException;
import net.xvis.streaming.hw.EncoderDebugger;
import net.xvis.streaming.rtsp.RtspHeader;
import net.xvis.streaming.rtsp.RtspResponse;
import net.xvis.streaming.video.DisplayStream;
import net.xvis.streaming.video.H264Stream;
import net.xvis.streaming.video.VideoQuality;
import net.xvis.streaming.video.VideoStream;
import net.xvis.utils.TimeUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Session {
    public final static String TAG = "Session";

    public final static String NET_TYPE_IN = "IN";
    public final static String ADDR_TYPE_IP4 = "IP4";

    public final static int VIDEO_H264 = 1;
    public final static int VIDEO_H263 = 2;
    public final static int DISPLAY_H264 = 3;

    /**
    Session description
    v=  (protocol version)
    o=  (originator and session identifier)
    s=  (session name)
    i=* (session information)
    u=* (URI of description)
    e=* (email address)
    p=* (phone number)
    c=* (connection information -- not required if included in
    all media)
    b=* (zero or more bandwidth information lines)
    One or more time descriptions ("t=" and "r=" lines; see below)
    z=* (time zone adjustments)
    k=* (encryption key)
    a=* (zero or more session attribute lines)
    Zero or more media descriptions

    Time description
    t=  (time the session is active)
    r=* (zero or more repeat times)

    Media description, if present
            m=  (media name and transport address)
    i=* (media title)
    c=* (connection information -- optional if included at
    session level)
    b=* (zero or more bandwidth information lines)
    k=* (encryption key)
    a=* (zero or more media attribute lines)
    **/

    public final static int STREAM_VIDEO = 0x01;
    public final static int STREAM_AUDIO = 0x00;

    public final static int ERROR_CONFIGURATION_NOT_SUPPORTED = 0x01;
    public final static int ERROR_STORAGE_NOT_READY = 0x02;
    public final static int ERROR_UNKNOWN_HOST = 0x05;
    public final static int ERROR_OTHER = 0x06;

    private String mDestination;
    private int timeToLive = 64;
    private long timeCreated;
    private String userName;
    private String sessionId;
    private String sessionVersion;
    private String sessionName;
    private String sessionInformation;
    private String serverAddress;
    private Uri uri;
    private String emailAddress;
    private String phoneNumber;
    private String connectionAddress;
    private long startTimeMillis;
    private long endTimeMillis;

    private int videoEncoder;
    private VideoStream videoStream;
    private VideoQuality videoQuality;
    private VirtualDisplay virtualDisplay;

    private int audioEncoder;
    private AudioStream audioStream;
    private AudioQuality audioQuality;

    private Map<String, MediaStream> tracks = new HashMap<>();
    private Callback mCallback;
    private HandlerThread sessionThread;
    private Handler uiHandler;
    private Handler sessionHandler;

    private Session() { }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Session session = new Session();

        private Builder() {}

        public Session build(Uri sessionUri) {
            session.timeCreated = TimeUtils.currentTimeMillis();

            // session threads and handlers
            session.sessionThread = new HandlerThread(sessionUri.getPath());
            session.sessionThread.start();
            session.sessionHandler = new Handler(session.sessionThread.getLooper());
            session.uiHandler = new Handler(Looper.getMainLooper());

            // session attributes
            session.userName = "-"; // no user id available
            session.sessionId = String.valueOf(TimeUtils.toNtpTimestamp(session.timeCreated)); // NTP timestamp
            session.sessionVersion = session.sessionId;
            session.sessionInformation = "";
            session.serverAddress = "";
            session.sessionName = "Unnamed";
            session.sessionInformation = "N/A";
            session.uri = sessionUri;
            session.emailAddress = ""; // j.doe@example.com (Jane Doe) or Jane Doe <j.doe@example.com>
            session.phoneNumber = ""; // p=+1 617 555-6011
            session.connectionAddress = "";
            session.startTimeMillis = 0; // "regarded as permanent"
            session.endTimeMillis = 0; // "not bounded"

            // media stream init
            switch (session.videoEncoder) {
                case VIDEO_H264:
                    session.setVideoStream(new H264Stream());
                    break;
                case DISPLAY_H264:
                    session.setVideoStream(new DisplayStream(session.virtualDisplay));
            }
            return session;
        }

        public Builder setVideoQuality(VideoQuality quality) {
            session.videoQuality = new VideoQuality(quality);
            return this;
        }

        public Builder setVideoEncoder(int videoEncoder) {
            session.videoEncoder = videoEncoder;
            return this;
        }

        public Builder setTimeToLive(int ttl) {
            session.timeToLive = ttl;
            return this;
        }

        public Builder setVirtualDisplay(VirtualDisplay virtualDisplay) {
            session.virtualDisplay = virtualDisplay;
            return this;
        }
    }


    public interface Callback {
        void onBitrateUpdate(long bitrate);
        void onSessionError(int reason, int streamType, Exception e);
        void onSessionConfigured();
        void onSessionStarted();
        void onSessionStopped();
    }

    public void setVideoStream(VideoStream videoStream) {
        tracks.put("1", videoStream);
        this.videoStream = videoStream;
    }

    public void setAudioStream(AudioStream audioStream) {
        tracks.put("0", audioStream);
        this.audioStream = audioStream;
    }


    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public void setConnectionAddress(String connectionAddress) {
        this.connectionAddress = connectionAddress;
    }

    public Callback getCallback() {
        return mCallback;
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
        description.append(NET_TYPE_IN).append(RtspHeader.SP);
        description.append(ADDR_TYPE_IP4).append(RtspHeader.SP);
        description.append(originator.getHostAddress()).append(RtspHeader.CRLF);

        // Session Name ("s=")
        description.append("s=");
        description.append(sessionName).append(RtspHeader.CRLF);

        // Session Information ("i=")
        description.append("i=");
        description.append(sessionInformation).append(RtspHeader.CRLF);

        // URI ("u=") -- OPTIONAL
        if (uri != null) {
            description.append("u=");
            description.append(uri.getPath()).append(RtspHeader.CRLF);
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
        description.append(NET_TYPE_IN).append(RtspHeader.SP);
        description.append(ADDR_TYPE_IP4).append(RtspHeader.SP);
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
        if (audioStream != null) {
            description.append(videoStream.getSessionDescription());
            description.append("a=control:trackID=0" + "\r\n");
        }
        if (videoStream != null) {
            description.append(videoStream.getSessionDescription());
            description.append("a=control:trackID=1" + "\r\n");
        }

        return description.toString();
    }

    public Uri getUri() {
        return uri;
    }

    public String getDestination() {
        return mDestination;
    }

    public String getSessionId() {
        return sessionId;
    }
    public boolean isStreaming() {
        return videoStream != null && videoStream.isStreaming()
                || audioStream != null && audioStream.isStreaming();
    }

    public boolean trackExists(String trackId) {
        return tracks.containsKey(trackId);
    }

    public MediaStream getTrack(String trackId) {
        return tracks.get(trackId);
    }

    public Set<String> getAllTrackIds() {
        return tracks.keySet();
    }

    public void configure() {
        sessionHandler.post(new Runnable() {
            @Override
            public void run() {
//                Stream stream = videoStream;
//                if (stream != null && !stream.isStreaming()) {
//                    try {
//                        stream.configure();
//                    } catch (StorageUnavailableException e) {
//                        postError(ERROR_STORAGE_NOT_READY, 1, e);
//                    } catch (ConfNotSupportedException e) {
//                        postError(ERROR_CONFIGURATION_NOT_SUPPORTED, 1, e);
//                    } catch (IOException e) {
//                        postError(ERROR_OTHER, 1, e);
//                    } catch (RuntimeException e) {
//                        postError(ERROR_OTHER, 1, e);
//                    }
//                }
            }
        });
    }

    public void startStream() {
        sessionHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (videoStream != null) {
                        videoStream.setVideoQuality(videoQuality);
                        videoStream.start();
                    }
                    if (audioStream != null) {
                        audioStream.start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();

                }
            }
        });
    }

    public void stopStream() {
        sessionHandler.post(new Runnable() {
            @Override
            public void run() {
                if (videoStream != null) {
                    videoStream.stop();
                }
                if (audioStream != null) {
                    audioStream.stop();
                }
                sessionHandler.getLooper().quit();
            }
        });
    }

    private void postSessionConfigured() {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mCallback != null) {
                    mCallback.onSessionConfigured();
                }
            }
        });
    }

    private void postSessionStarted() {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mCallback != null) {
                    mCallback.onSessionStarted();
                }
            }
        });
    }

    private void postSessionStopped() {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mCallback != null) {
                    mCallback.onSessionStopped();
                }
            }
        });
    }

    private void postError(final int reason, final int streamType, final Exception e) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mCallback != null) {
                    mCallback.onSessionError(reason, streamType, e);
                }
            }
        });
    }

    private void postBitRate(final long bitrate) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mCallback != null) {
                    mCallback.onBitrateUpdate(bitrate);
                }
            }
        });
    }
}
