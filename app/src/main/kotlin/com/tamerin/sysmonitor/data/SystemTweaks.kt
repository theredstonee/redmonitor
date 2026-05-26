package com.tamerin.sysmonitor.data

object SystemTweaks {

    // ===================== DOZE WHITELIST =====================

    data class WhitelistEntry(val pkg: String, val isUser: Boolean, val uid: Int?)

    fun listDozeWhitelist(context: android.content.Context): List<WhitelistEntry> {
        val res = ShizukuHelper.runCommand(context, "cmd", "deviceidle", "whitelist")
        if (!res.ok) return emptyList()
        val out = mutableListOf<WhitelistEntry>()
        for (line in res.stdout.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            // Format: "system,com.foo.bar,10024" OR plain "com.foo.bar"
            val parts = t.split(",")
            when (parts.size) {
                1 -> if (parts[0].contains(".")) out += WhitelistEntry(parts[0], false, null)
                2 -> if (parts[1].contains(".")) out += WhitelistEntry(parts[1], parts[0] == "user", null)
                3 -> if (parts[1].contains(".")) {
                    out += WhitelistEntry(parts[1], parts[0] == "user", parts[2].toIntOrNull())
                }
            }
        }
        return out.distinctBy { it.pkg }
    }

    fun addToWhitelist(context: android.content.Context, pkg: String): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(context, "cmd", "deviceidle", "whitelist", "+$pkg")

    fun removeFromWhitelist(context: android.content.Context, pkg: String): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(context, "cmd", "deviceidle", "whitelist", "-$pkg")

    fun isWhitelisted(context: android.content.Context, pkg: String): Boolean =
        listDozeWhitelist(context).any { it.pkg == pkg }

    // ===================== DISPLAY TWEAKS =====================

    data class DisplayState(
        val currentDpi: Int,
        val physicalDpi: Int,
        val currentSize: String,
        val physicalSize: String
    )

    fun readDisplay(context: android.content.Context): DisplayState {
        val dpiRes = ShizukuHelper.runCommand(context, "wm", "density")
        val sizeRes = ShizukuHelper.runCommand(context, "wm", "size")
        // wm density output:
        //   Physical density: 420
        //   Override density: 380     (only if overridden)
        val physDpi = Regex("""Physical density:\s*(\d+)""").find(dpiRes.stdout)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val overDpi = Regex("""Override density:\s*(\d+)""").find(dpiRes.stdout)?.groupValues?.get(1)?.toIntOrNull()
        val physSize = Regex("""Physical size:\s*(\S+)""").find(sizeRes.stdout)?.groupValues?.get(1) ?: "?"
        val overSize = Regex("""Override size:\s*(\S+)""").find(sizeRes.stdout)?.groupValues?.get(1)
        return DisplayState(
            currentDpi = overDpi ?: physDpi,
            physicalDpi = physDpi,
            currentSize = overSize ?: physSize,
            physicalSize = physSize
        )
    }

    fun setDpi(context: android.content.Context, dpi: Int): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(context, "wm", "density", dpi.toString())

    fun resetDpi(context: android.content.Context): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(context, "wm", "density", "reset")

    fun setSize(context: android.content.Context, size: String): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(context, "wm", "size", size)

    fun resetSize(context: android.content.Context): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(context, "wm", "size", "reset")

    // ===================== ANIMATION SCALES =====================

    data class AnimScales(val window: Float, val transition: Float, val animator: Float)

    fun readAnimScales(context: android.content.Context): AnimScales {
        fun get(key: String): Float = ShizukuHelper.runCommand(
            context, "settings", "get", "global", key
        ).stdout.trim().toFloatOrNull() ?: 1f
        return AnimScales(
            window = get("window_animation_scale"),
            transition = get("transition_animation_scale"),
            animator = get("animator_duration_scale")
        )
    }

    fun setAllAnimScales(context: android.content.Context, scale: Float): List<ShizukuHelper.CmdResult> {
        return listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
            .map {
                ShizukuHelper.runCommand(
                    context, "settings", "put", "global", it, scale.toString()
                )
            }
    }

    // ===================== USEFUL SECURE / SYSTEM TOGGLES =====================

    fun setShowTouches(context: android.content.Context, enabled: Boolean): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(
            context, "settings", "put", "system", "show_touches", if (enabled) "1" else "0"
        )

    fun isShowTouches(context: android.content.Context): Boolean =
        ShizukuHelper.runCommand(
            context, "settings", "get", "system", "show_touches"
        ).stdout.trim() == "1"

    fun setPointerLocation(context: android.content.Context, enabled: Boolean): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(
            context, "settings", "put", "system", "pointer_location", if (enabled) "1" else "0"
        )

    fun isPointerLocation(context: android.content.Context): Boolean =
        ShizukuHelper.runCommand(
            context, "settings", "get", "system", "pointer_location"
        ).stdout.trim() == "1"

    fun setAlwaysOnDisplay(context: android.content.Context, enabled: Boolean): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(
            context, "settings", "put", "secure", "doze_always_on", if (enabled) "1" else "0"
        )

    fun isAlwaysOnDisplay(context: android.content.Context): Boolean =
        ShizukuHelper.runCommand(
            context, "settings", "get", "secure", "doze_always_on"
        ).stdout.trim() == "1"
}
