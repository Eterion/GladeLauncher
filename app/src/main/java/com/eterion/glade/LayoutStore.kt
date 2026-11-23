package com.eterion.glade

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class LayoutStore(private val preferences: SharedPreferences) {
    fun read(): List<Placement> {
        val raw = preferences.getString("home_v1", "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            val items = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                Placement(item.optString("component"), item.optInt("cell", -1))
            }
            // Earlier builds stored six rows without row metadata. Anchor top/bottom rows.
            val oldRows = preferences.getInt("home_rows", if (preferences.contains("home_v1")) 6 else HomeLayout.ROWS)
            val result = HomeLayout.remapRows(items, oldRows)
            if (oldRows != HomeLayout.ROWS) write(result)
            result
        } catch (_: org.json.JSONException) {
            emptyList()
        }
    }

    fun write(items: List<Placement>) {
        val array = JSONArray()
        HomeLayout.normalize(items).forEach {
            array.put(JSONObject().put("component", it.component).put("cell", it.cell))
        }
        preferences.edit().putString("home_v1", array.toString()).putInt("home_rows", HomeLayout.ROWS).apply()
    }

    fun readQuickAccess(): List<String> = try {
        val array = JSONArray(preferences.getString("quick_access_v1", "[]") ?: "[]")
        QuickAccess.normalize((0 until array.length()).mapNotNull { array.opt(it) as? String })
    } catch (_: org.json.JSONException) {
        emptyList()
    }

    fun writeQuickAccess(ids: List<String>) {
        preferences.edit().putString("quick_access_v1", JSONArray(QuickAccess.normalize(ids)).toString()).apply()
    }
}
