package net.xvis.streaming.hw;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

@SuppressLint("NewApi")
public class EncoderDebugger {

    public final static String TAG = "EncoderDebugger";
    private final static int BITRATE = 1000000;
    private final static int FRAME_RATE = 20;
    private final static String MIME_TYPE = "video/avc";
    private final static int NB_DECODED = 10;
    private final static int NB_ENCODED = 16;

    private int mDecoderColorFormat;
    private int mEncoderColorFormat;
    private String mDecoderName;
    private String mEncoderName;
    private String mErrorLog;
    private MediaCodec mEncoder;
    private MediaCodec mDecoder;
    private int mWidth;
    private int mHeight;
    private int mSize;
    private byte[] mSPS;
    private byte[] mPPS;
    private byte[] mData;
    private byte[] mInitialImage;
    private MediaFormat mDecOutputFormat;
    private NV21Convertor mNV21;
    private byte[][] mVideo;
    private byte[][] mDecodedVideo;
    private String mB64PPS;
    private String mB64SPS;

    public synchronized static EncoderDebugger debug(int width, int height) {
        EncoderDebugger debugger = new EncoderDebugger(width, height);
        debugger.debug();
        return debugger;
    }

    public String getB64PPS() {
        return mB64PPS;
    }

    public String getB64SPS() {
        return mB64SPS;
    }

    public String getEncoderName() {
        return mEncoderName;
    }

    public int getEncoderColorFormat() {
        return mEncoderColorFormat;
    }

    public NV21Convertor getNV21Convertor() {
        return mNV21;
    }

    public String getErrorLog() {
        return mErrorLog;
    }

    private EncoderDebugger(int width, int height) {
        mWidth = width;
        mHeight = height;
        mSize = width * height;
        reset();
    }

    private void reset() {
        mNV21 = new NV21Convertor();
        mVideo = new byte[NB_ENCODED][];
        mDecodedVideo = new byte[NB_DECODED][];
        mErrorLog = "";
        mPPS = null;
        mSPS = null;
    }

    private void debug() {
        Log.e(TAG, ">>>> Testing the phone for resolution " + mWidth + "x" + mHeight);
        // Builds a list of available encoders and decoders we may be able to use
        // because they support some nice color formats

//
//        // Tries available encoders
//        for (int i = 0; i < encoders.length; i++) {
//            for (int j = 0; j < encoders[i].formats.length; j++) {
//                reset();
//
//                mEncoderName = encoders[i].name;
//                mEncoderColorFormat = encoders[i].formats[j];
//
//                Log.e(TAG, ">> Test " + (n++) + "/" + count + ": " + mEncoderName + " with color format " + mEncoderColorFormat + " at " + mWidth + "x" + mHeight);
//
//                // Converts from NV21 to YUV420 with the specified parameters
//                mNV21.setSize(mWidth, mHeight);
//                mNV21.setSliceHeigth(mHeight);
//                mNV21.setStride(mWidth);
//                mNV21.setYPadding(0);
//                mNV21.setEncoderColorFormat(mEncoderColorFormat);
//
//                // /!\ NV21Convertor can directly modify the input
//                createTestImage();
//                mData = mNV21.convert(mInitialImage);
//
//                try {
//                    // Starts the encoder
//                    configureEncoder();
//                    searchSPSandPPS();
//
//                    Log.e(TAG, "SPS and PPS in b64: SPS=" + mB64SPS + ", PPS=" + mB64PPS);
//
//                    // Feeds the encoder with an image repeatedly to produce some NAL units
//                    encode();
//
//                    // We now try to decode the NALs with decoders available on the phone
//                    boolean decoded = false;
//                    for (int k = 0; k < decoders.length && !decoded; k++) {
//                        for (int l = 0; l < decoders[k].formats.length && !decoded; l++) {
//                            mDecoderName = decoders[k].name;
//                            mDecoderColorFormat = decoders[k].formats[l];
//                            try {
//                                configureDecoder();
//                            } catch (Exception e) {
//                                Log.d(TAG, mDecoderName + " can't be used with " + mDecoderColorFormat + " at " + mWidth + "x" + mHeight);
//                                releaseDecoder();
//                                break;
//                            }
//                            try {
//                                decode(true);
//                                Log.d(TAG, mDecoderName + " successfully decoded the NALs (color format " + mDecoderColorFormat + ")");
//                                decoded = true;
//                            } catch (Exception e) {
//                                Log.e(TAG, mDecoderName + " failed to decode the NALs");
//                                e.printStackTrace();
//                            } finally {
//                                releaseDecoder();
//                            }
//                        }
//                    }
//
//                    if (!decoded)
//                        throw new RuntimeException("Failed to decode NALs from the encoder.");
//
//                    // Compares the image before and after
//                    if (!compareLumaPanes()) {
//                        // TODO: try again with a different stride
//                        // TODO: try again with the "stride" param
//                        //throw new RuntimeException("It is likely that stride!=width");
//                    }
//
//                    int padding;
//                    if ((padding = checkPaddingNeeded()) > 0) {
//                        if (padding < 4096) {
//                            Log.d(TAG, "Some padding is needed: " + padding);
//                            mNV21.setYPadding(padding);
//                            createTestImage();
//                            mData = mNV21.convert(mInitialImage);
//                            encodeDecode();
//                        } else {
//                            // TODO: try again with a different sliceHeight
//                            // TODO: try again with the "slice-height" param
//                            //throw new RuntimeException("It is likely that sliceHeight!=height");
//                        }
//                    }
//
//                    createTestImage();
//                    if (!compareChromaPanes(false)) {
//                        if (compareChromaPanes(true)) {
//                            mNV21.setColorPanesReversed(true);
//                            Log.d(TAG, "U and V pane are reversed");
//                        } else {
//                            //throw new RuntimeException("Incorrect U or V pane...");
//                        }
//                    }
//
//                    //saveTestResult(true);
//                    Log.v(TAG, "The encoder " + mEncoderName + " is usable with resolution " + mWidth + "x" + mHeight);
//                    return;
//
//                } catch (Exception e) {
//                    StringWriter sw = new StringWriter();
//                    PrintWriter pw = new PrintWriter(sw);
//                    e.printStackTrace(pw);
//                    String stack = sw.toString();
//                    String str = "Encoder " + mEncoderName + " cannot be used with color format " + mEncoderColorFormat;
//                    Log.e(TAG, str, e);
//                    mErrorLog += str + "\n" + stack;
//                    e.printStackTrace();
//                } finally {
//                    releaseEncoder();
//                }
//            }
//        }

        Log.e(TAG, "No usable encoder were found on the phone for resolution " + mWidth + "x" + mHeight);
        throw new RuntimeException("No usable encoder were found on the phone for resolution " + mWidth + "x" + mHeight);

    }

    /**
     * Creates the test image that will be used to feed the encoder.
     */
    private void createTestImage() {
        mInitialImage = new byte[3 * mSize / 2];
        for (int i = 0; i < mSize; i++) {
            mInitialImage[i] = (byte) (40 + i % 199);
        }
        for (int i = mSize; i < 3 * mSize / 2; i += 2) {
            mInitialImage[i] = (byte) (40 + i % 200);
            mInitialImage[i + 1] = (byte) (40 + (i + 99) % 200);
        }

    }

    /**
     * Compares the Y pane of the initial image, and the Y pane
     * after having encoded & decoded the image.
     */
    private boolean compareLumaPanes() {
        int d, e, f = 0;
        for (int j = 0; j < NB_DECODED; j++) {
            for (int i = 0; i < mSize; i += 10) {
                d = (mInitialImage[i] & 0xFF) - (mDecodedVideo[j][i] & 0xFF);
                e = (mInitialImage[i + 1] & 0xFF) - (mDecodedVideo[j][i + 1] & 0xFF);
                d = d < 0 ? -d : d;
                e = e < 0 ? -e : e;
                if (d > 50 && e > 50) {
                    mDecodedVideo[j] = null;
                    f++;
                    break;
                }
            }
        }
        return f <= NB_DECODED / 2;
    }

    private int checkPaddingNeeded() {
        int i = 0, j = 3 * mSize / 2 - 1, max = 0;
        int[] r = new int[NB_DECODED];
        for (int k = 0; k < NB_DECODED; k++) {
            if (mDecodedVideo[k] != null) {
                i = 0;
                while (i < j && (mDecodedVideo[k][j - i] & 0xFF) < 50) i += 2;
                if (i > 0) {
                    r[k] = ((i >> 6) << 6);
                    max = r[k] > max ? r[k] : max;
                    Log.e(TAG, "Padding needed: " + r[k]);
                } else {
                    Log.v(TAG, "No padding needed.");
                }
            }
        }

        return ((max >> 6) << 6);
    }

    /**
     * Compares the U or V pane of the initial image, and the U or V pane
     * after having encoded & decoded the image.
     */
    private boolean compareChromaPanes(boolean crossed) {
        int d, f = 0;

        for (int j = 0; j < NB_DECODED; j++) {
            if (mDecodedVideo[j] != null) {
                // We compare the U and V pane before and after
                if (!crossed) {
                    for (int i = mSize; i < 3 * mSize / 2; i += 1) {
                        d = (mInitialImage[i] & 0xFF) - (mDecodedVideo[j][i] & 0xFF);
                        d = d < 0 ? -d : d;
                        if (d > 50) {
                            //if (VERBOSE) Log.e(TAG,"BUG "+(i-mSize)+" d "+d);
                            f++;
                            break;
                        }
                    }

                    // We compare the V pane before with the U pane after
                } else {
                    for (int i = mSize; i < 3 * mSize / 2; i += 2) {
                        d = (mInitialImage[i] & 0xFF) - (mDecodedVideo[j][i + 1] & 0xFF);
                        d = d < 0 ? -d : d;
                        if (d > 50) {
                            f++;
                        }
                    }
                }
            }
        }
        return f <= NB_DECODED / 2;
    }

    /**
     * Converts the image obtained from the decoder to NV21.
     */
    private void convertToNV21(int k) {
        byte[] buffer = new byte[3 * mSize / 2];

        int stride = mWidth, sliceHeight = mHeight;
        int colorFormat = mDecoderColorFormat;
        boolean planar = false;

        if (mDecOutputFormat != null) {
            MediaFormat format = mDecOutputFormat;
            if (format != null) {
                if (format.containsKey("slice-height")) {
                    sliceHeight = format.getInteger("slice-height");
                    if (sliceHeight < mHeight) sliceHeight = mHeight;
                }
                if (format.containsKey("stride")) {
                    stride = format.getInteger("stride");
                    if (stride < mWidth) stride = mWidth;
                }
                if (format.containsKey(MediaFormat.KEY_COLOR_FORMAT)) {
                    if (format.getInteger(MediaFormat.KEY_COLOR_FORMAT) > 0) {
                        colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                    }
                }
            }
        }

        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                planar = false;
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                planar = true;
                break;
        }

        for (int i = 0; i < mSize; i++) {
            if (i % mWidth == 0) i += stride - mWidth;
            buffer[i] = mDecodedVideo[k][i];
        }

        if (!planar) {
            for (int i = 0, j = 0; j < mSize / 4; i += 1, j += 1) {
                if (i % mWidth / 2 == 0) i += (stride - mWidth) / 2;
                buffer[mSize + 2 * j + 1] = mDecodedVideo[k][stride * sliceHeight + 2 * i];
                buffer[mSize + 2 * j] = mDecodedVideo[k][stride * sliceHeight + 2 * i + 1];
            }
        } else {
            for (int i = 0, j = 0; j < mSize / 4; i += 1, j += 1) {
                if (i % mWidth / 2 == 0) i += (stride - mWidth) / 2;
                buffer[mSize + 2 * j + 1] = mDecodedVideo[k][stride * sliceHeight + i];
                buffer[mSize + 2 * j] = mDecodedVideo[k][stride * sliceHeight * 5 / 4 + i];
            }
        }

        mDecodedVideo[k] = buffer;

    }

    /**
     * Instantiates and starts the encoder.
     */
    private void configureEncoder() {
        try {
            mEncoder = MediaCodec.createByCodecName(mEncoderName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mEncoderColorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoder.start();
    }

    private void releaseEncoder() {
        if (mEncoder != null) {
            try {
                mEncoder.stop();
            } catch (Exception ignore) {
            }
            try {
                mEncoder.release();
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Instantiates and starts the decoder.
     */
    private void configureDecoder() {
        byte[] prefix = new byte[]{0x00, 0x00, 0x00, 0x01};

        ByteBuffer csd0 = ByteBuffer.allocate(4 + mSPS.length + 4 + mPPS.length);
        csd0.put(new byte[]{0x00, 0x00, 0x00, 0x01});
        csd0.put(mSPS);
        csd0.put(new byte[]{0x00, 0x00, 0x00, 0x01});
        csd0.put(mPPS);

        try {
            mDecoder = MediaCodec.createByCodecName(mDecoderName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        mediaFormat.setByteBuffer("csd-0", csd0);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mDecoderColorFormat);
        mDecoder.configure(mediaFormat, null, null, 0);
        mDecoder.start();

        ByteBuffer[] decInputBuffers = mDecoder.getInputBuffers();

        int decInputIndex = mDecoder.dequeueInputBuffer(1000000 / FRAME_RATE);
        if (decInputIndex >= 0) {
            decInputBuffers[decInputIndex].clear();
            decInputBuffers[decInputIndex].put(prefix);
            decInputBuffers[decInputIndex].put(mSPS);
            mDecoder.queueInputBuffer(decInputIndex, 0, decInputBuffers[decInputIndex].position(), timestamp(), 0);
        } else {
            Log.e(TAG, "No buffer available !");
        }

        decInputIndex = mDecoder.dequeueInputBuffer(1000000 / FRAME_RATE);
        if (decInputIndex >= 0) {
            decInputBuffers[decInputIndex].clear();
            decInputBuffers[decInputIndex].put(prefix);
            decInputBuffers[decInputIndex].put(mPPS);
            mDecoder.queueInputBuffer(decInputIndex, 0, decInputBuffers[decInputIndex].position(), timestamp(), 0);
        } else {
            Log.e(TAG, "No buffer available !");
        }


    }

    private void releaseDecoder() {
        if (mDecoder != null) {
            try {
                mDecoder.stop();
            } catch (Exception ignore) {
            }
            try {
                mDecoder.release();
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Tries to obtain the SPS and the PPS for the encoder.
     */
    private long searchSPSandPPS() {
        Log.e(TAG, "Searching SPS and PPS");

        ByteBuffer[] outputBuffers = mEncoder.getOutputBuffers();
        BufferInfo info = new BufferInfo();
        byte[] csd = new byte[128];
        int len = 0;
        int p = 4;
        int q = 4;
        long elapsed = 0;
        long now = timestamp();

        while (elapsed < 3000000 && (mSPS == null || mPPS == null)) {
            // Some encoders won't give us the SPS and PPS unless they receive something to encode first...
            int bufferIndex = mEncoder.dequeueInputBuffer(1000000 / FRAME_RATE);
            ByteBuffer inputBuffer = (bufferIndex >= 0) ? mEncoder.getInputBuffer(bufferIndex) : null;
            if (inputBuffer != null) {
                check(inputBuffer.capacity() >= mData.length, "The input buffer is not big enough.");
                inputBuffer.clear();
                inputBuffer.put(mData, 0, mData.length);
                mEncoder.queueInputBuffer(bufferIndex, 0, mData.length, timestamp(), 0);
            } else {
                Log.e(TAG, "No buffer available!");
            }

            // We are looking for the SPS and the PPS here. As always, Android is very inconsistent, I have observed that some
            // encoders will give those parameters through the MediaFormat object (that is the normal behaviour).
            // But some other will not, in that case we try to find a NAL unit of type 7 or 8 in the byte stream outputed by the encoder...

            int index = mEncoder.dequeueOutputBuffer(info, 1000000 / FRAME_RATE);

            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.e(TAG, "Output format changed during SPS/PPS search");
                // The PPS and PPS should be there
                MediaFormat format = mEncoder.getOutputFormat();
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
                mEncoder.releaseOutputBuffer(index, false);
            } else {
                Log.e(TAG, "Output buffer not valid index=" + index);
            }

            elapsed = timestamp() - now;
        }

        check(mPPS != null & mSPS != null, "Could not determine the SPS & PPS.");
        mB64PPS = Base64.encodeToString(mPPS, 0, mPPS.length, Base64.NO_WRAP);
        mB64SPS = Base64.encodeToString(mSPS, 0, mSPS.length, Base64.NO_WRAP);

        return elapsed;
    }

    private long encode() {
        int n = 0;
        long elapsed = 0;
        long now = timestamp();
        BufferInfo bufferInfo = new BufferInfo();

        while (elapsed < 5000000) {
            // Feeds the encoder with an image
            int inputIndex = mEncoder.dequeueInputBuffer(1000000); // 1 sec
            ByteBuffer encInputBuffer = (inputIndex >= 0) ? mEncoder.getInputBuffer(inputIndex) : null;
            if (encInputBuffer != null) {
                check(encInputBuffer.capacity() >= mData.length, "The input buffer is not big enough.");
                encInputBuffer.clear();
                encInputBuffer.put(mData, 0, mData.length);
                mEncoder.queueInputBuffer(inputIndex, 0, mData.length, timestamp(), 0);
            } else {
                //Log.e(TAG, "No input buffer available for encoding!");
            }

            // Tries to get a NAL unit
            int outputIndex = mEncoder.dequeueOutputBuffer(bufferInfo, 1000000 / FRAME_RATE);
            ByteBuffer encOutputBuffer = (outputIndex >= 0) ? mEncoder.getOutputBuffer(outputIndex) : null;
            if (encOutputBuffer != null) {
                mVideo[n] = new byte[bufferInfo.size];
                encOutputBuffer.clear();
                encOutputBuffer.get(mVideo[n++], 0, bufferInfo.size);
                mEncoder.releaseOutputBuffer(outputIndex, false);
                if (n >= NB_ENCODED) {
                    flushMediaCodec(mEncoder);
                    return elapsed;
                }
                //Log.e(TAG, "n < NB_ENCODED, n=" + n);
            } else {
                //Log.e(TAG, "No output buffer available from encoder");
            }

            elapsed = timestamp() - now;
        }

        throw new RuntimeException("The encoder is too slow.");
    }

    /**
     * @param withPrefix If set to true, the decoder will be fed with NALs preceeded with 0x00000001.
     * @return How long it took to decode all the NALs
     */
    private long decode(boolean withPrefix) {
        int n = 0, i = 0, j = 0;
        long elapsed = 0, now = timestamp();
        BufferInfo info = new BufferInfo();

        while (elapsed < 3000000) {
            if (i < NB_ENCODED) {
                int inputIndex = mDecoder.dequeueInputBuffer(1000000 / FRAME_RATE);
                ByteBuffer inputBuffer = (inputIndex >= 0) ? mDecoder.getInputBuffer(inputIndex) : null;
                //Log.e(TAG, "decoder inputBuffer index=" + inputIndex + ", i=" + i);
                if (inputBuffer != null) {
                    int l1 = inputBuffer.capacity();
                    int l2 = mVideo[i].length;
                    inputBuffer.clear();

                    if ((withPrefix && hasPrefix(mVideo[i])) || (!withPrefix && !hasPrefix(mVideo[i]))) {
                        check(l1 >= l2, "The decoder input buffer is not big enough (nal=" + l2 + ", capacity=" + l1 + ").");
                        inputBuffer.put(mVideo[i], 0, mVideo[i].length);
                    } else if (withPrefix && !hasPrefix(mVideo[i])) {
                        check(l1 >= l2 + 4, "The decoder input buffer is not big enough (nal=" + (l2 + 4) + ", capacity=" + l1 + ").");
                        inputBuffer.put(new byte[]{0, 0, 0, 1});
                        inputBuffer.put(mVideo[i], 0, mVideo[i].length);
                    } else if (!withPrefix && hasPrefix(mVideo[i])) {
                        check(l1 >= l2 - 4, "The decoder input buffer is not big enough (nal=" + (l2 - 4) + ", capacity=" + l1 + ").");
                        inputBuffer.put(mVideo[i], 4, mVideo[i].length - 4);
                    }

                    mDecoder.queueInputBuffer(inputIndex, 0, l2, timestamp(), 0);
                    i++;
                } else {
                    //Log.d(TAG, "No input buffer available from decoder!");
                }
            }

            // Tries to get a decoded image
            int outputIndex = mDecoder.dequeueOutputBuffer(info, 1000000 / FRAME_RATE);
            ByteBuffer outputBuffer = (outputIndex >= 0) ? mDecoder.getOutputBuffer(outputIndex) : null;

            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.e(TAG, "decoder output format changed");
                mDecOutputFormat = mDecoder.getOutputFormat();
                Log.e(TAG, "outputFormat=" + mDecOutputFormat.toString());
            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.e(TAG, "decoder output try again");
            } else if (outputBuffer != null) {
                //Log.e(TAG, "outputBuffer index=" + outputIndex + ", n=" + n + ", j=" + j);
                if (n > 2) {
                    // We have successfully encoded and decoded an image !
                    int length = info.size;
                    mDecodedVideo[j] = new byte[length];
                    outputBuffer.clear();
                    outputBuffer.get(mDecodedVideo[j], 0, length);
                    // Converts the decoded frame to NV21
                    convertToNV21(j);
                    if (j >= NB_DECODED - 1) {
                        flushMediaCodec(mDecoder);
                        Log.v(TAG, "Decoding " + n + " frames took " + elapsed / 1000 + " ms");
                        return elapsed;
                    }
                    j++;
                }
                mDecoder.releaseOutputBuffer(outputIndex, false);
                n++;
            } else {
                //Log.e(TAG, "outputBuffer index invalid value=" + outputIndex);
            }
            elapsed = timestamp() - now;
        }

        throw new RuntimeException("The decoder did not decode anything.");

    }

    /**
     * Makes sure the NAL has a header or not.
     *
     */
    private boolean hasPrefix(byte[] nal) {
        if (nal[0] == 0 && nal[1] == 0 && nal[2] == 0 && nal[3] == 0x01)
            return true;
        else
            return false;
    }

    private void encodeDecode() {
        encode();
        try {
            configureDecoder();
            decode(true);
        } finally {
            releaseDecoder();
        }
    }

    private void flushMediaCodec(MediaCodec mc) {
        int index = 0;
        BufferInfo info = new BufferInfo();
        while (index != MediaCodec.INFO_TRY_AGAIN_LATER) {
            index = mc.dequeueOutputBuffer(info, 1000000 / FRAME_RATE);
            if (index >= 0) {
                mc.releaseOutputBuffer(index, false);
            }
        }
    }

    private void check(boolean cond, String message) {
        if (!cond) {
            Log.e(TAG, message);
            throw new IllegalStateException(message);
        }
    }

    private long timestamp() {
        return System.nanoTime() / 1000;
    }
}
