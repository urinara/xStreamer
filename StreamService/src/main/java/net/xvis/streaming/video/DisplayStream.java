package net.xvis.streaming.video;

import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.DisplayMetrics;
import android.util.Log;

import net.xvis.display.VirtualDisplaySurface;

import net.xvis.streaming.mp4.MP4Config;
import net.xvis.streaming.rtp.RtpSocket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class DisplayStream extends VideoStream {

    public final static String TAG = "DisplayStream";

    private MP4Config mConfig;
    private VirtualDisplay virtualDisplay;
    private VirtualDisplaySurface displaySurface;
    private long clockRateHz = 90000; // in Hz for H.264
    private int payloadType = 96;
    private long sequenceNum = 0;
    private byte[] sps = null, pps = null, stapa = null;

    public DisplayStream(VirtualDisplay virtualDisplay) {
        super();
        mimeType = "video/avc";
        rtpSocket.setPayloadType(payloadType);
        rtpSocket.setClockRateHz(clockRateHz);
        this.virtualDisplay = virtualDisplay;
    }

    @Override
    protected void prepareMediaCodec() throws RuntimeException, IOException {
        super.prepareMediaCodec();

    }

    @Override
    public synchronized String getSessionDescription() throws IllegalStateException {
        // m=<media> <port> <transport> <fmt list>
        // a=rtpmap:<payload type> <encoding name>/<clock rate>[/<encoding params>]
        // a=fmtp:<format> <format specific parameters>
        return "m=video " + String.valueOf(rtpSocket.getRtpPort(null)) + " RTP/AVP " + payloadType + "\r\n" +
                "a=rtpmap:" + payloadType + " H264/" + String.valueOf(clockRateHz) + "\r\n" +
                "a=fmtp:" + payloadType +
                " packetization-mode=1;" + // non-interleaved mode supports only Single NALU, STAP-A, FU-A
                //"profile-level-id=" + mConfig.getProfileLevel() + ";" +
                "sprop-parameter-sets=" + b64SPS + "," + b64PPS + ";\r\n";
    }

    @Override
    protected void onDataEncodeStart() {
        Log.e(TAG, "onDataEncodeStart on InputThread");
        final DisplayMetrics metrics = new DisplayMetrics();
        virtualDisplay.getDisplay().getMetrics(metrics);
        displaySurface = new VirtualDisplaySurface(metrics.widthPixels, metrics.heightPixels, supportedColorFormat);
        virtualDisplay.setSurface(displaySurface.getSurface());
    }

    @Override
    protected ByteBuffer waitAndGetData() {
        //Log.e(TAG, "waitAndGetData on InputThread");
        displaySurface.waitForFrame();
        return displaySurface.getYuvBuffer();
    }

    @Override
    protected void onDataEncodeEnd() {
        Log.e(TAG, "onDataEncodeEnd on InputThread");
        virtualDisplay.setSurface(null);
        displaySurface.release();
    }

    @Override
    protected void onEncoderReleased() { }

    @Override
    protected void onFormatChanged(MediaFormat mediaFormat) {
        Log.e(TAG, "onFormatChanged: " + mediaFormat.toString());
        // The PPS and PPS should be there
        ByteBuffer spsb = mediaFormat.getByteBuffer("csd-0");
        ByteBuffer ppsb = mediaFormat.getByteBuffer("csd-1");
        if (spsb != null) {
            sps = new byte[spsb.capacity() - 4];
            spsb.position(4);
            spsb.get(sps, 0, sps.length);
        }
        if (ppsb != null) {
            pps = new byte[ppsb.capacity() - 4];
            ppsb.position(4);
            ppsb.get(pps, 0, pps.length);
        }
    }

    @Override
    protected void streamEncodedData(MediaCodec.BufferInfo bufferInfo, ByteBuffer encodedData) {
        if (encodedData == null) {
            Log.e(TAG, "nothing to encode");
            return;
        }

        // regular frame = 0
        // BUFFER_FLAG_KEY_FRAME = 1
        // BUFFER_FLAG_CODEC_CONFIG = 2
        // BUFFER_FLAG_END_OF_STREAM = 4
        // BUFFER_FLAG_PARTIAL_FRAME = 8
        long timestampUs = bufferInfo.presentationTimeUs;
        long rtpTimestamp = (long) (bufferInfo.presentationTimeUs / 1000.0 * clockRateHz / 1000.0 + 0.5);
        //Log.e(TAG, "BufferSize=" + bufferInfo.size + ", flags=" + bufferInfo.flags + ", rtpTs=" + timestamp + ", timeUs=" + bufferInfo.presentationTimeUs);

        // Check NAL unit start code
        byte startCode[] = new byte[4];
        encodedData.get(startCode, 0, 4);
        if (startCode[0] != 0 || startCode[1] != 0 || startCode[2] != 0 || startCode[3] != 1) {
            Log.e(TAG, "invalid startCode, " + Arrays.toString(startCode));
            return;
        }

        // NAL unit size
        int nalUnitSize = bufferInfo.size - encodedData.position();

        // NAL unit header: [F:1|NRI:2|Type:5] forbidden_zero_bit, nal_ref_id, nal_unit_type
        byte unitHeader = encodedData.get();
        int nalUnitType = unitHeader & 0x1F;
        //Log.e(TAG, "NAL unit type=" + nalUnitType + ", size=" + nalUnitSize);

        try {
            //if (nalUnitType == 7 || nalUnitType == 8) {
            //    // 7: sequence parameter set
            //    // 8: picture parameter set
            //}
            //// We send two packets containing NALU type 7 (SPS) and 8 (PPS)
            //// Those should allow the H264 stream to be decoded even if no SDP was sent to the decoder.
            //if (nalUnitType == 5 && sps != null && pps != null) {
            //    DatagramPacket packet = rtpSocket.dequeuePacket(); // blocking
            //    System.arraycopy(stapa, 0, packet.getData(), RtpSocket.HEADER_SIZE, stapa.length);
            //    packet.setLength(RtpSocket.HEADER_SIZE + stapa.length);
            //    rtpSocket.enqueuePacket(packet, true, sequenceNum++, timestamp);
            //}

            int maxPayloadSize = rtpSocket.getMaxPayloadSize();
            int payloadOffset = rtpSocket.getPayloadOffset();
            int payloadSize = nalUnitSize;

            if (payloadSize <= maxPayloadSize) {
                // Single NAL unit
                RtpSocket.RtpData rtpData = rtpSocket.dequeueData(); // blocking
                // prepare payload
                DatagramPacket packet = rtpData.getPacket();
                byte[] data = packet.getData();
                data[payloadOffset] = unitHeader;
                encodedData.get(data, payloadOffset + 1, payloadSize - 1);
                packet.setLength(payloadOffset + payloadSize);
                // send over
                rtpData.setHeader(true, rtpTimestamp, timestampUs, sequenceNum++);
                rtpSocket.enqueueData(rtpData);
            } else {
                // Fragment Units
                int bytesRead = 1; // 1st byte (unitHeader) is read already
                byte startBit = (byte) 0x80;
                byte endBit = 0;
                boolean marker = false;
                payloadSize = maxPayloadSize;

                while (bytesRead < nalUnitSize) {
                    RtpSocket.RtpData rtpData = rtpSocket.dequeueData(); // blocking
                    // prepare payload
                    DatagramPacket packet = rtpData.getPacket();
                    byte[] data = packet.getData();

                    // FU indicator, [F:1|NRI:2|Type:5] F=0, NRI, type=28 FU-A, 29 for FU-B
                    data[payloadOffset] = (byte) (unitHeader & 0x60 | 28); // 0110 0000, 0001 1100
                    // FU header [S:1|E:1|R:1|Type:5], R=0
                    data[payloadOffset + 1] = (byte) (startBit | endBit | (unitHeader & 0x1F)); // 0001 1111

                    // NAL
                    encodedData.get(data, payloadOffset + 2, payloadSize - 2);

                    // payload size
                    packet.setLength(payloadOffset + payloadSize);

                    // send
                    rtpData.setHeader(marker, rtpTimestamp, timestampUs, sequenceNum++);
                    rtpSocket.enqueueData(rtpData);

                    // update for the next fragment
                    bytesRead += (payloadSize - 2);
                    payloadSize = nalUnitSize - bytesRead + 2;
                    if (payloadSize <= maxPayloadSize) { // last fragment
                        endBit = (byte) 0x40;
                        marker = true;
                    } else {
                        payloadSize = maxPayloadSize;
                    }
                    startBit = 0;
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // nal_ref_idc: NAL unit reference field | frame | picture
        if (bufferInfo.flags == 2) {

        }
    }
}
