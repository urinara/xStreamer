package net.xvis.streamer;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import net.xvis.display.VirtualDisplayService;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getApplication().registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks());

        Button buttonStart = findViewById(R.id.buttonStart);
        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startService(new Intent(MainActivity.this, VirtualDisplayService.class));
            }
        });

        Button buttonStop = findViewById(R.id.buttonStop);
        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopService(new Intent(MainActivity.this, VirtualDisplayService.class));
            }
        });

        TextView textView = findViewById(R.id.textViewInfo);
        textView.setText(getInfo());
    }

    private String getInfo() {
        StringBuilder sb = new StringBuilder();
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            sb.append("Large Heap size=").append(activityManager.getLargeMemoryClass()).append("MB\n");
            sb.append("Normal Heap size=").append(activityManager.getMemoryClass()).append("MB\n");
            sb.append("Runtime max mem=").append(Runtime.getRuntime().maxMemory() / 1024 / 1024).append("MB\n");
            sb.append("AvailableProcessors=").append(Runtime.getRuntime().availableProcessors());
        }
        sb.append("\n\n");

        ConnectivityManager connectManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectManager != null) {
            Network[] networks = connectManager.getAllNetworks();
            for (Network network : networks) {
                NetworkInfo networkInfo = connectManager.getNetworkInfo(network);
                sb.append(networkInfo.toString()).append("\n");
            }
        }
        sb.append("\n");

        try {
            Enumeration<NetworkInterface> networkItr = NetworkInterface.getNetworkInterfaces();
            while (networkItr.hasMoreElements()) {
                NetworkInterface netInterface = networkItr.nextElement();
                sb.append(netInterface.toString()).append("\n");
                for (InterfaceAddress address : netInterface.getInterfaceAddresses()) {
                    sb.append(address.toString()).append("\n");
                }
                for (Enumeration<InetAddress> enumIpAddr = netInterface.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    String ip = Formatter.formatIpAddress(inetAddress.hashCode());
                    sb.append("    ***** IP=").append(ip).append("\n");
                }
                sb.append("\n");
            }
        } catch (SocketException ignore) {
        }

        return sb.toString();
    }
}
