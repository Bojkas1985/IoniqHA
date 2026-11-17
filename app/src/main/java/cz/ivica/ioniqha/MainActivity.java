package cz.ivica.ioniqha;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import cz.ivica.ioniqha.service.IoniqHaService;

import static cz.ivica.ioniqha.Constants.ACTION_SEND_NOW;
import static cz.ivica.ioniqha.Constants.PREFERENCES_LAST_ERROR;
import static cz.ivica.ioniqha.Constants.PREFERENCES_LAST_ERROR_TIME;
import static cz.ivica.ioniqha.Constants.PREFERENCES_LAST_SEND_TIME;
import static cz.ivica.ioniqha.Constants.PREFERENCES_NAME;
import static cz.ivica.ioniqha.Constants.PREFERENCES_TRANSMIT_DATA;
import static cz.ivica.ioniqha.Constants.PREFERENCES_WEBHOOK_URL;

/**
 * Minimální nastavovací obrazovka - URL webhooku, přepínač "posílat data",
 * tlačítko na okamžité odeslání a jednoduchý "debugger" (poslední
 * úspěch/chyba), ať se appka dá kontrolovat bez notebooku a adb logcat.
 * Otevírá se buď normálně (ikona appky), nebo trojklikem tlačítka EV přes
 * ButtonReceiver.
 */
public class MainActivity extends AppCompatActivity {

    private static final String USB_FILE_NAME = "webhook_url.txt";
    // Kde všude zkusit hledat soubor s URL na flešce - přesná cesta, kam se
    // USB na tomhle head-unitu připojí, není dopředu jistá, tak zkoušíme
    // víc obvyklých míst + krátké rekurzivní prohledání.
    private static final String[] USB_SEARCH_ROOTS = {
            "/mnt/usb_storage", "/mnt/media_rw", "/storage", "/mnt/sdcard", "/mnt"
    };
    private static final int USB_SEARCH_MAX_DEPTH = 4;

    private EditText mWebhookUrlEditText;
    private Switch mTransmitSwitch;
    private TextView mDebugStatusText;
    private SharedPreferences mPrefs;

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRefreshStatus = new Runnable() {
        @Override
        public void run() {
            updateDebugStatus();
            mUiHandler.postDelayed(this, 2000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Dvě záložky - "Nastavení" a "Debug" - přes klasický TabHost (funguje
        // bez dalších knihoven i na starém API 17 hlavní jednotky).
        TabHost tabHost = findViewById(android.R.id.tabhost);
        tabHost.setup();

        TabHost.TabSpec settingsTab = tabHost.newTabSpec("settings");
        settingsTab.setIndicator(getString(R.string.tab_settings_label));
        settingsTab.setContent(R.id.tab_settings);
        tabHost.addTab(settingsTab);

        TabHost.TabSpec debugTab = tabHost.newTabSpec("debug");
        debugTab.setIndicator(getString(R.string.tab_debug_label));
        debugTab.setContent(R.id.tab_debug);
        tabHost.addTab(debugTab);

        mWebhookUrlEditText = findViewById(R.id.webhook_url);
        mTransmitSwitch = findViewById(R.id.transmit_switch);
        mDebugStatusText = findViewById(R.id.debug_status_text);
        Button saveButton = findViewById(R.id.save_button);
        Button sendNowButton = findViewById(R.id.send_now_button);
        Button loadFromUsbButton = findViewById(R.id.load_from_usb_button);

        mPrefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        mWebhookUrlEditText.setText(mPrefs.getString(PREFERENCES_WEBHOOK_URL, ""));
        mTransmitSwitch.setChecked(mPrefs.getBoolean(PREFERENCES_TRANSMIT_DATA, false));

        saveButton.setOnClickListener(v -> {
            mPrefs.edit()
                    .putString(PREFERENCES_WEBHOOK_URL, mWebhookUrlEditText.getText().toString().trim())
                    .putBoolean(PREFERENCES_TRANSMIT_DATA, mTransmitSwitch.isChecked())
                    .apply();
            Toast.makeText(this, "Uloženo", Toast.LENGTH_SHORT).show();
            // Pro jistotu službu nastartujeme i odsud - normálně běží od bootu sama.
            startService(new Intent(this, IoniqHaService.class));
        });

        sendNowButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, IoniqHaService.class);
            intent.setAction(ACTION_SEND_NOW);
            startService(intent);
            Toast.makeText(this, "Zkouším odeslat...", Toast.LENGTH_SHORT).show();
        });

        loadFromUsbButton.setOnClickListener(v -> loadUrlFromUsb());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDebugStatus();
        mUiHandler.postDelayed(mRefreshStatus, 2000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mUiHandler.removeCallbacks(mRefreshStatus);
    }

    /** Přečte uložený stav (poslední odeslání / poslední chyba) a zobrazí ho. */
    private void updateDebugStatus() {
        long lastSendTime = mPrefs.getLong(PREFERENCES_LAST_SEND_TIME, 0L);
        String lastError = mPrefs.getString(PREFERENCES_LAST_ERROR, null);
        long lastErrorTime = mPrefs.getLong(PREFERENCES_LAST_ERROR_TIME, 0L);

        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        StringBuilder sb = new StringBuilder();

        if (lastSendTime > 0) {
            sb.append("Poslední úspěšné odeslání: ").append(fmt.format(new Date(lastSendTime)));
        } else {
            sb.append("Zatím žádné úspěšné odeslání.");
        }

        if (lastError != null && !lastError.isEmpty()) {
            sb.append("\nPoslední chyba (").append(fmt.format(new Date(lastErrorTime))).append("): ").append(lastError);
        }

        mDebugStatusText.setText(sb.toString());
    }

    /**
     * Zkusí najít soubor webhook_url.txt na připojené USB flešce a jeho obsah
     * (první řádek) nastavit jako webhook URL - ať se nemusí ten dlouhý hash
     * přepisovat na dotykové klávesnici v autě.
     */
    private void loadUrlFromUsb() {
        File found = null;
        for (String root : USB_SEARCH_ROOTS) {
            found = searchForFile(new File(root), USB_FILE_NAME, 0);
            if (found != null) {
                break;
            }
        }
        if (found == null) {
            // Záložní pokus - běžná externí úložiště přes Android API.
            File external = Environment.getExternalStorageDirectory();
            if (external != null) {
                found = searchForFile(external, USB_FILE_NAME, 0);
            }
        }

        if (found == null) {
            Toast.makeText(this,
                    "Soubor " + USB_FILE_NAME + " jsem na USB nenašel.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(found))) {
            String line = reader.readLine();
            if (line != null) {
                line = line.trim();
                mWebhookUrlEditText.setText(line);
                Toast.makeText(this, "Načteno z " + found.getPath(), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Nepodařilo se přečíst soubor: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Krátké rekurzivní hledání souboru podle jména, omezené hloubkou a počtem souborů. */
    private File searchForFile(File dir, String fileName, int depth) {
        if (dir == null || depth > USB_SEARCH_MAX_DEPTH || !dir.isDirectory()) {
            return null;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (child.isFile() && fileName.equalsIgnoreCase(child.getName())) {
                return child;
            }
        }
        for (File child : children) {
            if (child.isDirectory()) {
                File result = searchForFile(child, fileName, depth + 1);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
