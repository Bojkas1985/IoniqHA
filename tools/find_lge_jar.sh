#!/usr/bin/env bash
# Pomocný skript: stáhne z head-unitu přes ADB dvojici souborů potřebných
# pro kompilaci - com.lge.ivi.jar A com.lge.ivi.odex (ten .odex obsahuje
# skutečná těla metod, samotný .jar bez něj může být jen prázdná kostra).
#
# Tenhle soubor NEJDE nikde stáhnout z internetu - je to vytažený kus
# Hyundai/LGE firmwaru, vázaný na konkrétní vůz. Ověřeno i u jiných
# podobných projektů (IoniqDI), které z právních důvodů jar nedistribuují
# a odkazují vždycky na vlastní vytažení z /system/framework.
#
# Použití:
#   adb connect <IP head-unitu>
#   ./tools/find_lge_jar.sh
#
# Pokud skript nic nenajde automaticky, zkus ručně:
#   adb shell find / -iname "*lge.ivi*" 2>/dev/null
# a cestu dej jako argument (bez přípony, skript zkusí .jar i .odex):
#   ./tools/find_lge_jar.sh /system/framework/com.lge.ivi

set -euo pipefail

DEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/app/externallibs"

if ! command -v adb >/dev/null 2>&1; then
    echo "adb není v PATH - nainstaluj android-tools / platform-tools." >&2
    exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
    echo "ADB nevidí žádné připojené zařízení. Zkus nejdřív:" >&2
    echo "  adb connect <IP head-unitu>" >&2
    exit 1
fi

mkdir -p "$DEST_DIR"

if [ "${1:-}" != "" ]; then
    BASE_PATH="$1"
    echo "Zkouším ručně zadanou cestu: $BASE_PATH{.jar,.odex}"
else
    echo "Výchozí předpoklad: /system/framework/com.lge.ivi"
    echo "Pokud tam nic nebude, spusť ručně:"
    echo "  adb shell find / -iname '*lge.ivi*' 2>/dev/null"
    echo "a skript spusť znovu s nalezenou cestou (bez přípony) jako argumentem."
    echo
    BASE_PATH="/system/framework/com.lge.ivi"
fi

FOUND_ANY=0
for EXT in jar odex; do
    SRC="${BASE_PATH}.${EXT}"
    DEST="$DEST_DIR/com.lge.ivi.${EXT}"
    echo "Stahuju $SRC ..."
    if adb pull "$SRC" "$DEST" 2>/dev/null; then
        echo "  -> OK: $DEST"
        FOUND_ANY=1
    else
        echo "  -> nenalezeno na této cestě"
    fi
done

if [ "$FOUND_ANY" -eq 0 ]; then
    echo "Nepodařilo se stáhnout ani .jar ani .odex. Zkus najít přesnou cestu ručně:" >&2
    echo "  adb shell find / -iname '*lge.ivi*' 2>/dev/null" >&2
    exit 1
fi

echo
echo "Hotovo. com.lge.ivi.jar je compile-only závislost (build.gradle na ni už ukazuje)."
echo ".odex je jen pro jistotu vedle - Android Studio ho k samotné kompilaci nepotřebuje,"
echo "ale kdyby .jar sám o sobě dělal problémy (prázdné/abstraktní metody), je dobré ho mít po ruce."
echo "Over obsah jaru: unzip -l '$DEST_DIR/com.lge.ivi.jar' | grep -i -E 'greencar|carinfo|hvac'"
