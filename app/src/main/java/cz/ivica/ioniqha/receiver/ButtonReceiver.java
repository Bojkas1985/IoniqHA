package cz.ivica.ioniqha.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import cz.ivica.ioniqha.MainActivity;

/**
 * Stejný princip jako v původním ABRPTransmitteru: firmware posílá broadcast
 * com.lge.ivi.action.KEY_EV při každém stisku tlačítka EV na head-unitu -
 * detekci "N stisků do X sekund" děláme sami, tady.
 *
 * Záměrně TROJKLIK, ne dvojklik jako originál - dokud je na head-unitu
 * nainstalovaný i starý ABRPTransmitter (má vlastní nezávislé počítadlo na
 * dvojklik), dvojklik by otevřel jeho okno. Trojklik tomu jde mimo - staré
 * appce na cestě ke 3 stisku stejně jednou vyskočí okno na jeho vlastním 2.
 * stisku, ale naše appka se otevře zvlášť na 3. stisku bez ohledu na to.
 * Až starou appku vypneš/odinstaluješ, trojklik zůstane - klidně by šlo
 * vrátit zpět na dvojklik, ale není to nutné.
 */
public class ButtonReceiver extends BroadcastReceiver {

    private static int sCalls = 0;
    private static long sLastCall = 0;
    private static final int REQUIRED_PRESSES = 3;
    private static final long PRESS_WINDOW_MS = 3000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        long now = System.currentTimeMillis();
        if (now - sLastCall > PRESS_WINDOW_MS) {
            sCalls = 0;
        }
        sLastCall = now;
        sCalls++;

        if (sCalls == REQUIRED_PRESSES) {
            sCalls = 0;
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }
}
