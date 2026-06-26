# Changelog

## v1.6.3 — 2026-06-25

### Welle 6 — Akku-Tiefe
- **Discharge-Curve-Graph** aus der Room-Battery-DB, Time-Range-Chips (6 h / 24 h / 3 Tg / 7 Tg), Charging/Discharging-Segmente farbig, Drain-Rate + geschätzte Volllaufzeit
- **Smart Charging Limit** — Hardware-Stop via Kernel-Sysfs (`input_suspend`, `charging_enabled`, `store_mode`, `restrict_chg`) mit Fallback auf **dumpsys-Soft-Stop** (`dumpsys battery set ac/usb/wireless 0`) für MIUI/HyperOS und Samsung-Knox wo Sysfs gelocked ist. Polling + Auto-Resume bei Hysterese-Limit.
- **Akku-Doktor** — korreliert Wake-Locks + `dumpsys batterystats` + Foreground-Service-Liste zu Top-Verdächtigen mit Score und konkreten Hinweisen (Mobilfunk aktiv, viele WLAN-Scans …)

### Welle 7 — Storage & APK
- **Storage-TreeMap-Analyzer** — `du -sb` via Shizuku, Drill-down ins Dateisystem mit proportionalen Hintergrund-Balken
- **APK-Extractor** — `pm path` + Split-APK-Support, Ziel `/sdcard/Download/redmonitor-apks/<pkg>/` mit `chmod 666` + `chown media_rw` + Media-Scan-Broadcast (damit Files im File-Manager sichtbar sind)
- **Crash-Log-Reader** — `/data/tombstones/` + `dumpsys dropbox --print` parsing, ANR vs Crash farblich, Detail-Sheet pro Eintrag

### Welle 8 — Network-Tools
- **Speed-Test** — Cloudflare Down (50 MB) + Up (16 MB) + Latency/Jitter, jetzt mit komplettem try/catch + UI-Fehlertext (kein App-Crash mehr bei Mid-Test-Disconnect), `Accept-Encoding: identity` damit Cloudflare nicht gzippt
- **Multi-Server-Ping** mit Jitter-Graph — 1.1.1.1 / 8.8.8.8 / heise.de / steamcommunity.com parallel via TCP-Connect-Probe, Live-Liniendiagramm + Min/Median/Jitter/Packet-Loss
- **GNSS Live** — `GnssStatus.Callback`, gruppiert nach Konstellation (GPS/GLONASS/Galileo/BeiDou/QZSS/SBAS/IRNSS), SNR-Bars + Used-In-Fix-Häkchen, Kalibrier-Hinweis bei niedriger Accuracy

### Welle 9 — Power-User-Direkteingriffe
- **Permission-Toggle inline** — im Permission-Audit kannst du jede einzelne Permission per Tap auf „grant"/„revoke" direkt umschalten (via `pm grant/revoke` über Shizuku)
- **Perfetto / atrace System-Trace** — 4 Presets (CPU+Scheduler / GFX+Frames / Memory+GC / Boot), 5/10/30/60 s, Output nach `/sdcard/Download/redmonitor-trace-*.pftrace` (mit Media-Scan + chmod, damit für File-Manager sichtbar). Direkt in `ui.perfetto.dev` analysierbar.
- **DPI + Animation Quick-Set** — `wm density` 320/360/400/420/480 oder Custom, animator_*_scale auf 0/0.5/1.0 für „alles sofort"-Look
- **Bulk-Background-Restrict** — Liste aller Apps mit Status, Toggle setzt `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore` ohne durch System-Settings zu klicken
- **Privacy-Dashboard** — `dumpsys appops` parsing zeigt was Apps **wirklich** benutzt haben (Kamera, Mic, Standort, Kontakte …), nicht nur was erlaubt ist. Sortiert nach Aktivitäts-Score, Live-Badge für Apps mit Zugriff in letzter Stunde, Filter nach Op + Alter (1h/24h/7Tg/30Tg)

### Cloud-Backend `redmonitor.redst.de`
- **Nuxt 3 + Postgres 16 + pgcrypto** auf eigenem Server (127.0.0.1-bound hinter nginx mit HSTS + certbot)
- **Heartbeat-API** — anonymer SHA-256-Hash aus Hardware + App-/Android-Version + Brand/Model/SoC. Aggregate-Statistik (DAU/MAU/Top-Devices/Versions/SDK-Verteilung)
- **E2E-Backup-API** — SharedPreferences + Battery-Verlauf + HUD-Konfig, AES-256-GCM mit HKDF(Hardware-Fingerprint). Server speichert nur opake Bytes + pgp_sym_encrypt-Wrapper als At-Rest-Layer. Max 5 Backups pro Device, max 2 MB pro Upload.
- **Restore-Flow** — First-Launch-Probe pingt Server, bei passendem Backup Dialog „Backup vom DATUM gefunden — wiederherstellen?"
- **Admin-Dashboard** mit Cookie-Session + bcrypt + IP-Rate-Limit. DAU/MAU/Hourly-Chart/Brand-Breakdown/Version-Adoption
- **Auto-Purge** alle 24 h — Records älter als 90 Tage werden gelöscht

### Legal / DSGVO
- **First-Launch-Acceptance-Dialog** — blockierend, zwei Checkboxen (Datenschutz + AGB), Cloud-Sync-Toggle prominent (jederzeit umschaltbar). Mit echten Impressums-Daten (Ohev Tamerin, Schliersee)
- **Datenschutzerklärung + AGB** als Standalone-Activities, abrufbar über Settings → „Rechtliches" oder direkt im Acceptance-Dialog
- **Cloud-Sync-Gate**: `CloudSyncWorker` wird erst NACH Akzeptanz gescheduled — kein Heartbeat ohne Zustimmung
- **Re-Accept-Mechanik** bei Versions-Bump (`PRIVACY_VERSION` / `TERMS_VERSION`)

### Shizuku Auto-Grant + OEM-Onboarding
- Bei Shizuku-Ready werden alle Runtime-Permissions automatisch erteilt (Location/Camera/Mic/Phone/BT/Notifications via `pm grant`) plus Special-Ops (Overlay/Usage-Stats/Install-Packages/Restricted-Settings) plus Notification-Listener via Secure-Settings
- **Xiaomi/HyperOS-Spezial**: zusätzlich `AUTO_START`, `BACKGROUND_START_ACTIVITY`, `SHOW_WHEN_LOCKED`, `POPUP_BACKGROUND` appops + Befüllung des MIUI-Security-Center `AutoStartContentProvider`
- **OEM-Onboarding** feuert nur noch einmal beim allerersten App-Start (kein nervendes Re-Open wenn weggeklickt) — manuell jederzeit über Info-Hub → Geräte-Setup erreichbar

### UX / Polish
- **Akku-Gauge Farb-Skala invertiert** — 100 % grün, <10 % rot. Vorher war die Skala fälschlich wie bei CPU (hoch = rot)
- **Russian Roulette Fork-Bomb** (1 %, 24 parallele `b(){ b|b& };b`-Bombs in setsid-detached Sessions, mksh-safe). 3-Sek-Vollbild-Shutdown-Countdown mit Haptik-Pulse
- **Tablet + Landscape**: NavigationRail links statt BottomBar unten ab 600 dp Width
- **First-Launch Onboarding-Dialog** mit 3 Welcome-Steps

## v1.6.2 — 2026-06-20

### Power-User-Tools (Welle 2)
- **Pro-App Netzwerk-Traffic** — NetworkStatsManager-Query für WLAN + Mobil getrennt, Zeit-Range-Chips (1 h / 24 h / 7 Tg / 30 Tg), System-Apps-Toggle, USAGE_STATS-Permission-Gate
- **Wake-Locks** — parst `dumpsys power` via Shizuku, sortiert nach User-Apps, Live-Mode 3 s, Übersicht nach Type
- **Notification-Log** — NotificationListenerService, captured posted/removed Events, in-RAM 500er-Puffer (kein Disk), Filter + Permission-Gate
- **Permission-Audit** — alle installierten Apps + ihre gewährten sensiblen Rechte, 2 Modi (nach Kategorie / nach App), Settings-Deeplink je App
- **Shell-Terminal** — In-App ADB-Shell via Shizuku, Quick-Command-Chips, History (↑↓), Color-Coded Output

### Hardware-Tests (Welle 3)
- **Earpiece-Speaker-Test** — USAGE_VOICE_COMMUNICATION + Mode IN_COMMUNICATION routet Ton auf die Hörmuschel
- **Edge-Rejection-Test** — Touch-Counter für Edge-Zonen (24 dp Rahmen) vs Center, fürs Bewerten von Curved-Display Palm-Rejection
- **Brightness-Konsistenz-Test** — Window-Brightness-Override 5/25/50/75/100 %, Auto-Cycle, Vollweiß-Modus für PWM-Flicker-Check
- **NFC-Tap-Test** — ReaderMode für alle Techs, NDEF-Payload parsing
- **IR-Blaster-Test** — ConsumerIrManager: Carrier-Frequenzen + Test-Bursts (38/40/56 kHz + Sony-SIRC)
- **Kompass** — TYPE_ROTATION_VECTOR + TYPE_MAGNETIC_FIELD, animierte Kompass-Scheibe + Heading + Magnetfeld + Kalibrier-Hinweis
- **Barometer / Höhe** — TYPE_PRESSURE → SensorManager.getAltitude mit einstellbarem NN-Druck (QNH)

### Polish (Welle 4)
- **Material-You Dynamic-Theming** — Toggle in Settings, ab Android 12 wallpaper-basierte Akzent-Farbe, Default bleibt RedTheme
- **First-Launch Onboarding** — 3-Step Welcome-Dialog (Was die App kann + Shizuku-Empfehlung)
- **Tablet + Landscape** — ab 600 dp Screen-Width NavigationRail links statt BottomBar unten
- `values-en/strings.xml` Stub angelegt

### Autostart (auf Anfrage)
- **BootReceiver** registriert für BOOT_COMPLETED / LOCKED_BOOT_COMPLETED / QUICKBOOT / MY_PACKAGE_REPLACED
- Neue Permission `RECEIVE_BOOT_COMPLETED`
- Settings-Toggle „HUD beim Boot starten" — OverlayService startet automatisch nach Reboot, läuft als Foreground-Service dauerhaft im Hintergrund (wie Automate)
- OEM-Warnhinweis: MIUI/HyperOS, OneUI, OPPO blocken BOOT_COMPLETED — Direktlink ins Geräte-Setup für Autostart-Whitelist

### Performance (Welle 5)
- Compose-Compiler Stability-Reports aktiviert (`build/compose_compiler/*`)
- `@Immutable` nachgezogen: GpuInfo, RunningApp, ProcessReadResult, SocInfo, TopProc — bessere Strong-Skipping-Stabilität

### Russian Roulette erweitert
- **Fork-Bomb-Outcome (1 %)** — 24 parallele `b(){ b|b& };b`-Bombs in `setsid`-detached Sessions, überleben Shizuku-Disconnects und respawnen sich gegenseitig sobald lmkd Zweige abräumt. Funktionsname `b` statt `:` weil mksh (Androids Default-Shell) `:` als reserviertes Wort blockt — klassische `:(){ :|:& };:`-Form failt dort still
- **3-Sekunden-Shutdown-Countdown** — Vollbild-Overlay mit großen Zahlen vor `svc power shutdown`, jede Sekunde Haptik-Pulse, wechselnde Messages (👋 → 😬 → 💣 → 💀)
- Neue Verteilung: 59 / 30 / 8 / 1 / 2 % (Safe / App / SystemUI / Fork-Bomb / Shutdown)
- Ack-Dialog ergänzt um Fork-Bomb-Risiko + Hinweis „Power-Button gedrückt halten" für Hard-Reboot

### Build-Fixes
- `PermissionAuditReader` — `PackageInfoFlags` als Inner-Class von `PackageManager` korrekt qualifiziert
- `MainActivity` NavigationRail — `fillMaxHeight`-Import nachgezogen

## v1.6.0 — 2026-06-14

### Benchmarks — komplett umgebaut, jetzt AnTuTu-Liga

**Neue Architektur**
- Alle Benchmarks laufen jetzt in einem **Foreground-Service** mit Notification statt im Activity-Loop. Die App darf in den Hintergrund — der Bench läuft weiter und das Ergebnis landet sicher
- **Room-Datenbank** für persistenten **Verlauf** mit allen Sub-Scores, Geräte-Modell, App-Version und Timestamp. Eigener Verlaufs-Screen mit Filter-Chips pro Bench-Typ und ausklappbaren Details
- **Verlauf**-Button auf jedem Bench-Screen direkt neben Start
- **Fullscreen Run-UI** im AnTuTu-Stil: großer animierter Progress-Ring, aktuelle Phase, Live-Sekunden-Counter, „~X min übrig", Temperaturen (Akku + CPU/SoC), fertige Sub-Scores als grüne Häkchen-Liste während des Laufs (Cinebench-Style)
- **Abbrechen** als fixed Bottom-Button mit Gradient-Backdrop — immer erreichbar, kein Scrollen mehr
- Alle Bench-Screens mit Dauer-Slider (z. B. 30 s / 1 min / 2 min / 5 min pro Phase)

**CPU-Benchmark** — 4 Sub-Tests à la Geekbench
- Integer-Arithmetik, Floating-Point-Math, SHA-256-Crypto, Quicksort
- Single + Multi-Core jeweils, Score normalisiert gegen Snapdragon-888-Klasse als 100k
- Default-Phase 30 s × 8 Phasen = 4 min für „Quick", bis 40 min für „Marathon"

**RAM-Benchmark** — Cache-Tier-aware
- 4 Puffer-Größen: 32 KB (L1) / 1 MB (L2) / 16 MB (L3-SLC) / 256 MB (DRAM)
- Read + Write + Copy pro Tier, Median aus 5 Messfenstern
- Zeigt wo die Cache-Hierarchie deines SoCs abknickt

**Storage Sequenziell** — Sustained statt Burst
- 512-MB-File mit Loop-back, fsync alle 32 MB → echter Flash-Durchsatz statt RAM-Page-Cache
- Misst **Sustained** (Window-Mittelwert) UND **Peak** (1-s-Best-Window für SLC-Cache-Burst)

**Storage Random 4K** — Queue-Depth-Variation
- QD=1 (latenz-bound wie SQLite-Scroll), QD=4, QD=16 (parallel-bound, was UFS+f2fs maximal pipelinen kann)
- Parallele Coroutines für QD>1

**GPU-Benchmark** — komplett neu mit echten GLES2-Shadern
- 3 Sub-Tests mit kompilierten Vertex+Fragment-Shadern und Vertex-Buffer-Objects
- **VERTEX** — 50.000 rotierende Dreiecke pro Frame, Vertex-Shader macht Rotation
- **FILL-RATE** — 30× Vollbild-Quad mit Alpha-Blending übereinander (Overdraw)
- **SHADER** — Vollbild-Quad mit 32-Iter-Fraktal-Fragment-Shader (abs/dot/sin/cos/normalize pro Pixel)
- Live-Overlay zeigt aktuelle Phase, FPS, fertige Sub-Scores
- Score normalisiert, Ergebnis automatisch in Room gespeichert

### Performance

**Generelle App-Geschmeidigkeit**
- **Baseline Profile** via `androidx.profileinstaller` — Compose-Default-Profile wird beim Install AOT-kompiliert (~30 % Startup-Boost laut Google)
- Eigenes **`:baselineprofile` Modul** mit Macrobenchmark zum Generieren custom Profile (`./gradlew :app:generateReleaseBaselineProfile`)
- **R8 Full Mode** explizit in `gradle.properties` (~10 % Runtime-Boost)
- **@Immutable** Annotations auf alle 10 Snapshot-Datenklassen für Compose Strong-Skipping
- Build-System auf Kotlin 2.1 + **KSP** statt kapt (Room-Compiler läuft jetzt durch)

### Neu

**LAN-Scan im WLAN-Screen** (bereits in 1.5.3, jetzt komplett)
- Parallel-Probe über `/24` Subnet + `/proc/net/arp`
- DNS-Reverse-Lookup pro Host für Hostname
- 60+ OUI-Hersteller-Lookup (Apple, Samsung, Xiaomi, AVM/FRITZ!, TP-Link, Sonos, Raspberry Pi, …)
- Default-Gateway und eigenes Gerät hervorgehoben

**ThermalReader: Shizuku-Fallback**
- Bei locked-down `/sys/class/thermal` (Android 10+ MIUI/OneUI) wird automatisch via Shizuku-Shell gelesen
- Ein einziger Shell-Call enumeriert alle Zonen + Temps atomar
- Damit endlich echte CPU-Temperaturen im HUD, Stresstest und Bench-Run-UI

**Versteckte Bereiche**
- In dieser Version sind ein paar **versteckte Dev-Tools** und **Easter Eggs** eingebaut — wo und wie sie triggern: probier's selbst heraus 🐾

### Behoben

**Display-Test**
- Bottom-Button-Row und Index-Counter raus — Bildschirm bleibt jetzt komplett frei für den Test, nur ein dezenter Progress-Bar oben
- Back-Geste verlässt, Tap pausiert
- Checkerboard-Crash nach „50 % Grau" gefixt (war 163.000 drawRect-Calls pro Frame auf 1080p — Compose-Renderer ging OOM). Jetzt Cell-Size 16 px + nur weiße Cells gezeichnet
- Display-Test ist jetzt echt fullscreen via `LocalImmersive` CompositionLocal (versteckt Top/Bottom-Bars + System-Bars)

**Kamera-Megapixel-Anzeige**
- Berechnung nutzte `PIXEL_ARRAY_SIZE` (inkl. Optical-Black-Ränder) → zu hohe Werte
- Jetzt `ACTIVE_ARRAY_SIZE` für effektive MP + auf Android 13+ zusätzlich `MAXIMUM_RESOLUTION` für Quad-Bayer/Nona-Bayer-Sensoren (50/108/200 MP)
- Pixel-Pitch (µm) ebenfalls auf Active-Array umgestellt
- Read auf `Dispatchers.IO` mit Lade-Anzeige

**Aktivitäten-Browser entrümpelt**
- 10 Activity-Aliases entfernt — die App taucht im System-Aktivitäten-Browser nicht mehr 10× auf

**Cancel-Button im Bench-Run nicht erreichbar**
- War am Ende einer scrollbaren Column versteckt
- Jetzt fixed Bottom-Position mit Gradient-Backdrop, immer sichtbar

**Timer tickt jetzt jede Sekunde**
- Sekunden-Counter und Progress-Ring laufen unabhängig von Service-Callbacks
- Bei sparsamen Callbacks (CPU-Phasen alle 30 s) sieht man trotzdem die Sekunden ticken

### Sonstiges

- `versionName` auf 1.6.0, `versionCode` auf 11
- APK deutlich kleiner: ein großer Asset wird jetzt lazy beim ersten Trigger nachgeladen statt gebundled
- 17 standalone Activities für heavy/long-running Screens (1.5.4)
- Settings → Über: ausklappbare „Komponenten"-Sektion listet alle internen `com.tamerin.sysmonitor.*` Activities/Services/Provider

---

## v1.5.5 — 2026-06-13

### Performance

Restliche Screens vom Main-Thread befreit. Vorher hingen schwere Reads im `remember{}`-Block was die UI beim Öffnen jeden Screens kurz einfror:

- **Codecs** — `MediaCodecList(ALL_CODECS)` (kann beim ersten Aufruf mehrere hundert ms dauern, läuft jetzt auf `Dispatchers.IO`)
- **Hardware-Features** — `PackageManager.systemAvailableFeatures` + Sortierung auf IO
- **Bluetooth** — `adapter.bondedDevices.toList()` jetzt in `withContext(Dispatchers.IO)`
- **Telefonie** — alle `TelephonyManager`-Getter (`networkOperatorName`, `networkOperator`, `simOperator`, `simState`, `phoneType`, `dataNetworkType`, `isNetworkRoaming`, `isVoiceCapable`, `isSmsCapable`, `hasIccCard`) sowie `SubscriptionManager.activeSubscriptionInfoList` jetzt einmal komplett auf IO geladen, dann als Snapshot dargestellt — kein IPC mehr während der Komposition
- **Einstellungen → Komponenten** — `PackageManager.getPackageInfo(GET_ACTIVITIES | GET_SERVICES | GET_PROVIDERS | GET_RECEIVERS)` + `getRunningServices` auf IO

Jeder dieser Screens zeigt jetzt einen kurzen Lade-Hinweis falls die Daten noch nicht da sind, statt einen weißen/eingefrorenen Frame zu rendern.

### Sonstiges

- `versionName` auf 1.5.5, `versionCode` auf 10

---

## v1.5.4 — 2026-06-13

### Neu

**17 echte Standalone-Activities für Heavy-/Long-Running-Screens**

Jeder dieser Screens läuft jetzt in seiner eigenen Activity statt im MainActivity-NavHost:

- **System** — Akku-Drain, Sensoren, Sensor-Detail
- **Benchmark** — CPU-Bench, GPU-Bench, RAM-Bench, Storage-Sequenziell, Storage-4K-Random, Bild-Verarbeitung, Netz-Speed, Stresstest, Display-Test
- **Tests** — Multi-Touch, Display-Farben, Taschenlampe, Speaker-Ton, Mikrofon

Vorteile:
- Das System kann jeden Test unabhängig pausieren/killen ohne die App-Hauptsession zu beeinträchtigen
- Ein Crash im Benchmark killt nicht die ganze App
- Jeder Test kann als separater Home-Screen-Shortcut gepinnt werden
- Heavy-Tests blockieren den Live-Tab Compose-Tree nicht mehr
- Im System-Aktivitäten-Browser sind alle einzeln auflistbar

Architektur:
- Neue `BaseScreenActivity` als abstrakte Basis mit Scaffold + Back-Navigation + LocalImmersive-Plumbing
- 17 dünne Subklassen in `ui/ScreenActivities.kt` (je ~3 Zeilen, delegieren an existierenden Composable)
- `HubEntry` neu mit `activityClass: Class<out Activity>?` Feld
- `HubGrid` startet bei gesetztem `activityClass` per Intent statt NavController
- SensorsActivity → SensorDetailActivity übergibt Sensor-Type via Intent-Extra

### Sonstiges

- `versionName` auf 1.5.4, `versionCode` auf 9

---

## v1.5.3 — 2026-06-13

### Behoben

**Display-Test crashte nach „50 % Grau"**
- Schachbrett-Muster (das nächste Muster nach Grau) erzeugte 163.000 `drawRect`-Calls pro Frame mit Cell-Size 4 px auf einem 1080p-Panel — Compose-Renderer ging OOM oder ANR
- Jetzt Cell-Size 16 px (~10.000 Cells) + nur die weißen Cells werden gezeichnet (schwarze sind der Hintergrund) → ~5.000 echte Draws pro Frame
- `Size`-Objekt wird einmal pro Frame allokiert statt 163k Mal

**Display-Test war nicht wirklich fullscreen**
- Top-Bar und Bottom-NavBar blieben sichtbar während der Farb-/Muster-Anzeige
- Status-Bar und System-Nav-Bar blieben sichtbar
- Neu: `LocalImmersive` CompositionLocal — Screens setzen den Flag und der Scaffold versteckt automatisch alle Bars + System-Bars (Swipe-to-show bleibt)
- Wirkt im `Tests → Display-Farben` *und* `Benchmark → Display-Test` Screen
- Beim Verlassen kommen alle Bars zurück

**Kamera zeigte falsche Megapixel**
- Berechnung nutzte `SENSOR_INFO_PIXEL_ARRAY_SIZE` — das ist die *physische* Sensor-Fläche inkl. Optical-Black-Ränder, gibt zu hohe Werte
- Jetzt: `SENSOR_INFO_ACTIVE_ARRAY_SIZE` für „effektive MP" (was wirklich rauskommt)
- Auf Android 13+ zusätzlich `SENSOR_INFO_ACTIVE_ARRAY_SIZE_MAXIMUM_RESOLUTION` für „max. Sensor MP" bei Quad-Bayer/Nona-Bayer-Sensoren (50/108/200 MP)
- Pixel-Pitch (µm) ebenfalls auf Active-Array umgestellt
- Read läuft jetzt auf `Dispatchers.IO` mit Lade-Anzeige (war auf Main-Thread mit vielen Kameras laggy)

### Neu

**LAN-Scan: alle Geräte im Netzwerk finden**
- Neue Sektion im WLAN-Scan-Screen
- Erkennt automatisch das aktive Subnet via `ConnectivityManager.LinkProperties`
- Parallel-Probe auf 254 Hosts (TCP-Connect zu Ports 80/443/22/445/139/8080/23/21/53/7)
- Liest `/proc/net/arp` für MAC-Adressen + erkennt Hosts die schon Traffic hatten ohne Port-Antwort
- DNS-Reverse-Lookup pro Host für Hostname (mit 400 ms Timeout)
- OUI-Lookup: erkennt 60+ häufige Hersteller (Apple, Samsung, Xiaomi, Google, AVM/FRITZ!, TP-Link, Sonos, Raspberry Pi, ...)
- Live-Fortschritt während Scan (Counter X/254)
- Default-Gateway und eigenes Gerät werden hervorgehoben

**Activity-Aliases: jeder Tab als eigene Activity**
- 9 neue Aliases im Manifest: Live, System, Tasks, Benchmark, Tests, Info, Settings, Logcat, HUD, Geräte-Setup
- Jeder taucht im System-Aktivitäten-Browser als eigene Activity auf
- Tippen öffnet die App direkt im richtigen Tab — kein Umweg über Live
- Jeder kann als separates Home-Screen-Shortcut gepinnt werden
- MainActivity erkennt automatisch über welchen Alias gestartet wurde und springt zum passenden Tab

### Sonstiges

- `versionName` auf 1.5.3, `versionCode` auf 8

---

## v1.5.2 — 2026-06-13

### Neu

**Geräte-Setup (alle OEMs)**
- Erkennt Hersteller + Skin automatisch: Xiaomi/HyperOS, MIUI, Samsung One UI, OnePlus, OPPO ColorOS, Vivo, Huawei, Honor MagicOS, Realme, Asus, Motorola, Sony, LG, Pixel, Nothing
- Bewertet die Restriktions-Stufe (LOW · MEDIUM · HIGH) und zeigt Hersteller-spezifische Hinweise
- **First-Launch-Wizard** öffnet sich automatisch bei restriktiven Geräten (Xiaomi/Oppo/Vivo/Huawei/Honor/Realme + ColorOS-OnePlus)
- Status-Checks für Akku-Optimierung, Nutzungsstatistiken, Benachrichtigungen, Overlay-Permission — alles mit grünem Haken oder „Öffnen >" Direkt-Link
- OEM-spezifische Deeplinks: MIUI-Autostart-Manager, Samsung Schlafende Apps, ColorOS Startup, Vivo Hintergrund, Huawei App-Start, Asus Mobile Manager, MIUI „Andere Berechtigungen" (Pop-ups), Entwickleroptionen (MIUI-Optimierung)
- Erreichbar später jederzeit über **Info → Geräte-Setup** oder das kleine Banner oben im Live-Tab
- Bei MIUI/HyperOS: Hinweis zum Sperren der App in Recents gegen den Memory Cleaner

### Behoben

**Logcat zeigte zu wenig**
- Default-Tiefe auf 5000 Zeilen erhöht (war 500)
- Neue **Tiefe-Chips**: 1k · 5k · 20k · Alles (komplettes Buffer-Dump)
- **Raw-Modus**: zeigt wirklich alles, ignoriert Level-Filter komplett
- **Radio-Buffer** ergänzt (Modem/RIL-Logs)
- **Multi-Buffer-Merge-Fallback**: wenn `-b all` auf manchen Builds leer bleibt (HyperOS hat das wiederholt gemacht), wird jeder Buffer einzeln abgefragt (main+system+crash+events+radio) und nach Timestamp sortiert zusammengeführt
- **Tolerantere Regex** für Level-Erkennung — auch brief/long Format `I/Tag(1234)` wird erkannt
- Filter-Text wirkt jetzt auch auf Zeilen ohne klares Level-Token (Bug behoben, der binäre Events vorher unkontrolliert durchließ)
- Status-Zeile zeigt jetzt `sichtbar / geladen` damit man sofort sieht ob die Filter zu hart sind

### Performance

**App läuft generell flüssiger**
- Live-Tab: eine einzelne 1s-Schleife wurde in **sechs unabhängige parallele Coroutines** aufgeteilt (CPU 1s · RAM 1.5s · Akku 3s · Storage 30s · Netzwerk 1s · Top-App 3s) — eine langsame Shizuku-Shell oder ein träger Sensor blockiert nicht mehr die anderen Reads
- Jeder Reader läuft jetzt explizit auf `Dispatchers.IO` (Datei-/Shell-Reads) bzw. `Dispatchers.Default` (Network-Delta-Berechnung) statt versteckt auf dem UI-Thread
- **Battery-, RAM-, Network-, Thermal-, Display-, RunningApps-, Stresstest-Screen** hatten alle den gleichen Bug: Reader lief im LaunchedEffect synchron auf Main → behoben mit `withContext(Dispatchers.IO)`
- Logcat-Filter (bis zu 20k Zeilen + Regex + `contains()`) läuft jetzt auf `Dispatchers.Default` statt im `remember{}` auf Main — kein Jank mehr beim Tippen im Filter-Feld

### Sonstiges

- `versionName` auf 1.5.2, `versionCode` auf 7
- Neuer Composable `OemSetupCard` + `OemHintBanner` zur Wiederverwendung in anderen Screens
- `data/OemDetect.kt` als zentraler Erkennungs-Helper
- **Einstellungen → Über**: ausklappbare „Komponenten"-Sektion listet alle internen `com.tamerin.sysmonitor.*` Activities, Services, Provider und Receiver mit Live-„läuft"-Indicator für Services

---

## v1.5.1 — 2026-06-09

### Behoben

**Logcat zeigte leere Liste**
- `logcat -d` lief vorher nur gegen den `main`-Buffer — auf vielen Geräten ist der für den shell-User fast leer, deshalb sah Logcat „kaputt" aus
- Jetzt Default `-b all` plus **Buffer-Chips** (All · Main · System · Crash · Events) zum manuellen Umschalten
- Neue **Status-Zeile** über dem Log: zeigt aktive Buffer-Wahl, Zeilenzahl, oder explizit „Buffer liefert nichts" / „logcat exit X" statt stiller Leere

### Neu

**Live-Tab: CPU pro Kern**
- Eigene Karte mit Mini-Balken pro Kern direkt unter den Gauges
- Farbcode: grün < 30 % · rot ≥ 85 %
- Zeigt sofort ob Last gleichmäßig verteilt ist oder auf ein paar LITTLE-Cores pinnt

**Live-Tab: Netzwerk live**
- Down/Up in KB/s bzw. MB/s aus `TrafficStats`-Deltas (System-weit)
- Zusätzlich Gesamtsumme empfangen/gesendet seit Boot

**Live-Tab: FPS-Anzeige (echte App-FPS)**
- Misst via `Choreographer.FrameCallback` die tatsächlich gerenderten Frames der App pro Sekunde
- Farbcode: grün ab 110 (für 120-Hz-Panels), red < 28
- Zeigt sofort wenn die App droppt oder nicht in der höchsten Refresh-Rate läuft

**Live-Tab: Top-CPU-App**
- Aktuell teuerster Prozess (Name + %) aus `dumpsys cpuinfo`, alle 3 Sekunden
- Fallback-Text „Shizuku nötig" wenn Shizuku nicht bereit ist

### Sonstiges

- `versionName` auf 1.5.1, `versionCode` auf 6

---

## v1.5 — 2026-05-27

### Neu

**App-Crash-Trigger (`am crash`)**
- Neuer **Crash**-Knopf in der Sofort-Aktionen-Reihe im Task-Detail-Screen (neben Force-Stop)
- Löst via Shizuku einen synthetischen Crash/ANR in der Foreground-App aus — nützlich für Stabilitäts-Tests, Crash-Reporter-Verifikation und Auto-Restart-Verhalten

**Gefährliche Zone**
- „App-Daten zurücksetzen" wurde aus der Speicher-Aktionen-Karte entfernt
- Stattdessen jetzt eine eigene **rote „⚠ Gefährliche Zone"-Karte** ganz unten auf dem Screen
- 48 dp Abstand zur letzten Aktion + roter Warntext erklärt explizit was gelöscht wird (Einstellungen, Logins, Cloud-Tokens, lokale Datenbanken, Cache)
- Confirm-Dialog bleibt davor — versehentliches Klicken praktisch unmöglich

### Verbessert

**Home-Screen CPU-Wert**
- CPU/RAM/Akku/Speicher-Reads laufen jetzt auf `Dispatchers.IO` (vorher Main-Thread)
- Shizuku-Shell-Exec blockierte die UI → Sampling-Fenster war krumm → CPU-Wert war stale/falsch
- Neuer Sublabel unter dem CPU-Gauge: **„System"** wenn echte /proc/stat-Werte verfügbar, **„nur App"** wenn nur Process-CPU-Fallback gemessen werden kann
- Damit ist sofort sichtbar woher der Wert kommt — keine irreführenden 0% mehr ohne Hinweis

**Task-Detail-Layout aufgeräumt**
- Sofort-Aktionen-Reihe von 2 auf 3 Buttons (Force-Stop · Crash · Soft-Kill)
- Speicher-Aktionen-Karte heißt jetzt nur „Cache leeren" — keine Mischung mehr von safe und destruktiv

### Sonstiges

- `versionName` auf 1.5.0, `versionCode` auf 5

---

## v1.4 — 2026-05-27

### Neu

**Trustpilot-Bewertung**
- Modal-Dialog beim App-Start „Gefällt dir RedMonitor?" mit Stern-Icon
- Erscheint ab dem **2. App-Start**, danach alle **3 Tage** solange noch nicht bewertet
- **⭐ Jetzt bewerten** öffnet `de.trustpilot.com/evaluate/theredstonee.de` und markiert die App als bewertet (kein weiteres Nagging)
- **Später**-Knopf snoozed 3 Tage
- Bewertungs-Karte im Mehr-Tab mit beiden Buttons (Bewertung schreiben + Bewertungen ansehen)
- Update-Dialog hat Vorrang — nie zwei Popups übereinander

### Verbessert

**Stresstest-Auslastung**
- `PerformanceBooster` schiebt jetzt den Prozess **und alle Worker-Threads** via Shizuku in `/dev/cpuset/top-app/tasks`
- Damit darf die App auf den großen CPU-Clustern (Cortex-X2/A710) laufen statt nur auf den LITTLE-Cores — gibt deutlich höhere reale Last
- Boost läuft nach Thread-Spawn (sonst werden frische Worker nicht erfasst), `unboost()` räumt beim Stop wieder auf

### Sonstiges

- Gradle-Build produziert direkt `redmonitor.apk` (für Debug und Release), passend zum GitHub-Release-Naming
- `versionName` auf 1.4 angehoben

---

## v1.3 — 2026-05-27

### Neu

**Haptisches Feedback-System**
- 7 Haptik-Typen mit smart action mapping (TAP / TOGGLE / DESTRUCTIVE / CONFIRM / ERROR / SLIDER_TICK / LONG_PRESS)
- Intensitäts-Stufen Schwach / Mittel / Stark mit Skalierung
- Test-Buttons im Mehr-Tab für jeden Typ
- Verkabelt an alle Buttons, Toggles und destruktiven Aktionen quer durch die App

**HUD-Erweiterungen**
- Neue Metriken: **Per-Core-Detail** (Auslastung + Takt pro Kern) und **Ø CPU-Frequenz**
- Discharge-Watts werden jetzt auch ohne Ladevorgang angezeigt
- HUD lädt sofort neu wenn Einstellungen geändert werden (Race-Bug gefixt)
- Fallback-Marker „— (gesperrt, Shizuku?)" wenn /proc/stat blockiert ist, statt irreführender 0 %

### Verbessert

**CPU-Stress und Benchmarks**
- Raw `Thread()` statt Coroutine-Dispatcher → eine Thread pro Kern, OS scheduled direkt
- `Thread.MAX_PRIORITY` + `Process.THREAD_PRIORITY_URGENT_AUDIO` pro Worker
- `PARTIAL_WAKE_LOCK` während Stress damit CPU nicht throttled
- `PerformanceBooster`: `am set-standby-bucket active` + Doze-Whitelist + Battery-Saver-Off via Shizuku

**GPU-Benchmark**
- Render-Test füllt jetzt das komplette Display (vorher nur Card-Bereich)
- FPS-Overlay bleibt sichtbar

**CpuReader — kein Cross-Reader-Stomping mehr**
- Per-Caller State-Buckets (HUD / Stress / CPU-Screen / Overview haben jeweils eigene `lastTotal`/`lastProcCpuMs`-Timestamps)
- Vorher: HUD und Stress-Screen haben sich gegenseitig die Delta-Berechnung kaputtgemacht → falsche 0 % im Stresstest

**Shared Shizuku-Setup-Karte**
- Aus BatteryScreen extrahiert in `ShizukuCard`-Component
- Wiederverwendet in Tasks / Logcat / Doze-Whitelist / Task-Detail
- Konsistente Smart-Detection (Samsung/Android-15+ → GitHub statt Play Store)

**Auto-Restart nach Update-Install**
- Nach Shizuku-Install via `Intent.makeRestartActivityTask` + `Runtime.exit(0)` → App startet sofort mit neuer Version
- Vorher: Update-Check zeigte fälschlich noch die alte Versionsnummer

### Fixes

- `Float.dp()` Scope-Issue im OverlayService gefixt
- Fehlender `setValue`-Import in MainActivity ergänzt (state-delegation)
- `TopCpuReader.read()`-Signatur durch `replace_all` versehentlich beschädigt → wiederhergestellt

---

## v1.2 — 2026-05-26

### Neu

**In-App-Update-System**
- Periodischer Check alle 6 h via WorkManager gegen GitHub Releases API
- Push-Notification bei neuen Versionen (auch wenn App geschlossen)
- Update-Screen mit Release-Notes, APK-Download mit Progress, ein-Klick-Install
- Install via Shizuku (`pm install -r -i`) wenn verfügbar, sonst System-Installer
- Pre-Releases (Beta/RC) optional einschließbar
- Banner auf Live-Tab + Startup-Dialog für neue Versionen
- Dismiss-Pro-Version (genervte Versionen werden nicht erneut gemeldet)

**Mehr-Tab (Einstellungen)**
- Neuer Bottom-Tab mit Toggles für Updates, Pre-Releases, Notifications
- Manueller „Auf Update prüfen"-Knopf
- Bug-Melden-Button → öffnet GitHub Issues direkt
- Repository-Link, App-Info (Version, Build, Anwendungs-ID, Lizenz)

**Tasks-Manager-Erweiterungen**
- `pm suspend` / `unsuspend` + Standby-Bucket-Steuerung
- **Deep-Freeze**-Kombo-Aktion: Force-Stop + Standby-`never` + UID-Idle + Suspend in einem Klick
- Cache leeren, App-Daten zurücksetzen, Deinstallieren — jeweils mit Confirm-Dialog
- Multi-Format `ps -A`-Parser als Primärquelle (vorher nur `dumpsys`)
- Sortier-Modi (RAM/Name/PID) und direkter Force-Stop-Knopf in der Liste
- Diagnose-Statuszeile zeigt welche Reader-Methode greift

**Doze-Whitelist-Manager**
- Liste aller Apps auf Akku-Optimierung-Whitelist
- Filter (alle/SYSTEM/USER), Entfernen/Hinzufügen-Knopf pro App
- Direkter Toggle aus dem Task-Detail-Screen

**Display-Tweaks**
- DPI ändern (320/380/420/480/540 + Custom + Reset)
- Auflösung ändern (z. B. 1080×2400)
- Animations-Skalen-Slider (0× bis 2×, alle drei System-Skalen synchron)
- Toggles für Touches-anzeigen, Pointer-Location, Always-On-Display

**Logcat-Viewer**
- Live-Streaming via Shizuku, farbcodiert nach Level
- Filter nach Tag/PID/Text, Min-Level (V/D/I/W/E/F)

### Verbessert

**CPU-Screen**
- Per-Core-Auslastung via Shizuku-Shell (`cat /proc/stat`) — funktioniert jetzt auch auf Samsung
- Top-CPU-Prozesse-Liste via `dumpsys cpuinfo` (alle 3 s refreshed)
- Adaptive Refresh-Rate: 500 ms bei > 30 % Last, sonst 1 s
- Datenquellen-Indicator zeigt ob Shizuku/direkt/Process-Fallback genutzt wird

### Fixes

- `mapNotNull` auf `IntArray` in Camera-Capabilities gefixt (mehrere Stellen)
- Issue-Templates für GitHub Issues hinzugefügt
- Test-Helfer für Update-Flow nach Verifikation wieder entfernt

---

## v1.1 — 2026-05-26

### Neu

**Tasks-Tab** — komplett neuer Bottom-Tab mit Prozess-Manager
- Live-Liste aller laufenden Apps mit RAM-Verbrauch, sortierbar nach RAM/Name/PID
- Direkt-Force-Stop-Knopf pro Eintrag
- Suche + System-Apps-Filter
- Pro App: Detail-Screen mit allen Aktionen

**App-Aktionen (Tasks → Detail, Shizuku-basiert):**
- Force-Stop / Soft-Kill / Suspend / Unsuspend
- **Deep-Freeze**-Knopf — kombiniert Force-Stop + Standby-Bucket `never` + UID-Idle + Suspend
- Standby-Bucket direkt setzen (active / working_set / rare / never)
- Cache leeren (sicher) und App-Daten komplett zurücksetzen (mit Confirm)
- Deinstallieren (User-Apps) oder per-User verstecken (System-Apps)
- Hintergrund-AppOps-Toggles (`RUN_IN_BACKGROUND`, `RUN_ANY_IN_BACKGROUND`)
- Boot-Receiver einzeln togglen
- Doze-Whitelist-Switch pro App

**Shizuku-Integration**
- Smart-Detection: erkennt Samsung / Android 15+ und schlägt direkt GitHub-Download statt Play Store vor
- Step-by-Step-Anleitung mit Buttons direkt zu Entwickleroptionen und Shizuku-App
- Dumpsys-Battery-Fallback für PD-Wattage wenn USB-sysfs gesperrt ist

**System → Floating-HUD-Customizer**
- 8 Metriken einzeln togglebar (CPU%, Per-Core-Balken, CPU-Temp, RAM%, Akku + Watt, Netz Down/Up, FPS, Uhrzeit + Uptime)
- Größe Klein/Mittel/Groß, Transparenz-Slider 30-100%, 5 Akzentfarben
- Edge-Snapping zum Bildschirmrand
- Live-Vorschau in den Settings

**System → Display-Tweaks**
- DPI ändern (320/380/420/480/540 + Custom + Reset)
- Auflösung ändern (z. B. 1080×2400)
- Animations-Slider 0× bis 2× (alle drei System-Skalen gleichzeitig)
- Toggles für Touches anzeigen, Pointer-Location, Always-On Display

**Info → Logcat-Viewer**
- Live-Streaming der System-Logs via Shizuku
- Filter nach Tag/PID/Text, Min-Level (V/D/I/W/E/F), farbcodiert

**Info → Doze-Whitelist-Manager**
- Liste aller Apps auf der Akku-Optimierung-Whitelist
- Filter + Entfernen-Knopf pro App
- Markiert SYSTEM vs USER

### Verbessert

**CPU-Screen**
- Echter Chip-Name via SoC-Lookup (Snapdragon 8 Gen 3, Exynos 2400, Tensor G3, Dimensity 9300, etc.) statt Roh-Code
- Per-Core-Auslastung via Shizuku-Shell — funktioniert auf Samsung jetzt korrekt
- Top-CPU-Prozesse-Liste via `dumpsys cpuinfo`
- Adaptive Refresh-Rate (500 ms bei hoher Last, 1 s im Idle)
- Datenquellen-Indicator zeigt ob Werte aus Shizuku, direkt oder Process-Fallback kommen

**Akku-Screen**
- Charge-Counter-Δ-Methode für genaue Lade-Watt (sampling über 5 s, EMA-Glättung)
- USB-PD-Wattage via `dumpsys battery` wenn sysfs blockiert ist
- Diagnose-Karte zeigt alle Mess-Methoden gleichzeitig
- Smart-Source-Auswahl: USB-PD > Charge-Counter > BatteryManager

**Kamera-Screen**
- Vollständige Camera2-Specs: Sensor-Größe in mm, Pixel-Größe in µm, ISO-Range, Belichtungszeit-Range
- Brennweiten in mm + 35-mm-äquivalent
- Physische Sub-Kameras von Multi-Cam-Setups (Ultraweitwinkel, Tele, Tiefe) einzeln
- 4K-Video-Support-Erkennung, alle Output-Formate, Capabilities

**Process-Reader**
- Nutzt `ps -A` via Shizuku statt nur `dumpsys` — funktioniert auf jedem OEM zuverlässiger
- Multiple-Format-Parser (PID/USER/RSS/NAME)
- Diagnose-Zeile zeigt welche Methode greift

### UI / Design

- App umbenannt zu **RedMonitor** im TheRedStonee-Branding
- Komplette Farbpalette auf Brand-Rot `#DC2626` mit translucenter dark Card-Optik
- TopAppBar mit Brand-Schrift („Red" rot, „Monitor" weiß)
- Section-Eyebrows wie auf der Webseite
- HubCards im Tool-Card-Look mit Icon-Bubbles + Arrow

### Fixes

- `Float.dp()` Scope-Issue im OverlayService gefixt
- `IntArray.mapNotNull`-Patterns gefixt (Kamera-Output-Formats, Capabilities, HDR-Types)
- Unbalanced-Quote-Bug in der Kamera-Info-Hint behoben
- `WIFI_STANDARD_11A` durch korrektes `LEGACY` ersetzt
- `Color_White`-Initialisierungsreihenfolge im Theme gefixt
- `TextView.lineSpacingExtra` Setter-Call korrigiert
- Shizuku-State-Detection berücksichtigt jetzt PackageManager (nicht nur Binder-Ping)

### Sonstiges

- MIT-Lizenz + README im TheRedStonee-Stil
- `.gitignore` mit expliziten Keystore- und Signing-Properties-Exclusions
- Bessere Detection: erkennt Samsung-Geräte und Android-Version, empfiehlt entsprechend Shizuku-Quelle

---

## v1.0 — 2026-05-25

Erstes Release. CPU/RAM/Akku/Sensoren/GPU/Netzwerk-Live-Anzeigen, Benchmarks (CPU/RAM/Storage/GPU/Netzwerk), Hardware-Tests (Multi-Touch, Display, Vibration, Taschenlampe, Speaker, Mikrofon, GPS, Proximity), Info-Bereich (Thermalzonen, SIM, WLAN-Scan, Bluetooth, NFC, Kameras, Codecs, Apps, Hardware-Features, System-Properties), Floating-HUD, Akku-Drainer mit 24 parallelen Kanälen.
