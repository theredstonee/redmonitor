package com.tamerin.sysmonitor.legal

import com.tamerin.sysmonitor.legal.LegalConstants as L

object TermsText {

    val SUMMARY = """
        Kostenlose Open-Source-App. Du nutzt sie auf eigenes Risiko —
        besonders die Stresstest-, Drain- und Russian-Roulette-Features
        können dein Gerät oder dessen Akku belasten. Keine Garantie auf
        Verfügbarkeit der Cloud-Funktion.
    """.trimIndent()

    val FULL: String = """
# Nutzungsbedingungen — RedMonitor App

Stand: ${L.LEGAL_LAST_UPDATED}

## 1. Geltungsbereich und Anbieter

Diese Nutzungsbedingungen regeln die Verwendung der Android-App „RedMonitor"
und des dazugehörigen Backend-Dienstes unter ${L.BACKEND_HOST}.

Anbieter:
${L.PROVIDER_NAME}
${L.PROVIDER_REPRESENTATIVE}, ${L.PROVIDER_STREET}, ${L.PROVIDER_CITY},
${L.PROVIDER_COUNTRY}
${L.PROVIDER_EMAIL}

Volles Impressum: ${L.IMPRINT_URL}

## 2. Lizenz und Open Source

Die App wird kostenlos zur Verfügung gestellt. Der Quellcode steht unter der
MIT-Lizenz öffentlich auf GitHub (theredstonee/redmonitor) zur Verfügung.

## 3. Eigenverantwortung

RedMonitor ist ein technisches Werkzeug zur Analyse und Modifikation von
System-Eigenschaften. Du verwendest die App auf eigenes Risiko. Insbesondere
gilt:

### 3.1 Destruktive Features
Folgende Features können das Gerät vorübergehend belasten oder Daten löschen.
Sie sind klar als solche gekennzeichnet und erfordern eine separate
Bestätigung:

- **Stresstest / Akku-Drainer**: erzeugt maximale CPU-/GPU-Last und kann
  das Gerät stark erhitzen. Bei Dauerbetrieb potenzielle Akku-Alterung.
- **Russian Roulette**: würfelt zufällig destruktive Aktionen (App-Crash,
  SystemUI-Crash, Fork-Bomb, Shutdown). 5%-Chance auf Fork-Bomb, die das
  Gerät bis zum nächsten Hardware-Reboot zäh macht. 2%-Chance auf sofortigen
  Shutdown ohne Speicherung laufender Prozesse.
- **Shizuku-Operationen**: Mit erteilten Shizuku-Rechten kann die App
  System-Settings ändern (DPI, Animation, Charging-Stop), Apps force-stoppen,
  Permissions revoken etc. Falsche Bedienung kann das System unbenutzbar
  machen oder Daten anderer Apps gefährden.

Der Anbieter haftet NICHT für Schäden, die durch die bewusste Auslösung
solcher Features entstehen.

### 3.2 Charging-Control
Die Smart-Charging-Stop-Funktion schreibt direkt in den Kernel-Sysfs. Bei
inkompatiblen Geräten kann sich der Lade-Status hängen. Reboot löst das
Problem in der Regel; der Anbieter haftet nicht für Akku-Probleme durch
fehlerhafte Sysfs-Pfade.

## 4. Backend / Cloud-Sync

### 4.1 Verfügbarkeit
Der Cloud-Sync-Dienst unter ${L.BACKEND_HOST} wird ohne Verfügbarkeits-
Garantie betrieben. Wartungsausfälle, Server-Migrationen oder die
endgültige Einstellung des Dienstes können jederzeit erfolgen. Du wirst
nach Möglichkeit vorher in der App benachrichtigt.

### 4.2 Backup-Wiederherstellung
Backups werden Ende-zu-Ende verschlüsselt — der Schlüssel wird aus deinem
Hardware-Fingerprint abgeleitet (siehe Datenschutzerklärung). Bei
Hardware-Wechsel, Factory-Reset oder ANDROID_ID-Änderung KANN das Backup
nicht wiederhergestellt werden. Es ist KEIN allgemeines Cross-Device-
Synchronisations-System.

### 4.3 Daten-Limits
Backup-Größe ist auf 2 MB pro Upload begrenzt. Pro Device werden max. die 5
neuesten Backups aufbewahrt; ältere werden bei jedem Upload gelöscht. Alle
Daten werden 90 Tage nach letztem Heartbeat automatisch gelöscht.

## 5. Pflichten des Nutzers

Du verpflichtest dich:
- Die App nicht zu missbrauchen, um fremde Geräte zu schädigen
- Die destruktiven Features (siehe 3.1) nur auf eigenen Geräten zu nutzen
- Keine Vulnerabilities/Bugs für Angriffe gegen Dritte einzusetzen
- Bei sicherheitsrelevanten Funden eine verantwortungsvolle Offenlegung
  per E-Mail an ${L.PROVIDER_EMAIL} durchzuführen

## 6. Haftungsausschluss

Der Anbieter haftet uneingeschränkt nur für vorsätzlich oder grob
fahrlässig verursachte Schäden sowie nach Maßgabe des
Produkthaftungsgesetzes. Für leichte Fahrlässigkeit haftet der Anbieter
nur bei Verletzung wesentlicher Vertragspflichten, und in diesem Fall
nur bis zur Höhe des vorhersehbaren, typischerweise eintretenden
Schadens.

Insbesondere wird keine Haftung übernommen für:
- Datenverlust durch destruktive Features (Roulette, Stresstest, etc.)
- Akku-Schäden durch Drain-Mode oder Charging-Control-Experimente
- System-Instabilität durch Shizuku-Operationen oder Sysfs-Writes
- Ausfall oder Datenverlust des Cloud-Backup-Services

## 7. Änderungen der Nutzungsbedingungen

Bei wesentlichen Änderungen wird die Versions-Nummer hochgezählt. Du wirst
beim nächsten Start der App erneut um Zustimmung gebeten.

## 8. Schlussbestimmungen

Gerichtsstand für alle Streitigkeiten ist, soweit gesetzlich zulässig,
der Sitz des Anbieters (Schliersee, Landgericht München II). Es gilt
deutsches Recht.

Sollte eine Bestimmung dieser Nutzungsbedingungen unwirksam sein, bleibt
die Wirksamkeit der übrigen Bestimmungen unberührt.
""".trimIndent()
}
