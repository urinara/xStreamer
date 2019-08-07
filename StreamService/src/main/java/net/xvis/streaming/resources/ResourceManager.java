package net.xvis.streaming.resources;

import android.util.Log;

import net.xvis.streaming.MediaStream;
import net.xvis.streaming.Session;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class ResourceManager {
    // resource
    // * : general capabilities of the next hop (the RTSP agent in direct comm with the request header)
    // hostAddress : RTSP agent general capabilities without regard to any specific media
    // test/live
    // test/live/trackID=0

    private static String TAG = "ResourceManager";
    private static Map<String, MediaContainer> resourceMap = new HashMap<>();

    private ResourceManager() { }

    public synchronized static MediaContainer findResource(URI resourceUri) {
        Log.e(TAG, "finding a session: " + resourceUri);
        return resourceMap.get(resourceUri.getPath());
    }

    public synchronized static void addResource(MediaContainer mediaContainer) {
        // register base URI
        URI baseUri = URI.create(mediaContainer.getBaseUri());
        resourceMap.put(baseUri.getPath(), mediaContainer);
        Log.d(TAG, "Added " + baseUri.getPath());
        // add any control URI's
        for (String uri : mediaContainer.getControlUris()) {
            URI controlUri = baseUri.resolve(uri);
            resourceMap.put(controlUri.getPath(), mediaContainer);
            Log.d(TAG, "Added " + controlUri.getPath());
        }
    }

    public synchronized static void removeResource(String resourceUri) {
        resourceMap.remove(resourceUri);
    }

}
