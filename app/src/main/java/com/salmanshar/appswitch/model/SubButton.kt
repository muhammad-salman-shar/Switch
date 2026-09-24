package com.salmanshar.appswitch.model

import org.json.JSONObject

data class SubButton(
    var pkg: String,
    var color: Int,
    var sizeDp: Int,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("pkg", pkg); put("color", color); put("sizeDp", sizeDp)
    }
    companion object {
        fun fromJson(o: JSONObject) = SubButton(
            pkg = o.optString("pkg", ""),
            color = o.optInt("color", 0xFF00C853.toInt()),
            sizeDp = o.optInt("sizeDp", 45),
        )
    }
}
