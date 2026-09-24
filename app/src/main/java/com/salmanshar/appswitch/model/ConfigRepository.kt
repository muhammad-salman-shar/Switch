package com.salmanshar.appswitch.model

import android.content.Context
import org.json.JSONArray

class ConfigRepository(context: Context) {
    private val prefs = context.getSharedPreferences("switch", Context.MODE_PRIVATE)

    fun loadButtons(): MutableList<ButtonConfig> {
        val raw = prefs.getString("buttonsJson", null)
        if (raw != null) {
            return try {
                val arr = JSONArray(raw)
                val list = mutableListOf<ButtonConfig>()
                for (i in 0 until arr.length()) list.add(ButtonConfig.fromJson(arr.getJSONObject(i)))
                list
            } catch (_: Exception) { mutableListOf() }
        }
        val a = prefs.getString("pkgA", "") ?: ""
        val b = prefs.getString("pkgB", "") ?: ""
        val list = mutableListOf<ButtonConfig>()
        if (a.isNotEmpty() && b.isNotEmpty()) {
            val b1 = ButtonConfig.new(2)
            b1.subButtons[0].pkg = a
            b1.subButtons[1].pkg = b
            b1.posX = prefs.getInt("posX", 20)
            b1.posY = prefs.getInt("posY", 240)
            b1.sizeDp = prefs.getInt("sizeDp", 45)
            list.add(b1)
        }
        saveButtons(list)
        return list
    }

    fun saveButtons(list: List<ButtonConfig>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("buttonsJson", arr.toString()).apply()
    }

    fun updateButton(updated: ButtonConfig) {
        val list = loadButtons()
        val idx = list.indexOfFirst { it.id == updated.id }
        if (idx >= 0) list[idx] = updated else list.add(updated)
        saveButtons(list)
    }

    fun deleteButton(id: String) {
        val list = loadButtons()
        list.removeAll { it.id == id }
        saveButtons(list)
    }
}
