package net.xvis.streaming;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import net.xvis.streaming.rtp.RtpSocket;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Random;

public abstract class MediaStream {
    protected static final String TAG = "MediaStream";

    protected boolean streaming = false;
    protected boolean configured = false;
    protected int ssrc = 0;
    protected byte mChannelIdentifier = 0;

    protected OutputStream mOutputStream = null;
    private InputThread inputThread;
    private OutputThread outputThread;

    private int mtu = 1500;
    private int timeToLive = 64;
    protected MediaCodec mediaCodec;
    protected String mimeType;
    protected RtpSocket rtpSocket; // expand this to support multiple clients

    public MediaStream() {
        ssrc = new Random().nextInt();
        rtpSocket = new RtpSocket(mtu, 5004, 5005);
        rtpSocket.setSSRC(ssrc);
        rtpSocket.setTimeToLive(timeToLive);
    }

    public void addDestination(InetAddress destination, int rtpPort, int rtcpPort) {
        rtpSocket.addDestination(destination, rtpPort, rtcpPort);
    }

    public void setTimeToLive(int timeToLive) {
        this.timeToLive = timeToLive;
    }

    public int getRtpPort(InetAddress destination) {
        return rtpSocket.getRtpPort(destination);
    }

    public int getRtcpPort(InetAddress destination) {
        return rtpSocket.getRtcpPort(destination);
    }

    public int getLocalRtpPort() {
        return rtpSocket.getLocalRtpPort();
    }

    public int getLocalRtcpPort() {
        return rtpSocket.getLocalRtcpPort();
    }

    public int getSSRC() {
        return ssrc;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public synchronized void configure() throws IllegalStateException, IOException {
        if (streaming)
            throw new IllegalStateException("Can't be called while streaming.");

        //if (mPacketizer != null) {
        //    mPacketizer.setDestination(destination, rtpPort, rtcpPort);
        //    mPacketizer.getRtpSocket().setOutputStream(mOutputStream, mChannelIdentifier);
        //}
        configured = true;
    }

    public synchronized void start() throws IllegalStateException, IOException {
        if (streaming) {
            configure();
            return; // streaming started already
        }

        prepareMediaCodec();
        mediaCodec.start();
        streaming = true;

        inputThread = new InputThread();
        inputThread.start();
        outputThread = new OutputThread();
        outputThread.start();

        //if (mDestination == null)
        //    throw new IllegalStateException("No destination ip address set for the stream !");

        //if (mRtpPort <= 0 || mRtcpPort <= 0)
        //    throw new IllegalStateException("No destination ports set for the stream !");

        //Log.e(TAG, "Destination=" + mDestination + ", RptPort=" + mRtpPort + ", RtcpPort=" + mRtcpPort);
        //mPacketizer.setTimeToLive(mTTL);

        // start mediaCodec
        //mediaCodec.setCallback(callBack);
        Log.e(TAG, " MediaCodec started" + ":" + Thread.currentThread().getId());
    }

    public synchronized void stop() {
        if (!streaming) {
            return;
        }

        Log.e(TAG, "STOPPING MediaStream");

        streaming = false;

        try {
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
            }
            mediaCodec = null;
            onEncoderReleased();

        } catch (Exception e) {
            e.printStackTrace();
        }
        streaming = false;
    }

    protected abstract void prepareMediaCodec() throws IOException;

    protected void onDataEncodeStart() { }

    protected ByteBuffer waitAndGetData() {
        return null;
    }

    protected void onDataEncodeEnd() { }

    protected void onEncoderReleased() { }

    protected void streamEncodedData(MediaCodec.BufferInfo bufferInfo, ByteBuffer encodedData) { }

    protected void onFormatChanged(MediaFormat mediaFormat) { }

    public abstract String getSessionDescription();

    private class InputThread extends Thread {
        @Override
        public void run() {

            Log.e("TAG", "MediaStream input thread started");
            onDataEncodeStart();

            while (streaming) {
                //Log.e("TAG", "MediaStream getDataToEncode()");

                // wait for input data for encoding
                ByteBuffer dataBuffer = waitAndGetData();
                if (dataBuffer == null) {
                    continue;
                }

                // wait for input buffer from the encoder
                int index = mediaCodec.dequeueInputBuffer(2000000); // 2 sec wait
                //Log.d("TAG", "dequeueInputBuffer with index=" + index);
                if (index < 0) {
                    Log.e("TAG", "invalid buffer index");
                    continue;
                }

                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(index);
                if (inputBuffer == null) {
                    Log.e("TAG", "inputBuffer is null");
                    continue;
                }

                inputBuffer.clear();
                inputBuffer.put(dataBuffer.array(), 0, inputBuffer.capacity());

                // push to encoder
                long nowUs = System.nanoTime() / 1000;
                mediaCodec.queueInputBuffer(index, 0, inputBuffer.position(), nowUs, 0);
            }

            onDataEncodeEnd();
        }
    }

    private class OutputThread extends Thread {
        @Override
        public void run() {
            Log.e("TAG", "MediaStream output thread started");

            while (streaming) {
                //Log.e("TAG", "MediaStream output thread de-que");
                // wait for output buffer from the encoder
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                int index = mediaCodec.dequeueOutputBuffer(bufferInfo, 2000000);
                //Log.e(TAG, "bufferIndex=" + index + ", bufferSize=" + bufferInfo.size + ", flags=" + bufferInfo.flags);

                if (index >= 0) {
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(index);
                    streamEncodedData(bufferInfo, outputBuffer);
                    mediaCodec.releaseOutputBuffer(index, false);
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.e(TAG, "output format changed");
                    onFormatChanged(mediaCodec.getOutputFormat());
                }
            }
        }
    }
}
