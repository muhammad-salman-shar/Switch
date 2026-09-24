package com.salmanshar.appswitch.model

import org.json.JSONObject

data class MiniTimer(
    var role: String = "single",
    var delayMs: Long = 0L,
    var tapsCount: Int = 10,
    var windowMs: Long = 1000L,
    var sizeDp: Int = 30,
    var alpha: Int = 80,
    var color: Int = 0xFFFF6D00.toInt(),
    var name: String = "",
    var posX: Int = -1,
    var posY: Int = -1,
    var locked: Boolean = false,
    var offsetX: Int = 0,
    var offsetY: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("role", role); put("delayMs", delayMs); put("tapsCount", tapsCount)
        put("windowMs", windowMs); put("sizeDp", sizeDp); put("alpha", alpha)
        put("color", color); put("name", name)
        put("posX", posX); put("posY", posY); put("locked", locked)
        put("offsetX", offsetX); put("offsetY", offsetY)
    }
    companion object {
        fun fromJson(o: JSONObject) = MiniTimer(
            role = o.optString("role", "single"),
            delayMs = o.optLong("delayMs", 0L),
            tapsCount = o.optInt("tapsCount", 10),
            windowMs = o.optLong("windowMs", 1000L),
            sizeDp = o.optInt("sizeDp", 30),
            alpha = o.optInt("alpha", 80),
            color = o.optInt("color", 0xFFFF6D00.toInt()),
            name = o.optString("name", ""),
            posX = o.optInt("posX", -1),
            posY = o.optInt("posY", -1),
            locked = o.optBoolean("locked", false),
            offsetX = o.optInt("offsetX", 0),
            offsetY = o.optInt("offsetY", 0),
        )
    }
}
