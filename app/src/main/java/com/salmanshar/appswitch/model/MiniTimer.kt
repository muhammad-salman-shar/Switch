package com.salmanshar.appswitch.model

import org.json.JSONObject

data class MiniTimer(
    var role: String = "single",
    var delayMs: Long = 500L,
    var tapsCount: Int = 10,
    var windowMs: Long = 1000L,
    var sizeDp: Int = 22,
    var alpha: Int = 80,
    var color: Int = 0xFFFFFFFF.toInt(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("role", role); put("delayMs", delayMs); put("tapsCount", tapsCount)
        put("windowMs", windowMs); put("sizeDp", sizeDp); put("alpha", alpha); put("color", color)
    }
    companion object {
        fun fromJson(o: JSONObject) = MiniTimer(
            role = o.optString("role", "single"),
            delayMs = o.optLong("delayMs", 500L),
            tapsCount = o.optInt("tapsCount", 10),
            windowMs = o.optLong("windowMs", 1000L),
            sizeDp = o.optInt("sizeDp", 22),
            alpha = o.optInt("alpha", 80),
            color = o.optInt("color", 0xFFFFFFFF.toInt()),
        )
    }
}
