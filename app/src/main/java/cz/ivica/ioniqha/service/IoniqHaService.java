package cz.ivica.ioniqha.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.lge.ivi.carinfo.CarInfoManager;
import com.lge.ivi.greencar.GreenCarManager;
import com.lge.ivi.hvac.HvacManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import cz.ivica.ioniqha.R;
import cz.ivica.ioniqha.TLSSocketFactory;
import cz.ivica.ioniqha.receiver.ConnectivityChangeReceiver;

import static cz.ivica.ioniqha.Constants.ACTION_SEND_NOW;
import static cz.ivica.ioniqha.Constants.EXTRA_ALT;
import static cz.ivica.ioniqha.Constants.EXTRA_HEADING;
import static cz.ivica.ioniqha.Constants.EXTRA_LAT;
import static cz.ivica.ioniqha.Constants.EXTRA_LON;
import static cz.ivica.ioniqha.Constants.INTERVAL_AVERAGE_COLLECTOR;
import static cz.ivica.ioniqha.Constants.INTERVAL_SEND_UPDATE;
import static cz.ivica.ioniqha.Constants.JSON_AVG_EFFICIENCY;
import static cz.ivica.ioniqha.Constants.JSON_BATTERY_LEVEL;
import static cz.ivica.ioniqha.Constants.JSON_BATTERY_PERCENT_ALT;
import static cz.ivica.ioniqha.Constants.JSON_CHARGE_LIMIT;
import static cz.ivica.ioniqha.Constants.JSON_CHARGING;
import static cz.ivica.ioniqha.Constants.JSON_DRIVE_MODE;
import static cz.ivica.ioniqha.Constants.JSON_DRIVING_LOCKOUT;
import static cz.ivica.ioniqha.Constants.JSON_EVSE_FAULT;
import static cz.ivica.ioniqha.Constants.JSON_GPS_ELEVATION;
import static cz.ivica.ioniqha.Constants.JSON_GPS_HEADING;
import static cz.ivica.ioniqha.Constants.JSON_GPS_LAT;
import static cz.ivica.ioniqha.Constants.JSON_GPS_LON;
import static cz.ivica.ioniqha.Constants.JSON_INNER_TEMP;
import static cz.ivica.ioniqha.Constants.JSON_ODOMETER;
import static cz.ivica.ioniqha.Constants.JSON_POWER;
import static cz.ivica.ioniqha.Constants.JSON_RANGE;
import static cz.ivica.ioniqha.Constants.JSON_RANGE_ADD;
import static cz.ivica.ioniqha.Constants.JSON_SOC;
import static cz.ivica.ioniqha.Constants.JSON_SPEED;
import static cz.ivica.ioniqha.Constants.JSON_TEMPERATURE_EXT;
import static cz.ivica.ioniqha.Constants.JSON_TIME;
import static cz.ivica.ioniqha.Constants.JSON_USED_EFFICIENCY;
import static cz.ivica.ioniqha.Constants.MESSAGE_CONNECTIVITY_CHANGED;
import static cz.ivica.ioniqha.Constants.MESSAGE_LAST_ERROR;
import static cz.ivica.ioniqha.Constants.MESSAGE_LAST_UPDATE_SENT;
import static cz.ivica.ioniqha.Constants.NOTIFICATION_CHANNEL_ID;
import static cz.ivica.ioniqha.Constants.NOTIFICATION_ID;
import static cz.ivica.ioniqha.Constants.PREFERENCES_LAST_ERROR;
import static cz.ivica.ioniqha.Constants.PREFERENCES_LAST_ERROR_TIME;
import static cz.ivica.ioniqha.Constants.PREFERENCES_LAST_SEND_TIME;
import static cz.ivica.ioniqha.Constants.PREFERENCES_NAME;
import static cz.ivica.ioniqha.Constants.PREFERENCES_TRANSMIT_DATA;
import static cz.ivica.ioniqha.Constants.PREFERENCES_WEBHOOK_URL;

/**
 * Založeno na AbrpTransmitterService z původního ABRPTransmitteru - čtení
 * vozidlových dat (GreenCarManager/CarInfoManager/HvacManager) je beze
 * změny, jen místo HTTPS GET na api.iternio.com děláme POST na webhook
 * v Home Assistantu. Žádný ABRP token, žádné OAuth, žádný offline buffer
 * (v1 - co se nepovede poslat, se prostě zahodí).
 */
public class IoniqHaService extends Service {

    private static final Logger LOG = LoggerFactory.getLogger(IoniqHaService.class);

    private GreenCarManager mGreenCarManager;
    private CarInfoManager mCarInfoManager;
    private HvacManager mHvacManager;

    private final JSONObject mTelemetry = new JSONObject();
    private final Map<Long, Double> mPowerSamples = new LinkedHashMap<>();

    private ScheduledExecutorService mExecutor;
    private boolean mStarted = false;
    private volatile boolean mWifiConnected = false;

    private ConnectivityChangeReceiver mConnectivityChangeReceiver;

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_CONNECTIVITY_CHANGED:
                    mWifiConnected = (boolean) msg.obj;
                    LOG.info("WiFi connected: {}", mWifiConnected);
                    break;
                case MESSAGE_LAST_UPDATE_SENT:
                    LOG.debug("Telemetrie odeslána v {}", msg.obj);
                    // Uložíme i do SharedPreferences - MainActivity (jiná
                    // komponenta, jiná instance) se na tohle Handleru
                    // nedostane, ale do stejného souboru preferencí ano.
                    getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putLong(PREFERENCES_LAST_SEND_TIME, (long) msg.obj)
                            .putString(PREFERENCES_LAST_ERROR, null)
                            .apply();
                    break;
                case MESSAGE_LAST_ERROR:
                    LOG.warn("Chyba odesílání: {}", msg.obj);
                    getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putString(PREFERENCES_LAST_ERROR, String.valueOf(msg.obj))
                            .putLong(PREFERENCES_LAST_ERROR_TIME, System.currentTimeMillis())
                            .apply();
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        mGreenCarManager = GreenCarManager.getInstance(this);
        mCarInfoManager = CarInfoManager.getInstance();
        mHvacManager = HvacManager.getInstance();

        mConnectivityChangeReceiver = new ConnectivityChangeReceiver(mHandler);
        registerReceiver(mConnectivityChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        startForeground(NOTIFICATION_ID, buildNotification());

        if (!mStarted) {
            mStarted = true;
            mExecutor = Executors.newScheduledThreadPool(2);
            mExecutor.scheduleAtFixedRate(this::collectPowerSample, 0, INTERVAL_AVERAGE_COLLECTOR, TimeUnit.MILLISECONDS);
            mExecutor.scheduleAtFixedRate(this::sendUpdate, 0, INTERVAL_SEND_UPDATE, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra(EXTRA_LAT)) {
            double lat = intent.getDoubleExtra(EXTRA_LAT, 0);
            double lon = intent.getDoubleExtra(EXTRA_LON, 0);
            double alt = intent.getDoubleExtra(EXTRA_ALT, 0);
            int heading = intent.getIntExtra(EXTRA_HEADING, 0);
            if (lat != 0 || lon != 0) {
                try {
                    synchronized (mTelemetry) {
                        mTelemetry.put(JSON_GPS_LAT, lat);
                        mTelemetry.put(JSON_GPS_LON, lon);
                        mTelemetry.put(JSON_GPS_ELEVATION, alt);
                        mTelemetry.put(JSON_GPS_HEADING, heading);
                    }
                } catch (JSONException e) {
                    LOG.warn("Nepodařilo se uložit GPS", e);
                }
            }
        }
        if (intent != null && ACTION_SEND_NOW.equals(intent.getAction())) {
            // Tlačítko "Odeslat teď" v appce - okamžitý pokus mimo pravidelný
            // 5vteřinový rozvrh, ať se na výsledek/chybu nemusí čekat.
            if (mExecutor != null) {
                mExecutor.execute(this::sendUpdate);
            }
        }
        // START_STICKY - Android může službu po zabití restartovat, chceme ji mít
        // pořád naživu, dokud head-unit běží.
        return START_STICKY;
    }

    /** Zapíše důvod, proč se telemetrie tentokrát neodeslala - pro "debugger" v appce. */
    private void recordSkipped(String reason) {
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREFERENCES_LAST_ERROR, reason)
                .putLong(PREFERENCES_LAST_ERROR_TIME, System.currentTimeMillis())
                .apply();
    }

    /** Volá se z pozadí každých INTERVAL_AVERAGE_COLLECTOR ms - sbírá vzorky výkonu. */
    private void collectPowerSample() {
        try {
            double aircon = mGreenCarManager.getCrDatcAcnCompPwrConW() / 100.0;
            double heating = mGreenCarManager.getCrDatcPtcPwrConW() / 100.0;
            double electric = mGreenCarManager.getCrLdcPwrMonW() / 100.0;
            double engine = mGreenCarManager.getCrMcuMotPwrAvnKw();
            double totalKw = aircon + heating + electric + engine;
            synchronized (mPowerSamples) {
                mPowerSamples.put(System.currentTimeMillis(), totalKw);
            }
        } catch (Exception e) {
            // Čtení z GreenCarManageru klidně může občas selhat (auto spí apod.) -
            // nechceme kvůli tomu shodit celou službu.
            LOG.debug("collectPowerSample selhal", e);
        }
    }

    private double averagePowerAndClear() {
        synchronized (mPowerSamples) {
            if (mPowerSamples.isEmpty()) {
                return 0;
            }
            double sum = 0;
            for (double v : mPowerSamples.values()) {
                sum += v;
            }
            double avg = sum / mPowerSamples.size();
            mPowerSamples.clear();
            return avg;
        }
    }

    /** Volá se každých INTERVAL_SEND_UPDATE ms - sestaví a pošle telemetrii. */
    private void sendUpdate() {
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        boolean transmitEnabled = prefs.getBoolean(PREFERENCES_TRANSMIT_DATA, false);
        String webhookUrl = prefs.getString(PREFERENCES_WEBHOOK_URL, null);

        if (!transmitEnabled) {
            recordSkipped("Vypnuto v nastavení (\"Posílat data\")");
            return;
        }
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            recordSkipped("Chybí webhook URL v nastavení");
            return;
        }
        if (!mWifiConnected) {
            // V1: žádný buffer, bez WiFi data prostě zahodíme.
            recordSkipped("Není WiFi připojení");
            return;
        }

        int soc;
        try {
            soc = mGreenCarManager.getBatteryChargePersent();
        } catch (Exception e) {
            LOG.warn("Nepodařilo se přečíst SoC", e);
            recordSkipped("Nepodařilo se přečíst SoC z vozidla: " + e.getMessage());
            return;
        }
        if (soc <= 0) {
            // Stejná pojistka jako v originále - 0 % je skoro jistě chybové čtení, ne reálný stav.
            recordSkipped("SoC z vozidla vyšlo 0 % - ignoruji jako chybné čtení");
            return;
        }

        try {
            synchronized (mTelemetry) {
                mTelemetry.put(JSON_TIME, System.currentTimeMillis() / 1000);
                mTelemetry.put(JSON_SOC, soc);
                mTelemetry.put(JSON_SPEED, mCarInfoManager.getCarSpeed());
                mTelemetry.put(JSON_CHARGING, mGreenCarManager.getChargeStatus());
                mTelemetry.put(JSON_TEMPERATURE_EXT, mHvacManager.getAmbientTemperatureC());
                mTelemetry.put(JSON_POWER, averagePowerAndClear());

                // Dojezd a nájezd - stejná rodina GreenCarManager volání jako SoC, jen
                // o něco méně prověřená (viz IoniqDI), proto je čteme každý zvlášť a
                // chybu jednoho nenecháme spadnout na zbytek telemetrie.
                try {
                    mTelemetry.put(JSON_RANGE, mGreenCarManager.getCrVcuDistEmptyKm());
                } catch (Exception e) {
                    LOG.debug("Nepodařilo se přečíst dojezd", e);
                }
                try {
                    mTelemetry.put(JSON_ODOMETER, mGreenCarManager.getCluOdometer());
                } catch (Exception e) {
                    LOG.debug("Nepodařilo se přečíst nájezd", e);
                }

                // Doplněné 2026-11-17 - další pole objevená deodexováním. U
                // některých (battery_level/battery_percent_alt/evse_fault_flag/
                // driving_lockout) si nejsme jistí přesným významem, proto
                // je posíláme taky, ale každé zvlášť v try/catch, ať případná
                // chyba nebo nepodporované volání nerozbije zbytek telemetrie.
                try {
                    mTelemetry.put(JSON_DRIVE_MODE, mGreenCarManager.getDriveMode());
                } catch (Exception e) {
                    LOG.debug("Nepodařilo se přečíst drive mode", e);
                }
                try {
                    mTelemetry.put(JSON_AVG_EFFICIENCY, mGreenCarManager.getAverageFuelEfficiency());
                } catch (Exception e) {
                    LOG.debug("Nepodařilo se přečíst průměrnou spotřebu", e);
                }
                try {
                    mTelemetry.put(JSON_USED_EFFICIENCY, mGreenCarManager.getUsedFuelEfficiency());
                } catch (Exception e) {
                    LOG.debug("Nepodařilo se přečíst aktuální spotřebu", e);
                }
                try {
                    mTelemetry.put(JSON_CHARGE_LIMIT, mGreenCarManager.getStandardCharge());
                } catch (Exception e) {
                    LOG.debug("Nepodařilo se přečíst limit nabíjení", e);
                }
                try {
                    mTelemetry.put(JSON_RANGE_ADD, mGreenCarManager.getCrVcuDistEmptyAddKm());
                } catch (Exception e) {
                    LOG.debug("Nepodařilo se přečíst doplňkový dojezd", e);
                }
                try {
                    mTelemetry.put(JSON_BATTERY_LEVEL, mGreenCarManager.getBatteryLevel());
                } catch (Exception e) {
                    LOG.debug("Nepodařilo se přečíst battery level", e);
                }
                try {
                    mTelemetry.put(JSON_BATTERY_PERCENT_ALT, mGreenCarManager.getBatteryPersent());
                } catch (Exception e) {
                    LOG.debug("Nepodařilo se přečíst alternativní SoC", e);
                }
                try {
                    mTelemetry.put(JSON_EVSE_FAULT, mGreenCarManager.getEVSEFaultFlag());
                } catch (Exception e) {
                    LOG.debug("Nepodařilo se přečíst EVSE fault flag", e);
                }
                try {
                    mTelemetry.put(JSON_DRIVING_LOCKOUT, mCarInfoManager.getDrivingLockOut());
                } catch (Exception e) {
                    LOG.debug("Nepodařilo se přečíst driving lockout", e);
                }
                try {
                    float[] innerTemps = mHvacManager.getInnerTemperatureC();
                    if (innerTemps != null && innerTemps.length > 0) {
                        // Pole teplot pro víc zón - posíláme první (obvykle řidič),
                        // ať payload zůstane jednoduchý JSON, ne pole v poli.
                        mTelemetry.put(JSON_INNER_TEMP, innerTemps[0]);
                    }
                } catch (Exception e) {
                    LOG.debug("Nepodařilo se přečíst vnitřní teplotu", e);
                }
            }
        } catch (JSONException e) {
            LOG.warn("Nepodařilo se sestavit telemetrii", e);
            return;
        }

        postToWebhook(webhookUrl, mTelemetry.toString());
    }

    private void postToWebhook(String webhookUrl, String jsonPayload) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(webhookUrl);
            connection = (HttpURLConnection) url.openConnection();

            // Lokální HTTP webhook (doma na WiFi) funguje bez úprav. Pro HTTPS
            // (např. vzdálený webhook přes Nabu Casa, aby appku mohl použít
            // i někdo jiný bez společné lokální sítě) starý Android na
            // head-unitu defaultně nenabídne dost moderní TLS - proto tu
            // vynutíme TLSv1.1/1.2 přes TLSSocketFactory.
            if (connection instanceof javax.net.ssl.HttpsURLConnection) {
                try {
                    ((javax.net.ssl.HttpsURLConnection) connection)
                            .setSSLSocketFactory(new TLSSocketFactory());
                } catch (java.security.GeneralSecurityException e) {
                    LOG.warn("Nepodařilo se nastavit TLSSocketFactory, zkusím to bez ní", e);
                }
            }

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            // Doplněno 2026-11-17 - appka chvílemi uspěje, chvílemi ne
            // (SSLException: Connection closed by peer). Podezření je na
            // keep-alive recyklaci TCP spojení - "Connection: close" vynutí
            // nové spojení (a tím nový TLS handshake přes náš
            // TLSSocketFactory/SNI fix) pro každý request místo recyklace
            // starého socketu z poolu.
            connection.setRequestProperty("Connection", "close");
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);

            byte[] body = jsonPayload.getBytes("UTF-8");
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                mHandler.obtainMessage(MESSAGE_LAST_UPDATE_SENT, System.currentTimeMillis()).sendToTarget();
            } else {
                mHandler.obtainMessage(MESSAGE_LAST_ERROR, "HTTP " + responseCode).sendToTarget();
            }
        } catch (IOException e) {
            // Doplněno 2026-11-17 - "Connection closed by peer" samo o sobě
            // neřekne, jestli jde o TLS/SNI problém nebo něco jiného. Celý
            // stacktrace jde do logcatu (ať jde dohledat přes adb) a do
            // debug obrazovky appky navíc posíláme i přesný typ výjimky.
            LOG.warn("Chyba při POST na webhook", e);
            String detail = e.getClass().getSimpleName() + ": " + e.getMessage();
            mHandler.obtainMessage(MESSAGE_LAST_ERROR, detail).sendToTarget();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, "IoniqHA", NotificationManager.IMPORTANCE_LOW);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("IoniqHA")
                .setContentText("Odesílám data do Home Assistanta")
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mExecutor != null) {
            mExecutor.shutdownNow();
        }
        if (mConnectivityChangeReceiver != null) {
            unregisterReceiver(mConnectivityChangeReceiver);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
