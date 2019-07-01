package net.xvis.display;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.DisplayMetrics;
import android.util.Log;

import net.xvis.streaming.Session;
import net.xvis.streaming.SessionManager;
import net.xvis.streaming.hw.EncoderDebugger;
import net.xvis.streaming.resources.MediaContainer;
import net.xvis.streaming.resources.ResourceManager;
import net.xvis.streaming.rtsp.RtspMethod;
import net.xvis.streaming.rtsp.RtspServer;
import net.xvis.streaming.video.DisplayStream;
import net.xvis.streaming.video.VideoQuality;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;

public class VirtualDisplayService extends Service {
    private static final String TAG = "VirtualDisplayService";
    public static final int SCREEN_SHARE_PERMISSION = 37854;

    private NotificationManager notificationManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private static EncoderDebugger encoderDebugger;

    private HandlerThread serviceThread;
    private Messenger serviceMessenger;
    private boolean requestingPermission;
    private RtspServer rtspServer;

    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = getString(R.string.app_name);

        Notification notification;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel notificationChannel = new NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setDescription(channelId);
            notificationChannel.setSound(null, null);
            notificationManager.createNotificationChannel(notificationChannel);
            notification = new Notification.Builder(this, channelId)
                    .setContentTitle("VirtualDisplayTitle")
                    .setContentText("VirtualDisplayText")
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .build();
        } else {
            notification = new Notification.Builder(this)
                    .setContentTitle("VirtualDisplayTitle")
                    .setContentText("VirtualDisplayText")
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .build();
        }

        startForeground(1, notification);

        serviceThread = new HandlerThread(VirtualDisplayService.class.getName());
        serviceThread.start();
        serviceMessenger = new Messenger(new ServiceHandler(serviceThread.getLooper(), this));
        Log.e(TAG, "VirtualDisplayService created" + ":" + Thread.currentThread().getId());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG, "onStartCommand" + ":" + Thread.currentThread().getId());
        tryGetPermission();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.e(TAG, "onBind" + ":" + Thread.currentThread().getId());
        tryGetPermission();
        return serviceMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.e(TAG, "onUnbind" + ":" + Thread.currentThread().getId());
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "onDestroy" + ":" + Thread.currentThread().getId());
        endStream();
        serviceThread.quit(); // quitSafely for <= API 18
    }

    synchronized private void tryGetPermission() {
        if (requestingPermission) {
            Log.e(TAG, "permission already being requested");
            stopForeground(true);
            return;
        }

        if (mediaProjection != null) {
            Log.e(TAG, "Media projection already acquired");
            return;
        }

        requestingPermission = true;
        notificationManager.notify(1, new Notification.Builder(this)
                .setContentTitle("try get permission")
                .setSmallIcon(android.R.drawable.ic_dialog_dialer)
                .build());

        Intent intent = new Intent(this, PermissionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    synchronized private void beginStream(MediaProjection mediaProjection) {
        Log.e(TAG, "beginStream" + ":" + Thread.currentThread().getId());
        requestingPermission = false;
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection is null. May be permission not granted");
            endStream();
            return;
        }

        Log.e(TAG, "creating virtual display" + ":" + Thread.currentThread().getId());
        DisplayMetrics displayMetrics = getApplicationContext().getResources().getDisplayMetrics();
        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenShare",
                displayMetrics.widthPixels,
                displayMetrics.heightPixels,
                displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                null,//surface,
                null,
                null);

        if (virtualDisplay == null) {
            Log.e(TAG, "Unable to create virtual display");
            endStream();
            return;
        }

        // register this media resource
        try {
            URI baseUri = new URI(RtspServer.SCHEME, "127.0.0.1:8086", "/test/live/", null, null);
            MediaContainer mediaContainer = new MediaContainer(baseUri.toString());
            URI controlUri = baseUri.resolve("trackID=0");
            mediaContainer.addMedia(controlUri.toString(), new DisplayStream(virtualDisplay));
            mediaContainer.addSupportedMethod(RtspMethod.DESCRIBE);
            mediaContainer.addSupportedMethod(RtspMethod.OPTIONS);
            mediaContainer.addSupportedMethod(RtspMethod.PLAY);
            mediaContainer.addSupportedMethod(RtspMethod.SETUP);
            mediaContainer.addSupportedMethod(RtspMethod.TEARDOWN);
            mediaContainer.addSupportedMethod(RtspMethod.PAUSE);
            ResourceManager.addResource(mediaContainer);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

//        // start stream session
//        Session session = SessionManager.findSession(URI.create("rtsp://localhost/test/live"));
//        if (session == null) {
//            session = Session.builder()
//                    .setVideoEncoder(Session.DISPLAY_H264)
//                    .setVirtualDisplay(virtualDisplay)
//                    .setVideoQuality(new VideoQuality(displayMetrics.widthPixels, displayMetrics.heightPixels))
//                    .build(Uri.parse("rtsp://localhost/test/live"));
//            SessionManager.addSession(session);
//            session.startStream();
//        }

        Log.e("TAG", "Starting RtspServer " + displayMetrics.widthPixels + "x" + displayMetrics.heightPixels + ":" + Thread.currentThread().getId());
        rtspServer = new RtspServer();
        try {
            rtspServer.start();
        } catch (IOException e) {
            Log.e(TAG, "Unable to start RTSP server: " + e.getMessage());
            endStream();
            return;
        }

        this.mediaProjection = mediaProjection; // cache this for later use
        notificationManager.notify(1, new Notification.Builder(this)
                .setContentTitle("share on")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build());
    }

    synchronized private void endStream() {
        Log.e(TAG, "endStream");
        if (rtspServer != null) {
            rtspServer.stop();
            rtspServer = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        notificationManager.cancel(1);
        stopForeground(false);
    }

    private static class ServiceHandler extends Handler {
        private WeakReference<VirtualDisplayService> serviceRef;

        private ServiceHandler(Looper looper, VirtualDisplayService service) {
            super(looper);
            serviceRef = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            VirtualDisplayService service = serviceRef.get();
            if (service == null) {
                return;
            }

            switch (msg.what) {
                case SCREEN_SHARE_PERMISSION:
                    MediaProjection mediaProjection = (MediaProjection) msg.obj;
                    service.beginStream(mediaProjection);

//                    Point outSize = new Point();
//                    virtualDisplay.getDisplay().getSize(outSize);

//                    Point supportedSize = new Point(outSize);
                    //supportedSize.x = 640; // TODO: fix this hardcoded ratio
                    //supportedSize.y = 480; //

                    // trying 640x480, 320x240, 160x120
//                    while (supportedSize.x > 100) {
//                        try {
//                            encoderDebugger = EncoderDebugger.debug(supportedSize.x, supportedSize.y);
//                        } catch (Exception e) {
//                            supportedSize.x /= 2;
//                            supportedSize.y /= 2;
//                            continue;
//                        }
//                        break;
//                    }
//
//                    if (supportedSize.x <= 0) {
//                        String errMsg = "Unable to support display res at " + outSize.x + "x" + outSize.y;
//                        Log.e("TAG", errMsg);
//                        service.virtualDisplay.release();
//                        service.virtualDisplay = null;
//                        encoderDebugger = null;
//                        Toast.makeText(service, errMsg, Toast.LENGTH_SHORT).show();
//                        return;
//                    }
//
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
}