package net.xvis.display;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

public class PermissionActivity extends Activity {
    private static String TAG = PermissionActivity.class.getSimpleName();
    private MediaProjectionManager projectionManager;
    private boolean serviceBound;
    private Messenger serviceMessenger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blank);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Log.e(TAG, "PermissionActivity.onCreate()" + ":" + Thread.currentThread().getId());
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (projectionManager == null) {
            Log.e(TAG, "projectionManager is null");
            Toast.makeText(this, "Unable to get projection manager", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.e(TAG, "bindService to VirtualDisplayService" + ":" + Thread.currentThread().getId());
        Intent intent = new Intent(this, VirtualDisplayService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (serviceBound) {
            Log.e(TAG, "unbindService from VirtualDisplayService");
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.e(TAG, "VirtualDisplayService connected" + ":" + Thread.currentThread().getId());
            serviceMessenger = new Messenger(iBinder);
            serviceBound = true;

            Log.e(TAG, "Screen capture intent for permission" + ":" + Thread.currentThread().getId());
            Intent captureIntent = projectionManager.createScreenCaptureIntent();
            startActivityForResult(captureIntent, VirtualDisplayService.SCREEN_SHARE_PERMISSION);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.e(TAG, "VirtualDisplayService disconnected" + ":" + Thread.currentThread().getId());
            serviceBound = false;
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != VirtualDisplayService.SCREEN_SHARE_PERMISSION) {
            Log.e(TAG, "Not from VirtualDisplayService screen share permission");
            finish();
            return;
        }

        MediaProjection mediaProjection = null;
        if (resultCode != RESULT_OK) {
            Log.e(TAG, "Screen capture permission not granted");
            Toast.makeText(this, "Unable to get screen share permission", Toast.LENGTH_SHORT).show();
        } else {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        }

        try {
            Message message = Message.obtain(null, VirtualDisplayService.SCREEN_SHARE_PERMISSION, mediaProjection);
            serviceMessenger.send(message);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        finish();
    }
}
