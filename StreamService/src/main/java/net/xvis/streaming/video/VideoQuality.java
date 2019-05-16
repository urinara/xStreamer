package net.xvis.streaming.video;

import android.provider.MediaStore;

public class VideoQuality {
    public final static String TAG = "VideoQuality";

    private int frameRate = 20;
    private int bitRate = 500000;
    private int width = 176;
    private int height = 144;

    public VideoQuality(VideoQuality videoQuality) {
        if (videoQuality != null) {
            this.frameRate = videoQuality.frameRate;
            this.bitRate = videoQuality.bitRate;
            this.width = videoQuality.width;
            this.height = videoQuality.height;
        }
    }

    public VideoQuality(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public VideoQuality(int width, int height, int frameRate, int bitRate) {
        this.frameRate = frameRate;
        this.bitRate = bitRate;
        this.width = width;
        this.height = height;
    }

    public int getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;
    }

    public int getBitRate() {
        return bitRate;
    }

    public void setBitRate(int bitRate) {
        this.bitRate = bitRate;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public boolean isSameQuality(VideoQuality quality) {
        return quality != null
                && this.width == quality.width
                && this.height == quality.height
                && this.frameRate == quality.frameRate
                && this.bitRate == quality.bitRate;
    }
}
