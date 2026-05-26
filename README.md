# <span style="color:#dc2626">Red</span>Monitor

> Android-System-Monitor + Benchmark + Hardware-Tester. Werbefrei, Open Source, gebaut in Deutschland.
> Ein Projekt aus dem **TheRedStonee**-Universum.

[![Lizenz: MIT](https://img.shields.io/badge/Lizenz-MIT-dc2626.svg)](LICENSE)
[![Kotlin 2.1](https://img.shields.io/badge/Kotlin-2.1-7F52FF.svg)](https://kotlinlang.org/)
[![Min SDK 29](https://img.shields.io/badge/Min%20SDK-29-3DDC84.svg)](#)

---

## Was die App macht

Alles auf einen Blick auf deinem Android-Gerät: **Live-Auslastung von CPU, RAM, Akku und Speicher**, ein vollständiger **Sensoren-Browser** mit Detail-Graphen, **Benchmarks** für CPU/RAM/Storage/GPU/Netzwerk, **Hardware-Tests** für Display, Speaker, Mikrofon, Vibration, Taschenlampe und GPS, plus tiefe **System-Infos** wie Thermalzonen, alle Codecs, Build-Properties und Hardware-Features.

Bonus: **Floating-HUD** über allen Apps und ein **Akku-Drainer** mit 24 parallelen Drain-Kanälen für Stress-Tests.

---

## Features

### Live
- 4 Ring-Gauges (CPU, RAM, Akku, Speicher) mit Sekunden-Updates
- Brand-Header mit Modell, Android-Version, Display-Specs

### System
- **CPU** — Last pro Kern, Frequenz min/max/aktuell, Governor, ABIs
- **RAM & Speicher** — Belegt/frei/gesamt, Low-Memory-Schwelle
- **Akku** — Echte Watt-Berechnung via Charge-Counter-Δ + optional Shizuku für PD-Wattage
- **Sensoren** — Liste aller verbauten Sensoren, klick auf einen für Live-Graph + alle Specs
- **GPU** — Renderer, OpenGL-Version, GLSL, alle Extensions
- **Netzwerk** — Transport, Signal, IP, Traffic
- **Display & Gerät** — Build, Uptime, Java VM, OpenSSL
- **Floating HUD** — kleines Overlay über allen Apps

### Bench
- **CPU** — Single + Multi-Core Score
- **RAM** — Speed in MB/s
- **Storage** — Sequenziell + 4K Random IOPS
- **GPU** — FPS-Render-Test
- **Netz** — Download-Speed via Cloudflare
- **Bild-Verarbeitung** — Megapixel/s CPU-Bildfilter
- **Stresstest** — Vollast + Temperatur-Verlauf
- **Farbraum** — sRGB, HDR-Caps, Gradienten
- **Display-Test** — Auto-Sequenz Vollbild-Muster

### Tests
- **Multi-Touch** — Alle Finger visualisiert
- **Display-Farben** — Dead-Pixel-Check
- **Vibration** — Verschiedene Muster
- **Taschenlampe** — Kamera-LED
- **Speaker-Ton** — Sinus-Generator L/R, 50 Hz – 15 kHz
- **Mikrofon** — Live-Pegelmeter dB FS
- **GPS** — Live-Position + Satelliten-Status
- **Proximity & Licht** — interaktive Live-Anzeige

### Info
- **Thermalzonen** — alle Live-Temperaturen
- **SIM & Telefonie** — Operator, Netz-Typ (4G/5G), Roaming
- **WLAN-Scan** — Umliegende Netze mit Signal/Kanal/Sicherheit
- **Bluetooth** — Adapter + gekoppelte Geräte
- **NFC** — Status
- **Kameras** — Camera2-Specs aller Kameras
- **Codecs** — Alle Audio/Video-Codecs, HW-beschleunigt?
- **Laufende Apps** + **Installierte Apps** + Größen
- **Hardware-Features** + **System-Properties** (alle getprop)

---

## Quick Start

```bash
git clone https://github.com/theredstonee/redmonitor.git
cd redmonitor
./gradlew assembleDebug
```

APK liegt unter `app/build/outputs/apk/debug/app-debug.apk`.

Oder einfach in **Android Studio** öffnen → grünen Run-Knopf drücken.

---

## Tech

- **Kotlin 2.1** + **Jetpack Compose** (Material 3)
- **Min SDK 29** (Android 10) / **Target SDK 35** (Android 15)
- **Gradle 8.13** mit Kotlin DSL + Version Catalog
- Optional: [**Shizuku**](https://shizuku.rikka.app/) für PD-Wattage auf restriktiven OEMs

---

## Privacy

Keine Tracker. Keine Werbung. Keine Telemetrie. Keine externen Server.
Einzige Netzwerk-Calls: der Speedtest gegen Cloudflare (nur wenn du ihn manuell startest).

---

## Lizenz

[MIT](LICENSE) — mach was du willst, behalt den Copyright-Hinweis drin.

---

<p align="center">
  <sub>Built with ❤ by <a href="https://www.theredstonee.de">TheRedStonee</a></sub>
</p>
