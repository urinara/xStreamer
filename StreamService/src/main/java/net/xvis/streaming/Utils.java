package net.xvis.streaming;


import android.media.MediaCodecInfo;
import android.util.Log;
import android.util.Size;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {
    private static Map<Integer, String> colorFormatMap;

    public static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }

    public static void sleepSlient(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static boolean contains(int[] array, int value) {
        for (int element : array) {
            if (element == value) {
                return true;
            }
        }
        return false;
    }

    // Write value in network byte order (big-endian)
    public static void writeValue(byte[] buffer, long value, int begin, int end) {
        if (buffer == null || buffer.length == 0 || begin >= end) {
            return;
        }
        for (int i = end - 1; i >= begin; i--) {
            buffer[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
    }

    // Read value in network byte order (big-endian)
    public static long readValue(byte[] buffer, int begin, int end) {
        long value = 0;
        if (buffer == null || buffer.length == 0 || begin >= end) {
            return value;
        }
        for (int i = begin; i < end; i++) {
            value <<= 8;
            value = value | buffer[i];
        }
        return value;
    }

    public static String readableColorFormats(List<Integer> colorFormats) {
        if (colorFormats == null || colorFormats.size() == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int colorFormat : colorFormats) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            String format = getColorFormats().get(colorFormat);
            sb.append(isNullOrEmpty(format) ? String.valueOf(colorFormat) : format);
        }
        sb.insert(0, "[");
        sb.append("]");
        return sb.toString();
    }

    public static String readableColorFormats(int[] colorFormats) {
        if (colorFormats == null || colorFormats.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int colorFormat : colorFormats) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            String format = getColorFormats().get(colorFormat);
            sb.append(isNullOrEmpty(format) ? String.valueOf(colorFormat) : format);
        }
        sb.insert(0, "[");
        sb.append("]");
        return sb.toString();
    }

    public static String printCodecInfo(MediaCodecInfo codecInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("name: ").append(codecInfo.getName()).append("\n");
        sb.append(" - isEncoder=").append(codecInfo.isEncoder()).append("\n");
        sb.append(" - supportedTypes=").append(Arrays.toString(codecInfo.getSupportedTypes())).append("\n");

        for (String type : codecInfo.getSupportedTypes()) {
            sb.append(" -- type=").append(type).append("\n");
            MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(type);
            sb.append(" -- colorFormats=").append(Utils.readableColorFormats(capabilities.colorFormats)).append("\n");
            sb.append(" -- mime=").append(capabilities.getMimeType()).append("\n");
            MediaCodecInfo.VideoCapabilities videoCaps = capabilities.getVideoCapabilities();
            if (videoCaps != null) {
                sb.append(" -- bitrate=").append(videoCaps.getBitrateRange()).append("\n");
                sb.append(" -- supportedFrameRates=").append(videoCaps.getSupportedFrameRates()).append("\n");
                sb.append(" -- supportedWidths=").append(videoCaps.getSupportedWidths()).append("\n");
                sb.append(" -- supportedHeights=").append(videoCaps.getSupportedHeights()).append("\n");
                sb.append(" -- widthAlignment=").append(videoCaps.getWidthAlignment()).append("\n");
                sb.append(" -- heightAlignment=").append(videoCaps.getHeightAlignment()).append("\n");
            }
        }

        return sb.toString();
    }

    public static Map<Integer, String> getColorFormats() {
        if (colorFormatMap != null) {
            return colorFormatMap;
        }

        colorFormatMap = new HashMap<>();
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format12bitRGB444, "12bitRGB444");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatMonochrome, "Monochrome");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format8bitRGB332, "8bitRGB332");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format12bitRGB444, "12bitRGB444");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format16bitARGB4444, "16bitARGB4444");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format16bitARGB1555, "16bitARGB1555");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format16bitRGB565, "16bitRGB565");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format16bitBGR565, "16bitBGR565");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format18bitRGB666, "18bitRGB666");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format18bitARGB1665, "18bitARGB1665");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format19bitARGB1666, "19bitARGB1666");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format24bitRGB888, "24bitRGB888");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format24bitBGR888, "24bitBGR888");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format24bitARGB1887, "24bitARGB1887");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format25bitARGB1888, "25bitARGB1888");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format32bitBGRA8888, "32bitBGRA8888");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format32bitARGB8888, "32bitARGB8888");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV411Planar, "YUV411Planar");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV411PackedPlanar, "YUV411PackedPlanar");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar, "YUV420Planar");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar, "YUV420PackedPlanar");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar, "YUV420SemiPlanar");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422Planar, "YUV422Planar");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422PackedPlanar, "YUV422PackedPlanar");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422SemiPlanar, "YUV422SemiPlanar");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatYCbYCr, "YCbYCr");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatYCrYCb, "YCrYCb");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatCbYCrY, "CbYCrY");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatCrYCbY, "CrYCbY");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV444Interleaved, "YUV444Interleaved");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatRawBayer8bit, "RawBayer8bit");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatRawBayer10bit, "RawBayer10bit");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatRawBayer8bitcompressed, "RawBayer8bitcompressed");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatL2, "L2");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatL4, "L4");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatL8, "L8");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatL16, "L16");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatL24, "L24");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatL32, "L32");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar, "YUV420PackedSemiPlanar");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422PackedSemiPlanar, "YUV422PackedSemiPlanar");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format18BitBGR666, "18BitBGR666");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format24BitARGB6666, "24BitARGB6666");
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format24BitABGR6666, "24BitABGR6666");

        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar, "COLOR_TI_FormatYUV420PackedSemiPlanar");// 2130706688 (0x7f000100)
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar, "COLOR_QCOM_FormatYUV420SemiPlanar"); // 2141391872 (0x7fa30c00)
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible, "YUV420Flexible"); // 2135033992 (0x7f420888)
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface, "Surface"); // 2130708361 (0x7f000789)

        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_Format32bitABGR8888, "32bitABGR8888");// 2130747392 (0x7f00a000)
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatRGBAFlexible, "RGBAFlexible");// 2134288520 (0x7f36a888)
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatRGBFlexible, "RGBFlexible"); // 2134292616 (0x7f36b888)
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422Flexible, "YUV422Flexible"); // 2135042184 (0x7f422888)
        colorFormatMap.put(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV444Flexible, "YUV444Flexible"); // 2135181448 (0x7f444888)
        return colorFormatMap;
    }
}
