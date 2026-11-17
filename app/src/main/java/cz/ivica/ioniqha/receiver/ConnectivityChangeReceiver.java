package cz.ivica.ioniqha.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Message;

import static cz.ivica.ioniqha.Constants.MESSAGE_CONNECTIVITY_CHANGED;

/**
 * Registruje se dynamicky přímo ve službě (ne v manifestu) na
 * ConnectivityManager.CONNECTIVITY_ACTION - stejně jako v originále.
 * Hlídá JEN jestli je aktivní WiFi (ať už domácí, nebo třeba hotspot z
 * telefonu v autě - appce je to jedno, obojí je z jejího pohledu "WiFi").
 */
public class ConnectivityChangeReceiver extends BroadcastReceiver {

    private final Handler mHandler;

    public ConnectivityChangeReceiver(Handler handler) {
        mHandler = handler;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return;
        }
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean wifiConnected = activeNetwork != null
                && activeNetwork.isConnected()
                && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;

        Message msg = mHandler.obtainMessage(MESSAGE_CONNECTIVITY_CHANGED, wifiConnected);
        mHandler.sendMessage(msg);
    }
}
