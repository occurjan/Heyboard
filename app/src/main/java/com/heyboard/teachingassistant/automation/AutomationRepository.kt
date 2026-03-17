package com.heyboard.teachingassistant.automation

import android.content.Context
import org.json.JSONArray

object AutomationRepository {

    private const val PREFS_NAME = "automation_prefs"
    private const val KEY_SCENARIOS = "scenarios"

    fun getScenarios(context: Context): MutableList<AutomationScenario> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SCENARIOS, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<AutomationScenario>()
            for (i in 0 until arr.length()) {
                list.add(AutomationScenario.fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveScenarios(context: Context, scenarios: List<AutomationScenario>) {
        val arr = JSONArray()
        scenarios.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SCENARIOS, arr.toString())
            .apply()
    }

    fun getScenario(context: Context, id: String): AutomationScenario? {
        return getScenarios(context).find { it.id == id }
    }

    fun saveScenario(context: Context, scenario: AutomationScenario) {
        val list = getScenarios(context)
        val index = list.indexOfFirst { it.id == scenario.id }
        if (index >= 0) {
            list[index] = scenario
        } else {
            list.add(scenario)
        }
        saveScenarios(context, list)
    }

    fun deleteScenario(context: Context, id: String) {
        val list = getScenarios(context)
        list.removeAll { it.id == id }
        saveScenarios(context, list)
    }

    fun getScenariosForTrigger(context: Context, trigger: String): List<AutomationScenario> {
        return getScenarios(context).filter { it.trigger == trigger }
    }
}
