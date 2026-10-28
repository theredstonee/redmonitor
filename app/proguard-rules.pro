# Standard Android / Compose rules
-dontwarn org.jetbrains.annotations.**
-keep class com.tamerin.sysmonitor.** { *; }

# Eigener eingebetteter ADB-Zugang: Conscrypt (JNI/native) + Bouncy Castle (Provider
# per Reflection über Algorithmus-Namen) — nicht shrinken/obfuskieren, sonst brechen
# TLS-Handshake bzw. Cert-Erzeugung im Release-Build.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-keep class org.conscrypt.** { *; }
-keep class org.bouncycastle.** { *; }

# ShizukuHelper ruft Shizuku.newProcess per Reflection beim Namen auf (die Methode ist @hide).
# Das Shizuku-AAR bringt keine Keep-Regel dafuer mit — ohne diese Zeile benennt R8 sie im
# Release-Build um und JEDES Shizuku-Kommando scheitert still mit "newProcess failed".
-keepclassmembers class rikka.shizuku.Shizuku {
    *** newProcess(...);
}
