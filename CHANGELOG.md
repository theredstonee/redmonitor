# Changelog

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
