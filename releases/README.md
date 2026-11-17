# Releases

Sem patří zkompilovaný `.apk` appky IoniqHA, aby šel stáhnout rovnou z
GitHubu bez nutnosti appku sám sestavovat v Android Studiu.

Jak appku sestavit (pokud chceš nahrát novou verzi):

1. Otevři projekt v Android Studiu.
2. **Build → Clean Project**, pak **Build → Make Project** (Ctrl+F9 /
   Cmd+F9).
3. Hotový soubor najdeš v `app/build/outputs/apk/debug/app-debug.apk`.
4. Zkopíruj ho sem do složky `releases/` (klidně i s názvem podle verze,
   např. `IoniqHA-v1.apk`), přidej do gitu a commitni:
   ```
   git add releases/
   git commit -m "Přidat APK appky"
   git push
   ```

*This folder holds the compiled `.apk` of the IoniqHA app, so it can be
downloaded directly from GitHub without having to build it yourself in
Android Studio first.*
