package net.xvis.streaming.rtsp;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public class RtspResponse extends RtspHeader {
    private static final String TAG = "RtspResponse";

    private static final String VERSION = "RTSP/1.0";
    // Status code definitions
    public static final String STATUS_OK = "200 OK";
    public static final String STATUS_BAD_REQUEST = "400 Bad Request";
    public static final String STATUS_UNAUTHORIZED = "401 Unauthorized";
    public static final String STATUS_NOT_FOUND = "404 Not Found";
    public static final String STATUS_INTERNAL_SERVER_ERROR = "500 Internal Server Error";
    public static final String STATUS_100_CONTINUE = "100 Continue";
    public static final String STATUS_200_OK = "200 OK";
    public static final String STATUS_201_CREATED = "201 Created";
    public static final String STATUS_250_LOW_ON_STORAGE_SPACE = "250 Low on Storage Space";
    public static final String STATUS_300_MULTIPLE_CHOICES = "300 Multiple Choices";
    public static final String STATUS_301_MOVED_PERMANENTLY = "301 Moved Permanently";
    public static final String STATUS_302_MOVED_TEMPORARILY = "302 Moved Temporarily";
    public static final String STATUS_303_SEE_OTHER = "303 See Other";
    public static final String STATUS_304_NOT_MODIFIED = "304 Not Modified";
    public static final String STATUS_305_USE_PROXY = "305 Use Proxy";
    public static final String STATUS_400_BAD_REQUEST = "400 Bad Request";
    public static final String STATUS_401_UNAUTHORIZED = "401 Unauthorized";
    public static final String STATUS_402_PAYMENT_REQUIRED = "402 Payment Required";
    public static final String STATUS_403_FORBIDDEN = "403 Forbidden";
    public static final String STATUS_404_NOT_FOUND = "404 Not Found";
    public static final String STATUS_405_METHOD_NOT_ALLOWED = "405 Method Not Allowed";
    public static final String STATUS_406_NOT_ACCEPTABLE = "406 Not Acceptable";
    public static final String STATUS_407_PROXY_AUTHENTICATION_REQUIRED = "407 Proxy Authentication Required";
    public static final String STATUS_408_REQUEST_TIME_OUT = "408 Request Time-out";
    public static final String STATUS_410_GONE = "410 Gone";
    public static final String STATUS_411_LENGTH_REQUIRED = "411 Length Required";
    public static final String STATUS_412_PRECONDITION_FAILED = "412 Precondition Failed";
    public static final String STATUS_413_REQUEST_ENTITY_TOO_LARGE = "413 Request Entity Too Large";
    public static final String STATUS_414_REQUEST_URI_TOO_LARGE = "414 Request-URI Too Large";
    public static final String STATUS_415_UNSUPPORTED_MEDIA_TYPE = "415 Unsupported Media Type";
    public static final String STATUS_451_PARAMETER_NOT_UNDERSTOOD = "451 Parameter Not Understood";
    public static final String STATUS_452_CONFERENCE_NOT_FOUND = "452 Conference Not Found";
    public static final String STATUS_453_NOT_ENOUGH_BANDWIDTH = "453 Not Enough Bandwidth";
    public static final String STATUS_454_SESSION_NOT_FOUND = "454 Session Not Found";
    public static final String STATUS_455_METHOD_NOT_VALID_IN_THIS_STATE = "455 Method Not Valid in This State";
    public static final String STATUS_456_HEADER_FIELD_NOT_VALID_FOR_RESOURCE = "456 Header Field Not Valid for Resource";
    public static final String STATUS_457_INVALID_RANGE = "457 Invalid Range";
    public static final String STATUS_458_PARAMETER_IS_READ_ONLY = "458 Parameter Is Read-Only";
    public static final String STATUS_459_AGGREGATE_OPERATION_NOT_ALLOWED = "459 Aggregate operation not allowed";
    public static final String STATUS_460_ONLY_AGGREGATE_OPERATION_ALLOWED = "460 Only aggregate operation allowed";
    public static final String STATUS_461_UNSUPPORTED_TRANSPORT = "461 Unsupported transport";
    public static final String STATUS_462_DESTINATION_UNREACHABLE = "462 Destination unreachable";
    public static final String STATUS_500_INTERNAL_SERVER_ERROR = "500 Internal Server Error";
    public static final String STATUS_501_NOT_IMPLEMENTED = "501 Not Implemented";
    public static final String STATUS_502_BAD_GATEWAY = "502 Bad Gateway";
    public static final String STATUS_503_SERVICE_UNAVAILABLE = "503 Service Unavailable";
    public static final String STATUS_504_GATEWAY_TIME_OUT = "504 Gateway Time-out";
    public static final String STATUS_505_RTSP_VERSION_NOT_SUPPORTED = "505 RTSP Version not supported";
    public static final String STATUS_551_OPTION_NOT_SUPPORTED = "551 Option not supported";

    private String status = STATUS_500_INTERNAL_SERVER_ERROR;
    private String content = "";
    private RtspRequest rtspRequest;

    RtspResponse(RtspRequest clientRequest) {
        this.rtspRequest = clientRequest;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void send(OutputStream output) throws IOException {
        int seqId = -1;

        try {
            String value = (rtspRequest != null) ? rtspRequest.getValue(RtspHeader.CSEQ) : "";
            if (value != null && !value.isEmpty()) {
                seqId = Integer.parseInt(value.replace(" ", ""));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing CSeq: " + (e.getMessage() != null ? e.getMessage() : ""));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(VERSION).append(SP).append(status).append(CRLF);
        if (seqId > -1) {
            sb.append(RtspResponse.CSEQ).append(": ").append(String.valueOf(seqId)).append(CRLF);
        }

        for (Map.Entry<String, String> entry : fieldValueMap.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append(CRLF);
        }
        sb.append(CRLF);
        sb.append(content);

        Log.e(TAG, sb.toString());
        output.write(sb.toString().getBytes());
    }

}
