@echo off
REM Windows verze find_lge_jar.sh - stahne com.lge.ivi.jar a com.lge.ivi.odex
REM z head-unitu pres ADB do app\externallibs\.
REM
REM Pouziti (v prikazovem radku, ve slozce tools):
REM   adb connect <IP head-unitu>
REM   find_lge_jar.bat
REM
REM Pokud nic nenajde, zkus rucne:
REM   adb shell find / -iname "*lge.ivi*" 2^>nul
REM a cestu (bez pripony) dej jako parametr:
REM   find_lge_jar.bat /system/framework/com.lge.ivi

setlocal

set "DEST_DIR=%~dp0..\app\externallibs"
if not exist "%DEST_DIR%" mkdir "%DEST_DIR%"

where adb >nul 2>nul
if errorlevel 1 (
    echo ADB neni v PATH. Stahni Android platform-tools:
    echo https://developer.android.com/tools/releases/platform-tools
    echo Rozbal a spoustej z te slozky, nebo pridej do PATH.
    exit /b 1
)

adb get-state >nul 2>nul
if errorlevel 1 (
    echo ADB nevidi zadne pripojene zarizeni. Zkus nejdriv:
    echo   adb connect ^<IP head-unitu^>
    exit /b 1
)

if "%~1"=="" (
    echo Vychozi predpoklad: /system/framework/com.lge.ivi
    echo Pokud tam nic nebude, spust rucne:
    echo   adb shell find / -iname "*lge.ivi*" 2^>nul
    echo a skript spust znovu s nalezenou cestou (bez pripony) jako parametrem.
    set "BASE_PATH=/system/framework/com.lge.ivi"
) else (
    set "BASE_PATH=%~1"
    echo Zkousim rucne zadanou cestu: %BASE_PATH%.jar a .odex
)

set FOUND_ANY=0

echo Stahuju %BASE_PATH%.jar ...
adb pull "%BASE_PATH%.jar" "%DEST_DIR%\com.lge.ivi.jar"
if not errorlevel 1 set FOUND_ANY=1

echo Stahuju %BASE_PATH%.odex ...
adb pull "%BASE_PATH%.odex" "%DEST_DIR%\com.lge.ivi.odex"
if not errorlevel 1 set FOUND_ANY=1

if "%FOUND_ANY%"=="0" (
    echo.
    echo Nepodarilo se stahnout ani jar ani odex. Zkus najit presnou cestu rucne:
    echo   adb shell find / -iname "*lge.ivi*" 2^>nul
    exit /b 1
)

echo.
echo Hotovo. Soubory jsou v %DEST_DIR%
echo com.lge.ivi.jar je compile-only zavislost - build.gradle uz na ni ukazuje.

endlocal
