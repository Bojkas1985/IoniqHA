package cz.ivica.ioniqha.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import cz.ivica.ioniqha.service.IoniqHaService;

import static cz.ivica.ioniqha.Constants.ACTION_NAVI_GPS_CHANGED;
import static cz.ivica.ioniqha.Constants.EXTRA_ALT;
import static cz.ivica.ioniqha.Constants.EXTRA_HEADING;
import static cz.ivica.ioniqha.Constants.EXTRA_LAT;
import static cz.ivica.ioniqha.Constants.EXTRA_LON;

/**
 * Dvě různé role v jednom (stejně jako NaviGpsChangedReceiver v originále):
 *
 * 1) BOOT_COMPLETED -> appka se tímhle startuje sama po nastartování head-unitu,
 *    bez jakéhokoli zásahu. Žádnou další logiku na to nepotřebujeme - stačí
 *    službu nastartovat, ona si poradí sama.
 *
 * 2) NAVI_GPS_CHANGED -> vlastní navigace vozu posílá aktuální polohu; pokud
 *    přijde, přepošleme ji do služby jako extra v Intentu.
 *
 * Služba se startuje při KAŽDÉM doručeném broadcastu (i při bootu, kdy GPS
 * extras prostě nebudou v Intentu) - to je záměr, přesně takhle to dělal i
 * originál a díky tomu appka "jede sama" bez nutnosti ji ručně spouštět.
 */
public class VehicleBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, IoniqHaService.class);

        if (intent != null && ACTION_NAVI_GPS_CHANGED.equals(intent.getAction())) {
            serviceIntent.putExtra(EXTRA_LAT, intent.getDoubleExtra(EXTRA_LAT, 0));
            serviceIntent.putExtra(EXTRA_LON, intent.getDoubleExtra(EXTRA_LON, 0));
            serviceIntent.putExtra(EXTRA_ALT, intent.getDoubleExtra(EXTRA_ALT, 0));
            serviceIntent.putExtra(EXTRA_HEADING, intent.getIntExtra(EXTRA_HEADING, 0));
        }

        context.startService(serviceIntent);
    }
}
