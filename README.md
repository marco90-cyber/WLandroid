# WLandroid

Launcher Android in stile Windows Phone (come Square Home), scritto in Kotlin + Jetpack Compose.

## Funzioni (v0.1)
- **Start**: griglia di tile a 4 colonne (piccola, media, larga), con tile live orologio/data.
- **Modifica**: tieni premuto su una tile, poi trascina per riordinare, ✕ per rimuovere, ⤢ per cambiare dimensione. Tocca uno spazio vuoto o premi Indietro per uscire.
- **Elenco app**: scorri a sinistra, lista alfabetica con lettere. Tieni premuto su un'app per aggiungerla alla Start, aprire le info o disinstallarla.
- **Impostazioni** (⋮ nell'elenco app): colore delle tile e tema scuro/chiaro.

## Build
Ogni push su `main` compila l'APK con GitHub Actions: scaricalo da **Actions → ultimo run → Artifacts → WLandroid-debug**, poi installalo e imposta WLandroid come app Home.

Per compilare in locale serve Android Studio (JDK 17, Gradle 8.9).
