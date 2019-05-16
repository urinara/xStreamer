package net.xvis.streamer;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import java.lang.ref.WeakReference;

public class ActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
    private static String TAG = ActivityLifecycleCallbacks.class.getName();
    private WeakReference<Activity> lastActivityCreated;

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
        Log.d(TAG, activity.getClass().getName() + " : onActivityCreated");
        lastActivityCreated = new WeakReference<>(activity);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        Log.d(TAG, activity.getClass().getName() + " : onActivityStarted");
    }

    @Override
    public void onActivityResumed(Activity activity) {
        Log.d(TAG, activity.getClass().getName() + " : onActivityResumed");
    }

    @Override
    public void onActivityPaused(Activity activity) {
        Log.d(TAG, activity.getClass().getName() + " : onActivityPaused");
    }

    @Override
    public void onActivityStopped(Activity activity) {
        Log.d(TAG, activity.getClass().getName() + " : onActivityStopped");
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
        Log.d(TAG, activity.getClass().getName() + " : onActivitySaveInstanceState");
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        Log.d(TAG, activity.getClass().getName() + " : onActivityDestroyed");
    }
}
