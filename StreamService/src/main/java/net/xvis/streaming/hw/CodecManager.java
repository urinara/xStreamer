package net.xvis.streaming.hw;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.util.Range;
import android.util.Size;

import net.xvis.streaming.Utils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class CodecManager {

    public final static String TAG = "CodecManager";
    public static String mB64PPS;
    public static String mB64SPS;

    private static int findGcd(int a, int b) {
        // gcd(a,b) = gcd(b, mod(a,b))
        while (b != 0) {
            int t = a;
            a = b;
            b = t % b;
        }
        return a;
    }

    private static int align(int length, int alignment) {
        int lengthAug = length + alignment - 1;
        return lengthAug - (lengthAug % alignment);
    }

    private static Size adjustSize(MediaCodecInfo.VideoCapabilities videoCaps, int width, int height) {
        float originalAspectRatio = (float) width / (float) height;
        Log.e(TAG, " ------ originalSize=" + width + "x" + height + ", aspectRatio=" + originalAspectRatio);

        Range<Integer> widthRange = videoCaps.getSupportedWidths();
        Range<Integer> heightRange = videoCaps.getSupportedHeights();
        int widthAlignment = videoCaps.getWidthAlignment();
        int heightAlignment = videoCaps.getHeightAlignment();

        int widthAdjusted = width;
        int widthAligned = align(widthAdjusted,  widthAlignment);
        Log.e(TAG, " ------ widthAligned=" + widthAligned);
        if (widthAligned > widthRange.getUpper()) {
            widthAligned = widthRange.getUpper();
            Log.e(TAG, " ------ width clipped to max=" + widthAligned);
        } else if (widthAligned < widthRange.getLower()) {
            widthAligned = widthRange.getLower();
            Log.e(TAG, " ------ width clipped to min=" + widthAligned);
        }

        int heightAdjusted = (int) ((float) widthAligned / originalAspectRatio + 0.5f);
        Log.e(TAG, " ------ heightAdjusted=" + heightAdjusted);
        int heightAligned = align(heightAdjusted, heightAlignment);
        Log.e(TAG, " ------ heightAligned=" + heightAligned);
        if (heightAligned > heightRange.getUpper()) {
            heightAligned = heightRange.getUpper();
            Log.e(TAG, " ------ height clipped to max=" + heightAligned);
        } else if (heightAligned < heightRange.getLower()) {
            heightAligned = heightRange.getLower();
            Log.e(TAG, " ------ height clipped to min=" + heightAligned);
        }

        widthAdjusted = (int) ((float) heightAligned * originalAspectRatio + 0.5f);
        widthAligned = align(widthAdjusted, widthAlignment);

        Log.e(TAG, " ------ finalAligned: " + widthAligned + "x" + heightAligned + "; AR=" + (float) widthAligned / (float) heightAligned);

        Range<Integer> heightRangeForMaxWidth = videoCaps.getSupportedHeightsFor(widthRange.getUpper());
        Range<Integer> heightRangeForMinWidth = videoCaps.getSupportedHeightsFor(widthRange.getLower());

        int maxBlocks = (widthRange.getUpper() / widthAlignment) * (heightRangeForMaxWidth.getUpper() / heightAlignment);
        int minBlocks = (widthRange.getLower() / widthAlignment) * (heightRangeForMinWidth.getLower() / heightAlignment);
        Log.e(TAG, " ------ blockRange=" + minBlocks + ", " + maxBlocks);

        int widthInBlocks = (widthAligned + widthAlignment - 1) / widthAlignment;
        int heightInBlocks = (heightAligned + heightAlignment - 1) / heightAlignment;
        int numBlocks = widthInBlocks * heightInBlocks;
        Log.e(TAG, " ------ size in blocks = " + widthInBlocks + "x" + heightInBlocks + ", numBlocks=" + numBlocks);

        int gcd = findGcd(widthInBlocks, heightInBlocks);
        int minWidthBlocks = widthInBlocks / gcd;
        int minHeightBlocks = heightInBlocks / gcd;
        Log.e(TAG, " ------ in fraction = " + minWidthBlocks + ":" + minHeightBlocks);

        int scale = gcd;
        if (numBlocks > maxBlocks) {
            scale = (int) Math.floor(Math.sqrt(maxBlocks / (double) (minWidthBlocks * minHeightBlocks)));
        }
        if (numBlocks < minBlocks) {
            scale = (int) Math.ceil(Math.sqrt(minBlocks / (double) (minWidthBlocks * minHeightBlocks)));
        }

        int newWidthInBlocks = scale * minWidthBlocks;
        int newHeightInBlocks = scale * minHeightBlocks;
        numBlocks = newWidthInBlocks * newHeightInBlocks;
        Log.e(TAG, " ------ new size in blocks = " + newWidthInBlocks + "x" + newHeightInBlocks + ", numBlocks=" + numBlocks);

        int newWidth = newWidthInBlocks * widthAlignment;
        int newHeight = newHeightInBlocks * heightAlignment;
        Log.e(TAG, " ------ new size in pixels = " + newWidth + "x" + newHeight + ", numPixels=" + newWidth * newHeight);
        Log.e(TAG, " ------ is the new size supported? " + videoCaps.isSizeSupported(newWidth, newHeight));
        return new Size(newWidth, newHeight);
    }

    public static List<MediaCodecInfo> findAvailableEncoders(String mime, int colorFormat) {
        List<MediaCodecInfo> mediaCodecInfoList = new ArrayList<>();
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
            if (!codecInfo.isEncoder()) {
                continue;
            }

            for (String type : codecInfo.getSupportedTypes()) {
                if (!type.equalsIgnoreCase(mime)) {
                    continue;
                }

                MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(type);
                if (!Utils.contains(capabilities.colorFormats, colorFormat)) {
                    continue;
                }

                MediaCodecInfo.VideoCapabilities videoCaps = capabilities.getVideoCapabilities();
                if (videoCaps == null) {
                    continue;
                }

                mediaCodecInfoList.add(codecInfo);
            }
        }

        return mediaCodecInfoList;
    }

    public static MediaCodec findEncoder(String mime, int width, int height, int colorFormat) {

        List<MediaCodecInfo> codecInfoList = findAvailableEncoders(mime, colorFormat);
        if (codecInfoList.size() == 0) {
            Log.e(TAG, "No supporting encode found");
            return null;
        }

        // see if any of the codec supports the original size
        MediaCodecInfo bestCodecInfo = null;
        Size bestSize = null;
        int bestBitRate = 0;
        for (MediaCodecInfo codecInfo : codecInfoList) {
            MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mime);
            MediaCodecInfo.VideoCapabilities videoCaps = capabilities.getVideoCapabilities();
            Log.e(TAG, codecInfo.getName() + ": "  + width + "x" + height);
            if (videoCaps.isSizeSupported(width, height)) {
                bestCodecInfo = codecInfo;
                bestSize = new Size(width, height);
                break;
            }
        }

        // try adjust the size if none supports the  original size.
        if (bestCodecInfo == null) {
            for (MediaCodecInfo codecInfo : codecInfoList) {
                MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mime);
                MediaCodecInfo.VideoCapabilities videoCaps = capabilities.getVideoCapabilities();
                Size adjustedSize = adjustSize(videoCaps, width, height);
                Log.e(TAG, codecInfo.getName() + ": sizeAdjusted = " + adjustedSize);
                if (videoCaps.isSizeSupported(adjustedSize.getWidth(), adjustedSize.getHeight())) {
                    bestCodecInfo = codecInfo;
                    bestSize = adjustedSize;
                    bestBitRate = videoCaps.getBitrateRange().getUpper();
                    break;
                }
            }
        }

        if (bestCodecInfo == null) {
            Log.e(TAG, "No Codec to support for size=" + width + "x" + height);
            return null;
        }

        Log.e(TAG, "Best chosen codecInfo = " + bestCodecInfo.getName() + " for size=" + bestSize + ", bitRate=" + bestBitRate);

        MediaCodec mediaCodec = null;
        try {
            MediaCodecInfo.CodecCapabilities capabilities = bestCodecInfo.getCapabilitiesForType(mime);
            MediaCodecInfo.VideoCapabilities videoCap = capabilities.getVideoCapabilities();
            // bit rate
            int bitRate = videoCap.getBitrateRange().getUpper();

            // frame rate
            Range<Double> frameRates = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                frameRates = videoCap.getAchievableFrameRatesFor(bestSize.getWidth(), bestSize.getHeight());
            }
            if (frameRates == null) {
                frameRates = videoCap.getSupportedFrameRatesFor(bestSize.getWidth(), bestSize.getHeight());
            }
            float frameRate = Math.min(30.f, frameRates.getUpper().floatValue());

            MediaFormat mediaFormat = MediaFormat.createVideoFormat(mime, bestSize.getWidth(), bestSize.getHeight());
            mediaFormat.setString(MediaFormat.KEY_MIME, mime);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            mediaFormat.setInteger(MediaFormat.KEY_WIDTH, bestSize.getWidth());
            mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, bestSize.getHeight());
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                mediaFormat.setString(MediaFormat.KEY_FRAME_RATE, null);
            } else {
                mediaFormat.setFloat(MediaFormat.KEY_FRAME_RATE, frameRate);
            }
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
            Log.e(TAG, mediaFormat.toString());

            mediaCodec = MediaCodec.createByCodecName(bestCodecInfo.getName());
            searchSPSandPPS(mediaCodec, mediaFormat);

            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return mediaCodec;
    }

    private static long searchSPSandPPS(MediaCodec mediaCodec, MediaFormat mediaFormat) {
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();

        MediaFormat _format = mediaCodec.getOutputFormat();
        ByteBuffer _spsb = _format.getByteBuffer("csd-0");
        ByteBuffer _ppsb = _format.getByteBuffer("csd-1");


        byte[] mSPS = null;
        byte[] mPPS = null;
        byte[] mData = null;

        Log.e(TAG, "Searching SPS and PPS");

        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        byte[] csd = new byte[128];
        int len = 0;
        int p = 4;
        int q = 4;
        long elapsed = 0;
        long now = System.nanoTime();

        while (elapsed < 3000000 && (mSPS == null || mPPS == null)) {
            // Some encoders won't give us the SPS and PPS unless they receive something to encode first...
            int bufferIndex = mediaCodec.dequeueInputBuffer(1000000); // 1 sec
            ByteBuffer inputBuffer = (bufferIndex >= 0) ? mediaCodec.getInputBuffer(bufferIndex) : null;
            if (inputBuffer != null) {
                if (mData == null) {
                    mData = new byte[inputBuffer.capacity()];
                }
                inputBuffer.clear();
                inputBuffer.put(mData, 0, mData.length);
                mediaCodec.queueInputBuffer(bufferIndex, 0, mData.length, timestamp(), 0);
            } else {
                Log.e(TAG, "No buffer available!");
            }

            // We are looking for the SPS and the PPS here. As always, Android is very inconsistent, I have observed that some
            // encoders will give those parameters through the MediaFormat object (that is the normal behaviour).
            // But some other will not, in that case we try to find a NAL unit of type 7 or 8 in the byte stream outputed by the encoder...

            int index = mediaCodec.dequeueOutputBuffer(info, 1000000);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.e(TAG, "Output format changed during SPS/PPS search");
                // The PPS and PPS should be there
                MediaFormat format = mediaCodec.getOutputFormat();
                ByteBuffer spsb = format.getByteBuffer("csd-0");
                ByteBuffer ppsb = format.getByteBuffer("csd-1");
                mSPS = new byte[spsb.capacity() - 4];
                spsb.position(4);
                spsb.get(mSPS, 0, mSPS.length);
                mPPS = new byte[ppsb.capacity() - 4];
                ppsb.position(4);
                ppsb.get(mPPS, 0, mPPS.length);
                break;
            } else if (index >= 0) {
                Log.e(TAG, "got buffer=" + index);
                len = info.size;
                if (len < 128) {
                    outputBuffers[index].get(csd, 0, len);
                    if (len > 0 && csd[0] == 0 && csd[1] == 0 && csd[2] == 0 && csd[3] == 1) {
                        // Parses the SPS and PPS, they could be in two different packets and in a different order
                        //depending on the phone so we don't make any assumption about that
                        while (p < len) {
                            while (!(csd[p + 0] == 0 && csd[p + 1] == 0 && csd[p + 2] == 0 && csd[p + 3] == 1) && p + 3 < len)
                                p++;
                            if (p + 3 >= len) p = len;
                            if ((csd[q] & 0x1F) == 7) {
                                mSPS = new byte[p - q];
                                System.arraycopy(csd, q, mSPS, 0, p - q);
                            } else {
                                mPPS = new byte[p - q];
                                System.arraycopy(csd, q, mPPS, 0, p - q);
                            }
                            p += 4;
                            q = p;
                        }
                    }
                }
                mediaCodec.releaseOutputBuffer(index, false);
            } else {
                Log.e(TAG, "Output buffer not valid index=" + index);
            }

            elapsed = timestamp() - now;
        }

        if (mPPS == null || mSPS == null) {
            Log.e(TAG, "Could not determine the SPS & PPS.");
        } else {
            mB64PPS = Base64.encodeToString(mPPS, 0, mPPS.length, Base64.NO_WRAP);
            mB64SPS = Base64.encodeToString(mSPS, 0, mSPS.length, Base64.NO_WRAP);
        }

        mediaCodec.stop();
        return elapsed;
    }

    private static long timestamp() {
        return System.nanoTime()/1000;
    }
}

