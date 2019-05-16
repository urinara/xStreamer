package net.xvis.streaming.rtcp;

import android.os.SystemClock;

import net.xvis.streaming.Utils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import static net.xvis.streaming.rtp.RtpSocket.TRANSPORT_TCP;
import static net.xvis.streaming.rtp.RtpSocket.TRANSPORT_UDP;

// Header
//  0                   1                   2                   3
//  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |V=2|P|    RC   |   PT=SR=200   |             length            |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |                         SSRC of sender                        |
// +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+

// Sender Info
// |              NTP timestamp, most significant word             |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |             NTP timestamp, least significant word             |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |                         RTP timestamp                         |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |                     sender's packet count                     |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |                      sender's octet count                     |
// +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+

// Report Block 1
// |                 SSRC_1 (SSRC of first source)                 |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// | fraction lost |       cumulative number of packets lost       |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |           extended highest sequence number received           |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |                      interarrival jitter                      |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |                         last SR (LSR)                         |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |                   delay since last SR (DLSR)                  |
// +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+

public class SenderReport {

    public static final int MTU = 1500;
    private static final int PACKET_LENGTH = 28;

    private MulticastSocket multicastSocket;
    private DatagramPacket packet;

    private int mTransport;
    private OutputStream mOutputStream = null;
    private byte[] buffer = new byte[MTU];

    private int senderSSRC;
    private double fractionLost;
    private long cumulativeNumPacketsLost;
    private long extendedHighestSeqNumReceived;
    private double interArrivalJitter;
    private double lastSR;
    private long delayLastSR;


    private long lastTimeRtcpPacketTransmitted; // tp
    private long currentTime; // tc
    private long nextTimeRtcpPacketScheduled; // tn
    private int numPrevSessionMembers; // pmemebers
    private int numCurrentSessionMembers; // members
    private int numSenders; // senders
    private int targetRtcpBw;// rtcp_bw target RTCP bandwidth
    private boolean sentData; // we_sent
    private int avgRtcpSize;// average compound RTCP packet size
    private boolean initialRtcp;// true if app has not yet sent an RTCP packet


    private int port = -1;

    private int mOctetCount = 0, mPacketCount = 0;
    private long delta, now, oldnow, interval;

    //It is RECOMMENDED that the fraction of the session bandwidth added for RTCP
    //   be fixed at 5%.    private int interval = 3000; // 3 seconds
    private byte mTcpHeader[];

    public SenderReport(int port) {

        mTransport = TRANSPORT_UDP;
        mTcpHeader = new byte[] {'$',0,0,PACKET_LENGTH};

        // [Version:2|P:1|RC:5] : version 2, padding, reception report count
        buffer[0] = (byte) 0x40; // 1000 0000
        // PT (Packet type): SR=200, RR=201, SDES=202, BYE=203, APP=204
        buffer[1] = (byte) 200;
        // Packet length
        Utils.writeValue(buffer, PACKET_LENGTH/4-1, 2, 4);
        // SSRC
        //Utils.writeValue(buffer, SSRC, 4, 8);
        try {
            multicastSocket = new MulticastSocket(port);
        } catch (IOException e) {
            // Very unlikely to happen. Means that all UDP ports are already being used
            throw new RuntimeException(e.getMessage());
        }
        packet = new DatagramPacket(buffer, 1);
        interval = 3000;
    }

    public void close() {
        multicastSocket.close();
    }

    /**
     * Sets the temporal interval between two RTCP Sender Reports.
     * Default interval is set to 3 seconds.
     * Set 0 to disable RTCP.
     * @param interval The interval in milliseconds
     */
    public void setInterval(long interval) {
        this.interval = interval;
    }

    /**
     * Updates the number of packets sent, and the total amount of data sent.
     * @param length The length of the packet
     * @param rtpts
     *            The RTP timestamp.
     * @throws IOException
     **/
    public void update(int length, long rtpts) throws IOException {
        mPacketCount += 1;
        mOctetCount += length;
        Utils.writeValue(buffer, mPacketCount, 20, 24);
        Utils.writeValue(buffer, mOctetCount, 24, 28);

        now = SystemClock.elapsedRealtime();
        delta += oldnow != 0 ? now-oldnow : 0;
        oldnow = now;
        if (interval>0 && delta>=interval) {
            // We send a Sender Report
            send(System.nanoTime(), rtpts);
            delta = 0;
        }

    }

    public void setSSRC(int ssrc) {
        //this.mSSRC = ssrc;
        Utils.writeValue(buffer, ssrc,4,8);
        mPacketCount = 0;
        mOctetCount = 0;
        Utils.writeValue(buffer, mPacketCount, 20, 24);
        Utils.writeValue(buffer, mOctetCount, 24, 28);
    }

    public void setDestination(InetAddress dest, int rtcpPort) {
        mTransport = TRANSPORT_UDP;
        packet.setPort(rtcpPort);
        packet.setAddress(dest);
    }

    /**
     * If a TCP is used as the transport protocol for the RTP session,
     * the output stream to which RTP packets will be written to must
     * be specified with this method.
     */
    public void setOutputStream(OutputStream os, byte channelIdentifier) {
        mTransport = TRANSPORT_TCP;
        mOutputStream = os;
        mTcpHeader[1] = channelIdentifier;
    }

    public int getPort() {
        return port;
    }

    public int getLocalPort() {
        return multicastSocket.getLocalPort();
    }

    public int getSSRC() {
        return 0;//mSSRC;
    }

    /**
     * Resets the reports (total number of bytes sent, number of packets sent, etc.)
     */
    public void reset() {
        mPacketCount = 0;
        mOctetCount = 0;
        Utils.writeValue(buffer, mPacketCount, 20, 24);
        Utils.writeValue(buffer, mOctetCount, 24, 28);
        delta = now = oldnow = 0;
    }

    /**
     * Sends the RTCP packet over the network.
     *
     * @param ntpts
     *            the NTP timestamp.
     * @param rtpts
     *            the RTP timestamp.
     */
    private void send(long ntpts, long rtpts) throws IOException {
        long hb = ntpts/1000000000;
        long lb = ( ( ntpts - hb*1000000000 ) * 4294967296L )/1000000000;
        Utils.writeValue(buffer, hb, 8, 12);
        Utils.writeValue(buffer, lb, 12, 16);
        Utils.writeValue(buffer, rtpts, 16, 20);
        if (mTransport == TRANSPORT_UDP) {
            packet.setLength(PACKET_LENGTH);
            //multicastSocket.send(packet);
            //multicastSocket.receive(packet);
        } else {
            synchronized (mOutputStream) {
                try {
                    //mOutputStream.write(mTcpHeader);
                    //mOutputStream.write(mBuffer, 0, PACKET_LENGTH);
                } catch (Exception e) {}
            }
        }
    }
}
