package com.tamerin.sysmonitor.privileged.adb

/** Gemeinsame Konstanten zwischen App-Seite und dem app_process-Starter. */
object AdbConst {
    const val APP_PKG = "com.tamerin.sysmonitor"

    /** Explizite Broadcast-Action, über die der Starter seinen Binder an die App reicht. */
    const val BINDER_ACTION = "com.tamerin.sysmonitor.PRIVILEGED_BINDER"

    /** Bundle-Key für den übergebenen IBinder. */
    const val EXTRA_BINDER = "service"

    /** Protokoll-Version, damit App und Starter zusammenpassen. */
    const val EXTRA_VERSION = "version"
    const val SERVER_VERSION = 1

    /** nice-name des Starter-Prozesses (in `ps` sichtbar). */
    const val PROCESS_NICE_NAME = "com.tamerin.sysmonitor:priv"

    /** Vollqualifizierter Name der Starter-main-Klasse (für den app_process-Aufruf). */
    const val STARTER_CLASS = "com.tamerin.sysmonitor.privileged.adb.Starter"
}
