package net.xvis.display;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import net.xvis.streaming.Utils;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;

public class VirtualDisplaySurface implements SurfaceTexture.OnFrameAvailableListener {

    private final String TAG = "VirtualDisplaySurface";

    private FrameRenderer frameRenderer;
    private SurfaceTexture surfaceTexture;
    private Surface surface;
    private long lastUpdated;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;

    private int width;
    private int height;
    private int colorFormat;

    private HandlerThread surfaceThread;
    private Handler surfaceHandler;
    private final Object syncObject = new Object();     // guards mFrameAvailable

    private ByteBuffer mPixelBuf;                       // used by saveFrame()
    private ByteBuffer yuvBuffer;


    public VirtualDisplaySurface(int width, int height, int colorFormat) {
        if (width <= 0 || height <= 0 || colorFormat == 0) {
            throw new IllegalArgumentException();
        }

        this.width = width;
        this.height = height;
        this.colorFormat = colorFormat;

        final CountDownLatch latch = new CountDownLatch(1);
        surfaceThread = new HandlerThread(TAG + ".virtualDisplaySurface");
        surfaceThread.start();
        surfaceHandler = new Handler(surfaceThread.getLooper());
        surfaceHandler.post(new Runnable() {
            @Override
            public void run() {
                eglSetup();
                makeCurrent();
                setup();
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void eglSetup() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            eglDisplay = null;
            throw new RuntimeException("unable to initialize EGL14");
        }

        // Configure EGL for pbuffer and OpenGL ES 2.0, 24-bit RGB.
        int[] displayAttrs = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, displayAttrs, 0, configs, 0, configs.length, numConfigs, 0)) {
            throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
        }

        // Configure context for OpenGL ES 2.0.
        int[] contextAttrs = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttrs, 0);
        checkEglError("eglCreateContext");
        if (eglContext == null) {
            throw new RuntimeException("null context");
        }

        // Create a pbuffer surface.
        int[] surfaceAttrs = {
                EGL14.EGL_WIDTH, width,
                EGL14.EGL_HEIGHT, height,
                EGL14.EGL_NONE
        };
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], surfaceAttrs, 0);
        checkEglError("eglCreatePbufferSurface");
        if (eglSurface == null) {
            throw new RuntimeException("surface was null");
        }
    }

    private void makeCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
        checkEglError("eglMakeCurrent");
    }

    private void setup() {
        frameRenderer = new FrameRenderer();
        Log.d(TAG, "textureID=" + frameRenderer.getTextureId());
        surfaceTexture = new SurfaceTexture(frameRenderer.getTextureId());
        surfaceTexture.setDefaultBufferSize(width, height);
        surfaceTexture.setOnFrameAvailableListener(this, surfaceHandler);

        surface = new Surface(surfaceTexture);
        mPixelBuf = ByteBuffer.allocateDirect(width * height * 4);
        mPixelBuf.order(ByteOrder.LITTLE_ENDIAN);
        yuvBuffer = ByteBuffer.allocateDirect(width * height * 4);
        yuvBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    public void release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface);
            EGL14.eglDestroyContext(eglDisplay, eglContext);
            //mEgl.eglReleaseThread();
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
            EGL14.eglTerminate(eglDisplay);
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglContext = EGL14.EGL_NO_CONTEXT;
        eglSurface = EGL14.EGL_NO_SURFACE;
        surface.release();

        // this causes a bunch of warnings that appear harmless but might confuse someone:
        //  W BufferQueue: [unnamed-3997-2] cancelBuffer: BufferQueue has been abandoned!
        //mSurfaceTexture.release();
        frameRenderer = null;
        surface = null;
        surfaceTexture = null;
    }

    public Surface getSurface() {
        return surface;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (syncObject) {
            Log.e(TAG, "onFrameAvailable");
            FrameRenderer.checkGlError("before updateTexImage");
            surfaceTexture.updateTexImage(); // make sure this is called in the same thread where glContext is associated.
        }
    }

    public void waitForFrame() {
        // make sure at least 50ms between frames
        long now = System.currentTimeMillis();
        if (now - lastUpdated < 50) {
            Utils.sleepSlient(50 - (now - lastUpdated));
        }
        lastUpdated = System.currentTimeMillis();

        synchronized (syncObject) {
            // TODO: do this only when texture is updated (onFrameAvailable)
            //FrameRenderer.checkGlError("before updateTexImage");
            // convert to YUV
            try {
                saveFrame(null, null, 320, 240);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void drawImage(boolean invert) {
        frameRenderer.drawFrame(surfaceTexture, invert);
    }

    public ByteBuffer getYuvBuffer() {
        return yuvBuffer;
    }

    public void saveFrame(String filename, ByteBuffer displayByteBuffer, int actualX, int actualY) throws IOException {
        // glReadPixels gives us a ByteBuffer filled with what is essentially big-endian RGBA
        // data (i.e. a byte of red, followed by a byte of green...).  To use the Bitmap
        // constructor that takes an int[] array with pixel data, we need an int[] filled
        // with little-endian ARGB data.
        //
        // If we implement this as a series of buf.get() calls, we can spend 2.5 seconds just
        // copying data around for a 720p frame.  It's better to do a bulk get() and then
        // rearrange the data in memory.  (For comparison, the PNG compress takes about 500ms
        // for a trivial frame.)
        //
        // So... we set the ByteBuffer to little-endian, which should turn the bulk IntBuffer
        // get() into a straight memcpy on most Android devices.  Our ints will hold ABGR data.
        // Swapping B and R gives us ARGB.  We need about 30ms for the bulk get(), and another
        // 270ms for the color swap.
        //
        // We can avoid the costly B/R swap here if we do it in the fragment shader (see
        // http://stackoverflow.com/questions/21634450/ ).
        //
        // Having said all that... it turns out that the Bitmap#copyPixelsFromBuffer()
        // method wants RGBA pixels, not ARGB, so if we create an empty bitmap and then
        // copy pixel data in we can avoid the swap issue entirely, and just copy straight
        // into the Bitmap from the ByteBuffer.
        //
        // Making this even more interesting is the upside-down nature of GL, which means
        // our output will look upside-down relative to what appears on screen if the
        // typical GL conventions are used.  (For ExtractMpegFrameTest, we avoid the issue
        // by inverting the frame when we render it.)
        //
        // Allocating large buffers is expensive, so we really want mPixelBuf to be
        // allocated ahead of time if possible.  We still get some allocations from the
        // Bitmap / PNG creation.

        mPixelBuf.rewind();
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mPixelBuf);
        checkEglError("glReadPixels");

        if (filename != null && !filename.isEmpty()) {
            BufferedOutputStream bos = null;
            try {
                bos = new BufferedOutputStream(new FileOutputStream(filename));
                Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                mPixelBuf.rewind();
                bmp.copyPixelsFromBuffer(mPixelBuf);
                bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
                bmp.recycle();
            } finally {
                if (bos != null) bos.close();
            }
            Log.d(TAG, "Saved " + width + "x" + height + " frame as '" + filename + "'");
        }

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        mPixelBuf.rewind();
        bmp.copyPixelsFromBuffer(mPixelBuf);
        Bitmap scaledBmp = Bitmap.createScaledBitmap(bmp, actualX, actualY, false);

        mPixelBuf.rewind();
        scaledBmp.copyPixelsToBuffer(mPixelBuf);
        byte[] rgba = mPixelBuf.array();

        yuvBuffer.rewind();
        byte[] yuv = yuvBuffer.array();

        rgbaToYuv42(rgba, yuv, actualX, actualY);

        if (displayByteBuffer != null) {
            displayByteBuffer.put(yuvBuffer.array(), 0, displayByteBuffer.capacity());
            //Log.d(TAG, "Saved " + mWidth + "x" + mHeight + " frame to displayBuffer " + displayByteBuffer.capacity());
        }

        bmp.recycle();
        scaledBmp.recycle();
    }

    private void rgbaToYuv42(byte[] rgba, byte[] yuv, int width, int height) {
        final int frameSize = width * height;
        final int chromaSize = frameSize / 4;

        int yIndex = 0;
        int uIndex = frameSize;
        int vIndex = frameSize + chromaSize;

        int R, G, B, Y, U, V;
        int index = 0;

        int rgbaIndex = 0;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                R = rgba[rgbaIndex++] & 0xff;
                G = rgba[rgbaIndex++] & 0xff;
                B = rgba[rgbaIndex++] & 0xff;
                rgbaIndex++; // skip A

                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                yuv[yIndex++] = (byte) ((Y < 0) ? 0 : (Y > 255) ? 255 : Y);//clamp(Y);

                if (j % 2 == 0 && index % 2 == 0)  {
                    yuv[uIndex++] = (byte) ((U < 0) ? 0 : (U > 255) ? 255 : U);
                    yuv[vIndex++] = (byte) ((V < 0) ? 0 : (V > 255) ? 255 : V);
                }

                index++;
            }
        }
    }

    private void checkEglError(String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }

}