package net.xvis.streaming.rtp;

import android.os.SystemClock;
import android.util.Log;

import net.xvis.streaming.Utils;
import net.xvis.streaming.rtcp.SenderReport;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class RtpSocket {
    public static final String TAG = "RtpSocket";

    public static final int TRANSPORT_UDP = 0;
    public static final int TRANSPORT_TCP = 1;
    public static final int HEADER_SIZE = 12;

    //  0                   1                   2                   3
    //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |V=2|P|X|  CC   |M|     PT      |       sequence number         |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |                           timestamp                           |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |           synchronization source (SSRC) identifier            |
    // +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
    // |            contributing source (CSRC) identifiers             |
    // |                             ....                              |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    //
    // timestamp:
    // The timestamp reflects the sampling instant of the first octet in
    // the RTP data packet.  The sampling instant MUST be derived from a
    // clock that increments monotonically and linearly in time to allow
    // synchronization and jitter calculations

    public class RtpData {
        private DatagramPacket packet;
        private long timestampUs;

        public RtpData(int maxPacketSize, long timestampUs) {
            packet = new DatagramPacket(new byte[maxPacketSize], 2);
            this.timestampUs = timestampUs;
        }

        public DatagramPacket getPacket() {
            return packet;
        }

        public long getTimestampUs() {
            return timestampUs;
        }

        public void setHeader(boolean marker, long rtpTimestamp, long timestampUs, long sequenceNum) {
            this.timestampUs = timestampUs;
            packet.getData()[1] = (byte) (((byte) payloadType) | ((byte) (marker ? (0x80) : 0)));
            Utils.writeValue(packet.getData(), sequenceNum, 2, 4);
            Utils.writeValue(packet.getData(), rtpTimestamp, 4, 8);
        }
    }

    private MulticastSocket multicastSocket;
    private SenderReport senderReport;

    // destinations
    private Map<InetAddress, int[]> destinationMap = new LinkedHashMap<>();
    private int defaultRtpPort;
    private int defaultRtcpPort;
    private int transport;

    private long mCacheSize;
    private long mClock = 0;

    private int mtu;
    private int maxPacketSize;
    private int maxPayloadSize;
    private long clockRateHz;
    private int payloadType;

    private int[] csrc = new int[0];
    private int ssrc;
    private int sequenceNum = 0;
    private int numBuffers;
    private int mBufferIn;
    private int mBufferOut;
    private int mCount = 0;
    private byte mTcpHeader[];
    protected OutputStream mOutputStream = null;
    private RtpData[] rtpBuffers;
    BlockingQueue<RtpData> emptyRtpData;
    BlockingQueue<RtpData> filledRtpData;

    private RtpThread rtpThread;
    //private RtcpThread rtcpThread;
    private final Object syncObject = new Object();

    private AverageBitrate mAverageBitrate;

    public RtpSocket(int mtu, int defaultRtpPort, int defaultRtcpPort) {
        this.mtu = mtu;
        this.defaultRtpPort = defaultRtpPort;
        this.defaultRtcpPort = defaultRtcpPort;
        maxPacketSize = mtu - 28; // IP + UDP = 20 + 8
        maxPayloadSize = maxPacketSize - HEADER_SIZE;

        mCacheSize = 0;

        numBuffers = 300; // TODO: readjust that when the FIFO is full
        rtpBuffers = new RtpData[numBuffers];
        senderReport = new SenderReport(45005);
        mAverageBitrate = new AverageBitrate();
        transport = TRANSPORT_UDP;
        mTcpHeader = new byte[] { 0x24, 0, 0, 0 }; // 0x24, channel number, data length

        resetFifo();

        emptyRtpData = new ArrayBlockingQueue<>(numBuffers);
        filledRtpData = new ArrayBlockingQueue<>(numBuffers);

        for (int i = 0; i < numBuffers; i++) {
            RtpData rtpData = new RtpData(maxPacketSize, 0L);
            rtpData.getPacket().getData()[0] = (byte) 0b10000000; // Version|P|X|CC
            rtpData.getPacket().getData()[1] = (byte) payloadType;//0b01100000; // M|payload type -> 0|dynamic(96)
            rtpBuffers[i] = rtpData;
            emptyRtpData.add(rtpData);
        }

        try {
            multicastSocket = new MulticastSocket(45004);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        rtpThread = new RtpThread();
        rtpThread.start();
    }

    private void resetFifo() {
        mCount = 0;
        mBufferIn = 0;
        mBufferOut = 0;
        senderReport.reset();
        mAverageBitrate.reset();
    }

    public void close() {
        senderReport.close();
        multicastSocket.close();
    }

    public int getMaxPacketSize() {
        return maxPacketSize;
    }

    public int getMaxPayloadSize() {
        return maxPacketSize - HEADER_SIZE - (csrc.length * Integer.BYTES);
    }

    public int getPayloadOffset() {
        return HEADER_SIZE - (csrc.length * Integer.BYTES);
    }

    public void setSSRC(int ssrc) {
        synchronized (syncObject) {
            this.ssrc = ssrc;
            for (int i = 0; i < numBuffers; i++) {
                Utils.writeValue(rtpBuffers[i].getPacket().getData(), ssrc, 8, 12);
            }
            senderReport.setSSRC(this.ssrc);
        }
    }

    public void setCSRC(int[] csrc) {
        synchronized (syncObject) {
            this.csrc = csrc;
            maxPayloadSize = maxPacketSize - HEADER_SIZE - csrc.length * 4;
            for (int i = 0; i < csrc.length; i++) {
                int begin = HEADER_SIZE + i * 4;
                Utils.writeValue(rtpBuffers[i].getPacket().getData(), csrc[i], begin, begin + 4);
            }
        }
    }

    public int getSSRC() {
        return ssrc;
    }

    public void setClockRateHz(long clockRateHz) {
        this.clockRateHz = clockRateHz;
    }

    public void setPayloadType(int payloadType) {
        this.payloadType = payloadType;
    }

    /**
     * Sets the size of the FIFO in ms.
     */
    public void setCacheSize(long cacheSize) {
        mCacheSize = cacheSize;
    }

    public void setTimeToLive(int timeToLive) {
        try {
            multicastSocket.setTimeToLive(timeToLive);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addDestination(InetAddress destination, int rtpPort, int rtcpPort) {
        synchronized (syncObject) {
            destinationMap.put(destination, new int[]{rtpPort, rtcpPort});
        }
    }

    public void removeDestination(InetAddress destination) {
        synchronized (syncObject) {
            destinationMap.remove(destination);
        }
    }

    public int getRtpPort(InetAddress destination) {
        synchronized (syncObject) {
            int[] ports = (destination != null) ? destinationMap.get(destination) : null;
            return (ports != null) ? ports[0] : defaultRtpPort;
        }
    }

    public int getRtcpPort(InetAddress destination) {
        synchronized (syncObject) {
            int[] ports = (destination != null) ? destinationMap.get(destination) : null;
            return (ports != null) ? ports[1] : defaultRtcpPort;
        }
    }

    public int getLocalRtpPort() {
        return multicastSocket.getLocalPort();
    }

    public int getLocalRtcpPort() {
        return senderReport.getLocalPort();
    }

    synchronized public RtpData dequeueData() throws InterruptedException {
        return emptyRtpData.take();
    }

    synchronized public void enqueueData(RtpData rtpData) throws InterruptedException {
        mAverageBitrate.push(rtpData.getPacket().getLength());
        filledRtpData.put(rtpData);
    }

    public long getBitrate() {
        return mAverageBitrate.average();
    }

    public void stop() {
    }


    private class RtpThread extends Thread {

        @Override
        public void run() {
            Statistics stats = new Statistics(50, 3000);
            // Caches mCacheSize milliseconds of the stream in the FIFO.
            Utils.sleepSlient(mCacheSize);

            long delta = 0;
            boolean keepSending = true;
            long oldTimestamp = 0L;

            while (keepSending) {
                try {
                    RtpData rtpData = filledRtpData.take();

                    DatagramPacket packet = rtpData.getPacket();
                    long timestampUs = rtpData.getTimestampUs();

                    if (oldTimestamp != 0) {
                        // We use our knowledge of the clock rate of the stream and the difference between two timestamps to
                        // compute the time lapse that the packet represents.
                        if ((timestampUs - oldTimestamp) > 0) {
                            stats.push(timestampUs - oldTimestamp);
                            long d = stats.average() / 1000000;
                            //Log.e(TAG, "delay: " + d + " d: " + (mTimestamps[mBufferOut] - mOldTimestamp) / 1000000);
                            // We ensure that packets are sent at a constant and suitable rate no matter how the RtpSocket is used.
                            if (mCacheSize > 0) Thread.sleep(d);
                        } else if ((timestampUs - oldTimestamp) < 0) {
                            Log.e(TAG, "TS: " + timestampUs + " OLD: " + oldTimestamp);
                        }
                        delta += timestampUs - oldTimestamp;
                        if (delta > 500000000 || delta < 0) {
                            //Log.e(TAG, "permits: " + mBufferCommitted.availablePermits());
                            delta = 0;
                        }
                    }

                    oldTimestamp = timestampUs;
                    if (transport == TRANSPORT_UDP) {
                        synchronized (syncObject) {
                            for (Map.Entry<InetAddress, int[]> entry : destinationMap.entrySet()) {
                                InetAddress clientAddress = entry.getKey();
                                int rtpPort = entry.getValue()[0];
                                int rtcpPort = entry.getValue()[1];
                                packet.setAddress(clientAddress);
                                packet.setPort(rtpPort);
                                senderReport.setDestination(clientAddress, rtcpPort);
                                try {
                                    senderReport.update(packet.getLength(), (timestampUs / 100L) * (mClock / 1000L) / 10000L);
                                    multicastSocket.send(packet);
                                    Log.e(TAG, "sent to: " + packet.getAddress() + ":" + packet.getPort() + " " + packet.getLength());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    } else {
                        int len = packet.getLength();
                        mTcpHeader[2] = (byte) (len >> 8);
                        mTcpHeader[3] = (byte) (len & 0xFF);
                        try {
                            mOutputStream.write(mTcpHeader);
                            mOutputStream.write(packet.getData(), 0, len);
                        } catch (Exception e) {
                        }
                    }

                    emptyRtpData.put(rtpData);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    keepSending = false;
                }
            }
        }
    }

    /**
     * Computes an average bit rate.
     **/
    protected static class AverageBitrate {

        private final static long RESOLUTION = 200;

        private long mOldNow, mNow, mDelta;
        private long[] mElapsed, mSum;
        private int mCount, mIndex, mTotal;
        private int mSize;

        public AverageBitrate() {
            mSize = 5000 / ((int) RESOLUTION);
            reset();
        }

        public AverageBitrate(int delay) {
            mSize = delay / ((int) RESOLUTION);
            reset();
        }

        public void reset() {
            mSum = new long[mSize];
            mElapsed = new long[mSize];
            mNow = SystemClock.elapsedRealtime();
            mOldNow = mNow;
            mCount = 0;
            mDelta = 0;
            mTotal = 0;
            mIndex = 0;
        }

        public void push(int length) {
            mNow = SystemClock.elapsedRealtime();
            if (mCount > 0) {
                mDelta += mNow - mOldNow;
                mTotal += length;
                if (mDelta > RESOLUTION) {
                    mSum[mIndex] = mTotal;
                    mTotal = 0;
                    mElapsed[mIndex] = mDelta;
                    mDelta = 0;
                    mIndex++;
                    if (mIndex >= mSize) mIndex = 0;
                }
            }
            mOldNow = mNow;
            mCount++;
        }

        public int average() {
            long delta = 0, sum = 0;
            for (int i = 0; i < mSize; i++) {
                sum += mSum[i];
                delta += mElapsed[i];
            }
            //Log.d(TAG, "Time elapsed: "+delta);
            return (int) (delta > 0 ? 8000 * sum / delta : 0);
        }

    }

    /**
     * Computes the proper rate at which packets are sent.
     */
    protected static class Statistics {

        public final static String TAG = "Statistics";

        private int count = 500, c = 0;
        private float m = 0, q = 0;
        private long elapsed = 0;
        private long start = 0;
        private long duration = 0;
        private long period = 6000000000L;
        private boolean initoffset = false;

        public Statistics(int count, long period) {
            this.count = count;
            this.period = period * 1000000L;
        }

        public void push(long value) {
            duration += value;
            elapsed += value;
            if (elapsed > period) {
                elapsed = 0;
                long now = System.nanoTime();
                if (!initoffset || (now - start < 0)) {
                    start = now;
                    duration = 0;
                    initoffset = true;
                }
                value -= (now - start) - duration;
                //Log.d(TAG, "sum1: "+duration/1000000+" sum2: "+(now-start)/1000000+" drift: "+((now-start)-duration)/1000000+" v: "+value/1000000);
            }
            if (c < 40) {
                // We ignore the first 40 measured values because they may not be accurate
                c++;
                m = value;
            } else {
                m = (m * q + value) / (q + 1);
                if (q < count) q++;
            }
        }

        public long average() {
            long l = (long) m - 2000000;
            return l > 0 ? l : 0;
        }
    }
}
