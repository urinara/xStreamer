/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
 *
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.xvis.streaming.video;

import android.media.MediaRecorder;
import android.service.textservice.SpellCheckerService.Session;
import android.util.Base64;

import net.xvis.streaming.hw.EncoderDebugger;
import net.xvis.streaming.mp4.MP4Config;

import java.io.IOException;
import java.util.concurrent.Semaphore;

public class H264Stream extends VideoStream {

    public final static String TAG = "H264Stream";

    private Semaphore mLock = new Semaphore(0);
    private MP4Config mConfig;

    public H264Stream() {
        super();
        mimeType = "video/avc";
        mVideoEncoder = MediaRecorder.VideoEncoder.H264;
    }

    /**
     * Returns a description of the stream using SDP. It can then be included in an SDP file.
     */
    public synchronized String getSessionDescription() throws IllegalStateException {
        if (mConfig == null)
            throw new IllegalStateException("You need to call configure() first !");
        return "m=video " + String.valueOf(rtpSocket.getRtpPort(null)) + " RTP/AVP 96\r\n" +
                "a=rtpmap:96 H264/90000\r\n" +
                "a=fmtp:96 packetization-mode=1;profile-level-id=" + mConfig.getProfileLevel() + ";sprop-parameter-sets=" + mConfig.getB64SPS() + "," + mConfig.getB64PPS() + ";\r\n";
    }

    public synchronized void start() throws IllegalStateException, IOException {
        if (!streaming) {
            configure();
//            byte[] pps = Base64.decode(mConfig.getB64PPS(), Base64.NO_WRAP);
//            byte[] sps = Base64.decode(mConfig.getB64SPS(), Base64.NO_WRAP);
//            ((H264Packetizer) mPacketizer).setStreamParameters(pps, sps);
            super.start();
        }
    }

    /**
     * Configures the stream. You need to call this before calling {@link #getSessionDescription()} to apply
     * your configuration of the stream.
     */
    public synchronized void configure() throws IllegalStateException, IOException {
        super.configure();
        //mQuality = mRequestedQuality.clone();
        //mConfig = testH264();
    }
}
