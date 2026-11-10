package com.example.ricochet

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Singleton repository for Ricochet backed by SharedPreferences + JSON
 * for reliable persistence across BroadcastReceiver invocations and UI updates.
 */
class ForwarderRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _masterEnabled = MutableStateFlow(prefs.getBoolean(KEY_MASTER_ENABLED, true))
    val masterEnabled: StateFlow<Boolean> = _masterEnabled.asStateFlow()

    private val _rules = MutableStateFlow(loadRulesFromPrefs())
    val rules: StateFlow<List<ForwardingRule>> = _rules.asStateFlow()

    private val _history = MutableStateFlow(loadHistoryFromPrefs())
    val history: StateFlow<List<ForwardingLogEntry>> = _history.asStateFlow()

    fun setMasterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
        _masterEnabled.value = enabled
    }

    fun isMasterEnabled(): Boolean = prefs.getBoolean(KEY_MASTER_ENABLED, true)

    fun saveRule(rule: ForwardingRule) {
        val current = loadRulesFromPrefs().toMutableList()
        val index = current.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            current[index] = rule
        } else {
            current.add(0, rule)
        }
        persistRules(current)
    }

    fun deleteRule(ruleId: String) {
        val updated = loadRulesFromPrefs().filterNot { it.id == ruleId }
        persistRules(updated)
    }

    fun toggleRule(ruleId: String, enabled: Boolean) {
        val updated = loadRulesFromPrefs().map {
            if (it.id == ruleId) it.copy(isEnabled = enabled) else it
        }
        persistRules(updated)
    }

    fun getActiveRules(): List<ForwardingRule> {
        return loadRulesFromPrefs().filter { it.isEnabled }
    }

    fun addHistoryEntry(entry: ForwardingLogEntry) {
        val current = loadHistoryFromPrefs().toMutableList()
        current.add(0, entry)
        val trimmed = current.take(MAX_HISTORY_ITEMS)
        persistHistory(trimmed)
    }

    fun clearHistory() {
        persistHistory(emptyList())
    }

    private fun persistRules(rulesList: List<ForwardingRule>) {
        val array = JSONArray()
        rulesList.forEach { rule ->
            val obj = JSONObject().apply {
                put("id", rule.id)
                put("name", rule.name)
                put("recipientNumber", rule.recipientNumber)
                put("senderRegex", rule.senderRegex)
                put("messageRegex", rule.messageRegex)
                put("isEnabled", rule.isEnabled)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_RULES, array.toString()).apply()
        _rules.value = rulesList
    }

    private fun loadRulesFromPrefs(): List<ForwardingRule> {
        val raw = prefs.getString(KEY_RULES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        ForwardingRule(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            recipientNumber = obj.getString("recipientNumber"),
                            senderRegex = obj.optString("senderRegex", ""),
                            messageRegex = obj.optString("messageRegex", ""),
                            isEnabled = obj.optBoolean("isEnabled", true)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistHistory(entries: List<ForwardingLogEntry>) {
        val array = JSONArray()
        entries.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("timestamp", item.timestamp)
                put("ruleName", item.ruleName)
                put("originalSender", item.originalSender)
                put("recipientNumber", item.recipientNumber)
                put("forwardedBody", item.forwardedBody)
                put("status", item.status)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
        _history.value = entries
    }

    private fun loadHistoryFromPrefs(): List<ForwardingLogEntry> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        ForwardingLogEntry(
                            id = obj.getString("id"),
                            timestamp = obj.getLong("timestamp"),
                            ruleName = obj.getString("ruleName"),
                            originalSender = obj.getString("originalSender"),
                            recipientNumber = obj.getString("recipientNumber"),
                            forwardedBody = obj.getString("forwardedBody"),
                            status = obj.getString("status")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "ricochet_prefs"
        private const val KEY_MASTER_ENABLED = "master_enabled"
        private const val KEY_RULES = "forwarding_rules_json"
        private const val KEY_HISTORY = "forwarding_history_json"
        private const val MAX_HISTORY_ITEMS = 200

        @Volatile
        private var INSTANCE: ForwarderRepository? = null

        fun getInstance(context: Context): ForwarderRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ForwarderRepository(context).also { INSTANCE = it }
            }
        }
    }
}
