package com.tamerin.sysmonitor.legal

import com.tamerin.sysmonitor.legal.LegalConstants as L

object PrivacyText {

    val SUMMARY = """
        RedMonitor ist eine lokale System-Monitoring-App. Standardmäßig bleiben alle
        Daten auf deinem Gerät. Optional kannst du anonymisierte Cloud-Telemetrie und
        ein verschlüsseltes Backup aktivieren — beides jederzeit in den Einstellungen
        wieder abschaltbar.
    """.trimIndent()

    val FULL: String = """
# Datenschutzerklärung — RedMonitor App

Stand: ${L.LEGAL_LAST_UPDATED}

## 1. Verantwortlicher

${L.PROVIDER_NAME}
${L.PROVIDER_REPRESENTATIVE}
${L.PROVIDER_STREET}
${L.PROVIDER_CITY}
${L.PROVIDER_COUNTRY}

Telefon: ${L.PROVIDER_PHONE}
E-Mail: ${L.PROVIDER_EMAIL}
Impressum: ${L.IMPRINT_URL}

## 2. Geltungsbereich

Diese Datenschutzerklärung gilt für die Android-App „RedMonitor" und für den
Backend-Dienst unter ${L.BACKEND_HOST}, soweit dieser von der App
angesprochen wird.

## 3. Lokale Datenverarbeitung (auf deinem Gerät)

Die App liest System-Metriken (CPU, RAM, Akku, Sensoren, Netzwerk, Wake-Locks,
laufende Prozesse u.v.m.) ausschließlich lokal aus Android-APIs und — soweit
verfügbar — über die Shizuku-Brücke. Diese Daten werden im RAM und in lokalen
SQLite-Dateien (Room) gespeichert. Sie verlassen das Gerät NICHT, außer du
aktivierst ausdrücklich die optionale Cloud-Sync-Funktion (siehe Abschnitt 4).

Lokal gespeichert werden u.a.:
- App-Einstellungen (SharedPreferences)
- Akku-Verlauf der letzten 30 Tage (Coulomb-Counter + Ladezustand alle 15 Minuten)
- Benchmark-Ergebnisse (auf deinen Wunsch)
- HUD-Konfiguration
- Notification-Log (nur im RAM, max. 500 Einträge, kein Disk-Persist)

## 4. Optionale Cloud-Synchronisation

Wenn du in den Einstellungen unter „Cloud-Backup" das Feature aktiviert lässt
(Default: aktiv), sendet die App in regelmäßigen Abständen folgende Daten an
${L.BACKEND_HOST}:

### 4.1 Heartbeat (alle ~6 Stunden)
- Device-ID: SHA-256-Hash aus Hardware-Eigenschaften (ANDROID_ID,
  Build.MANUFACTURER, .MODEL, .BOARD, .HARDWARE, .BOOTLOADER) plus einem
  in der App eingebakten Salt. Die ID ist opak — aus ihr lassen sich KEINE
  identifizierenden Daten zurückrechnen.
- App-Version (z.B. „1.6.2")
- Android-Version (SDK-Level + Release-String)
- Geräte-Marke und -Modell (z.B. „Xiaomi", „Mi 11")
- SoC-Bezeichnung (z.B. „taro qcom-sm8450")
- Netzwerk-Typ (wifi / mobile / none) — KEINE WLAN-Namen, KEINE IP
- Akku-Ladezustand in %
- Uptime in Sekunden

Zweck: Aggregierte Statistik (wie viele User, welche Geräteklassen, welche
App-Versionen aktiv). Rechtsgrundlage: Art. 6 Abs. 1 lit. f DSGVO
(berechtigtes Interesse an Produktverbesserung). Bei deaktiviertem Cloud-Sync
findet KEIN Heartbeat statt.

### 4.2 Verschlüsseltes Backup (alle ~24 Stunden)
Inhalt: SharedPreferences-Werte, HUD-Konfiguration, Update-Präferenzen,
Akku-Sample-Verlauf der letzten 30 Tage — als JSON serialisiert,
**Ende-zu-Ende-verschlüsselt** mit AES-256-GCM. Der Schlüssel wird via
HKDF-SHA256 aus deinem Hardware-Fingerprint abgeleitet und verlässt das
Gerät NIE.

Der Server speichert ausschließlich opake verschlüsselte Bytes und kann den
Inhalt nicht entschlüsseln, auch nicht auf Anfrage von Behörden. Zusätzlich
liegt der Blob in der Postgres-Datenbank mit pgcrypto-Spalten-Verschlüsselung
(Defense-in-Depth, At-Rest-Layer).

Zweck: Wiederherstellung deiner Settings nach App-Neuinstallation oder
Geräte-Reset auf derselben Hardware.

## 5. Speicherdauer

Daten auf dem Backend werden automatisch gelöscht:
- Heartbeats: 90 Tage nach Eingang
- Backups: 90 Tage nach Upload
- Device-Records: 90 Tage nach letztem Heartbeat
- Admin-Sessions: nach Ablauf (max. 14 Tage)

Ein automatischer Cleanup-Job läuft alle 24 Stunden auf dem Server.

## 6. Server-Standort und Sicherheit

Der Backend-Server steht in Deutschland. Postgres ist nur auf 127.0.0.1
gebunden, der HTTP-Server lauscht ebenfalls nur lokal hinter einem
nginx-Reverse-Proxy mit HTTPS (Let's Encrypt) + HSTS (max-age 2 Jahre).

## 7. Empfänger / Drittlandtransfer

Es findet KEIN Datentransfer in Drittländer statt. Es gibt keine
Werbe-Tracker, keine Analytics-SDKs, keine Crash-Reporter wie Firebase.

## 8. Deine Rechte (DSGVO)

Du hast jederzeit das Recht auf:
- Auskunft (Art. 15)
- Berichtigung (Art. 16)
- Löschung (Art. 17) — siehe Abschnitt 9
- Einschränkung der Verarbeitung (Art. 18)
- Datenübertragbarkeit (Art. 20)
- Widerspruch (Art. 21)
- Beschwerde bei der zuständigen Datenschutzaufsicht

Kontakt für DSGVO-Anfragen: ${L.PROVIDER_EMAIL}

## 9. Löschung deiner Cloud-Daten

Sofortige Löschung deines Backends-Records:
1. App öffnen → Einstellungen → Cloud-Backup → Toggle AUS, oder
2. E-Mail an ${L.PROVIDER_EMAIL} mit deiner Device-ID (anzeigbar
   in den App-Einstellungen unter „Cloud-Backup")

Nach automatischer 90-Tage-Inaktivität werden die Daten ohnehin gelöscht.

## 10. Berechtigungen der App

Die App fordert je nach genutztem Feature Berechtigungen an (Standort für
GPS-Test, Mikrofon für Mic-Test, Kamera für Flash-Test usw.). Diese
Berechtigungen werden ausschließlich lokal für den jeweiligen Test
verwendet — die erfassten Sensor-/Audio-/Bild-Daten verlassen das Gerät
NICHT.

## 11. Änderungen dieser Datenschutzerklärung

Bei wesentlichen Änderungen wird die Versions-Nummer hochgezählt. Du wirst
beim nächsten Start der App erneut um Zustimmung gebeten.
""".trimIndent()
}
