package cz.ivica.ioniqha;

public class Constants {

    // --- SharedPreferences ---
    public static final String PREFERENCES_NAME = "preferences";
    public static final String PREFERENCES_WEBHOOK_URL = "webhook_url";
    public static final String PREFERENCES_TRANSMIT_DATA = "transmit_data";
    // Jednoduchý "debugger" - poslední úspěšné odeslání a poslední chyba se
    // ukládají sem, aby je šlo zobrazit v appce bez nutnosti adb logcat.
    public static final String PREFERENCES_LAST_SEND_TIME = "last_send_time";
    public static final String PREFERENCES_LAST_ERROR = "last_error";
    public static final String PREFERENCES_LAST_ERROR_TIME = "last_error_time";

    // --- Akce pro IoniqHaService (ne broadcast z firmwaru, posíláme si sami) ---
    public static final String ACTION_SEND_NOW = "cz.ivica.ioniqha.action.SEND_NOW";

    // --- Broadcast akce, co posílá firmware head-unitu (ne naše appka) ---
    public static final String ACTION_KEY_EV = "com.lge.ivi.action.KEY_EV";
    public static final String ACTION_NAVI_GPS_CHANGED = "com.hkmc.telematics.gis.action.NAVI_GPS_CHANGED";
    public static final String EXTRA_LAT = "com.hkmc.telematics.gis.extra.LAT";
    public static final String EXTRA_LON = "com.hkmc.telematics.gis.extra.LON";
    public static final String EXTRA_ALT = "com.hkmc.telematics.gis.extra.ALT";
    public static final String EXTRA_HEADING = "com.hkmc.telematics.gis.extra.HEADING";

    // --- JSON pole posílaná do Home Assistant webhooku ---
    public static final String JSON_TIME = "utc";
    public static final String JSON_SOC = "soc";
    public static final String JSON_SPEED = "speed";
    public static final String JSON_CHARGING = "is_charging";
    public static final String JSON_POWER = "power";
    public static final String JSON_TEMPERATURE_EXT = "ext_temp";
    public static final String JSON_GPS_LAT = "lat";
    public static final String JSON_GPS_LON = "lon";
    public static final String JSON_GPS_ELEVATION = "elevation";
    public static final String JSON_GPS_HEADING = "heading";
    public static final String JSON_RANGE = "range_km";
    public static final String JSON_ODOMETER = "odometer_km";

    // Doplněné 2026-11-17 - další pole z GreenCarManager/CarInfoManager/
    // HvacManager, zabalené v samostatných try/catch (viz IoniqHaService),
    // protože u některých si nejsme jistí přesným významem/jednotkami.
    public static final String JSON_DRIVE_MODE = "drive_mode";
    public static final String JSON_AVG_EFFICIENCY = "avg_efficiency";
    public static final String JSON_USED_EFFICIENCY = "used_efficiency";
    public static final String JSON_CHARGE_LIMIT = "charge_limit_percent";
    public static final String JSON_RANGE_ADD = "range_add_km";
    public static final String JSON_BATTERY_LEVEL = "battery_level";
    public static final String JSON_BATTERY_PERCENT_ALT = "battery_percent_alt";
    public static final String JSON_EVSE_FAULT = "evse_fault_flag";
    public static final String JSON_DRIVING_LOCKOUT = "driving_lockout";
    public static final String JSON_INNER_TEMP = "inner_temp";

    // --- Intervaly (ms) ---
    public static final long INTERVAL_SEND_UPDATE = 5000L;
    public static final long INTERVAL_AVERAGE_COLLECTOR = 500L;

    // --- Handler message codes (service -> UI) ---
    public static final int MESSAGE_CONNECTIVITY_CHANGED = 1;
    public static final int MESSAGE_LAST_UPDATE_SENT = 2;
    public static final int MESSAGE_LAST_ERROR = 3;

    public static final int NOTIFICATION_ID = 1;
    public static final String NOTIFICATION_CHANNEL_ID = "ioniqha_channel";
}
