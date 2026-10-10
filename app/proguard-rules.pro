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
