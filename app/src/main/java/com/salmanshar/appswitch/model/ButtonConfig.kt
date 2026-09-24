package com.salmanshar.appswitch.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ButtonConfig(
    val id: String,
    val type: Int,
    val subButtons: MutableList<SubButton>,
    var posX: Int,
    var posY: Int,
    var sizeDp: Int,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("type", type); put("posX", posX); put("posY", posY); put("sizeDp", sizeDp)
        put("subs", JSONArray().apply { subButtons.forEach { put(it.toJson()) } })
    }
    companion object {
        fun fromJson(o: JSONObject): ButtonConfig {
            val arr = o.getJSONArray("subs")
            val subs = mutableListOf<SubButton>()
            for (i in 0 until arr.length()) subs.add(SubButton.fromJson(arr.getJSONObject(i)))
            return ButtonConfig(
                id = o.optString("id", UUID.randomUUID().toString()),
                type = o.getInt("type"),
                subButtons = subs,
                posX = o.optInt("posX", 20),
                posY = o.optInt("posY", 240),
                sizeDp = o.optInt("sizeDp", 45),
            )
        }
        fun new(type: Int): ButtonConfig {
            val subs = mutableListOf<SubButton>()
            repeat(type) { subs.add(SubButton("", defaultColor(it), 45)) }
            return ButtonConfig(UUID.randomUUID().toString(), type, subs, 20, 240, 45)
        }
    }
}

fun defaultColor(i: Int): Int = when (i) {
    0 -> 0xFF00C853.toInt()
    1 -> 0xFF2962FF.toInt()
    2 -> 0xFFD50000.toInt()
    3 -> 0xFFFF6D00.toInt()
    4 -> 0xFFAA00FF.toInt()
    else -> 0xFF616161.toInt()
}
