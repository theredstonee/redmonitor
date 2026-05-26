# Changelog

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
