package com.salmanshar.appswitch.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ButtonConfig(
    val id: String,
    val type: Int, // 0 = timer, 1-5 = switch variants
    val subButtons: MutableList<SubButton>,
    val minis: MutableList<MiniTimer>,
    var posX: Int,
    var posY: Int,
    var sizeDp: Int,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("type", type); put("posX", posX); put("posY", posY); put("sizeDp", sizeDp)
        put("subs", JSONArray().apply { subButtons.forEach { put(it.toJson()) } })
        put("minis", JSONArray().apply { minis.forEach { put(it.toJson()) } })
    }
    companion object {
        fun fromJson(o: JSONObject): ButtonConfig {
            val subsArr = o.optJSONArray("subs") ?: JSONArray()
            val subs = mutableListOf<SubButton>()
            for (i in 0 until subsArr.length()) subs.add(SubButton.fromJson(subsArr.getJSONObject(i)))
            val minisArr = o.optJSONArray("minis") ?: JSONArray()
            val minis = mutableListOf<MiniTimer>()
            for (i in 0 until minisArr.length()) minis.add(MiniTimer.fromJson(minisArr.getJSONObject(i)))
            return ButtonConfig(
                id = o.optString("id", UUID.randomUUID().toString()),
                type = o.getInt("type"),
                subButtons = subs,
                minis = minis,
                posX = o.optInt("posX", 20),
                posY = o.optInt("posY", 240),
                sizeDp = o.optInt("sizeDp", 45),
            )
        }
        fun new(type: Int): ButtonConfig {
            val subs = mutableListOf<SubButton>()
            if (type != 0) repeat(type) { subs.add(SubButton("", defaultColor(it), 45)) }
            return ButtonConfig(
                id = UUID.randomUUID().toString(),
                type = type,
                subButtons = subs,
                minis = mutableListOf(),
                posX = 20, posY = 240, sizeDp = if (type == 0) 90 else 45,
            )
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
