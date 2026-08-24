package com.mrzgaming.ezbox

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("EZBoxSessions", Context.MODE_PRIVATE)

    fun getAllSessions(): List<EzSession> {
        val raw = prefs.getString("sessions", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val result = mutableListOf<EzSession>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(
                EzSession(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    resolution = obj.optString("resolution", "960x540"),
                    wineVariant = obj.optString("wineVariant", "wine-staging"),
                    displayNum = obj.optInt("displayNum", 1),
                    lastUsed = obj.optLong("lastUsed", 0L)
                )
            )
        }
        return result.sortedByDescending { it.lastUsed }
    }

    fun saveSession(session: EzSession) {
        val sessions = getAllSessions().toMutableList()
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            sessions[index] = session
        } else {
            sessions.add(session)
        }
        persist(sessions)
    }

    fun deleteSession(id: String) {
        val sessions = getAllSessions().filterNot { it.id == id }
        persist(sessions)
    }

    fun createNewSession(name: String): EzSession {
        val usedDisplays = getAllSessions().map { it.displayNum }.toSet()
        var nextDisplay = 1
        while (usedDisplays.contains(nextDisplay)) {
            nextDisplay++
        }
        val session = EzSession(id = UUID.randomUUID().toString(), name = name, displayNum = nextDisplay)
        saveSession(session)
        return session
    }

    fun markUsed(id: String) {
        val sessions = getAllSessions().toMutableList()
        val index = sessions.indexOfFirst { it.id == id }
        if (index >= 0) {
            sessions[index] = sessions[index].copy(lastUsed = System.currentTimeMillis())
            persist(sessions)
        }
    }

    private fun persist(sessions: List<EzSession>) {
        val arr = JSONArray()
        for (s in sessions) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("name", s.name)
            obj.put("resolution", s.resolution)
            obj.put("wineVariant", s.wineVariant)
            obj.put("displayNum", s.displayNum)
            obj.put("lastUsed", s.lastUsed)
            arr.put(obj)
        }
        prefs.edit().putString("sessions", arr.toString()).apply()
    }
}
