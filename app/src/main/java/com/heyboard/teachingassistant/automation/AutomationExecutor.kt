package com.heyboard.teachingassistant.automation

import android.content.Context
import android.util.Log

object AutomationExecutor {

    private const val TAG = "AutomationExecutor"

    fun executeOnStart(context: Context) {
        val scenarios = AutomationRepository.getScenariosForTrigger(context, "on_start")
        if (scenarios.isEmpty()) return
        Log.i(TAG, "Executing ${scenarios.size} on_start scenarios")
        Thread {
            for (scenario in scenarios) {
                executeScenario(context, scenario)
            }
        }.start()
    }

    fun executeOnClose(context: Context) {
        val scenarios = AutomationRepository.getScenariosForTrigger(context, "on_close")
        if (scenarios.isEmpty()) return
        Log.i(TAG, "Executing ${scenarios.size} on_close scenarios")
        val thread = Thread {
            for (scenario in scenarios) {
                executeScenario(context, scenario)
            }
        }
        thread.start()
        try {
            thread.join(5000)
        } catch (_: InterruptedException) {}
    }

    private fun executeScenario(context: Context, scenario: AutomationScenario) {
        Log.i(TAG, "Executing scenario: ${scenario.name}")
        for (action in scenario.actions) {
            val result = SerialCommandExecutor.execute(context, action)
            if (result.isFailure) {
                Log.e(TAG, "Action '${action.name}' failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }
}
