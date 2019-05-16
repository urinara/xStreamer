package net.xvis.streaming.video;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import net.xvis.streaming.MediaStream;
import net.xvis.streaming.Utils;
import net.xvis.streaming.hw.CodecManager;

import java.io.IOException;

public abstract class VideoStream extends MediaStream {
    protected final static String TAG = "VideoStream";

    protected VideoQuality videoQuality = new VideoQuality(null);
    protected int mVideoEncoder = 0;
    protected int mRequestedOrientation = 0;
    protected int mOrientation = 0;
    protected boolean qualityUpdated;
    protected int supportedColorFormat = 0;
    protected String b64SPS;
    protected String b64PPS;

    public static final int[] YUV420_COLOR_FORMATS = {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar,
    };

    public void setVideoQuality(VideoQuality videoQuality) {
        if (!this.videoQuality.isSameQuality(videoQuality)) {
            this.videoQuality = new VideoQuality(videoQuality);
            qualityUpdated = true;
        }
    }

    public VideoQuality getVideoQuality() {
        return videoQuality;
    }

    @Override
    public synchronized void configure() throws IllegalStateException, IOException {
        super.configure();
        mOrientation = mRequestedOrientation;
    }

    @Override
    public synchronized void stop() {
        super.stop();
    }

    @Override
    protected void prepareMediaCodec() throws RuntimeException, IOException {
        Log.d(TAG, "Video encoded using the MediaCodec API with a buffer");

        // get the mediaCodec configured...
        //mediaCodec = CodecManager.findEncoder(mimeType, videoQuality.getWidth(), videoQuality.getHeight(), YUV420_COLOR_FORMATS[0]);
        mediaCodec = CodecManager.findEncoder(mimeType, 320, 240, YUV420_COLOR_FORMATS[0]);
        if (mediaCodec == null) {
            throw new RuntimeException("Unable to find the codec");
        }
        supportedColorFormat = mediaCodec.getInputFormat().getInteger(MediaFormat.KEY_COLOR_FORMAT);
        Log.e(TAG, " inputColorFormat=" + Utils.readableColorFormats(new int[] { supportedColorFormat }));
        b64PPS = CodecManager.mB64PPS;
        b64SPS = CodecManager.mB64SPS;
        Log.e(TAG, "PPS=" + b64PPS);
        Log.e(TAG, "SPS=" + b64SPS);
    }
}
