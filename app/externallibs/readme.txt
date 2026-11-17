Sem dej com.lge.ivi.jar (stub jar s třídami com.lge.ivi.greencar.GreenCarManager,
com.lge.ivi.carinfo.CarInfoManager, com.lge.ivi.hvac.HvacManager), vytažený přes ADB
z head-unitu (typicky /system/framework/ nebo /system/app/<...>/, podle verze firmwaru).
Stejný jar, jaký používal původní ABRPTransmitter - je to compile-only závislost,
v runtime appka sahá na skutečnou implementaci, co už na head-unitu běží.
