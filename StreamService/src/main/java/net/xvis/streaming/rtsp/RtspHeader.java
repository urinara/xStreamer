package net.xvis.streaming.rtsp;

import java.util.HashMap;
import java.util.Map;

public class RtspHeader {
    public static final String CRLF = "\r\n";
    public static final String SP = " ";

    public static final String ACCEPT = "Accept";                   // R      opt.      entity
    public static final String ACCEPT_ENCODING = "Accept-Encoding"; // R      opt.      entity
    public static final String ACCEPT_LANGUAGE = "Accept-Language"; // R      opt.      all
    public static final String ALLOW = "Allow";                     // r      opt.      all
    public static final String AUTHORIZATION = "Authorization";     // R      opt.      all
    public static final String BANDWIDTH = "Bandwidth";             // R      opt.      all
    public static final String BLOCKSIZE = "Blocksize";             // R      opt.      all but OPTIONS, TEARDOWN
    public static final String CACHE_CONTROL = "Cache-Control";     // g      opt.      SETUP
    public static final String CONFERENCE = "Conference";           // R      opt.      SETUP
    public static final String CONNECTION = "Connection";           // g      req.      all
    public static final String CONTENT_BASE = "Content-Base";       // e      opt.      entity
    public static final String CONTENT_ENCODING = "Content-Encoding";//     e      req.      SET_PARAMETER
    //public static final String CONTENT_ENCODING = "Content-Encoding";//     e      req.      DESCRIBE, ANNOUNCE
    public static final String CONTENT_LANGUAGE = "Content-Language";//     e      req.      DESCRIBE, ANNOUNCE
    public static final String CONTENT_LENGTH = "Content-Length";//       e      req.      SET_PARAMETER, ANNOUNCE
    //public static final String CONTENT_LENGTH = "Content-Length";//       e      req.      entity
    public static final String CONTENT_LOCATION = "Content-Location";//     e      opt.      entity
    public static final String CONTENT_TYPE = "Content-Type";//         e      req.      SET_PARAMETER, ANNOUNCE
    //public static final String CONTENT_TYPE = "Content-Type";//         r      req.      entity
    public static final String CSEQ = "CSeq";//                 g      req.      all
    public static final String DATE = "Date";//                 g      opt.      all
    public static final String EXPIRES = "Expires";//             e      opt.      DESCRIBE, ANNOUNCE
    public static final String FROM = "From";//                 R      opt.      all
    public static final String IF_MODIFIED_SINCE = "If-Modified-Since";//    R      opt.      DESCRIBE, SETUP
    public static final String LAST_MODIFIED = "Last-Modified";//        e      opt.      entity
    public static final String PROXY_AUTHENTICATE = "Proxy-Authenticate";//
    public static final String PROXY_REQUIRE = "Proxy-Require";//        R      req.      all
    public static final String PUBLIC = "Public";//               r      opt.      all
    public static final String RANGE = "Range";//               R      opt.      PLAY, PAUSE, RECORD
    //public static final String RANGE = "Range";//                r      opt.      PLAY, PAUSE, RECORD
    public static final String REFERER = "Referer";//              R      opt.      all
    public static final String REQUIRE = "Require";//              R      req.      all
    public static final String RETRY_AFTER = "Retry-After";//          r      opt.      all
    public static final String RTP_INFO = "RTP-Info";//             r      req.      PLAY
    public static final String SCALE = "Scale";//               Rr     opt.      PLAY, RECORD
    public static final String SESSION = "Session";//              Rr     req.      all but SETUP, OPTIONS
    public static final String SERVER = "Server";//               r      opt.      all
    public static final String SPEED = "Speed";//                Rr     opt.      PLAY
    public static final String TRANSPORT = "Transport";//            Rr     req.      SETUP
    public static final String UNSUPPORTED = "Unsupported";//          r      req.      all
    public static final String USER_AGENT = "User-Agent";//           R      opt.      all
    public static final String VIA = "Via";//                  g      opt.      all
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";//     r      opt.      all

    private static Map<String, String> validFieldsMap = new HashMap<>();

    static {
        validFieldsMap.put(ACCEPT.toLowerCase(), ACCEPT);
        validFieldsMap.put(ACCEPT_ENCODING.toLowerCase(), ACCEPT_ENCODING);
        validFieldsMap.put(ACCEPT_LANGUAGE.toLowerCase(), ACCEPT_LANGUAGE);
        validFieldsMap.put(ALLOW.toLowerCase(), ALLOW);
        validFieldsMap.put(AUTHORIZATION.toLowerCase(), AUTHORIZATION);
        validFieldsMap.put(BANDWIDTH.toLowerCase(), BANDWIDTH);
        validFieldsMap.put(BLOCKSIZE.toLowerCase(), BLOCKSIZE);
        validFieldsMap.put(CACHE_CONTROL.toLowerCase(), CACHE_CONTROL);
        validFieldsMap.put(CONFERENCE.toLowerCase(), CONFERENCE);
        validFieldsMap.put(CONNECTION.toLowerCase(), CONNECTION);
        validFieldsMap.put(CONTENT_BASE.toLowerCase(), CONTENT_BASE);
        validFieldsMap.put(CONTENT_ENCODING.toLowerCase(), CONTENT_ENCODING);
        validFieldsMap.put(CONTENT_LANGUAGE.toLowerCase(), CONTENT_LANGUAGE);
        validFieldsMap.put(CONTENT_LENGTH.toLowerCase(), CONTENT_LENGTH);
        validFieldsMap.put(CONTENT_LOCATION.toLowerCase(), CONTENT_LOCATION);
        validFieldsMap.put(CONTENT_TYPE.toLowerCase(), CONTENT_TYPE);
        validFieldsMap.put(CSEQ.toLowerCase(), CSEQ);
        validFieldsMap.put(DATE.toLowerCase(), DATE);
        validFieldsMap.put(EXPIRES.toLowerCase(), EXPIRES);
        validFieldsMap.put(FROM.toLowerCase(), FROM);
        validFieldsMap.put(IF_MODIFIED_SINCE.toLowerCase(), IF_MODIFIED_SINCE);
        validFieldsMap.put(LAST_MODIFIED.toLowerCase(), LAST_MODIFIED);
        validFieldsMap.put(PROXY_AUTHENTICATE.toLowerCase(), PROXY_AUTHENTICATE);
        validFieldsMap.put(PROXY_REQUIRE.toLowerCase(), PROXY_REQUIRE);
        validFieldsMap.put(PUBLIC.toLowerCase(), PUBLIC);
        validFieldsMap.put(RANGE.toLowerCase(), RANGE);
        validFieldsMap.put(REFERER.toLowerCase(), REFERER);
        validFieldsMap.put(REQUIRE.toLowerCase(), REQUIRE);
        validFieldsMap.put(RETRY_AFTER.toLowerCase(), RETRY_AFTER);
        validFieldsMap.put(RTP_INFO.toLowerCase(), RTP_INFO);
        validFieldsMap.put(SCALE.toLowerCase(), SCALE);
        validFieldsMap.put(SESSION.toLowerCase(), SESSION);
        validFieldsMap.put(SERVER.toLowerCase(), SERVER);
        validFieldsMap.put(SPEED.toLowerCase(), SPEED);
        validFieldsMap.put(TRANSPORT.toLowerCase(), TRANSPORT);
        validFieldsMap.put(UNSUPPORTED.toLowerCase(), UNSUPPORTED);
        validFieldsMap.put(USER_AGENT.toLowerCase(), USER_AGENT);
        validFieldsMap.put(VIA.toLowerCase(), VIA);
        validFieldsMap.put(WWW_AUTHENTICATE.toLowerCase(), WWW_AUTHENTICATE);
    }

    protected Map<String, String> fieldValueMap = new HashMap<>();


    public boolean isValidField(String field) {
        return validFieldsMap.containsKey(field.toLowerCase());
    }

    public boolean addHeader(String field, String value) {
        if (field == null || field.isEmpty() || !isValidField(field)) {
            return false;
        }
        fieldValueMap.put(field, value);
        return true;
    }

    public boolean removeHeader(String field) {
        if (field == null || field.isEmpty()) {
            return false;
        }
        return (fieldValueMap.remove(field) != null);
    }

    public String getValue(String field) {
        if (field == null || field.isEmpty()) {
            return null;
        }
        return fieldValueMap.get(field);
    }
}
